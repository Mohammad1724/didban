package main

import (
	"bufio"
	"context"
	"os"
	"runtime"
	"strconv"
	"strings"
	"sync"
	"syscall"
	"time"
)

// CPUStats holds CPU usage percentages.
type CPUStats struct {
	Cores  int     `json:"cores"`
	Usage  float64 `json:"usage"` // busy (user+nice+system+irq+softirq)
	User   float64 `json:"user"`
	System float64 `json:"system"`
	Iowait float64 `json:"iowait"`
	Steal  float64 `json:"steal"`
}

// MemStats holds memory info in bytes plus usage percentages.
type MemStats struct {
	Total        uint64  `json:"total"`
	Used         uint64  `json:"used"`
	Available    uint64  `json:"available"`
	SwapTotal    uint64  `json:"swap_total"`
	SwapUsed     uint64  `json:"swap_used"`
	UsagePct     float64 `json:"usage_pct"`
	SwapUsagePct float64 `json:"swap_usage_pct"`
}

// DiskInfo holds one filesystem's usage.
type DiskInfo struct {
	Device   string  `json:"device"`
	Mount    string  `json:"mount"`
	FSType   string  `json:"fs_type"`
	Total    uint64  `json:"total"`
	Used     uint64  `json:"used"`
	Free     uint64  `json:"free"`
	UsagePct float64 `json:"usage_pct"`
}

// NetIface holds per-interface throughput in bytes/sec.
type NetIface struct {
	Name string  `json:"name"`
	RX   float64 `json:"rx"`
	TX   float64 `json:"tx"`
}

// Snapshot is the full current state of the machine.
type Snapshot struct {
	Hostname  string     `json:"hostname"`
	Time      time.Time  `json:"time"`
	Version   string     `json:"version"`
	UptimeSec uint64     `json:"uptime_sec"`
	LoadAvg   []float64  `json:"load_avg"`
	CPU       CPUStats   `json:"cpu"`
	Memory    MemStats   `json:"memory"`
	Disks     []DiskInfo `json:"disks"`
	Network   []NetIface `json:"network"`
}

// HistPoint is one minute of history (for charts).
type HistPoint struct {
	T   int64   `json:"t"` // unix seconds
	CPU float64 `json:"cpu"`
	Mem float64 `json:"mem"`
	RX  float64 `json:"rx"`
	TX  float64 `json:"tx"`
}

type cpuTicks struct {
	user, nice, sys, idle, iowait, irq, sirq, steal, total uint64
}

type netCounters struct {
	rx, tx uint64
}

// Monitor samples the system and keeps the latest snapshot,
// the top processes, history and spike events.
type Monitor struct {
	mu     sync.RWMutex
	cfg    *Config
	snap   Snapshot
	procs  []ProcessInfo
	hist   []HistPoint
	events *EventLog

	// internals (only touched from the Run goroutine)
	prevCPU         *cpuTicks
	prevNet         map[string]netCounters
	prevNetAt       time.Time
	prevProcs       map[int]uint64 // pid -> utime+stime
	prevJiff        uint64         // total jiffies at last CPU sample
	lastProcJiffies uint64         // total jiffies at last process sample
	memTotal        uint64
	uidMap          map[string]string

	lastCPUEvent   time.Time
	lastMemEvent   time.Time
	lastStealEvent time.Time
	lastDiskEvent  map[string]time.Time
	watchState     map[string]bool
	procNames      map[string]bool
}

func NewMonitor(cfg *Config) *Monitor {
	host, _ := os.Hostname()
	return &Monitor{
		cfg:           cfg,
		events:        NewEventLog(cfg.DataDir + "/events.jsonl"),
		uidMap:        loadUserMap(),
		lastDiskEvent: make(map[string]time.Time),
		watchState:    make(map[string]bool),
		snap: Snapshot{
			Hostname: host,
			Version:  version,
			LoadAvg:  []float64{0, 0, 0},
		},
	}
}

// Run starts all sampling loops until ctx is cancelled.
func (m *Monitor) Run(ctx context.Context) {
	m.events.Add(Event{Time: time.Now(), Type: "agent_restart", Detail: "agent (re)started"})
	m.sampleAll()

	fast := time.NewTicker(2 * time.Second)
	slow := time.NewTicker(10 * time.Second)
	minute := time.NewTicker(60 * time.Second)
	users := time.NewTicker(10 * time.Minute)
	defer fast.Stop()
	defer slow.Stop()
	defer minute.Stop()
	defer users.Stop()

	for {
		select {
		case <-fast.C:
			m.sampleCPU()
			m.sampleProcs()
			m.detectEvents()
			m.checkWatchedProcs()
		case <-slow.C:
			m.sampleMem()
			m.sampleDisks()
			m.checkDisks()
			m.sampleNet()
			m.sampleMisc()
		case <-minute.C:
			m.appendHistory()
		case <-users.C:
			m.uidMap = loadUserMap()
		case <-ctx.Done():
			return
		}
	}
}

func (m *Monitor) sampleAll() {
	m.sampleCPU()
	m.sampleProcs()
	m.sampleMem()
	m.sampleDisks()
	m.sampleNet()
	m.sampleMisc()
	m.appendHistory()
}

