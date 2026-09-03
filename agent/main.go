// Didban (دیدبان) — a tiny Linux server monitoring agent.
// It exposes CPU / memory / disk / network metrics, top processes
// and a spike event log over an authenticated HTTP(S) JSON API.
//
// Standard library only — no external dependencies.
package main

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"flag"
	"fmt"
	"net"
	"net/http"
	"os"
	"os/signal"
	"path/filepath"
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
	CPUThreshold   float64 // percent, spike event threshold
	MemThreshold   float64 // percent, spike event threshold
	StealThreshold float64 // percent, steal event threshold
	DiskThreshold  float64 // percent, disk usage event threshold
	// PasarGuard panel monitoring (optional)
	PanelURL      string
	PanelUser     string
	PanelPass     string
	PanelInsecure bool
	// Process watchlist (optional)
	WatchProcs []string
}

func envOr(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}

func main() {
	cfg := &Config{}
	flag.StringVar(&cfg.Addr, "addr", envOr("DIDBAN_ADDR", ":8686"), "HTTP listen address")
	flag.StringVar(&cfg.Token, "token", os.Getenv("DIDBAN_TOKEN"), "auth token (auto-generated if empty)")
	flag.StringVar(&cfg.DataDir, "data", envOr("DIDBAN_DATA", "/var/lib/didban"), "data directory (events, TLS certs)")
	flag.BoolVar(&cfg.PlainHTTP, "plain", os.Getenv("DIDBAN_PLAIN") == "1", "disable TLS (NOT recommended)")
	flag.Float64Var(&cfg.CPUThreshold, "cpu-th", 70, "CPU spike event threshold (percent)")
	flag.Float64Var(&cfg.MemThreshold, "mem-th", 90, "memory spike event threshold (percent)")
	flag.Float64Var(&cfg.StealThreshold, "steal-th", 10, "CPU steal event threshold (percent)")
	flag.Float64Var(&cfg.DiskThreshold, "disk-th", 90, "disk usage event threshold (percent)")
	printVersion := flag.Bool("version", false, "print version and exit")
	flag.Parse()

	if *printVersion {
		fmt.Println("didban-agent", version)
		return
	}

	if err := os.MkdirAll(cfg.DataDir, 0o700); err != nil {
		fatal("cannot create data directory %s: %v", cfg.DataDir, err)
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

	cfg.PanelURL = envOr("DIDBAN_PANEL_URL", "")
	cfg.PanelUser = envOr("DIDBAN_PANEL_USER", "")
	cfg.PanelPass = envOr("DIDBAN_PANEL_PASS", "")
	cfg.PanelInsecure = os.Getenv("DIDBAN_PANEL_INSECURE") == "1"
	if w := os.Getenv("DIDBAN_WATCH"); w != "" {
		for _, name := range strings.Split(w, ",") {
			if name = strings.TrimSpace(name); name != "" {
				cfg.WatchProcs = append(cfg.WatchProcs, name)
			}
		}
	}

	mon := NewMonitor(cfg)
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	go mon.Run(ctx)

	var panel *PanelMonitor
	if cfg.PanelURL != "" && cfg.PanelUser != "" {
		panel = NewPanelMonitor(cfg, mon.events)
		go panel.Run(ctx)
	}

	srv := &http.Server{
		Addr:              cfg.Addr,
		Handler:           newAPI(cfg, mon, panel).routes(),
		ReadHeaderTimeout: 5 * time.Second,
	}

	banner(cfg, fingerprint)

	errCh := make(chan error, 1)
	go func() {
		if cfg.PlainHTTP {
			errCh <- srv.ListenAndServe()
		} else {
			errCh <- srv.ListenAndServeTLS(certPath, keyPath)
		}
	}()

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
	if b, err := os.ReadFile(path); err == nil {
		if t := strings.TrimSpace(string(b)); t != "" {
			return t
		}
	}
	b := make([]byte, 24)
	if _, err := rand.Read(b); err != nil {
		fatal("cannot generate token: %v", err)
	}
	tok := hex.EncodeToString(b)
	if err := os.WriteFile(path, []byte(tok), 0o600); err != nil {
		fatal("cannot write token file %s: %v", path, err)
	}
	return tok
}

func banner(cfg *Config, fingerprint string) {
	scheme := "https"
	host := firstLocalIP()
	if cfg.PlainHTTP {
		scheme = "http"
	}
	// For display: expand ":port" or "0.0.0.0:port" into a real reachable address.
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
	fmt.Printf("  Token:        %s\n", cfg.Token)
	if fingerprint != "" {
		fmt.Printf("  Cert SHA256:  %s\n", fingerprint)
	}
	fmt.Printf("  Data dir:     %s\n", cfg.DataDir)
	fmt.Printf("  Thresholds:   cpu>%.0f%%  mem>%.0f%%  steal>%.0f%%  disk>%.0f%%\n", cfg.CPUThreshold, cfg.MemThreshold, cfg.StealThreshold, cfg.DiskThreshold)
	if cfg.PanelURL != "" {
		fmt.Printf("  Panel watch:  %s (user: %s)\n", cfg.PanelURL, cfg.PanelUser)
	}
	if len(cfg.WatchProcs) > 0 {
		fmt.Printf("  Process watch: %s\n", strings.Join(cfg.WatchProcs, ", "))
	}
	fmt.Println("──────────────────────────────────────────────────────")
	fmt.Printf("  Test:  curl -k %s://%s/api/metrics -H \"Authorization: Bearer %s\"\n",
		scheme, addr, cfg.Token)
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
