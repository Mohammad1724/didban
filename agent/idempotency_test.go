package main

import (
	"net/http"
	"net/http/httptest"
	"sync"
	"sync/atomic"
	"testing"
)

func TestIdempotencyGuardAllowsKeyOncePerEndpoint(t *testing.T) {
	g := newIdempotencyGuard()
	key := "operation-key-1234567890"
	if !g.reserve("/api/docker/stop", key) {
		t.Fatal("first reservation rejected")
	}
	if g.reserve("/api/docker/stop", key) {
		t.Fatal("duplicate reservation accepted")
	}
	if !g.reserve("/api/docker/restart", key) {
		t.Fatal("same key should be independently scoped to another endpoint")
	}
}

func TestIdempotencyMiddlewareExecutesConcurrentDuplicateOnce(t *testing.T) {
	api := hardeningAPI(t)
	var calls atomic.Int32
	h := api.idempotent(func(w http.ResponseWriter, _ *http.Request) {
		calls.Add(1)
		w.WriteHeader(http.StatusNoContent)
	})

	const n = 20
	var wg sync.WaitGroup
	statuses := make(chan int, n)
	for i := 0; i < n; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			req := httptest.NewRequest(http.MethodPost, "/api/docker/stop", nil)
			req.Header.Set("X-Idempotency-Key", "concurrent-key-1234567890")
			rec := httptest.NewRecorder()
			h(rec, req)
			statuses <- rec.Code
		}()
	}
	wg.Wait()
	close(statuses)

	var accepted, conflicts int
	for status := range statuses {
		if status == http.StatusNoContent {
			accepted++
		} else if status == http.StatusConflict {
			conflicts++
		}
	}
	if accepted != 1 || conflicts != n-1 || calls.Load() != 1 {
		t.Fatalf("accepted=%d conflicts=%d calls=%d", accepted, conflicts, calls.Load())
	}
}

func TestIdempotencyMiddlewareRejectsMissingOrMalformedKey(t *testing.T) {
	api := hardeningAPI(t)
	h := api.idempotent(func(w http.ResponseWriter, _ *http.Request) { w.WriteHeader(http.StatusNoContent) })
	for _, key := range []string{"", "short", "contains spaces 123456"} {
		req := httptest.NewRequest(http.MethodPost, "/api/docker/stop", nil)
		req.Header.Set("X-Idempotency-Key", key)
		rec := httptest.NewRecorder()
		h(rec, req)
		if rec.Code != http.StatusBadRequest {
			t.Fatalf("key=%q status=%d, want 400", key, rec.Code)
		}
	}
}
