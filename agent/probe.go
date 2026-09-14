// Phase 4 · 4-B — multi-point probing.
//
// The phone is a single vantage point: when the phone cannot reach a
// target we cannot tell "the target is down" from "the phone's network is
// down". This monitor closes that blind spot by turning the user's own
// servers into probe points: each agent probes the registered targets at
// a fixed interval and reports, per target:
//
//	last up/down + latency, a bounded latency history (sparkline), and
//	recent up/down transitions.
//
// Alerts fire only on state changes (up → down / down → up) through the
// existing AlertDispatcher (via Monitor.RecordEvent, same path as the
// tunnel watchdog). The first observation of a target after an agent
// start is silent, so a target that is intentionally down does not
// re-alert on every agent restart — the same convention as 4-A.
//
// The target set is owned by the app (PUT /api/probe/targets) and
// persisted to <data-dir>/probes.json so it survives agent restarts.
package main

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"
)

// ProbeMode selects how a target is probed from the agent's host.
type ProbeMode string

const (
	ProbeTCP  ProbeMode = "tcp"  // dial host:port; a successful connect is up
	ProbeHTTP ProbeMode = "http" // GET the target; status < 400 is up
)

const (
	// probeDialTimeout bounds a TCP connect from one probe point.
	probeDialTimeout = 4 * time.Second
	// probeHTTPTimeout bounds the whole HTTP request (connect + body).
	probeHTTPTimeout = 6 * time.Second
	// probeBodyLimit discards response bodies beyond 1 MiB (latency probe,
	// not a download).
	probeBodyLimit = 1 << 20
	// maxProbeHistory caps the per-target latency history ring.
	maxProbeHistory = 30
	// maxProbeTransitions caps the per-target transition ring.
	maxProbeTransitions = 20
	// maxProbeTargets bounds the registered set (each target costs a
	// network probe every interval).
	maxProbeTargets = 50
	// probeConcurrency bounds simultaneous in-flight probes per tick.
	probeConcurrency = 5
	probeTargetsFile = "probes.json"
)

// ProbeTargetSpec is one registered probe target (app-owned, validated).
type ProbeTargetSpec struct {
	Name   string    `json:"name"`
	Mode   ProbeMode `json:"mode"`
	Host   string    `json:"host"`
	Port   int       `json:"port"`
	Scheme string    `json:"scheme,omitempty"` // http only: "" = auto (https for :443, else http)
	Path   string    `json:"path,omitempty"`   // http only: "" = "/"
}

// ID is the stable per-target key: the (validated, trimmed) name.
func (s ProbeTargetSpec) ID() string { return s.Name }

// ProbeState is a target's classification from this probe point.
type ProbeState string

const (
	ProbeUp   ProbeState = "up"
	ProbeDown ProbeState = "down"
)

// ProbeResult is one probe outcome.
type ProbeResult struct {
	Up        bool      `json:"up"`
	LatencyMs int64     `json:"latency_ms"`
	Detail    string    `json:"detail,omitempty"`
	At        time.Time `json:"at"`
}

// ProbeTransition is one recorded state change (newest last).
type ProbeTransition struct {
	From   ProbeState `json:"from"`
	To     ProbeState `json:"to"`
	At     time.Time  `json:"at"`
	Detail string     `json:"detail,omitempty"`
}

// ProbePoint is the per-target API payload.
type ProbePoint struct {
	Target      ProbeTargetSpec   `json:"target"`
	State       ProbeState        `json:"state"` // "" until the first observation
	Observed    bool              `json:"observed"`
	Last        ProbeResult       `json:"last"`
	History     []ProbeResult     `json:"history"`     // oldest first, capped at maxProbeHistory
	Transitions []ProbeTransition `json:"transitions"` // newest last, capped at maxProbeTransitions
}

// ProbeSnapshot is the GET /api/probe payload.
type ProbeSnapshot struct {
	Enabled    bool         `json:"enabled"`
	IntervalMs int64        `json:"interval_ms"`
	Hostname   string       `json:"hostname"` // this probe point's identity
	Points     []ProbePoint `json:"points"`
}

// probeHTTPClient is the client used for HTTP probes. Package-level so
// tests can swap in a client that trusts a test TLS certificate.
var probeHTTPClient = func() *http.Client {
	return &http.Client{Timeout: probeHTTPTimeout}
}

