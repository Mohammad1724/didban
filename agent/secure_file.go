package main

import (
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
	"syscall"
)

// secureWriteFileAtomic writes in the destination directory, fsyncs, applies
// the exact mode, then atomically renames. It never follows a destination
// symlink and does not expose partially-written credentials after a crash.
func ensurePrivateDirectory(path string) error {
	if err := os.MkdirAll(path, 0o700); err != nil {
		return err
	}
	info, err := os.Lstat(path)
	if err != nil {
		return err
	}
	if info.Mode()&os.ModeSymlink != 0 || !info.IsDir() {
		return fmt.Errorf("private data path is not a real directory")
	}
	return os.Chmod(path, 0o700)
}

// validateCredentialFile rejects symlinks and special files before any caller
// reads credential material. A missing path is reported as exists=false.
func validateCredentialFile(path string, mode os.FileMode) (exists bool, err error) {
	info, err := os.Lstat(path)
	if os.IsNotExist(err) {
		return false, nil
	}
	if err != nil {
		return false, err
	}
	if info.Mode()&os.ModeSymlink != 0 || !info.Mode().IsRegular() {
		return false, fmt.Errorf("credential path %s is not a regular file", path)
	}
	if err := os.Chmod(path, mode); err != nil {
		return false, err
	}
	return true, nil
}

// secureMkdirAllWithin creates a directory chain without accepting symlinks or
// non-directory components. root and dir must be absolute, and dir must remain
// within root after lexical cleaning.
func secureMkdirAllWithin(root, dir string, mode os.FileMode) error {
	root = filepath.Clean(root)
	dir = filepath.Clean(dir)
	if !filepath.IsAbs(root) || !filepath.IsAbs(dir) ||
		(dir != root && !strings.HasPrefix(dir, root+string(os.PathSeparator))) {
		return fmt.Errorf("directory %q is outside secure root %q", dir, root)
	}

	current := root
	parts := []string{}
	if dir != root {
		rel, err := filepath.Rel(root, dir)
		if err != nil {
			return err
		}
		parts = strings.Split(rel, string(os.PathSeparator))
	}
	for i := -1; i < len(parts); i++ {
		if i >= 0 {
			current = filepath.Join(current, parts[i])
		}
		info, err := os.Lstat(current)
		if os.IsNotExist(err) {
			if err := os.Mkdir(current, mode); err != nil && !os.IsExist(err) {
				return fmt.Errorf("create secure directory %s: %w", current, err)
			}
			info, err = os.Lstat(current)
		}
		if err != nil {
			return fmt.Errorf("inspect secure directory %s: %w", current, err)
		}
		if info.Mode()&os.ModeSymlink != 0 || !info.IsDir() {
			return fmt.Errorf("secure directory component %s is not a real directory", current)
		}
	}
	return nil
}

// secureOpenRegularRead opens a persistent file without following a final
// symlink and rejects devices, directories, sockets, and other special files.
func secureOpenRegularRead(path string) (*os.File, error) {
	if err := requireRealParent(path); err != nil {
		return nil, err
	}
	f, err := os.OpenFile(path, os.O_RDONLY|syscall.O_NOFOLLOW, 0)
	if err != nil {
		return nil, err
	}
	info, err := f.Stat()
	if err != nil || !info.Mode().IsRegular() {
		_ = f.Close()
		return nil, fmt.Errorf("read source is not a regular file")
	}
	return f, nil
}

func secureReadFile(path string, maxBytes int64) ([]byte, error) {
	if maxBytes < 1 {
		return nil, fmt.Errorf("invalid read limit")
	}
	f, err := secureOpenRegularRead(path)
	if err != nil {
		return nil, err
	}
	defer f.Close()
	limited := io.LimitReader(f, maxBytes+1)
	data, err := io.ReadAll(limited)
	if err != nil {
		return nil, err
	}
	if int64(len(data)) > maxBytes {
		return nil, fmt.Errorf("persistent file exceeds read limit")
	}
	return data, nil
}

// secureAppendFile appends without following a destination symlink and
// tightens an existing file's mode before writing.
func requireRealParent(path string) error {
	parent := filepath.Dir(path)
	info, err := os.Lstat(parent)
	if err != nil {
		return fmt.Errorf("inspect destination directory: %w", err)
	}
	if info.Mode()&os.ModeSymlink != 0 || !info.IsDir() {
		return fmt.Errorf("destination parent is not a real directory")
	}
	return nil
}

func secureAppendFile(path string, data []byte, mode os.FileMode) (int64, error) {
	if err := requireRealParent(path); err != nil {
		return 0, err
	}
	f, err := os.OpenFile(path, os.O_APPEND|os.O_CREATE|os.O_WRONLY|syscall.O_NOFOLLOW, mode)
	if err != nil {
		return 0, err
	}
	defer f.Close()
	info, err := f.Stat()
	if err != nil || !info.Mode().IsRegular() {
		return 0, fmt.Errorf("append destination is not a regular file")
	}
	if err := f.Chmod(mode); err != nil {
		return 0, err
	}
	if _, err := f.Write(data); err != nil {
		return 0, err
	}
	info, err = f.Stat()
	if err != nil {
		return 0, err
	}
	return info.Size(), nil
}

func secureWriteFileAtomic(path string, data []byte, mode os.FileMode) error {
	if err := requireRealParent(path); err != nil {
		return err
	}
	dir := filepath.Dir(path)
	f, err := os.CreateTemp(dir, ".didban-write-*")
	if err != nil {
		return err
	}
	tmp := f.Name()
	ok := false
	defer func() {
		_ = f.Close()
		if !ok {
			_ = os.Remove(tmp)
		}
	}()
	if err := f.Chmod(mode); err != nil {
		return err
	}
	if _, err := f.Write(data); err != nil {
		return err
	}
	if err := f.Sync(); err != nil {
		return err
	}
	if err := f.Close(); err != nil {
		return err
	}
	if err := os.Rename(tmp, path); err != nil {
		return fmt.Errorf("atomic replace: %w", err)
	}
	ok = true
	if d, err := os.Open(dir); err == nil {
		_ = d.Sync()
		_ = d.Close()
	}
	return nil
}
