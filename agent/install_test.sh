#!/usr/bin/env bash
#
# Tests for install.sh (H13): release checksum verification + install wiring.
# Sources install.sh (its BASH_SOURCE guard keeps main() from auto-running),
# then exercises verify_release() with real files and main() end-to-end with
# stubbed curl/install/systemctl/journalctl/hostname — no root required.
#
# Usage:  bash install_test.sh
#
set -u
DIR="$(cd "$(dirname "$0")" && pwd)"
PASS=0; FAIL=0
ok()  { PASS=$((PASS+1)); echo "  ok: $1"; }
bad() { FAIL=$((FAIL+1)); echo "  FAIL: $1"; }

# ── source the installer (defines functions; main() does NOT run) ────────────
# shellcheck disable=SC1091
source "$DIR/install.sh"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# ── unit: verify_release ─────────────────────────────────────────────────────
echo "── verify_release (unit) ──"

F="$WORK/a.tar.gz"; S="$WORK/a.tar.gz.sha256"
printf 'binary-bytes' > "$F"
( cd "$WORK" && sha256sum a.tar.gz > a.tar.gz.sha256 )

# verify_release calls die→exit on failure, so each call runs in a subshell.
run_verify() { (verify_release "$F" "$S" >"$WORK/out" 2>&1); }

if run_verify && grep -q "SHA-256 verified" "$WORK/out"; then
  ok "valid checksum accepted"
else
  bad "valid checksum rejected: $(cat "$WORK/out")"
fi

printf 'tampered-bytes' > "$F"
if run_verify; then
  bad "tampered file was accepted"
else
  if grep -q "SHA-256 mismatch" "$WORK/out"; then ok "tampered file rejected with mismatch message"; else bad "mismatch message missing: $(cat "$WORK/out")"; fi
fi

printf 'binary-bytes' > "$F"
echo "$(awk '{print $1}' "$S")  wrong-name.tar.gz" > "$S"
if run_verify; then
  bad "checksum for a different filename was accepted"
else
  if grep -q "refusing to install" "$WORK/out"; then ok "wrong-filename checksum rejected"; else bad "wrong-filename message missing: $(cat "$WORK/out")"; fi
fi

echo "not-a-hash  a.tar.gz" > "$S"
if run_verify; then
  bad "malformed checksum file was accepted"
else
  if grep -q "malformed" "$WORK/out"; then ok "malformed checksum file rejected"; else bad "malformed message missing: $(cat "$WORK/out")"; fi
fi

: > "$S"
if run_verify; then
  bad "empty checksum file was accepted"
else
  ok "empty checksum file rejected"
fi

# ── e2e: main() with stubs ───────────────────────────────────────────────────
echo "── main() end-to-end (stubbed, no root) ──"

# fixture: a tar.gz containing a fake executable didban-agent
FIX="$WORK/fx/didban-agent-linux-amd64.tar.gz"
mkdir -p "$WORK/fx/payload"
printf '#!/bin/sh\necho "didban-agent fixture 9.9.9"\n' > "$WORK/fx/payload/didban-agent"
chmod +x "$WORK/fx/payload/didban-agent"
tar -czf "$FIX" -C "$WORK/fx/payload" didban-agent

STUB_LOG="$WORK/stub.log"
: > "$STUB_LOG"

