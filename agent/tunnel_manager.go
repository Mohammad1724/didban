package main

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"strings"
	"sync"
	"time"
)

// Sentinel errors for input validation. The API layer maps these to 4xx.
var (
	errInvalidTunnelID    = errors.New("invalid tunnel id")
	errInvalidServiceName = errors.New("invalid service name")
	errConfigPathOutside  = errors.New("config_path is outside the allowed tunnel configuration directory")
	errTunnelNotFound     = errors.New("tunnel not found")
)

var (
	// Tunnel ids are used in file names (meta-<id>.json) and must stay path-safe.
	tunnelIDPattern = regexp.MustCompile(`^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$`)
	// Systemd unit names we manage must be conservative: no dots, no slashes.
	serviceNamePattern = regexp.MustCompile(`^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$`)
)

const (
	maxConfigContentLen = 1024 * 1024 // 1 MB per config file
	maxDeployScriptLen  = 64 * 1024   // 64 KB per install script
	maxFieldNameLen     = 128         // name/core/role metadata cap

	// systemCommandTimeout bounds external systemctl/journalctl calls so a hung
	// D-Bus can never wedge an API handler.
	systemCommandTimeout = 10 * time.Second
)

// DeployMode controls how much the agent is allowed to do when applying a tunnel.
type DeployMode string

const (
	// DeployModeScripts (default) allows writing configs AND running the install
	// script provided by the (authenticated) app.
	DeployModeScripts DeployMode = "scripts"
	// DeployModeConfigOnly is the hardened mode: the agent only writes config
	// files inside its sandbox and manages the systemd unit name; it will never
	// execute arbitrary shell commands.
	DeployModeConfigOnly DeployMode = "config-only"
)

func (m DeployMode) valid() bool {
	return m == DeployModeScripts || m == DeployModeConfigOnly
}

// ValidateTunnelID ensures the id is safe to embed in file names and unit names.
func ValidateTunnelID(id string) error {
	if !tunnelIDPattern.MatchString(id) {
		return fmt.Errorf("%w: id must be 1-64 characters of [A-Za-z0-9_-] and start with a letter or digit", errInvalidTunnelID)
	}
	return nil
}

