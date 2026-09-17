package main

import (
	"crypto/sha256"
	"crypto/subtle"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"strconv"
	"strings"
	"time"
)

// API serves the authenticated JSON endpoints and public status page.
type API struct {
	cfg         *Config
	mon         *Monitor
	tm          *TunnelManager
	wd          *TunnelWatchdog // nil when the watchdog is disabled
	pm          *ProbeMonitor   // nil when the probe monitor is disabled
	limiter     *rateLimiter
	mutations   *mutationLimiter
	idempotency *idempotencyGuard
}

func newAPI(cfg *Config, mon *Monitor, tm *TunnelManager, wd *TunnelWatchdog, pm *ProbeMonitor) *API {
	return &API{
		cfg: cfg, mon: mon, tm: tm, wd: wd, pm: pm,
		limiter: newRateLimiter(), mutations: newMutationLimiter(), idempotency: newIdempotencyGuard(),
	}
}

func (a *API) routes() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("/health", a.handleHealth)
	if a.cfg.PublicStatus {
		mux.HandleFunc("/status", a.handleStatusPage)
	} else {
		mux.HandleFunc("/status", a.auth(a.handleStatusPage))
	}
	// The API namespace is always authenticated, regardless of the optional
	// public status-page posture.
	mux.HandleFunc("/api/status", a.auth(a.handleStatusPage))
	mux.HandleFunc("/api/metrics", a.auth(a.handleMetrics))
	mux.HandleFunc("/api/processes", a.auth(a.handleProcesses))
	mux.HandleFunc("/api/processes/kill", a.auth(a.limitMutation(a.idempotent(a.auditDestructive("process_kill", a.handleProcessKill)))))
	mux.HandleFunc("/api/network/sockets", a.auth(a.handleNetworkSockets))

	// Real bandwidth test (streaming download / upload sink)
	mux.HandleFunc("/api/bandwidth/download", a.auth(a.handleBandwidthDownload))
	mux.HandleFunc("/api/bandwidth/upload", a.auth(a.handleBandwidthUpload))
	mux.HandleFunc("/api/docker/containers", a.auth(a.handleDockerContainers))
	mux.HandleFunc("/api/docker/restart", a.auth(a.limitMutation(a.idempotent(a.auditDestructive("docker_restart", a.handleDockerRestart)))))
	mux.HandleFunc("/api/docker/stop", a.auth(a.limitMutation(a.idempotent(a.auditDestructive("docker_stop", a.handleDockerStop)))))

	// Tunnel Management APIs (Smite / Marzban style auto-orchestration)
	mux.HandleFunc("/api/tunnel/apply", a.auth(a.limitMutation(a.idempotent(a.auditDestructive("tunnel_apply", a.handleTunnelApply)))))
	mux.HandleFunc("/api/tunnel/start", a.auth(a.limitMutation(a.idempotent(a.auditDestructive("tunnel_start", a.handleTunnelStart)))))
	mux.HandleFunc("/api/tunnel/stop", a.auth(a.limitMutation(a.idempotent(a.auditDestructive("tunnel_stop", a.handleTunnelStop)))))
	mux.HandleFunc("/api/tunnel/restart", a.auth(a.limitMutation(a.idempotent(a.auditDestructive("tunnel_restart", a.handleTunnelRestart)))))
	mux.HandleFunc("/api/tunnel/delete", a.auth(a.limitMutation(a.idempotent(a.auditDestructive("tunnel_delete", a.handleTunnelDelete)))))
	mux.HandleFunc("/api/tunnel/status", a.auth(a.handleTunnelStatus))
	mux.HandleFunc("/api/tunnel/list", a.auth(a.handleTunnelList))
	mux.HandleFunc("/api/tunnel/watchdog", a.auth(a.handleTunnelWatchdog))
	// Multi-point probing (Phase 4 · 4-B)
	mux.HandleFunc("/api/probe", a.auth(a.handleProbeStatus))
	mux.HandleFunc("/api/probe/targets", a.auth(a.handleProbeTargets))
	mux.HandleFunc("/api/probe/now", a.auth(a.handleProbeNow))

	mux.HandleFunc("/api/alerts/telegram/test", a.auth(a.handleAlertsTest))
	mux.HandleFunc("/api/alerts/test", a.auth(a.handleAlertsTest))
	mux.HandleFunc("/api/events", a.auth(a.handleEvents))
	mux.HandleFunc("/api/history", a.auth(a.handleHistory))
	return a.harden(mux)
}

// ── Hardening middleware (H5) ───────────────────────────────────────────────

// maxRequestBodyBytes caps every request body globally (tunnel apply's own
// cap is the same value and is therefore no longer needed per-handler).
const maxRequestBodyBytes = 2 * 1024 * 1024

