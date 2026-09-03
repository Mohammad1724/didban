package main

import (
	"context"
	"crypto/tls"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"
)

// ── PasarGuard panel monitoring ─────────────────────────────────────────────
//
// Optionally, the agent watches a PasarGuard (Marzban-family) panel API and
// reports version, user stats and node statuses. Node status transitions are
// recorded as events — the mobile equivalent of the "#Error_Node /
// #Recovered_Node" Telegram messages.

type PanelNode struct {
	ID       int    `json:"id"`
	Name     string `json:"name"`
	Status   string `json:"status"`
	Uplink   int64  `json:"uplink"`
	Downlink int64  `json:"downlink"`
}

type PanelState struct {
	Configured   bool        `json:"configured"`
	OK           bool        `json:"ok"`
	Version      string      `json:"version"`
	UptimeSec    int64       `json:"uptime_seconds"`
	TotalUsers   int         `json:"total_user"`
	OnlineUsers  int         `json:"online_users"`
	ActiveUsers  int         `json:"active_users"`
	ExpiredUsers int         `json:"expired_users"`
	LimitedUsers int         `json:"limited_users"`
	InBand       int64       `json:"incoming_bandwidth"`
	OutBand      int64       `json:"outgoing_bandwidth"`
	Nodes        []PanelNode `json:"nodes"`
	LastError    string      `json:"last_error,omitempty"`
	UpdatedAt    time.Time   `json:"updated_at"`
}

type sysStats struct {
	Version           string `json:"version"`
	UptimeSeconds     int64  `json:"uptime_seconds"`
	TotalUser         int    `json:"total_user"`
	OnlineUsers       int    `json:"online_users"`
	ActiveUsers       int    `json:"active_users"`
	ExpiredUsers      int    `json:"expired_users"`
	LimitedUsers      int    `json:"limited_users"`
	IncomingBandwidth int64  `json:"incoming_bandwidth"`
	OutgoingBandwidth int64  `json:"outgoing_bandwidth"`
}

type nodesResponse struct {
	Nodes []PanelNode `json:"nodes"`
}

type PanelMonitor struct {
	cfg    *Config
	events *EventLog
	mu     sync.RWMutex
	state  PanelState
	token  string
	client *http.Client

	prevNodeStatus map[int]string
	panelWasOK     bool
	firstPoll      bool
}

func NewPanelMonitor(cfg *Config, events *EventLog) *PanelMonitor {
	transport := &http.Transport{}
	if cfg.PanelInsecure {
		transport.TLSClientConfig = &tls.Config{InsecureSkipVerify: true} //nolint:gosec
	}
	return &PanelMonitor{
		cfg:            cfg,
		events:         events,
		prevNodeStatus: make(map[int]string),
		firstPoll:      true,
		client:         &http.Client{Timeout: 15 * time.Second, Transport: transport},
	}
}

// Configured reports whether panel monitoring is enabled.
func (pm *PanelMonitor) Configured() bool {
	return pm.cfg.PanelURL != "" && pm.cfg.PanelUser != ""
}

// Run polls the panel every 60 seconds until ctx is cancelled.
func (pm *PanelMonitor) Run(ctx context.Context) {
	if !pm.Configured() {
		return
	}
	ticker := time.NewTicker(60 * time.Second)
	defer ticker.Stop()
	pm.poll()
	for {
		select {
		case <-ticker.C:
			pm.poll()
		case <-ctx.Done():
			return
		}
	}
}

// State returns a copy of the current panel state.
func (pm *PanelMonitor) State() PanelState {
	pm.mu.RLock()
	defer pm.mu.RUnlock()
	s := pm.state
	s.Nodes = append([]PanelNode(nil), pm.state.Nodes...)
	return s
}

