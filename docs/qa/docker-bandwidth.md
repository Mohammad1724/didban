# Docker and bandwidth compatibility / regression repair

## Findings (2026-09-19)

The user updated only the Android app. At investigation time the latest public Agent release was **v0.6.1** (published 2026-09-03); that tag's `agent/api.go` does not register `/api/docker/containers` or `/api/bandwidth/download`. Both routes exist on current main. This establishes a release/main compatibility gap, **not proof of the version or proxy configuration on the user's private server**. An HTTP 404 can also come from the wrong port or proxy. Missing Docker itself is different: the current agent returns a 200 Docker summary with availability/error fields.

Separately, the bandwidth screen launched a Main coroutine and called blocking `Call.execute()` / response reads directly in it. That is invalid Android main-thread networking; an exception without a message was reduced to the generic “Network error”. Live throughput also divided by elapsed milliseconds as if they were seconds, and the running flag was saved across recreation even though its job was cancelled.

## Changes

- Extracted a bandwidth engine with IO-side TCP sampling and asynchronous OkHttp response consumption. Cancellation cancels the active call; each short-lived streaming client is released. No TLS/pin weakening or authentication bypass.
- Correct per-leg Mbps timing, exact download size and upload `received_bytes` acknowledgement. Partial transfers cannot become a successful result. No invented 999 ms ping / 2 ms jitter; one latency sample shows unknown jitter. The copy explicitly describes TCP handshake failures rather than measured ICMP loss.
- The shell-selected connection is authoritative. Run callbacks read the latest selection. Cancel is visible; recreation cancels the run instead of restoring a stuck busy flag. Progress updates cannot leak from a cancelled run into a newer run.
- HTTP status is preserved for GET API failures. Docker and bandwidth distinguish endpoint absence, authentication, oversized payload, DNS, TLS, timeout and connection failures. Both tokens are redacted from fallback detail. Docker daemon errors are no longer mistaken for an empty, healthy container list.
- The installer stages/validates the executable before atomic replacement and explicitly restarts the service. `systemctl enable --now` alone does not restart an already running old executable. Existing config, tokens and certificate data remain unchanged unless rotation is explicitly requested.

## Updating a main-build Agent

The default installer downloads **latest Release**, not latest main. Rerunning it can reinstall the same incompatible older release. Use a verified Agent artifact from the **same CI build as the app**, or compile the reviewed main revision locally on the server (git and an up-to-date Go toolchain, minimum language version 1.22, required):

```sh
(
  set -e
  workdir="$(mktemp -d)"
  git clone --depth 1 https://github.com/Mohammad1724/didban.git "$workdir/didban"
  cd "$workdir/didban/agent"
  CGO_ENABLED=0 go build -trimpath -ldflags "-s -w -X main.version=$(git rev-parse --short HEAD)" -o didban-agent .
  sudo env DIDBAN_ROTATE_TOKENS=0 bash ./install.sh
  sudo systemctl is-active didban-agent
  /usr/local/bin/didban-agent -version
)
```

Review the source revision first; a published per-fix guide can pin the exact tested commit. Back up custom deployment configuration privately before upgrading. The installer updates the base systemd unit; retain any custom service overrides. Expect a brief Agent restart. Do not delete `/etc/didban`, `/var/lib/didban`, the Android connection, or its saved certificate pin. The install summary contains credentials: do not share it or commit it. For prebuilt binaries verify the matching `.sha256`, extract into a separate directory, and run the reviewed installer **from that directory** so it uses `./didban-agent` rather than downloading the old Release.

If 404 remains after a verified new process is running, check the app host/port, the proxy path forwarding and whether the traffic actually reaches that Agent. Do not disable TLS verification or install Docker merely to remedy an HTTP endpoint 404.

## Verification

- Android: **409 tests, 406 passed, 3 existing live-test skips, zero failures**. New tests: 10 loopback HTTP/engine cases and 4 Robolectric UI cases.
- Real local streamed HTTP tests cover both legs, read-only authentication, byte counts, delayed response/UI responsiveness, cancellation, truncated transfers, 404 and 413. Fake UI runner tests cover current selection, errors, Cancel and saved-state restoration. These are not measurements of the user's network or tests on their phone.
- Lint: **0 errors, 51 existing warnings, 5 information items**. Debug APK built and v2 signature verified. On the 2 GB runner, combined lint/dex ran out of memory; isolated external-dex merge at 640 MB followed by a separate assemble completed successfully.
- Agent: `go vet`, `go test -race` (128 top-level tests passed), and build passed. Installer: 14 checks passed, including credential/config preservation, restart wiring and failed binary validation. The installer uses stubbed systemctl in tests; no private server was accessed or upgraded.

Manual acceptance: install matching app/Agent builds, confirm both new endpoints reach the correct process, test real Docker socket availability and a full 100 MiB download + 100 MiB upload, switch servers, cancel during each leg, rotate and navigate away. Preserve pin verification throughout.
