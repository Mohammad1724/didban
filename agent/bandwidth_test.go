package main

import (
	"bytes"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func newBandwidthTestAPI(t *testing.T) *API {
	t.Helper()
	dir := t.TempDir()
	cfg := &Config{Token: "bw-token", DataDir: dir}
	return newAPI(cfg, NewMonitor(cfg), NewTunnelManager(dir, dir+"/configs", DeployModeScripts), nil)
}

func TestBandwidthDownloadStreamsExactBytes(t *testing.T) {
	api := newBandwidthTestAPI(t)
	rec := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodGet, "/api/bandwidth/download?bytes=200000", nil)
	req.Header.Set("Authorization", "Bearer bw-token")
	api.routes().ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, body=%s", rec.Code, rec.Body.String())
	}
	if got := rec.Header().Get("Content-Length"); got != "200000" {
		t.Fatalf("Content-Length = %q", got)
	}
	if rec.Body.Len() != 200000 {
		t.Fatalf("streamed %d bytes, want 200000", rec.Body.Len())
	}
}

// disconnectingWriter captures the headers, then fails on the first write —
// simulating a client that closed the connection, so the handler must stop
// streaming (used to test the size clamp without buffering 200 MB in memory).
type disconnectingWriter struct {
	header http.Header
	wrote  bool
}

func (d *disconnectingWriter) Header() http.Header         { return d.header }
func (d *disconnectingWriter) Write(p []byte) (int, error) { d.wrote = true; return 0, io.EOF }
func (d *disconnectingWriter) WriteHeader(status int)      {}

func TestBandwidthDownloadDefaultsAndCaps(t *testing.T) {
	api := newBandwidthTestAPI(t)

	// Default size.
	dw := &disconnectingWriter{header: http.Header{}}
	req := httptest.NewRequest(http.MethodGet, "/api/bandwidth/download", nil)
	req.Header.Set("Authorization", "Bearer bw-token")
	api.routes().ServeHTTP(dw, req)
	if got := dw.header.Get("Content-Length"); got != "10485760" {
		t.Fatalf("default Content-Length = %q", got)
	}

	// Oversized request is clamped to the cap (streaming stops immediately).
	dw = &disconnectingWriter{header: http.Header{}}
	req = httptest.NewRequest(http.MethodGet, "/api/bandwidth/download?bytes=999999999999", nil)
	req.Header.Set("Authorization", "Bearer bw-token")
	api.routes().ServeHTTP(dw, req)
	if got := dw.header.Get("Content-Length"); got != "209715200" {
		t.Fatalf("clamped Content-Length = %q", got)
	}
	if !dw.wrote {
		t.Fatal("handler never attempted to stream")
	}

	// Below block size is rejected.
	rec := httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodGet, "/api/bandwidth/download?bytes=1000", nil)
	req.Header.Set("Authorization", "Bearer bw-token")
	api.routes().ServeHTTP(rec, req)
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("small bytes: status = %d, want 400", rec.Code)
	}

	// Non-numeric rejected.
	rec = httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodGet, "/api/bandwidth/download?bytes=abc", nil)
	req.Header.Set("Authorization", "Bearer bw-token")
	api.routes().ServeHTTP(rec, req)
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("bad bytes: status = %d, want 400", rec.Code)
	}
}

func TestBandwidthDownloadRequiresAuth(t *testing.T) {
	api := newBandwidthTestAPI(t)
	rec := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodGet, "/api/bandwidth/download?bytes=70000", nil)
	api.routes().ServeHTTP(rec, req)
	if rec.Code != http.StatusUnauthorized {
		t.Fatalf("status = %d, want 401", rec.Code)
	}
}

func TestBandwidthDownloadMethodNotAllowed(t *testing.T) {
	api := newBandwidthTestAPI(t)
	rec := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPost, "/api/bandwidth/download", nil)
	req.Header.Set("Authorization", "Bearer bw-token")
	api.routes().ServeHTTP(rec, req)
	if rec.Code != http.StatusMethodNotAllowed {
		t.Fatalf("status = %d, want 405", rec.Code)
	}
}

func TestBandwidthUploadReportsBytes(t *testing.T) {
	api := newBandwidthTestAPI(t)
	body := bytes.Repeat([]byte("x"), 123456)
	rec := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPost, "/api/bandwidth/upload", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer bw-token")
	api.routes().ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, body=%s", rec.Code, rec.Body.String())
	}
	if !strings.Contains(rec.Body.String(), `"received_bytes":123456`) {
		t.Fatalf("body = %s", rec.Body.String())
	}
}

func TestBandwidthUploadMethodNotAllowed(t *testing.T) {
	api := newBandwidthTestAPI(t)
	rec := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodGet, "/api/bandwidth/upload", nil)
	req.Header.Set("Authorization", "Bearer bw-token")
	api.routes().ServeHTTP(rec, req)
	if rec.Code != http.StatusMethodNotAllowed {
		t.Fatalf("status = %d, want 405", rec.Code)
	}
}

// Ensure the downloader side sees the exact streamed content length
// independent of the recorder (real io.ReadAll over the recorded body).
func TestBandwidthDownloadContentMatchesLength(t *testing.T) {
	api := newBandwidthTestAPI(t)
	rec := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodGet, "/api/bandwidth/download?bytes=131072", nil)
	req.Header.Set("Authorization", "Bearer bw-token")
	api.routes().ServeHTTP(rec, req)

	data, err := io.ReadAll(rec.Result().Body)
	if err != nil {
		t.Fatal(err)
	}
	if len(data) != 131072 {
		t.Fatalf("read %d bytes, want 131072", len(data))
	}
}
