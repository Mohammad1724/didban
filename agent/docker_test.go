package main

import (
	"encoding/json"
	"net"
	"net/http"
	"path/filepath"
	"reflect"
	"strings"
	"testing"
)

func TestFilterEnv(t *testing.T) {
	raw := []string{
		"PASSWORD=s3cr3t",
		"DB_PASSWORD=must-not-leak", // exact-key matching, not substring
		"PATH=/usr/local/bin",
		"NO_VALUE",           // no '='
		"PASSWORD2=also-not", // not in allowlist
		"=",                  // empty key
		"PASSWORD=",          // empty value
		"HOME=/root",
	}
	got := filterEnv(raw)
	want := map[string]string{"PASSWORD": "s3cr3t"}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("filterEnv = %v, want %v", got, want)
	}
	if filterEnv(nil) != nil {
		t.Fatal("empty input must yield nil (no env field in JSON)")
	}
}

// fakeDockerDaemon serves the endpoints GetDockerContainers uses on a unix
// socket, standing in for /var/run/docker.sock.
func fakeDockerDaemon(t *testing.T, inspectStatus int, inspectEnv []string) string {
	t.Helper()
	dir := t.TempDir()
	sock := filepath.Join(dir, "docker.sock")
	l, err := net.Listen("unix", sock)
	if err != nil {
		t.Fatal(err)
	}
	mux := http.NewServeMux()
	mux.HandleFunc("/v1.41/containers/json", func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode([]map[string]any{
			{
				"Id":      "abc123def456789",
				"Names":   []string{"/didban-tunnel-42"},
				"Image":   "stormotron/narnia:0.0.3",
				"State":   "running",
				"Status":  "Up 2 hours",
				"Created": 1700000000,
				"Ports":   []any{},
			},
		})
	})
	mux.HandleFunc("/v1.41/containers/", func(w http.ResponseWriter, r *http.Request) {
		if !strings.HasSuffix(r.URL.Path, "/inspect") {
			http.NotFound(w, r)
			return
		}
		if inspectStatus != http.StatusOK {
			w.WriteHeader(inspectStatus)
			return
		}
		json.NewEncoder(w).Encode(map[string]any{
			"Config": map[string]any{"Env": inspectEnv},
		})
	})
	srv := &http.Server{Handler: mux}
	go func() { _ = srv.Serve(l) }()
	t.Cleanup(func() { _ = srv.Close() })
	return sock
}

func TestGetDockerContainersEnrichesFilteredEnv(t *testing.T) {
	oldSock := dockerSockPath
	dockerSockPath = fakeDockerDaemon(t, http.StatusOK, []string{
		"PASSWORD=RealNarniaKey123",
		"PATH=/usr/local/sbin:/usr/local/bin",
		"INTERFACE=nvpn",
		"MTU=1400",
	})
	t.Cleanup(func() { dockerSockPath = oldSock })

	s := GetDockerContainers()
	if !s.Installed {
		t.Fatalf("daemon should be reachable, error: %s", s.Error)
	}
	if len(s.Containers) != 1 {
		t.Fatalf("want 1 container, got %d", len(s.Containers))
	}
	c := s.Containers[0]
	if c.Name != "didban-tunnel-42" || c.Image != "stormotron/narnia:0.0.3" {
		t.Fatalf("unexpected container identity: %+v", c)
	}
	want := map[string]string{"PASSWORD": "RealNarniaKey123"}
	if !reflect.DeepEqual(c.Env, want) {
		t.Fatalf("Env = %v, want %v (unrelated keys must never be exposed)", c.Env, want)
	}
}

func TestGetDockerContainersInspectFailureIsTolerated(t *testing.T) {
	// A daemon that answers the listing but 500s on inspect: the summary
	// must still succeed, with a nil (omitted) env.
	oldSock := dockerSockPath
	dockerSockPath = fakeDockerDaemon(t, http.StatusInternalServerError, nil)
	t.Cleanup(func() { dockerSockPath = oldSock })

	s := GetDockerContainers()
	if !s.Installed || len(s.Containers) != 1 {
		t.Fatalf("summary must survive inspect failures, got %+v", s)
	}
	if s.Containers[0].Env != nil {
		t.Fatalf("failed inspect must yield nil env, got %v", s.Containers[0].Env)
	}
}