// bodyCapExemptPaths are routes that stream a large body and enforce their own
// limit inside the handler. The global 2 MiB cap must not be applied to them:
// the bandwidth benchmark uploads up to bandwidthMaxBytes (200 MiB) and its
// handler already wraps the body in http.MaxBytesReader, so capping it here
// made every upload test fail with 400 "request body too large".
var bodyCapExemptPaths = map[string]bool{
	"/api/bandwidth/upload": true,
}

// clientIP is the direct peer address. X-Forwarded-For is deliberately not
// trusted: the agent listens directly on the LAN/WAN interface.
func clientIP(r *http.Request) string {
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		return r.RemoteAddr
	}
	return host
}

type statusRecorder struct {
	http.ResponseWriter
	status int
}

func (s *statusRecorder) WriteHeader(code int) {
	s.status = code
	s.ResponseWriter.WriteHeader(code)
}

// harden wraps the mux with: a global body cap, per-IP rate limiting (except
// the trivial /health liveness probe) and an access log that never writes
// query strings, headers or bodies (no secrets in logs).
func (a *API) harden(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("X-Content-Type-Options", "nosniff")
		w.Header().Set("X-Frame-Options", "DENY")
		w.Header().Set("Referrer-Policy", "no-referrer")
		if strings.HasPrefix(r.URL.Path, "/api/") || r.URL.Path == "/status" {
			w.Header().Set("Cache-Control", "no-store")
			w.Header().Set("Pragma", "no-cache")
		}
		if r.URL.Path == "/status" || r.URL.Path == "/api/status" {
			w.Header().Set("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'")
		}
		// Global body cap for every request (enforced at read time), except for
		// the routes that stream a large payload and cap it themselves.
		if !bodyCapExemptPaths[r.URL.Path] {
			r.Body = http.MaxBytesReader(w, r.Body, maxRequestBodyBytes)
		}

		rec := &statusRecorder{ResponseWriter: w, status: http.StatusOK}
		start := time.Now()

		if r.URL.Path != "/health" && !a.limiter.allow(clientIP(r)) {
			w.Header().Set("Retry-After", "1")
			writeJSON(rec, http.StatusTooManyRequests, map[string]string{"error": "rate limit exceeded"})
			logAccess(clientIP(r), r, rec.status, time.Since(start))
			return
		}

		next.ServeHTTP(rec, r)
		logAccess(clientIP(r), r, rec.status, time.Since(start))
	})
}

func logAccess(ip string, r *http.Request, status int, d time.Duration) {
	// ip method path status duration — path only, never the query string.
	log.Printf("access %s %s %s %d %s", ip, r.Method, r.URL.Path, status, d.Round(time.Microsecond))
}

// decodeJSON accepts exactly one JSON object with the declared media type.
// Unknown/trailing fields are rejected so typos cannot silently change the
// target or semantics of a destructive request.
func decodeJSON(w http.ResponseWriter, r *http.Request, dst any) bool {
	if !strings.HasPrefix(strings.ToLower(r.Header.Get("Content-Type")), "application/json") {
		writeJSON(w, http.StatusUnsupportedMediaType, map[string]string{"error": "application/json required"})
		return false
	}
	dec := json.NewDecoder(r.Body)
	dec.DisallowUnknownFields()
	if err := dec.Decode(dst); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid json"})
		return false
	}
	if err := dec.Decode(&struct{}{}); err != io.EOF {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "exactly one json object required"})
		return false
	}
	return true
}

// auth wraps a handler with bearer-token authentication (constant time).
func (a *API) auth(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		// Bearer header only. Query-string tokens (?token=) were removed:
		// they leak into proxy/access logs and browser history. The
		// didban:// onboarding link still carries the token (one-time,
		// user-initiated) — that is not an HTTP request.
		tok := r.Header.Get("Authorization")
		if len(tok) > 7 && tok[:7] == "Bearer " {
			tok = tok[7:]
		} else {
			tok = ""
		}
		if subtle.ConstantTimeCompare([]byte(tok), []byte(a.cfg.Token)) != 1 {
			writeJSON(w, http.StatusUnauthorized, map[string]string{"error": "unauthorized"})
			return
		}
		next(w, r)
	}
}

func (a *API) handleHealth(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "method not allowed"})
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"status":          "ok",
		"version":         version,
		"agent":           "didban",
		"alerts_active":   a.mon.dispatcher.HasActiveProviders(),
		"telegram_active": a.mon.dispatcher.IsTelegramEnabled(),
		"timestamp":       time.Now().Unix(),
	})
}

