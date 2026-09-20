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

write_secret_atomic() {
  local path="$1" value="$2" dir tmp
  dir="$(dirname "$path")"
  [[ ! -L "$dir" ]] || die "refusing symlinked credential directory: $dir"
  [[ ! -L "$path" ]] || die "refusing symlinked credential file: $path"
  tmp="$(mktemp "$dir/.didban-secret.XXXXXX")"
  chmod 0600 "$tmp"
  printf '%s' "$value" > "$tmp"
  mv -f "$tmp" "$path"
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

# gen_hex_token BYTES — print BYTES of randomness as one lowercase hex line.
# The outer whitespace strip keeps this single-line no matter how the hex
# tool wraps its output (stock xxd -ps breaks every 60 chars, which used to
# split the 64-char admin token across two lines and break agent.conf plus
# the printed import link).
gen_hex_token() {
  {
    head -c "$1" /dev/urandom | xxd -ps -c 256 2>/dev/null \
      || head -c "$1" /dev/urandom | od -An -tx1 | tr -d ' \n'
  } | tr -d '[:space:]'
}

# is_ipv4 ADDR — syntactic IPv4 check (dotted digits only).
is_ipv4() { [[ "${1:-}" =~ ^[0-9]{1,3}(\.[0-9]{1,3}){3}$ ]]; }

# is_private_ipv4 ADDR — true for RFC1918, loopback and link-local ranges.
is_private_ipv4() {
  local ip="${1:-}"
  is_ipv4 "$ip" || return 1
  case "$ip" in
    10.*|192.168.*|127.*|169.254.*) return 0 ;;
    172.1[6-9].*|172.2[0-9].*|172.3[01].*) return 0 ;;
    *) return 1 ;;
  esac
}

# pick_local_ipv4 CANDIDATES — choose one address out of a space-separated
# list: the first public IPv4 wins (a phone rarely reaches private or Docker
# addresses); otherwise the first syntactically valid IPv4; otherwise nothing.
# Always succeeds.
pick_local_ipv4() {
  local candidates="${1:-}" cand first=""
  # shellcheck disable=SC2086 — word-splitting the list is intended.
  for cand in $candidates; do
    is_ipv4 "$cand" || continue
    if [[ -z "$first" ]]; then first="$cand"; fi
    if ! is_private_ipv4 "$cand"; then echo "$cand"; return 0; fi
  done
  if [[ -n "$first" ]]; then echo "$first"; fi
  return 0
}

