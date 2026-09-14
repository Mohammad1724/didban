package main

import (
	"bytes"
	"crypto/tls"
	"encoding/json"
	"fmt"
	"net"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"sync"
	"testing"
	"time"
)

// ── Spec validation ─────────────────────────────────────────────────────────

func TestNormalizeProbeSpec_Valid(t *testing.T) {
	cases := []struct {
		in   ProbeTargetSpec
		sch  string
		path string
	}{
		{ProbeTargetSpec{Name: "my site", Mode: ProbeTCP, Host: "example.com", Port: 443}, "", ""},
		{ProbeTargetSpec{Name: "x", Mode: ProbeHTTP, Host: "example.com", Port: 80}, "", ""}, // auto scheme resolved at probe time
		{ProbeTargetSpec{Name: "x", Mode: ProbeHTTP, Host: "example.com", Port: 443, Scheme: "https"}, "https", ""},
		{ProbeTargetSpec{Name: "x", Mode: ProbeHTTP, Host: "example.com", Port: 8080, Path: "health"}, "", "/health"},
	}
	for i, c := range cases {
		got, err := normalizeProbeSpec(c.in)
		if err != nil {
			t.Fatalf("case %d: unexpected error: %v", i, err)
		}
		if got.Scheme != c.sch || got.Path != c.path {
			t.Fatalf("case %d: scheme/path = %q/%q, want %q/%q", i, got.Scheme, got.Path, c.sch, c.path)
		}
	}
}

func TestNormalizeProbeSpec_Invalid(t *testing.T) {
	cases := []struct {
		name string
		in   ProbeTargetSpec
	}{
		{"empty name", ProbeTargetSpec{Name: "  ", Mode: ProbeTCP, Host: "h", Port: 1}},
		{"name too long", ProbeTargetSpec{Name: strings.Repeat("a", 65), Mode: ProbeTCP, Host: "h", Port: 1}},
		{"empty host", ProbeTargetSpec{Name: "n", Mode: ProbeTCP, Host: "", Port: 1}},
		{"host too long", ProbeTargetSpec{Name: "n", Mode: ProbeTCP, Host: strings.Repeat("a", 254), Port: 1}},
		{"port zero", ProbeTargetSpec{Name: "n", Mode: ProbeTCP, Host: "h", Port: 0}},
		{"port huge", ProbeTargetSpec{Name: "n", Mode: ProbeTCP, Host: "h", Port: 70000}},
		{"bad mode", ProbeTargetSpec{Name: "n", Mode: "icmp", Host: "h", Port: 1}},
		{"bad scheme", ProbeTargetSpec{Name: "n", Mode: ProbeHTTP, Host: "h", Port: 80, Scheme: "ftp"}},
		{"path too long", ProbeTargetSpec{Name: "n", Mode: ProbeHTTP, Host: "h", Port: 80, Path: "/" + strings.Repeat("a", 300)}},
	}
	for _, c := range cases {
		if _, err := normalizeProbeSpec(c.in); err == nil {
			t.Fatalf("%s: expected error, got nil", c.name)
		}
	}
}

// TCP mode must strip http-only fields.
func TestNormalizeProbeSpec_TCPStripsHTTPFields(t *testing.T) {
	got, err := normalizeProbeSpec(ProbeTargetSpec{
		Name: "n", Mode: ProbeTCP, Host: "h", Port: 22, Scheme: "https", Path: "/x",
	})
	if err != nil {
		t.Fatal(err)
	}
	if got.Scheme != "" || got.Path != "" {
		t.Fatalf("tcp kept http fields: scheme=%q path=%q", got.Scheme, got.Path)
	}
}

// ── Probe I/O ───────────────────────────────────────────────────────────────

// startTCPLisener returns a TCP listener plus its "host:port" and a stop func.
func startTCPLisener(t *testing.T) (addr string, stop func()) {
	t.Helper()
	l, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("listen: %v", err)
	}
	// Accept loop so dials complete; it exits when the listener is closed.
	go func() {
		for {
			c, err := l.Accept()
			if err != nil {
				return
			}
			_ = c.Close()
		}
	}()
	// stop must be SYNCHRONOUS: a probe racing an async close would still
	// see the port open (that flake burned us once).
	return l.Addr().String(), func() { _ = l.Close() }
}

