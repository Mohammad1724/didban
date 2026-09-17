package main

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
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
	serviceNamePattern      = regexp.MustCompile(`^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$`)
	secretAssignmentPattern = regexp.MustCompile(`(?i)(token|password|secret|key|auth)([[:space:]]*[:=][[:space:]]*["']?)([^[:space:]"',]+)`)
	commandAuthPattern      = regexp.MustCompile(`(?i)(--auth[[:space:]]+)([^[:space:]]+)`)
	urlCredentialPattern    = regexp.MustCompile(`(://[^:/[:space:]@]+:)([^@/[:space:]]+)(@)`)
)

const (
	maxConfigContentLen = 1024 * 1024 // 1 MB per config file
	maxDeployScriptLen  = 64 * 1024   // 64 KB per install script
	maxFieldNameLen     = 128         // name/core/role metadata cap
	maxReturnedLogLen   = 16 * 1024   // bounded, redacted diagnostic tail

	// systemCommandTimeout bounds external systemctl/journalctl calls so a hung
	// D-Bus can never wedge an API handler.
	systemCommandTimeout = 10 * time.Second
)

// DeployMode controls how much the agent is allowed to do when applying a tunnel.
type DeployMode string

const (
	// DeployModeScripts is an explicit operator opt-in that allows writing
	// configs AND running the install script provided by the authenticated app.
	DeployModeScripts DeployMode = "scripts"
	// DeployModeConfigOnly is the secure default: the agent writes config files
	// inside its sandbox and manages validated systemd unit names, but never
	// executes request-supplied shell commands.
	DeployModeConfigOnly DeployMode = "config-only"
	defaultDeployMode               = DeployModeConfigOnly
)

func (m DeployMode) valid() bool {
	return m == DeployModeScripts || m == DeployModeConfigOnly
}

func sanitizeTunnelLog(raw string) string {
	safe := secretAssignmentPattern.ReplaceAllString(raw, `${1}${2}[REDACTED]`)
	safe = commandAuthPattern.ReplaceAllString(safe, `${1}[REDACTED]`)
	safe = urlCredentialPattern.ReplaceAllString(safe, `${1}[REDACTED]${3}`)
	if len(safe) > maxReturnedLogLen {
		safe = safe[len(safe)-maxReturnedLogLen:]
		safe = "[truncated] " + safe
	}
	return strings.TrimSpace(safe)
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
	// Port is the local TCP port this node's service is expected to listen
	// on (0 = this role does not listen, e.g. the dialing client side). The
	// watchdog uses it for the "service alive but port dead" (degraded)
	// check. Old agents ignore the field; new agents with old apps (port 0)
	// fall back to the service-active check only.
	Port int `json:"port"`
}

type TunnelActionReq struct {
	ID          string `json:"id"`
	ServiceName string `json:"service_name"`
	Action      string `json:"action"` // "start", "stop", "restart", "delete"
}