# stubs (functions shadow external commands / builtins inside main's subshell)
curl() {
  echo "curl $*" >> "$STUB_LOG"
  local out=""
  while [[ $# -gt 0 ]]; do
    if [[ "$1" == "-o" ]]; then out="$2"; shift 2; continue; fi
    shift
  done
  if [[ "$out" == *.sha256 ]]; then
    if [[ "${STUB_SHA_FAIL:-0}" == 1 ]]; then return 22; fi
    local base
    base="$(basename "${out%.sha256}")"
    if [[ "${STUB_SHA_TAMPER:-0}" == 1 ]]; then
      echo "0000000000000000000000000000000000000000000000000000000000000000  $base" > "$out"
    else
      echo "$(sha256_of "$FIX")  $base" > "$out"
    fi
    return 0
  fi
  cp "$FIX" "$out"
}
install() {
  echo "install $*" >> "$STUB_LOG"
  local src="" dst=""
  for a in "$@"; do src="$dst"; dst="$a"; done
  cp "$src" "$dst"
  chmod +x "$dst"
}
systemctl()  { echo "systemctl $*" >> "$STUB_LOG"; }
journalctl() { return 0; }
hostname()   { echo "10.9.9.9"; }
require_root() { :; }

run_main() {  # sets MAIN_OUT / MAIN_RC
  MAIN_RC=0
  MAIN_OUT="$( (main) 2>"$WORK/err" )" || MAIN_RC=$?
}

E2E_CONF="$WORK/etc"; E2E_DATA="$WORK/lib"; E2E_BIN="$WORK/bin/didban-agent"
CONF_DIR="$E2E_CONF"; DATA_DIR="$E2E_DATA"; UNIT_FILE="$WORK/agent.service"; BIN_DEST="$E2E_BIN"
mkdir -p "$(dirname "$E2E_BIN")"   # mirrors the real /usr/local/bin always existing

# t6: local binary → no download at all
mkdir -p "$WORK/localrun"
cp "$WORK/fx/payload/didban-agent" "$WORK/localrun/didban-agent"; chmod +x "$WORK/localrun/didban-agent"
: > "$STUB_LOG"
( cd "$WORK/localrun" && MAIN_RC=0; MAIN_OUT="$( (main) 2>"$WORK/err" )" || MAIN_RC=$?; \
  echo "$MAIN_RC" > "$WORK/rc6"; echo "$MAIN_OUT" > "$WORK/out6" )
if [[ "$(cat "$WORK/rc6")" == 0 ]] && ! grep -q "^curl" "$STUB_LOG" && grep -q "Using local binary" "$WORK/out6" && [[ -x "$E2E_BIN" ]]; then
  ok "local binary: installed without any download"
else
  bad "local binary path (rc=$(cat "$WORK/rc6")): $(cat "$WORK/out6")"
fi

# t7: download + valid checksum → verified, installed
: > "$STUB_LOG"
run_main
if [[ $MAIN_RC == 0 ]] && grep -q "SHA-256 verified" <<< "$MAIN_OUT" && grep -q "^curl" "$STUB_LOG" && [[ -x "$E2E_BIN" ]]; then
  ok "download: checksum verified before install"
else
  bad "download happy path (rc=$MAIN_RC): $MAIN_OUT"
fi

# t8: download + tampered checksum → abort, NOTHING installed
rm -f "$E2E_BIN"; : > "$STUB_LOG"
STUB_SHA_TAMPER=1 run_main
unset STUB_SHA_TAMPER
if [[ $MAIN_RC != 0 ]] && grep -q "SHA-256 mismatch" "$WORK/err" && ! grep -q "^install" "$STUB_LOG" && [[ ! -e "$E2E_BIN" ]]; then
  ok "tampered download: install aborted, nothing installed"
else
  bad "tampered download (rc=$MAIN_RC): $MAIN_OUT / $(cat "$WORK/err")"
fi

# t9: download OK but .sha256 asset missing → abort
rm -f "$E2E_BIN"; : > "$STUB_LOG"
STUB_SHA_FAIL=1 run_main
unset STUB_SHA_FAIL
if [[ $MAIN_RC != 0 ]] && grep -q "refusing to install an unverified binary" "$WORK/err" && ! grep -q "^install" "$STUB_LOG" && [[ ! -e "$E2E_BIN" ]]; then
  ok "missing checksum asset: install aborted"
else
  bad "missing checksum (rc=$MAIN_RC): $MAIN_OUT / $(cat "$WORK/err")"
fi

# ── summary ──────────────────────────────────────────────────────────────────
echo ""
echo "install_test: $PASS passed, $FAIL failed"
[[ $FAIL == 0 ]]
