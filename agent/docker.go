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
	State   string          `json:"state"`   // running, exited, restarting, paused, dead
	Status  string          `json:"status"`  // "Up 2 hours", "Exited (1) 5 mins ago"
	Created int64           `json:"created"`
	Ports   []ContainerPort `json:"ports"`
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

func getDockerClient() (*http.Client, string, error) {
	sockPath := "/var/run/docker.sock"
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
	for _, r := range rawList {
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
