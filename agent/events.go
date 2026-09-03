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
	Type   string      `json:"type"` // cpu | memory | steal | disk | node_down | node_up | process_down | process_up | panel_down | panel_up | agent_restart
	Value  float64     `json:"value"`
	Detail string      `json:"detail,omitempty"`
	Top    []EventProc `json:"top,omitempty"`
}

// EventLog keeps the most recent events in memory and persists them
// to a JSONL file so history survives agent restarts.
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
	el.load()
	return el
}

// Add appends an event (in memory + JSONL file).
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
	defer f.Close()
	_, _ = f.Write(append(line, '\n'))
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
