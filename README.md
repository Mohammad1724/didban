# Didban — دیدبان 👁️

**Monitor your Linux servers from your pocket. Simple, elegant, and it tells you *what* ate your CPU last night.**

🇮🇷 [فارسی](README-fa.md) | 🇬🇧 English

---

## Why Didban?

Every server admin knows the 2 AM question: **"CPU was at 100% last night — what did it?"** Didban answers it. A tiny agent runs on each server, records resource spikes **with the processes that caused them**, and a beautiful Android app (coming in phase 2) shows everything live.

- 📊 **Live metrics** — CPU (incl. %steal!), RAM/swap, disk, network throughput, load, uptime
- 🕵️ **Spike log** — every CPU/memory spike is recorded server-side with the **top culprit processes** — even while your phone is off
- 📈 **History** — 24h of minute-resolution charts
- 📱 **Android app** — bilingual (فارسی/English), dark theme (phase 2)
- 🔐 **Secure by default** — HTTPS with self-signed certs + token auth; the app pins the certificate fingerprint (SSH-style trust-on-first-use)
- 🪶 **Featherweight** — single static Go binary (~8 MB), zero dependencies, ~10 MB RAM, systemd-hardened

## Architecture

```
┌────────────── Your phone ──────────────┐
│  Didban Android app (phase 2)          │
│  • live dashboard + charts             │
│  • spike log: "what ate the CPU?"      │
└───────────────┬────────────────────────┘
                │ HTTPS + Bearer token + cert pinning
┌───────────────▼────────────────────────┐
│  didban-agent (each server)            │
│  • /api/metrics  /api/processes        │
│  • /api/events   /api/history          │
│  • records spikes 24/7 to disk         │
└────────────────────────────────────────┘
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

## API

All `/api/*` endpoints require `Authorization: Bearer <token>` (or `?token=`).

| Endpoint | Description |
|---|---|
| `GET /health` | liveness (no auth) |
| `GET /api/metrics` | CPU (usage/user/system/iowait/**steal**), memory, swap, disks, network rates, load, uptime |
| `GET /api/processes` | top 25 processes by CPU (instant %, memory, user, cmd) |
| `GET /api/events?limit=50` | spike events (newest first) with top-5 culprit processes |
| `GET /api/history?hours=24` | minute-resolution history for charts |

Example:

```bash
curl -sk https://SERVER:8686/api/events \
  -H "Authorization: Bearer TOKEN"
```

```json
{
  "events": [
    {
      "time": "2026-09-03T21:30:06Z",
      "type": "cpu",
      "value": 100,
      "top": [
        {"name": "gzip", "pid": 351964, "cpu": 95.6, "mem_mb": 2.1},
        {"name": "xray", "pid": 167562, "cpu": 4.0, "mem_mb": 108.5}
      ]
    }
  ]
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
| `-cpu-th` | — | `70` | CPU spike threshold (%) |
| `-mem-th` | — | `90` | memory spike threshold (%) |

Spike events survive restarts (`events.jsonl`), are rate-limited (60 s for CPU, 5 min for memory), and keep the last 500.

## Security model

- HTTPS with an auto-generated self-signed certificate (10 years, all local IPs in SANs)
- The Android app pins the certificate SHA-256 fingerprint — trust on first use, exactly like SSH host keys
- Bearer-token auth, constant-time comparison, token stored `0600`
- systemd hardening: read-only filesystem except the data dir, no new privileges, private tmp

If you expose the port to the internet, keep TLS on and use a long token. Firewalling the port to specific IPs is even better.

## Roadmap

- [x] Phase 1 — agent (this repo, `agent/`)
- [ ] Phase 2 — Android app: server manager, live dashboard, charts, spike log, notifications (فارسی + English)
- [ ] Phase 3 — ideas: alert rules, multi-user, Telegram alerts, metrics push

## License

MIT — see [LICENSE](LICENSE).
