# Unified server workspace

This supersedes the five-page overview/incidents/fleet/dossier/connections flow and the overview-root portions of `back-and-refresh.md`.

## UI contract

- The navigation menu exposes **Servers** once. Overview, Incidents, Server Dossier and Manage Connections are legacy route keys only; callers and saved histories normalize into the same workspace.
- The workspace includes a compact live summary, current-alert shortcuts, search, All/Offline/Needs attention filters, and the server list. Current alerts are explicitly not presented as historical events.
- Phone details use a modal bottom sheet. At a content width of 840 dp or more, the inspector appears next to the list. Choosing another server replaces the inspector rather than stacking detail pages.
- Adding/editing opens a scoped form above the workspace. The old multi-connection selector and separate management destination are not mounted by the shell.
- Docker, Processes, SSH, Tunnels, Services and SFTP remain dedicated tools with the selected server ID carried along. Back restores that server's inspector; closing it returns to the list.
- Search/filter/list state uses a route saveable-state holder. Navigation saves only route keys, pane types and server IDs. The old two-field saved navigation format remains readable.
- Fleet refresh targets the latest saved fleet; inspector refresh targets only that server. Both observe the existing real refresh coordinator and its progress/error/timeout results. A storage read failure is shown as an error, not as an empty fleet.

## Credential and mutation safety

- The editor uses the existing HTTPS, separate read/admin tokens and certificate-pin validation. Invalid ports and alert thresholds are rejected rather than silently clamped.
- Credentials remain masked. Screenshots are **allowed by default**, including the connection form. Settings → Security offers an optional sensitive-screen screenshot block (Persian/English). Activity flags, editor and discard-dialog policies follow the same reactive preference; switching it off releases active protection. Encryption, TLS/pinning, redaction and RAM-only drafts are unchanged.
- Unsaved drafts live in an Activity ViewModel **in RAM only**. Rotation does not reset the draft. No secret is put into a Bundle, SavedStateHandle or `rememberSaveable`. Process death can discard an unsaved draft; the form explains this limitation.
- Back, outside dismissal and Close on a dirty editor require discard confirmation. Notifications wait until editing finishes instead of replacing a credential form.
- Before save/delete, the latest encrypted records are reloaded. Full original-record equality is checked: stale editors cannot overwrite a newer edit, recreate a deleted server, or erase another server added during editing. An ID collision on creation also fails closed.
- Delete remains local removal of the connection, not uninstalling an Agent or deleting remote services. The confirmation identifies the server.

## Automated verification

```sh
gradle testDebugUnitTest lintDebug assembleDebug --no-daemon
```

Relevant tests:
- `CommandNavigationTest`: menu consolidation, old-route migration, panel/tool Back, save/cancel, deletion cleanup and bounded history.
- `ServerWorkspaceTest`: live-health classification, filters/search, stable sorting, stale-write protection, validation, memory-only draft rebinding and storage-error handling.
- `SensitiveSurfacePolicyTest`: sensitive surfaces participate in the optional protection policy.
- `ServerEditorUiTest`: actual Compose name-entry/Save regression, Test action, sequential field edits, valid/invalid quick-connect button interactions and editor/discard-dialog secure flags.
- `ScreenshotProtectionUiTest`: default-off policy, nested flag lifetime, live preference changes, ordinary screens and accessible/persistent Settings toggle (Robolectric API 33).
- Existing refresh, socket cancellation, HTTP pinning, storage, parser and localization tests remain enabled.

## Device acceptance checklist — not executed in the workspace

Use Persian and English, portrait and landscape, small phones and a wide tablet:

1. Launch with no servers: only one Servers destination, clear empty state and a working Add server action.
2. Add via quick-connect and manually; Test Agent, Save, then verify the new server inspector and live polling. Invalid port/threshold/token/pin must not save.
3. Edit server A. Back/Close/outside dismissal asks before discarding. Cancel dismissal keeps all input. Rotate during editing: draft remains. Save changes A only. With default settings, screenshots work and token fields remain masked. Enable the optional protection and verify sensitive Activity/dialog screenshots and Recents are blocked; disable it and verify capture works again, including after leaving/reopening the form.
4. With A's form open, change/delete A using another local record source. Save must report a conflict rather than overwrite or resurrect it. If an unrelated server B was added, saving A preserves B.
5. Search/filter, open A, launch Docker/SSH and press Back. A's inspector and the list's previous search/filter/scroll state remain. Closing the inspector returns to the list.
6. Open A, then select B on a wide display. Close returns to the list, not a chain of inspectors.
7. Delete a server: Cancel changes nothing; confirm removes only the named local record and its stale navigation scope. Do not disconnect/uninstall remote services.
8. Refresh all, then a single server. Verify real progress and completion/error labels with reachable, offline and slow Agents. Existing data must not produce an immediate fake success.
9. Rotate with a phone inspector open: wide mode shows it beside the list, narrow mode shows the sheet. Back dismisses the form/inspector before leaving the workspace.
10. Deliver an internal server notification while an editor is dirty: the draft is not discarded. Once editing ends, the pending notification may open its server.
11. Corrupt/unavailable encrypted storage must show an explicit read error and disable adding through that failed load, not present an apparently empty fleet ready to overwrite data.
12. Root Back still uses the two-press exit hint. Opening a panel, help or refreshing disarms an old exit hint.

