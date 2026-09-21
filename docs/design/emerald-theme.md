# Emerald identity — سبز زمردی جای آبی (۲۰۲۶-۰۹-۲۱)

جهت مالک پروژه: «سبز زمردی جایگزین بشه»، «یه کم انیمیشن داشته باشه».
این سند فقط لایهٔ **تم** را توصیف می‌کند؛ بازچینش چیدمان مرحلهٔ بعد است.

## چه چیزی عوض شد

| فایل | تغییر |
|---|---|
| `CommandTokens.kt` | `CommandLightPalette` و `CommandDarkPalette` کامل بازنویسی شدند؛ چهار توکن تازه اضافه شد: `gold`, `onGold`, `heroTop`, `heroBottom` (و اکسسورهای `CommandColors.*`). |
| `CommandGlass.kt` | orb های `commandAtmosphere()` از بنفش/آبی/صورتی به زمرد/طلایی/یشمی تغییر کردند و یک **دریفت آرام ۳۰ ثانیه‌ای** اضافه شد (با احترام به reduce-motion). |
| `CommandEmeraldThemeTest.kt` | تست تازهٔ قرارداد تم (سبز بودن accent، کنتراست طلایی، گرادیان هیرو، ریاضیِ انیمیشن). |

`CommandTheme` و امضای پالت دست‌نخورده ماند؛ هیچ صفحه‌ای نیاز به تغییر نداشت.

## پالت

### روشن — Emerald daylight
`canvas` `#F3F5F1` · `surface` `#FFFFFF` · `border` `#D5E0D8` · `borderStrong` `#A2B4A8`
`textPrimary` `#16241C` · `textSecondary` `#46584E` · `textTertiary` `#5E7266`
`accent` `#0C6B4E` · `onAccent` `#FFFFFF` · `focus` `#0C6B4E`
`gold` `#7B5D13` · `onGold` `#FFFAEC` · `heroTop` `#17724F` → `heroBottom` `#00402C`
`success` `#167B52` / `#E7F4ED` · `warning` `#B45309` / `#FDF3E2` · `danger` `#C9342F` / `#FCEEF0`
`info` `#0F7079` / `#E4F1F2` · `violet` (تله‌متری حافظه) `#A87A12` · `track` `#DFE8E1`

### تیره — Emerald night
`canvas` `#07100D` · `surface` `#121F19` · `surfaceRaised` `#18271F` · `border` `#2A3A32` · `borderStrong` `#5D7A6C`
`textPrimary` `#EFF5F1` · `textSecondary` `#D3E0D8` · `textTertiary` `#C2D2C8`
`accent` `#7DE7B6` · `onAccent` `#04211A` · `focus` `#7DE7B6`
`gold` `#D6AE4A` · `onGold` `#241A04` · `heroTop` `#0F5431` → `heroBottom` `#032418`
`success` `#54C88F` / `#16311F` · `warning` `#F0833C` / `#3A2A16` · `danger` `#FF8A84` / `#3A1F1E`
`info` `#57CFDA` / `#12312F` · `violet` `#E3BE63` · `track` `#24352C`

### نگاشت از پالت آبی قبلی

| نقش | قبل (روشن) | بعد (روشن) | قبل (تیره) | بعد (تیره) |
|---|---|---|---|---|
| accent | `#2364A6` | `#0C6B4E` | `#BFE9F4` | `#6FE0AF` |
| canvas | `#E9E3D8` | `#F3F5F1` | `#151131` | `#07100D` |
| surface | `#FCFDFE` | `#FFFFFF` | `#19222C` | `#121F19` |
| violet (RAM) | `#2364A6` | `#A87A12` | `#BFE9F4` | `#E3BE63` |
| success | `#176B50` | `#167B52` | `#92D5B9` | `#54C88F` |
| warning | `#8C5B15` | `#B45309` | `#EDC787` | `#F0833C` |
| danger | `#B33348` | `#C9342F` | `#F1A6B1` | `#FF8A84` |

