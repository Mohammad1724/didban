// Didban (دیدبان) — a tiny Linux server monitoring agent.
// It exposes CPU / memory / disk / network metrics, top processes
// and a spike event log over an authenticated HTTP(S) JSON API.
//
// Standard library only — no external dependencies.
package main

import (
	"context"
	"crypto/rand"
	"crypto/tls"
	"encoding/hex"
	"flag"
	"fmt"
	"net"
	"net/http"
	"os"
	"os/signal"
	"path/filepath"
	"strconv"
	"strings"
	"syscall"
	"time"
)

var version = "dev"

// Config holds the agent configuration.
type Config struct {
	Addr           string
	Token          string
	DataDir        string
	PlainHTTP      bool
	PublicStatus   bool    // expose detailed /status without authentication (opt-in)
	CPUThreshold   float64 // percent, spike event threshold
	MemThreshold   float64 // percent, spike event threshold
	StealThreshold float64 // percent, steal event threshold
	DiskThreshold  float64 // percent, disk usage event threshold
	// Process watchlist (optional)
	WatchProcs []string
	// Tunnel deployment
	DeployMode DeployMode // "scripts" (default) or "config-only"
	ConfigDir  string     // sandboxed dir for tunnel config files

	// Tunnel watchdog (Phase 4 · 4-A)
	WatchdogEnabled     bool
	WatchdogIntervalSec int
	// Multi-point probing (Phase 4 · 4-B)
	ProbeEnabled     bool
	ProbeIntervalSec int

	// Alerts
	TelegramToken        string
	TelegramChatID       string
	TelegramProxy        string
	DiscordWebhook       string
	GenericWebhook       string
	EnableAlerts         bool
	AllowPrivateWebhooks bool
}

func envOr(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}

// floatEnvOr returns the env value parsed as float64, or def when the key is
// unset/empty. A set-but-invalid value is an operator error, not a silent
// default: it returns an error so startup fails loudly (H13 — the DIDBAN_*_TH
// knobs in /etc/didban/agent.conf must actually do something, and a typo must
// not be swallowed).
func floatEnvOr(key string, def float64) (float64, error) {
	v := os.Getenv(key)
	if v == "" {
		return def, nil
	}
	f, err := strconv.ParseFloat(v, 64)
	if err != nil {
		return 0, fmt.Errorf("invalid %s=%q: expected a number, e.g. 70", key, v)
	}
	return f, nil
}

// intEnvOr: same fail-loud contract as floatEnvOr for integer knobs.
func intEnvOr(key string, def int) (int, error) {
	v := os.Getenv(key)
	if v == "" {
		return def, nil
	}
	n, err := strconv.Atoi(v)
	if err != nil {
		return 0, fmt.Errorf("invalid %s=%q: expected an integer, e.g. 30", key, v)
	}
	return n, nil
}

