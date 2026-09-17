package main

import (
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

func TestMutationLimiterBurstRefillAndClientIsolation(t *testing.T) {
	l := newMutationLimiter()
	now := time.Unix(1000, 0)
	for i := 0; i < mutationBurst; i++ {
		if !l.allow("192.0.2.1", now) {
			t.Fatalf("burst request %d rejected", i+1)
		}
	}
	if l.allow("192.0.2.1", now) {
		t.Fatal("request beyond burst accepted")
	}
	if !l.allow("192.0.2.2", now) {
		t.Fatal("independent client was limited")
	}
	if !l.allow("192.0.2.1", now.Add(5*time.Second)) {
		t.Fatal("token did not refill after five seconds")
	}
}

func TestMutationLimitMiddlewareReturns429AndRetryAfter(t *testing.T) {
	api := hardeningAPI(t)
	h := api.limitMutation(func(w http.ResponseWriter, _ *http.Request) { w.WriteHeader(http.StatusNoContent) })
	for i := 0; i < mutationBurst; i++ {
		req := httptest.NewRequest(http.MethodPost, "/api/docker/stop", nil)
		rec := httptest.NewRecorder()
		h(rec, req)
		if rec.Code != http.StatusNoContent {
			t.Fatalf("request %d status=%d", i+1, rec.Code)
		}
	}
	req := httptest.NewRequest(http.MethodPost, "/api/docker/stop", nil)
	rec := httptest.NewRecorder()
	h(rec, req)
	if rec.Code != http.StatusTooManyRequests || rec.Header().Get("Retry-After") != "5" {
		t.Fatalf("status=%d Retry-After=%q", rec.Code, rec.Header().Get("Retry-After"))
	}
}
