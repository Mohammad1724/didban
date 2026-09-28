# Changelog

## Unreleased — 2026-09-27

### Scanner: take a result with you, and stop asking for a donor port

- Clean-IP and REALITY donor results can now be copied one at a time. The clean-IP screen only ever exported the top 20 as a block, and the REALITY screen had no clipboard support at all — so a single useful IP found deep in a scan, or the donor name you came for, could not be taken away.
- Removed the global "port" field from the REALITY donor scanner. A donor is an ordinary website reached over TLS 443, so the field read as "scan donors on my REALITY port" (2887, 31049, ...) where no website answers; such a scan correctly found nothing and looked broken. A non-default port is still available per target as `example.com:8443` in the manual list, which `parseSniList` already understood.

### DPI forensics engine (`DpiEngine.kt`)

The censorship diagnosis could report "healthy" for a target the real client could not use at all. Three structural causes, all addressed by the new engine:

- TLS was only attempted on a hardcoded port whitelist (443, 8443, 2053, 2083, 2087, 2096, 9443), so a REALITY service on 2887 or 31049 never got a handshake — a bare `connect()` was reported as healthy.
- Not a single payload byte was exchanged after the handshake, so the dominant "accept then reset after data" operator pattern was invisible.
- The probe's TLS fingerprint was Conscrypt's, not the client's, and the run had no controls, so it could not tell "no filtering" apart from "I am blind on this network".

- `ClientHelloForge` builds ClientHellos for seven fingerprints (modern Chrome with a post-quantum group, legacy Chrome, Firefox, Safari, Edge, Go/Java, and a REALITY-like Chrome with a 32-byte session id), so an operator that whitelists ClientHello patterns is detected by differential.
- `PostHandshakeProbe` watches the connection *after* the handshake (idle survival, then an 8 KB payload) and reports resets that only appear once data moves.
- `PortMatrix`, `RunValidator` (positive/negative controls) and `RemoteVantageCompare` (Check-Host) separate "address filtered" from "port filtered" from "service down", and refuse to trust a run whose negative control came back clean.
- Verdicts now carry a confidence level, the evidence behind them, and an explicit list of what the run did **not** check. The clean verdict is `NO_FILTERING_SEEN`, never "provably unfiltered".

### Removed tools

- Removed Proxy Inspector, Developer Tools (Developer Lab) and Single-Port from the app and the Tools launcher; the Tools launcher is now two tabs (All, Network Tools) with the Share and Batch utilities kept in All. Routes and their saved keys fall back safely for existing installs.

### Smarter guidance from the first real-world DPI run

