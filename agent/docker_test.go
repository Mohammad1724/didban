package main

import (
	"encoding/json"
	"net"
	"net/http"
	"path/filepath"
	"strings"
	"sync/atomic"
	"testing"
)

// fakeDockerDaemon serves a container listing and records whether the agent
// attempted to access the secret-bearing /inspect API.
func fakeDockerDaemon(t *testing.T, inspectCalls *atomic.Int32) string {
	t.Helper()
	dir := t.TempDir()
	sock := filepath.Join(dir, "docker.sock")
	l, err := net.Listen("unix", sock)
	if err != nil {
		t.Fatal(err)
	}
	mux := http.NewServeMux()
	mux.HandleFunc("/v1.41/containers/json", func(w http.ResponseWriter, r *http.Request) {
		_ = json.NewEncoder(w).Encode([]map[string]any{{
			"Id": "abc123def456789", "Names": []string{"/didban-tunnel-42"},
			"Image": "stormotron/narnia:0.0.3", "State": "running",
			"Status": "Up 2 hours", "Created": 1700000000, "Ports": []any{},
		}})
	})
	mux.HandleFunc("/v1.41/containers/", func(w http.ResponseWriter, r *http.Request) {
		inspectCalls.Add(1)
		_ = json.NewEncoder(w).Encode(map[string]any{
			"Config": map[string]any{"Env": []string{"PASSWORD=must-not-leak"}},
		})
	})
	srv := &http.Server{Handler: mux}
	go func() { _ = srv.Serve(l) }()
	t.Cleanup(func() { _ = srv.Close() })
	return sock
}

func TestValidateDockerIdentifier(t *testing.T) {
	valid := []string{"abcdef123456", "web-1", "stack_service.2", "A"}
	for _, value := range valid {
		if got, err := validateDockerIdentifier(value); err != nil || got != value {
			t.Errorf("valid identifier %q rejected: got=%q err=%v", value, got, err)
		}
	}
	invalid := []string{"", "../containers/victim/stop", "name/restart", "name?x=1", " name with spaces ", strings.Repeat("a", 129)}
	for _, value := range invalid {
		if _, err := validateDockerIdentifier(value); err == nil {
			t.Errorf("unsafe identifier %q accepted", value)
		}
	}
}

func TestGetDockerContainersNeverInspectsOrReturnsEnvironment(t *testing.T) {
	oldSock := dockerSockPath
	var inspectCalls atomic.Int32
	dockerSockPath = fakeDockerDaemon(t, &inspectCalls)
	t.Cleanup(func() { dockerSockPath = oldSock })

	summary := GetDockerContainers()
	if !summary.Installed || len(summary.Containers) != 1 {
		t.Fatalf("unexpected summary: %+v", summary)
	}
	container := summary.Containers[0]
	if container.Name != "didban-tunnel-42" || container.Image != "stormotron/narnia:0.0.3" {
		t.Fatalf("unexpected container identity: %+v", container)
	}
	if inspectCalls.Load() != 0 {
		t.Fatalf("secret-bearing Docker inspect endpoint called %d times", inspectCalls.Load())
	}
	encoded, err := json.Marshal(summary)
	if err != nil {
		t.Fatal(err)
	}
	if strings.Contains(string(encoded), "PASSWORD") || strings.Contains(string(encoded), `"env"`) {
		t.Fatalf("Docker environment leaked in API payload: %s", encoded)
	}
}