func TestProbeOnce_TCP(t *testing.T) {
	addr, stop := startTCPLisener(t)
	defer stop()
	host, portStr, _ := net.SplitHostPort(addr)
	port := 0
	fmt.Sscanf(portStr, "%d", &port)

	res := probeOnce(ProbeTargetSpec{Name: "x", Mode: ProbeTCP, Host: host, Port: port})
	if !res.Up {
		t.Fatalf("open port: want up, got %v (detail=%s)", res, res.Detail)
	}
	if res.LatencyMs < 0 {
		t.Fatalf("negative latency: %d", res.LatencyMs)
	}
}

func TestProbeOnce_TCPClosedPort(t *testing.T) {
	// Grab a port, close it, probe it.
	l, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	addr := l.Addr().String()
	_ = l.Close()
	host, portStr, _ := net.SplitHostPort(addr)
	port := 0
	fmt.Sscanf(portStr, "%d", &port)

	res := probeOnce(ProbeTargetSpec{Name: "x", Mode: ProbeTCP, Host: host, Port: port})
	if res.Up {
		t.Fatal("closed port: want down, got up")
	}
	if res.Detail == "" {
		t.Fatal("closed port: expected a non-empty detail")
	}
}

func TestProbeOnce_HTTP(t *testing.T) {
	// 200 → up
	srv200 := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprint(w, "ok")
	}))
	defer srv200.Close()
	h, p, _ := splitHostPort(t, srv200.URL)
	res := probeOnce(ProbeTargetSpec{Name: "x", Mode: ProbeHTTP, Host: h, Port: p, Scheme: "http"})
	if !res.Up || res.Detail != "HTTP 200" {
		t.Fatalf("200: want up/HTTP 200, got up=%v detail=%q", res.Up, res.Detail)
	}

	// 500 → down (with latency measured)
	srv500 := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.Error(w, "boom", http.StatusInternalServerError)
	}))
	defer srv500.Close()
	h, p, _ = splitHostPort(t, srv500.URL)
	res = probeOnce(ProbeTargetSpec{Name: "x", Mode: ProbeHTTP, Host: h, Port: p, Scheme: "http"})
	if res.Up || res.Detail != "HTTP 500" {
		t.Fatalf("500: want down/HTTP 500, got up=%v detail=%q", res.Up, res.Detail)
	}
}

func TestProbeOnce_HTTPS(t *testing.T) {
	srv := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprint(w, "secure")
	}))
	defer srv.Close()
	h, p, _ := splitHostPort(t, srv.URL)

	// Swap the client for one that trusts the test certificate.
	old := probeHTTPClient
	probeHTTPClient = func() *http.Client {
		tr := &http.Transport{
			TLSClientConfig: &tls.Config{InsecureSkipVerify: true}, // #nosec G402 — test cert only
		}
		return &http.Client{Timeout: probeHTTPTimeout, Transport: tr}
	}
	defer func() { probeHTTPClient = old }()

	res := probeOnce(ProbeTargetSpec{Name: "x", Mode: ProbeHTTP, Host: h, Port: p, Scheme: "https"})
	if !res.Up || res.Detail != "HTTP 200" {
		t.Fatalf("https: want up/HTTP 200, got up=%v detail=%q", res.Up, res.Detail)
	}
}

func TestProbeOnce_HTTPUnreachable(t *testing.T) {
	l, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	addr := l.Addr().String()
	_ = l.Close()
	host, portStr, _ := net.SplitHostPort(addr)
	port := 0
	fmt.Sscanf(portStr, "%d", &port)

	res := probeOnce(ProbeTargetSpec{Name: "x", Mode: ProbeHTTP, Host: host, Port: port, Scheme: "http"})
	if res.Up {
		t.Fatal("unreachable: want down, got up")
	}
}

func splitHostPort(t *testing.T, urlStr string) (string, int, string) {
	t.Helper()
	urlStr = strings.TrimPrefix(urlStr, "https://")
	urlStr = strings.TrimPrefix(urlStr, "http://")
	host, portStr, err := net.SplitHostPort(urlStr)
	if err != nil {
		t.Fatalf("split %q: %v", urlStr, err)
	}
	port := 0
	fmt.Sscanf(portStr, "%d", &port)
	return host, port, urlStr
}

// ── State machine ───────────────────────────────────────────────────────────

