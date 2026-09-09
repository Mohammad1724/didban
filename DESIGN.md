# Didban · Nightwatch Design Language

_version 1.0 — introduced with v0.7.0_

A calm, precise "observatory" aesthetic for a server command center:
deep space surfaces, hairline structure, **one luminous accent**, and
telemetry-grade typography. Dark-first, daylight-ready, fully RTL.

---

## 1. Concept

دیدبان means *sentinel / watchman*. The UI behaves like one: a quiet radar
console that only lights up when something matters. Three rules drive every
screen:

1. **Calm surfaces, loud signals** — backgrounds and cards stay quiet; color is
   reserved for meaning (status, danger, interaction).
2. **Structure through hairlines, not shadows** — 1dp borders define geometry.
   Elevation is expressed by *surface tone steps*, never drop shadows.
3. **Numbers are telemetry** — every metric renders in JetBrains Mono so digits
   never jitter as values refresh.

## 2. Color tokens

| Token | Nightwatch (dark) | Daylight (light) | Use |
|---|---|---|---|
| `canvas` | `#0A0D13` | `#F4F6FB` | app background (+ faint accent aurora at top) |
| `surface` | `#12161F` | `#FFFFFF` | cards & panels |
| `surfaceLow` | `#0D1017` | `#EEF2F8` | wells: inputs, terminal, segmented control |
| `surfaceHigh` | `#1A2030` | `#E7ECF4` | elevated chips / pressed states |
| `hairline` | `#1E2536` | `#E3E9F2` | 1dp structure borders |
| `hairlineStrong` | `#2A3448` | `#C9D3E2` | emphasized borders |
| `textPrimary` | `#E9EDF5` | `#141B27` | headings & values |
| `textSecondary` | `#94A0B4` | `#5A667A` | body & labels |
| `textTertiary` | `#5C6678` | `#8B96A9` | hints & micro-labels |
| `accent` | `#22D3EE` | `#0891B2` | the single interactive hue |
| `accentDeep` | `#0891B2` | `#0E7490` | gradient partner of accent |
| `accentDim` | 10% cyan | 10% cyan | soft accent washes |
| `onAccent` | `#052730` | `#FFFFFF` | content on accent fills |
| `ok` | `#34D399` | `#059669` | healthy status |
| `warn` | `#FBBF24` | `#D97706` | degraded / attention |
| `danger` | `#FB7185` | `#E11D48` | down / destructive |
| `info` | `#7DA9FF` | `#2563EB` | informational |
| `violet` | `#A78BFA` | `#6D28D9` | secondary metric hue (RAM) |
| `track` | `#202839` | `#E2E8F1` | gauge & progress tracks |

All tokens live in **`Theme.kt`** (`DidbanPalette`) and are read in composition
via the `Ds` accessor object — e.g. `Ds.accent`, `Ds.hairline`. No raw hex
values are allowed in screens.

Semantic mapping onto Material 3 (`primary = accent`, `outline = hairlineStrong`,
…) means any stock M3 component automatically matches the language.

## 3. Typography

- **Vazirmatn** (400/500/600/700) — UI text in RTL/Persian, bundled in
  `res/font/`. Selected automatically via `AppFontFamily` based on
  `LocalLayoutDirection`, so Persian screens render in a real Persian
  typeface while Latin screens stay Inter.
- **Inter** (400/500/600/700) — UI text in LTR locales, bundled in `res/font/`.
- **JetBrains Mono** (400/500/600, exposed as `Telemetry`) — every number,
  PID, port, endpoint, hash and command. Monospaced digits = zero jitter.

Key styles: screen title 21/`Bold`/−0.3 tracking · page header 17/`Bold` ·
section label 11/`Bold`/+0.9 tracking tertiary (`SectionLabel`) · body 13–14 ·
micro 10.5.

## 4. Spacing & shape

- 4dp grid; screen gutters **20dp**; card padding **14–16dp**; section gaps ~12dp.
- Radii: cards **18**, tiles/inputs **12–14**, pills & segmented control **999/13**,
  dialogs 24 (M3 extraLarge).
- Lists use **hairline dividers between rows on canvas**, not card-per-row —
  quieter, denser, faster to scan.

## 5. Signature elements

| Element | Where |
|---|---|
| **RadarMark** — animated radar (rings, rotating sweep, echoing blips) | app header, empty states, launcher icon |
| **PulseDot** — breathing halo for live status | server cards, monitors, containers |
| **SegmentedTabs** — sunken well with raised keys | dashboard, sockets, dialogs |
| **TerminalBox** — traffic lights + mono body + copy chip | install commands, tunnel code |
| **HeartbeatBar** — 30 rounded pulse bars | uptime monitors |
| **Sparkline** — smooth curve, gradient fill, luminous endpoint | 24h CPU/RAM telemetry |
| **Gauge** — animated arc with glowing endpoint dot | overview hero card |
| **Banner / FeatureGuideCard** — tinted, hairlined, quiet | notices & guides |
| **EmptyState** — radar or glyph + one hint + one action | every empty surface |

## 6. Motion

Subtle and purposeful: radar sweep 4.2s linear · status pulse ~1.1s ·
gauge/bars/chart reveal 0.7–0.9s `FastOutSlowInEasing` · chip/tab color
transitions ~0.2s. Nothing bounces; nothing distracts.

## 7. Rules for contributors

1. Never hardcode `Color(0x…)` in screens — read from `Ds`.
2. Numeric text always uses `Telemetry` (`fontFamily = Telemetry`).
3. One accent color only; status colors carry meaning, never decoration.
4. New surfaces come from the existing steps (`surface`/`surfaceLow`/`surfaceHigh`)
   — do not invent new greys.
5. Both themes and both layout directions (fa/RTL, en/LTR) must look right.
