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
	return newAPI(cfg, mon, tm, nil, nil), configRoot
}

func applyReq(id, configPath, configContent, script string) TunnelApplyReq {
	return applyReqCore(id, "marzban", "iran", configPath, configContent, script)
}

func applyReqCore(id, core, role, configPath, configContent, script string) TunnelApplyReq {
	return TunnelApplyReq{
		ID:            id,
		Name:          "test-tunnel",
		Core:          core,
		Role:          role,
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

func TestApplyAndDeletePerTunnelDirectory(t *testing.T) {
	tm, root := newTestManager(t, DeployModeScripts)

	// Item 26 layout: <configRoot>/<id>/<file> — the parent directory does
	// not exist before the apply; the agent must create it, and delete
	// must remove both the file and the (now empty) tunnel directory.
	cfgPath := filepath.Join(root, "12", "server.toml")
	if _, err := tm.ApplyTunnel(applyReq("12", cfgPath, "x", "")); err != nil {
		t.Fatalf("ApplyTunnel: %v", err)
	}
	dir := filepath.Join(root, "12")
	if _, err := os.Stat(cfgPath); err != nil {
		t.Fatalf("config not written: %v", err)
	}

	// A non-tunnel-id sibling directory must survive the delete.
	other := filepath.Join(root, "misc")
	if err := os.MkdirAll(other, 0o755); err != nil {
		t.Fatal(err)
	}

	if err := tm.DeleteTunnel("12", ""); err != nil {
		t.Fatalf("DeleteTunnel: %v", err)
	}
	if _, err := os.Stat(cfgPath); !os.IsNotExist(err) {
		t.Fatal("sandboxed config not removed")
	}
	if _, err := os.Stat(dir); !os.IsNotExist(err) {
		t.Fatal("per-tunnel directory not removed")
	}
	if _, err := os.Stat(other); err != nil {
		t.Fatal("unrelated directory was removed")
	}
}

// withResidueLayouts redirects the Item 27 layout roots into temp dirs and
// restores them afterwards, so tests never touch /etc or /usr/local/bin.
func withResidueLayouts(t *testing.T) string {
	t.Helper()
	base := t.TempDir()
	old := struct {
		bin, didbanEtc, sysctl, legacy, log string
	}{binRoot, didbanEtc, sysctlRoot, legacyEtc, logRoot}
	binRoot = filepath.Join(base, "usr-local-bin")
	didbanEtc = filepath.Join(base, "etc-didban")
	sysctlRoot = filepath.Join(base, "etc-sysctl.d")
	legacyEtc = filepath.Join(base, "etc")
	logRoot = filepath.Join(base, "var-log")
	for _, d := range []string{binRoot, didbanEtc, sysctlRoot, legacyEtc, logRoot} {
		if err := os.MkdirAll(d, 0o755); err != nil {
			t.Fatal(err)
		}
	}
	t.Cleanup(func() {
		binRoot, didbanEtc, sysctlRoot, legacyEtc, logRoot = old.bin, old.didbanEtc, old.sysctl, old.legacy, old.log
	})
	return base
}

func touchFile(t *testing.T, path string) {
	t.Helper()
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, []byte("x"), 0o644); err != nil {
		t.Fatal(err)
	}
}

func mustExist(t *testing.T, path string) {
	t.Helper()
	if _, err := os.Stat(path); err != nil {
		t.Fatalf("expected %s to exist: %v", path, err)
	}
}

func mustNotExist(t *testing.T, path string) {
	t.Helper()
	if _, err := os.Stat(path); !os.IsNotExist(err) {
		t.Fatalf("expected %s to be gone, stat err = %v", path, err)
	}
}

func TestIptablesChainHashMatchesApp(t *testing.T) {
	// The app computes SHA-256("<id>") and takes the first 8 bytes as hex.
	// Pin the exact vector so the Go and Kotlin sides cannot drift apart:
	// the iptables chain names are derived from this on both ends.
	const want = "73475cb40a568e8d" // hex(sha256("42")[:8])
	if got := iptablesChainHash("42"); got != want {
		t.Fatalf("iptablesChainHash(42) = %q, want %q", got, want)
	}
	if len(want) != 16 {
		t.Fatalf("hash must be 16 hex chars, got %q", want)
	}
}

func TestDeleteTunnelRemovesNarniaResidue(t *testing.T) {
	withResidueLayouts(t)
	tm, root := newTestManager(t, DeployModeScripts)

	fw := filepath.Join(didbanEtc, "narnia-42-iran-fw.sh")
	log := filepath.Join(logRoot, "didban-narnia-42.log")
	dropIn := filepath.Join(sysctlRoot, "99-didban-narnia.conf")
	touchFile(t, fw)
	touchFile(t, log)
	touchFile(t, dropIn)

	if _, err := tm.ApplyTunnel(applyReqCore("42", "NARNIA", "iran", filepath.Join(root, "42", "ref.conf"), "# ref", "")); err != nil {
		t.Fatalf("ApplyTunnel: %v", err)
	}
	if err := tm.DeleteTunnel("42", ""); err != nil {
		t.Fatalf("DeleteTunnel: %v", err)
	}
	mustNotExist(t, fw)
	mustNotExist(t, log)
	mustNotExist(t, dropIn) // no other NARNIA meta → shared drop-in removed
}

func TestDeleteTunnelKeepsSharedDropInWhileSiblingRemains(t *testing.T) {
	withResidueLayouts(t)
	tm, root := newTestManager(t, DeployModeScripts)

	dropIn := filepath.Join(sysctlRoot, "99-didban-narnia.conf")
	touchFile(t, dropIn)

	if _, err := tm.ApplyTunnel(applyReqCore("42", "NARNIA", "iran", filepath.Join(root, "42", "ref.conf"), "# ref", "")); err != nil {
		t.Fatalf("ApplyTunnel: %v", err)
	}
	if _, err := tm.ApplyTunnel(applyReqCore("43", "NARNIA", "foreign", filepath.Join(root, "43", "ref.conf"), "# ref", "")); err != nil {
		t.Fatalf("ApplyTunnel: %v", err)
	}
	if err := tm.DeleteTunnel("42", ""); err != nil {
		t.Fatalf("DeleteTunnel: %v", err)
	}
	mustExist(t, dropIn) // sibling NARNIA still registered → drop-in kept
	if err := tm.DeleteTunnel("43", ""); err != nil {
		t.Fatalf("DeleteTunnel: %v", err)
	}
	mustNotExist(t, dropIn)
}

func TestDeleteTunnelRemovesIptablesRulesAndChains(t *testing.T) {
	withResidueLayouts(t)
	tm, root := newTestManager(t, DeployModeScripts)

	hash := iptablesChainHash("42")
	rules := filepath.Join(didbanEtc, "iptables-"+hash+"-rules.sh")
	dropIn := filepath.Join(sysctlRoot, "99-didban-iptables.conf")
	touchFile(t, rules)
	touchFile(t, dropIn)

	if _, err := tm.ApplyTunnel(applyReqCore("42", "IPTABLES", "iran", filepath.Join(root, "42", "ref.conf"), "# ref", "")); err != nil {
		t.Fatalf("ApplyTunnel: %v", err)
	}
	if err := tm.DeleteTunnel("42", ""); err != nil {
		t.Fatalf("DeleteTunnel: %v", err)
	}
	mustNotExist(t, rules)
	mustNotExist(t, dropIn)
}

func TestDeleteTunnelKeepsSharedBinaryWhileSiblingRemains(t *testing.T) {
	withResidueLayouts(t)
	tm, root := newTestManager(t, DeployModeScripts)

	bin := coreBinaryPath("BACKPACK", "iran")
	touchFile(t, bin)

	if _, err := tm.ApplyTunnel(applyReqCore("42", "BACKPACK", "iran", filepath.Join(root, "42", "a.conf"), "# ref", "")); err != nil {
		t.Fatalf("ApplyTunnel: %v", err)
	}
	if _, err := tm.ApplyTunnel(applyReqCore("43", "BACKPACK", "foreign", filepath.Join(root, "43", "b.conf"), "# ref", "")); err != nil {
		t.Fatalf("ApplyTunnel: %v", err)
	}
	if err := tm.DeleteTunnel("42", ""); err != nil {
		t.Fatalf("DeleteTunnel: %v", err)
	}
	mustExist(t, bin) // sibling BACKPACK still registered → binary kept
	if err := tm.DeleteTunnel("43", ""); err != nil {
		t.Fatalf("DeleteTunnel: %v", err)
	}
	mustNotExist(t, bin)
}

func TestDeleteTunnelFRPRoleSpecificBinaries(t *testing.T) {
	withResidueLayouts(t)
	tm, root := newTestManager(t, DeployModeScripts)

	frps := coreBinaryPath("FRP", "foreign")
	frpc := coreBinaryPath("FRP", "iran")
	touchFile(t, frps)
	touchFile(t, frpc)

	// Only the foreign (frps) side exists for tunnel 42.
	if _, err := tm.ApplyTunnel(applyReqCore("42", "FRP", "foreign", filepath.Join(root, "42", "a.conf"), "# ref", "")); err != nil {
		t.Fatalf("ApplyTunnel: %v", err)
	}
	if err := tm.DeleteTunnel("42", ""); err != nil {
		t.Fatalf("DeleteTunnel: %v", err)
	}
	mustNotExist(t, frps) // last foreign-role FRP → frps removed
	mustExist(t, frpc)    // no iran-role FRP ever existed → frpc untouched
}

func TestDeleteTunnelRemovesLegacyConfigAndDir(t *testing.T) {
	withResidueLayouts(t)
	tm, root := newTestManager(t, DeployModeScripts)

	legacyFile := filepath.Join(legacyEtc, "backpack/server.toml")
	legacyDir := filepath.Join(legacyEtc, "backpack")
	touchFile(t, legacyFile)

	if _, err := tm.ApplyTunnel(applyReqCore("42", "BACKPACK", "iran", filepath.Join(root, "42", "a.conf"), "# ref", "")); err != nil {
		t.Fatalf("ApplyTunnel: %v", err)
	}
	if err := tm.DeleteTunnel("42", ""); err != nil {
		t.Fatalf("DeleteTunnel: %v", err)
	}
	mustNotExist(t, legacyFile)
	mustNotExist(t, legacyDir)
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
	if body != nil || method == http.MethodPost || method == http.MethodDelete {
		req.Header.Set("Content-Type", "application/json")
		addTestOperationKey(req)
	}
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
	addTestOperationKey(req)
	req.Header.Set("Authorization", "Bearer test-token")
	req.Header.Set("Content-Type", "application/json")
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
