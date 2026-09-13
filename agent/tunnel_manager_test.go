package main

import (
	"bytes"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// newTestManager builds a TunnelManager rooted in a temp dir with the settle
// delay disabled so tests do not sleep.
func newTestManager(t *testing.T, deployMode DeployMode) (*TunnelManager, string) {
	t.Helper()
	dir := t.TempDir()
	dataDir := filepath.Join(dir, "data")
	configRoot := filepath.Join(dir, "configs")
	tm := NewTunnelManager(dataDir, configRoot, deployMode)
	tm.settleDelay = 0
	return tm, configRoot
}

func newTestAPI(t *testing.T, deployMode DeployMode) (*API, string) {
	t.Helper()
	dir := t.TempDir()
	dataDir := filepath.Join(dir, "data")
	if err := os.MkdirAll(dataDir, 0o755); err != nil {
		t.Fatal(err)
	}
	configRoot := filepath.Join(dir, "configs")
	cfg := &Config{Token: "test-token", DataDir: dataDir}
	mon := NewMonitor(cfg)
	tm := NewTunnelManager(dataDir, configRoot, deployMode)
	tm.settleDelay = 0
	return newAPI(cfg, mon, tm), configRoot
}

func applyReq(id, configPath, configContent, script string) TunnelApplyReq {
	return TunnelApplyReq{
		ID:            id,
		Name:          "test-tunnel",
		Core:          "marzban",
		Role:          "iran",
		ConfigContent: configContent,
		ConfigPath:    configPath,
		ExecScript:    script,
	}
}

// ── ID / service name validation ─────────────────────────────────────────────

func TestValidateTunnelID(t *testing.T) {
	valid := []string{"1", "a", "abc-123_X", strings.Repeat("x", 64)}
	invalid := []string{
		"", "../etc", "a/b", "a b", "-x", "_x", "a.b", "a\n",
		strings.Repeat("x", 65), "café", "id;rm", "$(x)",
	}
	for _, id := range valid {
		if err := ValidateTunnelID(id); err != nil {
			t.Errorf("ValidateTunnelID(%q) unexpected error: %v", id, err)
		}
	}
	for _, id := range invalid {
		if err := ValidateTunnelID(id); !errors.Is(err, errInvalidTunnelID) {
			t.Errorf("ValidateTunnelID(%q) = %v, want errInvalidTunnelID", id, err)
		}
	}
}

func TestResolveServiceName(t *testing.T) {
	tm, _ := newTestManager(t, DeployModeScripts)

	got, err := tm.resolveServiceName("1", "")
	if err != nil || got != "didban-tunnel-1" {
		t.Fatalf("default service name = %q, %v; want didban-tunnel-1", got, err)
	}
	got, err = tm.resolveServiceName("1", "my-tunnel_1")
	if err != nil || got != "my-tunnel_1" {
		t.Fatalf("custom service name = %q, %v", got, err)
	}
	for _, bad := range []string{"../etc", "a/b", "a b", "a.b", "-x", strings.Repeat("x", 65)} {
		if _, err := tm.resolveServiceName("1", bad); !errors.Is(err, errInvalidServiceName) {
			t.Errorf("resolveServiceName(%q) = %v, want errInvalidServiceName", bad, err)
		}
	}
}

// ── Config path sandbox ──────────────────────────────────────────────────────

func TestResolveConfigPath(t *testing.T) {
	tm, root := newTestManager(t, DeployModeScripts)

	// Inside the sandbox: accepted.
	got, err := tm.resolveConfigPath(filepath.Join(root, "abc.toml"))
	if err != nil {
		t.Fatalf("inside sandbox: %v", err)
	}
	if got != filepath.Join(root, "abc.toml") {
		t.Fatalf("resolved = %q", got)
	}

	// Traversal outside the sandbox: rejected.
	outside := filepath.Join(t.TempDir(), "outside.toml")
	cases := []string{
		outside, // absolute outside
		filepath.Join(root, "..", "..", "x.toml"),    // parent traversal
		filepath.Join(root, "..", "other", "x.toml"), // sibling dir
		"relative/path.toml",                         // not absolute
		"",                                           // missing
	}
	for _, p := range cases {
		if _, err := tm.resolveConfigPath(p); !errors.Is(err, errConfigPathOutside) {
			t.Errorf("resolveConfigPath(%q) = %v, want errConfigPathOutside", p, err)
		}
	}
}

func TestResolveConfigPathSymlinkEscape(t *testing.T) {
	tm, root := newTestManager(t, DeployModeScripts)

	outsideDir := t.TempDir()
	link := filepath.Join(root, "escape")
	if err := os.Symlink(outsideDir, link); err != nil {
		t.Skipf("symlinks not supported here: %v", err)
	}
	target := filepath.Join(link, "secret.toml")
	if _, err := tm.resolveConfigPath(target); !errors.Is(err, errConfigPathOutside) {
		t.Fatalf("symlink escape accepted: err=%v", err)
	}
}

// ── ApplyTunnel behaviour ────────────────────────────────────────────────────

func TestApplyTunnelWritesSandboxedConfig(t *testing.T) {
	tm, root := newTestManager(t, DeployModeScripts)

	cfgPath := filepath.Join(root, "t1.toml")
	res, err := tm.ApplyTunnel(applyReq("1", cfgPath, "port = 443", ""))
	if err != nil {
		t.Fatalf("ApplyTunnel: %v", err)
	}
	// No systemd service exists in the test environment, so the tunnel is
	// reported inactive — the request itself must have succeeded.
	if res.ServiceName != "didban-tunnel-1" {
		t.Errorf("ServiceName = %q", res.ServiceName)
	}
	data, err := os.ReadFile(cfgPath)
	if err != nil || string(data) != "port = 443" {
		t.Fatalf("config file not written in sandbox: %v %q", err, data)
	}
	// Metadata must be registered.
	if _, err := os.Stat(filepath.Join(tm.tunnelsDir, "meta-1.json")); err != nil {
		t.Fatalf("meta file missing: %v", err)
	}
	// Nothing may have been written outside the sandbox.
	if _, err := os.Stat(cfgPath + ".escape"); err == nil {
		t.Fatal("stray file outside sandbox")
	}
}

func TestApplyTunnelRejectsConfigOutsideSandbox(t *testing.T) {
	tm, _ := newTestManager(t, DeployModeScripts)

	outside := filepath.Join(t.TempDir(), "pwned.toml")
	_, err := tm.ApplyTunnel(applyReq("1", outside, "x", ""))
	if !errors.Is(err, errConfigPathOutside) {
		t.Fatalf("err = %v, want errConfigPathOutside", err)
	}
	if _, statErr := os.Stat(outside); !os.IsNotExist(statErr) {
		t.Fatal("file was written outside the sandbox!")
	}
}

func TestApplyTunnelConfigOnlyBlocksScript(t *testing.T) {
	tm, _ := newTestManager(t, DeployModeConfigOnly)

	marker := filepath.Join(t.TempDir(), "ran.marker")
	res, err := tm.ApplyTunnel(applyReq("2", "", "", "touch "+marker))
	if err != nil {
		t.Fatalf("ApplyTunnel: %v", err)
	}
	if res.Success {
		t.Fatal("script must not be treated as successful in config-only mode")
	}
	if !strings.Contains(res.Error, "config-only") {
		t.Errorf("error message = %q, want mention of config-only", res.Error)
	}
	if _, err := os.Stat(marker); !os.IsNotExist(err) {
		t.Fatal("script WAS executed in config-only mode!")
	}
	// Metadata must not be registered for a blocked deploy.
	if _, err := os.Stat(filepath.Join(tm.tunnelsDir, "meta-2.json")); !os.IsNotExist(err) {
		t.Fatal("meta registered for blocked deploy")
	}
}

func TestApplyTunnelScriptsModeExecutesScript(t *testing.T) {
	tm, _ := newTestManager(t, DeployModeScripts)

	marker := filepath.Join(t.TempDir(), "ran.marker")
	res, err := tm.ApplyTunnel(applyReq("3", "", "", "touch "+marker))
	if err != nil {
		t.Fatalf("ApplyTunnel: %v", err)
	}
	if _, err := os.Stat(marker); err != nil {
		t.Fatalf("script did not execute (marker missing): %v", err)
	}
	// Success reflects service liveness; without systemd the service is
	// inactive, but the deploy itself ran end-to-end.
	if res.Status == "" {
		t.Error("expected a status string")
	}
}

func TestApplyTunnelScriptTooLarge(t *testing.T) {
	tm, _ := newTestManager(t, DeployModeScripts)
	script := strings.Repeat("echo x\n", 10000) // 70 KB > 64 KB limit
	if _, err := tm.ApplyTunnel(applyReq("4", "", "", script)); err == nil {
		t.Fatal("oversized script accepted")
	}
}

func TestApplyTunnelConfigTooLarge(t *testing.T) {
	tm, root := newTestManager(t, DeployModeScripts)
	big := strings.Repeat("a", maxConfigContentLen+1)
	if _, err := tm.ApplyTunnel(applyReq("5", filepath.Join(root, "big.toml"), big, "")); err == nil {
		t.Fatal("oversized config accepted")
	}
}

func TestApplyTunnelRejectsBadMetadata(t *testing.T) {
	tm, root := newTestManager(t, DeployModeScripts)
	req := applyReq("6", filepath.Join(root, "t6.toml"), "x", "")
	req.Name = strings.Repeat("n", maxFieldNameLen+1)
	if _, err := tm.ApplyTunnel(req); err == nil {
		t.Fatal("oversized metadata accepted")
	}
}

// ── Status / start / stop / delete semantics ─────────────────────────────────

func TestGetTunnelStatusUnknown(t *testing.T) {
	tm, _ := newTestManager(t, DeployModeScripts)
	if _, err := tm.GetTunnelStatus("999"); !errors.Is(err, errTunnelNotFound) {
		t.Fatalf("err = %v, want errTunnelNotFound", err)
	}
}

func TestGetTunnelStatusBadID(t *testing.T) {
	tm, _ := newTestManager(t, DeployModeScripts)
	if _, err := tm.GetTunnelStatus("../etc"); !errors.Is(err, errInvalidTunnelID) {
		t.Fatalf("err = %v, want errInvalidTunnelID", err)
	}
}

func TestStartStopUnknownTunnel(t *testing.T) {
	tm, _ := newTestManager(t, DeployModeScripts)
	if _, err := tm.StartTunnel("777", ""); !errors.Is(err, errTunnelNotFound) {
		t.Fatalf("StartTunnel err = %v, want errTunnelNotFound", err)
	}
	if _, err := tm.StopTunnel("777", ""); !errors.Is(err, errTunnelNotFound) {
		t.Fatalf("StopTunnel err = %v, want errTunnelNotFound", err)
	}
}

func TestDeleteTunnelUnknownIsIdempotent(t *testing.T) {
	tm, _ := newTestManager(t, DeployModeScripts)
	if err := tm.DeleteTunnel("888", ""); err != nil {
		t.Fatalf("delete of unknown tunnel must be idempotent, got %v", err)
	}
	if err := tm.DeleteTunnel("../x", ""); !errors.Is(err, errInvalidTunnelID) {
		t.Fatalf("err = %v, want errInvalidTunnelID", err)
	}
}

func TestDeleteTunnelRemovesSandboxedConfig(t *testing.T) {
	tm, root := newTestManager(t, DeployModeScripts)

	cfgPath := filepath.Join(root, "t9.toml")
	if _, err := tm.ApplyTunnel(applyReq("9", cfgPath, "x", "")); err != nil {
		t.Fatalf("ApplyTunnel: %v", err)
	}
	if err := tm.DeleteTunnel("9", ""); err != nil {
		t.Fatalf("DeleteTunnel: %v", err)
	}
	if _, err := os.Stat(cfgPath); !os.IsNotExist(err) {
		t.Fatal("sandboxed config not removed")
	}
	if _, err := os.Stat(filepath.Join(tm.tunnelsDir, "meta-9.json")); !os.IsNotExist(err) {
		t.Fatal("meta not removed")
	}
}

// ── HTTP API layer ───────────────────────────────────────────────────────────

func doJSON(t *testing.T, h http.Handler, method, path, token string, body any) *httptest.ResponseRecorder {
	t.Helper()
	var r *bytes.Reader
	if body != nil {
		b, _ := json.Marshal(body)
		r = bytes.NewReader(b)
	} else if method == http.MethodPost {
		r = bytes.NewReader(nil)
	} else {
		r = bytes.NewReader(nil)
	}
	req := httptest.NewRequest(method, path, r)
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	return rec
}

func TestAPI_TunnelStatusUnknownID_404(t *testing.T) {
	api, _ := newTestAPI(t, DeployModeScripts)
	rec := doJSON(t, api.routes(), http.MethodGet, "/api/tunnel/status?id=999", "test-token", nil)
	if rec.Code != http.StatusNotFound {
		t.Fatalf("status = %d body=%s, want 404", rec.Code, rec.Body.String())
	}
}

func TestAPI_TunnelStatusPathTraversal_400(t *testing.T) {
	api, _ := newTestAPI(t, DeployModeScripts)
	rec := doJSON(t, api.routes(), http.MethodGet, "/api/tunnel/status?id=../etc/passwd", "test-token", nil)
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want 400", rec.Code)
	}
}

