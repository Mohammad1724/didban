package main

import (
	"testing"
	"time"
)

// TestParseSystemdTimestamp covers every format `systemctl show` can emit for a
// timestamp property. The regression this guards: ActiveEnterTimestamp was
// parsed as RFC3339, but systemctl prints a human string, so the parse always
// failed, UptimeSec stayed 0 and the watchdog reported a permanent crash_loop.
func TestParseSystemdTimestamp(t *testing.T) {
	ref := time.Date(2019, 12, 11, 21, 44, 50, 0, time.UTC)

	cases := []struct {
		name  string
		value string
		want  bool
		unix  int64 // checked only when want == true
	}{
		{"unix seconds (--timestamp=unix)", "1576100690", true, ref.Unix()},
		{"unix seconds with padding", "  1576100690 \n", true, ref.Unix()},
		{"human C-locale with MST", "Wed 2019-12-11 21:44:50 UTC", true, ref.Unix()},
		{"human C-locale with offset", "Wed 2019-12-11 21:44:50 +0000", true, ref.Unix()},
		{"human without weekday", "2019-12-11 21:44:50 UTC", true, ref.Unix()},
		{"rfc3339", "2019-12-11T21:44:50Z", true, ref.Unix()},
		{"empty means never active", "", false, 0},
		{"whitespace only", "   ", false, 0},
		{"n/a", "n/a", false, 0},
		{"dash", "-", false, 0},
		{"none", "none", false, 0},
		{"zero epoch is not a valid uptime anchor", "0", false, 0},
		{"garbage", "not-a-timestamp", false, 0},
		{"partial date", "Wed 2019-12-11", false, 0},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			got, ok := parseSystemdTimestamp(tc.value)
			if ok != tc.want {
				t.Fatalf("parseSystemdTimestamp(%q) ok = %v, want %v", tc.value, ok, tc.want)
			}
			if !ok {
				return
			}
			if got.Unix() != tc.unix {
				t.Errorf("parseSystemdTimestamp(%q) = %d (%s), want %d (%s)",
					tc.value, got.Unix(), got, tc.unix, time.Unix(tc.unix, 0).UTC())
			}
		})
	}
}

// TestParseSystemdTimestampNeverReturnsEpoch guards the specific failure mode:
// an unparseable value must not silently become the zero time, because
// time.Since(zero) is a huge positive number that would make every unit look
// infinitely healthy — the opposite of the crash_loop bug, and just as wrong.
func TestParseSystemdTimestampNeverReturnsEpoch(t *testing.T) {
	for _, v := range []string{"", "n/a", "garbage", "Wed 2019-12-11 21:44:50 UTC UTC"} {
		got, ok := parseSystemdTimestamp(v)
		if ok && got.IsZero() {
			t.Errorf("parseSystemdTimestamp(%q) returned ok with the zero time", v)
		}
		if !ok && !got.IsZero() {
			t.Errorf("parseSystemdTimestamp(%q) returned !ok with a non-zero time %s", v, got)
		}
	}
}

func TestUptimeSeconds(t *testing.T) {
	if got := uptimeSeconds(time.Now().Add(-90 * time.Second)); got < 88 || got > 91 {
		t.Errorf("uptimeSeconds(90s ago) = %d, want ~90", got)
	}
	// Clock skew / future timestamp must clamp, never go negative.
	if got := uptimeSeconds(time.Now().Add(1 * time.Hour)); got != 0 {
		t.Errorf("uptimeSeconds(future) = %d, want 0", got)
	}
	if got := uptimeSeconds(time.Time{}); got <= 0 {
		t.Errorf("uptimeSeconds(zero time) = %d, want a large positive value", got)
	}
}

func TestFormatUptime(t *testing.T) {
	cases := []struct {
		sec  uint64
		want string
	}{
		{0, "0s"},
		{1, "1s"},
		{59, "59s"},
		{60, "1m 0s"},
		{65, "1m 5s"},
		{3599, "59m 59s"},
		{3600, "1h 0m"},
		{3725, "1h 2m"},
		{86399, "23h 59m"},
		{86400, "1d 0h 0m"},
		{93784, "1d 2h 3m"},
	}
	for _, tc := range cases {
		if got := formatUptime(tc.sec); got != tc.want {
			t.Errorf("formatUptime(%d) = %q, want %q", tc.sec, got, tc.want)
		}
	}
}

// TestWatchInputsUptimeFromHumanTimestamp is the end-to-end shape of the bug:
// given the value systemctl actually prints, WatchInputs must produce a
// plausible UptimeSec rather than 0. It exercises the parser the same way the
// property loop does, without needing a systemd host.
func TestWatchInputsUptimeFromHumanTimestamp(t *testing.T) {
	line := "ActiveEnterTimestamp=" + time.Now().Add(-2*time.Minute).Format("Mon 2006-01-02 15:04:05 MST")
	value := line[len("ActiveEnterTimestamp="):]
	ts, ok := parseSystemdTimestamp(value)
	if !ok {
		t.Fatalf("could not parse %q — this is exactly the crash_loop regression", value)
	}
	if got := uptimeSeconds(ts); got < 118 || got > 122 {
		t.Errorf("UptimeSec = %d, want ~120", got)
	}
}
