package main

import (
	"bytes"
	"log"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func hardeningAPI(t *testing.T) *API {
	t.Helper()
	dir := t.TempDir()
	cfg := &Config{Token: "hard-token", DataDir: dir}
	return newAPI(cfg, NewMonitor(cfg), NewTunnelManager(dir, dir+"/configs", DeployModeScripts), nil, nil)
}

func doAuth(t *testing.T, api *API, method, path, token string) *httptest.ResponseRecorder {
	t.Helper()
	req := httptest.NewRequest(method, path, nil)
	addTestOperationKey(req)
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	rec := httptest.NewRecorder()
	api.routes().ServeHTTP(rec, req)
	return rec
}

// ── Token transport ─────────────────────────────────────────────────────────

func TestAuth_BearerAccepted_QueryTokenRejected(t *testing.T) {
	api := hardeningAPI(t)

	// Bearer header works.
	if rec := doAuth(t, api, http.MethodGet, "/api/metrics", "hard-token"); rec.Code != http.StatusOK {
		t.Fatalf("bearer: status = %d, body=%s", rec.Code, rec.Body.String())
	}
	// Query-string token is no longer accepted (H5: leaks into logs/Referer).
	if rec := doAuth(t, api, http.MethodGet, "/api/metrics?token=hard-token", ""); rec.Code != http.StatusUnauthorized {
		t.Fatalf("query token: status = %d, want 401", rec.Code)
	}
	// Garbage bearer is rejected.
	if rec := doAuth(t, api, http.MethodGet, "/api/metrics", "wrong"); rec.Code != http.StatusUnauthorized {
		t.Fatalf("wrong bearer: status = %d, want 401", rec.Code)
	}
}

// ── Rate limiting ───────────────────────────────────────────────────────────

func TestRateLimit_BurstIsAllowedThen429(t *testing.T) {
	api := hardeningAPI(t)

	var got429 int
	var got200 int
	var first429At int
	for i := 0; i < 40; i++ {
		rec := doAuth(t, api, http.MethodGet, "/api/metrics", "hard-token")
		if rec.Code == http.StatusTooManyRequests {
			if first429At == 0 {
				first429At = i + 1
			}
			got429++
			if rec.Header().Get("Retry-After") == "" {
				t.Fatal("429 without Retry-After header")
			}
		} else if rec.Code == http.StatusOK {
			got200++
		} else {
			t.Fatalf("unexpected status %d at request %d", rec.Code, i+1)
		}
	}
	if got200 < rateLimitBurst-2 {
		t.Errorf("too few allowed: %d, want >= %d", got200, rateLimitBurst-2)
	}
	if got429 == 0 {
		t.Error("no 429 after exceeding the burst")
	}
	// The burst budget must be exhausted first.
	if first429At <= rateLimitBurst-2 {
		t.Errorf("429 at request %d, before burst was exhausted", first429At)
	}
}

func TestRateLimit_DistinctClientsAreIndependent(t *testing.T) {
	api := hardeningAPI(t)

	// Exhaust client A's budget.
	var last *httptest.ResponseRecorder
	for i := 0; i < rateLimitBurst+5; i++ {
		req := httptest.NewRequest(http.MethodGet, "/api/metrics", nil)
		addTestOperationKey(req)
		req.Header.Set("Authorization", "Bearer hard-token")
		req.RemoteAddr = "10.0.0.1:5555"
		last = httptest.NewRecorder()
		api.routes().ServeHTTP(last, req)
	}
	if last.Code != http.StatusTooManyRequests {
		t.Fatalf("client A should be limited, last = %d", last.Code)
	}
	// Client B is unaffected.
	req := httptest.NewRequest(http.MethodGet, "/api/metrics", nil)
	addTestOperationKey(req)
	req.Header.Set("Authorization", "Bearer hard-token")
	req.RemoteAddr = "10.0.0.2:5555"
	rec := httptest.NewRecorder()
	api.routes().ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("client B blocked: %d, want 200", rec.Code)
	}
}

// ── Global body cap ─────────────────────────────────────────────────────────

func TestGlobalBodyCap_AppliesToAllEndpoints(t *testing.T) {
	api := hardeningAPI(t)

	big := bytes.Repeat([]byte("a"), maxRequestBodyBytes+1)
	req := httptest.NewRequest(http.MethodPost, "/api/processes/kill", bytes.NewReader(big))
	addTestOperationKey(req)
	req.Header.Set("Authorization", "Bearer hard-token")
	req.Header.Set("Content-Type", "application/json")
	rec := httptest.NewRecorder()
	api.routes().ServeHTTP(rec, req)
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("oversized body on non-tunnel endpoint: %d, want 400", rec.Code)
	}
}

