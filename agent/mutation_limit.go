package main

import (
	"net/http"
	"sync"
	"time"
)

const (
	mutationRate       = 0.2 // one new destructive operation token per 5 seconds
	mutationBurst      = 5
	mutationIdleTTL    = 10 * time.Minute
	mutationMaxClients = 10000
)

type mutationBucket struct {
	tokens float64
	last   time.Time
}

type mutationLimiter struct {
	mu      sync.Mutex
	clients map[string]*mutationBucket
}

func newMutationLimiter() *mutationLimiter {
	return &mutationLimiter{clients: make(map[string]*mutationBucket)}
}

func (l *mutationLimiter) allow(ip string, now time.Time) bool {
	l.mu.Lock()
	defer l.mu.Unlock()

	for key, b := range l.clients {
		if now.Sub(b.last) > mutationIdleTTL {
			delete(l.clients, key)
		}
	}
	b := l.clients[ip]
	if b == nil {
		if len(l.clients) >= mutationMaxClients {
			return false
		}
		b = &mutationBucket{tokens: mutationBurst, last: now}
		l.clients[ip] = b
	} else {
		b.tokens = min(float64(mutationBurst), b.tokens+now.Sub(b.last).Seconds()*mutationRate)
		b.last = now
	}
	if b.tokens < 1 {
		return false
	}
	b.tokens--
	return true
}

func (a *API) limitMutation(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if !a.mutations.allow(clientIP(r), time.Now()) {
			w.Header().Set("Retry-After", "5")
			writeJSON(w, http.StatusTooManyRequests, map[string]string{"error": "destructive operation rate limit exceeded"})
			return
		}
		next(w, r)
	}
}
