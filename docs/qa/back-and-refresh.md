# Back navigation and refresh regression checks

## Contract

- Back pops actual visited destinations, including the selected server ID. It does not guess a workspace parent.
- Visiting an existing destination pops to it; repeated menu taps and editor completion must not create loops.
- The shell displays the next Back destination. Hardware/gesture and in-page Back use the same handler.
- Help/navigation overlays close first. Only the overview root can arm the two-second exit hint; navigation clears that hint. The timer uses elapsed realtime.
- History is saved across recreation as route keys and server IDs only (no credentials), and capped at 64 entries.
- Shell refresh exists only on overview, fleet, incidents and server dossier. Dossier refresh targets that server; the other three refresh fleet metrics.
- Refresh feedback is driven by the actual batch, not by old cached data or an animation timer. Repeated taps coalesce. Empty, running, complete, partial, failed, interrupted and timed-out outcomes are distinct.
- A batch has at most four concurrent probes and a 30-second timeout. Metrics cancellation cancels the OkHttp call. Automatic and manual metrics requests share per-server locks; Repo updates are atomic.
- Docker/process loaders show progress even when old results are visible. Tunnel refresh also reports batch outcomes and merges health into the latest saved configuration.
- Other tools retain their own operation-specific actions; shell refresh must not imply that DNS, a scan or an SSH command was re-run.

## Automated verification

Run using Java 17, Gradle 8.7, Android SDK 34:

```sh
gradle testDebugUnitTest lintDebug assembleDebug --no-daemon
```

For low-memory environments, use one worker and the in-process Kotlin compiler rather than changing project build settings:

```sh
gradle testDebugUnitTest --no-daemon --max-workers=1 \
  -Dorg.gradle.jvmargs='-Xmx640m -XX:MaxMetaspaceSize=256m -XX:ReservedCodeCacheSize=96m -XX:+UseSerialGC -Dfile.encoding=UTF-8' \
  -Pkotlin.compiler.execution.strategy=in-process
```

Tests: `CommandNavigationTest`, `ManualRefreshTest`, `RefreshIntegrationTest`; existing client-pinning, copy coverage and other unit tests also run.

## Device acceptance checklist (not yet executed in the workspace)

Test both Persian/RTL and English/LTR, phone widths and tablet/landscape, hardware and gesture Back:

1. Overview → server A → Docker → Back: return to server A, then Overview.
2. Incidents → server A → Processes → Back: return to A, then Incidents (not Fleet).
3. Fleet → server A → server B using selector → Back: restore server A.
4. Uptime → editor → Back: return to Uptime, not Radar. Repeat for DNS, Network Tools and Tunnels.
5. Open the menu or help on a detail screen. Back closes the overlay without moving the underlying page.
6. Rotate a detail screen. The back destination/server scope survives. A hidden phone menu must not intercept Back on a tablet layout.
7. On the overview root, first Back shows the exit hint; second Back within two seconds exits. Opening another page/help or refreshing must disarm the old hint.
8. Refresh with no servers: show nothing to refresh, not success.
9. Refresh one reachable agent: spinner immediately, then completion/time after the response. Rapid taps must not queue extra batches.
10. Refresh a mixed online/offline fleet: report successes and failures separately. A non-responsive agent eventually times out; never show all-success while targets remain pending.
11. Rotate during a metrics refresh: it continues in the process-level coordinator and the new UI observes the same state.
12. Refresh Docker/process lists while old data is visible: progress stays visible, completion time changes, and errors are explicit.
13. Refresh tunnels, then leave the page or delete/edit a tunnel: no stuck UI, resurrected deleted tunnel, or overwritten configuration.

## Distribution

The locally generated APK is debug-signed, not a trusted production release. Do not uninstall an existing app to bypass a signature mismatch: that can delete Keystore-backed credentials. Test on a separate device/profile or build through the repository's existing trusted signing pipeline. Push/release requires a secure authenticated GitHub connection; no chat-posted credential is included in source, logs or patches.
