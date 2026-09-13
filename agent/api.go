package main

import (
	"crypto/sha256"
	"crypto/subtle"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"strconv"
	"time"
)

// API serves the authenticated JSON endpoints and public status page.
type API struct {
	cfg *Config
	mon *Monitor
	tm  *TunnelManager
}

func newAPI(cfg *Config, mon *Monitor, tm *TunnelManager) *API {
	return &API{cfg: cfg, mon: mon, tm: tm}
}

func (a *API) routes() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("/health", a.handleHealth)
	mux.HandleFunc("/status", a.handleStatusPage)
	mux.HandleFunc("/api/status", a.handleStatusPage)
	mux.HandleFunc("/api/metrics", a.auth(a.handleMetrics))
	mux.HandleFunc("/api/processes", a.auth(a.handleProcesses))
	mux.HandleFunc("/api/processes/kill", a.auth(a.handleProcessKill))
	mux.HandleFunc("/api/network/sockets", a.auth(a.handleNetworkSockets))

	// Real bandwidth test (streaming download / upload sink)
	mux.HandleFunc("/api/bandwidth/download", a.auth(a.handleBandwidthDownload))
	mux.HandleFunc("/api/bandwidth/upload", a.auth(a.handleBandwidthUpload))
	mux.HandleFunc("/api/docker/containers", a.auth(a.handleDockerContainers))
	mux.HandleFunc("/api/docker/restart", a.auth(a.handleDockerRestart))
	mux.HandleFunc("/api/docker/stop", a.auth(a.handleDockerStop))

	// Tunnel Management APIs (Smite / Marzban style auto-orchestration)
	mux.HandleFunc("/api/tunnel/apply", a.auth(a.handleTunnelApply))
	mux.HandleFunc("/api/tunnel/start", a.auth(a.handleTunnelStart))
	mux.HandleFunc("/api/tunnel/stop", a.auth(a.handleTunnelStop))
	mux.HandleFunc("/api/tunnel/restart", a.auth(a.handleTunnelRestart))
	mux.HandleFunc("/api/tunnel/delete", a.auth(a.handleTunnelDelete))
	mux.HandleFunc("/api/tunnel/status", a.auth(a.handleTunnelStatus))
	mux.HandleFunc("/api/tunnel/list", a.auth(a.handleTunnelList))

	mux.HandleFunc("/api/alerts/telegram/test", a.auth(a.handleAlertsTest))
	mux.HandleFunc("/api/alerts/test", a.auth(a.handleAlertsTest))
	mux.HandleFunc("/api/events", a.auth(a.handleEvents))
	mux.HandleFunc("/api/history", a.auth(a.handleHistory))
	return mux
}

// auth wraps a handler with bearer-token authentication (constant time).
func (a *API) auth(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		tok := r.Header.Get("Authorization")
		if len(tok) > 7 && tok[:7] == "Bearer " {
			tok = tok[7:]
		} else {
			tok = r.URL.Query().Get("token")
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
	_ = json.NewDecoder(r.Body).Decode(&req)
	if req.ID == "" {
		req.ID = r.URL.Query().Get("id")
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
	_ = json.NewDecoder(r.Body).Decode(&req)
	if req.ID == "" {
		req.ID = r.URL.Query().Get("id")
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
	// Cap the request body so a (stolen) token cannot be used to exhaust memory.
	r.Body = http.MaxBytesReader(w, r.Body, 2*1024*1024)
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid json: " + err.Error()})
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
	_ = json.NewDecoder(r.Body).Decode(&req)
	if req.ID == "" {
		req.ID = r.URL.Query().Get("id")
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
	_ = json.NewDecoder(r.Body).Decode(&req)
	if req.ID == "" {
		req.ID = r.URL.Query().Get("id")
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
	_ = json.NewDecoder(r.Body).Decode(&req)
	if req.ID == "" {
		req.ID = r.URL.Query().Get("id")
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
	_ = json.NewDecoder(r.Body).Decode(&req)
	if req.ID == "" {
		req.ID = r.URL.Query().Get("id")
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

func (a *API) handleProcessKill(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost && r.Method != http.MethodDelete {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "method not allowed (POST or DELETE required)"})
		return
	}

	var req killRequest
	if r.Header.Get("Content-Type") == "application/json" || r.Body != nil {
		_ = json.NewDecoder(r.Body).Decode(&req)
	}

	if req.PID == 0 {
		if p, err := strconv.Atoi(r.URL.Query().Get("pid")); err == nil {
			req.PID = p
		}
	}
	if req.Signal == "" {
		req.Signal = r.URL.Query().Get("signal")
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
