#!/usr/bin/env bash
#
# Tests for install.sh (H13): release checksum verification + install wiring.
# Sources install.sh (its BASH_SOURCE guard keeps main() from auto-running),
# then exercises verify_release() with real files and main() end-to-end with
# stubbed curl/install/systemctl/journalctl/hostname/ip — no root required.
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
  local a
  # Public-IP echo probes (detect_server_ip): answer from STUB_IPIFY instead
  # of the download logic below. Logged distinctly so the "no download"
  # assertion on local-binary installs keeps passing.
  for a in "$@"; do
    if [[ "$a" == *api.ipify.org* || "$a" == *checkip.amazonaws.com* ]]; then
      echo "ipify-probe $a" >> "$STUB_LOG"
      if [[ "${STUB_IPIFY:-203.0.113.9}" == "FAIL" ]]; then return 22; fi
      printf '%s' "${STUB_IPIFY:-203.0.113.9}"
      return 0
    fi
  done
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
hostname()   { echo "${STUB_HOSTNAME_IPS:-10.9.9.9}"; }
ip() {
  if [[ "${1:-}" == "route" ]]; then
    if [[ "${STUB_IPROUTE_FAIL:-0}" == 1 ]]; then return 1; fi
    echo "1.1.1.1 via 10.9.9.1 dev eth0 src ${STUB_IPROUTE_SRC:-10.9.9.9} uid 0"
    return 0
  fi
  return 1
}
require_root() { :; }

run_main() {  # sets MAIN_OUT / MAIN_RC
  MAIN_RC=0
  MAIN_OUT="$( (main) 2>"$WORK/err" )" || MAIN_RC=$?
}

E2E_CONF="$WORK/etc"; E2E_DATA="$WORK/lib"; E2E_BIN="$WORK/bin/didban-agent"
CONF_DIR="$E2E_CONF"; DATA_DIR="$E2E_DATA"; UNIT_FILE="$WORK/agent.service"; BIN_DEST="$E2E_BIN"
mkdir -p "$(dirname "$E2E_BIN")"   # mirrors the real /usr/local/bin always existing
# Keep e2e runs fast: the stubbed journalctl never emits a cert line, so the
# H14 fingerprint wait must not burn its real 15s default in every run.
export DIDBAN_FP_RETRIES=2 DIDBAN_FP_DELAY=0

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

# Tokens must be single-line hex even on hosts with xxd (stock xxd -ps
# wraps every 60 chars, which used to split the 64-char admin token and
# break agent.conf plus the import link).
if [[ "$(grep -c '' "$E2E_CONF/token")" == 1 ]] && grep -q -E '^[0-9a-f]{48}$' "$E2E_CONF/token" \
  && [[ "$(grep -c '' "$E2E_CONF/admin-token")" == 1 ]] && grep -q -E '^[0-9a-f]{64}$' "$E2E_CONF/admin-token"; then
  ok "generated tokens are single-line hex"
else
  bad "token format wrong"
fi

# Token generation stays one line even if the hex tool wraps its output.
xxd() { od -An -tx1 | tr -d ' \n' | fold -w 60; }
if [[ "$(gen_hex_token 32)" =~ ^[0-9a-f]{64}$ ]]; then
  ok "token generation survives wrapping hex output"
else
  bad "token generation broke on wrapped output"
fi
unset -f xxd

# Upgrade must keep credentials/config/data and actually restart the service.
printf 'custom-setting=keep\n' >> "$E2E_CONF/agent.conf"
printf 'existing certificate fixture\n' > "$E2E_DATA/cert.pem"
BEFORE_CONF="$(sha256_of "$E2E_CONF/agent.conf")"
BEFORE_READ="$(sha256_of "$E2E_CONF/token")"
BEFORE_ADMIN="$(sha256_of "$E2E_CONF/admin-token")"
: > "$STUB_LOG"
run_main
if [[ $MAIN_RC == 0 ]] && grep -q '^systemctl restart didban-agent$' "$STUB_LOG" && grep -q '^systemctl is-active --quiet didban-agent$' "$STUB_LOG"; then
  ok "upgrade restarts the agent and checks active state"
else
  bad "upgrade did not restart/check the service"
fi
if [[ "$BEFORE_CONF" == "$(sha256_of "$E2E_CONF/agent.conf")" && "$BEFORE_READ" == "$(sha256_of "$E2E_CONF/token")" && "$BEFORE_ADMIN" == "$(sha256_of "$E2E_CONF/admin-token")" ]] && grep -q 'existing certificate fixture' "$E2E_DATA/cert.pem"; then
  ok "upgrade preserves config, credentials and certificate data"
else
  bad "upgrade changed existing configuration or secrets"
fi

# Broken executable must not replace the current binary.
BEFORE_BIN="$(sha256_of "$E2E_BIN")"
printf '#!/bin/sh\nexit 1\n' > "$WORK/broken-agent"
chmod +x "$WORK/broken-agent"
if (install_binary "$WORK/broken-agent" > /dev/null 2>&1); then
  bad "broken agent was accepted"
elif [[ "$BEFORE_BIN" == "$(sha256_of "$E2E_BIN")" ]]; then
  ok "failed executable validation preserves the previous binary"
else
  bad "failed validation replaced the current binary"
fi

# ── H14: wait_for_fingerprint (bounded, no racing fixed sleep) ──────────────
echo "── wait_for_fingerprint (H14) ──"
export DIDBAN_FP_DELAY=0   # fast for tests; real default is 1s

FP_EXPECT="abc123abc123abc123abc123abc123abc123abc123abc123abc123abc123"
FP_CALLS="$WORK/fp_calls"; : > "$FP_CALLS"

