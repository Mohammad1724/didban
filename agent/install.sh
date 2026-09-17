#!/usr/bin/env bash
#
# Didban agent installer — دیدبان
# Installs the didban-agent binary, generates a token, sets up systemd.
#
# Usage:
#   sudo bash install.sh
#
# If your agent binaries live somewhere other than GitHub Releases,
# build them locally (cd agent && go build) and place the binary next
# to this script — the installer will use it instead of downloading.
#
# Downloaded binaries are ALWAYS verified against the .sha256 checksum
# published with the same GitHub release before anything is installed
# (H13). A missing or mismatching checksum aborts the install.
#
set -euo pipefail

# ── Configuration ────────────────────────────────────────────────────────────
REPO="${DIDBAN_REPO:-https://github.com/Mohammad1724/didban}"
PORT="${DIDBAN_PORT:-8686}"
CONF_DIR="/etc/didban"
DATA_DIR="/var/lib/didban"
UNIT_FILE="/etc/systemd/system/didban-agent.service"
# Install destination (overridable for tests / custom layouts).
BIN_DEST="${DIDBAN_BIN_DEST:-/usr/local/bin/didban-agent}"

TG_TOKEN="${DIDBAN_TG_TOKEN:-}"
TG_CHAT_ID="${DIDBAN_TG_CHAT_ID:-}"
TG_PROXY="${DIDBAN_TG_PROXY:-}"

die() {
  echo "Error: $*" >&2
  exit 1
}

# ── Checksum verification (H13) ──────────────────────────────────────────────
# sha256_of FILE — print the SHA-256 hex digest of FILE using whatever tool
# the target system provides.
sha256_of() {
  local f="$1"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$f" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$f" | awk '{print $1}'
  elif command -v openssl >/dev/null 2>&1; then
    openssl dgst -sha256 -r "$f" | awk '{print $1}'
  else
    die "no SHA-256 tool available (need one of: sha256sum, shasum, openssl)"
  fi
}

# verify_release TARBALL SHA256_FILE — enforce the checksum published with the
# release. SHA256_FILE is the release's .sha256 asset, format "<hex>  <name>".
# Refuses to continue on any discrepancy; prints the verified digest on
# success. This runs BEFORE extraction, so a tampered download is never even
# unpacked, let alone installed.
verify_release() {
  local tarball="$1" sha_file="$2"
  local expected actual name base
  [[ -s "$sha_file" ]] || die "checksum file is empty"
  expected="$(awk 'NR==1 {print $1; exit}' "$sha_file" | tr '[:upper:]' '[:lower:]')"
  name="$(awk 'NR==1 {print $2; exit}' "$sha_file")"
  base="$(basename "$tarball")"
  [[ "$expected" =~ ^[0-9a-f]{64}$ ]] \
    || die "checksum file is malformed (expected a 64-hex-digit SHA-256)"
  [[ "$name" == "$base" ]] \
    || die "checksum file is for '$name' but the download is '$base' — refusing to install"
  actual="$(sha256_of "$tarball")"
  if [[ "$actual" != "$expected" ]]; then
    die "SHA-256 mismatch — the download was rejected and NOTHING was installed.
  expected: $expected
  actual:   $actual
  The file may be corrupted or tampered with. Re-run the installer, or build
  from source:  cd agent && go build -o didban-agent ."
  fi
  echo ">> SHA-256 verified: $actual"
}

require_root() {
  if [[ $EUID -ne 0 ]]; then
    echo "Error: run as root:  sudo bash install.sh" >&2
    exit 1
  fi
}