func TestAPI_TunnelStatusMissingID_400(t *testing.T) {
	api, _ := newTestAPI(t, DeployModeScripts)
	rec := doJSON(t, api.routes(), http.MethodGet, "/api/tunnel/status", "test-token", nil)
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want 400", rec.Code)
	}
}

func TestAPI_TunnelApplyEmptyID_400(t *testing.T) {
	api, _ := newTestAPI(t, DeployModeScripts)
	rec := doJSON(t, api.routes(), http.MethodPost, "/api/tunnel/apply", "test-token", TunnelApplyReq{})
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("status = %d body=%s, want 400", rec.Code, rec.Body.String())
	}
}

func TestAPI_TunnelApplyConfigOutsideSandbox_400(t *testing.T) {
	api, _ := newTestAPI(t, DeployModeScripts)
	req := applyReq("10", "/tmp/didban-test-pwned.toml", "x", "")
	rec := doJSON(t, api.routes(), http.MethodPost, "/api/tunnel/apply", "test-token", req)
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want 400", rec.Code)
	}
	if _, err := os.Stat("/tmp/didban-test-pwned.toml"); !os.IsNotExist(err) {
		t.Fatal("file written outside sandbox via API")
	}
}

func TestAPI_TunnelApplyConfigOnlyScriptBlocked(t *testing.T) {
	api, _ := newTestAPI(t, DeployModeConfigOnly)
	marker := filepath.Join(t.TempDir(), "m.marker")
	req := applyReq("11", "", "", "touch "+marker)
	rec := doJSON(t, api.routes(), http.MethodPost, "/api/tunnel/apply", "test-token", req)
	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want 200", rec.Code)
	}
	var res TunnelStatusResp
	if err := json.Unmarshal(rec.Body.Bytes(), &res); err != nil {
		t.Fatal(err)
	}
	if res.Success {
		t.Fatal("blocked deploy reported as success")
	}
	if _, err := os.Stat(marker); !os.IsNotExist(err) {
		t.Fatal("script executed in config-only mode")
	}
}