# detect_server_ip — print the IPv4 the phone app should use for this host.
#
# `hostname -I` order is meaningless: its first address is often a private
# or Docker address, or a stale address that is still configured after the
# operator renumbered the server. On NAT/cloud hosts no local address
# equals the public address at all. So:
#   1. ask an external echo service for the public IPv4 (what the phone
#      actually reaches, including through NAT) — strictly validated and
#      time-bounded so a filtered network only costs a few seconds;
#   2. else the primary outbound interface address (`ip route get`);
#   3. else the local list with public addresses preferred (the old code
#      picked the first entry blindly, private or stale).
# Prints nothing (but still succeeds) when no IPv4 exists at all.
detect_server_ip() {
  local ip="" svc
  if command -v curl >/dev/null 2>&1; then
    for svc in "https://api.ipify.org" "https://checkip.amazonaws.com"; do
      ip="$(curl -fsSL --max-time 4 "$svc" 2>/dev/null | tr -d '[:space:]' || true)"
      if is_ipv4 "$ip"; then echo "$ip"; return 0; fi
      ip=""
    done
  fi
  if command -v ip >/dev/null 2>&1; then
    ip="$(ip route get 1.1.1.1 2>/dev/null | awk '/ src /{for(i=1;i<=NF;i++) if ($i=="src") {print $(i+1); exit}}' || true)"
    if is_ipv4 "$ip"; then echo "$ip"; return 0; fi
  fi
  pick_local_ipv4 "$(hostname -I 2>/dev/null || true)"
  return 0
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

# Replace the inode atomically; never overwrite an executable that is running.
install_binary() {
  local source="$1" staged
  [[ ! -L "$BIN_DEST" ]] || die "refusing symlinked agent binary: $BIN_DEST"
  staged="$(mktemp "$(dirname "$BIN_DEST")/.didban-agent.XXXXXX")"
  if ! install -m 0755 "$source" "$staged" || ! "$staged" -version; then
    rm -f "$staged"
    die "new agent binary failed validation; current binary was preserved"
  fi
  mv -f "$staged" "$BIN_DEST"
}

activate_agent() {
  systemctl daemon-reload
  systemctl enable didban-agent
  # enable --now does NOT restart an already-running old agent after upgrade.
  systemctl restart didban-agent
  systemctl is-active --quiet didban-agent
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

  install_binary "$BIN"

# ── Configuration ────────────────────────────────────────────────────────────
  mkdir -p "$CONF_DIR" "$DATA_DIR"
  chmod 0700 "$DATA_DIR"

  [[ ! -L "$CONF_DIR" ]] || die "refusing symlinked credential directory: $CONF_DIR"
  [[ ! -L "$CONF_DIR/token" ]] || die "refusing symlinked credential file: $CONF_DIR/token"
  [[ ! -L "$CONF_DIR/admin-token" ]] || die "refusing symlinked credential file: $CONF_DIR/admin-token"
  local TOKEN=""
  if [[ -f "$CONF_DIR/token" && "${DIDBAN_ROTATE_TOKENS:-0}" != "1" ]]; then
    TOKEN="$(cat "$CONF_DIR/token")"
  fi
  if [[ -z "$TOKEN" ]]; then
    TOKEN="$(gen_hex_token 24)"
    write_secret_atomic "$CONF_DIR/token" "$TOKEN"
  fi
  local ADMIN_TOKEN=""
  if [[ -f "$CONF_DIR/admin-token" && "${DIDBAN_ROTATE_TOKENS:-0}" != "1" ]]; then
    ADMIN_TOKEN="$(cat "$CONF_DIR/admin-token")"
  fi
  if [[ -z "$ADMIN_TOKEN" ]]; then
    ADMIN_TOKEN="$(gen_hex_token 32)"
    write_secret_atomic "$CONF_DIR/admin-token" "$ADMIN_TOKEN"
  fi

# On upgrade, the operator's existing agent.conf (custom thresholds, alert
# settings, deploy mode, ...) is preserved; it is only generated on first install.
  if [[ -L "$CONF_DIR/agent.conf" ]]; then
    echo "ERROR: refusing symlinked configuration: $CONF_DIR/agent.conf" >&2
    exit 1
  fi
  if [[ -f "$CONF_DIR/agent.conf" ]]; then
    if [[ "${DIDBAN_ROTATE_TOKENS:-0}" == "1" ]]; then
      local CONF_TMP="$CONF_DIR/.agent.conf.rotate.$$"
      awk -v read_token="$TOKEN" -v admin_token="$ADMIN_TOKEN" '
        BEGIN { read_seen=0; admin_seen=0 }
        /^DIDBAN_TOKEN=/ { print "DIDBAN_TOKEN=" read_token; read_seen=1; next }
        /^DIDBAN_ADMIN_TOKEN=/ { print "DIDBAN_ADMIN_TOKEN=" admin_token; admin_seen=1; next }
        { print }
        END {
          if (!read_seen) print "DIDBAN_TOKEN=" read_token
          if (!admin_seen) print "DIDBAN_ADMIN_TOKEN=" admin_token
        }
      ' "$CONF_DIR/agent.conf" > "$CONF_TMP"
      chmod 0600 "$CONF_TMP"
      mv -f "$CONF_TMP" "$CONF_DIR/agent.conf"
      echo ">> Read/admin tokens rotated; update Android server credentials after restart."
    else
      echo ">> Existing $CONF_DIR/agent.conf preserved (upgrade). Edit it to change settings."
    fi
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
# Detailed status is authenticated. Explicit opt-in exposes only a minimal public signal:
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

  activate_agent

# ── Summary ──────────────────────────────────────────────────────────────────
  local SERVER_IP FINGERPRINT OTHER_IPS
  SERVER_IP="$(detect_server_ip || true)"
  # Other local IPv4s, so the operator can substitute one when the detected
  # address is not how their phone reaches this host (multi-homed, VPN...).
  OTHER_IPS="$(hostname -I 2>/dev/null | tr ' ' '\n' | grep -E '^[0-9]{1,3}(\.[0-9]{1,3}){3}$' | grep -v -x -F -e "$SERVER_IP" | tr '\n' ' ' | sed 's/ *$//' || true)"
  # H14: bounded wait for the cert line instead of a racing fixed sleep —
  # an empty fingerprint here is what made the app save the server unpinned.
  FINGERPRINT="$(wait_for_fingerprint || true)"
  # Journal access can be delayed or restricted even though the certificate is
  # already present. Derive the same leaf-certificate SHA-256 directly before
  # deciding whether a safe quick-connect code can be printed.
  if [[ -z "$FINGERPRINT" && -f "$DATA_DIR/cert.pem" ]] && command -v openssl >/dev/null 2>&1; then
    FINGERPRINT="$(openssl x509 -in "$DATA_DIR/cert.pem" -noout -fingerprint -sha256 2>/dev/null | sed -n 's/^[^=]*=//p' | tr -d ':' | tr 'A-F' 'a-f' || true)"
  fi

  echo ""
  echo "══════════════════════════════════════════════════════════"
  echo "  Didban agent installed successfully! — نصب موفق"
  echo "══════════════════════════════════════════════════════════"
  if [[ -z "$SERVER_IP" ]]; then
    echo "  WARNING: no IPv4 address detected on this host — put your server's"
    echo "  reachable IP into the URL and import link below."
  fi
  echo "  URL:          https://${SERVER_IP}:${PORT}"
  if [[ -n "$OTHER_IPS" ]]; then
    echo "  Also on this host: $OTHER_IPS"
    echo "  (use one of these above if that is how your phone reaches this server)"
  fi
  echo "  Read token:   ${TOKEN}"
  echo "  Admin token:  ${ADMIN_TOKEN}"
  if [[ -n "$FINGERPRINT" ]]; then
    echo "  Cert SHA256:  ${FINGERPRINT}"
  fi
  echo ""
  if [[ "$FINGERPRINT" =~ ^[a-f0-9]{64}$ ]]; then
    echo "  📲 One-Click Mobile Import Link (کپی این خط برای اتصال فوری در اپ):"
    echo "  didban://${SERVER_IP}:${PORT}?token=${TOKEN}&admin_token=${ADMIN_TOKEN}&fp=${FINGERPRINT}&name=${SERVER_IP}"
  else
    echo "  WARNING: certificate fingerprint unavailable; quick-connect code was not printed."
    echo "  Run: journalctl -u didban-agent --no-pager | grep 'Cert SHA256'"
  fi
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
