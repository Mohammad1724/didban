package main

import (
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"testing"
	"time"
)

func countLines(t *testing.T, path string) int {
	t.Helper()
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("read %s: %v", path, err)
	}
	n := 0
	for _, l := range strings.Split(strings.TrimRight(string(data), "\n"), "\n") {
		if l != "" {
			n++
		}
	}
	return n
}

// H12: the events JSONL file must stay bounded no matter how many events
// arrive — the in-memory log is capped at 500 and the file is compacted to
// it whenever it outgrows the size budget.
func TestEventLogFileStaysBounded(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "events.jsonl")
	el := NewEventLog(path)

	detail := strings.Repeat("x", 200) // ~230 bytes per line
	for i := 0; i < 5000; i++ {
		el.Add(Event{Time: time.Now(), Type: "cpu", Value: float64(i), Detail: detail})
	}

	if got := len(el.List(0)); got > 500 {
		t.Fatalf("in-memory log not capped: %d events", got)
	}
	fi, err := os.Stat(path)
	if err != nil {
		t.Fatalf("stat: %v", err)
	}
	if fi.Size() > eventFileMaxBytes+512 {
		t.Fatalf("events file exceeded budget: %d bytes", fi.Size())
	}
	// The newest event must be on disk (nothing lost by compaction).
	newest := el.List(1)[0]
	data, _ := os.ReadFile(path)
	if !strings.Contains(string(data), newest.Detail) {
		t.Fatal("newest event missing from the compacted file")
	}
}

// H12: upgrading from an old agent (which never rotated) leaves an oversized
// file — NewEventLog must reclaim the disk space.
func TestEventLogReclaimsOversizedFileOnLoad(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "events.jsonl")

	// 2500 lines of ~500B ≈ 1.25 MB (> 1 MiB budget), all distinct.
	var sb strings.Builder
	for i := 0; i < 2500; i++ {
		sb.WriteString("{\"time\":\"2026-01-01T00:00:00Z\",\"type\":\"cpu\",\"value\":" +
			strconv.Itoa(i) + ",\"detail\":\"" + strings.Repeat("z", 480) + "\"}\n")
	}
	if err := os.WriteFile(path, []byte(sb.String()), 0o600); err != nil {
		t.Fatal(err)
	}

	el := NewEventLog(path)
	if got := len(el.List(0)); got != 500 {
		t.Fatalf("in-memory log after load: %d events, want 500", got)
	}
	if lines := countLines(t, path); lines != 500 {
		t.Fatalf("file not reclaimed on load: %d lines, want 500", lines)
	}
	fi, _ := os.Stat(path)
	if fi.Size() > eventFileMaxBytes {
		t.Fatalf("file still oversized after load: %d bytes", fi.Size())
	}
}

// A compaction that crashes between write and rename leaves a .tmp file;
// the next startup must clean it up.
func TestEventLogRemovesStaleTmp(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "events.jsonl")
	if err := os.WriteFile(path+".tmp", []byte("stale"), 0o600); err != nil {
		t.Fatal(err)
	}
	NewEventLog(path)
	if _, err := os.Stat(path + ".tmp"); !os.IsNotExist(err) {
		t.Fatal("stale .tmp file was not removed")
	}
}