func (a *API) handleMetrics(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "method not allowed"})
		return
	}
	writeJSON(w, http.StatusOK, a.mon.Snapshot())
}

func (a *API) handleProcesses(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "method not allowed"})
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"processes": a.mon.Procs(),
	})
}

func (a *API) handleNetworkSockets(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "method not allowed"})
		return
	}
	writeJSON(w, http.StatusOK, GetNetworkSockets())
}

func (a *API) handleDockerContainers(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "method not allowed"})
		return
	}
	writeJSON(w, http.StatusOK, GetDockerContainers())
}

type dockerActionReq struct {
	ID string `json:"id"`
}

func (a *API) handleDockerRestart(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "method not allowed"})
		return
	}
	var req dockerActionReq
	if !decodeJSON(w, r, &req) {
		return
	}
	if err := RestartDockerContainer(req.ID); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": err.Error()})
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"success": true, "message": "Container restarted"})
}

func (a *API) handleDockerStop(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "method not allowed"})
		return
	}
	var req dockerActionReq
	if !decodeJSON(w, r, &req) {
		return
	}
	if err := StopDockerContainer(req.ID); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": err.Error()})
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"success": true, "message": "Container stopped"})
}

// ── Tunnel Endpoints ────────────────────────────────────────────────────────

func (a *API) handleTunnelApply(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "method not allowed"})
		return
	}
	var req TunnelApplyReq
	// Body is globally capped; strict decoding also rejects unknown/trailing data.
	if !decodeJSON(w, r, &req) {
		return
	}

	// Audit trail: log deploy attempts that carry an install script.
	if req.ExecScript != "" {
		sum := sha256.Sum256([]byte(req.ExecScript))
		a.mon.RecordEventLocal(Event{
			Time:   time.Now(),
			Type:   "tunnel_deploy_start",
			Detail: fmt.Sprintf("id=%s core=%s role=%s script_sha256=%s", req.ID, req.Core, req.Role, hex.EncodeToString(sum[:8])),
		})
	}

	res, err := a.tm.ApplyTunnel(req)
	if err != nil {
		writeTunnelError(w, err)
		return
	}

	if req.ExecScript != "" {
		evType := "tunnel_deploy_ok"
		if !res.Success {
			evType = "tunnel_deploy_failed"
		}
		a.mon.RecordEventLocal(Event{
			Time:   time.Now(),
			Type:   evType,
			Detail: fmt.Sprintf("id=%s service=%s error=%s", req.ID, res.ServiceName, res.Error),
		})
	}

	writeJSON(w, http.StatusOK, res)
}

// writeTunnelError maps tunnel manager errors to proper HTTP status codes.
func writeTunnelError(w http.ResponseWriter, err error) {
	switch {
	case errors.Is(err, errTunnelNotFound):
		writeJSON(w, http.StatusNotFound, map[string]string{"error": err.Error()})
	case errors.Is(err, errInvalidTunnelID),
		errors.Is(err, errInvalidServiceName),
		errors.Is(err, errConfigPathOutside):
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": err.Error()})
	default:
		writeJSON(w, http.StatusInternalServerError, map[string]string{"error": err.Error()})
	}
}

func (a *API) handleTunnelStart(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "method not allowed"})
		return
	}
	var req TunnelActionReq
	if !decodeJSON(w, r, &req) {
		return
	}

	res, err := a.tm.StartTunnel(req.ID, req.ServiceName)
	if err != nil {
		writeTunnelError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, res)
}

func (a *API) handleTunnelStop(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "method not allowed"})
		return
	}
	var req TunnelActionReq
	if !decodeJSON(w, r, &req) {
		return
	}

	res, err := a.tm.StopTunnel(req.ID, req.ServiceName)
	if err != nil {
		writeTunnelError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, res)
}

func (a *API) handleTunnelRestart(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "method not allowed"})
		return
	}
	var req TunnelActionReq
	if !decodeJSON(w, r, &req) {
		return
	}

	res, err := a.tm.StartTunnel(req.ID, req.ServiceName)
	if err != nil {
		writeTunnelError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, res)
}

func (a *API) handleTunnelDelete(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost && r.Method != http.MethodDelete {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "method not allowed"})
		return
	}
	var req TunnelActionReq
	if !decodeJSON(w, r, &req) {
		return
	}

	if err := a.tm.DeleteTunnel(req.ID, req.ServiceName); err != nil {
		writeTunnelError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"success": true, "message": "Tunnel removed"})
}

func (a *API) handleTunnelStatus(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "method not allowed"})
		return
	}
	id := r.URL.Query().Get("id")
	if id == "" {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "missing id parameter"})
		return
	}

	res, err := a.tm.GetTunnelStatus(id)
	if err != nil {
		writeTunnelError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, res)
}