// Snapshot returns a copy of the current snapshot.
func (m *Monitor) Snapshot() Snapshot {
	m.mu.RLock()
	defer m.mu.RUnlock()
	s := m.snap
	s.Disks = append([]DiskInfo(nil), m.snap.Disks...)
	s.Network = append([]NetIface(nil), m.snap.Network...)
	s.LoadAvg = append([]float64(nil), m.snap.LoadAvg...)
	return s
}

// Procs returns a copy of the current top process list.
func (m *Monitor) Procs() []ProcessInfo {
	m.mu.RLock()
	defer m.mu.RUnlock()
	out := make([]ProcessInfo, 0, len(m.procs))
	return append(out, m.procs...)
}

// History returns points newer than the given duration.
func (m *Monitor) History(d time.Duration) []HistPoint {
	cutoff := time.Now().Add(-d).Unix()
	m.mu.RLock()
	defer m.mu.RUnlock()
	out := make([]HistPoint, 0, len(m.hist))
	for _, p := range m.hist {
		if p.T >= cutoff {
			out = append(out, p)
		}
	}
	return out
}

// ── CPU ─────────────────────────────────────────────────────────────────────

func readCPUTicks() *cpuTicks {
	f, err := os.Open("/proc/stat")
	if err != nil {
		return nil
	}
	defer f.Close()
	sc := bufio.NewScanner(f)
	for sc.Scan() {
		line := sc.Text()
		if !strings.HasPrefix(line, "cpu ") {
			continue
		}
		fields := strings.Fields(line)[1:]
		t := &cpuTicks{}
		vals := make([]uint64, 8)
		for i := 0; i < 8 && i < len(fields); i++ {
			v, _ := strconv.ParseUint(fields[i], 10, 64)
			vals[i] = v
		}
		t.user, t.nice, t.sys, t.idle = vals[0], vals[1], vals[2], vals[3]
		t.iowait, t.irq, t.sirq, t.steal = vals[4], vals[5], vals[6], vals[7]
		t.total = t.user + t.nice + t.sys + t.idle + t.iowait + t.irq + t.sirq + t.steal
		return t
	}
	return nil
}

func (m *Monitor) sampleCPU() {
	cur := readCPUTicks()
	if cur == nil {
		return
	}
	m.mu.Lock()
	defer m.mu.Unlock()
	m.snap.Time = time.Now()
	m.snap.CPU.Cores = runtime.NumCPU()
	if m.prevCPU != nil {
		d := cur.total - m.prevCPU.total
		if d > 0 {
			pct := func(v uint64) float64 { return float64(v) * 100 / float64(d) }
			idle := cur.idle - m.prevCPU.idle
			iow := cur.iowait - m.prevCPU.iowait
			m.snap.CPU.Usage = 100 - pct(idle+iow)
			if m.snap.CPU.Usage < 0 {
				m.snap.CPU.Usage = 0
			}
			m.snap.CPU.User = pct(cur.user - m.prevCPU.user + cur.nice - m.prevCPU.nice)
			m.snap.CPU.System = pct(cur.sys - m.prevCPU.sys + cur.irq - m.prevCPU.irq + cur.sirq - m.prevCPU.sirq)
			m.snap.CPU.Iowait = pct(iow)
			m.snap.CPU.Steal = pct(cur.steal - m.prevCPU.steal)
		}
	}
	m.prevCPU = cur
	m.prevJiff = cur.total
}

// ── Memory ──────────────────────────────────────────────────────────────────

func (m *Monitor) sampleMem() {
	values := map[string]uint64{}
	f, err := os.Open("/proc/meminfo")
	if err != nil {
		return
	}
	defer f.Close()
	sc := bufio.NewScanner(f)
	for sc.Scan() {
		fields := strings.Fields(sc.Text())
		if len(fields) < 2 {
			continue
		}
		v, _ := strconv.ParseUint(fields[1], 10, 64)
		values[strings.TrimSuffix(fields[0], ":")] = v * 1024 // kB -> bytes
	}
	total := values["MemTotal"]
	avail := values["MemAvailable"]
	if avail == 0 {
		avail = values["MemFree"] + values["Buffers"] + values["Cached"]
	}
	used := uint64(0)
	if total > avail {
		used = total - avail
	}
	swapTotal := values["SwapTotal"]
	swapFree := values["SwapFree"]
	swapUsed := uint64(0)
	if swapTotal > swapFree {
		swapUsed = swapTotal - swapFree
	}

	m.mu.Lock()
	defer m.mu.Unlock()
	m.memTotal = total
	m.snap.Memory = MemStats{
		Total:     total,
		Used:      used,
		Available: avail,
		SwapTotal: swapTotal,
		SwapUsed:  swapUsed,
	}
	if total > 0 {
		m.snap.Memory.UsagePct = float64(used) * 100 / float64(total)
	}
	if swapTotal > 0 {
		m.snap.Memory.SwapUsagePct = float64(swapUsed) * 100 / float64(swapTotal)
	}
}

// ── Disks ───────────────────────────────────────────────────────────────────

