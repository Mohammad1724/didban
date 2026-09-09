package main

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"sync"
	"time"
)

// TunnelManager manages tunnels on this Linux node (like Smite panel / Marzban).
// It can apply configs, run auto-installers, manage systemd services, and report live statuses.
type TunnelManager struct {
	dataDir    string
	tunnelsDir string
	mu         sync.RWMutex
}

type TunnelApplyReq struct {
	ID            string `json:"id"`
	Name          string `json:"name"`
	Core          string `json:"core"`
	Role          string `json:"role"` // "iran" or "foreign"
	ConfigContent string `json:"config_content"`
	ConfigPath    string `json:"config_path"`
	ServiceName   string `json:"service_name"`
	ExecScript    string `json:"exec_script"`
	MultiPorts    string `json:"multi_ports"`
}

type TunnelActionReq struct {
	ID          string `json:"id"`
	ServiceName string `json:"service_name"`
	Action      string `json:"action"` // "start", "stop", "restart", "delete"
}

type TunnelMeta struct {
	ID            string    `json:"id"`
	Name          string    `json:"name"`
	Core          string    `json:"core"`
	Role          string    `json:"role"`
	ConfigPath    string    `json:"config_path"`
	ServiceName   string    `json:"service_name"`
	MultiPorts    string    `json:"multi_ports"`
	CreatedAt     time.Time `json:"created_at"`
	LastUpdatedAt time.Time `json:"last_updated_at"`
}

type TunnelStatusResp struct {
	ID          string `json:"id"`
	Name        string `json:"name"`
	Core        string `json:"core"`
	Role        string `json:"role"`
	ServiceName string `json:"service_name"`
	Active      bool   `json:"active"`
	Status      string `json:"status"` // "active", "inactive", "failed", "unknown"
	PID         int    `json:"pid"`
	Uptime      string `json:"uptime"`
	Logs        string `json:"logs"`
	Success     bool   `json:"success"`
	Message     string `json:"message"`
	Error       string `json:"error,omitempty"`
}

func NewTunnelManager(dataDir string) *TunnelManager {
	tDir := filepath.Join(dataDir, "tunnels")
	_ = os.MkdirAll(tDir, 0o755)
	_ = os.MkdirAll("/etc/didban/tunnels", 0o755)
	return &TunnelManager{
		dataDir:    dataDir,
		tunnelsDir: tDir,
	}
}

func (tm *TunnelManager) getServiceName(id string, custom string) string {
	if custom != "" {
		if !strings.HasPrefix(custom, "didban-tunnel-") && !strings.HasSuffix(custom, ".service") {
			return "didban-tunnel-" + custom
		}
		return strings.TrimSuffix(custom, ".service")
	}
	return "didban-tunnel-" + id
}

// ApplyTunnel writes configuration files, executes the auto-install/update script,
// and starts the systemd service.
func (tm *TunnelManager) ApplyTunnel(req TunnelApplyReq) (*TunnelStatusResp, error) {
	tm.mu.Lock()
	defer tm.mu.Unlock()

	if req.ID == "" {
		return nil, fmt.Errorf("tunnel id is required")
	}

	serviceName := tm.getServiceName(req.ID, req.ServiceName)

	// 1. Write Config File if content and path are given
	if req.ConfigContent != "" && req.ConfigPath != "" {
		cfgDir := filepath.Dir(req.ConfigPath)
		_ = os.MkdirAll(cfgDir, 0o755)
		if err := os.WriteFile(req.ConfigPath, []byte(req.ConfigContent), 0o644); err != nil {
			return nil, fmt.Errorf("failed to write config file %s: %w", req.ConfigPath, err)
		}
	}

	// 2. Execute script if provided
	if req.ExecScript != "" {
		ctx, cancel := context.WithTimeout(context.Background(), 75*time.Second)
		defer cancel()
		cmd := exec.CommandContext(ctx, "bash", "-c", req.ExecScript)
		out, err := cmd.CombinedOutput()
		if err != nil {
			return &TunnelStatusResp{
				ID:          req.ID,
				Name:        req.Name,
				Core:        req.Core,
				Role:        req.Role,
				ServiceName: serviceName,
				Active:      false,
				Status:      "failed",
				Success:     false,
				Logs:        string(out),
				Error:       fmt.Sprintf("exec script failed: %v\nOutput: %s", err, string(out)),
			}, nil
		}
	}

	// 3. Save metadata
	meta := TunnelMeta{
		ID:            req.ID,
		Name:          req.Name,
		Core:          req.Core,
		Role:          req.Role,
		ConfigPath:    req.ConfigPath,
		ServiceName:   serviceName,
		MultiPorts:    req.MultiPorts,
		CreatedAt:     time.Now(),
		LastUpdatedAt: time.Now(),
	}
	metaPath := filepath.Join(tm.tunnelsDir, fmt.Sprintf("meta-%s.json", req.ID))
	if b, err := json.MarshalIndent(meta, "", "  "); err == nil {
		_ = os.WriteFile(metaPath, b, 0o644)
	}

	// Wait 1 second for service to stabilize
	time.Sleep(1 * time.Second)

	// 4. Query service status
	return tm.queryServiceStatus(req.ID, meta.Name, meta.Core, meta.Role, serviceName)
}

// StartTunnel starts or restarts the systemd tunnel service.
func (tm *TunnelManager) StartTunnel(id, customService string) (*TunnelStatusResp, error) {
	tm.mu.Lock()
	defer tm.mu.Unlock()

	serviceName := tm.getServiceName(id, customService)
	cmd := exec.Command("systemctl", "restart", serviceName)
	_ = cmd.Run()
	time.Sleep(500 * time.Millisecond)

	meta := tm.loadMeta(id)
	return tm.queryServiceStatus(id, meta.Name, meta.Core, meta.Role, serviceName)
}

