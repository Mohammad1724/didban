# Didban · Obsidian Zenith Design System

_version 2.0 — introduced with v0.8.0_

A hyper-refined, minimalist, telemetry-grade visual language for server observability and DevOps command centers. Deep obsidian surfaces, delicate micro-hairlines, luminous electric accents, and precision typography (Vazirmatn + Inter + JetBrains Mono).

---

## 1. Concept & Philosophy

ديدبان (*Didban* = *Sentinel / Watchman*) operates as an observatory console:

1. **Quiet Obsidian Canvas, Loud Signals** — Deep, noise-free backgrounds (`#07090E`) allow status pulses, metric rings, and telemetry charts to shine without visual fatigue.
2. **Structure through Micro-Hairlines** — 1dp delicate borders define geometry instead of heavy shadows or bulky borders.
3. **Numbers as Telemetry** — Every metric, IP address, port, PID, hash, and latency renders in JetBrains Mono with tabular numerals (`tnum`) for jitter-free real-time refreshes.
4. **Bidirectional Type Perfection** — Native typography automatically applies Vazirmatn for Persian/RTL and Inter for Latin/LTR locales.

---

## 2. Color Tokens

| Token | Obsidian (Dark) | Platinum (Light) | Usage |
|---|---|---|---|
| `canvas` | `#07090E` | `#F8FAFC` | Root app background (+ faint ambient top glow) |
| `surface` | `#0F131D` | `#FFFFFF` | Primary cards, panels, and floating containers |
| `surfaceElevated` | `#151C28` | `#F1F5F9` | Elevated modals, popups, and dialogs |
| `surfaceLow` | `#0A0D14` | `#F1F5F9` | Sunken wells, inputs, code boxes, segmented wells |
| `surfaceHighlight` | `#1C2536` | `#E2E8F0` | Active chip fills, pressed states |
| `hairline` | `#171F2C` | `#E2E8F0` | 1dp structure and container border |
| `hairlineStrong` | `#26334A` | `#CBD5E1` | Focused border for active controls |
| `textPrimary` | `#F1F5F9` | `#0F172A` | Primary titles, active values, headers |
| `textSecondary` | `#94A3B8` | `#475569` | Body copy, secondary metadata, descriptions |
| `textTertiary` | `#54627A` | `#94A3B8` | Micro-captions, hints, unit labels |
| `accent` | `#00E5FF` | `#0284C7` | Signature interactive electric cyan |
| `accentDim` | `10% #00E5FF` | `10% #0284C7` | Soft interactive highlight washes |
| `violet` | `#A855F7` | `#7C3AED` | Secondary telemetry hue (RAM / Memory) |
| `ok` | `#10B981` | `#059669` | Healthy status / Online / Running |
| `warn` | `#F59E0B` | `#D97706` | Warning / High CPU / Attention |
| `danger` | `#EF4444` | `#DC2626` | Down / Critical spike / Kill action |
| `info` | `#3B82F6` | `#2563EB` | Network info / Sockets |
| `track` | `#151C28` | `#E2E8F0` | Gauge & progress tracks |

All tokens live in `Theme.kt` (`DidbanPalette`) and are read in Compose via the `Ds` accessor object (e.g. `Ds.accent`, `Ds.surface`, `Ds.hairline`).

---

## 3. Typography

- **Vazirmatn** (400, 500, 600, 700) — Bundled in `res/font/`. Used for all RTL/Persian interface elements.
- **Inter** (400, 500, 600, 700) — Bundled in `res/font/`. Used for LTR English interface elements.
- **JetBrains Mono** (400, 500, 600, exposed as `Telemetry`) — Used for all numbers, PIDs, ports, IPs, bandwidth rates, hashes, and terminal commands.

---

## 4. Signature Components

| Element | Component | Role |
|---|---|---|
| **Sentinel Radar** | `RadarMark` | Multi-ring rotating radar sweep with beacon echoes |
| **Status Pulse** | `PulseDot` | Breathing halo indicator for live nodes & services |
| **Bento Metric** | `BentoMetricTile` | 3-column micro telemetry pod displaying real-time fleet aggregates |
| **Bento Guide Card** | `StepByStepGuideCard` | 3-step interactive onboarding card with 1-click terminal installer |
| **Server Bento Card** | `ServerBentoCard` | Hyper-detailed server card with live CPU sparklines, country flags, and latency pills |
| **Ring Gauge** | `RingGauge` | Circular telemetry gauge with animated sweep |
| **Sparkline** | `Sparkline` | Smooth cubic spline with vertical gradient drop fill |
| **Heartbeat Rhythm** | `HeartbeatBar` | 30-pulse SLA status strip for uptime monitors |
| **Terminal Box** | `TerminalBox` | Traffic light terminal container with 1-click copy feedback |
| **Segmented Control** | `SegmentedControl` | Sunken well pill switcher with animated selection pill |
| **Liquid Spotlight Dock** | `LiquidSpotlightDock` | VisionOS-inspired floating dock with sliding liquid spotlight capsule & glyph bounce |

---

## 5. Rules for Contributors

1. Never hardcode `Color(0x...)` — always read from `Ds`.
2. Numeric telemetry text must always use `Telemetry` (`fontFamily = Telemetry`).
3. Maintain symmetric support for both RTL (Persian) and LTR (English) layouts.
4. Keep cards and surfaces calm; let color signify real-time state and metrics.