// errToDetail shortens transport errors for API/UI display.
func errToDetail(err error) string {
	if err == nil {
		return ""
	}
	s := err.Error()
	if len(s) > 160 {
		s = s[:160] + "…"
	}
	return s
}

// normalizeProbeSpec validates and canonicalizes one target. It returns an
// error for anything that would make the probe meaningless or dangerous.
func normalizeProbeSpec(raw ProbeTargetSpec) (ProbeTargetSpec, error) {
	spec := raw
	spec.Name = strings.TrimSpace(spec.Name)
	spec.Host = strings.TrimSpace(spec.Host)
	spec.Scheme = strings.ToLower(strings.TrimSpace(spec.Scheme))
	spec.Path = strings.TrimSpace(spec.Path)

	if spec.Name == "" {
		return spec, errors.New("name is required")
	}
	if len(spec.Name) > 64 {
		return spec, fmt.Errorf("name too long (%d > 64)", len(spec.Name))
	}
	if spec.Host == "" {
		return spec, errors.New("host is required")
	}
	if len(spec.Host) > 253 {
		return spec, fmt.Errorf("host too long (%d > 253)", len(spec.Host))
	}
	if spec.Port < 1 || spec.Port > 65535 {
		return spec, fmt.Errorf("port must be 1..65535, got %d", spec.Port)
	}
	switch spec.Mode {
	case ProbeTCP:
		spec.Scheme = ""
		spec.Path = ""
	case ProbeHTTP:
		switch spec.Scheme {
		case "", "http", "https":
		default:
			return spec, fmt.Errorf("scheme must be http or https, got %q", spec.Scheme)
		}
		if spec.Path != "" {
			if !strings.HasPrefix(spec.Path, "/") {
				spec.Path = "/" + spec.Path
			}
			if len(spec.Path) > 256 {
				return spec, fmt.Errorf("path too long (%d > 256)", len(spec.Path))
			}
		}
	default:
		return spec, fmt.Errorf("mode must be %q or %q, got %q", ProbeTCP, ProbeHTTP, spec.Mode)
	}
	return spec, nil
}

// probeOnce performs one probe of the target from this host. It never
// returns an error: failures are encoded as ProbeResult{Up:false}.
func probeOnce(spec ProbeTargetSpec) ProbeResult {
	start := time.Now()
	at := func() time.Time { return time.Now() }
	switch spec.Mode {
	case ProbeTCP:
		addr := net.JoinHostPort(spec.Host, fmt.Sprint(spec.Port))
		conn, err := net.DialTimeout("tcp", addr, probeDialTimeout)
		if err != nil {
			return ProbeResult{Up: false, Detail: errToDetail(err), At: at()}
		}
		_ = conn.Close()
		return ProbeResult{Up: true, LatencyMs: time.Since(start).Milliseconds(), At: at()}
	case ProbeHTTP:
		scheme := spec.Scheme
		if scheme == "" {
			if spec.Port == 443 {
				scheme = "https"
			} else {
				scheme = "http"
			}
		}
		path := spec.Path
		if path == "" {
			path = "/"
		}
		host := spec.Host
		if spec.Port > 0 && !(scheme == "http" && spec.Port == 80) && !(scheme == "https" && spec.Port == 443) {
			host = net.JoinHostPort(spec.Host, fmt.Sprint(spec.Port))
		}
		u := &url.URL{Scheme: scheme, Host: host, Path: path}
		req, err := http.NewRequest(http.MethodGet, u.String(), nil)
		if err != nil {
			return ProbeResult{Up: false, Detail: errToDetail(err), At: at()}
		}
		req.Header.Set("User-Agent", "didban-agent-probe/1.0")
		resp, err := probeHTTPClient().Do(req)
		if err != nil {
			return ProbeResult{Up: false, Detail: errToDetail(err), At: at()}
		}
		_, _ = io.Copy(io.Discard, io.LimitReader(resp.Body, probeBodyLimit))
		_ = resp.Body.Close()
		res := ProbeResult{
			Up:        resp.StatusCode < 400,
			LatencyMs: time.Since(start).Milliseconds(),
			Detail:    fmt.Sprintf("HTTP %d", resp.StatusCode),
			At:        at(),
		}
		return res
	default:
		return ProbeResult{Up: false, Detail: "unknown mode: " + string(spec.Mode), At: at()}
	}
}