func (a *API) handleTunnelList(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "method not allowed"})
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"tunnels": a.tm.ListTunnels(),
	})
}

// ── Process & Alerts Endpoints ──────────────────────────────────────────────

type killRequest struct {
	PID    int    `json:"pid"`
	Signal string `json:"signal"`
}

// handleTunnelWatchdog reports the watchdog's current per-tunnel
// classification and recent transitions (Phase 4 · 4-A).
func (a *API) handleTunnelWatchdog(w http.ResponseWriter, r *http.Request) {
	if a.wd == nil {
		writeJSON(w, http.StatusOK, WatchdogSnapshot{Enabled: false})
		return
	}
	writeJSON(w, http.StatusOK, a.wd.Snapshot())
}

// handleProbeStatus reports the multi-point probe snapshot (Phase 4 · 4-B):
// every registered target as seen from THIS host, with latency history.
func (a *API) handleProbeStatus(w http.ResponseWriter, r *http.Request) {
	if a.pm == nil {
		writeJSON(w, http.StatusOK, ProbeSnapshot{Enabled: false})
		return
	}
	writeJSON(w, http.StatusOK, a.pm.Snapshot())
}

type probeTargetsReq struct {
	Targets []ProbeTargetSpec `json:"targets"`
}

// handleProbeTargets replaces the registered probe target set (owned by the
// app). The whole batch is validated before anything is applied.
func (a *API) handleProbeTargets(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPut && r.Method != http.MethodPost {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "method not allowed (PUT required)"})
		return
	}
	if a.pm == nil {
		writeJSON(w, http.StatusServiceUnavailable, map[string]string{"error": "probe monitor is disabled on this agent"})
		return
	}
	var req probeTargetsReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid JSON: " + err.Error()})
		return
	}
	if err := a.pm.SetTargets(req.Targets); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": err.Error()})
		return
	}
	writeJSON(w, http.StatusOK, a.pm.Snapshot())
}

type probeNowReq struct {
	Target string `json:"target"` // empty = probe everything
}

// handleProbeNow triggers an immediate probe (one target, or all).
func (a *API) handleProbeNow(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "method not allowed (POST required)"})
		return
	}
	if a.pm == nil {
		writeJSON(w, http.StatusServiceUnavailable, map[string]string{"error": "probe monitor is disabled on this agent"})
		return
	}
	var req probeNowReq
	_ = json.NewDecoder(r.Body).Decode(&req) // empty body = probe all
	if req.Target != "" {
		pt, ok := a.pm.ProbeNow(req.Target)
		if !ok {
			writeJSON(w, http.StatusNotFound, map[string]string{"error": "unknown target: " + req.Target})
			return
		}
		writeJSON(w, http.StatusOK, pt)
		return
	}
	a.pm.ProbeAll()
	writeJSON(w, http.StatusOK, a.pm.Snapshot())
}

func (a *API) handleProcessKill(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "method not allowed (POST required)"})
		return
	}

	var req killRequest
	if !decodeJSON(w, r, &req) {
		return
	}

	if req.PID <= 0 {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "missing or invalid pid parameter"})
		return
	}

	res, err := KillProcess(req.PID, req.Signal)
	if err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": err.Error()})
		return
	}

	writeJSON(w, http.StatusOK, res)
}

func (a *API) handleAlertsTest(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost && r.Method != http.MethodGet {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "method not allowed"})
		return
	}

	if !a.mon.dispatcher.HasActiveProviders() {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "No alert channels are configured. Configure Telegram, Discord, or Webhook."})
		return
	}

	a.mon.mu.RLock()
	host := a.mon.snap.Hostname
	a.mon.mu.RUnlock()

	if err := a.mon.dispatcher.SendTest(host); err != nil {
		writeJSON(w, http.StatusInternalServerError, map[string]string{"error": err.Error()})
		return
	}

	writeJSON(w, http.StatusOK, map[string]any{
		"success": true,
		"message": "Test alert dispatched to configured notification channels successfully",
	})
}

func (a *API) handleEvents(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "method not allowed"})
		return
	}
	limit := 50
	if v := r.URL.Query().Get("limit"); v != "" {
		if n, err := strconv.Atoi(v); err == nil && n > 0 {
			limit = n
		}
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"events": a.mon.events.List(limit),
	})
}

func (a *API) handleHistory(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "method not allowed"})
		return
	}
	hours := 24
	if v := r.URL.Query().Get("hours"); v != "" {
		if n, err := strconv.Atoi(v); err == nil && n > 0 && n <= 168 {
			hours = n
		}
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"points": a.mon.History(time.Duration(hours) * time.Hour),
	})
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}
