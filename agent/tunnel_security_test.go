package main

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestSanitizeTunnelLogRedactsCredentials(t *testing.T) {
	raw := "token = \"alpha123\"\npassword: beta456\nchisel --auth user:gamma789 https://u:delta@example.com"
	safe := sanitizeTunnelLog(raw)
	for _, secret := range []string{"alpha123", "beta456", "gamma789", "delta"} {
		if strings.Contains(safe, secret) {
			t.Fatalf("secret %q leaked in %q", secret, safe)
		}
	}
}

func TestApplyTunnelConfigIsOwnerOnly(t *testing.T) {
	root := t.TempDir()
	mgr := NewTunnelManager(filepath.Join(root, "data"), filepath.Join(root, "configs"), DeployModeConfigOnly)
	mgr.settleDelay = 0
	path := filepath.Join(root, "configs", "secure", "config.toml")
	_, err := mgr.ApplyTunnel(TunnelApplyReq{
		ID: "secure", Name: "secure", Core: "test", Role: "iran",
		ConfigContent: "token = secret", ConfigPath: path,
	})
	if err != nil {
		t.Fatal(err)
	}
	info, err := os.Stat(path)
	if err != nil {
		t.Fatal(err)
	}
	if got := info.Mode().Perm(); got != 0o600 {
		t.Fatalf("config mode = %o, want 600", got)
	}
}
