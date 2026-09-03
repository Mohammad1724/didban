package main

import (
	"bytes"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"time"
)

// ProcessInfo describes one running process.
type ProcessInfo struct {
	PID    int     `json:"pid"`
	Name   string  `json:"name"`
	Cmd    string  `json:"cmd,omitempty"`
	User   string  `json:"user"`
	CPU    float64 `json:"cpu"`     // percent of total CPU capacity
	MemPct float64 `json:"mem_pct"` // percent of RAM
	MemMB  float64 `json:"mem_mb"`
}

// EventProc is a compact process record attached to spike events.
type EventProc struct {
	Name  string  `json:"name"`
	PID   int     `json:"pid"`
	CPU   float64 `json:"cpu"`
	MemMB float64 `json:"mem_mb"`
}

// sampleProcs computes instantaneous per-process CPU usage by diffing
// /proc/<pid>/stat counters between consecutive samples.
func (m *Monitor) sampleProcs() {
	entries, err := os.ReadDir("/proc")
	if err != nil {
		return
	}
	nowTotal := m.prevJiff // total jiffies at CPU sample time
	type acc struct {
		pid, utime, stime, rss uint64
		name, user, state      string
		cmd                    string
	}
	var list []acc
	for _, e := range entries {
		if !e.IsDir() {
			continue
		}
		pid, err := strconv.Atoi(e.Name())
		if err != nil {
			continue
		}
		statBytes, err := os.ReadFile(filepath.Join("/proc", e.Name(), "stat"))
		if err != nil {
			continue
		}
		name, state, utime, stime, ok := parseProcStat(statBytes)
		if !ok {
			continue
		}
		a := acc{pid: uint64(pid), utime: utime, stime: stime, name: name, state: state}
		if b, err := os.ReadFile(filepath.Join("/proc", e.Name(), "statm")); err == nil {
			f := strings.Fields(string(b))
			if len(f) >= 2 {
				pages, _ := strconv.ParseUint(f[1], 10, 64)
				a.rss = pages * uint64(os.Getpagesize())
			}
		}
		if b, err := os.ReadFile(filepath.Join("/proc", e.Name(), "cmdline")); err == nil && len(b) > 0 {
			a.cmd = truncate(string(bytes.ReplaceAll(b, []byte{0}, []byte(" "))), 100)
		}
		if b, err := os.ReadFile(filepath.Join("/proc", e.Name(), "status")); err == nil {
			for _, line := range strings.Split(string(b), "\n") {
				if strings.HasPrefix(line, "Uid:") {
					f := strings.Fields(line)
					if len(f) >= 2 {
						a.user = m.uidMap[f[1]]
					}
					break
				}
			}
		}
		list = append(list, a)
	}

	// Compute CPU% per process vs. previous sample.
	procs := make([]ProcessInfo, 0, len(list))
	var prevTotal uint64
	if m.prevProcs != nil {
		prevTotal = m.lastProcJiffies
	}
	dTotal := int64(nowTotal) - int64(prevTotal)
	for _, a := range list {
		p := ProcessInfo{
			PID:   int(a.pid),
			Name:  a.name,
			Cmd:   a.cmd,
			User:  a.user,
			MemMB: float64(a.rss) / (1024 * 1024),
		}
		if m.memTotal > 0 {
			p.MemPct = float64(a.rss) * 100 / float64(m.memTotal)
		}
		if m.prevProcs != nil && dTotal > 0 {
			prev, ok := m.prevProcs[int(a.pid)]
			if ok {
				d := int64(a.utime+a.stime) - int64(prev)
				if d > 0 {
					p.CPU = float64(d) * 100 / float64(dTotal)
				}
			}
		}
		procs = append(procs, p)
	}

	// Sort by CPU desc, then memory, keep the top 25.
	sort.Slice(procs, func(i, j int) bool {
		if procs[i].CPU != procs[j].CPU {
			return procs[i].CPU > procs[j].CPU
		}
		if procs[i].MemMB != procs[j].MemMB {
			return procs[i].MemMB > procs[j].MemMB
		}
		return procs[i].PID < procs[j].PID
	})
	if len(procs) > 25 {
		procs = procs[:25]
	}

	newPrev := make(map[int]uint64, len(list))
	names := make(map[string]bool, len(list))
	for _, a := range list {
		newPrev[int(a.pid)] = a.utime + a.stime
		names[a.name] = true
	}
	m.prevProcs = newPrev
	m.lastProcJiffies = nowTotal

	m.mu.Lock()
	m.procs = procs
	m.procNames = names
	m.mu.Unlock()
}