func (pm *PanelMonitor) login() error {
	form := url.Values{}
	form.Set("grant_type", "password")
	form.Set("username", pm.cfg.PanelUser)
	form.Set("password", pm.cfg.PanelPass)
	resp, err := pm.client.Post(strings.TrimRight(pm.cfg.PanelURL, "/")+"/api/admin/token",
		"application/x-www-form-urlencoded", strings.NewReader(form.Encode()))
	if err != nil {
		return fmt.Errorf("login: %w", err)
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(io.LimitReader(resp.Body, 1<<20))
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("login: HTTP %d", resp.StatusCode)
	}
	var tok struct {
		AccessToken string `json:"access_token"`
	}
	if err := json.Unmarshal(body, &tok); err != nil || tok.AccessToken == "" {
		return fmt.Errorf("login: bad token response")
	}
	pm.token = tok.AccessToken
	return nil
}

func (pm *PanelMonitor) apiGet(path string, out any) error {
	target := strings.TrimRight(pm.cfg.PanelURL, "/") + path
	try := func() (*http.Response, error) {
		req, err := http.NewRequest(http.MethodGet, target, nil)
		if err != nil {
			return nil, err
		}
		req.Header.Set("Authorization", "Bearer "+pm.token)
		return pm.client.Do(req)
	}
	resp, err := try()
	if err != nil {
		return err
	}
	if resp.StatusCode == http.StatusUnauthorized {
		resp.Body.Close()
		if err := pm.login(); err != nil {
			return err
		}
		if resp, err = try(); err != nil {
			return err
		}
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(io.LimitReader(resp.Body, 4<<20))
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("GET %s: HTTP %d", path, resp.StatusCode)
	}
	return json.Unmarshal(body, out)
}

func (pm *PanelMonitor) poll() {
	var sys sysStats
	var nodes nodesResponse

	err := pm.apiGet("/api/system", &sys)
	if err == nil {
		err = pm.apiGet("/api/nodes", &nodes)
	}

	pm.mu.Lock()
	now := time.Now()
	if err != nil {
		was := pm.state.OK
		pm.state.OK = false
		pm.state.LastError = err.Error()
		pm.state.UpdatedAt = now
		pm.mu.Unlock()
		if was || pm.firstPoll {
			pm.recordEvent("panel_down", 0, err.Error())
		}
		pm.firstPoll = false
		return
	}

	wasOK := pm.state.OK
	pm.state = PanelState{
		Configured:   true,
		OK:           true,
		Version:      sys.Version,
		UptimeSec:    sys.UptimeSeconds,
		TotalUsers:   sys.TotalUser,
		OnlineUsers:  sys.OnlineUsers,
		ActiveUsers:  sys.ActiveUsers,
		ExpiredUsers: sys.ExpiredUsers,
		LimitedUsers: sys.LimitedUsers,
		InBand:       sys.IncomingBandwidth,
		OutBand:      sys.OutgoingBandwidth,
		Nodes:        nodes.Nodes,
		UpdatedAt:    now,
	}
	currentStatus := make(map[int]string, len(nodes.Nodes))
	for _, n := range nodes.Nodes {
		currentStatus[n.ID] = n.Status
	}
	pm.mu.Unlock()

	if !wasOK && !pm.firstPoll {
		pm.recordEvent("panel_up", 0, "")
	}

	// Node status transitions → events
	for _, n := range nodes.Nodes {
		prev, known := pm.prevNodeStatus[n.ID]
		if known && prev != n.Status {
			if nodeIsUp(n.Status) && !nodeIsUp(prev) {
				pm.recordEvent("node_up", 0, n.Name)
			} else if !nodeIsUp(n.Status) && nodeIsUp(prev) {
				pm.recordEvent("node_down", 0, fmt.Sprintf("%s (%s)", n.Name, n.Status))
			}
		}
	}
	pm.prevNodeStatus = currentStatus
	pm.firstPoll = false
}

func nodeIsUp(status string) bool {
	return status == "connected" || status == "connecting"
}

func (pm *PanelMonitor) recordEvent(typ string, value float64, detail string) {
	if pm.events == nil {
		return
	}
	pm.events.Add(Event{
		Time:   time.Now(),
		Type:   typ,
		Value:  value,
		Detail: detail,
	})
}