# Wait (bounded) for the agent's startup banner — specifically the
# "Cert SHA256:" line — to reach the journal. On slow hosts the agent may
# take a few seconds after `systemctl enable --now`, and a fixed `sleep 2`
# raced that and printed a deep link with an EMPTY fingerprint, which made
# the mobile app save the server without TLS pinning (H14). Bounded retry:
# up to $DIDBAN_FP_RETRIES tries, $DIDBAN_FP_DELAY seconds apart (0/1 are
# valid for tests).
wait_for_fingerprint() {
  local fp="" i
  for i in $(seq 1 "${DIDBAN_FP_RETRIES:-15}"); do
    fp="$(journalctl -u didban-agent --no-pager 2>/dev/null | grep -o 'Cert SHA256:  [a-f0-9]*' | head -1 | awk '{print $3}' || true)"
    if [[ -n "$fp" ]]; then
      echo "$fp"
      return 0
    fi
    sleep "${DIDBAN_FP_DELAY:-1}"
  done
  return 1
}

main() {
# ── Preflight ────────────────────────────────────────────────────────────────
  require_root

  ARCH="$(uname -m)"
  case "$ARCH" in
    x86_64)         GOARCH="amd64" ;;
    aarch64|arm64)  GOARCH="arm64" ;;
    armv7l|armv6l)  GOARCH="arm" ;;
    i686|i386)      GOARCH="386" ;;
    *) echo "Unsupported architecture: $ARCH" >&2; exit 1 ;;
  esac
  echo ">> Architecture: $ARCH (linux/$GOARCH)"

# ── Obtain the binary ────────────────────────────────────────────────────────
  local BIN=""
  if [[ -x "./didban-agent" ]]; then
    BIN="./didban-agent"   # local build next to this script
    echo ">> Using local binary: $BIN"
  else
    local AGENT_TARBALL="didban-agent-linux-$GOARCH.tar.gz"
    local URL="$REPO/releases/latest/download/$AGENT_TARBALL"
    echo ">> Downloading: $URL"
    local TMP
    TMP="$(mktemp -d)"
    if curl -fsSL "$URL" -o "$TMP/$AGENT_TARBALL" 2>/dev/null; then
      # Verify against the checksum published with the SAME release before
      # extracting anything (H13). Missing checksum = abort: a root binary
      # is never installed without its signature of authenticity.
      if ! curl -fsSL "$URL.sha256" -o "$TMP/$AGENT_TARBALL.sha256" 2>/dev/null; then
        die "could not download the published checksum ($URL.sha256) — refusing to install an unverified binary.
  Check that the release has a .sha256 asset, or use a local binary / build
  from source:  cd agent && go build -o didban-agent ."
      fi
      verify_release "$TMP/$AGENT_TARBALL" "$TMP/$AGENT_TARBALL.sha256"
      tar -xzf "$TMP/$AGENT_TARBALL" -C "$TMP"
      BIN="$TMP/didban-agent"
    else
      # Fallback: if Go is installed on this machine, build directly from repository
      if command -v go >/dev/null 2>&1; then
        echo ">> Download failed, attempting to build from source using local Go toolchain..."
        git clone "$REPO.git" "$TMP/repo" 2>/dev/null || true
        if [[ -d "$TMP/repo/agent" ]]; then
          (cd "$TMP/repo/agent" && go build -o "$TMP/didban-agent" .)
          BIN="$TMP/didban-agent"
        fi
      fi

      if [[ -z "$BIN" || ! -x "$BIN" ]]; then
        echo "Error: binary download failed. Edit the REPO variable at the top of this" >&2
        echo "script (set it to your GitHub repository URL), or build locally:" >&2
        echo "  cd agent && go build -o didban-agent ." >&2
        exit 1
      fi
    fi
  fi

  install -m 0755 "$BIN" "$BIN_DEST"
  "$BIN_DEST" -version

