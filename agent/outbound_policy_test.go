package main

import "testing"

func TestOutboundHTTPSRejectsPrivateAndUnsafeURLs(t *testing.T) {
	bad := []string{
		"http://1.1.1.1/hook",
		"https://user:pass@1.1.1.1/hook",
		"https://127.0.0.1/hook",
		"https://169.254.169.254/latest/meta-data",
		"https://1.1.1.1:8443/hook",
	}
	for _, raw := range bad {
		if _, err := validateOutboundHTTPS(raw, nil, false); err == nil {
			t.Errorf("unsafe URL accepted: %s", raw)
		}
	}
}

func TestOutboundHTTPSPrivateRequiresExplicitOptIn(t *testing.T) {
	if _, err := validateOutboundHTTPS("https://127.0.0.1/hook", nil, true); err != nil {
		t.Fatalf("explicit private opt-in rejected: %v", err)
	}
}

func TestOutboundHTTPSHostAllowlist(t *testing.T) {
	allowed := map[string]bool{"discord.com": true}
	if _, err := validateOutboundHTTPS("https://1.1.1.1/api/webhooks/x", allowed, false); err == nil {
		t.Fatal("non-allowlisted host accepted")
	}
}

func TestOutboundProxySchemePolicy(t *testing.T) {
	if _, err := validateOutboundProxy("file:///etc/passwd", true); err == nil {
		t.Fatal("file proxy accepted")
	}
	if _, err := validateOutboundProxy("socks5://127.0.0.1:1080", false); err == nil {
		t.Fatal("private proxy accepted without opt-in")
	}
	if _, err := validateOutboundProxy("socks5://127.0.0.1:1080", true); err != nil {
		t.Fatalf("private proxy opt-in rejected: %v", err)
	}
}
