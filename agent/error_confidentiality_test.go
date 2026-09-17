package main

import (
	"errors"
	"strings"
	"testing"
)

func TestPublicErrorRedactsCredentialsAndBoundsLength(t *testing.T) {
	secret := "very-secret-password"
	message := publicError(errors.New("request https://alice:" + secret + "@example.test/path?token=" + secret + " failed: " + strings.Repeat("x", 500)))
	if strings.Contains(message, secret) {
		t.Fatalf("public error leaked credentials: %q", message)
	}
	if len(message) > 260 {
		t.Fatalf("public error was not bounded: %d", len(message))
	}
}
