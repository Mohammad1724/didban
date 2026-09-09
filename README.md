# Didban — دیدبان 👁️

**Your all-in-one Linux server monitoring & DevOps command center. Simple, featherweight, and it tells you *what* ate your CPU last night.**

🇮🇷 [فارسی](README-fa.md) | 🇬🇧 English

---

## Why Didban?

Every server admin knows the 2 AM question: **"CPU was at 100% last night — what did it?"** Didban answers it. A tiny Go agent runs on each server, records resource spikes **with the processes that caused them**, sends instant **Telegram alerts**, provides **live open ports & sockets**, allows **1-tap process termination**, and pairs with a powerhouse **mobile DevOps toolkit** (Cloudflare DNS, SSL inspector, Port scanner, GeoIP, encrypted vault, local web server, and developer tools).

- 📊 **Live server metrics** — CPU (incl. %steal!), RAM/swap, disk, network throughput, load, uptime
- 🕵️ **Spike forensics** — every CPU/memory spike is recorded server-side with the **top culprit processes** — even while your phone is off
- ✈️ **Telegram instant alerts** — get rich alerts with top culprit processes whenever CPU/RAM spikes occur (proxy-friendly)
- 👂 **Listening ports & active connections** — live inspection of server ports, sockets, connected remote IPs, matched to PIDs and process names
- 🛑 **Remote process killer** — search, inspect, and terminate (SIGTERM/SIGKILL) heavy or runaway processes straight from the app or API
- ☁️ **Cloudflare DNS manager** — manage zones and DNS records (A, AAAA, CNAME, TXT, MX), toggle proxy status, and adjust TTL
- 🌐 **Global Check-Host** — probe server reachability (Ping, HTTP, TCP, DNS) from 20+ nodes worldwide (Germany, USA, Iran, France, etc.)
- 🛰️ **Network diagnostics** — multi-threaded Port Scanner, SSL Inspector (expiry countdown, SANs, chain), IP & GeoIP lookup, and TCP Pinger
- 🔐 **Encrypted Vault & backup** — store confidential notes & credentials with AES-256-GCM encryption and export/import full backups
- 📡 **Local Web Server & QR Code** — share files and text across local Wi-Fi with instant QR code downloading
- 🛠️ **Developer Lab (String Lab)** — Base64, JSON formatter/minifier, Subnet/CIDR calculator, JWT decoder, Hashes, and UUID/Password generator
- 📱 **Android app** — bilingual (فارسی/English), Obsidian dark theme, 1-click SSH auto-installer
- 🔐 **Secure by default** — HTTPS with self-signed certs + token auth; the app pins the certificate fingerprint (SSH-style trust-on-first-use)
- 🪶 **Featherweight** — single static Go binary (~8 MB), zero external dependencies, ~10 MB RAM, systemd-hardened

## Architecture

