package main

import (
	"bufio"
	"encoding/json"
	"os"
	"sync"
	"time"
)

// Event records a resource spike with the processes that caused it.
type Event struct {
	Time   time.Time   `json:"time"`
	Type   string      `json:"type"` // cpu | memory | steal | disk | process_down | process_up | agent_restart
	Value  float64     `json:"value"`
	Detail string      `json:"detail,omitempty"`
	Top    []EventProc `json:"top,omitempty"`
}

// eventFileMaxBytes bounds the on-disk JSONL file (H12): the in-memory log
// keeps at most `max` events, so when the file outgrows the budget it is
// rewritten with exactly the events we still keep. ~1 MiB of event history
// is plenty for a monitoring agent and keeps restart loading instant.
const eventFileMaxBytes = 1 << 20

// EventLog keeps the most recent events in memory and persists them
// to a JSONL file so history survives agent restarts. The file is bounded
// (see eventFileMaxBytes) and can no longer grow unboundedly (H12).
type EventLog struct {
	mu     sync.Mutex
	events []Event
	path   string
	max    int
}

func NewEventLog(path string) *EventLog {
	el := &EventLog{
		path: path,
		max:  500,
	}
	// Remove a temp file left behind if a previous compaction crashed
	// between write and rename.
	os.Remove(path + ".tmp")
	el.load()
	// Reclaim disk on upgrade: files written by older versions (no
	// rotation) may already be over budget.
	if el.fileSize() > eventFileMaxBytes {
		el.mu.Lock()
		el.compactLocked()
		el.mu.Unlock()
	}
	return el
}

func (e *EventLog) fileSize() int64 {
	if fi, err := os.Stat(e.path); err == nil {
		return fi.Size()
	}
	return 0
}

// Add appends an event (in memory + JSONL file). If the write pushes the
// file over the size budget, the file is compacted to the capped in-memory
// events so disk usage stays bounded (H12).
func (e *EventLog) Add(ev Event) {
	e.mu.Lock()
	defer e.mu.Unlock()
	e.events = append(e.events, ev)
	if len(e.events) > e.max {
		e.events = e.events[len(e.events)-e.max:]
	}
	line, err := json.Marshal(ev)
	if err != nil {
		return
	}
	f, err := os.OpenFile(e.path, os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0o600)
	if err != nil {
		return
	}
	wrote, _ := f.Write(append(line, '\n'))
	var size int64
	if fi, err := f.Stat(); err == nil {
		size = fi.Size()
	}
	f.Close()
	if wrote > 0 && size > eventFileMaxBytes {
		e.compactLocked()
	}
}

// compactLocked rewrites the JSONL file with the current (capped) in-memory
// events, atomically (temp file + rename). Caller must hold e.mu.
func (e *EventLog) compactLocked() {
	tmp := e.path + ".tmp"
	f, err := os.OpenFile(tmp, os.O_CREATE|os.O_TRUNC|os.O_WRONLY, 0o600)
	if err != nil {
		return
	}
	w := bufio.NewWriter(f)
	for _, ev := range e.events {
		if line, err := json.Marshal(ev); err == nil {
			w.Write(append(line, '\n'))
		}
	}
	if err := w.Flush(); err != nil {
		f.Close()
		os.Remove(tmp)
		return
	}
	if err := f.Close(); err != nil {
		os.Remove(tmp)
		return
	}
	if err := os.Rename(tmp, e.path); err != nil {
		os.Remove(tmp)
	}
}

// List returns up to limit events, newest first.
func (e *EventLog) List(limit int) []Event {
	e.mu.Lock()
	defer e.mu.Unlock()
	n := len(e.events)
	if limit <= 0 || limit > n {
		limit = n
	}
	out := make([]Event, 0, limit)
	for i := n - 1; i >= n-limit && i >= 0; i-- {
		out = append(out, e.events[i])
	}
	return out
}

// load restores previous events from the JSONL file (keeps the newest max).
func (e *EventLog) load() {
	f, err := os.Open(e.path)
	if err != nil {
		return
	}
	defer f.Close()
	var events []Event
	sc := bufio.NewScanner(f)
	sc.Buffer(make([]byte, 64*1024), 1024*1024)
	for sc.Scan() {
		var ev Event
		if err := json.Unmarshal(sc.Bytes(), &ev); err == nil {
			events = append(events, ev)
		}
	}
	if len(events) > e.max {
		events = events[len(events)-e.max:]
	}
	e.events = events
}
