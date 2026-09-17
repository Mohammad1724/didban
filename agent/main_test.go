package main

import (
	"testing"
)

func TestValidateAuthToken(t *testing.T) {
	valid := []string{
		"0123456789abcdef0123456789abcdef",
		"A-secure_token.with~symbols-123456789",
	}
	for _, token := range valid {
		if err := validateAuthToken(token); err != nil {
			t.Errorf("valid token rejected: %v", err)
		}
	}
	invalid := []string{
		"short-token",
		"0123456789abcdef 0123456789abcdef",
		"0123456789abcdef\n0123456789abcdef",
		"توکن-بسیار-طولانی-اما-غیر-اسکی-است",
		string(make([]byte, maxAuthTokenBytes+1)),
	}
	for _, token := range invalid {
		if err := validateAuthToken(token); err == nil {
			t.Errorf("invalid token accepted: %q", token)
		}
	}
}

func TestFloatEnvOr(t *testing.T) {
	t.Setenv("DIDBAN_TEST_TH", "55.5")
	if v, err := floatEnvOr("DIDBAN_TEST_TH", 70); err != nil || v != 55.5 {
		t.Fatalf("set value: got %v, err %v; want 55.5, nil", v, err)
	}

	t.Setenv("DIDBAN_TEST_TH", "")
	if v, err := floatEnvOr("DIDBAN_TEST_TH", 70); err != nil || v != 70 {
		t.Fatalf("empty value: got %v, err %v; want default 70", v, err)
	}

	t.Setenv("DIDBAN_TEST_TH", "abc")
	if _, err := floatEnvOr("DIDBAN_TEST_TH", 70); err == nil {
		t.Fatal("invalid value must return an error, not a silent default")
	}
}
