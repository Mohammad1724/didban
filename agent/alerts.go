package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"html"
	"net/http"
	"net/url"
	"strings"
	"time"
)

// AlertDispatcher handles dispatching alerts to Telegram, Discord, and Webhooks.
type AlertDispatcher struct {
	tgToken        string
	tgChatID       string
	discordWebhook string
	genericWebhook string
	client         *http.Client
	tgEnabled      bool
	discordEnabled bool
	webhookEnabled bool
}

func NewAlertDispatcher(tgToken, tgChatID, tgProxy, discordWebhook, genericWebhook string) *AlertDispatcher {
	tgToken = strings.TrimSpace(tgToken)
	tgChatID = strings.TrimSpace(tgChatID)
	discordWebhook = strings.TrimSpace(discordWebhook)
	genericWebhook = strings.TrimSpace(genericWebhook)

	client := &http.Client{
		Timeout: 10 * time.Second,
	}

	if tgProxy = strings.TrimSpace(tgProxy); tgProxy != "" {
		if parsed, err := url.Parse(tgProxy); err == nil {
			client.Transport = &http.Transport{
				Proxy: http.ProxyURL(parsed),
			}
		}
	}

	return &AlertDispatcher{
		tgToken:        tgToken,
		tgChatID:       tgChatID,
		discordWebhook: discordWebhook,
		genericWebhook: genericWebhook,
		client:         client,
		tgEnabled:      tgToken != "" && tgChatID != "",
		discordEnabled: discordWebhook != "",
		webhookEnabled: genericWebhook != "",
	}
}

func (ad *AlertDispatcher) HasActiveProviders() bool {
	return ad != nil && (ad.tgEnabled || ad.discordEnabled || ad.webhookEnabled)
}

func (ad *AlertDispatcher) IsTelegramEnabled() bool {
	return ad != nil && ad.tgEnabled
}

// SendAlert broadcasts the event to all configured notification channels.
func (ad *AlertDispatcher) SendAlert(ev Event, hostname string) {
	if !ad.HasActiveProviders() {
		return
	}
	if ad.tgEnabled {
		_ = ad.sendTelegram(formatTelegramEvent(ev, hostname))
	}
	if ad.discordEnabled {
		_ = ad.sendDiscord(ev, hostname)
	}
	if ad.webhookEnabled {
		_ = ad.sendGenericWebhook(ev, hostname)
	}
}

// SendTest sends a test notification to all configured channels.
func (ad *AlertDispatcher) SendTest(hostname string) error {
	if !ad.HasActiveProviders() {
		return fmt.Errorf("no notification channels configured (set Telegram, Discord, or Webhook)")
	}
	var errs []string
	if ad.tgEnabled {
		msg := fmt.Sprintf("✅ <b>Didban Alert Test</b>\n\n"+
			"Notifications are working successfully for <code>%s</code>!\n"+
			"⏱ <i>%s</i>",
			html.EscapeString(hostname),
			time.Now().UTC().Format("2006-01-02 15:04:05 UTC"),
		)
		if err := ad.sendTelegram(msg); err != nil {
			errs = append(errs, "Telegram: "+err.Error())
		}
	}
	if ad.discordEnabled {
		testEv := Event{Time: time.Now(), Type: "test", Detail: "Test notification from Didban Agent"}
		if err := ad.sendDiscord(testEv, hostname); err != nil {
			errs = append(errs, "Discord: "+err.Error())
		}
	}
	if ad.webhookEnabled {
		testEv := Event{Time: time.Now(), Type: "test", Detail: "Test notification from Didban Agent"}
		if err := ad.sendGenericWebhook(testEv, hostname); err != nil {
			errs = append(errs, "Webhook: "+err.Error())
		}
	}
	if len(errs) > 0 {
		return fmt.Errorf(strings.Join(errs, "; "))
	}
	return nil
}

