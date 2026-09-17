package main

import (
	"context"
	"crypto/tls"
	"fmt"
	"net"
	"net/http"
	"net/url"
	"strings"
	"time"
)

const maxOutboundURLLength = 4096

func validateOutboundHTTPS(raw string, allowedHosts map[string]bool, allowPrivate bool) (string, error) {
	if len(raw) == 0 || len(raw) > maxOutboundURLLength {
		return "", fmt.Errorf("outbound URL length is invalid")
	}
	u, err := url.Parse(raw)
	if err != nil || !strings.EqualFold(u.Scheme, "https") || u.Hostname() == "" {
		return "", fmt.Errorf("outbound URL must use HTTPS")
	}
	if u.User != nil || u.Fragment != "" {
		return "", fmt.Errorf("outbound URL credentials and fragments are not allowed")
	}
	if u.Port() != "" && u.Port() != "443" {
		return "", fmt.Errorf("outbound URL must use port 443")
	}
	host := strings.ToLower(strings.TrimSuffix(u.Hostname(), "."))
	if allowedHosts != nil && !allowedHosts[host] {
		return "", fmt.Errorf("outbound URL host is not allowed")
	}
	if !allowPrivate {
		if _, err := resolvePublicHost(host); err != nil {
			return "", err
		}
	}
	return u.String(), nil
}

func resolvePublicHost(host string) ([]net.IP, error) {
	ips, err := net.LookupIP(host)
	if err != nil || len(ips) == 0 {
		return nil, fmt.Errorf("outbound host did not resolve")
	}
	for _, ip := range ips {
		if !publicProbeIP(ip) {
			return nil, fmt.Errorf("outbound host resolves to a private or reserved address")
		}
	}
	return ips, nil
}

func validateOutboundProxy(raw string, allowPrivate bool) (*url.URL, error) {
	if len(raw) == 0 || len(raw) > maxOutboundURLLength {
		return nil, fmt.Errorf("proxy URL length is invalid")
	}
	u, err := url.Parse(raw)
	if err != nil || u.Hostname() == "" {
		return nil, fmt.Errorf("proxy URL is invalid")
	}
	switch strings.ToLower(u.Scheme) {
	case "http", "https", "socks5", "socks5h":
	default:
		return nil, fmt.Errorf("proxy scheme is not allowed")
	}
	if !allowPrivate {
		if _, err := resolvePublicHost(u.Hostname()); err != nil {
			return nil, err
		}
	}
	return u, nil
}

func secureOutboundTransport(allowPrivate bool) *http.Transport {
	dialer := &net.Dialer{Timeout: 10 * time.Second, KeepAlive: 30 * time.Second}
	return &http.Transport{
		Proxy:           http.ProxyFromEnvironment,
		TLSClientConfig: &tls.Config{MinVersion: tls.VersionTLS12},
		DialContext: func(ctx context.Context, network, address string) (net.Conn, error) {
			host, port, err := net.SplitHostPort(address)
			if err != nil {
				return nil, fmt.Errorf("invalid outbound address")
			}
			ips, err := net.LookupIP(host)
			if err != nil || len(ips) == 0 {
				return nil, fmt.Errorf("outbound host did not resolve")
			}
			if !allowPrivate {
				for _, ip := range ips {
					if !publicProbeIP(ip) {
						return nil, fmt.Errorf("private outbound destination blocked")
					}
				}
			}
			return dialer.DialContext(ctx, network, net.JoinHostPort(ips[0].String(), port))
		},
	}
}
