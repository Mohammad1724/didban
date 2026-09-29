# Didban — performance & build audit (2026-09-29)

Every item below was measured against the tree at `13f12bc`. Commands are
reproducible from the repo root unless noted.

## Snapshot

| Metric | Value |
| --- | --- |
| Kotlin sources (`app/src/main`) | 105 files, 42,225 lines (incl. tests) |
| `@Composable` functions | ~200, heaviest files `Ui.kt` (37), `Theme.kt` (35), `CommandUi.kt` (30) |
| Biggest files | `CommandTokens.kt` 3,169 · `TunnelEngine.kt` 2,107 · `Ui.kt` 1,375 · `DpiEngine.kt` 1,112 |
| `res/` total | 2.1 MB — of which **2.0 MB is `res/font`** |
| Release APK (CI artifact) | 3.7 MB |
| Tests | 636 tests / 0 failures / 1m37s |
| AGP / Gradle / Kotlin / Compose | 8.5.2 / 8.7 (no wrapper) / 1.9.24 / compiler 1.5.14, BOM 2024.06.00 |
| compileSdk / targetSdk | 34 / 34 |

---

## 1. `targetSdk 34` — a release blocker, not an optimization

Google Play has required **API 36 (Android 16)** for all new apps *and updates*
since **2026-08-31**. Today is 2026-09-29, so the app is currently below the bar
that Play enforces. Apps below API 35 also stop being served to new users on
newer devices. A Play Console extension is available to 2026-11-01.

Raising `compileSdk` past 34 also forces the toolchain up (AGP 8.5.2 caps out at
compileSdk 35), which is where this item pays for itself twice: see §2.

Android 16 behaviour changes to expect: edge-to-edge is enforced (the opt-out is
gone), predictive back, and full-screen notifications need
`USE_FULL_SCREEN_INTENT`.

## 2. Toolchain: AGP 8.5.2 / Kotlin 1.9.24 / Compose compiler 1.5.14

Kotlin 2.x + the Compose compiler Gradle plugin gives **strong skipping** —
composables with unstable parameters become skippable instead of recomposing
every time. For a 42k-line, ~200-composable app this is the single largest
free runtime win available, and it is also a prerequisite for §1.

Compose BOM `2024.06.00` is more than two years old.

## 3. 2.0 MB of fonts that nothing references — fixed

```
$ ls -la app/src/main/res/font/      # at audit time: 11 files, 2,036 KB
inter_400/500/600/700.ttf     ~325 KB each
jbmono_400/500/600.ttf        ~112 KB each
vazir_400/500/600/700.ttf     ~105 KB each

$ grep -rn "R\.font\." app/src/main/java --include=*.kt     # (no matches)
$ grep -rn "font"      app/src/main/res/values              # (no matches)
```

`Theme.kt` deliberately uses platform families instead:

```kotlin
val Inter: FontFamily      = FontFamily.SansSerif
val Telemetry: FontFamily  = FontFamily.Monospace
val Vazirmatn: FontFamily  = FontFamily.Default
```

So the fonts were dead weight in every clone and every build input, and if
resource shrinking misses them they are 2 MB of the 3.7 MB APK.

**Fixed.** All 11 files plus `app/FONT_LICENSES.md` are deleted; `app/src/main/res`
went from **2.1 MB to 45 KB**. History confirms the switch was deliberate:
commit `5360cc2` ("fix(stability): eliminate font loading runtime crashes")
replaced the bundled families with platform ones and left the files behind. If
Persian typography is ever wanted back, Vazirmatn is the one family that would
visibly change the UI — add just it, not all three.

## 4. The ambient aurora redraws the whole screen every frame, forever

`CommandGlass.kt:197` `Modifier.commandAtmosphere()` is attached at the **root**
of three entry points:

- `MainActivity.kt:134` (crash-recovery shell)
- `CommandShell.kt:357` (main app shell)
- `CommandUi.kt:89`

It is well written — brushes are built in `drawWithCache` and only the drift
value is read in the draw phase, so nothing recomposes. But the draw phase still
runs **5 full-screen radial gradients, every frame, at 60 fps, indefinitely**:

```kotlin
internal object CommandAurora {
    const val periodMs = 30_000   // one full loop
    const val driftDp  = 9f       // total travel
}
```

9 dp over 30 s is **0.3 dp per second** — 1,800 frames to move nine pixels'
worth of gradient.

**Fixed.** The drift is quantized with `CommandAurora.quantize()`: the raw
animation phase is wrapped in a `derivedStateOf`, so a *new* value is published
only when the step changes. Measured step sizes (worst case over both harmonic
axes, in dp and in pixels at 3x density):

| steps per period | repaint every | fps | jump per step | px @3x |
| --- | --- | --- | --- | --- |
| 1800 (unquantized) | 17 ms | 60 | 0.06 dp | 0.19 px |
| **375 (chosen)** | **80 ms** | **12.5** | **0.30 dp** | **0.90 px** |
| 180 | 167 ms | 6 | 0.63 dp | 1.88 px |
| 60 | 500 ms | 2 | 1.87 dp | 5.60 px |

375 rather than a rounder 360 because it divides the 30 s period exactly, so
every step lasts the same 80 ms. Result: **~79% fewer full-screen repaints**
with a sub-pixel step, i.e. no visible change to the motion. Three tests in
`CommandEmeraldThemeTest` lock the budget, the sub-pixel step and loop closure.

