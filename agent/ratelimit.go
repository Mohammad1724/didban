package main

import (
	"sync"
	"time"
)

// rateLimiter is a per-client-IP token bucket. It protects the agent from
// request floods (a stolen token used for brute-force kill/restart loops,
// bandwidth-endpoint abuse, or blind DoS against the SSR status page).
//
// Limits are generous for legitimate clients (a phone polling every 30 s
// plus a browser refreshing the status page) and tight enough to stop
// abuse. State is kept in memory and idle buckets are reaped, so a flood of
// unique source IPs cannot grow the map without bound.

const (
	// sustained requests per second per IP
	rateLimitRate = 10.0
	// burst size per IP
	rateLimitBurst = 20
	// idle buckets older than this are reaped
	rateLimitIdleTTL = 5 * time.Minute
	// hard cap on tracked IPs
	rateLimitMaxTracked = 10000
	// how often the reaper runs
	rateLimitSweepInterval = 1 * time.Minute
)

type bucket struct {
	tokens     float64
	lastRefill time.Time
}

type rateLimiter struct {
	mu      sync.Mutex
	buckets map[string]*bucket
}

func newRateLimiter() *rateLimiter {
	rl := &rateLimiter{buckets: make(map[string]*bucket)}
	go rl.sweepLoop()
	return rl
}

// allow reports whether a request from [ip] may proceed right now.
func (rl *rateLimiter) allow(ip string) bool {
	rl.mu.Lock()
	defer rl.mu.Unlock()

	now := time.Now()
	b, ok := rl.buckets[ip]
	if !ok {
		if len(rl.buckets) >= rateLimitMaxTracked {
			// Under a unique-IP flood: drop the oldest entries to make
			// room (worst case a legitimate IP is re-limited briefly).
			rl.evictOldestLocked(now)
			b, ok = rl.buckets[ip]
		}
		if !ok {
			b = &bucket{tokens: float64(rateLimitBurst), lastRefill: now}
			rl.buckets[ip] = b
		}
	} else {
		// Refill by elapsed time.
		elapsed := now.Sub(b.lastRefill).Seconds()
		b.lastRefill = now
		b.tokens = min(b.tokens+elapsed*rateLimitRate, float64(rateLimitBurst))
	}
	if b.tokens >= 1 {
		b.tokens--
		return true
	}
	return false
}

// evictOldestLocked removes the ~half of tracked buckets that have been idle
// longest. Caller must hold rl.mu.
func (rl *rateLimiter) evictOldestLocked(now time.Time) {
	type entry struct {
		ip string
		ts time.Time
	}
	all := make([]entry, 0, len(rl.buckets))
	for ip, b := range rl.buckets {
		all = append(all, entry{ip, b.lastRefill})
	}
	// Simple insertion sort is fine for this size; avoids importing sort
	// for a rarely taken path.
	for i := 1; i < len(all); i++ {
		for j := i; j > 0 && all[j].ts.Before(all[j-1].ts); j-- {
			all[j], all[j-1] = all[j-1], all[j]
		}
	}
	for i := 0; i < len(all)/2; i++ {
		delete(rl.buckets, all[i].ip)
	}
}

func (rl *rateLimiter) sweepLoop() {
	t := time.NewTicker(rateLimitSweepInterval)
	defer t.Stop()
	for range t.C {
		rl.mu.Lock()
		now := time.Now()
		for ip, b := range rl.buckets {
			if now.Sub(b.lastRefill) > rateLimitIdleTTL {
				delete(rl.buckets, ip)
			}
		}
		rl.mu.Unlock()
	}
}
