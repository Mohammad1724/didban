package main

import (
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
	"time"
)

// H12: the 7-day chart must survive an agent restart — points appended
// before the "restart" are visible to a freshly constructed Monitor.
func TestHistoryPersistsAcrossRestart(t *testing.T) {
	cfg := &Config{DataDir: t.TempDir()}
	m1 := NewMonitor(cfg)
	m1.appendHistory()
	m1.appendHistory()

	// A brand-new Monitor over the same data dir (simulates the restart).
	m2 := NewMonitor(cfg)
	if got := len(m2.hist); got != 2 {
		t.Fatalf("history not persisted across restart: got %d points, want 2", got)
	}
	if got := len(m2.History(24 * time.Hour)); got != 2 {
		t.Fatalf("History(24h) after restart: got %d points, want 2", got)
	}
}

// H12: the history file is bounded — after more than the line budget is
// appended, the file is compacted back to the in-memory window.
func TestHistoryFileStaysBounded(t *testing.T) {
	cfg := &Config{DataDir: t.TempDir()}
	m := NewMonitor(cfg)
	for i := 0; i < histFileMaxLines+50; i++ {
		m.appendHistory()
	}
	if lines := countLines(t, m.histPath); lines > histFileMaxLines {
		t.Fatalf("history file exceeded budget: %d lines, want <= %d", lines, histFileMaxLines)
	}
	if got := len(m.hist); got != histMaxPoints {
		t.Fatalf("in-memory history cap: got %d, want %d", got, histMaxPoints)
	}
	// And the API still serves the window.
	if got := len(m.History(7 * 24 * time.Hour)); got != histMaxPoints {
		t.Fatalf("History(7d): got %d, want %d", got, histMaxPoints)
	}
}

// H12: points older than the 7-day window are pruned when the history is
// loaded (e.g. the agent was down for two weeks).
func TestHistoryLoadPrunesOldPoints(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "history.jsonl")

	old := time.Now().Add(-8 * 24 * time.Hour).Unix()
	recent := time.Now().Unix()
	var lines []string
	for i := 0; i < 2; i++ {
		b, _ := json.Marshal(HistPoint{T: old, CPU: 1, Mem: 1})
		lines = append(lines, string(b))
	}
	for i := 0; i < 3; i++ {
		b, _ := json.Marshal(HistPoint{T: recent + int64(i), CPU: 5, Mem: 7})
		lines = append(lines, string(b))
	}
	data := ""
	for _, l := range lines {
		data += l + "\n"
	}
	if err := os.WriteFile(path, []byte(data), 0o600); err != nil {
		t.Fatal(err)
	}

	m := NewMonitor(&Config{DataDir: dir})
	if got := len(m.hist); got != 3 {
		t.Fatalf("old points not pruned on load: got %d, want 3", got)
	}
}

// A corrupt history file must not crash startup — the agent should just
// start with an empty chart.
func TestHistoryLoadToleratesCorruptFile(t *testing.T) {
	dir := t.TempDir()
	if err := os.WriteFile(filepath.Join(dir, "history.jsonl"), []byte("garbage\n{not json}\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	m := NewMonitor(&Config{DataDir: dir})
	if got := len(m.hist); got != 0 {
		t.Fatalf("corrupt history file should yield an empty chart, got %d points", got)
	}
}
