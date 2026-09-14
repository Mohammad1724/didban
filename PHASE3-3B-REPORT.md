# گزارش فاز ۳ · آیتم 3-B — خانه فضایی (Aerospatial Home)

تاریخ: ۲۰۲۶-۰۹-۱۴
وضعیت: **پایان‌یافته** — مدل داده با تست JVM تأیید شده؛ لایه Compose از نظر کد بررسی شده.

## ۱. چی ساخته شد

این آیتم «اسکلت» اپلیکیشن را طبق طرح v3 تغییر ساختاری داد:

| قبل (فاز ۲) | بعد (3-B) |
|---|---|
| TopAppBar + نوار پایین (LiquidSpotlightDock) با ۵ تب | بوم تمام‌صفحه فضایی + HUD شناور + دِک افقی ۵ صفحه‌ای |
| لیست کارت‌ساز серверها در خانه | نقشه‌ی فضایی (گراف): سرورها گره، تونل‌ها کمان |
| تب «سرورها» در دُک | ServersScreen به‌عنوان overlay کامل درون‌بر (drill-in) از HUD |

**صفحات دِک (مرور با سواپ افقی):**
0. **نقشه** (جدید) — گرگراف فضایی سرورها/تونل‌ها
1. تونل‌ها (`TunnelScreen` — دست‌نخورده)
2. آپتایم (`UptimeScreen` — دست‌نخورده)
3. ابر و هشدارها (`NetworkCloudScreen` — دست‌نخورده)
4. گاوصندوق و ابزارها (`VaultToolsScreen` — دست‌نخورده)

**HUD** (یک پیل ۴۰dp، بدون app bar): نام اپ + سه سلول KPI (آنلاین/کل، تونل‌های فعال، هشدارها) + سه دکمه: مدیریت سرورها، تم، زبان.

**نقشه:** شبکه‌ی نقطه‌ای ظریف + حلقه‌های مداری + کمان‌های بوسری:
- ACTIVE: خط مویین پایه + خط‌چین سبزِ در حال جریان (بدون درز، با `Animatable`)
- DOWN: خط‌چین قرمز کدر
- DORMANT: خط تیره‌ی خنثی
- گره‌ها: هاله‌ی رادیالی + حلقه‌ی وضعیت (آفلاین = خط‌چین) + هسته‌ی توخالی؛ سرورِ اول بزرگ‌تر (مرکز)
- برچسب‌ها و هدف لمس ۴۴dp به‌عنوان overlay روی Canvas
- خالی: `EmptyState` با دکمه‌ی «افزودن سرور» → overlay مدیریت

## ۲. فایل‌ها

| فایل | نقش | نوع |
|---|---|---|
| `AeroMapModel.kt` (جدید) | مدل خالص JVM: `MapNode/MapArc/MapKpis/MapGraph`، چیدمان مداری `nodePosition`، `buildMapGraph` (حالت گره از متریک‌ها + آستانه‌های `cpuAlert/memAlert`؛ حل سران کمان با id و بعد host؛ KPIها) | **تست‌شده** |
| `AeroMapModelTest.kt` (جدید) | ۱۹ تست | **سبز** |
| `AeroCanvasHome.kt` (جدید) | دِک + HUD + صفحه‌ی نقشه + رندرر Canvas + BackHandler ریست صفحه | بررسی کد |
| `MainActivity.kt` | بازنویسی بخش «APP SHELL»: `AeroTheme` + سلسله‌مراتب Back + overlay مدیریت + حذف کامل `LiquidSpotlightDock`/`DockItemSpec` + پاک‌سازی ایمپورت‌های مرده | بررسی کد |
| `Strings.kt` | +۱۰ رشته (fa/en): deckHint, mapEmptyTitle, mapEmptyBody, mapAddServer, manageServers, kpiOnline, kpiTunnels, kpiAlerts, mapOffline, mapSpike | تست‌شده (compile) |

## ۳. تصمیمات کلیدی و تحلیل رجگرسیون