func TestAPI_TunnelApplyNoAuth_401(t *testing.T) {
	api, _ := newTestAPI(t, DeployModeScripts)
	rec := doJSON(t, api.routes(), http.MethodPost, "/api/tunnel/apply", "", TunnelApplyReq{ID: "1"})
	if rec.Code != http.StatusUnauthorized {
		t.Fatalf("status = %d, want 401", rec.Code)
	}
}

func TestAPI_TunnelApplyBodyCap(t *testing.T) {
	api, _ := newTestAPI(t, DeployModeScripts)
	// 3 MB body exceeds the 2 MB cap.
	big := strings.Repeat("a", 3*1024*1024)
	req := httptest.NewRequest(http.MethodPost, "/api/tunnel/apply", strings.NewReader(big))
	req.Header.Set("Authorization", "Bearer test-token")
	rec := httptest.NewRecorder()
	api.routes().ServeHTTP(rec, req)
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want 400 for oversized body", rec.Code)
	}
}

func TestAPI_TunnelApplyAuditEvents(t *testing.T) {
	api, _ := newTestAPI(t, DeployModeScripts)
	marker := filepath.Join(t.TempDir(), "audit.marker")
	req := applyReq("12", "", "", "touch "+marker)
	rec := doJSON(t, api.routes(), http.MethodPost, "/api/tunnel/apply", "test-token", req)
	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want 200", rec.Code)
	}

	// The marker must exist (script ran) and the audit trail must contain the
	// deploy_start + deploy result events.
	if _, err := os.Stat(marker); err != nil {
		t.Fatalf("script did not run: %v", err)
	}
	events, err := json.Marshal(api.mon.events.List(100))
	if err != nil {
		t.Fatal(err)
	}
	s := string(events)
	if !strings.Contains(s, "tunnel_deploy_start") {
		t.Error("tunnel_deploy_start event missing")
	}
	if !strings.Contains(s, "script_sha256=") {
		t.Error("script sha256 missing from audit event")
	}
	if !strings.Contains(s, "tunnel_deploy_ok") && !strings.Contains(s, "tunnel_deploy_failed") {
		t.Error("deploy result event missing")
	}
}
