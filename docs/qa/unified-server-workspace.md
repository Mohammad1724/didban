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
- Credentials are masked and both the Activity and dialog are protected with secure-window flags.
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
- `SensitiveSurfacePolicyTest`: secure editor surface.
- Existing refresh, socket cancellation, HTTP pinning, storage, parser and localization tests remain enabled.

## Device acceptance checklist — not executed in the workspace

Use Persian and English, portrait and landscape, small phones and a wide tablet:

1. Launch with no servers: only one Servers destination, clear empty state and a working Add server action.
2. Add via quick-connect and manually; Test Agent, Save, then verify the new server inspector and live polling. Invalid port/threshold/token/pin must not save.
3. Edit server A. Back/Close/outside dismissal asks before discarding. Cancel dismissal keeps all input. Rotate during editing: draft remains. Save changes A only; secrets are not visible in screenshots or Recents.
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
