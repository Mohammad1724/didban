package main

import (
	"testing"
)

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