type recordingSink struct {
	mu     sync.Mutex
	events []Event
}

func (r *recordingSink) RecordEvent(ev Event) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.events = append(r.events, ev)
}

func (r *recordingSink) types() []string {
	r.mu.Lock()
	defer r.mu.Unlock()
	out := make([]string, 0, len(r.events))
	for _, e := range r.events {
		out = append(out, e.Type)
	}
	return out
}

func newTestProbe(t *testing.T, sink EventSink) (*ProbeMonitor, func()) {
	t.Helper()
	dir := t.TempDir()
	pm := NewProbeMonitor(dir, sink, time.Minute)
	return pm, func() {}
}

// Target flipping between up and down exercises the full alert path:
// silent first observation, alert on down, alert on up, transition ring.
func TestProbeStateMachine_AlertPath(t *testing.T) {
	sink := &recordingSink{}
	pm, _ := newTestProbe(t, sink)

	// Up: listener open.
	addr, stop := startTCPLisener(t)
	host, portStr, _ := net.SplitHostPort(addr)
	port := 0
	fmt.Sscanf(portStr, "%d", &port)
	spec := ProbeTargetSpec{Name: "flip", Mode: ProbeTCP, Host: host, Port: port}
	if err := pm.SetTargets([]ProbeTargetSpec{spec}); err != nil {
		t.Fatal(err)
	}

	// First observation: up, SILENT.
	if _, ok := pm.ProbeNow("flip"); !ok {
		t.Fatal("ProbeNow: unknown target")
	}
	if got := sink.types(); len(got) != 0 {
		t.Fatalf("first observation must be silent, got %v", got)
	}
	snap := pm.Snapshot()
	if len(snap.Points) != 1 {
		t.Fatalf("snapshot points = %d, want 1", len(snap.Points))
	}
	pt := snap.Points[0]
	if pt.State != ProbeUp || !pt.Observed {
		t.Fatalf("after first probe: state=%q observed=%v, want up/true", pt.State, pt.Observed)
	}

	// Down: stop the listener.
	stop()
	if _, ok := pm.ProbeNow("flip"); !ok {
		t.Fatal("ProbeNow: unknown target")
	}
	if got := sink.types(); len(got) != 1 || got[0] != "probe_down" {
		t.Fatalf("after down: events=%v, want [probe_down]", got)
	}

	// Back up: a new listener on the same port? Ports are not reusable on
	// demand, so register a second spec? No — the spec is bound to the port.
	// Instead: re-open on the same port is not portable. Assert the down
	// snapshot, then verify recovery via a second target on a fresh listener.
	snap = pm.Snapshot()
	if snap.Points[0].State != ProbeDown {
		t.Fatalf("after down: state=%q, want down", snap.Points[0].State)
	}
	if len(snap.Points[0].Transitions) != 1 {
		t.Fatalf("transitions = %d, want 1", len(snap.Points[0].Transitions))
	}
	if snap.Points[0].Transitions[0].From != ProbeUp || snap.Points[0].Transitions[0].To != ProbeDown {
		t.Fatalf("transition = %+v, want up→down", snap.Points[0].Transitions[0])
	}

	// Recovery: swap in a target on a fresh (open) listener and verify
	// down→up alerts.
	addr2, stop2 := startTCPLisener(t)
	defer stop2()
	host2, portStr2, _ := net.SplitHostPort(addr2)
	port2 := 0
	fmt.Sscanf(portStr2, "%d", &port2)
	spec2 := ProbeTargetSpec{Name: "flip", Mode: ProbeTCP, Host: host2, Port: port2}
	if err := pm.SetTargets([]ProbeTargetSpec{spec2}); err != nil {
		t.Fatal(err)
	}
	// SetTargets replaced the rec set? No — same name, rec kept: state is
	// still down. Probe now → up → alert.
	if _, ok := pm.ProbeNow("flip"); !ok {
		t.Fatal("ProbeNow: unknown target")
	}
	if got := sink.types(); len(got) != 2 || got[1] != "probe_up" {
		t.Fatalf("after recovery: events=%v, want [probe_down probe_up]", got)
	}
	snap = pm.Snapshot()
	if len(snap.Points[0].Transitions) != 2 {
		t.Fatalf("transitions = %d, want 2", len(snap.Points[0].Transitions))
	}
	if snap.Points[0].Transitions[1].From != ProbeDown || snap.Points[0].Transitions[1].To != ProbeUp {
		t.Fatalf("recovery transition = %+v, want down→up", snap.Points[0].Transitions[1])
	}
}

