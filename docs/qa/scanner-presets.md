# Offline scanner presets

## Scope and provenance

- The clean-IP engine already contained Cloudflare ranges but did not expose range selection. The UI now exposes all **15 official IPv4 CIDRs**, selected by default, with per-range accessible checkboxes, Select all / Clear, a visible source URL and bundled-list date. The unchanged range snapshot was compared with https://www.cloudflare.com/ips-v4/ on **2026-09-19**. This scanner is Cloudflare-specific: it does not pretend its `/cdn-cgi/trace` checks work on arbitrary CDN/provider ranges. IPv6 is not offered by this IPv4 planner.
- SNI presets contain **226 unique public-host candidates**: technology/hardware 53, development/open source 63, knowledge/media 54, services/shopping 56. This is an in-repository, hand-curated list of domain names, **not** a downloaded ranking or independently verified working-donor feed. Neither availability nor REALITY suitability is guaranteed. No website content is bundled.
- The All category interleaves the four groups so a short run is not limited to the first group. Default run size is **50**, maximum **256**; the preview shows the actual count. Port selection applies to the batch. Existing manual single-domain testing remains available.
- Lists are offline snapshots. Displaying a list, previewing it or changing category does not contact its domains. Only explicit Start initiates probes. Updating the app can update the bundled catalog; there is no hidden automatic list download or background scan.

## User flow

Clean IP: choose preset ranges (default: all), optionally expand and select individual CIDRs, set the sample count, Start. The engine samples addresses; it does not enumerate entire large networks. Clearing every selection reports an error instead of silently using all ranges. The custom-IP/CIDR mode remains, and its run now respects the configured count too.

SNI: choose Built-in list → category → scan limit → Scan selected list. View list previews the chosen category. Copy to custom list makes it editable. Custom input accepts one domain or domain:port per line, optional HTTP(S) URL form and `#` comments; it normalizes/deduplicates, reports invalid lines and bounds the run. Distinct ports for one host have distinct result keys.

Both screens offer **Import text file** through Android's document picker, with strict UTF-8 decoding (BOM accepted) and a hard **64 KiB** actual-stream limit. Files do not trigger a scan. Imported content becomes the editable custom list; no persistent URI permission or server upload is needed. The test suite verifies the bounded reader, not a physical Android document provider.

## Runtime and safety

- Existing TLS/certificate/CDN assessment criteria are unchanged. Preset membership cannot produce a good result without a probe. The UI explicitly warns that phone reachability does not prove server-side REALITY suitability.
- SNI batching uses up to **four workers**, not a coroutine per arbitrary imported row. The UI probe uses a 3-second socket-phase timeout. Stop cancels the batch and prevents queued work from starting. Already-blocking platform DNS/socket operations can take time to unwind; do not promise instantaneous DNS cancellation.
- Input/category/range selection is saveable; jobs/results are not represented as persisted running work. Controls that change the scan plan are disabled during a run. Start reads current input state rather than a captured immutable plan.
- Manual input is bounded. No credentials are added to the catalog. No Agent update or new server-side route is involved.

## Verification

**424 Android tests: 421 passed, 3 pre-existing live-test skips, no failures/errors.** Eight new catalog/worker/file tests and seven new Robolectric/Compose UI tests cover default scans with no typing, live selected CIDRs/counts, empty selection, category/port changes, custom domains, duplicate ports and Stop. Probe callbacks in UI tests are fixtures: no mass live-domain scan was run in this environment.

`lintDebug`: zero errors, 51 existing warnings, 5 information items. Debug APK built and its v2 signature verified. On the small runner lint, external dex merge and assemble were run in separate Gradle processes.

Manual acceptance still required on real devices: Persian/English and small/large text, portrait/landscape, range and category selection across rotation, Android document-provider Cancel/success/oversize/permission-error paths, stop on slow DNS, real ISP connectivity, and interpretation of rejected/unknown results. Never describe the catalog as guaranteed clean IPs or confirmed SNI donors.