type probeRec struct {
	state       ProbeState // "" until the first observation
	last        ProbeResult
	history     []ProbeResult
	transitions []ProbeTransition
	changedAt   time.Time
}

// ProbeMonitor runs the per-host probe loop and owns the target set.
type ProbeMonitor struct {
	hostname string
	interval time.Duration
	sink     EventSink
	dataDir  string

	mu      sync.Mutex
	targets []ProbeTargetSpec
	recs    map[string]*probeRec
}

// NewProbeMonitor builds the monitor and loads any persisted target set.
// interval is clamped by the caller (main.go enforces 10..3600s).
func NewProbeMonitor(dataDir string, sink EventSink, interval time.Duration) *ProbeMonitor {
	pm := &ProbeMonitor{
		hostname: probeHostname(),
		interval: interval,
		sink:     sink,
		dataDir:  dataDir,
		recs:     make(map[string]*probeRec),
	}
	pm.loadTargets()
	return pm
}

func probeHostname() string {
	h, err := os.Hostname()
	if err != nil || strings.TrimSpace(h) == "" {
		return "agent"
	}
	return h
}

// SetTargets atomically replaces the registered set. The whole batch is
// validated before anything is applied (all-or-nothing — the same
// fail-loud contract as the watchdog thresholds), then persisted.
func (pm *ProbeMonitor) SetTargets(raw []ProbeTargetSpec) error {
	specs := make([]ProbeTargetSpec, 0, len(raw))
	seen := make(map[string]bool, len(raw))
	for i, r := range raw {
		spec, err := normalizeProbeSpec(r)
		if err != nil {
			return fmt.Errorf("target %d (%q): %w", i+1, r.Name, err)
		}
		if seen[spec.Name] {
			return fmt.Errorf("duplicate target name %q", spec.Name)
		}
		seen[spec.Name] = true
		specs = append(specs, spec)
	}
	if len(specs) > maxProbeTargets {
		return fmt.Errorf("at most %d targets allowed, got %d", maxProbeTargets, len(specs))
	}

	pm.mu.Lock()
	pm.targets = specs
	for id := range pm.recs {
		if !seen[id] {
			delete(pm.recs, id)
		}
	}
	pm.mu.Unlock()
	return pm.saveTargets()
}

// ProbeNow probes one registered target immediately and returns its
// updated point. ok=false when the target is not registered.
func (pm *ProbeMonitor) ProbeNow(name string) (ProbePoint, bool) {
	pm.mu.Lock()
	spec, ok := pm.targetLocked(name)
	pm.mu.Unlock()
	if !ok {
		return ProbePoint{}, false
	}
	pm.observe(spec, probeOnce(spec))
	pm.mu.Lock()
	defer pm.mu.Unlock()
	return pm.pointLocked(spec), true
}

// ProbeAll probes every registered target (bounded concurrency).
func (pm *ProbeMonitor) ProbeAll() {
	pm.mu.Lock()
	specs := make([]ProbeTargetSpec, len(pm.targets))
	copy(specs, pm.targets)
	pm.mu.Unlock()

	sem := make(chan struct{}, probeConcurrency)
	var wg sync.WaitGroup
	for _, s := range specs {
		wg.Add(1)
		sem <- struct{}{}
		go func(s ProbeTargetSpec) {
			defer wg.Done()
			defer func() { <-sem }()
			pm.observe(s, probeOnce(s))
		}(s)
	}
	wg.Wait()
}

// Run ticks ProbeAll until the context is cancelled.
func (pm *ProbeMonitor) Run(ctx context.Context) {
	if len(pm.targets) == 0 && pm.interval <= 0 {
		return
	}
	t := time.NewTicker(pm.interval)
	defer t.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-t.C:
			pm.ProbeAll()
		}
	}
}

