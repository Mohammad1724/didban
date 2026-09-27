# Changelog

## Unreleased — 2026-09-27

### Removed tools

- Removed Proxy Inspector, Developer Tools (Developer Lab) and Single-Port from the app and the Tools launcher; the Tools launcher is now two tabs (All, Network Tools) with the Share and Batch utilities kept in All. Routes and their saved keys fall back safely for existing installs.

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
