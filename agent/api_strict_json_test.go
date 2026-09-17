package main

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestDestructiveEndpointRequiresJSONContentType(t *testing.T) {
	api := hardeningAPI(t)
	req := httptest.NewRequest(http.MethodPost, "/api/processes/kill", strings.NewReader(`{"pid":123,"signal":"SIGTERM"}`))
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
	req.Header.Set("Authorization", "Bearer hard-token")
	req.Header.Set("Content-Type", "application/json")
	rec := httptest.NewRecorder()
	api.routes().ServeHTTP(rec, req)
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("status=%d, want 400; body=%s", rec.Code, rec.Body.String())
	}
}