// TunnelManager manages tunnels on this Linux node (like Smite panel / Marzban).
// It can apply configs, run auto-install/update scripts, manage systemd services,
// and report live statuses.
type TunnelManager struct {
	dataDir     string
	tunnelsDir  string
	configRoot  string // sandbox directory for tunnel configuration files
	deployMode  DeployMode
	settleDelay time.Duration
	mu          sync.RWMutex
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

func NewTunnelManager(dataDir, configRoot string, deployMode DeployMode) *TunnelManager {
	tDir := filepath.Join(dataDir, "tunnels")
	_ = os.MkdirAll(tDir, 0o755)
	tm := &TunnelManager{
		dataDir:     dataDir,
		tunnelsDir:  tDir,
		configRoot:  filepath.Clean(configRoot),
		deployMode:  deployMode,
		settleDelay: time.Second,
	}
	// Best effort: the sandbox dir may not exist yet. Under a hardened systemd
	// unit this can fail (see install docs) — the error is surfaced per-request
	// instead of being silently swallowed at startup.
	_ = os.MkdirAll(tm.configRoot, 0o755)
	return tm
}

// resolveServiceName maps the requested service name to a validated unit name.
func (tm *TunnelManager) resolveServiceName(id, custom string) (string, error) {
	if custom == "" {
		return "didban-tunnel-" + id, nil
	}
	if !serviceNamePattern.MatchString(custom) {
		return "", fmt.Errorf("%w: %q", errInvalidServiceName, custom)
	}
	return custom, nil
}

// resolveConfigPath validates that a requested config path stays inside the
// tunnel sandbox directory. It defends against absolute paths outside the
// sandbox, parent-directory traversal, and symlink escapes.
func (tm *TunnelManager) resolveConfigPath(p string) (string, error) {
	if p == "" {
		return "", fmt.Errorf("%w: config_path is required when config_content is provided", errConfigPathOutside)
	}
	if !filepath.IsAbs(p) {
		return "", fmt.Errorf("%w: %q is not an absolute path", errConfigPathOutside, p)
	}
	clean := filepath.Clean(p)
	root := tm.configRoot
	if clean != root && !strings.HasPrefix(clean, root+string(os.PathSeparator)) {
		return "", fmt.Errorf("%w: %q is not inside %q", errConfigPathOutside, p, root)
	}
	// Best-effort symlink check on the parent directory chain.
	if resolved, err := filepath.EvalSymlinks(filepath.Dir(clean)); err == nil {
		resolvedClean := filepath.Clean(resolved)
		rootResolved, rerr := filepath.EvalSymlinks(root)
		if rerr != nil {
			rootResolved = root
		}
		if !strings.HasPrefix(resolvedClean, filepath.Clean(rootResolved)+string(os.PathSeparator)) && resolvedClean != filepath.Clean(rootResolved) {
			return "", fmt.Errorf("%w: path resolves outside the sandbox", errConfigPathOutside)
		}
	}
	return clean, nil
}

// loadMetaChecked reads tunnel metadata. The second return value reports
// whether the tunnel was actually registered on this node.
func (tm *TunnelManager) loadMetaChecked(id string) (TunnelMeta, bool) {
	metaPath := filepath.Join(tm.tunnelsDir, "meta-"+id+".json")
	data, err := os.ReadFile(metaPath)
	if err != nil {
		return TunnelMeta{ID: id}, false
	}
	var meta TunnelMeta
	if err := json.Unmarshal(data, &meta); err != nil {
		return TunnelMeta{ID: id}, false
	}
	return meta, true
}

// ApplyTunnel validates the request, writes the (sandboxed) configuration file,
// optionally executes the install script, registers metadata and reports the
// live service status.
func (tm *TunnelManager) ApplyTunnel(req TunnelApplyReq) (*TunnelStatusResp, error) {
	tm.mu.Lock()
	defer tm.mu.Unlock()

	if err := ValidateTunnelID(req.ID); err != nil {
		return nil, err
	}
	serviceName, err := tm.resolveServiceName(req.ID, req.ServiceName)
	if err != nil {
		return nil, err
	}
	if len(req.Name) > maxFieldNameLen || len(req.Core) > 32 || len(req.Role) > 16 {
		return nil, errors.New("invalid metadata: name/core/role too long")
	}

	base := &TunnelStatusResp{
		ID:          req.ID,
		Name:        req.Name,
		Core:        req.Core,
		Role:        req.Role,
		ServiceName: serviceName,
	}

	// 1. Write the config file, strictly inside the sandbox directory.
	if req.ConfigContent != "" {
		if len(req.ConfigContent) > maxConfigContentLen {
			return nil, errors.New("config_content exceeds the 1 MB limit")
		}
		cleanPath, err := tm.resolveConfigPath(req.ConfigPath)
		if err != nil {
			return nil, err
		}
		if err := os.MkdirAll(tm.configRoot, 0o755); err != nil {
			return nil, fmt.Errorf("failed to prepare config directory %s: %w", tm.configRoot, err)
		}
		if err := os.WriteFile(cleanPath, []byte(req.ConfigContent), 0o644); err != nil {
			return nil, fmt.Errorf("failed to write config file %s: %w", cleanPath, err)
		}
	}

	// 2. Execute the install script if provided and allowed by the deploy mode.
	if req.ExecScript != "" {
		if len(req.ExecScript) > maxDeployScriptLen {
			return nil, errors.New("exec_script exceeds the 64 KB limit")
		}
		if tm.deployMode == DeployModeConfigOnly {
			base.Active = false
			base.Status = "failed"
			base.Success = false
			base.Error = "install scripts are disabled on this agent (deploy-mode=config-only)"
			return base, nil
		}
		ctx, cancel := context.WithTimeout(context.Background(), 75*time.Second)
		defer cancel()
		cmd := exec.CommandContext(ctx, "bash", "-c", req.ExecScript)
		out, err := cmd.CombinedOutput()
		if err != nil {
			base.Active = false
			base.Status = "failed"
			base.Success = false
			base.Logs = string(out)
			base.Error = fmt.Sprintf("exec script failed: %v", err)
			return base, nil
		}
	}

	// 3. Save metadata (checked — a broken meta file would desynchronize the node).
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
	encoded, err := json.MarshalIndent(meta, "", "  ")
	if err != nil {
		return nil, fmt.Errorf("failed to encode tunnel metadata: %w", err)
	}
	if err := os.WriteFile(metaPath, encoded, 0o644); err != nil {
		return nil, fmt.Errorf("failed to write tunnel metadata %s: %w", metaPath, err)
	}

	// Let the (possibly newly enabled) service settle before querying.
	if tm.settleDelay > 0 {
		time.Sleep(tm.settleDelay)
	}

	// 4. Query service status.
	return tm.queryServiceStatusLocked(req.ID, meta.Name, meta.Core, meta.Role, serviceName), nil
}

// StartTunnel starts or restarts the systemd tunnel service.
func (tm *TunnelManager) StartTunnel(id, customService string) (*TunnelStatusResp, error) {
	if err := ValidateTunnelID(id); err != nil {
		return nil, err
	}
	tm.mu.Lock()
	defer tm.mu.Unlock()

	serviceName, err := tm.resolveServiceName(id, customService)
	if err != nil {
		return nil, err
	}
	ctx, cancel := systemCmdContext()
	defer cancel()
	cmd := exec.CommandContext(ctx, "systemctl", "restart", serviceName)
	_ = cmd.Run()
	if tm.settleDelay > 0 {
		time.Sleep(tm.settleDelay / 2)
	}

	meta, ok := tm.loadMetaChecked(id)
	if !ok {
		return nil, errTunnelNotFound
	}
	return tm.queryServiceStatusLocked(id, meta.Name, meta.Core, meta.Role, serviceName), nil
}

// StopTunnel stops the systemd tunnel service.
func (tm *TunnelManager) StopTunnel(id, customService string) (*TunnelStatusResp, error) {
	if err := ValidateTunnelID(id); err != nil {
		return nil, err
	}
	tm.mu.Lock()
	defer tm.mu.Unlock()

	serviceName, err := tm.resolveServiceName(id, customService)
	if err != nil {
		return nil, err
	}
	ctx, cancel := systemCmdContext()
	defer cancel()
	cmd := exec.CommandContext(ctx, "systemctl", "stop", serviceName)
	_ = cmd.Run()
	if tm.settleDelay > 0 {
		time.Sleep(tm.settleDelay / 2)
	}

	meta, ok := tm.loadMetaChecked(id)
	if !ok {
		return nil, errTunnelNotFound
	}
	return tm.queryServiceStatusLocked(id, meta.Name, meta.Core, meta.Role, serviceName), nil
}

// DeleteTunnel disables and removes the systemd unit and configuration files.
// It is idempotent: deleting an unknown tunnel still succeeds.
func (tm *TunnelManager) DeleteTunnel(id, customService string) error {
	if err := ValidateTunnelID(id); err != nil {
		return err
	}
	tm.mu.Lock()
	defer tm.mu.Unlock()

	serviceName, err := tm.resolveServiceName(id, customService)
	if err != nil {
		return err
	}
	ctx, cancel := systemCmdContext()
	defer cancel()
	_ = exec.CommandContext(ctx, "systemctl", "stop", serviceName).Run()
	_ = exec.CommandContext(ctx, "systemctl", "disable", serviceName).Run()

	unitPath := filepath.Join("/etc/systemd/system", serviceName+".service")
	_ = os.Remove(unitPath)
	_ = exec.CommandContext(ctx, "systemctl", "daemon-reload").Run()

	meta, found := tm.loadMetaChecked(id)
	if !found {
		return nil
	}
	_ = os.Remove(filepath.Join(tm.tunnelsDir, fmt.Sprintf("meta-%s.json", id)))

	// Clean up config files that live inside the sandbox.
	if meta.ConfigPath != "" {
		if clean, err := tm.resolveConfigPath(meta.ConfigPath); err == nil {
			_ = os.Remove(clean)
		}
	}
	// Legacy layout cleanup (pre-sandbox installs).
	for _, ext := range []string{".toml", ".json", ".yaml", ".conf"} {
		_ = os.Remove(filepath.Join(tm.configRoot, id+ext))
	}

	return nil
}

// GetTunnelStatus returns the live status and logs of a tunnel.
// It returns errTunnelNotFound when the tunnel is not registered on this node
// and its service is not running.
func (tm *TunnelManager) GetTunnelStatus(id string) (*TunnelStatusResp, error) {
	if err := ValidateTunnelID(id); err != nil {
		return nil, err
	}
	tm.mu.RLock()
	defer tm.mu.RUnlock()

	meta, found := tm.loadMetaChecked(id)
	serviceName, err := tm.resolveServiceName(id, meta.ServiceName)
	if err != nil {
		return nil, err
	}
	st := tm.queryServiceStatusLocked(id, meta.Name, meta.Core, meta.Role, serviceName)
	if !found && !st.Active {
		return nil, errTunnelNotFound
	}
	return st, nil
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
		serviceName, err := tm.resolveServiceName(meta.ID, meta.ServiceName)
		if err != nil {
			continue
		}
		if st := tm.queryServiceStatusLocked(meta.ID, meta.Name, meta.Core, meta.Role, serviceName); st != nil {
			res = append(res, st)
		}
	}
	return res
}