func main() {
	cfg := &Config{}
	flag.StringVar(&cfg.Addr, "addr", envOr("DIDBAN_ADDR", ":8686"), "HTTP listen address")
	flag.StringVar(&cfg.Token, "token", os.Getenv("DIDBAN_TOKEN"), "auth token (auto-generated if empty)")
	flag.StringVar(&cfg.DataDir, "data", envOr("DIDBAN_DATA", "/var/lib/didban"), "data directory (events, TLS certs)")
	flag.BoolVar(&cfg.PlainHTTP, "plain", os.Getenv("DIDBAN_PLAIN") == "1", "disable TLS (NOT recommended)")
	flag.BoolVar(&cfg.PublicStatus, "public-status", os.Getenv("DIDBAN_PUBLIC_STATUS") == "1", "expose detailed /status without a bearer token")
	// Spike thresholds: flag > env (DIDBAN_*_TH in /etc/didban/agent.conf) >
	// default. An invalid env value aborts startup instead of silently
	// falling back (H13).
	for _, t := range []struct {
		env, fl, usage string
		def            float64
		out            *float64
	}{
		{"DIDBAN_CPU_TH", "cpu-th", "CPU spike event threshold (percent)", 70, &cfg.CPUThreshold},
		{"DIDBAN_MEM_TH", "mem-th", "memory spike event threshold (percent)", 90, &cfg.MemThreshold},
		{"DIDBAN_STEAL_TH", "steal-th", "CPU steal event threshold (percent)", 10, &cfg.StealThreshold},
		{"DIDBAN_DISK_TH", "disk-th", "disk usage event threshold (percent)", 90, &cfg.DiskThreshold},
	} {
		v, err := floatEnvOr(t.env, t.def)
		if err != nil {
			fmt.Fprintln(os.Stderr, "Error:", err)
			os.Exit(1)
		}
		flag.Float64Var(t.out, t.fl, v, t.usage)
	}
	flag.StringVar(&cfg.TelegramToken, "tg-token", os.Getenv("DIDBAN_TG_TOKEN"), "Telegram Bot Token for alerts")
	flag.StringVar(&cfg.TelegramChatID, "tg-chat", os.Getenv("DIDBAN_TG_CHAT_ID"), "Telegram Chat/Channel ID for alerts")
	flag.StringVar(&cfg.TelegramProxy, "tg-proxy", os.Getenv("DIDBAN_TG_PROXY"), "HTTP/SOCKS5 proxy for Telegram API")
	flag.StringVar(&cfg.DiscordWebhook, "discord-webhook", os.Getenv("DIDBAN_DISCORD_WEBHOOK"), "Discord Webhook URL for alerts")
	flag.StringVar(&cfg.GenericWebhook, "webhook-url", os.Getenv("DIDBAN_WEBHOOK_URL"), "Generic Webhook URL for alerts")
	flag.BoolVar(&cfg.EnableAlerts, "alerts", os.Getenv("DIDBAN_ALERTS") != "0", "Enable outbound alerts")
	flag.BoolVar(&cfg.AllowPrivateWebhooks, "allow-private-webhooks", os.Getenv("DIDBAN_ALLOW_PRIVATE_WEBHOOKS") == "1", "allow alert webhooks/proxies to reach private networks")
	flag.StringVar((*string)(&cfg.DeployMode), "deploy-mode", envOr("DIDBAN_DEPLOY_MODE", "scripts"), "tunnel deploy mode: 'scripts' (default) or 'config-only' (never execute install scripts)")
	flag.StringVar(&cfg.ConfigDir, "config-dir", envOr("DIDBAN_TUNNEL_CONFIG_DIR", "/etc/didban/tunnels"), "sandboxed directory for tunnel configuration files")
	// Tunnel watchdog: enabled by default; interval 5..600s. A set-but-invalid
	// interval aborts startup (fail-loud, same contract as the thresholds).
	cfg.WatchdogEnabled = os.Getenv("DIDBAN_WATCHDOG_ENABLED") != "0"
	wdSec, err := intEnvOr("DIDBAN_WATCHDOG_INTERVAL_SEC", 30)
	if err != nil {
		fatal("%v", err)
	}
	if wdSec < 5 || wdSec > 600 {
		fatal("invalid DIDBAN_WATCHDOG_INTERVAL_SEC=%d: must be 5..600", wdSec)
	}
	cfg.WatchdogIntervalSec = wdSec

	// Multi-point probing: enabled by default; interval 10..3600s. A
	// set-but-invalid value is a loud startup failure (same contract as the
	// watchdog/thresholds).
	cfg.ProbeEnabled = os.Getenv("DIDBAN_PROBE_ENABLED") != "0"
	psSec, err := intEnvOr("DIDBAN_PROBE_INTERVAL_SEC", 60)
	if err != nil || psSec < 10 || psSec > 3600 {
		fatal("invalid DIDBAN_PROBE_INTERVAL_SEC=%d: must be 10..3600", psSec)
	}
	cfg.ProbeIntervalSec = psSec
	printVersion := flag.Bool("version", false, "print version and exit")
	flag.Parse()

	if !cfg.DeployMode.valid() {
		fatal("invalid deploy mode %q (use 'scripts' or 'config-only')", cfg.DeployMode)
	}

	if *printVersion {
		fmt.Println("didban-agent", version)
		return
	}

	if err := ensurePrivateDirectory(cfg.DataDir); err != nil {
		fatal("cannot secure data directory %s: %v", cfg.DataDir, err)
	}

	// Resolve or generate the auth token.
	if cfg.Token == "" {
		cfg.Token = loadOrCreateToken(filepath.Join(cfg.DataDir, "token"))
	}

	// Prepare TLS (self-signed, auto-generated) unless running in plain mode.
	certPath := filepath.Join(cfg.DataDir, "cert.pem")
	keyPath := filepath.Join(cfg.DataDir, "key.pem")
	fingerprint := ""
	if !cfg.PlainHTTP {
		var err error
		fingerprint, err = ensureSelfSigned(certPath, keyPath)
		if err != nil {
			fatal("TLS setup failed: %v", err)
		}
	}

	if w := os.Getenv("DIDBAN_WATCH"); w != "" {
		for _, name := range strings.Split(w, ",") {
			if name = strings.TrimSpace(name); name != "" {
				cfg.WatchProcs = append(cfg.WatchProcs, name)
			}
		}
	}

	mon := NewMonitor(cfg)
	tm := NewTunnelManager(cfg.DataDir, cfg.ConfigDir, cfg.DeployMode)
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	go mon.Run(ctx)

	var wd *TunnelWatchdog
	if cfg.WatchdogEnabled {
		wd = NewTunnelWatchdog(tm, mon, time.Duration(cfg.WatchdogIntervalSec)*time.Second)
		go wd.Run(ctx)
	}

	var pm *ProbeMonitor
	if cfg.ProbeEnabled {
		pm = NewProbeMonitor(cfg.DataDir, mon, time.Duration(cfg.ProbeIntervalSec)*time.Second)
		go pm.Run(ctx)
	}

	srv := &http.Server{
		Addr:              cfg.Addr,
		Handler:           newAPI(cfg, mon, tm, wd, pm).routes(),
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       5 * time.Minute, // permits the bounded bandwidth upload
		WriteTimeout:      5 * time.Minute, // permits the bounded bandwidth download
		IdleTimeout:       60 * time.Second,
		MaxHeaderBytes:    32 * 1024,
		TLSConfig: &tls.Config{
			MinVersion: tls.VersionTLS12,
		},
	}

	banner(cfg, fingerprint, mon.dispatcher.HasActiveProviders(), cfg.WatchdogEnabled, cfg.WatchdogIntervalSec, cfg.ProbeEnabled, cfg.ProbeIntervalSec, tokenFirstShow(cfg.DataDir))

	listener, err := net.Listen("tcp", cfg.Addr)
	if err != nil {
		fatal("cannot listen on %s: %v", cfg.Addr, err)
	}
	listener = newLimitedListener(listener, maxActiveConnections)
	if !cfg.PlainHTTP {
		pair, err := tls.LoadX509KeyPair(certPath, keyPath)
		if err != nil {
			_ = listener.Close()
			fatal("cannot load TLS credentials: %v", err)
		}
		tlsConfig := srv.TLSConfig.Clone()
		tlsConfig.Certificates = []tls.Certificate{pair}
		listener = tls.NewListener(listener, tlsConfig)
	}

	errCh := make(chan error, 1)
	go func() { errCh <- srv.Serve(listener) }()

	select {
	case err := <-errCh:
		fatal("server error: %v", err)
	case <-ctx.Done():
	}

	fmt.Println("didban-agent: shutting down...")
	shutdownCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	_ = srv.Shutdown(shutdownCtx)
}