// StopTunnel stops the systemd tunnel service.
func (tm *TunnelManager) StopTunnel(id, customService string) (*TunnelStatusResp, error) {
	tm.mu.Lock()
	defer tm.mu.Unlock()

	serviceName := tm.getServiceName(id, customService)
	cmd := exec.Command("systemctl", "stop", serviceName)
	_ = cmd.Run()
	time.Sleep(500 * time.Millisecond)

	meta := tm.loadMeta(id)
	return tm.queryServiceStatus(id, meta.Name, meta.Core, meta.Role, serviceName)
}

// DeleteTunnel disables and removes the systemd unit and configuration files.
func (tm *TunnelManager) DeleteTunnel(id, customService string) error {
	tm.mu.Lock()
	defer tm.mu.Unlock()

	serviceName := tm.getServiceName(id, customService)
	_ = exec.Command("systemctl", "stop", serviceName).Run()
	_ = exec.Command("systemctl", "disable", serviceName).Run()

	unitPath := fmt.Sprintf("/etc/systemd/system/%s.service", serviceName)
	_ = os.Remove(unitPath)
	_ = exec.Command("systemctl", "daemon-reload").Run()

	metaPath := filepath.Join(tm.tunnelsDir, fmt.Sprintf("meta-%s.json", id))
	_ = os.Remove(metaPath)

	// Clean up config files if known
	_ = os.Remove(fmt.Sprintf("/etc/didban/tunnels/%s.toml", id))
	_ = os.Remove(fmt.Sprintf("/etc/didban/tunnels/%s.json", id))
	_ = os.Remove(fmt.Sprintf("/etc/didban/tunnels/%s.yaml", id))
	_ = os.Remove(fmt.Sprintf("/etc/didban/tunnels/%s.conf", id))

	return nil
}

// GetTunnelStatus returns the live status and logs of a tunnel.
func (tm *TunnelManager) GetTunnelStatus(id string) (*TunnelStatusResp, error) {
	tm.mu.RLock()
	defer tm.mu.RUnlock()

	meta := tm.loadMeta(id)
	serviceName := tm.getServiceName(id, meta.ServiceName)
	return tm.queryServiceStatus(id, meta.Name, meta.Core, meta.Role, serviceName)
}

// ListTunnels returns statuses for all registered tunnels on this node.
func (tm *TunnelManager) ListTunnels() []*TunnelStatusResp {
	tm.mu.RLock()
	defer tm.mu.RUnlock()

	files, err := filepath.Glob(filepath.Join(tm.tunnelsDir, "meta-*.json"))
	if err != nil {
		return nil
	}

	res := make([]*TunnelStatusResp, 0, len(files))
	for _, f := range files {
		data, err := os.ReadFile(f)
		if err != nil {
			continue
		}
		var meta TunnelMeta
		if err := json.Unmarshal(data, &meta); err != nil {
			continue
		}
		serviceName := tm.getServiceName(meta.ID, meta.ServiceName)
		st, _ := tm.queryServiceStatus(meta.ID, meta.Name, meta.Core, meta.Role, serviceName)
		if st != nil {
			res = append(res, st)
		}
	}
	return res
}

func (tm *TunnelManager) loadMeta(id string) TunnelMeta {
	metaPath := filepath.Join(tm.tunnelsDir, fmt.Sprintf("meta-%s.json", id))
	data, err := os.ReadFile(metaPath)
	if err != nil {
		return TunnelMeta{ID: id}
	}
	var meta TunnelMeta
	_ = json.Unmarshal(data, &meta)
	return meta
}

func (tm *TunnelManager) queryServiceStatus(id, name, core, role, serviceName string) (*TunnelStatusResp, error) {
	// 1. Check is-active
	cmdActive := exec.Command("systemctl", "is-active", serviceName)
	outActive, _ := cmdActive.Output()
	statusStr := strings.TrimSpace(string(outActive))

	isActive := statusStr == "active"

	// 2. Query systemctl show for PID and uptime
	var pid int
	var uptimeStr string
	cmdShow := exec.Command("systemctl", "show", serviceName, "--property=MainPID,ActiveEnterTimestamp")
	if outShow, err := cmdShow.Output(); err == nil {
		lines := strings.Split(string(outShow), "\n")
		for _, l := range lines {
			if strings.HasPrefix(l, "MainPID=") {
				_, _ = fmt.Sscanf(l, "MainPID=%d", &pid)
			} else if strings.HasPrefix(l, "ActiveEnterTimestamp=") {
				uptimeStr = strings.TrimPrefix(l, "ActiveEnterTimestamp=")
			}
		}
	}

	// 3. Fetch recent journal logs
	var logs string
	cmdLogs := exec.Command("journalctl", "-u", serviceName, "-n", "20", "--no-pager")
	if outLogs, err := cmdLogs.Output(); err == nil {
		logs = strings.TrimSpace(string(outLogs))
	}

	msg := "Tunnel service is running"
	if !isActive {
		if statusStr == "inactive" {
			msg = "Tunnel service is stopped"
		} else {
			msg = fmt.Sprintf("Tunnel service status: %s", statusStr)
		}
	}

	return &TunnelStatusResp{
		ID:          id,
		Name:        name,
		Core:        core,
		Role:        role,
		ServiceName: serviceName,
		Active:      isActive,
		Status:      statusStr,
		PID:         pid,
		Uptime:      uptimeStr,
		Logs:        logs,
		Success:     isActive,
		Message:     msg,
	}, nil
}
