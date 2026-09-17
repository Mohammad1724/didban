package main

import (
	"net/http"
	"regexp"
	"sync"
	"time"
)

const (
	idempotencyTTL        = 10 * time.Minute
	idempotencyMaxTracked = 10000
)

var idempotencyKeyPattern = regexp.MustCompile(`^[A-Za-z0-9_-]{16,128}$`)

type idempotencyGuard struct {
	mu   sync.Mutex
	seen map[string]time.Time
}

func newIdempotencyGuard() *idempotencyGuard {
	return &idempotencyGuard{seen: make(map[string]time.Time)}
}

// reserve atomically admits a new operation key. Keys remain reserved even
// when the client disconnects or the handler reports an error: the mutation
// may already have happened, so blindly retrying would violate at-most-once.
func (g *idempotencyGuard) reserve(scope, key string) bool {
	if !idempotencyKeyPattern.MatchString(key) {
		return false
	}
	now := time.Now()
	g.mu.Lock()
	defer g.mu.Unlock()
	for k, expires := range g.seen {
		if !expires.After(now) {
			delete(g.seen, k)
		}
	}
	if len(g.seen) >= idempotencyMaxTracked {
		return false
	}
	composite := scope + "\x00" + key
	if _, exists := g.seen[composite]; exists {
		return false
	}
	g.seen[composite] = now.Add(idempotencyTTL)
	return true
}

func (a *API) idempotent(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		key := r.Header.Get("X-Idempotency-Key")
		if !idempotencyKeyPattern.MatchString(key) {
			writeJSON(w, http.StatusBadRequest, map[string]string{"error": "valid X-Idempotency-Key required"})
			return
		}
		if !a.idempotency.reserve(r.URL.Path, key) {
			writeJSON(w, http.StatusConflict, map[string]string{"error": "duplicate or unavailable operation key"})
			return
		}
		next(w, r)
	}
}
