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
set -euo pipefail

# ── Configuration ────────────────────────────────────────────────────────────
REPO="${DIDBAN_REPO:-https://github.com/Mohammad1724/didban}"
PORT="${DIDBAN_PORT:-8686}"
CONF_DIR="/etc/didban"
DATA_DIR="/var/lib/didban"
UNIT_FILE="/etc/systemd/system/didban-agent.service"

TG_TOKEN="${DIDBAN_TG_TOKEN:-}"
TG_CHAT_ID="${DIDBAN_TG_CHAT_ID:-}"
TG_PROXY="${DIDBAN_TG_PROXY:-}"

# ── Preflight ────────────────────────────────────────────────────────────────
if [[ $EUID -ne 0 ]]; then
  echo "Error: run as root:  sudo bash install.sh" >&2
  exit 1
fi

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
BIN=""
if [[ -x "./didban-agent" ]]; then
  BIN="./didban-agent"   # local build next to this script
  echo ">> Using local binary: $BIN"
else
  URL="$REPO/releases/latest/download/didban-agent-linux-$GOARCH.tar.gz"
  echo ">> Downloading: $URL"
  TMP="$(mktemp -d)"
  if curl -fsSL "$URL" -o "$TMP/agent.tar.gz" 2>/dev/null; then
    tar -xzf "$TMP/agent.tar.gz" -C "$TMP"
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

install -m 0755 "$BIN" /usr/local/bin/didban-agent
/usr/local/bin/didban-agent -version

# ── Configuration ────────────────────────────────────────────────────────────
mkdir -p "$CONF_DIR" "$DATA_DIR"
chmod 0700 "$DATA_DIR"

TOKEN=""
if [[ -f "$CONF_DIR/token" ]]; then
  TOKEN="$(cat "$CONF_DIR/token")"
fi
if [[ -z "$TOKEN" ]]; then
  TOKEN="$(head -c 24 /dev/urandom | xxd -ps 2>/dev/null || head -c 24 /dev/urandom | od -An -tx1 | tr -d ' \n')"
  echo -n "$TOKEN" > "$CONF_DIR/token"
  chmod 0600 "$CONF_DIR/token"
fi

cat > "$CONF_DIR/agent.conf" <<EOF
# Didban agent configuration (read by systemd)
DIDBAN_TOKEN=$TOKEN
DIDBAN_ADDR=:$PORT
DIDBAN_DATA=$DATA_DIR
# Uncomment to disable TLS (NOT recommended):
# DIDBAN_PLAIN=1

# ── Spike thresholds (percent) ──────────────────────────────────
# DIDBAN_CPU_TH=70
# DIDBAN_MEM_TH=90
# DIDBAN_STEAL_TH=10
# DIDBAN_DISK_TH=90

# ── Optional: Telegram alerts (instant alerts on spikes) ────────
${TG_TOKEN:+DIDBAN_TG_TOKEN=$TG_TOKEN}
${TG_CHAT_ID:+DIDBAN_TG_CHAT_ID=$TG_CHAT_ID}
${TG_PROXY:+DIDBAN_TG_PROXY=$TG_PROXY}
# DIDBAN_TG_TOKEN=123456789:ABCdefGHIjklMNOpqrsTUVwxyz
# DIDBAN_TG_CHAT_ID=-100123456789
# DIDBAN_TG_PROXY=socks5://127.0.0.1:1080

# ── Optional: process watchlist (alert when a process dies) ──────
# DIDBAN_WATCH=xray,pg-node-service
EOF
chmod 0600 "$CONF_DIR/agent.conf"

# ── systemd service ──────────────────────────────────────────────────────────
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
NoNewPrivileges=true
ProtectSystem=strict
ReadWritePaths=/var/lib/didban
ProtectHome=true
PrivateTmp=true
ProtectKernelTunables=true
ProtectControlGroups=true
RestrictSUIDSGID=true

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable --now didban-agent
sleep 2

# ── Summary ──────────────────────────────────────────────────────────────────
SERVER_IP="$(hostname -I 2>/dev/null | awk '{print $1}')"
FINGERPRINT="$(journalctl -u didban-agent --no-pager 2>/dev/null | grep -o 'Cert SHA256:  [a-f0-9]*' | head -1 | awk '{print $3}')"

echo ""
echo "══════════════════════════════════════════════════════════"
echo "  Didban agent installed successfully! — نصب موفق"
echo "══════════════════════════════════════════════════════════"
echo "  URL:          https://${SERVER_IP}:${PORT}"
echo "  Token:        ${TOKEN}"
if [[ -n "$FINGERPRINT" ]]; then
  echo "  Cert SHA256:  ${FINGERPRINT}"
fi
echo ""
echo "  📲 One-Click Mobile Import Link (کپی این خط برای اتصال فوری در اپ):"
echo "  didban://${SERVER_IP}:${PORT}?token=${TOKEN}&fp=${FINGERPRINT}&name=${SERVER_IP}"
echo ""
echo "  Save these — you will enter them in the Didban Android app."
echo "  این اطلاعات را در اپ اندروید دیدبان وارد کنید."
echo "══════════════════════════════════════════════════════════"
echo ""
echo "  Test:    curl -k https://${SERVER_IP}:${PORT}/api/metrics -H \"Authorization: Bearer ${TOKEN}\""
echo "  Logs:    journalctl -u didban-agent -f"
echo "  Stop:    systemctl stop didban-agent"
echo "  Remove:  systemctl disable --now didban-agent && rm -f /usr/local/bin/didban-agent $UNIT_FILE && rm -rf $CONF_DIR $DATA_DIR"
