package main

import (
	"context"
	"encoding/json"
	"fmt"
	"net"
	"net/http"
	"os"
	"strings"
	"time"
)

// ContainerPort describes port mapping of a container.
type ContainerPort struct {
	IP          string `json:"ip,omitempty"`
	PrivatePort int    `json:"private_port"`
	PublicPort  int    `json:"public_port,omitempty"`
	Type        string `json:"type"`
}

// ContainerInfo describes a single Docker container.
type ContainerInfo struct {
	ID      string          `json:"id"`
	Name    string          `json:"name"`
	Image   string          `json:"image"`
	State   string          `json:"state"`  // running, exited, restarting, paused, dead
	Status  string          `json:"status"` // "Up 2 hours", "Exited (1) 5 mins ago"
	Created int64           `json:"created"`
	Ports   []ContainerPort `json:"ports"`
	// Env holds ONLY the allowlisted env keys (see dockerEnvAllowlist) from
	// the container's Config.Env — the full env of unrelated containers
	// (and its secrets) must never reach the API surface.
	Env map[string]string `json:"env,omitempty"`
}

// DockerSummary is returned to the API.
type DockerSummary struct {
	Installed  bool            `json:"installed"`
	Containers []ContainerInfo `json:"containers"`
	Error      string          `json:"error,omitempty"`
}

type rawDockerPort struct {
	IP          string `json:"IP"`
	PrivatePort int    `json:"PrivatePort"`
	PublicPort  int    `json:"PublicPort"`
	Type        string `json:"Type"`
}

type rawDockerContainer struct {
	ID      string          `json:"Id"`
	Names   []string        `json:"Names"`
	Image   string          `json:"Image"`
	State   string          `json:"State"`
	Status  string          `json:"Status"`
	Created int64           `json:"Created"`
	Ports   []rawDockerPort `json:"Ports"`
}

// dockerSockPath is overridable in tests (fake daemon on a temp socket).
var dockerSockPath = "/var/run/docker.sock"

// dockerEnvAllowlist: the only env keys exposed through the API. The app's
// tunnel discovery reads core credentials from these (Narnia ships its key
// as PASSWORD, mirroring upstream Narnia.sh).
var dockerEnvAllowlist = map[string]bool{
	"PASSWORD": true,
}

// maxEnvInspect bounds the extra /inspect calls per containers listing.
const maxEnvInspect = 50

// filterEnv keeps only non-empty allowlisted keys from a raw docker
// env list ("KEY=VALUE" strings). Matching is EXACT — DB_PASSWORD must
// never leak through as PASSWORD.
func filterEnv(raw []string) map[string]string {
	out := map[string]string{}
	for _, kv := range raw {
		key, val, ok := strings.Cut(kv, "=")
		if !ok || key == "" || val == "" {
			continue
		}
		if dockerEnvAllowlist[key] {
			out[key] = val
		}
	}
	if len(out) == 0 {
		return nil
	}
	return out
}

// inspectEnv fetches one container's Config.Env and returns the filtered
// subset (nil on any error — enrichment is best effort).
func inspectEnv(client *http.Client, id string) map[string]string {
	resp, err := client.Get("http://localhost/v1.41/containers/" + id + "/inspect")
	if err != nil {
		return nil
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil
	}
	var raw struct {
		Config struct {
			Env []string `json:"Env"`
		} `json:"Config"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&raw); err != nil {
		return nil
	}
	return filterEnv(raw.Config.Env)
}

func getDockerClient() (*http.Client, string, error) {
	sockPath := dockerSockPath
	if _, err := os.Stat(sockPath); err != nil {
		return nil, sockPath, fmt.Errorf("docker socket not found (%s)", sockPath)
	}

	client := &http.Client{
		Timeout: 10 * time.Second,
		Transport: &http.Transport{
			DialContext: func(ctx context.Context, _, _ string) (net.Conn, error) {
				var d net.Dialer
				return d.DialContext(ctx, "unix", sockPath)
			},
		},
	}
	return client, sockPath, nil
}

// GetDockerContainers queries the local Docker daemon.
func GetDockerContainers() DockerSummary {
	client, _, err := getDockerClient()
	if err != nil {
		return DockerSummary{Installed: false, Error: err.Error(), Containers: []ContainerInfo{}}
	}

	resp, err := client.Get("http://localhost/v1.41/containers/json?all=1")
	if err != nil {
		return DockerSummary{Installed: true, Error: err.Error(), Containers: []ContainerInfo{}}
	}
	defer resp.Body.Close()

	var rawList []rawDockerContainer
	if err := json.NewDecoder(resp.Body).Decode(&rawList); err != nil {
		return DockerSummary{Installed: true, Error: err.Error(), Containers: []ContainerInfo{}}
	}

	out := make([]ContainerInfo, 0, len(rawList))
	for i, r := range rawList {
		// Item 29: enrich with allowlisted env keys so tunnel discovery can
		// recover the REAL credential of container-deployed tunnels (e.g.
		// Narnia's PASSWORD). Best effort, bounded to the first N.
		var env map[string]string
		if i < maxEnvInspect {
			env = inspectEnv(client, r.ID)
		}

		name := r.ID
		if len(r.Names) > 0 {
			name = strings.TrimPrefix(r.Names[0], "/")
		}

		ports := make([]ContainerPort, 0, len(r.Ports))
		for _, p := range r.Ports {
			ports = append(ports, ContainerPort{
				IP:          p.IP,
				PrivatePort: p.PrivatePort,
				PublicPort:  p.PublicPort,
				Type:        p.Type,
			})
		}

		shortID := r.ID
		if len(shortID) > 12 {
			shortID = shortID[:12]
		}

		out = append(out, ContainerInfo{
			ID:      shortID,
			Name:    name,
			Image:   r.Image,
			State:   strings.ToLower(r.State),
			Status:  r.Status,
			Created: r.Created,
			Ports:   ports,
			Env:     env,
		})
	}

	return DockerSummary{
		Installed:  true,
		Containers: out,
	}
}

// RestartDockerContainer restarts a container by ID or Name.
func RestartDockerContainer(idOrName string) error {
	idOrName = strings.TrimSpace(idOrName)
	if idOrName == "" {
		return fmt.Errorf("container ID or name is required")
	}

	client, _, err := getDockerClient()
	if err != nil {
		return err
	}

	url := fmt.Sprintf("http://localhost/v1.41/containers/%s/restart?t=10", idOrName)
	req, err := http.NewRequest(http.MethodPost, url, nil)
	if err != nil {
		return err
	}

	resp, err := client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return fmt.Errorf("docker API returned HTTP %d", resp.StatusCode)
	}
	return nil
}

// StopDockerContainer stops a container by ID or Name.
func StopDockerContainer(idOrName string) error {
	idOrName = strings.TrimSpace(idOrName)
	if idOrName == "" {
		return fmt.Errorf("container ID or name is required")
	}

	client, _, err := getDockerClient()
	if err != nil {
		return err
	}

	url := fmt.Sprintf("http://localhost/v1.41/containers/%s/stop?t=10", idOrName)
	req, err := http.NewRequest(http.MethodPost, url, nil)
	if err != nil {
		return err
	}

	resp, err := client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return fmt.Errorf("docker API returned HTTP %d", resp.StatusCode)
	}
	return nil
}