1. **صفر از دست دادن قابلیت:** `ServersScreen` (۱۲۴ خط، با مجیک‌های add/edit/delete و dialogهای خودش و `BackHandler` تودرتو) بدون هیچ تغییری به‌عنوان overlay تمام‌صفحه استفاده می‌شود. باز/بسته شدن با Back.
2. **سلسله‌مراتب Back (دقیق‌سازی):**
   - dashboard باز → Back بسته می‌کند
   - overlay مدیریت باز → Back overlay را می‌بندد و `manageTick++` (بازخوانی لیست نقشه)
   - غیرصفحه‌۱ِ دِک → Back به صفحه‌ی ۰ (مدل؛ `deckBackEnabled=false` زیر overlay تا اولویت درستی حفظ شود)
   - صفحه‌ی ۰ → Back دوگانه‌ی خروج (میراث فاز ۲)
   - Compose آخرین `BackHandler` را که فعال باشد برمی‌گزیند؛ handler دِک عمیق‌تر در ترکیب ثبت می‌شود، پس وقتی فعال است، بر handlerهای shell برتری دارد.
3. **H9 (بقای حالت) حفظ شد:** `openServerId` به‌صورت آدرس‌دهی با id + resolve از پرایوریت‌های saveable؛ `deckPage` با `rememberSaveable` → `PagerState(initialPage)`؛ اگر سرور حذف شده باشد، id یتیم در `LaunchedEffect` حذف می‌شود.
4. **H8 (ریکاوری کرش) دست‌نخورده** — `CrashRecoveryScreen` و سلسله‌مراتب آن باقی است.
5. **تم:** shell از `AeroTheme(mode = fromId(themeMode))` استفاده می‌کند (پیش‌فرض روشن/پلاتین). صفحات legacy تا 3-C..3-E هنوز `DidbanTheme` با palette خودشان را نگه داشته‌اند (مستندسازی‌شده در کد)؛ `Ds.*` از `LocalDidbanPalette` می‌خواند، پس هر صفحه palette فعالِ خودش را می‌بیند.
6. **عملکرد:** `offscreenLimit = 0` → فقط صفحه‌ی جاری compose می‌شود (هم‌ترازی با `when` قبلی). گراف در `remember(servers, tunnels, states)` ساخته می‌شود — با هر poll (۱۵ ثانیه) بازنشانی ارزان؛ انیمیشن خط‌چین `Animatable` پایدار است و با recompose از نو شروع **نمی‌شود**.
7. **پاکسازی:** `LiquidSpotlightDock`/`DockItemSpec` حذف (grep: هیچ مرجع خارجی نداشت)؛ ۲۴ ایمپورت مرده از MainActivity حذف شد.

## ۴. شواهد تست

| آزمایش | نتیجه |
|---|---|
| کامپایل بسته‌ی JVM (15 فایل، شامل `AeroMapModel.kt`) | **MAIN OK** |
| جعبه‌ی تست JVM (13 فایل) | **OK (126 tests)** — ۱۰۷ قبلی + ۱۹ جدید |
| `go build ./...` + `go test -race ./...` (agent) | **BUILD OK / ok 1.9s** |

تست‌های 19 جدیدی پوشش می‌دهند: حالت گره (آنلاین/آفلاین/خطا-با-متریک/هشدار-CPU/هشدار-رم/بدون-داده)، حل سران کمان (id → host، case-insensitive، سران نامعلوم/loopback skip، گره‌های معیوب)، حالت کمان (غیرفعال=DORMANT، هرگز-چک‌نشده=DORMANT، lastStatus 1/0، حمل latency)، KPIها (شمارش WARN به‌عنوان آنلاین، تجمیع هشدارها)، چیدمان مداری (مرکز، زاویه، شعاع).

## ۵. محدودیت‌های صادقانه

- `AeroCanvasHome.kt`، بخش بازنویسی‌شده‌ی `MainActivity.kt` و `Strings.kt` **این بخش به دلیل نداشتن Android SDK/Gradle در این محیط امکان تست واقعی نداشت و فقط از نظر کد بررسی شد** (بررسی دقیق API‌ها: امضای همه‌ی Composableهای مصرف‌شده، فیلدهای `Ds.*`، `Repo.states` object، overload `EmptyState` (برگشت‌پذیرِ بدون ابهام چون فقط نسخه‌ی v3 پارامتر `body` دارد)، نسخه‌ی Compose BOM 2024.06.00 برای `HorizontalPager`/`BackHandler(enabled)`، کلاسپیت `material-icons-extended` برای آیکون‌ها).
- مدل داده و منطق کمان/گره/KPI با 19 تست JVM کاملاً تأیید شده‌اند.
- رفتار بصری (دقت overlayها، RTL، reduce-motion) باید در 3-F با دستگاه واقعی/ایمولاتور ممیزی شود.

## ۶. کارهای بعدی (3-C)

کاکپیت سرور (sheet جایگزین `DashboardScreen`) — همه‌ی ویجت‌های فاز ۲ با زبان v3.
