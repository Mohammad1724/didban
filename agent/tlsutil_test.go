package main

import (
	"os"
	"path/filepath"
	"testing"
)

func TestEnsureSelfSignedRejectsCredentialSymlinks(t *testing.T) {
	dir := t.TempDir()
	victim := filepath.Join(dir, "victim-key")
	if err := os.WriteFile(victim, []byte("do-not-read"), 0o644); err != nil {
		t.Fatal(err)
	}
	keyPath := filepath.Join(dir, "key.pem")
	if err := os.Symlink(victim, keyPath); err != nil {
		t.Skipf("symlinks unavailable: %v", err)
	}
	if _, err := ensureSelfSigned(filepath.Join(dir, "cert.pem"), keyPath); err == nil {
		t.Fatal("TLS key symlink accepted")
	}
	info, err := os.Stat(victim)
	if err != nil {
		t.Fatal(err)
	}
	if info.Mode().Perm() != 0o644 {
		t.Fatalf("symlink target permissions changed to %o", info.Mode().Perm())
	}
}

func TestEnsureSelfSignedSecuresExistingCredentialModes(t *testing.T) {
	dir := t.TempDir()
	certPath := filepath.Join(dir, "cert.pem")
	keyPath := filepath.Join(dir, "key.pem")
	if _, err := ensureSelfSigned(certPath, keyPath); err != nil {
		t.Fatal(err)
	}
	if err := os.Chmod(certPath, 0o666); err != nil {
		t.Fatal(err)
	}
	if err := os.Chmod(keyPath, 0o666); err != nil {
		t.Fatal(err)
	}
	if _, err := ensureSelfSigned(certPath, keyPath); err != nil {
		t.Fatal(err)
	}
	certInfo, _ := os.Stat(certPath)
	keyInfo, _ := os.Stat(keyPath)
	if certInfo.Mode().Perm() != 0o644 || keyInfo.Mode().Perm() != 0o600 {
		t.Fatalf("cert=%o key=%o", certInfo.Mode().Perm(), keyInfo.Mode().Perm())
	}
}

func TestEnsurePrivateDirectoryRejectsSymlinkAndTightensMode(t *testing.T) {
	base := t.TempDir()
	realDir := filepath.Join(base, "real")
	if err := os.Mkdir(realDir, 0o755); err != nil {
		t.Fatal(err)
	}
	link := filepath.Join(base, "data")
	if err := os.Symlink(realDir, link); err != nil {
		t.Skipf("symlinks unavailable: %v", err)
	}
	if err := ensurePrivateDirectory(link); err == nil {
		t.Fatal("symlink data directory accepted")
	}
	if err := ensurePrivateDirectory(realDir); err != nil {
		t.Fatal(err)
	}
	info, _ := os.Stat(realDir)
	if info.Mode().Perm() != 0o700 {
		t.Fatalf("mode=%o", info.Mode().Perm())
	}
}
