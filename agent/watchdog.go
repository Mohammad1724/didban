// Phase 4 · 4-A — tunnel watchdog.
//
// The app-side fleet page only knows tunnel state while the phone is open.
// This watchdog closes that blind spot: a goroutine inside the agent checks
// every registered tunnel service at a fixed interval and alerts (through
// the existing AlertDispatcher via Monitor.RecordEvent) on STATE CHANGES:
//
//	up → down         (service stopped / failed)
//	up → degraded     (service alive, but its listen port is not accepting)
//	up → crash_loop   (systemd restarts spiking)
//	*  → up           (recovery, from down/degraded/crash_loop)
//
// The first observation after an agent start is silent (no alert) so an
// intentionally-stopped tunnel does not re-alert on every agent restart —
// the same convention as the docker container monitor.
package main

import (
	"context"
	"fmt"
	"net"
	"sync"
	"time"
)

// WatchState is the watchdog's per-tunnel classification.
type WatchState string

const (
	WatchUnknown   WatchState = "unknown"    // not yet observed
	WatchUp        WatchState = "up"         // active and (if known) listening
	WatchDown      WatchState = "down"       // service not active
	WatchDegraded  WatchState = "degraded"   // active, but listen port closed
	WatchCrashLoop WatchState = "crash_loop" // active, but restarts are spiking
)

const (
	// portDialTimeout bounds the local listen check per tunnel per tick.
	portDialTimeout = 1500 * time.Millisecond
	// maxTransitionHistory caps the in-memory transition ring.
	maxTransitionHistory = 50
	// crashLoopRestartWindow: restarts accumulated within ONE interval.
	crashLoopRestartDelta = 2
	// crashLoopTotalRestarts / crashLoopUptimeSec: a unit that has restarted
	// at least this many times AND was (re)started within 2 watchdog intervals
	// is still in its crash loop.
	crashLoopTotalRestarts = 3
)

// TunnelTransition is one recorded state change (newest last).
type TunnelTransition struct {
	ID     string     `json:"id"`
	Name   string     `json:"name"`
	From   WatchState `json:"from"`
	To     WatchState `json:"to"`
	At     time.Time  `json:"at"`
	Detail string     `json:"detail,omitempty"`
}

// TunnelWatchStatus is the current per-tunnel watchdog view (API payload).
type TunnelWatchStatus struct {
	ID        string     `json:"id"`
	Name      string     `json:"name"`
	Core      string     `json:"core"`
	Role      string     `json:"role"`
	State     WatchState `json:"state"`
	Active    bool       `json:"active"`
	Port      int        `json:"port"`    // 0 = this role does not listen
	PortOK    bool       `json:"port_ok"` // false when port==0 (not applicable)
	NRestarts int        `json:"n_restarts"`
	UptimeSec int        `json:"uptime_sec"`
	ChangedAt time.Time  `json:"changed_at"`
	Detail    string     `json:"detail,omitempty"`
}

// WatchdogSnapshot is the /api/tunnel/watchdog payload.
type WatchdogSnapshot struct {
	Enabled     bool                `json:"enabled"`
	IntervalMs  int64               `json:"interval_ms"`
	Tunnels     []TunnelWatchStatus `json:"tunnels"`
	Transitions []TunnelTransition  `json:"transitions"`
}

// EventSink is the alert/audit path (satisfied by *Monitor).
type EventSink interface {
	RecordEvent(ev Event)
}

type tunnelWatchRec struct {
	state        WatchState
	prevRestarts int
	changedAt    time.Time
	detail       string
	status       TunnelWatchStatus
}

// TunnelWatchdog continuously classifies every registered tunnel service.
type TunnelWatchdog struct {
	tm       *TunnelManager
	sink     EventSink
	interval time.Duration

	mu          sync.Mutex
	recs        map[string]*tunnelWatchRec // tunnel id -> rec
	transitions []TunnelTransition
}

// NewTunnelWatchdog builds the watchdog. interval is clamped by the caller
// (main.go enforces 5..600s).
func NewTunnelWatchdog(tm *TunnelManager, sink EventSink, interval time.Duration) *TunnelWatchdog {
	return &TunnelWatchdog{
		tm:       tm,
		sink:     sink,
		interval: interval,
		recs:     make(map[string]*tunnelWatchRec),
	}
}

// classifyWatch is the pure state machine (fully unit-tested). prevRestarts
// is the NRestarts seen on the previous tick (0 on first observation).
func classifyWatch(in WatchInputs, port int, portOK bool, prevRestarts int, intervalSec int) (WatchState, string) {
	if !in.Active {
		if in.Status == "" || in.Status == "inactive" {
			return WatchDown, "service is stopped"
		}
		return WatchDown, "service status: " + in.Status
	}
	if in.NRestarts-prevRestarts >= crashLoopRestartDelta {
		return WatchCrashLoop, fmt.Sprintf("restart spike: %d → %d restarts in one interval", prevRestarts, in.NRestarts)
	}
	if in.NRestarts >= crashLoopTotalRestarts && intervalSec > 0 && in.UptimeSec < 2*intervalSec {
		return WatchCrashLoop, fmt.Sprintf("%d restarts, alive only %ds", in.NRestarts, in.UptimeSec)
	}
	if port > 0 && !portOK {
		return WatchDegraded, fmt.Sprintf("service active but port %d is not listening", port)
	}
	return WatchUp, "service active"
}