// Removing a target drops its record (and re-adding re-silences).
func TestProbeRemoveTarget_DropsRecord(t *testing.T) {
	sink := &recordingSink{}
	pm, _ := newTestProbe(t, sink)

	addr, stop := startTCPLisener(t)
	defer stop()
	host, portStr, _ := net.SplitHostPort(addr)
	port := 0
	fmt.Sscanf(portStr, "%d", &port)
	spec := ProbeTargetSpec{Name: "gone", Mode: ProbeTCP, Host: host, Port: port}
	if err := pm.SetTargets([]ProbeTargetSpec{spec}); err != nil {
		t.Fatal(err)
	}
	pm.ProbeNow("gone")

	if err := pm.SetTargets(nil); err != nil {
		t.Fatal(err)
	}
	snap := pm.Snapshot()
	if len(snap.Points) != 0 {
		t.Fatalf("points after removal = %d, want 0", len(snap.Points))
	}

	// Re-add the same target: first observation is silent again.
	if err := pm.SetTargets([]ProbeTargetSpec{spec}); err != nil {
		t.Fatal(err)
	}
	if _, ok := pm.ProbeNow("gone"); !ok {
		t.Fatal("ProbeNow: unknown target")
	}
	if got := sink.types(); len(got) != 0 {
		t.Fatalf("re-added target must be silent on first observation, got %v", got)
	}
}

// History and transition rings are bounded.
func TestProbeHistoryAndTransitionCaps(t *testing.T) {
	pm, _ := newTestProbe(t, nil)

	addr, stop := startTCPLisener(t)
	defer stop()
	host, portStr, _ := net.SplitHostPort(addr)
	port := 0
	fmt.Sscanf(portStr, "%d", &port)
	spec := ProbeTargetSpec{Name: "cap", Mode: ProbeTCP, Host: host, Port: port}
	if err := pm.SetTargets([]ProbeTargetSpec{spec}); err != nil {
		t.Fatal(err)
	}

	// 40 probes, all up: history capped, no transitions.
	for i := 0; i < 40; i++ {
		pm.observe(spec, ProbeResult{Up: true, LatencyMs: int64(i), At: time.Now()})
	}
	snap := pm.Snapshot()
	if got := len(snap.Points[0].History); got != maxProbeHistory {
		t.Fatalf("history = %d, want %d", got, maxProbeHistory)
	}
	if len(snap.Points[0].Transitions) != 0 {
		t.Fatalf("transitions = %d, want 0", len(snap.Points[0].Transitions))
	}

	// Force 40 up/down flips: transition ring capped.
	for i := 0; i < 40; i++ {
		up := i%2 == 0
		pm.observe(spec, ProbeResult{Up: up, At: time.Now()})
	}
	snap = pm.Snapshot()
	if got := len(snap.Points[0].Transitions); got != maxProbeTransitions {
		t.Fatalf("transitions = %d, want %d", got, maxProbeTransitions)
	}
}

// ── SetTargets batch semantics ──────────────────────────────────────────────

func TestSetTargets_AllOrNothing(t *testing.T) {
	pm, _ := newTestProbe(t, nil)

	good := ProbeTargetSpec{Name: "ok", Mode: ProbeTCP, Host: "h", Port: 1}
	bad := ProbeTargetSpec{Name: "bad", Mode: "icmp", Host: "h", Port: 1}
	if err := pm.SetTargets([]ProbeTargetSpec{good, bad}); err == nil {
		t.Fatal("expected error for invalid batch")
	}
	if snap := pm.Snapshot(); len(snap.Points) != 0 {
		t.Fatalf("rejected batch must not apply partially, got %d points", len(snap.Points))
	}
}

