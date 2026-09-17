# Didban — دیدبان 👁️

**Your all-in-one Linux server monitoring & DevOps command center. Simple, featherweight, and it tells you *what* ate your CPU last night.**

🇮🇷 [فارسی](README-fa.md) | 🇬🇧 English

---

## Why Didban?

Every server admin knows the 2 AM question: **"CPU was at 100% last night — what did it?"** Didban answers it. A tiny Go agent runs on each server, records resource spikes **with the processes that caused them**, sends instant **Telegram/Discord/Webhook alerts**, provides an **embeddable public HTML status page**, monitors website **uptime with Uptime Kuma style heartbeat bars**, and packs a powerhouse **mobile DevOps toolkit** (Docker Manager, DPI Censorship Inspector, Cloudflare DNS, SSL inspector, Port scanner, GeoIP, encrypted vault, local web server, and developer tools).

- 🚀 **Dual-Node Tunnel Hub (Iran Node ➔ Foreign Node)** — manage, generate, and monitor anti-censorship reverse, raw-socket, ICMP ping, and IP spoofing tunnels between Iran and Foreign nodes with **Zero-Touch Automated Orchestration (Smite-panel style auto-deployment)**, full support for **Multi-Port Forwarding (e.g. 2096, 2097, 2098 or 443:8443, 80:8080)** and 10 powerful engines: **BackPack 🎒 (by AminMGMT)**, **Paqet (Raw Socket KCP by behzadea12)**, **Narnia (ICMP Ping Tunnel by Dnt3e)**, **Spoof Tunnel (Mutual IP Spoofing by ParsaKSH)**, **Backhaul**, **Rathole**, **GOST**, **Chisel**, **FRP**, and **IPTables**; supports Noise NNpsk0, PCK kernel bypass, KCP+FEC, WSS Chrome TLS, 1-click bash installers, systemd services, and Docker Compose configurations
- 📊 **Live server metrics** — CPU (incl. %steal!), RAM/swap, disk, network throughput, load, uptime
- 🕵️ **Spike forensics** — every CPU/memory spike is recorded server-side with the **top culprit processes** — even while your phone is off
- 🐳 **Docker Container Watcher & Remote Control** — monitor all Docker containers via native socket (`/var/run/docker.sock`, zero dependencies), detect crashed/unhealthy containers with instant alerts, and restart/stop containers on the go
- ⏱️ **Uptime & Heartbeat Engine (Uptime Kuma style)** — monitor HTTP(S), TCP ports (MySQL, Redis, Postgres), Ping, Keywords, and SSL with 30-bar heartbeats and incident downtime tracking
- 🛡️ **DPI Censorship & TLS Inspector** — diagnose TCP SYN drops, DPI TCP RST injections, and TLS Handshake SNI filtering/censorship
- 📄 **Authenticated HTML Status Page** — built-in responsive web status page at `/status` showing server health, uptime, disks, ports, and activity
- ✈️ **Multi-channel instant alerts** — get rich alerts with top culprit processes sent to **Telegram**, **Discord Webhooks**, and **Generic Webhooks**
- 👂 **Listening ports & active connections** — live inspection of server ports, sockets, connected remote IPs, matched to PIDs and process names
- 🛑 **Remote process killer** — search, inspect, and terminate (SIGTERM/SIGKILL) heavy or runaway processes straight from the app or API
- ☁️ **Cloudflare DNS manager** — manage zones and DNS records (A, AAAA, CNAME, TXT, MX), toggle proxy status, and adjust TTL
- 🌐 **Global Check-Host** — probe server reachability (Ping, HTTP, TCP, DNS) from 20+ nodes worldwide (Germany, USA, Iran, France, etc.)
- 🛰️ **Network diagnostics** — multi-threaded Port Scanner, SSL Inspector (expiry countdown, SANs, chain), IP & GeoIP lookup, and TCP Pinger
- 🔐 **Encrypted Vault & backup** — store confidential notes & credentials with AES-256-GCM encryption and export/import full backups
- 📡 **Local Web Server & QR Code** — share files and text across local Wi-Fi with instant QR code downloading
- 🛠️ **Developer Lab (String Lab)** — Base64, JSON formatter/minifier, Subnet/CIDR calculator, JWT decoder, Hashes, and UUID/Password generator
- 📱 **Android app** — bilingual (Persian/English), “Nightwatch” design language (dark & light themes, Inter + tabular telemetry numerals, animated radar mark), 1-click SSH install
- 🔐 **Secure by default** — HTTPS with self-signed certs + token auth; the app pins the certificate fingerprint (SSH-style trust-on-first-use)
- 🪶 **Featherweight** — single static Go binary (~8 MB), zero external dependencies, ~10 MB RAM, systemd-hardened