func (tm *TunnelManager) queryServiceStatusLocked(id, name, core, role, serviceName string) *TunnelStatusResp {
	// One bounded context covers every systemctl/journalctl call below.
	ctx, cancel := systemCmdContext()
	defer cancel()

	// 1. Check is-active (bounded so a hung D-Bus cannot wedge the handler).
	cmdActive := exec.CommandContext(ctx, "systemctl", "is-active", serviceName)
	outActive, _ := cmdActive.Output()
	statusStr := strings.TrimSpace(string(outActive))

	isActive := statusStr == "active"

	// 2. Query systemctl show for PID and uptime.
	var pid int
	var uptimeStr string
	cmdShow := exec.CommandContext(ctx, "systemctl", "show", serviceName, "--property=MainPID,ActiveEnterTimestamp")
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

	// 3. Fetch recent journal logs.
	var logs string
	cmdLogs := exec.CommandContext(ctx, "journalctl", "-u", serviceName, "-n", "20", "--no-pager")
	if outLogs, err := cmdLogs.Output(); err == nil {
		logs = strings.TrimSpace(string(outLogs))
	}

	msg := "Tunnel service is running"
	if !isActive {
		if statusStr == "inactive" || statusStr == "" {
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
	}
}

// systemCmdContext returns a context bounded by systemCommandTimeout plus its
// cancel function. Callers must cancel (typically via defer) once the command
// has finished so the underlying timer does not linger until the deadline.
func systemCmdContext() (context.Context, context.CancelFunc) {
	return context.WithTimeout(context.Background(), systemCommandTimeout)
}
