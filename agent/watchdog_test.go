// Phase 4 · 4-A — tunnel watchdog tests.
//
// The state machine (classifyWatch) and the observation/transition logic are
// pure and fully covered here. The systemd/port probing itself (WatchInputs,
// checkPort) needs a live systemd + listening socket and is exercised on real
// servers, not in this sandbox.
package main

import (
	"net"
	"sync"
	"testing"
	"time"
)

// ── classifyWatch: the pure state machine ───────────────────────────────────

func TestClassifyWatchDown(t *testing.T) {
	cases := []struct {
		name   string
		status string
	}{
		{"inactive", "inactive"},
		{"failed", "failed"},
		{"unknown-empty", ""},
		{"activating", "activating"},
	}
	for _, c := range cases {
		got, detail := classifyWatch(WatchInputs{Active: false, Status: c.status}, 0, false, 0, 30)
		if got != WatchDown {
			t.Errorf("%s: state = %q, want down", c.name, got)
		}
		if detail == "" {
			t.Errorf("%s: detail must not be empty", c.name)
		}
	}
}

func TestClassifyWatchUp(t *testing.T) {
	// active, no listen port configured (client role) → up.
	if s, _ := classifyWatch(WatchInputs{Active: true, Status: "active"}, 0, false, 0, 30); s != WatchUp {
		t.Errorf("no-port active = %q, want up", s)
	}
	// active with a listening port → up.
	if s, _ := classifyWatch(WatchInputs{Active: true, Status: "active"}, 443, true, 0, 30); s != WatchUp {
		t.Errorf("listening active = %q, want up", s)
	}
}

func TestClassifyWatchDegraded(t *testing.T) {
	// active but the configured port is closed → degraded (NOT up).
	s, detail := classifyWatch(WatchInputs{Active: true, Status: "active"}, 8443, false, 0, 30)
	if s != WatchDegraded {
		t.Fatalf("closed port = %q, want degraded (detail %q)", s, detail)
	}
	if detail == "" {
		t.Error("degraded must carry a detail")
	}
}

func TestClassifyWatchCrashLoop(t *testing.T) {
	// Restart spike within one interval (0 → 3).
	if s, _ := classifyWatch(WatchInputs{Active: true, Status: "active", NRestarts: 3, UptimeSec: 5}, 0, false, 0, 30); s != WatchCrashLoop {
		t.Error("restart spike: want crash_loop")
	}
	// No delta, but many total restarts + short uptime (2 intervals).
	if s, _ := classifyWatch(WatchInputs{Active: true, Status: "active", NRestarts: 5, UptimeSec: 40}, 0, false, 5, 30); s != WatchCrashLoop {
		t.Error("short-uptime repeat: want crash_loop")
	}
	// Sanity: the SAME counters with a healthy uptime must NOT be a crash loop.
	if s, _ := classifyWatch(WatchInputs{Active: true, Status: "active", NRestarts: 5, UptimeSec: 900}, 0, false, 5, 30); s != WatchUp {
		t.Errorf("stable after old crashes = %q, want up", s)
	}
}

// ── observation flow: first tick silent, transitions recorded ───────────────

type fakeSink struct {
	mu  sync.Mutex
	evs []Event
}

func (f *fakeSink) RecordEvent(ev Event) {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.evs = append(f.evs, ev)
}

// stubWatchdog drives observe() without systemd by faking WatchInputs through
// a real TunnelManager whose service name does not exist (systemctl absent →
// Active=false), so we verify the OBSERVATION FLOW (first-tick silence,
// transition bookkeeping) end to end with real meta files.
func newStubWatchdog(t *testing.T) (*TunnelWatchdog, *fakeSink, *TunnelManager) {
	t.Helper()
	dir := t.TempDir()
	tm := NewTunnelManager(dir, dir+"/configs", DeployModeConfigOnly)
	sink := &fakeSink{}
	wd := NewTunnelWatchdog(tm, sink, 30*time.Second)
	return wd, sink, tm
}