# Stub that emits the cert line only on the 3rd journalctl call (simulates a
# slow agent whose startup banner lags the installer).
journalctl() {
  echo x >> "$FP_CALLS"
  local n
  n="$(wc -l < "$FP_CALLS")"
  if [[ "$n" -ge 3 ]]; then
    echo "Cert SHA256:  ${FP_EXPECT}"
  fi
}

# t10: fingerprint arrives on the 3rd try → picked up, rc 0
DIDBAN_FP_RETRIES=5
if FP_GOT="$(wait_for_fingerprint)" && [[ "$FP_GOT" == "$FP_EXPECT" ]]; then
  ok "fingerprint picked up after delayed banner (no race)"
else
  bad "delayed fingerprint missed (got: '$FP_GOT')"
fi

# t11: fingerprint never arrives → bounded failure, rc 1, no hang
journalctl() { :; }   # empty forever
DIDBAN_FP_RETRIES=4
if FP_GOT="$(wait_for_fingerprint)" ; then
  bad "wait_for_fingerprint should fail when the banner never appears"
else
  if [[ -z "$FP_GOT" ]]; then ok "no banner → bounded failure (rc!=0, empty)"; else bad "expected empty on failure, got '$FP_GOT'"; fi
fi

# ── detect_server_ip (public-first, no stale first-entry) ─────────────
echo "── detect_server_ip ──"

if is_ipv4 "87.107.81.151" && ! is_ipv4 "abc" && ! is_ipv4 "1.2.3" && ! is_ipv4 "1.2.3.4.5" && ! is_ipv4 "" && ! is_ipv4 "2001:db8::1"; then
  ok "is_ipv4 accepts dotted IPv4 and rejects garbage/IPv6"
else
  bad "is_ipv4 validation wrong"
fi

# public echo wins over local addresses
STUB_IPIFY="87.107.81.151"; STUB_IPROUTE_SRC="10.9.9.9"; STUB_HOSTNAME_IPS="10.9.9.9 172.17.0.1"
if [[ "$(detect_server_ip)" == "87.107.81.151" ]]; then
  ok "public echo IP preferred over local addresses"
else
  bad "public IP not preferred (got: '$(detect_server_ip)')"
fi
unset STUB_IPIFY STUB_IPROUTE_SRC STUB_HOSTNAME_IPS

# echo service down -> primary outbound interface address
STUB_IPIFY="FAIL"; STUB_IPROUTE_SRC="10.1.2.3"; STUB_HOSTNAME_IPS="10.9.9.9"
if [[ "$(detect_server_ip)" == "10.1.2.3" ]]; then
  ok "echo failure falls back to the outbound interface address"
else
  bad "route fallback wrong (got: '$(detect_server_ip)')"
fi
unset STUB_IPIFY STUB_IPROUTE_SRC STUB_HOSTNAME_IPS

# echo + route down -> first PUBLIC local address (skips stale/private first entries)
STUB_IPIFY="FAIL"; STUB_IPROUTE_FAIL=1; STUB_HOSTNAME_IPS="172.17.0.1 10.9.9.9 87.107.81.151"
if [[ "$(detect_server_ip)" == "87.107.81.151" ]]; then
  ok "local fallback skips private/stale entries for the public one"
else
  bad "public-preferring fallback wrong (got: '$(detect_server_ip)')"
fi
unset STUB_IPIFY STUB_IPROUTE_FAIL STUB_HOSTNAME_IPS

# only private addresses -> first valid one (old behavior, minus IPv6)
STUB_IPIFY="FAIL"; STUB_IPROUTE_FAIL=1; STUB_HOSTNAME_IPS="172.17.0.1 10.9.9.9"
if [[ "$(detect_server_ip)" == "172.17.0.1" ]]; then
  ok "private-only hosts keep the first address"
else
  bad "private-only fallback wrong (got: '$(detect_server_ip)')"
fi
unset STUB_IPIFY STUB_IPROUTE_FAIL STUB_HOSTNAME_IPS

# IPv6 entries are skipped everywhere
STUB_IPIFY="FAIL"; STUB_IPROUTE_FAIL=1; STUB_HOSTNAME_IPS="2001:db8::1 10.9.9.9"
if [[ "$(detect_server_ip)" == "10.9.9.9" ]]; then
  ok "IPv6 entries never selected"
else
  bad "IPv6 leaked into detection (got: '$(detect_server_ip)')"
fi
unset STUB_IPIFY STUB_IPROUTE_FAIL STUB_HOSTNAME_IPS

# garbage echo body -> treated as failure, route used
STUB_IPIFY="<html>blocked"; STUB_IPROUTE_SRC="10.1.2.3"
if [[ "$(detect_server_ip)" == "10.1.2.3" ]]; then
  ok "non-IP echo body falls back instead of poisoning the URL"
else
  bad "garbage echo body leaked (got: '$(detect_server_ip)')"
fi
unset STUB_IPIFY STUB_IPROUTE_SRC

# end-to-end: summary URL and import link carry the detected public IP
FP64="0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
journalctl() { echo "Cert SHA256:  $FP64"; }
DIDBAN_FP_RETRIES=2
STUB_IPIFY="87.107.81.151"
run_main
unset STUB_IPIFY
if [[ $MAIN_RC == 0 ]] && grep -q "https://87.107.81.151:" <<< "$MAIN_OUT" && grep -q "didban://87.107.81.151:.*fp=$FP64" <<< "$MAIN_OUT"; then
  ok "installer summary and import link use the detected public IP"
else
  bad "summary IP wiring (rc=$MAIN_RC): $MAIN_OUT"
fi

# ── summary ──────────────────────────────────────────────────────────────────
echo ""
echo "install_test: $PASS passed, $FAIL failed"
[[ $FAIL == 0 ]]