func loadOrCreateToken(path string) string {
	if info, err := os.Lstat(path); err == nil {
		if info.Mode()&os.ModeSymlink != 0 || !info.Mode().IsRegular() {
			fatal("refusing non-regular token file %s", path)
		}
		if err := os.Chmod(path, 0o600); err != nil {
			fatal("cannot secure token file %s: %v", path, err)
		}
		if b, err := os.ReadFile(path); err == nil {
			if t := strings.TrimSpace(string(b)); t != "" {
				return t
			}
		}
	}
	b := make([]byte, 24)
	if _, err := rand.Read(b); err != nil {
		fatal("cannot generate token: %v", err)
	}
	tok := hex.EncodeToString(b)
	if err := secureWriteFileAtomic(path, []byte(tok), 0o600); err != nil {
		fatal("cannot write token file %s: %v", path, err)
	}
	return tok
}

// tokenFirstShow returns true only on the very first agent start (before the
// token was ever printed). After that, the banner shows a token prefix so the
// full secret does not accumulate in the systemd journal across restarts.
func tokenFirstShow(dataDir string) bool {
	marker := filepath.Join(dataDir, "token.shown")
	if _, err := os.Stat(marker); err == nil {
		return false
	}
	if err := secureWriteFileAtomic(marker, []byte(time.Now().UTC().Format(time.RFC3339)), 0o600); err != nil {
		fmt.Fprintf(os.Stderr, "warning: could not persist startup marker: %v\n", err)
	}
	return true
}