## کنتراست (اندازه‌گیری‌شده)

همهٔ جفت‌های متنی ≥ **4.5:1** و متن روی شیشه در بدترین استاپ ≥ **4.0:1** (اصلی) و ≥ **3.0:1** (ثانویه/سوم/accent).

| جفت | روشن | تیره |
|---|---|---|
| textPrimary / surface | 16.11 | 15.38 |
| textSecondary / surface | 7.59 | 12.48 |
| textTertiary / canvas | 4.70 | 10.80 |
| onAccent / accent | 6.50 | 11.29 |
| onGold / gold | 5.89 | 8.18 |
| success / successSurface | 4.65 | 6.72 |
| warning / warningSurface | 4.57 | 5.26 |
| danger / dangerSurface | 4.64 | 6.61 |
| info / infoSurface | 5.03 | 7.53 |
| شیشه (fill0) textPrimary | 15.84 | 4.92 |
| شیشه (fill0) accent | 6.39 | 3.36 |
| شیشه (کروم، بدترین حالت) textTertiary | — | 3.21 |

## انیمیشن محیطی

`CommandAurora` (منطق خالص، تست‌شده روی JVM):
- دورهٔ ۳۰ ثانیه (`periodMs`)، دامنهٔ جابه‌جایی ۹dp (`driftDp`)، دو محور با فاز متفاوت (هارمونیک‌های صحیح `1` و `2`).
- موج در ابتدا و انتهای دوره دقیقاً یکی است ⇒ بدون پرش.
- کل فراست/پایه در `drawWithCache` کش می‌ماند؛ فقط فاز رسم (`onDrawBehind`) invalidate می‌شود، پس هیچ recomposition و هیچ بازسازی براش رخ نمی‌دهد.
- با روشن‌بودن «کاهش حرکت» سیستمی (`useReduceMotion`) فاز صفر می‌شود و بوم کاملاً ثابت می‌ماند.
- هیچ بلور/سایه/RenderEffect اضافه نشد؛ قواعد `glass-refinement.md` دست‌نخورده است.

## رندرهای واقعی Compose

سیزده رندر در [`emerald-v1/`](emerald-v1/) ثبت شده‌اند (فارسی روشن/تیره، تبلت انگلیسی تیره، فونت ۱۵۰٪) — خروجی `CommandRedesignScreenshotTest` روی همین تغییرات، نه تصویر دستی.

## اجرای واقعی

```
$ gradle :app:testDebugUnitTest
BUILD SUCCESSFUL — 526 tests, 0 failures, 0 errors, 3 skipped (اسکنر زندهٔ قبلی)
```

تست‌های تم: `CommandEmeraldThemeTest` (۱۱ تست تازه) + `CommandGlassTest` + `CommandRedesignTest` + `AeroTokensTest` — همه پاس.

> یک نکتهٔ واقعی که همین تست‌ها گرفت: پالت تیرهٔ اولیه با `textTertiary#C2D2C8`/`accent` کمی از قرارداد شیشه رد می‌شد (کنتراست ۲٫۸ روی بدترین استاپ کروم). مقادیر روشن‌تر جایگزین شد (`textSecondary #D3E0D8`، `textTertiary #C2D2C8`، `accent #7DE7B6`) و همین‌طور هارمونیک دوم محور عمودی از `1.37` به `2` تغییر کرد تا حلقه دقیقاً بسته شود.

## کارهای باقی‌مانده (مرحلهٔ چیدمان)

- `gold` / `heroTop` / `heroBottom` هنوز مصرف‌کننده‌ای در صفحه‌ها ندارند؛ در مرحلهٔ بازچینش داشبورد (کارت هیرو + KPI strip + نوار مصرف) استفاده می‌شوند.
- لایهٔ قدیمی `AeroTokens`/`DidbanPalette` عمداً دست‌نخورده ماند (فقط صفحهٔ Crash از آن استفاده می‌کند)؛ اگر بخواهید همان‌جا هم زمردی می‌شود، جداگانه انجام می‌شود.
