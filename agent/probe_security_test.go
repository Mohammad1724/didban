package main

import (
	"net"
	"strings"
	"testing"
	"time"
)

func TestPublicProbeIPRejectsLocalAndMetadataRanges(t *testing.T) {
	blocked := []string{"127.0.0.1", "10.0.0.1", "172.16.0.1", "192.168.1.1", "169.254.169.254", "100.64.0.1", "::1", "fc00::1"}
	for _, raw := range blocked {
		if publicProbeIP(net.ParseIP(raw)) {
			t.Errorf("%s accepted as public", raw)
		}
	}
	for _, raw := range []string{"1.1.1.1", "2606:4700:4700::1111"} {
		if !publicProbeIP(net.ParseIP(raw)) {
			t.Errorf("%s rejected as non-public", raw)
		}
	}
}

func TestProbeMonitorRequiresGlobalAndPerTargetPrivateOptIn(t *testing.T) {
	spec := ProbeTargetSpec{Name: "internal", Mode: ProbeTCP, Host: "127.0.0.1", Port: 80, AllowPrivate: true}
	blocked := NewProbeMonitor(t.TempDir(), nil, time.Minute)
	if err := blocked.SetTargets([]ProbeTargetSpec{spec}); err == nil || !strings.Contains(err.Error(), "agent-level opt-in") {
		t.Fatalf("private target accepted without global opt-in: %v", err)
	}
	allowed := NewProbeMonitor(t.TempDir(), nil, time.Minute, true)
	if err := allowed.SetTargets([]ProbeTargetSpec{spec}); err != nil {
		t.Fatalf("double opt-in rejected: %v", err)
	}
}

func TestProbeOnceRequiresExplicitPrivateOptIn(t *testing.T) {
	res := probeOnce(ProbeTargetSpec{Name: "metadata", Mode: ProbeTCP, Host: "127.0.0.1", Port: 80})
	if res.Up || !strings.Contains(res.Detail, "allow_private") {
		t.Fatalf("unexpected result: %+v", res)
	}
}