// checkPort probes 127.0.0.1:port (a 0.0.0.0 listener always accepts on
// loopback). Returns false when the dial fails or times out.
func checkPort(port int) bool {
	if port <= 0 {
		return false
	}
	conn, err := net.DialTimeout("tcp", fmt.Sprintf("127.0.0.1:%d", port), portDialTimeout)
	if err != nil {
		return false
	}
	_ = conn.Close()
	return true
}

// Run ticks until the context is cancelled.
func (w *TunnelWatchdog) Run(ctx context.Context) {
	t := time.NewTicker(w.interval)
	defer t.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-t.C:
			w.tick()
		}
	}
}

func (w *TunnelWatchdog) tick() {
	intervalSec := int(w.interval.Seconds())
	for _, meta := range w.tm.ListMeta() {
		w.observe(meta, intervalSec)
	}
}

func (w *TunnelWatchdog) observe(meta TunnelMeta, intervalSec int) {
	in := w.tm.WatchInputs(meta.ServiceName)
	portOK := checkPort(meta.Port)

	w.mu.Lock()
	prev, known := w.recs[meta.ID]
	w.mu.Unlock()

	state, detail := classifyWatch(in, meta.Port, portOK, 0, intervalSec)
	if known {
		state, detail = classifyWatch(in, meta.Port, portOK, prev.prevRestarts, intervalSec)
	}

	// Capture the FROM state BEFORE the rec is mutated below.
	var from WatchState
	if known {
		from = prev.state
	}

	st := TunnelWatchStatus{
		ID:        meta.ID,
		Name:      meta.Name,
		Core:      meta.Core,
		Role:      meta.Role,
		State:     state,
		Active:    in.Active,
		Port:      meta.Port,
		PortOK:    portOK,
		NRestarts: in.NRestarts,
		UptimeSec: in.UptimeSec,
		Detail:    detail,
	}

	changed := !known || (from != state)
	if changed {
		now := time.Now()
		st.ChangedAt = now
	} else if known {
		st.ChangedAt = prev.changedAt
	}

	w.mu.Lock()
	if !known {
		w.recs[meta.ID] = &tunnelWatchRec{
			state: state, prevRestarts: in.NRestarts,
			changedAt: time.Now(), detail: detail, status: st,
		}
	} else {
		prev.state = state
		prev.prevRestarts = in.NRestarts
		if changed {
			prev.changedAt = st.ChangedAt
			prev.detail = detail
		}
		prev.status = st
	}
	w.mu.Unlock()

	// First observation is silent (startup re-alerts would be noise for
	// intentionally stopped tunnels); every subsequent change is alerted.
	if known && changed {
		if w.sink != nil {
			w.sink.RecordEvent(Event{
				Time: st.ChangedAt,
				Type: "tunnel_" + string(state),
				Detail: fmt.Sprintf("tunnel %q (%s/%s, %s) %s → %s: %s",
					meta.Name, meta.Core, meta.Role, meta.ServiceName,
					from, state, detail),
			})
		}
		tr := TunnelTransition{
			ID: meta.ID, Name: meta.Name,
			From: from, To: state,
			At: st.ChangedAt, Detail: detail,
		}
		w.mu.Lock()
		w.transitions = append(w.transitions, tr)
		if len(w.transitions) > maxTransitionHistory {
			w.transitions = w.transitions[len(w.transitions)-maxTransitionHistory:]
		}
		w.mu.Unlock()
	}
}

// Snapshot returns the current watchdog view for the API.
func (w *TunnelWatchdog) Snapshot() WatchdogSnapshot {
	w.mu.Lock()
	defer w.mu.Unlock()
	snap := WatchdogSnapshot{
		Enabled:    true,
		IntervalMs: int64(w.interval / time.Millisecond),
	}
	for _, meta := range w.tm.ListMeta() {
		if rec, ok := w.recs[meta.ID]; ok {
			snap.Tunnels = append(snap.Tunnels, rec.status)
		} else {
			// Registered but not yet observed (first tick pending).
			snap.Tunnels = append(snap.Tunnels, TunnelWatchStatus{
				ID: meta.ID, Name: meta.Name, Core: meta.Core, Role: meta.Role,
				State: WatchUnknown, Port: meta.Port, PortOK: false,
			})
		}
	}
	snap.Transitions = append([]TunnelTransition(nil), w.transitions...)
	return snap
}