func (ad *AlertDispatcher) sendTelegram(htmlText string) error {
	apiURL := fmt.Sprintf("https://api.telegram.org/bot%s/sendMessage", ad.tgToken)
	payload := map[string]any{
		"chat_id":                  ad.tgChatID,
		"text":                     htmlText,
		"parse_mode":               "HTML",
		"disable_web_page_preview": true,
	}

	body, err := json.Marshal(payload)
	if err != nil {
		return err
	}

	req, err := http.NewRequest(http.MethodPost, apiURL, bytes.NewReader(body))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := ad.client.Do(req)
	if err != nil {
		return fmt.Errorf("telegram request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return fmt.Errorf("telegram API returned HTTP %d", resp.StatusCode)
	}
	return nil
}

func (ad *AlertDispatcher) sendDiscord(ev Event, hostname string) error {
	color := 16278897 // red
	if ev.Type == "process_up" || ev.Type == "test" {
		color = 4906880 // green
	} else if ev.Type == "memory" {
		color = 9684477 // blue
	}

	var procsText strings.Builder
	for _, p := range ev.Top {
		procsText.WriteString(fmt.Sprintf("• `%s` (PID %d): **%.1f%% CPU** | %.0f MB RAM\n",
			p.Name, p.PID, p.CPU, p.MemMB))
	}

	embed := map[string]any{
		"title":       fmt.Sprintf("🚨 Didban Alert — %s", strings.ToUpper(ev.Type)),
		"description": fmt.Sprintf("**Server:** `%s`\n**Detail:** %s", hostname, ev.Detail),
		"color":       color,
		"timestamp":   ev.Time.UTC().Format(time.RFC3339),
	}

	if procsText.Len() > 0 {
		embed["fields"] = []map[string]any{
			{"name": "Top Culprit Processes", "value": procsText.String(), "inline": false},
		}
	}

	payload := map[string]any{
		"username": "Didban Bot",
		"embeds":   []any{embed},
	}

	body, _ := json.Marshal(payload)
	req, err := http.NewRequest(http.MethodPost, ad.discordWebhook, bytes.NewReader(body))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := ad.client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	return nil
}

func (ad *AlertDispatcher) sendGenericWebhook(ev Event, hostname string) error {
	payload := map[string]any{
		"app":       "didban",
		"server":    hostname,
		"event":     ev,
		"timestamp": time.Now().Unix(),
	}

	body, _ := json.Marshal(payload)
	req, err := http.NewRequest(http.MethodPost, ad.genericWebhook, bytes.NewReader(body))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := ad.client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	return nil
}

func formatTelegramEvent(ev Event, hostname string) string {
	var emoji, title string
	switch ev.Type {
	case "cpu":
		emoji, title = "🔥", "CPU Spike Alert"
	case "memory":
		emoji, title = "🧠", "Memory Spike Alert"
	case "steal":
		emoji, title = "🥷", "CPU Steal Warning"
	case "disk":
		emoji, title = "💽", "Disk Almost Full"
	case "process_down":
		emoji, title = "💀", "Watched Process Down"
	case "process_up":
		emoji, title = "✅", "Watched Process Restored"
	case "agent_restart":
		emoji, title = "🔄", "Agent (Re)started"
	default:
		emoji, title = "⚠️", fmt.Sprintf("Event: %s", ev.Type)
	}

	var sb strings.Builder
	sb.WriteString(fmt.Sprintf("%s <b>Didban — %s</b>\n", emoji, title))
	sb.WriteString(fmt.Sprintf("🖥 <b>Server:</b> <code>%s</code>\n", html.EscapeString(hostname)))

	if ev.Value > 0 {
		sb.WriteString(fmt.Sprintf("📊 <b>Value:</b> %.1f%%\n", ev.Value))
	}

	if ev.Detail != "" {
		sb.WriteString(fmt.Sprintf("📝 <b>Detail:</b> %s\n", html.EscapeString(ev.Detail)))
	}

	if len(ev.Top) > 0 {
		sb.WriteString("\n🕵️ <b>Top Culprit Processes:</b>\n")
		for _, p := range ev.Top {
			cpuStr := ""
			if p.CPU > 0 {
				cpuStr = fmt.Sprintf(" • <b>%.1f%% CPU</b>", p.CPU)
			}
			memStr := ""
			if p.MemMB > 0 {
				memStr = fmt.Sprintf(" • %.0f MB RAM", p.MemMB)
			}
			sb.WriteString(fmt.Sprintf("  • <code>%s</code> (PID %d)%s%s\n",
				html.EscapeString(p.Name), p.PID, cpuStr, memStr))
		}
	}

	sb.WriteString(fmt.Sprintf("\n⏱ <i>%s</i>", ev.Time.UTC().Format("2006-01-02 15:04:05 UTC")))
	return sb.String()
}