## Architecture

```
┌────────────────────────── Your phone ──────────────────────────┐
│  Didban Android App (All-in-One Command Center)                │
│  • Server Fleet Dashboard & 24h Charts                         │
│  • Docker Container Manager (Live status, Restart, Stop)       │
│  • Uptime Kuma Heartbeat Monitor (HTTP, TCP, Ping, SSL)        │
│  • DPI Censorship & TLS Handshake Inspector                    │
│  • Spike Log ("what ate the CPU?") + Process Killer            │
│  • Listening Ports & Active Socket Connections                 │
│  • Cloudflare DNS Manager (Zones, Records, Proxy)              │
│  • Network Hub (Port Scanner, SSL Inspector, GeoIP, Ping)      │
│  • Encrypted Vault & Backup (AES-256-GCM)                      │
│  • Developer Lab (Base64, JSON, CIDR, JWT, Generators)         │
└───────────────────────────────┬────────────────────────────────┘
                                │ HTTPS + Bearer token + cert pinning
┌───────────────────────────────▼────────────────────────────────┐
│  didban-agent (each server)                                    │
│  • /api/metrics  /api/processes  /api/network/sockets          │
│  • /api/docker/containers  /api/docker/restart  /api/docker/stop│
│  • /api/events   /api/history    /api/processes/kill           │
│  • Authenticated HTML Status Page (/status)                           │
│  • Multi-Channel Alert Dispatcher (Telegram/Discord/Webhook)   │
│  • records spikes & container events 24/7 to disk              │
└────────────────────────────────────────────────────────────────┘
```

## Install (one command per server)

Run on each server (Ubuntu/Debian, any arch — amd64/arm64/arm/386):

```bash
curl -fsSL https://raw.githubusercontent.com/Mohammad1724/didban/main/agent/install.sh -o didban-install.sh
sudo bash didban-install.sh
```

If you use a firewall, open the port:

```bash
sudo ufw allow 8686
```

The installer downloads the binary from the latest GitHub Release, generates a token, installs a hardened systemd service, and prints the **URL**, **token** and **certificate fingerprint** — save these three for the Android app.

Verify it works:

```bash
curl -sk https://YOUR_SERVER_IP:8686/api/metrics -H "Authorization: Bearer YOUR_TOKEN"
```

## Dual-Node Tunnel Hub (Iran-Foreign Relays & Reverse Tunnels)

Didban allows you to easily configure, deploy, and monitor dual-node tunnels between an Iran Bridge / Relay node and a Foreign Upstream / Server node using industry-standard high-performance tunneling cores:

1. **BackPack 🎒 (by AminMGMT)**: Cutting-edge Go reverse & direct tunnel engine with Noise NNpsk0 Stealth encryption, Linux conntrack bypass (PCK), gaming-grade UDP+KCP+FEC, real Chrome TLS fingerprinting (WSS Decoy), ICMP ping carrier (xDi), and Turbo/Gaming presets.
2. **Paqet (by behzadea12 / hanselime)**: High-throughput Raw Socket & KCP tunnel designed for bypassing DPI and stateful firewalls with customizable KCP tuning (Fast, Fast2, Fast3) and AES-GCM/ChaCha20 encryption.
3. **Narnia (by Dnt3e)**: Invisible Layer-3 tunnel encapsulated entirely within standard ICMP Ping packets with ChaCha20 encryption, dynamic MTU negotiation, and seamless virtual interface networking.
4. **Spoof Tunnel (by ParsaKSH & variants)**: Bidirectional Mutual IP Spoofing across Layer 3 and 4 with userspace Reliability Layer, Reed-Solomon Forward Error Correction (FEC), and cryptographic key authentication.
5. **Backhaul**: High-throughput multiplexed WebSocket & TCP reverse tunneling with multi-port forwarding and automatic reconnection.
6. **Rathole**: Ultra-lightweight Rust reverse proxy with Noise Protocol end-to-end encryption and minimal memory footprint (< 10 MB RAM).
7. **GOST (GO Simple Tunnel)**: Versatile multi-protocol forwarding engine with support for TLS, WebSocket, gRPC, TCPMux, and UDP.
8. **Chisel**: Fast TCP/UDP tunnel over HTTP/WebSocket secured via SSH, perfect for bypassing restrictive deep-packet inspection firewalls.
9. **FRP (Fast Reverse Proxy)**: Battle-tested multi-channel reverse proxy for exposing internal services.
10. **IPTables**: Direct in-kernel packet forwarding with zero user-space latency.

For every configured tunnel:
- **1-Click Installer Scripts**: Ready-to-paste one-line bash scripts for Iran and Foreign nodes.
- **Docker Compose & Config Files**: Ready-to-use `docker-compose.yml` and `toml`/`yaml` configuration definitions.
- **Live Latency & Health Probing**: Real-time round-trip latency (ms) and connectivity verification.

## Status Page

`/status` exposes hostname, disks, listening ports, process names, and recent events, so it requires the same Bearer token as the API by default. For an intentionally public deployment, set `DIDBAN_PUBLIC_STATUS=1` (or pass `--public-status`). Keep the default on Internet-facing agents.

## Alerts Configuration (Telegram, Discord, Webhooks)

Configure alerts in `/etc/didban/agent.conf` or via command-line flags:

```bash
# In /etc/didban/agent.conf
DIDBAN_TG_TOKEN="123456789:ABCdefGHIjklMNOpqrsTUVwxyz"
DIDBAN_TG_CHAT_ID="-100123456789"
# Optional Discord Webhook:
# DIDBAN_DISCORD_WEBHOOK="https://discord.com/api/webhooks/..."
# Optional Generic Webhook:
# DIDBAN_WEBHOOK_URL="https://mywebhook.site/endpoint"
# Optional SOCKS5 proxy for Telegram:
# DIDBAN_TG_PROXY="socks5://127.0.0.1:1080"
```

Then restart the service:
```bash
sudo systemctl restart didban-agent
```

Test alert dispatches:
```bash
curl -sk -X POST https://YOUR_SERVER_IP:8686/api/alerts/test -H "Authorization: Bearer YOUR_TOKEN"
```

## API