```
┌────────────────────────── Your phone ──────────────────────────┐
│  Didban Android App (All-in-One Command Center)                │
│  • Server Fleet Dashboard & 24h Charts                         │
│  • Spike Log ("what ate the CPU?") + Process Killer            │
│  • Listening Ports & Active Socket Connections                 │
│  • Cloudflare DNS Manager (Zones, Records, Proxy)              │
│  • Network Hub (Port Scanner, SSL Inspector, GeoIP, Ping)      │
│  • Encrypted Vault & Backup (AES-256-GCM)                      │
│  • Local File Sharing Server & QR Generator                    │
│  • Developer Lab (Base64, JSON, CIDR, JWT, Generators)         │
└───────────────────────────────┬────────────────────────────────┘
                                │ HTTPS + Bearer token + cert pinning
┌───────────────────────────────▼────────────────────────────────┐
│  didban-agent (each server)                                    │
│  • /api/metrics  /api/processes  /api/network/sockets          │
│  • /api/events   /api/history    /api/processes/kill           │
│  • Instant Telegram Alert Dispatcher                           │
│  • records spikes 24/7 to disk                                 │
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

<details>
<summary>Alternative: clone the repo</summary>

```bash
git clone https://github.com/Mohammad1724/didban.git
cd didban/agent
sudo bash install.sh
```
</details>

<details>
<summary>Alternative: manual run for testing (no install)</summary>

```bash
cd agent && go build -o didban-agent .
./didban-agent -addr 127.0.0.1:8686 -token mytoken -data ./data -plain
```
</details>

## Telegram Alerts Configuration

Configure Telegram notifications in `/etc/didban/agent.conf` or via command-line flags:

```bash
# In /etc/didban/agent.conf
DIDBAN_TG_TOKEN="123456789:ABCdefGHIjklMNOpqrsTUVwxyz"
DIDBAN_TG_CHAT_ID="-100123456789"
# Optional HTTP or SOCKS5 proxy if Telegram is blocked:
# DIDBAN_TG_PROXY="socks5://127.0.0.1:1080"
```

Then restart the service:
```bash
sudo systemctl restart didban-agent
```

Test your Telegram bot connection:
```bash
curl -sk -X POST https://YOUR_SERVER_IP:8686/api/alerts/telegram/test -H "Authorization: Bearer YOUR_TOKEN"
```

## API

All `/api/*` endpoints require `Authorization: Bearer <token>` (or `?token=`).

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/health` | liveness (no auth) |
| `GET` | `/api/metrics` | CPU (usage/user/system/iowait/**steal**), memory, swap, disks, network rates, load, uptime |
| `GET` | `/api/processes` | top 25 processes by CPU (instant %, memory, user, cmd) |
| `GET` | `/api/network/sockets` | listening ports & active TCP/UDP sockets matched to PIDs and process names |
| `POST` | `/api/processes/kill` | terminate a runaway process safely (`{"pid": 1234, "signal": "SIGTERM"}`) |
| `POST` | `/api/alerts/telegram/test` | dispatch a test alert to the configured Telegram chat |
| `GET` | `/api/events?limit=50` | spike events (newest first) with top culprit processes |
| `GET` | `/api/history?hours=24` | minute-resolution history for charts |

### Kill Process Example:
```bash
curl -sk -X POST https://SERVER:8686/api/processes/kill \
  -H "Authorization: Bearer TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"pid": 351964, "signal": "SIGTERM"}'
```

```json
{
  "pid": 351964,
  "name": "gzip",
  "signal": "SIGTERM",
  "success": true,
  "message": "Signal SIGTERM sent to gzip (PID 351964)"
}
```

## Configuration

Flags (or environment variables via `/etc/didban/agent.conf`):

| Flag | Env | Default | Description |
|---|---|---|---|
| `-addr` | `DIDBAN_ADDR` | `:8686` | listen address |
| `-token` | `DIDBAN_TOKEN` | auto-generated | auth token |
| `-data` | `DIDBAN_DATA` | `/var/lib/didban` | data dir (events, certs, token) |
| `-plain` | `DIDBAN_PLAIN=1` | off | disable TLS (**not** recommended) |
| `-cpu-th` | `DIDBAN_CPU_TH` | `70` | CPU spike threshold (%) |
| `-mem-th` | `DIDBAN_MEM_TH` | `90` | memory spike threshold (%) |
| `-steal-th` | `DIDBAN_STEAL_TH` | `10` | CPU steal threshold (%) |
| `-disk-th` | `DIDBAN_DISK_TH` | `90` | disk usage warning threshold (%) |
| `-tg-token` | `DIDBAN_TG_TOKEN` | empty | Telegram Bot Token |
| `-tg-chat` | `DIDBAN_TG_CHAT_ID` | empty | Telegram Chat / Channel ID |
| `-tg-proxy` | `DIDBAN_TG_PROXY` | empty | HTTP/SOCKS5 proxy for Telegram |
| `-tg-alerts` | `DIDBAN_TG_ALERTS` | `1` | Enable Telegram alerts |

## Security model

- HTTPS with an auto-generated self-signed certificate (10 years, all local IPs in SANs)
- The Android app pins the certificate SHA-256 fingerprint — trust on first use, exactly like SSH host keys
- Bearer-token auth, constant-time comparison, token stored `0600`
- Encrypted Vault with AES-256-GCM + PBKDF2 (100,000 iterations)
- Process killer safeguards: PID <= 1 and agent self-PID are strictly protected against signals
- systemd hardening: read-only filesystem except data dir, no new privileges, private tmp

## License

MIT — see [LICENSE](LICENSE).
