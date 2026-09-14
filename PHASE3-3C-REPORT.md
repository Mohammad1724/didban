# گزارش فاز ۳ · آیتم 3-C — کاکپیت سرور (Server Cockpit)

تاریخ: ۲۰۲۶-۰۹-۱۴
وضعیت: **پایان‌یافته** — `DashboardScreen` جایگزین و حذف شد؛ لایه Compose از نظر کد بررسی شده.

## ۱. چی ساخته شد

`AeroCockpit.kt` (جدید، ~۱۳۵ خط) — لایه‌ی «کانتکست» طرح v3: وقتی روی گره‌ی سرور در نقشه ضربه می‌زنید، به‌جای dashboard قدیمی، **کاکپیت فضایی** باز می‌شود:

```
┌─────────────────────────────────────────────────────────┐
│ ← ● my-vps                     12 ms  [🌙] [↻]         │  هدر ابزار (بدون app bar)
│    host:8686 · uptime 21d 4h                             │
├─────────────────────────────────────────────────────────┤
│  ◔ CPU 42%   ◔ RAM 61%   ◔ DISK 73%   ↓NET  LOAD  CORES │  یک باند پیوسته (InstrumentBand)
├─────────────────────────────────────────────────────────┤
│ [خلاصه][پردازش‌ها][رویدادها][پورت‌ها][Docker][پایش سراسری]  │  چیپ‌های سکشن
├─────────────────────────────────────────────────────────┤
│  محتوای سکشن — سطح واحد + خطوط مویین (نه کاردک‌کارد)      │
└─────────────────────────────────────────────────────────┘
```

## ۲. تفاوت‌های ساختاری با dashboard legacy

| legacy (حذف‌شده) | v3 کاکپیت |
|---|---|
| TopAppBar + FilterChipRow | هدر ابزار فشرده: نقطه‌ی وضعیت + هویت + uptime + latency زنده + StaleBadge + تم + refresh |
| ۳ RingGauge بزرگ + StatBand در کارد | **یک InstrumentBand پیوسته‌ی ۶ سلولی** (حلقه‌های مینی CPU/RAM/DISK + Net/Load/Cores + دلتای steal) |
| یک `ModernCard` برای هر ردیف | **سطح واحد با خطوط مویین**؛ لیست‌های نامحدود (پردازش‌ها/سوکت/دکر) روی خودِ `LazyColumn` سطح v3 هستند → **لِیِیز می‌مانند** (اصلاح عملکردی) |
| EmptyState/LoadingState | سیستم ۴حالته‌ی 3-A: `ErrorState`/`SkeletonBlock`(شکل نهایی، بدون پرش layout)/`EmptyState`/`StaleBadge` (داده‌ی کهنه هرگز به‌عنوان زنده نمایش داده نمی‌شود) |
| — | سوکت‌ها با جدول ۴ستونه‌ی چگال (PROTO/ADDR/PROC·REMOTE/STATE) با LTR و فونت تله‌متری |

## ۳. صفر از دست دادن قابلیت (تحقیق ۱:۱)

مقاله‌به‌مقاله از `DashboardScreen` (۱۱۲۴ خط) پورت شد:
- **پایپلاین داده H7** دست‌نخورده: `Repo.states` برای متریک/latency/error، رفرش هر سکشن با `ApiClient` هنگام سوئچ، `PollingCoordinator.requestNow` روی refresh دستی، حلقه‌ی history با `Prefs.getPollIntervalMs`.
- **۶ سکشن:** خلاصه (چارت ۲۴ساعته CPU/RAM، تست هشدار تلگرام، ۴ کاشی سریع) · پردازش‌ها (جستجو، مرتب CPU/MEM، MeterBar، کیل) · رویدادها (تایم‌لاین جنایی اسپایک با ۹ نوع + top-4 و کیل) · سوکت‌ها (listening/connections + رفرش) · دکر (restart/stop با توست) · پایش سراسری (check-host: ping/http/tcp + ۲۰ نود + timeout).
- **دیالوگ کیل** (SIGTERM/SIGKILL + loading) و **بنر پین سرتیفیکیت** (`api.lastSeenFingerprint`) بدون تغییر منطقی.
- `BackHandler`، `imePadding`، کل استرینگ‌های fa/en.
- **پاکسازی کد مرده‌ی کشف‌شده در audit:** `latHist` در legacy جمع‌آوری و پاس می‌شد ولی **هیچ‌جا رندر نمی‌شد** (مرده) → حذف شد؛ `okCount` در GlobalCheckTab (مرده) → حذف شد.

## ۴. فایل‌ها

| فایل | تغییر |
|---|---|
| `AeroCockpit.kt` | جدید — کاکپیت کامل |
| `DashboardScreen.kt` | **حذف** (۱۱۲۴ خط؛ فقط در MainActivity و یک کامنت اشاره داشت) |
| `MainActivity.kt` | `DashboardScreen` → `AeroCockpitScreen` (امضا یکسان) |
| `Strings.kt` | +۲ رشته (fa/en): `retryProbe` (دکمه‌ی ErrorState)، `staleSuffix` (برچسب StaleBadge) |

## ۵. شواهد

| آزمایش | نتیجه |
|---|---|
| کامپایل بسته‌ی JVM — **اکنون ۱۶ فایل، شامل `Strings.kt`** (اولین بار است interface `Str` + FaStr + EnStr با هم compile می‌شوند) | **MAIN OK** |
| جعبه‌ی تست JVM (13 فایل) | **OK (126 tests)** — بدون رجگرسیون |
| `go build` + `go test -race` (agent) | **OK** |

## ۶. محدودیت‌های صادقانه

- `AeroCockpit.kt` و تغییر `MainActivity.kt` **این بخش به دلیل نداشتن Android SDK امکان تست واقعی نداشت و فقط از نظر کد بررسی شد**: امضای همه‌ی Composableهای مصرف‌شده (از جمله `CircleIconButton`، `MeterBar(width,height)`، `SectionHeader(badge,modifier)`، `PrimaryButton(loading,enabled)`، `PulseDot(pulsing)`) یکی‌یکی در `Ui.kt`/`Charts.kt` راستی‌آزمایی شد؛ `pollResults` واقعاً `Boolean` برمی‌گرداند (یک اشتباه در پیش‌نویس — استفاده از `isDone` که وجود نداشت — در review کشف و اصلاح شد).
- `Strings.kt` حالا در closure JVM است و کامپایلش شواهدِ تکمیل بودن overrideهاست؛ ولی رندر فارسی/RTL کاکپیت در 3-F روی دستگاه ممیزی می‌شود.
- **تصمیم طراحی مستند:** پیل متنی «آنلاین/آفلاین» legacy با **نقطه‌ی وضعیت رنگی** (همان زبان نقشه) + StaleBadge جایگزین شد — اطلاعات وضعیت کامل است ولی برچسب کلمه‌ای حذف شده؛ اگر برگشت خواستید، یک خط است.

## ۷. قدم بعدی (3-D)

شیت تونل (حفظ همه‌ی حالت‌های فاز ۲) + پالت فرمان (Command Palette) + منوی رادیال بلند-ضربه.
