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

// TelegramNotifier handles sending formatted alerts to Telegram chat/channel.
type TelegramNotifier struct {
	token   string
	chatID  string
	client  *http.Client
	enabled bool
}

func NewTelegramNotifier(token, chatID, proxyURL string) *TelegramNotifier {
	token = strings.TrimSpace(token)
	chatID = strings.TrimSpace(chatID)
	if token == "" || chatID == "" {
		return &TelegramNotifier{enabled: false}
	}

	client := &http.Client{
		Timeout: 10 * time.Second,
	}

	if proxyURL = strings.TrimSpace(proxyURL); proxyURL != "" {
		if parsed, err := url.Parse(proxyURL); err == nil {
			client.Transport = &http.Transport{
				Proxy: http.ProxyURL(parsed),
			}
		}
	}

	return &TelegramNotifier{
		token:   token,
		chatID:  chatID,
		client:  client,
		enabled: true,
	}
}

func (tn *TelegramNotifier) IsEnabled() bool {
	return tn != nil && tn.enabled
}

// SendAlert formats an Event and sends it to Telegram.
func (tn *TelegramNotifier) SendAlert(ev Event, hostname string) error {
	if !tn.IsEnabled() {
		return nil
	}
	msg := formatTelegramEvent(ev, hostname)
	return tn.sendMessage(msg)
}

// SendTest sends a test message to verify Telegram setup.
func (tn *TelegramNotifier) SendTest(hostname string) error {
	if !tn.IsEnabled() {
		return fmt.Errorf("telegram alerts not configured (token or chat_id missing)")
	}
	msg := fmt.Sprintf("✅ <b>Didban Alert Test</b>\n\n"+
		"Notifications are working successfully for <code>%s</code>!\n"+
		"⏱ <i>%s</i>",
		html.EscapeString(hostname),
		time.Now().UTC().Format("2006-01-02 15:04:05 UTC"),
	)
	return tn.sendMessage(msg)
}

func (tn *TelegramNotifier) sendMessage(htmlText string) error {
	apiURL := fmt.Sprintf("https://api.telegram.org/bot%s/sendMessage", tn.token)
	payload := map[string]any{
		"chat_id":                  tn.chatID,
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

	resp, err := tn.client.Do(req)
	if err != nil {
		return fmt.Errorf("telegram request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return fmt.Errorf("telegram API returned HTTP %d", resp.StatusCode)
	}
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
