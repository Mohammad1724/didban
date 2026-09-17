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