// tokenPrefix returns a short, non-secret preview of the token for logging.
func tokenPrefix(tok string) string {
	if len(tok) <= 8 {
		return "****"
	}
	return tok[:8] + "…"
}

func banner(cfg *Config, fingerprint string, alertsActive bool, watchdogEnabled bool, watchdogIntervalSec int, probeEnabled bool, probeIntervalSec int, showFullToken bool) {
	scheme := "https"
	host := firstLocalIP()
	if cfg.PlainHTTP {
		scheme = "http"
	}
	addr := cfg.Addr
	if strings.HasPrefix(addr, "0.0.0.0:") {
		addr = ":" + strings.TrimPrefix(addr, "0.0.0.0:")
	}
	if strings.HasPrefix(addr, ":") {
		addr = host + addr
	}
	fmt.Println("──────────────────────────────────────────────────────")
	fmt.Println("  Didban Agent — دیدبان")
	fmt.Println("──────────────────────────────────────────────────────")
	fmt.Printf("  Version:      %s\n", version)
	fmt.Printf("  Listening:    %s://%s\n", scheme, addr)
	fmt.Printf("  Status Page:  %s://%s/status\n", scheme, addr)
	if showFullToken {
		fmt.Printf("  Token:        %s\n", cfg.Token)
	} else {
		fmt.Printf("  Token:        %s\n", tokenPrefix(cfg.Token))
	}
	if fingerprint != "" {
		fmt.Printf("  Cert SHA256:  %s\n", fingerprint)
	}
	fmt.Printf("  Data dir:     %s\n", cfg.DataDir)
	fmt.Printf("  Thresholds:   cpu>%.0f%%  mem>%.0f%%  steal>%.0f%%  disk>%.0f%%\n", cfg.CPUThreshold, cfg.MemThreshold, cfg.StealThreshold, cfg.DiskThreshold)
	fmt.Printf("  Deploy mode:  %s\n", cfg.DeployMode)
	if watchdogEnabled {
		fmt.Printf("  Watchdog:     Enabled (tunnel check every %ds)\n", watchdogIntervalSec)
	}
	if probeEnabled {
		fmt.Printf("  Probe:        Enabled (multi-point check every %ds)\n", probeIntervalSec)
	} else {
		fmt.Println("  Watchdog:     Disabled")
	}
	if len(cfg.WatchProcs) > 0 {
		fmt.Printf("  Process watch: %s\n", strings.Join(cfg.WatchProcs, ", "))
	}
	if alertsActive {
		fmt.Printf("  Alerts:       Enabled (Telegram/Discord/Webhook)\n")
	} else {
		fmt.Println("  Alerts:       Disabled (configure Telegram, Discord, or Webhook)")
	}
	fmt.Println("──────────────────────────────────────────────────────")
	if showFullToken {
		if fingerprint != "" {
			fmt.Printf("  Mobile Link:  didban://%s?token=%s&fp=%s\n", addr, cfg.Token, fingerprint)
		} else {
			fmt.Printf("  Mobile Link:  didban://%s?token=%s\n", addr, cfg.Token)
		}
		fmt.Printf("  Test:         curl -k %s://%s/api/metrics -H \"Authorization: Bearer %s\"\n",
			scheme, addr, cfg.Token)
	} else {
		fmt.Println("  Mobile Link:  token was printed on the first start (see install output / data dir)")
		fmt.Printf("  Test:         curl -k %s://%s/api/metrics -H \"Authorization: Bearer <token>\"\n",
			scheme, addr)
	}
	fmt.Println("──────────────────────────────────────────────────────")
}

// firstLocalIP returns the first non-loopback IPv4 of this machine (best effort).
func firstLocalIP() string {
	ifaces, err := net.Interfaces()
	if err != nil {
		return "127.0.0.1"
	}
	for _, iface := range ifaces {
		if iface.Flags&net.FlagLoopback != 0 || iface.Flags&net.FlagUp == 0 {
			continue
		}
		addrs, _ := iface.Addrs()
		for _, a := range addrs {
			if ipn, ok := a.(*net.IPNet); ok {
				if v4 := ipn.IP.To4(); v4 != nil {
					return v4.String()
				}
			}
		}
	}
	return "127.0.0.1"
}

func fatal(format string, args ...any) {
	fmt.Fprintf(os.Stderr, "didban-agent: "+format+"\n", args...)
	os.Exit(1)
}
