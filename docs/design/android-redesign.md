# Approved Android redesign

This document records the first implementation (`8041a3f`). The subsequent owner-approved visual polish is documented in [Restrained glass refinement](glass-refinement.md); navigation and security constraints below remain in force.

The owner approved the `ui-redesign-v1.html` direction. This change applies it to the actual Compose application rather than shipping another web mockup. Existing serialized route keys, server records, Agent APIs and scanner engines remain unchanged.

## Navigation and page structure

| Primary section | Contents |
|---|---|
| Servers | Searchable fleet, honest health/freshness, all/offline/attention/not-checked filters, add/import, full-page details and editor |
| Monitoring | Lifecycle-backed Start/Stop, website/service targets, Radar comparison using already-saved servers |
| Tools | Cloudflare IP and Reality SNI scanners, network diagnostics, DNS, proxy inspection, single-port configuration, developer utilities, explicitly multi-server Batch |
| Settings | Language and appearance, alerts, Vault, backup, polling and capture protection |

Phones have four persistent bottom tabs. Layouts at least 840dp wide have a rail. Server details replace the fleet page; confirmations and Help remain dialogs. Editing hides the primary navigation and intercepts hardware Back to preserve the dirty-draft guard. The old dialog editor remains available to standalone callers with the same security behavior.

Docker, services, SSH, files, processes, bandwidth, tunnels and security are reached from the selected server's details. The actual tool-owned server identity remains visible; the shared header suppresses only duplicate titles and Back controls, not scope information or actions. Radar lives under Monitoring and retains its existing server picker and sole-server selection behavior.

Legacy route restoration and canonicalization are unchanged. Old overview/incidents/manage/dossier routes continue to resolve to the server workspace. A pure, exhaustive presentation mapping assigns every legacy route to one of the four sections.

## Presentation

- Light-first pale-gray canvas, white bordered surfaces, dark text and blue actions. Dark mode uses charcoal surfaces, not neon.
- Only an absent theme preference defaults to light. Explicit `dark`, `light` and `auto` choices remain intact.
- Shared page chrome, 48dp minimum controls, wrapping button text, scalable bundled typography, RTL/LTR layouts and contextual Help.
- Cards show actual resource measurements only when present; unknown and offline are separate states. Stale readings retain a visible stale warning. No synthetic CPU, RAM or healthy values are introduced.
- Uptime cards do not display a percentage or latency before the first check. A failed manual check is not colored as success.
- Cloudflare advanced settings are collapsed by default. Ready ranges, manual input and file import stay visible. Reality batch limit/port and the discouraged-donor reference use disclosures. Field state remains owned outside the disclosure, so collapsing does not reset values.
- Persian and English copy and navigation Help are updated. The obsolete copy keys no longer used by screens are removed.

## Preservation and boundaries

No changes to Agent routes, polling/monitoring service lifecycle, TLS pins, record encryption, SSH trust, scanner plans/probes or persisted navigation schema. Connection credentials remain masked and RAM-only while editing. Import, name updates, test/save validation, optimistic-concurrency checks, cancellation/discard, deferred notification routing and default-off capture protection are retained.

Older Agents without Docker/bandwidth endpoints still require an Agent update; changing Android visuals does not add those endpoints. Candidate SNI/IP lists are not promises of live connectivity. Server refresh and scheduled site monitoring remain distinct.

## Verification

Added coverage includes route grouping/reachability, unknown filtering, saved-theme preservation, palette contrast, four-tab UI navigation, full-page details, embedded-editor dirty Back and scanner field retention after collapsing. Radar tests now navigate via Monitoring and assert a single page title while retaining their server-picker/scope guarantees.

`CommandRedesignScreenshotTest` renders real Compose views using Robolectric native graphics: Persian light phone, all primary sections, details and scanner; English dark tablet; 320dp phone at 1.5× font scale. Its hosts are explicitly synthetic `.example` fixtures and no Agent results are fabricated. Generated images are test renders, **not physical-device screenshots**.

Final verification: **469 tests, 466 passed, 3 pre-existing skips, 0 failures/errors**. Lint: **0 errors, 51 warnings and 5 information messages**. Kotlin compilation, dependency DEX, debug APK assembly and v2 signature verification passed. Exact counts and artifact hashes are in [`android-redesign-verification.json`](android-redesign-verification.json). Eight native test renders are in [`android-screenshots/`](android-screenshots/). Physical phone/OEM behavior, TalkBack and a private live Agent are not validated by these automated tests.


On the 2GB verification host, compilation and tests ran in separate Gradle invocations to avoid retaining compiler memory alongside the Robolectric JVM. The initial combined attempt exhausted the build daemon; the split full run passed. Native screenshots draw the laid-out Compose view to a bitmap, since Robolectric does not provide a real display compositor for PixelCopy.
