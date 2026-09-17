package main

import (
	"bufio"
	"bytes"
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
	// Clean up the legacy fixed-name temporary file. New compactions use a
	// random same-directory temporary file via secureWriteFileAtomic.
	_ = os.Remove(path + ".tmp")
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
	size, err := secureAppendFile(e.path, append(line, '\n'), 0o600)
	if err != nil {
		return
	}
	if size > eventFileMaxBytes {
		e.compactLocked()
	}
}

// compactLocked rewrites the JSONL file with the current (capped) in-memory
// events, atomically (temp file + rename). Caller must hold e.mu.
func (e *EventLog) compactLocked() {
	var data bytes.Buffer
	for _, ev := range e.events {
		if line, err := json.Marshal(ev); err == nil {
			data.Write(append(line, '\n'))
		}
	}
	_ = secureWriteFileAtomic(e.path, data.Bytes(), 0o600)
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