func TestAPIResponsesAreNotCacheable(t *testing.T) {
	api := hardeningAPI(t)
	rec := doAuth(t, api, http.MethodGet, "/api/metrics", "hard-token")
	if rec.Header().Get("Cache-Control") != "no-store" {
		t.Fatalf("Cache-Control=%q, want no-store", rec.Header().Get("Cache-Control"))
	}
	if rec.Header().Get("X-Content-Type-Options") != "nosniff" {
		t.Fatalf("X-Content-Type-Options=%q", rec.Header().Get("X-Content-Type-Options"))
	}
}

func TestHealthIsMinimalNonCacheableAndRateLimited(t *testing.T) {
	api := hardeningAPI(t)
	var last *httptest.ResponseRecorder
	for i := 0; i < rateLimitBurst+1; i++ {
		req := httptest.NewRequest(http.MethodGet, "/health", nil)
		req.RemoteAddr = "198.51.100.9:4321"
		last = httptest.NewRecorder()
		api.routes().ServeHTTP(last, req)
		if i == 0 {
			if last.Code != http.StatusOK || strings.TrimSpace(last.Body.String()) != `{"status":"ok"}` {
				t.Fatalf("health response=%d %q", last.Code, last.Body.String())
			}
			if last.Header().Get("Cache-Control") != "no-store" {
				t.Fatal("health response is cacheable")
			}
		}
	}
	if last == nil || last.Code != http.StatusTooManyRequests {
		t.Fatalf("health flood last status=%v, want 429", last)
	}
}

func TestDetailedStatusIsPrivateByDefault(t *testing.T) {
	api := hardeningAPI(t)
	unauthorized := httptest.NewRecorder()
	api.routes().ServeHTTP(unauthorized, httptest.NewRequest(http.MethodGet, "/status", nil))
	if unauthorized.Code != http.StatusUnauthorized {
		t.Fatalf("private status without token=%d, want 401", unauthorized.Code)
	}
	authorized := doAuth(t, api, http.MethodGet, "/status", "hard-token")
	if authorized.Code != http.StatusOK {
		t.Fatalf("private status with token=%d, want 200", authorized.Code)
	}
	if authorized.Header().Get("Content-Security-Policy") == "" || authorized.Header().Get("X-Frame-Options") != "DENY" {
		t.Fatal("status page security headers missing")
	}
}

func TestDetailedStatusPublicOptInDoesNotExposeAPIStatus(t *testing.T) {
	api := hardeningAPI(t)
	api.cfg.PublicStatus = true
	public := httptest.NewRecorder()
	api.routes().ServeHTTP(public, httptest.NewRequest(http.MethodGet, "/status", nil))
	if public.Code != http.StatusOK {
		t.Fatalf("opt-in public status=%d, want 200", public.Code)
	}
	privateAPI := httptest.NewRecorder()
	api.routes().ServeHTTP(privateAPI, httptest.NewRequest(http.MethodGet, "/api/status", nil))
	if privateAPI.Code != http.StatusUnauthorized {
		t.Fatalf("API status without token=%d, want 401", privateAPI.Code)
	}
}

// ── Access log hygiene ──────────────────────────────────────────────────────

func TestAccessLog_DoesNotLeakSecrets(t *testing.T) {
	api := hardeningAPI(t)

	var buf bytes.Buffer
	old := log.Writer()
	log.SetOutput(&buf)
	// Valid Bearer token + a secret smuggled in the query string: the log
	// must show the request but never the query contents.
	rec := doAuth(t, api, http.MethodGet, "/api/metrics?token=super-secret&x=1", "hard-token")
	log.SetOutput(old)
	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d", rec.Code)
	}

	out := buf.String()
	if !strings.Contains(out, "access ") {
		t.Fatalf("no access log line written; log=\n%s", out)
	}
	if !strings.Contains(out, "GET /api/metrics 200") {
		t.Errorf("access line missing method/path/status; log=\n%s", out)
	}
	if strings.Contains(out, "super-secret") {
		t.Error("token leaked into the access log!")
	}
	if strings.Contains(out, "?") {
		t.Error("query string leaked into the access log!")
	}
}
