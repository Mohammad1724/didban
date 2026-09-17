package main

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestDestructiveAuditIsBoundedAndSecretFree(t *testing.T) {
	api := hardeningAPI(t)
	secretKey := "secret-operation-key-123456789"
	req := httptest.NewRequest(http.MethodPost, "/api/docker/stop?token=query-secret", nil)
	req.RemoteAddr = "192.0.2.10:4321"
	req.Header.Set("X-Idempotency-Key", secretKey)
	req.Header.Set("Authorization", "Bearer bearer-secret")
	rec := httptest.NewRecorder()

	api.auditDestructive("docker_stop", func(w http.ResponseWriter, _ *http.Request) {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "not recorded"})
	})(rec, req)

	events := api.mon.events.List(1)
	if len(events) != 1 {
		t.Fatalf("events=%d, want 1", len(events))
	}
	detail := events[0].Detail
	for _, forbidden := range []string{secretKey, "query-secret", "bearer-secret", "not recorded", "?"} {
		if strings.Contains(detail, forbidden) {
			t.Fatalf("audit leaked %q: %s", forbidden, detail)
		}
	}
	for _, required := range []string{"operation=docker_stop", "status=400", "client=192.0.2.10", "key_sha256="} {
		if !strings.Contains(detail, required) {
			t.Fatalf("audit missing %q: %s", required, detail)
		}
	}
	if len(detail) > 300 {
		t.Fatalf("audit detail unexpectedly large: %d", len(detail))
	}
}
