# Automatic monitoring — actual foreground-service controls

## Scope and user flow

Fixes the missing start control identified during the contextual-help review at `32d9645`.

- Open Uptime → the **Automatic monitoring / پایش خودکار** card.
- Add and manually test a target if needed, then explicitly choose **Start monitoring**.
- Android 13+ notification permission is requested at that point, not on every initial app launch. Denial does not pretend to stop monitoring: checks can run while a visible warning explains that phone notifications may be hidden. A button opens app notification settings; returning to the app rechecks permission/channel availability.
- The card distinguishes off, starting, on, stopping and failed. A start request does not optimistically claim that the service is running.
- Stop from either the card or the ongoing notification. Targets are retained, and manual tests remain available.
- This control covers background monitoring of **all saved servers and active uptime targets**, not merely the currently selected server. Foreground server-page refresh/polling remains independent.
- Help and the editor's saved message now explain the real start path instead of the former missing-control limitation.

No Agent route or Agent upgrade is required for this fix. No automatic monitoring starts on page entry.

## Lifecycle and correctness

`MonitoringState` holds requested transitions separately from authoritative service state. Start is confirmed only after foreground entry and engine startup; stop remains pending until destruction unless Android reports no service. Startup, runner and stop errors are shown without raw exception/credential text. Duplicate start requests are ignored.

`MonitorService` retains the single process-wide `PollingCoordinator` and the existing bounded `UptimeEngine`; it does not add a second fleet poller. The non-exported service has a notification stop action. Explicit stop is saved before asking Android to stop, so a stale start or sticky restart cannot override it. A sticky restart can resume previously requested monitoring; app/page entry and reboot do not silently start a new session. No boot receiver was added.

The runner now:
- owns in-flight IDs per run rather than allowing old cancelled workers to modify a restarted run's ID set;
- avoids reading a nullable global scope when queuing work;
- clears its actual running state on exit and reports unexpected scheduler failure to the service after cleanup;
- propagates cancellation rather than recording cancellation itself as a target outage;
- checks coroutine activity before publishing a result;
- checks copies of targets and publishes replacement snapshots, so `StateFlow` emits scheduled changes instead of suppressing in-place mutations;
- does not republish a stale result into a removed/edited/paused target record;
- returns the actual manual `Heartbeat` to the editor rather than reading the old target object.

Existing target assessment criteria, interval clamping (5–3600 seconds), maximum five concurrent uptime checks, paused-target scheduling and recent-history storage are preserved.

## Verified locally

- Kotlin app/unit-test compilation: passed.
- Full Android suite: **458 tests, 455 passed, 3 existing live skips, zero failures/errors**.
- Added **20 tests**: 7 pure transition tests, 5 Robolectric service/notification lifecycle tests, 5 Compose control tests and 3 runner tests.
- A real **loopback-only TCP listener** was used to verify repeated scheduled checks, observable replacement snapshots, paused targets and no further scheduled results after stop. No internet destinations or user servers were contacted.
- Foreground notification/action, explicit-stop persistence, stale-start refusal, sticky restart gating, lifecycle destruction, disabled notification channels, retry/error UI and pending permission controls were exercised under Robolectric or Compose.
- Localization coverage now includes `MonitorService.kt`, which renders translated notification text; unused-key checks remain enforced.
- `lintDebug`: **0 errors, 51 warnings, 5 information**.
- `mergeExtDexDebug`, `assembleDebug`: passed; APK v2 signature verified, one signer.

## Honest limits and remaining manual acceptance

This is not a claim of uninterrupted 24/7 Android monitoring. Force-stop, reboot, OEM battery policies, Android foreground-service constraints and loss of connectivity can interrupt or delay checks. Already-blocking DNS/socket calls may take time to unwind; stop is not instantaneous cancellation of the network stack.

Still test on a physical Android 13/14+ phone:

1. Start with notification permission allowed, denied and permanently denied; inspect actual Android dialogs and notification drawer/task-manager behavior.
2. Disable an individual alert channel, open settings from the card, re-enable it and return.
3. Start a saved monitor, leave the page/app, lock the phone and verify checks under the device's battery policy.
4. Stop from the notification and verify the card and target history after reopening; repeat rapid stop/start.
5. Force-stop/reboot and confirm the UI never claims a saved preference alone proves running.
6. Trigger genuine service-start rejection and verify retry from an active app.

No physical-device, OEM battery, TalkBack or private-Agent validation was performed here.