- Field feedback (a 126ms-healthy run whose certificate was the server's own domain, SAN mismatch): the healthy verdict now drops to attention and explains that the port just tested serves a different TLS service, not the SNI destination — rerun against the exact REALITY port.
- DPI mode now states it up front: «پورت را دقیقاً پورت سرویس REALITY بگذار (مثلاً 2887)، نه 443».

### Middlebox suspicion for the "everything green at 4ms" case

- The DPI run now measures a reference anchor (1.1.1.1 / 8.8.8.8) and compares: if the target answers impossibly fast versus that reference (like the reported 4ms), the verdict becomes «پاسخ‌ها مشکوک‌اند: احتمالاً سرور واقعی جواب نمی‌دهد» — an on-path middlebox is probably answering, not the server.
- If a VPN is active, the screen now leads with «VPN فعاله — نتیجه مالِ مسیر مستقیم نیست» since tunnelled probes say nothing about operator filtering.
- The TLS-with-SNI probe now captures the returned certificate (CN, issuer, SAN match) and shows it, exposing transparent TLS interception.

### DPI diagnostics that can actually catch operator filtering

- The DPI test no longer trusts a single plain connection. It now repeats probes — TCP 3×, TLS twice without SNI and twice with your SNI — and reports pass counts, average latency and injected-RST sightings, because RST injection is intermittent and operator filtering usually targets the ClientHello pattern (REALITY/uTLS), not the plain handshake.
- New optional "SNI مقایسه" field: enter your REALITY server's SNI domain; if plain TLS is open but TLS with that SNI is reset, the verdict reads "TLS blocked only with this SNI" — pattern/SNI filtering on the current operator.
- New network-context banner: Wi-Fi results explicitly warn they say nothing about an operator; on mobile data the current operator name is shown with a reminder to compare on another operator.
- Verdict tips now cover the reported case: plain connection healthy while REALITY fails → likely TLS-fingerprint (uTLS) or post-handshake blocking; try another SNI, fingerprint (chrome→firefox) or port and check server logs.
- Verdict logic pinned by a new unit test (healthy / TCP blocked / TCP down / unstable / TLS blocked / SNI blocked).

### REALITY SNI scanner

- Added an «سایت‌های ایرانی / Iranian sites» category (47 popular Iranian domains — e-commerce, media, telecom, banking, infrastructure) to the bundled SNI candidate list, for domestic-SNI setups; the bundled list is now 273 domains in five categories.

### Tools launcher polish

- The Tools launcher is now one page with two labeled sections — «ابزارهای شبکه» (network tiles) then «سایر ابزارها» (Share, Batch) — replacing the redundant All/Network Tools tabs whose contents overlapped.
- Removed the duplicate TLS and connection-quality tiles that opened the same diagnostics suite as the “تست‌های شبکه” tile; TLS/certificate checking stays inside that suite.
- Removed the GeoIP/DNS mode from the network diagnostics suite; GeoIP details remain on Check-Host results and the DNS manager, which already cover it.

### Backup & restore

- The restore field now keeps its own state (no longer shared with the created-backup output), so pasting a code no longer inserts the output card above and jumps the scroll position — the reported “it appears and disappears” paste bug.
- Added a «چسباندن از کلیپ‌بورد / Paste from clipboard» button that fills the restore field programmatically, with an explicit empty-clipboard message.

- The recovery code now survives real-world copy/paste: whitespace (hard-wrapped lines from messengers, emails or notes apps) inside a pasted backup code is ignored, so it decrypts instead of failing with “wrong password”.
- Copying the recovery code no longer auto-clears after 60 seconds and stays visible to keyboard clipboard managers; the copy button confirms with “کپی شد ✓ / Copied ✓” and a note explains that the preview text is shortened.
- The create-backup form is honest about the password: the field is labelled “رمز پشتیبان‌گیری (الزامی) / Backup password (required)” with an explanation that exports embed credentials, replacing the misleading “Password اختیاری / Password (optional)”.
- The Merge and Overwrite restore modes now carry a plain-language hint of what each one does to current data; regression tests pin the labels, the hints and the whitespace-tolerant paste.

### UI and product audit

- Completed the final product-owner audit for DNS, Tunnel, Manage Servers and Workbench.
- Made narrow layouts predictable for record actions, saved-server actions, SSH/SFTP server selectors and active-file controls.
- Moved visible clipboard labels into localized `CommandCopy` entries and kept important dimensions in `CommandMetrics`.
- Added explicit semantics and touch-target coverage for SFTP entries and batch selection.
- Added deterministic Compose coverage for the local empty states of DNS, Tunnel, Manage Servers and SFTP at 320dp with a 1.5 font scale.
- Aligned the Network Tools and DNS indexes with the shared section-title and icon-launcher primitives: no custom hero, no grouped-card exception and no duplicated page title.
- The Tools `All` tab now includes the canonical network launcher tiles; the `Network Tools` control is an in-place selectable tab instead of opening a second page.

### Check-Host reachability

- Check-Host probes now use a deterministic cohort selected from the live inventory, with balanced country quotas and ASN diversity; repeated cities remain valid when they represent separate networks.
- Ping results preserve min/average/max RTT, partial packet loss is shown as attention rather than healthy, resolved target IPs are displayed per probe, and unavailable probes remain visible as no-data/error.

### Navigation and tool discoverability

- A fresh app session now opens Tools first; the primary navigation order is Tools, Servers, Monitoring and Settings.
- The Tools launcher opens on Network Tools while keeping network tiles available in All, and network labels now state the action and technology plainly (Check-Host, DNS, TLS, network tests and connection quality).
- The Tools `All` tab now mirrors the tab order: network tiles are listed first, then the connection and development utilities, instead of leading with the connection tools.
- Renamed the scanner tiles to say what they do: «اسکنر IP کلودفلر» / “Cloudflare IP scanner” and «اسکنر REALITY» / “REALITY scanner”.
- Renamed `Developer Lab` to «ابزارهای توسعه‌دهنده» / “Developer tools” so the tile states its purpose; regression tests pin the All-tab order.

### Verification

- Android CI and Agent CI are green for the final audit commit: [Android #318](https://github.com/Mohammad1724/didban/actions/runs/36188090880) and [Agent #312](https://github.com/Mohammad1724/didban/actions/runs/36188090887).
- The Android report artifact includes the existing redesign screenshot output under `app/build/reports/redesign/**`.
- The current repository already has a `v0.7.0` tag; no new release tag is created by this audit. The next version tag should be chosen as part of the release decision.

### CI maintenance

- Updated GitHub Actions pins to Node 24-compatible releases.
- Pinned runners to `ubuntu-24.04` to avoid the upcoming `ubuntu-latest` image migration warning.
- Kept every action reference pinned to a full commit SHA.
