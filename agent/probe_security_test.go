package main

import (
	"net"
	"strings"
	"testing"
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

func TestProbeOnceRequiresExplicitPrivateOptIn(t *testing.T) {
	res := probeOnce(ProbeTargetSpec{Name: "metadata", Mode: ProbeTCP, Host: "127.0.0.1", Port: 80})
	if res.Up || !strings.Contains(res.Detail, "allow_private") {
		t.Fatalf("unexpected result: %+v", res)
	}
}
