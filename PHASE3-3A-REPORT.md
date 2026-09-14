# فاز ۳ — آیتم 3-A: توکن‌ها و پرایمیتیوهای «بوم فضایی»

> وضعیت: **انجام‌شده و تست‌شده** (منطق) + بررسی‌شده از نظر کد (لایه Compose)
> بستر: main @ eb3f3fd · تم پیش‌فرض: **روشن پلاتین** (تصمیم کاربر)

## محدوده

لایهٔ بنیادین سیستم طراحی v3 — بدون هیچ تغییری در موتور/Agent/کانتراکت‌ها و بدون
تغییری در رفتار فعلی اپ (پوستهٔ legacy تا 3-B دست نمی‌خورد):

1. **`AeroTokens.kt`** (منطق خالص JVM، بدون Compose/Android):
   - `AeroTokens` — ۳۰ توکن رنگی به‌صورت hex Long (منبع واحد ارزش)
   - `AeroPlatinumTokens` (روشن، پیش‌فرض) و `AeroCockpitTokens` (تیره/amber)
   - `AeroThemeMode { LIGHT, DARK, AUTO }` با `fromId` سازگار با رشته‌های `theme_mode` ذخیره‌شده در Prefs
   - `resolveAeroTheme(mode, systemDark)` — تابع خالص قابل تست
   - `opaqueFields()` — ابطال‌کنندهٔ شفافیت برای تست
2. **`AeroTheme.kt`** (لایهٔ Compose — فقط بررسی کد):
   - `AeroTokens.toDidbanPalette()` — رندر توکن‌ها در پایپ‌لاین فعلی `DidbanPalette` (یعنی همهٔ `Ds.*` بدون تغییر کار می‌کنند)
   - `AeroTheme(mode, systemDark)` — نقطهٔ ورود v3
   - `AeroRadii` / `AeroMotion` — توکن‌های هندسه و حرکت (sheet 220ms، radial 200ms، arc-flow 1600ms)
   - `useReduceMotion()` — احترام به تنظیم disable-animated-transitions اندروید
   - پرایمیتیوها: `InstrumentBand`/`InstrumentCell`/`RingGauge` (خوشهٔ ابزار پیوسته)، `DenseTable`/`DenseCell` (جدول با hairline، mono-LTR)، `EmptyState`، `ErrorState`، `SkeletonBlock` (shimmer + reduce-motion)، `StaleBadge` (حالت آفلاین-صادق)
3. **`Theme.kt`** — یک پارامتر اختیاری `palette` به `DidbanTheme` اضافه شد (پس‌نوست؛ همهٔ call-siteهای فعلی بدون تغییر legacy می‌مانند).
4. **`AeroTokensTest.kt`** — ۱۳ تست JVM.

## تصمیمات

- **پیش‌فروش تم**: `AeroThemeMode.LIGHT`؛ mode ناشناخته → LIGHT (مدافعانه).
- **مطابقت با Prefs**: `theme_mode` فعلی ("light"/"dark") عیناً به `AeroThemeMode` نگاشت می‌شود؛ "auto" برای آینده آماده است.
- **سازگاری**: تا 3-B، اپ با پوستهٔ legacy (Obsidian) روشن می‌شود؛ v3 از لحظهٔ بازنویسی shell (3-B) پیش‌فرض می‌شود.
- **جداسازی تست‌پذیر**: همهٔ منطق (ارزش‌ها، رزولوشن، نگاشت id) در فایل بدون وابستگی Compose — قابل تست واقعی JVM.

## شواهد (matrix تازه، ۲۰۲۶-09-14)

| بستر | نتیجه |
|---|---|
| `build.sh` (JVM closure + AeroTokens.kt) | **MAIN OK** |
| test.sh — ۱۲ فایل تست (۱۱ فایل قبلی + AeroTokensTest) | **OK (107 tests)** |
| agent: `go build ./...` | ok |
| agent: `go test -race -count=1 ./...` | **ok 1.825s** |

توضیح شفاف دربارهٔ عدد: «۵۴ تست» اعداد دوران فاز ۲ با فراخوانی کوچک‌تر بود؛
مجموعهٔ کاملِ قابلِ کامپایلِ JVM روی همین closure اکنون **۱۲ فایل = ۱۰۷ تست** است
(۹۴ تست ۱۱ فایل قبلی + ۱۳ تست AeroTokens) — همه سبز. فهرست در `.ktest/testfiles.txt`.

تست‌های 3-A: جدول رزولوشن (۴)، نگاشت id + fallback (۲)، مقادیر دقیق هر دو تم (۲)،
نگهبانان «کاملاً متفاوت» — accent/canvas v3 ≠ هر دو تم legacy (۲)، تمایز دو تم v3 (۱)،
شفافیت ۳۰ توکن + alpha partial برای glow/dim (۲).

## محدودیت‌های صادقانه

- `AeroTheme.kt` (لایهٔ Compose UI) **امکان تست واقعی نداشت** (بدون Android SDK/Compose در این بستر) و فقط از نظر کد بررسی شد: نام/ترتیب فیلدها با `DidbanPalette` در Theme.kt تطبیق داده شد، APIها (Canvas.drawArc، rememberInfiniteTransition، مادیال-۳ Text) با امضاهای رسمی مطابقت دارند.
- رفتار `useReduceMotion` (خواندن تنظیم اندروید) نیز جزو همان لایه است.
- تا 3-B، کاربر چیزی از ظاهر v3 در اپ نمی‌بیند — این آیتم بنیاد است.

## بعدی: 3-B

بوم تمام‌بها (نقشه Canvas + HUD + دک افقی) + بازنویسی shell MainActivity +
پیش‌فرض‌سازی تم v3 — ریسک‌ترین آیتم؛ با پلن رگرسیون جدا.