func TestSetTargets_DuplicatesAndLimit(t *testing.T) {
	pm, _ := newTestProbe(t, nil)
	a := ProbeTargetSpec{Name: "dup", Mode: ProbeTCP, Host: "h", Port: 1}
	b := ProbeTargetSpec{Name: "dup", Mode: ProbeTCP, Host: "h", Port: 2}
	if err := pm.SetTargets([]ProbeTargetSpec{a, b}); err == nil {
		t.Fatal("expected duplicate-name error")
	}

	many := make([]ProbeTargetSpec, 0, maxProbeTargets+1)
	for i := 0; i <= maxProbeTargets; i++ {
		many = append(many, ProbeTargetSpec{
			Name: fmt.Sprintf("t%d", i), Mode: ProbeTCP, Host: "h", Port: 1 + i,
		})
	}
	if err := pm.SetTargets(many); err == nil {
		t.Fatal("expected max-targets error")
	}
}

// ── Persistence ─────────────────────────────────────────────────────────────

func TestProbeTargets_PersistenceRoundTrip(t *testing.T) {
	dir := t.TempDir()
	specs := []ProbeTargetSpec{
		{Name: "web", Mode: ProbeHTTP, Host: "example.com", Port: 443, Scheme: "https", Path: "/health"},
		{Name: "ssh", Mode: ProbeTCP, Host: "10.0.0.5", Port: 22},
	}
	pm := NewProbeMonitor(dir, nil, time.Minute)
	if err := pm.SetTargets(specs); err != nil {
		t.Fatal(err)
	}

	// A fresh monitor (agent restart) loads the persisted set.
	pm2 := NewProbeMonitor(dir, nil, time.Minute)
	snap := pm2.Snapshot()
	if len(snap.Points) != 2 {
		t.Fatalf("loaded points = %d, want 2", len(snap.Points))
	}
	if snap.Points[0].Target != specs[0] || snap.Points[1].Target != specs[1] {
		t.Fatalf("loaded targets mismatch:\n got %v\nwant %v\n%v", snap.Points, specs[0], specs[1])
	}
}

func TestProbeTargets_CorruptFileStartsEmpty(t *testing.T) {
	dir := t.TempDir()
	if err := writeFile(dir+"/probes.json", "{not json", 0o644); err != nil {
		t.Fatal(err)
	}
	pm := NewProbeMonitor(dir, nil, time.Minute)
	if snap := pm.Snapshot(); len(snap.Points) != 0 {
		t.Fatalf("corrupt file: points = %d, want 0", len(snap.Points))
	}
}

// ── API integration ─────────────────────────────────────────────────────────

func probeAPI(t *testing.T, pm *ProbeMonitor) *API {
	t.Helper()
	dir := t.TempDir()
	cfg := &Config{Token: "probe-token", DataDir: dir}
	return newAPI(cfg, NewMonitor(cfg), NewTunnelManager(dir, dir+"/configs", DeployModeScripts), nil, pm)
}

