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
	cfg   *Config
	mon   *Monitor
	panel *PanelMonitor
}

func newAPI(cfg *Config, mon *Monitor, panel *PanelMonitor) *API {
	return &API{cfg: cfg, mon: mon, panel: panel}
}

func (a *API) routes() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("/health", a.handleHealth)
	mux.HandleFunc("/api/metrics", a.auth(a.handleMetrics))
	mux.HandleFunc("/api/processes", a.auth(a.handleProcesses))
	mux.HandleFunc("/api/events", a.auth(a.handleEvents))
	mux.HandleFunc("/api/history", a.auth(a.handleHistory))
	mux.HandleFunc("/api/panel", a.auth(a.handlePanel))
	return mux
}

// auth wraps a handler with bearer-token authentication (constant time).
func (a *API) auth(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet {
			writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "method not allowed"})
			return
		}
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
	writeJSON(w, http.StatusOK, map[string]any{
		"status":    "ok",
		"version":   version,
		"agent":     "didban",
		"timestamp": time.Now().Unix(),
	})
}

func (a *API) handleMetrics(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, a.mon.Snapshot())
}

func (a *API) handleProcesses(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]any{
		"processes": a.mon.Procs(),
	})
}

func (a *API) handleEvents(w http.ResponseWriter, r *http.Request) {
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

func (a *API) handlePanel(w http.ResponseWriter, r *http.Request) {
	if a.panel == nil {
		writeJSON(w, http.StatusOK, map[string]any{"configured": false, "ok": false})
		return
	}
	writeJSON(w, http.StatusOK, a.panel.State())
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}