## Build and signing

Workspace APKs are debug-signed, not production releases. Do not uninstall an existing signed installation to bypass a signature mismatch; that can destroy its Keystore-backed data. Use an isolated test profile/device or the existing trusted CI signing pipeline.

## 2026-09-19 regression repair

The actual Compose dialog on base `52534a9` showed an entered name but Save still reported NAME required. An unchanged UI regression test failed before and passed after the fix. Local function references could retain the immutable draft from an earlier composition. Save, Test and Import now read the ViewModel at click time, and field callbacks transform its latest draft instead of copying a captured record. Import is now a filled, full-width button with an icon, distinct disabled colors and a minimum 48 dp height.

Verification: 384 tests, 381 passed, 3 intentionally skipped, 0 failures/errors; all 11 new Robolectric/Compose tests passed. `lintDebug`: 0 errors, 51 pre-existing warnings, 5 information items. `assembleDebug` and APK v2 signature verification passed. Robolectric checks real Compose interactions and Android window flags, **not physical-device screenshot capture**. Successful end-to-end saving against device Keystore, live Agent Test/polling and physical rotation remain device acceptance items.

On a memory-constrained runner, compile `compileDebugUnitTestKotlin` first, then run `testDebugUnitTest` in a separate Gradle process with a smaller daemon heap (256 MB here; test worker 384 MB). This avoids concurrent compiler and Robolectric memory pressure. CI may use its usual commands on a larger runner.

## Radar scope regression — 2026-09-19

A saved server and a selected tool scope are different. The old Radar empty-state action navigated to the hub, while the scope dropdown navigated to the server inspector. A real Compose shell test on `8f67d8c` reproduced leaving Radar after pressing Select server, despite an existing fleet.

- Radar now automatically selects the only available server. With several servers, it preserves a valid current selection; otherwise the user explicitly chooses one. A stale ID is not treated as a usable connection.
- Select server opens a connection picker **over the current tool**. Selecting/dismissing it does not navigate to the hub or add a Back-history entry. The phone/tablet scope bar updates the current tool in place. Only an explicitly chosen Add action in a genuinely empty picker opens the connection editor.
- A storage read failure shows Retry, not Add. The picker never exposes token fields. The Radar selection action uses a server icon rather than the misleading plus icon.
- Radar's server-bound composition is keyed to the connection; switching it recreates local callbacks/state and disposes the previous coroutine scope. Other server-bound consumers of the shared scope selector also recreate their content on scope changes. Hub list/filter state is not keyed to the selected server.
- `RadarNavigationUiTest`: seven Robolectric API 33 full-shell scenarios (phone and tablet, one/multiple/no servers, dropdown switch/Back, cancellation and read failure). Saved connection fixtures are supplied through the shell's loader parameter; production still defaults to encrypted `Prefs.loadServersResult`. Test pins are intentionally missing so no live Agent request is sent.
- Four additional `CommandNavigationTest` cases cover in-place scope/history, clear scope, sole-server/invalid-ID resolution, and scope behavior outside Radar.

Verification: **395 tests, 392 passed, 3 intentionally skipped, zero failures/errors**. Lint: 0 errors, 51 existing warnings, 5 information items. Debug build and APK v2 signature verification passed. Physical-device interaction, encrypted on-device save, and live `/api/probe` operations remain manual acceptance items; automated UI tests are not a live Agent connectivity claim.

Device checklist: with one saved server open Radar from the menu; it must show that server's target form. With two servers choose A, then B in the top selector; remain in Radar and ensure refresh/target operations use B. Back must restore the real origin. Test Cancel and Android Back on the picker. With no servers only explicit Add opens the editor. Repeat on phone/tablet, Persian/English, rotation and a slow/offline Agent.
