package main

import (
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync/atomic"
	"testing"
)

var testOperationCounter atomic.Uint64

func addTestOperationKey(req *http.Request) {
	req.Header.Set("X-Idempotency-Key", fmt.Sprintf("test-operation-%016x", testOperationCounter.Add(1)))
}

func TestDestructiveEndpointRequiresJSONContentType(t *testing.T) {
	api := hardeningAPI(t)
	req := httptest.NewRequest(http.MethodPost, "/api/processes/kill", strings.NewReader(`{"pid":123,"signal":"SIGTERM"}`))
	addTestOperationKey(req)
	req.Header.Set("Authorization", "Bearer hard-token")
	rec := httptest.NewRecorder()
	api.routes().ServeHTTP(rec, req)
	if rec.Code != http.StatusUnsupportedMediaType {
		t.Fatalf("status=%d, want 415; body=%s", rec.Code, rec.Body.String())
	}
}

func TestDestructiveEndpointRejectsUnknownJSONFields(t *testing.T) {
	api := hardeningAPI(t)
	req := httptest.NewRequest(http.MethodPost, "/api/processes/kill", strings.NewReader(`{"pid":123,"signal":"SIGTERM","extra":true}`))
	addTestOperationKey(req)
	req.Header.Set("Authorization", "Bearer hard-token")
	req.Header.Set("Content-Type", "application/json")
	rec := httptest.NewRecorder()
	api.routes().ServeHTTP(rec, req)
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("status=%d, want 400; body=%s", rec.Code, rec.Body.String())
	}
}

func TestDestructiveEndpointRejectsTrailingJSONObject(t *testing.T) {
	api := hardeningAPI(t)
	req := httptest.NewRequest(http.MethodPost, "/api/docker/stop", strings.NewReader(`{"id":"one"} {"id":"two"}`))
	addTestOperationKey(req)
	req.Header.Set("Authorization", "Bearer hard-token")
	req.Header.Set("Content-Type", "application/json")
	rec := httptest.NewRecorder()
	api.routes().ServeHTTP(rec, req)
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("status=%d, want 400; body=%s", rec.Code, rec.Body.String())
	}
}