All `/api/*` endpoints require `Authorization: Bearer <token>`. The `?token=` query parameter is **no longer accepted** (it leaks into proxy logs and browser history). Requests are rate-limited per IP (10 req/s sustained, burst 20 — excess gets `429` with `Retry-After`), request bodies are capped at 2 MB, and an access log (IP, method, path, status, duration — never query strings, headers, or bodies) is written to the journal.

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/health` | liveness & alert status (no auth) |
| `GET` | `/status` | authenticated responsive HTML status board (public only with explicit opt-in) |
| `GET` | `/api/metrics` | CPU (usage/user/system/iowait/**steal**), memory, swap, disks, network rates, load, uptime |
| `GET` | `/api/docker/containers` | list all Docker containers, health, state, image, and exposed ports |
| `POST` | `/api/docker/restart` | restart a Docker container (`{"id": "container_id_or_name"}`) |
| `POST` | `/api/docker/stop` | stop a Docker container (`{"id": "container_id_or_name"}`) |
| `POST` | `/api/tunnel/apply` | automated zero-touch tunnel deployment & configuration |
| `POST` | `/api/tunnel/restart` | restart a managed tunnel service (`{"id": "..."}`) |
| `POST` | `/api/tunnel/stop` | stop a managed tunnel service (`{"id": "..."}`) |
| `POST` | `/api/tunnel/delete` | tear down tunnel service and remove configurations (`{"id": "..."}`) |
| `GET` | `/api/tunnel/status` | live service status, PID, uptime, and logs (`?id=...`) |
| `GET` | `/api/tunnel/list` | list all active tunnels running on this server |
| `GET` | `/api/processes` | top 25 processes by CPU (instant %, memory, user, cmd) |
| `GET` | `/api/network/sockets` | listening ports & active TCP/UDP sockets matched to PIDs and process names |
| `POST` | `/api/processes/kill` | terminate a runaway process safely (`{"pid": 1234, "signal": "SIGTERM"}`) |
| `POST` | `/api/alerts/test` | dispatch a test alert to Telegram, Discord, and Webhooks |
| `GET` | `/api/events?limit=50` | spike events & container crash events (newest first) with top culprit processes |
| `GET` | `/api/history?hours=24` | minute-resolution history for charts |

### Tunnel deployment security

Tunnel apply endpoints are hardened against command-injection and path-traversal:

- **`config_path` is sandboxed.** Config files can only be written inside the agent's tunnel configuration directory (default `/etc/didban/tunnels`, override with `--config-dir` / `DIDBAN_TUNNEL_CONFIG_DIR`). Absolute paths outside the sandbox, `..` traversal, and symlink escapes are rejected with HTTP `400`.
- **`id` and `service_name` are strictly validated** (`[A-Za-z0-9][A-Za-z0-9_-]{0,63}`). Path traversal via these fields is impossible.
- **Deploy mode.** `--deploy-mode scripts` (default) allows the authenticated app to run its install script. `--deploy-mode config-only` (or `DIDBAN_DEPLOY_MODE=config-only`) is a hardened mode where the agent **never executes shell commands** — it only writes sandboxed configs and manages the systemd unit name. Blocked scripts return `success:false` with a clear error.
- **Size caps.** Request bodies ≤ 2 MB, config content ≤ 1 MB, install script ≤ 64 KB.
- **Audit trail.** Every deploy carrying a script records `tunnel_deploy_start` / `tunnel_deploy_ok` / `tunnel_deploy_failed` events (visible at `/api/events`) including the first 8 bytes of the script's SHA-256.
- **Timeouts.** All `systemctl`/`journalctl` calls are bounded (10 s) so a hung D-Bus can never wedge an API handler.
- **Proper status codes.** Unknown tunnel `status`/`start`/`stop` → `404`; invalid input → `400`; internal failure → `500`.
- **Token hygiene.** The full token is printed only on the agent's first start; subsequent restarts show a prefix only, so the secret does not accumulate in the systemd journal.

## Data & persistence

All agent state lives in the data directory (default `/var/lib/didban`, `--data` / `DIDBAN_DATA`):

| File | Content | Bound |
|---|---|---|
| `token` | API token | 48 chars |
| `events.jsonl` | Spike / process / deploy events (newest last) | **≤ 1 MiB** — when the file outgrows the budget it is atomically rewritten with the 500 most recent events, so it can never grow unboundedly |
| `history.jsonl` | Minute-resolution chart points (CPU/mem/net) | **7 days** at 1-minute resolution (10 080 points); the file compacts itself when it grows 20% past the window |

Both files survive restarts: the event list (last 500) and the full 7-day chart are reloaded on startup — a reboot no longer wipes the history graphs.

## License

MIT — see [LICENSE](LICENSE).