// parseProcStat parses the tricky /proc/<pid>/stat format:
//
//	pid (comm with (possible) spaces) state ppid pgrp ...
//
// We split on the LAST ')' so comm may contain spaces and parentheses.
func parseProcStat(b []byte) (name, state string, utime, stime uint64, ok bool) {
	s := string(b)
	i := strings.LastIndex(s, ")")
	if i < 0 {
		return "", "", 0, 0, false
	}
	head := s[:i] // "pid (comm"
	sp := strings.IndexByte(head, ' ')
	if sp < 0 {
		return "", "", 0, 0, false
	}
	op := strings.IndexByte(head, '(')
	if op <= sp { // '(' must come after the space following the pid
		return "", "", 0, 0, false
	}
	name = head[op+1:]
	rest := strings.Fields(s[i+1:])
	if len(rest) < 13 {
		return "", "", 0, 0, false
	}
	state = rest[0]
	utime, _ = strconv.ParseUint(rest[11], 10, 64) // field 14
	stime, _ = strconv.ParseUint(rest[12], 10, 64) // field 15
	return name, state, utime, stime, true
}

func loadUserMap() map[string]string {
	m := map[string]string{}
	b, err := os.ReadFile("/etc/passwd")
	if err != nil {
		return m
	}
	for _, line := range strings.Split(string(b), "\n") {
		f := strings.Split(line, ":")
		if len(f) >= 3 {
			m[f[2]] = f[0]
		}
	}
	return m
}

func truncate(s string, n int) string {
	s = strings.TrimSpace(s)
	if len(s) <= n {
		return s
	}
	return s[:n] + "…"
}

// detectEvents records CPU/memory spike events (with the top processes at
// that moment) — the "what ate my CPU last night?" feature.
func (m *Monitor) detectEvents() {
	now := time.Now()
	m.mu.RLock()
	cpu := m.snap.CPU.Usage
	mem := m.snap.Memory.UsagePct
	procs := m.procs
	m.mu.RUnlock()

	if cpu >= m.cfg.CPUThreshold && now.Sub(m.lastCPUEvent) > 60*time.Second {
		m.lastCPUEvent = now
		m.events.Add(Event{
			Time:  now,
			Type:  "cpu",
			Value: cpu,
			Top:   topEventProcs(procs, 5),
		})
	}
	if mem >= m.cfg.MemThreshold && now.Sub(m.lastMemEvent) > 5*time.Minute {
		m.lastMemEvent = now
		m.events.Add(Event{
			Time:  now,
			Type:  "memory",
			Value: mem,
			Top:   topEventProcs(procs, 5),
		})
	}
	if m.snap.CPU.Steal >= m.cfg.StealThreshold && now.Sub(m.lastStealEvent) > 10*time.Minute {
		m.lastStealEvent = now
		m.events.Add(Event{
			Time:   now,
			Type:   "steal",
			Value:  m.snap.CPU.Steal,
			Detail: "hypervisor is taking CPU from this VM",
		})
	}
}

// checkDisks records an event when a filesystem crosses the disk threshold.
func (m *Monitor) checkDisks() {
	now := time.Now()
	m.mu.RLock()
	disks := m.snap.Disks
	m.mu.RUnlock()
	for _, d := range disks {
		if d.UsagePct >= m.cfg.DiskThreshold && now.Sub(m.lastDiskEvent[d.Mount]) > 30*time.Minute {
			m.lastDiskEvent[d.Mount] = now
			m.events.Add(Event{
				Time:   now,
				Type:   "disk",
				Value:  d.UsagePct,
				Detail: d.Mount,
			})
		}
	}
}

// checkWatchedProcs records process_down / process_up events for the
// configured watchlist (e.g. DIDBAN_WATCH=xray,pg-node-service).
func (m *Monitor) checkWatchedProcs() {
	if len(m.cfg.WatchProcs) == 0 {
		return
	}
	m.mu.RLock()
	names := m.procNames
	m.mu.RUnlock()
	for _, want := range m.cfg.WatchProcs {
		running := names[want]
		was, known := m.watchState[want]
		if known && was != running {
			typ := "process_up"
			if !running {
				typ = "process_down"
			}
			m.events.Add(Event{
				Time:   time.Now(),
				Type:   typ,
				Detail: want,
			})
		}
		m.watchState[want] = running
	}
}

func topEventProcs(procs []ProcessInfo, n int) []EventProc {
	out := make([]EventProc, 0, n)
	for i, p := range procs {
		if i >= n {
			break
		}
		out = append(out, EventProc{Name: p.Name, PID: p.PID, CPU: p.CPU, MemMB: p.MemMB})
	}
	return out
}
