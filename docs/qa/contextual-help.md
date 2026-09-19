# Beginner help throughout the app

> Historical QA for the help rollout at `32d9645`. The missing Uptime start-control gap described below was subsequently repaired; see [monitoring-control.md](monitoring-control.md). Current in-app guides describe the new Start/Stop path.

## Scope

All 33 `CommandRoute` values have distinct Persian and English guides in `ContextualHelpCatalog.kt`, including the editor routes and compatibility destinations. The catalog is an exhaustive `when`: adding a route requires an explicit guide rather than a generic fallback.

Each guide explains:

1. **What is this for?** — purpose and practical use in plain language.
2. **How do I start?** — 3–6 numbered, tool-specific steps, examples and prerequisites where appropriate.
3. **Tips and limitations** — what the result means, limitations, credentials and risks.

The catalog is bundled/offline. Viewing help does not invoke probes, install software, start monitoring, save drafts, send credentials or run shell commands. Installer/status snippets can only be copied, not executed. The example domains are placeholders, not actual user targets.

## Entry points and interaction

- The shared non-fleet scope bar uses a visible **Help / راهنما** button instead of only a question-mark icon, with a minimum 48dp touch target.
- The unified server list, server inspector (including the mobile bottom sheet) and add/edit-server dialog each have a reachable help control.
- `CommandDestination.helpRoute()` selects list, server-details or server-editor instructions without creating a new navigation destination or changing server scope.
- The server editor opens help above its modal; closing help preserves its existing in-memory, unsaved draft and does not save or close the editor.
- Help has a fixed close control and a weighted scrolling body. Route/language changes reset the scroll context. Copyable commands are laid out LTR inside Persian guides.
- Editor header stays compact on small screens; its full title remains in semantics. Existing screenshot-protection preferences, secret storage, Back history and destructive-action guards are unchanged.

## Accuracy corrections made during the review

Old help sometimes described functionality not present in the current UI. The rewritten guides follow the code:

- **Uptime:** explains website/port/content/certificate checks, phone-side origin, 30-check rolling percentage, the current TCP implementation of its PING option, notification permission and Android background limitations.
- **Known runner wiring gap, NOT fixed in this change:** `MonitorService` starts `UptimeEngine`, but there is no current UI call to start that service. Saving a monitor or pressing Resume must not be described as starting periodic monitoring. Both Uptime guides explicitly disclose the missing start control and the available manual testing path. Adding start/stop controls is a separate functional task; no monitoring is auto-started by help.
- **Single port:** generates HAProxy/Bash/Docker Compose configuration; it is not a port-connectivity test and does not deploy merely by opening the screen.
- **Proxy:** parses configuration/subscription links and probes the destination; it is not a connected VPN or proof of end-to-end proxy authentication.
- **Backup:** uses pasted/copied encrypted text and a password; the current page is not a file-picker workflow and does not require unlocking Vault to start.
- **SSH/SFTP/Services/Security/Batch:** distinguish SSH credentials from Agent tokens. SFTP describes the actual text-file browser/editor; SSH is command execution, not a full interactive terminal. Batch documents its current shared-password/root/port-22 UI path.
- **Security:** documents UFW port allow and Fail2ban unban, not a nonexistent generic Ban control.
- **Cloudflare and SNI scanners:** describe the shipped selectable 15-range and 226-domain catalogs, manual/file alternatives and candidate-only guarantees.
- **Radar:** uses the user's saved Agents and distinguishes last phone readings from server-origin results.
- **Bandwidth/Docker:** mention compatible Agent requirements; app-only upgrades do not add server endpoints. Bandwidth measures the phone–server path and does not expose fictional size/duration controls.
- **Agent installation:** copy commands only in server setup, warn about published-release versus newer-feature compatibility, and do not offer a blanket public firewall-opening command.

## Executed verification

- `compileDebugUnitTestKotlin`: passed.
- Full Android tests: **438 total, 435 passed, 3 existing live-test skips, 0 failures/errors**.
- New coverage: **8 catalog/navigation unit tests + 6 Robolectric/Compose UI tests**.
- UI tests render every route in both languages, reach the route-specific limitations, open/close help, copy static commands, preserve an unsaved editor draft, open inspector help and scroll a long Persian guide at 320dp / 1.5× font scale.
- Existing editor tests now scroll to the actual error/import-feedback text rather than only the card heading. With a 48dp help control, the old heading-based scroll could leave the body off-screen; all original state/value assertions are preserved.
- `lintDebug`: **0 errors, 51 warnings, 5 information**.
- `mergeExtDexDebug` and `assembleDebug`: passed in isolated builds.
- `apksigner verify --verbose`: APK v2 signature verified, one signer.
- `git diff --check`: clean.

No physical phone, TalkBack, live SSH/Agent, private server, external installation, or OEM background-monitoring test was performed. Existing live-test skips were not enabled to validate help. This change needs an app update only, not an Agent update.

## Manual acceptance still recommended

- Open every menu tool and its editor, read purpose/setup/limits in both languages, then close or use system Back.
- Repeat on a small phone, landscape and maximum supported font scale; verify the close control and scrolling remain reachable.
- Add/edit a server, type an unsaved draft, open help, dismiss it using Back and confirm the draft remains unchanged.
- Verify TalkBack labels and reading order, including Persian prose and LTR shell snippets.
- Treat the missing Uptime background start control as a separate tracked functional issue, not as fixed by documentation.
