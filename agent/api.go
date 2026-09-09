package main

import (
	"crypto/subtle"
	"encoding/json"
	"net/http"
	"strconv"
	"time"
)

// API serves the authenticated JSON endpoints.
type API struct {
	cfg *Config
	mon *Monitor
}

func newAPI(cfg *Config, mon *Monitor) *API {
	return &API{cfg: cfg, mon: mon}
}

func (a *API) routes() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("/health", a.handleHealth)
	mux.HandleFunc("/api/metrics", a.auth(a.handleMetrics))
	mux.HandleFunc("/api/processes", a.auth(a.handleProcesses))
	mux.HandleFunc("/api/processes/kill", a.auth(a.handleProcessKill))
	mux.HandleFunc("/api/alerts/telegram/test", a.auth(a.handleTelegramTest))
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
		"status":           "ok",
		"version":          version,
		"agent":            "didban",
		"telegram_enabled": a.mon.notifier.IsEnabled(),
		"timestamp":        time.Now().Unix(),
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

	// Also support query parameters: ?pid=1234&signal=SIGTERM
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

func (a *API) handleTelegramTest(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost && r.Method != http.MethodGet {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "method not allowed"})
		return
	}

	if !a.mon.notifier.IsEnabled() {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "Telegram alerts are not configured. Set DIDBAN_TG_TOKEN and DIDBAN_TG_CHAT_ID."})
		return
	}

	a.mon.mu.RLock()
	host := a.mon.snap.Hostname
	a.mon.mu.RUnlock()

	if err := a.mon.notifier.SendTest(host); err != nil {
		writeJSON(w, http.StatusInternalServerError, map[string]string{"error": err.Error()})
		return
	}

	writeJSON(w, http.StatusOK, map[string]any{
		"success": true,
		"message": "Telegram test alert sent successfully",
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
