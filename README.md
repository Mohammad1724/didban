# Didban — دیدبان 👁️

**Your all-in-one Linux server monitoring & DevOps command center. Simple, featherweight, and it tells you *what* ate your CPU last night.**

🇮🇷 [فارسی](README-fa.md) | 🇬🇧 English

---

## Why Didban?

Every server admin knows the 2 AM question: **"CPU was at 100% last night — what did it?"** Didban answers it. A tiny Go agent runs on each server, records resource spikes **with the processes that caused them**, sends instant **Telegram/Discord/Webhook alerts**, provides an **embeddable public HTML status page**, monitors website **uptime with Uptime Kuma style heartbeat bars**, and packs a powerhouse **mobile DevOps toolkit** (Cloudflare DNS, SSL inspector, Port scanner, GeoIP, encrypted vault, local web server, and developer tools).

- 📊 **Live server metrics** — CPU (incl. %steal!), RAM/swap, disk, network throughput, load, uptime
- 🕵️ **Spike forensics** — every CPU/memory spike is recorded server-side with the **top culprit processes** — even while your phone is off
- ⏱️ **Uptime & Heartbeat Engine (Uptime Kuma style)** — monitor HTTP(S), TCP ports (MySQL, Redis, Postgres), Ping, Keywords, and SSL with 30-bar heartbeats and incident downtime tracking
- 📄 **Public HTML Status Page** — built-in responsive web status page at `/status` showing server health, uptime, disks, ports, and activity
- ✈️ **Multi-channel instant alerts** — get rich alerts with top culprit processes sent to **Telegram**, **Discord Webhooks**, and **Generic Webhooks**
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
│  • Uptime Kuma Heartbeat Monitor (HTTP, TCP, Ping, SSL)        │
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
│  • /api/events   /api/history    /api/processes/kill           │
│  • Public HTML Status Page (/status)                           │
│  • Multi-Channel Alert Dispatcher (Telegram/Discord/Webhook)   │
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

## Public Status Page

Visit `https://YOUR_SERVER_IP:8686/status` in your browser to view the clean, auto-refreshing public server status board.

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

All `/api/*` endpoints require `Authorization: Bearer <token>` (or `?token=`).

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/health` | liveness & alert status (no auth) |
| `GET` | `/status` | public responsive HTML status board |
| `GET` | `/api/metrics` | CPU (usage/user/system/iowait/**steal**), memory, swap, disks, network rates, load, uptime |
| `GET` | `/api/processes` | top 25 processes by CPU (instant %, memory, user, cmd) |
| `GET` | `/api/network/sockets` | listening ports & active TCP/UDP sockets matched to PIDs and process names |
| `POST` | `/api/processes/kill` | terminate a runaway process safely (`{"pid": 1234, "signal": "SIGTERM"}`) |
| `POST` | `/api/alerts/test` | dispatch a test alert to Telegram, Discord, and Webhooks |
| `GET` | `/api/events?limit=50` | spike events (newest first) with top culprit processes |
| `GET` | `/api/history?hours=24` | minute-resolution history for charts |

## License

MIT — see [LICENSE](LICENSE).