var skipFSTypes = map[string]bool{
	"squashfs": true, "iso9660": true, "udf": true, "cramfs": true,
	"proc": true, "sysfs": true, "tmpfs": true, "devtmpfs": true,
	"securityfs": true, "debugfs": true, "tracefs": true, "configfs": true,
	"fusectl": true, "pstore": true, "bpf": true, "autofs": true,
	"efivarfs": true, "mqueue": true, "hugetlbfs": true, "devpts": true,
}

func (m *Monitor) sampleDisks() {
	f, err := os.Open("/proc/self/mounts")
	if err != nil {
		return
	}
	defer f.Close()
	disks := make([]DiskInfo, 0)
	seen := map[string]bool{}
	sc := bufio.NewScanner(f)
	for sc.Scan() {
		fields := strings.Fields(sc.Text())
		if len(fields) < 3 {
			continue
		}
		dev, mount, fsType := fields[0], unescapeMount(fields[1]), fields[2]
		if !strings.HasPrefix(dev, "/dev/") || skipFSTypes[fsType] {
			continue
		}
		if seen[dev] || strings.HasPrefix(mount, "/snap/") {
			continue
		}
		seen[dev] = true
		var st syscall.Statfs_t
		if err := syscall.Statfs(mount, &st); err != nil {
			continue
		}
		bs := uint64(st.Bsize)
		total := st.Blocks * bs
		if total == 0 {
			continue
		}
		free := st.Bavail * bs // available to unprivileged users (like df)
		used := total - st.Bfree*bs
		disks = append(disks, DiskInfo{
			Device:   dev,
			Mount:    mount,
			FSType:   fsType,
			Total:    total,
			Used:     used,
			Free:     free,
			UsagePct: float64(used) * 100 / float64(total),
		})
	}
	m.mu.Lock()
	m.snap.Disks = disks
	m.mu.Unlock()
}

func unescapeMount(s string) string {
	s = strings.ReplaceAll(s, "\\040", " ")
	s = strings.ReplaceAll(s, "\\011", "\t")
	return s
}

// ── Network ─────────────────────────────────────────────────────────────────

func (m *Monitor) sampleNet() {
	f, err := os.Open("/proc/net/dev")
	if err != nil {
		return
	}
	defer f.Close()
	cur := map[string]netCounters{}
	sc := bufio.NewScanner(f)
	for sc.Scan() {
		line := sc.Text()
		parts := strings.SplitN(line, ":", 2)
		if len(parts) != 2 {
			continue
		}
		name := strings.TrimSpace(parts[0])
		if name == "lo" {
			continue
		}
		fields := strings.Fields(parts[1])
		if len(fields) < 10 {
			continue
		}
		rx, _ := strconv.ParseUint(fields[0], 10, 64)
		tx, _ := strconv.ParseUint(fields[8], 10, 64)
		cur[name] = netCounters{rx: rx, tx: tx}
	}
	now := time.Now()
	ifaces := make([]NetIface, 0)
	if m.prevNet != nil {
		dt := now.Sub(m.prevNetAt).Seconds()
		if dt > 0 {
			for name, c := range cur {
				p, ok := m.prevNet[name]
				if !ok {
					continue
				}
				rx := float64(c.rx-p.rx) / dt
				tx := float64(c.tx-p.tx) / dt
				if rx < 0 {
					rx = 0
				}
				if tx < 0 {
					tx = 0
				}
				ifaces = append(ifaces, NetIface{Name: name, RX: rx, TX: tx})
			}
		}
	}
	m.mu.Lock()
	m.snap.Network = ifaces
	m.mu.Unlock()
	m.prevNet = cur
	m.prevNetAt = now
}

// ── Uptime / load ───────────────────────────────────────────────────────────

func (m *Monitor) sampleMisc() {
	var load [3]float64
	if b, err := os.ReadFile("/proc/loadavg"); err == nil {
		fields := strings.Fields(string(b))
		for i := 0; i < 3 && i < len(fields); i++ {
			load[i], _ = strconv.ParseFloat(fields[i], 64)
		}
	}
	var uptime uint64
	if b, err := os.ReadFile("/proc/uptime"); err == nil {
		fields := strings.Fields(string(b))
		if len(fields) > 0 {
			f, _ := strconv.ParseFloat(fields[0], 64)
			uptime = uint64(f)
		}
	}
	m.mu.Lock()
	m.snap.LoadAvg = load[:]
	m.snap.UptimeSec = uptime
	m.mu.Unlock()
}

// ── History ─────────────────────────────────────────────────────────────────

func (m *Monitor) appendHistory() {
	m.mu.RLock()
	p := HistPoint{
		T:   time.Now().Unix(),
		CPU: m.snap.CPU.Usage,
		Mem: m.snap.Memory.UsagePct,
	}
	for _, n := range m.snap.Network {
		p.RX += n.RX
		p.TX += n.TX
	}
	m.mu.RUnlock()

	m.mu.Lock()
	m.hist = append(m.hist, p)
	if len(m.hist) > 10080 { // 7 days at 1-minute resolution
		m.hist = m.hist[len(m.hist)-10080:]
	}
	m.mu.Unlock()
}
