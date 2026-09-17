package main

import (
	"os"
	"path/filepath"
	"testing"
)

func TestSecureWriteFileAtomicReplacesSymlinkWithoutFollowingIt(t *testing.T) {
	dir := t.TempDir()
	victim := filepath.Join(dir, "victim")
	destination := filepath.Join(dir, "token")
	if err := os.WriteFile(victim, []byte("do-not-touch"), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := os.Symlink(victim, destination); err != nil {
		t.Fatal(err)
	}
	if err := secureWriteFileAtomic(destination, []byte("new-secret"), 0o600); err != nil {
		t.Fatal(err)
	}
	victimData, _ := os.ReadFile(victim)
	if string(victimData) != "do-not-touch" {
		t.Fatalf("symlink target overwritten: %q", victimData)
	}
	info, err := os.Lstat(destination)
	if err != nil || !info.Mode().IsRegular() || info.Mode().Perm() != 0o600 {
		t.Fatalf("destination mode=%v err=%v", info.Mode(), err)
	}
}

func TestSecureMkdirAllWithinRejectsSymlinkComponent(t *testing.T) {
	base := t.TempDir()
	root := filepath.Join(base, "root")
	outside := filepath.Join(base, "outside")
	if err := os.MkdirAll(root, 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.MkdirAll(outside, 0o755); err != nil {
		t.Fatal(err)
	}
	link := filepath.Join(root, "tunnel")
	if err := os.Symlink(outside, link); err != nil {
		t.Skipf("symlinks unavailable: %v", err)
	}
	if err := secureMkdirAllWithin(root, filepath.Join(link, "nested"), 0o755); err == nil {
		t.Fatal("symlinked directory component accepted")
	}
	if _, err := os.Stat(filepath.Join(outside, "nested")); !os.IsNotExist(err) {
		t.Fatalf("directory created outside root: %v", err)
	}
}

func TestSecureMkdirAllWithinRejectsOutsidePath(t *testing.T) {
	base := t.TempDir()
	root := filepath.Join(base, "root")
	if err := secureMkdirAllWithin(root, filepath.Join(base, "root-evil"), 0o755); err == nil {
		t.Fatal("prefix-confusion path accepted")
	}
}

func TestSecureAppendFileRejectsSymlinkAndSecuresExistingMode(t *testing.T) {
	dir := t.TempDir()
	victim := filepath.Join(dir, "victim")
	link := filepath.Join(dir, "events")
	if err := os.WriteFile(victim, []byte("safe"), 0o644); err != nil {
		t.Fatal(err)
	}
	if err := os.Symlink(victim, link); err != nil {
		t.Skipf("symlinks unavailable: %v", err)
	}
	if _, err := secureAppendFile(link, []byte("bad"), 0o600); err == nil {
		t.Fatal("append followed a symlink")
	}
	got, _ := os.ReadFile(victim)
	if string(got) != "safe" {
		t.Fatalf("symlink target changed: %q", got)
	}
	if err := os.Remove(link); err != nil {
		t.Fatal(err)
	}
	if _, err := secureAppendFile(victim, []byte("-event"), 0o600); err != nil {
		t.Fatal(err)
	}
	info, err := os.Stat(victim)
	if err != nil || info.Mode().Perm() != 0o600 {
		t.Fatalf("append mode=%v err=%v", info.Mode(), err)
	}
}

func TestSecureWritersRejectSymlinkParent(t *testing.T) {
	root := t.TempDir()
	realDir := filepath.Join(root, "real")
	if err := os.Mkdir(realDir, 0o700); err != nil {
		t.Fatal(err)
	}
	linkDir := filepath.Join(root, "linked")
	if err := os.Symlink(realDir, linkDir); err != nil {
		t.Skipf("symlinks unavailable: %v", err)
	}
	path := filepath.Join(linkDir, "state")
	if err := secureWriteFileAtomic(path, []byte("bad"), 0o600); err == nil {
		t.Fatal("atomic writer accepted a symlink parent")
	}
	if _, err := secureAppendFile(path, []byte("bad"), 0o600); err == nil {
		t.Fatal("append writer accepted a symlink parent")
	}
	if _, err := os.Stat(filepath.Join(realDir, "state")); !os.IsNotExist(err) {
		t.Fatalf("symlink target was modified: %v", err)
	}
}

func TestSecureWriteFileAtomicLeavesNoTempFile(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "credential")
	if err := secureWriteFileAtomic(path, []byte("secret"), 0o600); err != nil {
		t.Fatal(err)
	}
	entries, err := os.ReadDir(dir)
	if err != nil {
		t.Fatal(err)
	}
	if len(entries) != 1 || entries[0].Name() != "credential" {
		t.Fatalf("unexpected directory entries: %+v", entries)
	}
}