// observe records one probe result and alerts on state changes — except
// the first observation, which is silent (startup re-alerts would be
// noise for intentionally-down targets; same convention as 4-A).
func (pm *ProbeMonitor) observe(spec ProbeTargetSpec, res ProbeResult) {
	state := ProbeDown
	if res.Up {
		state = ProbeUp
	}

	pm.mu.Lock()
	rec, known := pm.recs[spec.Name]
	if !known {
		rec = &probeRec{}
		pm.recs[spec.Name] = rec
	}
	from := rec.state
	rec.last = res
	rec.state = state
	rec.history = append(rec.history, res)
	if len(rec.history) > maxProbeHistory {
		rec.history = rec.history[len(rec.history)-maxProbeHistory:]
	}
	changed := known && from != state
	if changed {
		rec.transitions = append(rec.transitions, ProbeTransition{
			From: from, To: state, At: res.At, Detail: res.Detail,
		})
		if len(rec.transitions) > maxProbeTransitions {
			rec.transitions = rec.transitions[len(rec.transitions)-maxProbeTransitions:]
		}
		rec.changedAt = res.At
	}
	pm.mu.Unlock()

	if !known || !changed {
		return
	}
	evType := "probe_up"
	if state == ProbeDown {
		evType = "probe_down"
	}
	if pm.sink != nil {
		pm.sink.RecordEvent(Event{
			Time: res.At,
			Type: evType,
			Detail: fmt.Sprintf("probe %q (%s %s:%d) %s → %s: %s",
				spec.Name, spec.Mode, spec.Host, spec.Port, from, state, res.Detail),
		})
	}
}

// Snapshot builds the current API payload.
func (pm *ProbeMonitor) Snapshot() ProbeSnapshot {
	pm.mu.Lock()
	defer pm.mu.Unlock()
	points := make([]ProbePoint, 0, len(pm.targets))
	for _, s := range pm.targets {
		points = append(points, pm.pointLocked(s))
	}
	return ProbeSnapshot{
		Enabled:    true,
		IntervalMs: pm.interval.Milliseconds(),
		Hostname:   pm.hostname,
		Points:     points,
	}
}

func (pm *ProbeMonitor) targetLocked(name string) (ProbeTargetSpec, bool) {
	for _, s := range pm.targets {
		if s.Name == name {
			return s, true
		}
	}
	return ProbeTargetSpec{}, false
}

func (pm *ProbeMonitor) pointLocked(spec ProbeTargetSpec) ProbePoint {
	p := ProbePoint{Target: spec, Observed: false}
	rec := pm.recs[spec.Name]
	if rec == nil {
		p.History = []ProbeResult{}
		p.Transitions = []ProbeTransition{}
		return p
	}
	p.State = rec.state
	p.Observed = rec.state != ""
	p.Last = rec.last
	p.History = append([]ProbeResult(nil), rec.history...)
	p.Transitions = append([]ProbeTransition(nil), rec.transitions...)
	return p
}

// ── Persistence ─────────────────────────────────────────────────────────────

func (pm *ProbeMonitor) targetsPath() string {
	if pm.dataDir == "" {
		return ""
	}
	return filepath.Join(pm.dataDir, probeTargetsFile)
}

func (pm *ProbeMonitor) saveTargets() error {
	pm.mu.Lock()
	specs := make([]ProbeTargetSpec, len(pm.targets))
	copy(specs, pm.targets)
	pm.mu.Unlock()

	path := pm.targetsPath()
	if path == "" {
		return nil
	}
	encoded, err := json.MarshalIndent(specs, "", "  ")
	if err != nil {
		return err
	}
	if err := os.MkdirAll(pm.dataDir, 0o755); err != nil {
		return err
	}
	return os.WriteFile(path, encoded, 0o644)
}

func (pm *ProbeMonitor) loadTargets() {
	path := pm.targetsPath()
	if path == "" {
		return
	}
	data, err := os.ReadFile(path)
	if err != nil {
		return // missing/corrupt file = start with no targets
	}
	var specs []ProbeTargetSpec
	if err := json.Unmarshal(data, &specs); err != nil {
		return
	}
	clean := []ProbeTargetSpec{}
	seen := make(map[string]bool, len(specs))
	for _, s := range specs {
		s2, err := normalizeProbeSpec(s)
		if err != nil || seen[s2.Name] {
			continue
		}
		seen[s2.Name] = true
		clean = append(clean, s2)
	}
	pm.targets = clean
}