func doProbeAuth(t *testing.T, api *API, method, path string, body []byte) *httptest.ResponseRecorder {
	t.Helper()
	req := httptest.NewRequest(method, path, bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer probe-token")
	req.Header.Set("Content-Type", "application/json")
	rec := httptest.NewRecorder()
	api.routes().ServeHTTP(rec, req)
	return rec
}

func TestProbeAPI_FullFlow(t *testing.T) {
	pm, _ := newTestProbe(t, nil)
	api := probeAPI(t, pm)

	// No targets yet → empty snapshot, enabled.
	rec := doProbeAuth(t, api, http.MethodGet, "/api/probe", nil)
	if rec.Code != http.StatusOK {
		t.Fatalf("GET: %d", rec.Code)
	}
	var snap ProbeSnapshot
	if err := json.Unmarshal(rec.Body.Bytes(), &snap); err != nil {
		t.Fatal(err)
	}
	if !snap.Enabled || len(snap.Points) != 0 {
		t.Fatalf("initial snapshot = %+v", snap)
	}
	if snap.Hostname == "" {
		t.Fatal("snapshot hostname must be set")
	}

	// Register one target on a live local listener.
	addr, stop := startTCPLisener(t)
	defer stop()
	host, portStr, _ := net.SplitHostPort(addr)
	port := 0
	fmt.Sscanf(portStr, "%d", &port)
	body := fmt.Sprintf(`{"targets":[{"name":"local","mode":"tcp","host":%q,"port":%d}]}`, host, port)

	rec = doProbeAuth(t, api, http.MethodPut, "/api/probe/targets", []byte(body))
	if rec.Code != http.StatusOK {
		t.Fatalf("PUT targets: %d body=%s", rec.Code, rec.Body.String())
	}
	var putSnap ProbeSnapshot
	_ = json.Unmarshal(rec.Body.Bytes(), &putSnap)
	if len(putSnap.Points) != 1 {
		t.Fatalf("after PUT: points = %d, want 1", len(putSnap.Points))
	}

	// Invalid batch → 400 and previous set intact.
	rec = doProbeAuth(t, api, http.MethodPut, "/api/probe/targets",
		[]byte(`{"targets":[{"name":"","mode":"tcp","host":"h","port":1}]}`))
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("invalid PUT: %d, want 400", rec.Code)
	}
	rec = doProbeAuth(t, api, http.MethodGet, "/api/probe", nil)
	_ = json.Unmarshal(rec.Body.Bytes(), &putSnap)
	if len(putSnap.Points) != 1 {
		t.Fatalf("set clobbered by rejected batch: %d points", len(putSnap.Points))
	}

	// On-demand probe of one target.
	rec = doProbeAuth(t, api, http.MethodPost, "/api/probe/now", []byte(`{"target":"local"}`))
	if rec.Code != http.StatusOK {
		t.Fatalf("POST now: %d body=%s", rec.Code, rec.Body.String())
	}
	var pt ProbePoint
	if err := json.Unmarshal(rec.Body.Bytes(), &pt); err != nil {
		t.Fatal(err)
	}
	if pt.State != ProbeUp || !pt.Observed {
		t.Fatalf("after now: state=%q observed=%v detail=%q", pt.State, pt.Observed, pt.Last.Detail)
	}

	// Unknown target → 404.
	rec = doProbeAuth(t, api, http.MethodPost, "/api/probe/now", []byte(`{"target":"nope"}`))
	if rec.Code != http.StatusNotFound {
		t.Fatalf("unknown target: %d, want 404", rec.Code)
	}

	// Probe all (empty body) → snapshot.
	rec = doProbeAuth(t, api, http.MethodPost, "/api/probe/now", nil)
	if rec.Code != http.StatusOK {
		t.Fatalf("POST now all: %d", rec.Code)
	}
	var allSnap ProbeSnapshot
	_ = json.Unmarshal(rec.Body.Bytes(), &allSnap)
	if len(allSnap.Points) != 1 || allSnap.Points[0].State != ProbeUp {
		t.Fatalf("probe-all snapshot = %+v", allSnap)
	}

	// Method enforcement.
	if rec := doProbeAuth(t, api, http.MethodDelete, "/api/probe/targets", nil); rec.Code != http.StatusMethodNotAllowed {
		t.Fatalf("DELETE targets: %d, want 405", rec.Code)
	}
	if rec := doProbeAuth(t, api, http.MethodGet, "/api/probe/now", nil); rec.Code != http.StatusMethodNotAllowed {
		t.Fatalf("GET now: %d, want 405", rec.Code)
	}
}

func TestProbeAPI_AuthRequired(t *testing.T) {
	pm, _ := newTestProbe(t, nil)
	api := probeAPI(t, pm)
	req := httptest.NewRequest(http.MethodGet, "/api/probe", nil)
	rec := httptest.NewRecorder()
	api.routes().ServeHTTP(rec, req)
	if rec.Code != http.StatusUnauthorized {
		t.Fatalf("no token: %d, want 401", rec.Code)
	}
}

func TestProbeAPI_DisabledMonitor(t *testing.T) {
	dir := t.TempDir()
	cfg := &Config{Token: "probe-token", DataDir: dir}
	api := newAPI(cfg, NewMonitor(cfg), NewTunnelManager(dir, dir+"/configs", DeployModeScripts), nil, nil)

	rec := doProbeAuth(t, api, http.MethodGet, "/api/probe", nil)
	if rec.Code != http.StatusOK {
		t.Fatalf("GET: %d", rec.Code)
	}
	var snap ProbeSnapshot
	_ = json.Unmarshal(rec.Body.Bytes(), &snap)
	if snap.Enabled {
		t.Fatal("disabled monitor must report enabled=false")
	}

	rec = doProbeAuth(t, api, http.MethodPut, "/api/probe/targets", []byte(`{"targets":[]}`))
	if rec.Code != http.StatusServiceUnavailable {
		t.Fatalf("PUT: %d, want 503", rec.Code)
	}
}

func writeFile(path, content string, mode uint32) error {
	return os.WriteFile(path, []byte(content), os.FileMode(mode))
}