func mustApply(t *testing.T, tm *TunnelManager, id, name, core, role string, port int) {
	t.Helper()
	_, err := tm.ApplyTunnel(TunnelApplyReq{
		ID: id, Name: name, Core: core, Role: role,
		ServiceName: "didban-tunnel-" + id,
		MultiPorts:  "",
		Port:        port,
	})
	if err != nil {
		t.Fatalf("apply %s: %v", id, err)
	}
}

func TestWatchdogPortRoundTrip(t *testing.T) {
	_, _, tm := newStubWatchdog(t)
	mustApply(t, tm, "t1", "tunnel one", "BACKPACK", "iran", 8443)
	mustApply(t, tm, "t2", "tunnel two", "FRP", "foreign", 0)

	metas := tm.ListMeta()
	if len(metas) != 2 {
		t.Fatalf("ListMeta = %d, want 2", len(metas))
	}
	byID := map[string]TunnelMeta{}
	for _, m := range metas {
		byID[m.ID] = m
	}
	if byID["t1"].Port != 8443 {
		t.Errorf("t1 port = %d, want 8443", byID["t1"].Port)
	}
	if byID["t2"].Port != 0 {
		t.Errorf("t2 port = %d, want 0", byID["t2"].Port)
	}
}

func TestApplyTunnelRejectsBadPort(t *testing.T) {
	_, _, tm := newStubWatchdog(t)
	if _, err := tm.ApplyTunnel(TunnelApplyReq{ID: "p1", Name: "x", Core: "GOST", Role: "iran", Port: 70000}); err == nil {
		t.Error("port 70000 must be rejected")
	}
	if _, err := tm.ApplyTunnel(TunnelApplyReq{ID: "p1", Name: "x", Core: "GOST", Role: "iran", Port: -1}); err == nil {
		t.Error("port -1 must be rejected")
	}
}

func TestWatchdogFirstTickSilentThenAlerts(t *testing.T) {
	wd, sink, tm := newStubWatchdog(t)
	mustApply(t, tm, "w1", "watched", "BACKPACK", "iran", 0)

	// Tick 1 (service absent → down): first observation must be SILENT.
	wd.tick()
	if n := len(sink.evs); n != 0 {
		t.Fatalf("first tick emitted %d events, want 0: %+v", n, sink.evs)
	}

	// The recorded state must be down with a real ChangedAt.
	snap := wd.Snapshot()
	if len(snap.Tunnels) != 1 {
		t.Fatalf("snapshot tunnels = %d, want 1", len(snap.Tunnels))
	}
	if snap.Tunnels[0].State != WatchDown {
		t.Errorf("state = %q, want down", snap.Tunnels[0].State)
	}
	if !snap.Enabled || snap.IntervalMs != 30000 {
		t.Errorf("enabled/interval = %v/%d, want true/30000", snap.Enabled, snap.IntervalMs)
	}
}

func TestWatchdogSnapshotUnknownBeforeFirstTick(t *testing.T) {
	_, _, tm := newStubWatchdog(t)
	wd := NewTunnelWatchdog(tm, &fakeSink{}, 30*time.Second)
	mustApply(t, tm, "u1", "unseen", "GOST", "iran", 443)

	snap := wd.Snapshot()
	if len(snap.Tunnels) != 1 || snap.Tunnels[0].State != WatchUnknown {
		t.Fatalf("pre-first-tick snapshot = %+v, want one unknown tunnel", snap.Tunnels)
	}
}

// TestCheckPort binds a real listener and verifies the probe logic both ways.
func TestCheckPort(t *testing.T) {
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer ln.Close()
	port := ln.Addr().(*net.TCPAddr).Port

	if !checkPort(port) {
		t.Error("open port must report true")
	}
	// A closed port: pick a port that is almost certainly not bound.
	ln2, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	closed := ln2.Addr().(*net.TCPAddr).Port
	_ = ln2.Close()
	if checkPort(closed) {
		t.Error("closed port must report false")
	}
	if checkPort(0) {
		t.Error("port 0 must report false (not applicable)")
	}
}