Lifecycle note: no gating was needed for the background case — when the window
is not visible Android stops dispatching frames, so the loop already stops.

For an app designed to sit in a foreground service for hours this is the most
direct battery and thermals win in the list. Note `useReduceMotion()` is already
respected — the hook for options 2 and 3 exists.

## 5. Bouncy Castle is inserted on the main thread in `Application.onCreate` — fixed

```kotlin
// DidbanApplication.kt
CryptoSecurity.ensureInitialized()      // -> Security.insertProviderAt(BouncyCastleProvider(), 1)
```

Measured on a JVM with the project's own dependency (bcprov-jdk18on 1.78.1):

```
new BouncyCastleProvider()  : 250-255 ms      (4207 services registered)
insertProviderAt(p, 1)      :  24-57 ms
total, cold                 : 275-312 ms
total, warm (2nd insert)    :   7-11 ms
```

On a device this is *worse*, not better — the same work plus DEX loading and
verification, on a slower CPU. It was also the very first thing the process did,
before the first frame existed.

**Fixed**, in three parts:

1. `DidbanApplication` warms the provider up on a `didban-crypto-warmup`
   background thread, so activity creation and the first composition run
   without waiting for it.
2. `CryptoSecurity.ensureInitialized()` now builds the provider *outside* the
   monitor and only the remove/insert swap runs under it. A caller that arrives
   mid-warm-up waits tens of milliseconds instead of a quarter of a second.
3. Every call site that depends on Bouncy Castle asks for it itself —
   `EncryptedVault`, `SecureCipher` (and SSH/SFTP, which already did). So no
   crypto path can observe the few-millisecond window between
   `removeProvider("BC")` and `insertProviderAt(...)`, and nothing can silently
   be served by a substituted provider. Verified by a 30-run race check: four
   concurrent callers at five different offsets into the warm-up, every one got
   Bouncy Castle and none threw.

The first tick of `PollingCoordinator` is also deferred by one interval (2 s):
on a cold start every server is due at once, and probing them all in the same
instant as the first composition competed with the UI for I/O. Manual refresh
is unaffected.

What is *not* on the startup path: `Prefs.loadServersResult` reads through
`SecureStorage` (AndroidKeyStore + AES-GCM), not `EncryptedVault`, so the
600k-iteration PBKDF2 below is never paid at start-up — only when the vault or
notes are opened.

## 6. Build and CI

- **No Gradle wrapper.** CI installs Gradle 8.7 via `gradle/actions/setup-gradle`;
  local builds have no pinned version at all.
- `gradle.properties` sets only `org.gradle.jvmargs=-Xmx2048m` — no `parallel`,
  no `caching`, no configuration cache.
- `android.yml` `verify` job: `timeout-minutes: 45`. Test + lint run serially in
  one job; they are independent and could be two jobs.
- The test worker was already raised to `1536m` / `maxParallelForks = 2`
  (42+ min → 1m37s), so the remaining time is compile + lint, not tests.
- `material-icons-extended` (thousands of ImageVectors) is a dependency while
  only **49 distinct icons** are referenced in the whole app.

## 7. Compose stability: zero `@Stable`, zero `derivedStateOf`

```
$ grep -rn "@Stable\|@Immutable" app/src/main/java --include=*.kt | wc -l   # 0
$ grep -rn "derivedStateOf"      app/src/main/java --include=*.kt | wc -l   # 0
```

Good news: all 19 `items()` calls in lazy lists pass a `key`. Bad news: with
Kotlin 1.9.24 there is no strong skipping and no stability-config file, so every
unstable parameter (a `List`, a data class with a `var`, a lambda capturing
state) defeats skipping. Once on Kotlin 2.x, enable the Compose compiler metrics
and fix the top offenders rather than guessing.

Not a problem found: `HttpClientPool` already shares one `OkHttpClient` per
(fingerprint, host, port) key with a bounded LRU, `PollSchedule` already backs
off failing servers, and there is no `runBlocking` anywhere in `main`.

---

## 8. Measured and rejected: PBKDF2 is *not* slower under Bouncy Castle

`EncryptedVault` derives its key with PBKDF2-HMAC-SHA256 at 600,000 iterations
(OWASP's 2023 minimum), which costs ~600 ms per call here. Since Bouncy Castle
sits at provider priority 1, it serves that derivation — and on a device its
pure-Java PBKDF2 should be slower than the platform's native one.

A first benchmark said exactly that: **39 ms (SunJCE) vs 95 ms (BC)** per
100k iterations, 2.4x. It was wrong. Re-measured with both providers in the
*same* JVM, alternating, minimum of 7 rounds:

```
100k iterations   SunJCE  99.9 ms    BC 108.2 ms    1.08x
200k iterations   SunJCE 197.1 ms    BC 192.1 ms    0.98x
600k iterations   SunJCE 603.2 ms    BC 636.1 ms    1.05x
```

The 2.4x was CPU noise in the sandbox (the two providers were benchmarked in
separate JVM runs, minutes apart). Within 5% there is nothing to win by
preferring another provider for PBKDF2, so no change was made. The output is
identical across providers both ways — verified, and the vault round trip passes
under SunJCE *and* under BC — which is why the provider choice there is a
performance question only, never a correctness one.

Worth keeping an eye on anyway: ~600 ms per vault decrypt is real work, and
`loadVaultNotes` / `verifyMasterPassword` call it on whichever thread the screen
uses. Moving those off the main thread is a separate, larger change than the
start-up work above.