type TunnelMeta struct {
	ID          string `json:"id"`
	Name        string `json:"name"`
	Core        string `json:"core"`
	Role        string `json:"role"`
	ConfigPath  string `json:"config_path"`
	ServiceName string `json:"service_name"`
	MultiPorts  string `json:"multi_ports"`
	// Port: local TCP port this role listens on (0 = does not listen).
	// Persisted so the watchdog survives agent restarts.
	Port          int       `json:"port"`
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
	if req.Port < 0 || req.Port > 65535 {
		return nil, fmt.Errorf("invalid port %d: must be 0-65535", req.Port)
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
		// The config path may include per-tunnel subdirectories
		// (<configRoot>/<id>/file — the Item 26 layout), so create the
		// parent chain, not just the sandbox root.
		if err := secureMkdirAllWithin(tm.configRoot, filepath.Dir(cleanPath), 0o755); err != nil {
			return nil, fmt.Errorf("failed to prepare config directory %s: %w", filepath.Dir(cleanPath), err)
		}
		// Tunnel configs contain tokens/keys. Atomic replacement prevents
		// partial credentials and does not follow a destination symlink.
		if err := secureWriteFileAtomic(cleanPath, []byte(req.ConfigContent), 0o600); err != nil {
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
			base.Logs = sanitizeTunnelLog(string(out))
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
		Port:          req.Port,
		CreatedAt:     time.Now(),
		LastUpdatedAt: time.Now(),
	}
	metaPath := filepath.Join(tm.tunnelsDir, fmt.Sprintf("meta-%s.json", req.ID))
	encoded, err := json.MarshalIndent(meta, "", "  ")
	if err != nil {
		return nil, fmt.Errorf("failed to encode tunnel metadata: %w", err)
	}
	if err := secureWriteFileAtomic(metaPath, encoded, 0o600); err != nil {
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

// DeleteTunnel disables and removes the systemd unit, the tunnel's config
// files and the core's remaining deploy residue on this host (Item 27:
// helper scripts, logs, sysctl drop-ins, NAT chains, the shared binary and
// legacy fixed config paths — each under the refcounting rules described on
// cleanupCoreResidue). It is idempotent: deleting an unknown tunnel still
// succeeds.
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
			// Item 26 per-tunnel layout <configRoot>/<id>/ — remove the
			// tunnel directory too when it sits directly under the sandbox
			// root and is a valid tunnel id. os.Remove on a directory
			// succeeds only when it is EMPTY, so it can never wipe files
			// belonging to anything else.
			dir := filepath.Dir(clean)
			if filepath.Dir(dir) == tm.configRoot && ValidateTunnelID(filepath.Base(dir)) == nil {
				_ = os.Remove(dir)
			}
		}
	}
	// Legacy layout cleanup (pre-sandbox installs).
	for _, ext := range []string{".toml", ".json", ".yaml", ".conf"} {
		_ = os.Remove(filepath.Join(tm.configRoot, id+ext))
	}

	// Item 27: core residue (fw scripts, logs, drop-ins, chains, shared
	// binary, legacy fixed config paths). Best effort — see
	// cleanupCoreResidue for the exact rules.
	tm.cleanupCoreResidue(meta)

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
	if outShow, ok := systemctlShow(ctx, serviceName, "MainPID,ActiveEnterTimestamp"); ok {
		for _, l := range strings.Split(outShow, "\n") {
			switch {
			case strings.HasPrefix(l, "MainPID="):
				_, _ = fmt.Sscanf(l, "MainPID=%d", &pid)
			case strings.HasPrefix(l, "ActiveEnterTimestamp="):
				// This field used to carry the raw systemctl timestamp
				// ("Wed 2019-12-11 21:44:50 UTC"), which is a wall-clock
				// instant rather than an uptime. Report a duration instead.
				if t, found := parseSystemdTimestamp(strings.TrimPrefix(l, "ActiveEnterTimestamp=")); found {
					uptimeStr = formatUptime(uint64(uptimeSeconds(t)))
				}
			}
		}
	}

	// 3. Fetch recent journal logs.
	var logs string
	cmdLogs := exec.CommandContext(ctx, "journalctl", "-u", serviceName, "-n", "20", "--no-pager")
	if outLogs, err := cmdLogs.Output(); err == nil {
		logs = sanitizeTunnelLog(string(outLogs))
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

// ── systemctl timestamps ─────────────────────────────────────────────────────
//
// `systemctl show --property=ActiveEnterTimestamp` prints a *human* timestamp
// ("Wed 2019-12-11 21:44:50 UTC"), not RFC3339, and the day/month names follow
// the manager's locale. Parsing it as RFC3339 therefore always failed, which
// left UptimeSec at 0 and made the watchdog classify every unit with three or
// more restarts as a permanent crash_loop.
//
// The fix has two layers: ask systemctl for an unambiguous format
// (--timestamp=unix, systemd >= 247) with LC_ALL=C pinned, and keep a
// multi-layout fallback for older releases that reject the flag.

// systemdTimeLayouts are the fallback formats for the human-readable form,
// with LC_ALL=C pinned so the abbreviations are always English.
var systemdTimeLayouts = []string{
	"Mon 2006-01-02 15:04:05 MST",
	"Mon 2006-01-02 15:04:05 -0700",
	"Mon 2006-01-02 15:04:05 -0700 MST",
	"2006-01-02 15:04:05 MST",
	time.RFC3339,
}

// systemctlUnixTimestampOK reports once whether this host's systemctl accepts
// --timestamp=unix. Older systemd rejects the flag with a non-zero exit, and
// the watchdog calls this path every tick, so the probe must not repeat.
var systemctlUnixTimestampOK = sync.OnceValue(func() bool {
	ctx, cancel := systemCmdContext()
	defer cancel()
	cmd := exec.CommandContext(ctx, "systemctl", "--timestamp=unix", "show",
		"--property=SystemState")
	cmd.Env = append(os.Environ(), "LC_ALL=C")
	return cmd.Run() == nil
})

// systemctlShow runs `systemctl show <unit> --property=<props>` and returns its
// stdout. ok is false when systemctl itself failed (missing unit, no systemd).
func systemctlShow(ctx context.Context, unit, props string) (out string, ok bool) {
	args := []string{"show", unit, "--property=" + props}
	if systemctlUnixTimestampOK() {
		args = append([]string{"--timestamp=unix"}, args...)
	}
	cmd := exec.CommandContext(ctx, "systemctl", args...)
	cmd.Env = append(os.Environ(), "LC_ALL=C")
	b, err := cmd.Output()
	if err != nil {
		return "", false
	}
	return string(b), true
}

// parseSystemdTimestamp converts a systemctl timestamp property into a
// time.Time. It accepts unix seconds (from --timestamp=unix), the C-locale
// human format and RFC3339. Empty, "n/a" and non-positive values mean the unit
// has not been active, which callers must treat as "unknown", never as epoch.
func parseSystemdTimestamp(value string) (time.Time, bool) {
	v := strings.TrimSpace(value)
	if v == "" || v == "n/a" || v == "-" || v == "none" {
		return time.Time{}, false
	}
	if n, err := strconv.ParseInt(v, 10, 64); err == nil {
		if n <= 0 {
			return time.Time{}, false
		}
		return time.Unix(n, 0), true
	}
	for _, layout := range systemdTimeLayouts {
		if t, err := time.ParseInLocation(layout, v, time.Local); err == nil {
			return t, true
		}
	}
	return time.Time{}, false
}

// uptimeSeconds returns whole seconds elapsed since t, clamped to >= 0 so a
// clock skew or a future timestamp can never produce a negative uptime.
func uptimeSeconds(t time.Time) int {
	if sec := int(time.Since(t).Seconds()); sec > 0 {
		return sec
	}
	return 0
}

// ── Item 27: full delete-time cleanup ────────────────────────────────────────
//
// What a deploy leaves on a host beyond the unit + per-tunnel config, and how
// delete removes it:
//
//	Narnia     fw script (per-id), log (per-id, iran), docker container
//	           (named after the unit, dies with --rm), shared sysctl drop-in
//	IPTables   rules script (per-id hash), NAT chains (unit ExecStop),
//	           shared sysctl drop-in
//	all cores  shared binary (/usr/local/bin/<core>)
//	legacy     pre-Item-26 fixed config paths (/etc/backpack/..., /etc/frp/...)
//
// Shared artifacts are removed ONLY when no other agent-deployed tunnel of
// the same core remains on this node (meta-file reference count). Manual
// (non-agent) installs are invisible to that count — by design: the agent
// only removes what it can account for.

// Layout roots (overridable in tests).
var (
	binRoot    = "/usr/local/bin"
	didbanEtc  = "/etc/didban"
	sysctlRoot = "/etc/sysctl.d"
	legacyEtc  = "/etc"
	logRoot    = "/var/log"
)

// coreBinaryPath returns the shared binary a core's install script places,
// per role ("" for cores without one).
func coreBinaryPath(core, role string) string {
	switch core {
	case "BACKPACK":
		return filepath.Join(binRoot, "backpack")
	case "PAQET":
		return filepath.Join(binRoot, "paqet")
	case "SPOOF_TUNNEL":
		return filepath.Join(binRoot, "spoof-tunnel")
	case "BACKHAUL":
		return filepath.Join(binRoot, "backhaul")
	case "RATHOLE":
		return filepath.Join(binRoot, "rathole")
	case "GOST":
		return filepath.Join(binRoot, "gost")
	case "CHISEL":
		return filepath.Join(binRoot, "chisel")
	case "FRP":
		if role == "iran" {
			return filepath.Join(binRoot, "frpc")
		}
		return filepath.Join(binRoot, "frps")
	default:
		return ""
	}
}

// legacyConfigPaths lists the config file (+ dir) the pre-Item-26
// generators wrote to fixed per-core paths, so a delete after a migration
// deploy also removes the old layout.
func legacyConfigPaths(core, role string) (file, dir string) {
	switch core {
	case "BACKPACK":
		if role == "iran" {
			return filepath.Join(legacyEtc, "backpack/server.toml"), filepath.Join(legacyEtc, "backpack")
		}
		return filepath.Join(legacyEtc, "backpack/client.toml"), filepath.Join(legacyEtc, "backpack")
	case "PAQET":
		if role == "iran" {
			return filepath.Join(legacyEtc, "paqet/client.yaml"), filepath.Join(legacyEtc, "paqet")
		}
		return filepath.Join(legacyEtc, "paqet/server.yaml"), filepath.Join(legacyEtc, "paqet")
	case "SPOOF_TUNNEL":
		if role == "iran" {
			return filepath.Join(legacyEtc, "spoof-tunnel/client.json"), filepath.Join(legacyEtc, "spoof-tunnel")
		}
		return filepath.Join(legacyEtc, "spoof-tunnel/server.json"), filepath.Join(legacyEtc, "spoof-tunnel")
	case "BACKHAUL":
		return filepath.Join(legacyEtc, "backhaul/config.toml"), filepath.Join(legacyEtc, "backhaul")
	case "RATHOLE":
		if role == "iran" {
			return filepath.Join(legacyEtc, "rathole/client.toml"), filepath.Join(legacyEtc, "rathole")
		}
		return filepath.Join(legacyEtc, "rathole/server.toml"), filepath.Join(legacyEtc, "rathole")
	case "FRP":
		if role == "iran" {
			return filepath.Join(legacyEtc, "frp/frpc.toml"), filepath.Join(legacyEtc, "frp")
		}
		return filepath.Join(legacyEtc, "frp/frps.toml"), filepath.Join(legacyEtc, "frp")
	default:
		return "", ""
	}
}

// iptablesChainHash reproduces the app's deterministic chain salt: the first
// 8 bytes of SHA-256("<id>") as lowercase hex (16 chars; keeps the chain
// names inside iptables' 28-char limit).
func iptablesChainHash(id string) string {
	h := sha256.Sum256([]byte(id))
	return hex.EncodeToString(h[:8])
}

// otherMetaCount counts agent-deployed tunnels of [core] still registered on
// THIS node, excluding [id]. With roleOnly it additionally requires the same
// role (FRP: frpc and frps are distinct binaries per role).
func (tm *TunnelManager) otherMetaCount(id, core string, roleOnly bool, role string) int {
	n := 0
	files, _ := filepath.Glob(filepath.Join(tm.tunnelsDir, "meta-*.json"))
	for _, f := range files {
		data, err := os.ReadFile(f)
		if err != nil {
			continue
		}
		var m TunnelMeta
		if json.Unmarshal(data, &m) != nil {
			continue
		}
		if m.ID == id || m.Core != core {
			continue
		}
		if roleOnly && m.Role != role {
			continue
		}
		n++
	}
	return n
}

// cleanupCoreResidue removes the deploy residue for [meta] on this host
// (see the Item 27 layout table above). Best effort: a missing file or a
// missing docker/iptables binary is not an error — the unit+config+meta
// deletion has already succeeded.
func (tm *TunnelManager) cleanupCoreResidue(meta TunnelMeta) {
	ctx, cancel := systemCmdContext()
	defer cancel()
	rm := func(paths ...string) {
		for _, p := range paths {
			_ = os.Remove(p)
		}
	}

	switch meta.Core {
	case "NARNIA":
		suffix := "iran"
		if meta.Role == "foreign" {
			suffix = "foreign"
		}
		rm(filepath.Join(didbanEtc, "narnia-"+meta.ID+"-"+suffix+"-fw.sh"))
		if meta.Role == "iran" {
			rm(filepath.Join(logRoot, "didban-narnia-"+meta.ID+".log"))
		}
		// The container is named after the unit and dies with `docker stop`
		// (docker run --rm); force-remove a stuck one.
		_ = exec.CommandContext(ctx, "docker", "rm", "-f", "didban-tunnel-"+meta.ID).Run()
		if tm.otherMetaCount(meta.ID, "NARNIA", false, "") == 0 {
			rm(filepath.Join(sysctlRoot, "99-didban-narnia.conf"))
		}
	case "IPTABLES":
		hash := iptablesChainHash(meta.ID)
		rm(filepath.Join(didbanEtc, "iptables-"+hash+"-rules.sh"))
		// The unit's ExecStop normally removed the chains; heal the case
		// where the unit was already gone when the delete started.
		for _, pair := range [][2]string{{"PREROUTING", "didban-tun-" + hash}, {"POSTROUTING", "didban-tunp-" + hash}} {
			_ = exec.CommandContext(ctx, "iptables", "-t", "nat", "-F", pair[1]).Run()
			_ = exec.CommandContext(ctx, "iptables", "-t", "nat", "-D", pair[0], "-j", pair[1]).Run()
			_ = exec.CommandContext(ctx, "iptables", "-t", "nat", "-X", pair[1]).Run()
		}
		if tm.otherMetaCount(meta.ID, "IPTABLES", false, "") == 0 {
			rm(filepath.Join(sysctlRoot, "99-didban-iptables.conf"))
		}
	}

	// Shared binary + legacy fixed config paths: remove only when this was
	// the LAST agent-deployed tunnel of the same core (FRP: same role,
	// because frpc/frps are separate binaries).
	roleOnly := meta.Core == "FRP"
	if tm.otherMetaCount(meta.ID, meta.Core, roleOnly, meta.Role) == 0 {
		if bin := coreBinaryPath(meta.Core, meta.Role); bin != "" {
			_ = os.Remove(bin)
		}
		if file, dir := legacyConfigPaths(meta.Core, meta.Role); file != "" {
			rm(file, dir) // dir removal succeeds only when empty
		}
	}
}

// ── Watchdog support (Phase 4 · 4-A) ────────────────────────────────────────

// WatchInputs is the lightweight, allocation-cheap service health read the
// tunnel watchdog performs every interval: is-active + MainPID +
// ActiveEnterTimestamp + NRestarts (no journal scrape — that stays for the
// on-demand status endpoint).
type WatchInputs struct {
	Active    bool   // systemctl is-active == "active"
	Status    string // raw is-active output: active|inactive|failed|activating|...
	PID       int
	UptimeSec int // seconds since ActiveEnterTimestamp (0 if unknown)
	NRestarts int // systemd restart counter for the unit lifetime
}

// WatchInputs reads the service state for one tunnel unit. A missing unit or
// a failed systemctl call reports Active=false (the watchdog treats it as
// down, exactly like the on-demand status endpoint does for "unknown").
func (tm *TunnelManager) WatchInputs(serviceName string) WatchInputs {
	wi := WatchInputs{}
	ctx, cancel := systemCmdContext()
	defer cancel()

	outActive, _ := exec.CommandContext(ctx, "systemctl", "is-active", serviceName).Output()
	statusStr := strings.TrimSpace(string(outActive))
	wi.Status = statusStr
	wi.Active = statusStr == "active"

	if outShow, ok := systemctlShow(ctx, serviceName, "MainPID,ActiveEnterTimestamp,NRestarts"); ok {
		for _, l := range strings.Split(outShow, "\n") {
			switch {
			case strings.HasPrefix(l, "MainPID="):
				_, _ = fmt.Sscanf(l, "MainPID=%d", &wi.PID)
			case strings.HasPrefix(l, "NRestarts="):
				_, _ = fmt.Sscanf(l, "NRestarts=%d", &wi.NRestarts)
			case strings.HasPrefix(l, "ActiveEnterTimestamp="):
				// Unknown (never-active unit, unparseable value) must leave
				// UptimeSec at 0 — classifyWatch treats 0 as "just restarted",
				// so a bogus epoch here would fake a crash_loop.
				if t, found := parseSystemdTimestamp(strings.TrimPrefix(l, "ActiveEnterTimestamp=")); found {
					wi.UptimeSec = uptimeSeconds(t)
				}
			}
		}
	}
	return wi
}

// ListMeta returns the tunnels registered on this node (meta files). The
// watchdog iterates it each tick; ordering is stable by id.
func (tm *TunnelManager) ListMeta() []TunnelMeta {
	entries, err := os.ReadDir(tm.tunnelsDir)
	if err != nil {
		return nil
	}
	var metas []TunnelMeta
	for _, e := range entries {
		n := e.Name()
		if !strings.HasPrefix(n, "meta-") || !strings.HasSuffix(n, ".json") {
			continue
		}
		id := strings.TrimSuffix(strings.TrimPrefix(n, "meta-"), ".json")
		meta, ok := tm.loadMetaChecked(id)
		if ok {
			metas = append(metas, meta)
		}
	}
	sort.Slice(metas, func(i, j int) bool { return metas[i].ID < metas[j].ID })
	return metas
}