# ── Configuration ────────────────────────────────────────────────────────────
  mkdir -p "$CONF_DIR" "$DATA_DIR"
  chmod 0700 "$DATA_DIR"

  local TOKEN=""
  if [[ -f "$CONF_DIR/token" ]]; then
    TOKEN="$(cat "$CONF_DIR/token")"
  fi
  if [[ -z "$TOKEN" ]]; then
    TOKEN="$(head -c 24 /dev/urandom | xxd -ps 2>/dev/null || head -c 24 /dev/urandom | od -An -tx1 | tr -d ' \n')"
    echo -n "$TOKEN" > "$CONF_DIR/token"
    chmod 0600 "$CONF_DIR/token"
  fi
  local ADMIN_TOKEN=""
  if [[ -f "$CONF_DIR/admin-token" ]]; then
    ADMIN_TOKEN="$(cat "$CONF_DIR/admin-token")"
  fi
  if [[ -z "$ADMIN_TOKEN" ]]; then
    ADMIN_TOKEN="$(head -c 32 /dev/urandom | xxd -ps 2>/dev/null || head -c 32 /dev/urandom | od -An -tx1 | tr -d ' \n')"
    echo -n "$ADMIN_TOKEN" > "$CONF_DIR/admin-token"
    chmod 0600 "$CONF_DIR/admin-token"
  fi

# On upgrade, the operator's existing agent.conf (custom thresholds, alert
# settings, deploy mode, ...) is preserved; it is only generated on first install.
  if [[ -f "$CONF_DIR/agent.conf" ]]; then
    echo ">> Existing $CONF_DIR/agent.conf preserved (upgrade). Edit it to change settings."
  else
cat > "$CONF_DIR/agent.conf" <<EOF
# Didban agent configuration (read by systemd)
DIDBAN_TOKEN=$TOKEN
DIDBAN_ADMIN_TOKEN=$ADMIN_TOKEN
DIDBAN_ADDR=:$PORT
DIDBAN_DATA=$DATA_DIR
# Secure default: authenticated API requests cannot execute shell scripts.
# Set to scripts only when zero-touch installers are explicitly required.
DIDBAN_DEPLOY_MODE=config-only
# Uncomment to disable TLS (NOT recommended):
# DIDBAN_PLAIN=1
# Detailed status page is authenticated by default. Explicit public opt-in:
# DIDBAN_PUBLIC_STATUS=1

# ── Spike thresholds (percent) ──────────────────────────────────
# DIDBAN_CPU_TH=70
# DIDBAN_MEM_TH=90
# DIDBAN_STEAL_TH=10
# DIDBAN_DISK_TH=90

# ── Optional: Telegram alerts (instant alerts on spikes) ────────
${TG_TOKEN:+DIDBAN_TG_TOKEN=$TG_TOKEN}
${TG_CHAT_ID:+DIDBAN_TG_CHAT_ID=$TG_CHAT_ID}
${TG_PROXY:+DIDBAN_TG_PROXY=$TG_PROXY}
# Private network destinations remain blocked unless the operator explicitly
# enables them here. Probe targets must additionally set allow_private=true.
# DIDBAN_ALLOW_PRIVATE_PROBES=0
# DIDBAN_ALLOW_PRIVATE_WEBHOOKS=0
# DIDBAN_TG_TOKEN=123456789:ABCdefGHIjklMNOpqrsTUVwxyz
# DIDBAN_TG_CHAT_ID=-100123456789
# DIDBAN_TG_PROXY=socks5://127.0.0.1:1080

# ── Optional: process watchlist (alert when a process dies) ──────
# DIDBAN_WATCH=xray,pg-node-service
EOF
  fi
  chmod 0600 "$CONF_DIR/agent.conf"

# ── systemd service ──────────────────────────────────────────────────────────
# Sandbox design (H2): the agent is a *root control plane*, not a read-only
# metrics collector. At runtime it:
#   - writes tunnel configs to /etc/didban/tunnels and meta/data/certs to
#     /var/lib/didban,
#   - talks to /var/run/docker.sock (Docker) and the systemd bus (/run/dbus),
#   - runs systemctl / journalctl for tunnel services,
#   - explicit deploy mode "scripts" runs operator-approved install scripts.
# The old unit used ProtectSystem=strict (entire FS read-only except
# /dev,/proc,/sys) which made /etc read-only and silently broke tunnel config
# deployment. "full" keeps the OS image (/usr, /boot, /efi) immutable while
# leaving /etc, /var, /run writable — exactly this role's requirement.
  cat > "$UNIT_FILE" <<'EOF'
