package main

import (
	"fmt"
	"html"
	"net/http"
	"strings"
	"time"
)

// serveStatusPage renders an elegant, responsive HTML status page.
func (a *API) handleStatusPage(w http.ResponseWriter, r *http.Request) {
	snap := a.mon.Snapshot()
	events := a.mon.events.List(10)
	sockets := GetNetworkSockets()

	w.Header().Set("Content-Type", "text/html; charset=utf-8")

	statusBadge := `<span class="badge badge-ok">All Systems Operational</span>`
	if snap.CPU.Usage > 85 || snap.Memory.UsagePct > 90 {
		statusBadge = `<span class="badge badge-warn">High Resource Load</span>`
	}

	uptimeStr := formatUptime(snap.UptimeSec)

	var disksHTML strings.Builder
	for _, d := range snap.Disks {
		pct := int(d.UsagePct)
		barColor := "#4ade80"
		if pct > 85 {
			barColor = "#f87171"
		} else if pct > 70 {
			barColor = "#facc15"
		}
		disksHTML.WriteString(fmt.Sprintf(`
			<div class="metric-row">
				<div class="metric-header">
					<span>%s</span>
					<span>%d%% (%.1f / %.1f GB)</span>
				</div>
				<div class="progress-bar"><div class="progress-fill" style="width:%d%%;background:%s;"></div></div>
			</div>`,
			html.EscapeString(d.Mount), pct,
			float64(d.Used)/(1024*1024*1024), float64(d.Total)/(1024*1024*1024),
			pct, barColor))
	}

	var portsHTML strings.Builder
	for _, p := range sockets.Listening {
		procName := p.Process
		if procName == "" {
			procName = "system"
		}
		portsHTML.WriteString(fmt.Sprintf(`
			<div class="port-chip">
				<span class="proto">%s</span>
				<span class="port-num">:%d</span>
				<span class="proc">%s</span>
			</div>`,
			html.EscapeString(p.Proto), p.LocalPort, html.EscapeString(procName)))
	}

	var eventsHTML strings.Builder
	if len(events) == 0 {
		eventsHTML.WriteString(`<p style="color:#94a3b8;font-size:13px;">No recent incidents or spikes recorded.</p>`)
	} else {
		for _, e := range events {
			emoji := "⚡"
			if e.Type == "cpu" {
				emoji = "🔥"
			} else if e.Type == "memory" {
				emoji = "🧠"
			} else if e.Type == "disk" {
				emoji = "💽"
			} else if e.Type == "agent_restart" {
				emoji = "🔄"
			}

			eventsHTML.WriteString(fmt.Sprintf(`
				<div class="event-item">
					<div class="event-title">%s <b>%s</b> — <span style="color:#94a3b8;font-size:11px;">%s</span></div>
					<div class="event-detail">%s</div>
				</div>`,
				emoji, html.EscapeString(e.Type), e.Time.UTC().Format("15:04:05 UTC"),
				html.EscapeString(e.Detail)))
		}
	}

	page := fmt.Sprintf(`<!DOCTYPE html>
<html lang="en">
<head>
	<meta charset="UTF-8">
	<meta name="viewport" content="width=device-width, initial-scale=1.0">
	<title>Status — %s</title>
	<meta http-equiv="refresh" content="15">
	<style>
		:root {
			--bg: #090d16;
			--card: #121a2c;
			--border: #24304d;
			--text: #f1f5f9;
			--subtext: #a9b6ce;
			--accent: #4cc2ff;
			--ok: #4ade80;
			--warn: #facc15;
			--err: #f87171;
		}
		* { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; }
		body { background: var(--bg); color: var(--text); padding: 24px; max-width: 800px; margin: 0 auto; line-height: 1.5; }
		.header { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid var(--border); padding-bottom: 16px; margin-bottom: 24px; }
		.title { font-size: 22px; font-weight: 700; color: var(--accent); display: flex; align-items: center; gap: 8px; }
		.badge { padding: 4px 12px; border-radius: 20px; font-size: 12px; font-weight: 600; }
		.badge-ok { background: #0e2a1e; color: var(--ok); border: 1px solid #1e5c40; }
		.badge-warn { background: #3b280b; color: var(--warn); border: 1px solid #6b4716; }
		.grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 16px; margin-bottom: 24px; }
		.card { background: var(--card); border: 1px solid var(--border); border-radius: 14px; padding: 18px; }
		.card-title { font-size: 12px; color: var(--subtext); text-transform: uppercase; letter-spacing: 0.5px; margin-bottom: 8px; }
		.card-val { font-size: 26px; font-weight: 700; color: var(--text); }
		.progress-bar { background: #1c273e; height: 6px; border-radius: 3px; overflow: hidden; margin-top: 8px; }
		.progress-fill { height: 100%%; border-radius: 3px; transition: width 0.3s ease; }
		.section-title { font-size: 15px; font-weight: 600; margin-bottom: 12px; color: var(--subtext); }
		.ports-grid { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 24px; }
		.port-chip { background: var(--card); border: 1px solid var(--border); border-radius: 8px; padding: 6px 12px; font-size: 12px; display: flex; align-items: center; gap: 6px; }
		.port-chip .proto { font-weight: 700; color: var(--accent); font-size: 10px; text-transform: uppercase; }
		.port-chip .port-num { font-weight: 600; }
		.port-chip .proc { color: var(--subtext); font-size: 11px; }
		.event-item { background: var(--card); border: 1px solid var(--border); border-radius: 10px; padding: 12px; margin-bottom: 8px; font-size: 13px; }
		.event-title { font-weight: 600; margin-bottom: 2px; }
		.event-detail { color: var(--subtext); font-size: 12px; }
		.footer { text-align: center; color: var(--subtext); font-size: 12px; margin-top: 32px; border-top: 1px solid var(--border); padding-top: 16px; }
	</style>
</head>
<body>
	<div class="header">
		<div class="title">👁️ Didban Status</div>
		<div>%s</div>
	</div>

	<div class="grid">
		<div class="card">
			<div class="card-title">Server Hostname</div>
			<div class="card-val" style="font-size:18px;">%s</div>
			<div style="font-size:12px;color:var(--subtext);margin-top:6px;">Uptime: %s</div>
		</div>
		<div class="card">
			<div class="card-title">CPU Usage (%d Cores)</div>
			<div class="card-val">%.1f%%</div>
			<div class="progress-bar"><div class="progress-fill" style="width:%.1f%%;background:%s;"></div></div>
		</div>
		<div class="card">
			<div class="card-title">Memory Usage</div>
			<div class="card-val">%.1f%%</div>
			<div class="progress-bar"><div class="progress-fill" style="width:%.1f%%;background:%s;"></div></div>
			<div style="font-size:11px;color:var(--subtext);margin-top:4px;">%.1f / %.1f GB</div>
		</div>
	</div>

	<div class="section-title">Disks & Storage</div>
	<div class="card" style="margin-bottom:24px;">%s</div>

	<div class="section-title">Listening Ports & Services</div>
	<div class="ports-grid">%s</div>

	<div class="section-title">Recent Activity & Spikes</div>
	<div>%s</div>

	<div class="footer">
		Didban Agent %s • Last updated %s • Auto-refreshing
	</div>
</body>
</html>`,
		html.EscapeString(snap.Hostname),
		statusBadge,
		html.EscapeString(snap.Hostname),
		uptimeStr,
		snap.CPU.Cores,
		snap.CPU.Usage,
		snap.CPU.Usage,
		metricColor(snap.CPU.Usage),
		snap.Memory.UsagePct,
		snap.Memory.UsagePct,
		metricColor(snap.Memory.UsagePct),
		float64(snap.Memory.Used)/(1024*1024*1024), float64(snap.Memory.Total)/(1024*1024*1024),
		disksHTML.String(),
		portsHTML.String(),
		eventsHTML.String(),
		version,
		time.Now().UTC().Format("15:04:05 UTC"),
	)

	_, _ = w.Write([]byte(page))
}

func metricColor(val float64) string {
	if val > 85 {
		return "#f87171"
	}
	if val > 70 {
		return "#facc15"
	}
	return "#4ade80"
}

func formatUptime(sec uint64) string {
	d := sec / 86400
	h := (sec % 86400) / 3600
	m := (sec % 3600) / 60
	if d > 0 {
		return fmt.Sprintf("%dd %dh %dm", d, h, m)
	}
	if h > 0 {
		return fmt.Sprintf("%dh %dm", h, m)
	}
	return fmt.Sprintf("%dm", m)
}