[Unit]
Description=Didban monitoring agent — دیدبان
Documentation=https://github.com/Mohammad1724/didban
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
EnvironmentFile=/etc/didban/agent.conf
ExecStart=/usr/local/bin/didban-agent
Restart=always
RestartSec=5

# OS image immutable (/usr, /boot, /efi); /etc, /var, /run writable — the
# agent deploys configs, stores data, and uses docker.sock + the systemd bus.
ProtectSystem=full
ProtectHome=true
PrivateTmp=true

# Hardening that cannot conflict with the functions above:
NoNewPrivileges=true
UMask=0077
LockPersonality=true
RestrictRealtime=true
SystemCallArchitectures=native
ProtectKernelTunables=true
ProtectKernelModules=true
ProtectKernelLogs=true
ProtectClock=true
ProtectHostname=true
RestrictSUIDSGID=true

# Deliberately NOT set — each would break a legitimate agent function:
#   ProtectSystem=strict          tunnel config deploy writes under /etc
#   ProtectControlGroups=true     service management via systemctl
#   PrivateNetwork / RestrictAddressFamilies
#                                 listens on :8686; needs AF_UNIX
#                                 (docker.sock, dbus) + internet (alerts)
#   MemoryDenyWriteExecute=true   install scripts may need exec mappings
#
# config-only is the default. A stricter host-specific unit may additionally
# be tested with the following settings (paths vary by deployment):
#   ProtectSystem=strict
#   ReadWritePaths=/var/lib/didban /etc/didban /run/docker.sock /run/dbus

[Install]
WantedBy=multi-user.target
EOF

  systemctl daemon-reload
  systemctl enable --now didban-agent
  sleep 2

# ── Summary ──────────────────────────────────────────────────────────────────
  local SERVER_IP FINGERPRINT
  SERVER_IP="$(hostname -I 2>/dev/null | awk '{print $1}')"
  # H14: bounded wait for the cert line instead of a racing fixed sleep —
  # an empty fingerprint here is what made the app save the server unpinned.
  FINGERPRINT="$(wait_for_fingerprint || true)"

  echo ""
  echo "══════════════════════════════════════════════════════════"
  echo "  Didban agent installed successfully! — نصب موفق"
  echo "══════════════════════════════════════════════════════════"
  echo "  URL:          https://${SERVER_IP}:${PORT}"
  echo "  Read token:   ${TOKEN}"
  echo "  Admin token:  ${ADMIN_TOKEN}"
  if [[ -n "$FINGERPRINT" ]]; then
    echo "  Cert SHA256:  ${FINGERPRINT}"
  fi
  echo ""
  echo "  📲 One-Click Mobile Import Link (کپی این خط برای اتصال فوری در اپ):"
  echo "  didban://${SERVER_IP}:${PORT}?token=${TOKEN}&admin_token=${ADMIN_TOKEN}&fp=${FINGERPRINT}&name=${SERVER_IP}"
  echo ""
  echo "  Save these — you will enter them in the Didban Android app."
  echo "  این اطلاعات را در اپ اندروید دیدبان وارد کنید."
  echo "══════════════════════════════════════════════════════════"
  echo ""
  echo "  Test:    curl -k https://${SERVER_IP}:${PORT}/api/metrics -H \"Authorization: Bearer ${TOKEN}\""
  echo "  Logs:    journalctl -u didban-agent -f"
  echo "  Stop:    systemctl stop didban-agent"
  echo "  Remove:  systemctl disable --now didban-agent && rm -f $BIN_DEST $UNIT_FILE && rm -rf $CONF_DIR $DATA_DIR"
}

# Run main only when executed (not when sourced by install_test.sh).
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  main "$@"
fi
