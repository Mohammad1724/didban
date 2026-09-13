# گزارش آیتم ۱۲ فاز ۲ — H8: Crash recovery غیرقابل‌اتکا + crash loop

## مشکل (طبق ممیزی فاز ۱)

در `DidbanApplication`، handler کرش:
1. `startActivity(...)` و **بلافاصله** `Process.killProcess` + `System.exit` می‌زد → launch transaction ممکن بود پیش از آنکه سیستم‌سرویس پردازشش کند از بین برود (بسته به نسخهٔ اندروید) → **صفحهٔ سیاه**
2. هیچ شمارنده‌ای برای **crash loop** وجود نداشت → اگر کرش در مسیر استارتاپ تکرار می‌شد (مثلاً کرش در composition)، اپ در چرخهٔ مرگ بازمی‌گشت و هیچ مکانیزمی interupt نمی‌کرد
3. trace در prefs به‌صورت **plaintext** ذخیره می‌شد (trace می‌تواند دادهٔ حساس بارتاب کند)
4. دادهٔ مرده: `last_crash_msg` فقط نوشته و هیچ‌جا خوانده نمی‌شد؛ extra `is_crash_launch` هم خواننده‌ای نداشت

**نکتهٔ مهم ممیزی:** اپ از قبل صفحهٔ `CrashRecoveryScreen` (Zero-Panic Failure Guard) داشت و بعد از هر کرش در شروع‌به‌کار بعدی نمایش داده می‌شد. پس کار اصلی: قابل‌اعتماد کردن «رسیدن» به آن صفحه، قطع crash loop، و محافظت trace — نه ساخت صفحهٔ جدید.

## راه‌حل

### ۱) قطع race صفحه‌سیاه — `DidbanApplication`
- kill دیگر inline نیست: پس از `startActivity` (best-effort، در try/catch) یک thread جدا **۱.۵ ثانیه** grace period به پروسهٔ تازه می‌دهد و سپس terminate می‌کند → transaction launch فرصت commit پیدا می‌کند
- حتی اگر restart شکست، کاربر **صفحهٔ سیاه نمی‌بیند**: رکورد کرش روی دیسک هست و با بازکردن دستی اپ، همان صفحهٔ diagnostic می‌آید

### ۲) تشخیص و قطع crash loop — `CrashPolicy` (منطق خالص، JVM-testable)
- `CrashRecord(lastCrashAt, consecutiveCount)`: کرشی «متوالی» است اگر کمتر از **۱۰ ثانیه** پس از کرش قبل رخ بدهد (پنجرهٔ استارتاپ)؛ فاصلهٔ بیشتر → شمارش به ۱ بازمی‌گردد (اپی که چند دقیقه کار کرده و بعد کرش می‌کند، loop نیست)
- با رسیدن به **۳ کرش متوالی**، handler دیگر **auto-restart نمی‌کند** → پروسه می‌میرد، کاربر اپ را دستی باز می‌کند → صفحهٔ diagnostic با خطای زرد «N consecutive crashes — auto-restart disabled» → تصمیم با کاربر (Reset & Launch)
- شمارنده در `MainActivity.onStart` صفر می‌شود (استارتاپ زنده مانده)؛ بنابراین کرش‌های «بعد از استارتاپ» هرگز به‌اشتباه loop محسوب نمی‌شوند و بعد از یک بازیابی دستی، auto-restart دوباره فعال است (شمارندهٔ قدیمی برای همیشه auto-restart را غیرفعال نمی‌کند)

### ۳) حفاظت trace — `CrashLog` (glue اندروید)
- trace حالا با `SecureStorage` (AES-256-GCM روی کلید **AndroidKeyStore غیرexportable**) **مشفّر** در prefs ذخیره می‌شود؛ در صفحهٔ diagnostic decrypt و نمایش داده می‌شود
- اگر خود encryption شکست (keystore خراب)، فقط placeholder «trace unavailable» ذخیره می‌شود — **هرگز plaintext**
- مهاجرت نرم: trace plaintext قدیمی (نسخه‌های قبل) در صورت شکست decrypt به‌عنوان legacy نمایش داده می‌شود و با اولین Reset جایگزین نسخهٔ مشفّر می‌شود
- trace با سقف ۶۴KB (استک‌های Compose می‌تواند بزرگ باشد)
- همهٔ writes با `commit()` **سنکرون** (process ممکن است میلی‌ثانیه‌ها بعد بمیرد؛ `apply()` کافی نیست)
- فیلدهای مرده حذف شدند: `last_crash_msg` (نوشتن حذف + پاک‌سازی legacy در `clear`) و extra `is_crash_launch` (خواننده نداشت)

## تغییرات

| فایل | نوع |
|---|---|
| `CrashRecovery.kt` | جدید — `CrashPolicy`/`CrashRecord` (منطق خالص) |
| `CrashLog.kt` | جدید — record + trace مشفّر + clear (glue) |
| `DidbanApplication.kt` | بازنویسی handler — record قبل از هر کاری، auto-restart مشروط، grace period به‌جای kill inline |
| `MainActivity.kt` | خواندن trace از `CrashLog` (decrypt)، نمایش شمارش loop در صفحهٔ diagnostic، `markStartupOk` در onStart، Reset → `CrashLog.clear` |

ایجنت Go دست‌نخورده است.

## شواهد

- **JVM: `OK (117 tests)`** (قبل: ۱۰ — ۸ تست جدید `CrashRecoveryTest`):
  - شمارش از ۱، افزایش درون پنجره، reset بعد از پنجره، رفتار دقیق مرز پنجره (strict <)
  - auto-restart: مجاز زیر threshold، مسدود در threshold و بالاتر
  - **سناریوی کامل loop**: ۵ کرش پیوسته با فاصله ۲ ثانیه → `[restart, restart, NO, NO, NO]` — یعنی حداکثر ۲ چرخهٔ خودکار و بعد کنترل با کاربر
- بالانس براکت/پرانتز همهٔ فایل‌های دست‌خورده؛ ارجاع قدیمی به `last_crash_msg`/`is_crash_launch`: صفر
- `CrashRecoveryScreen` فقط در MainActivity صدا زده می‌شود؛ پارامتر جدید default دارد (شکست‌ناپذیر)

## معنای نهایی (خلاصهٔ رفتاری)

| سناریو | قبل | بعد |
|---|---|---|
| یک کرش (بعد از کارکردن عادی) | صفحهٔ سیاه ممکن + restart | grace period + صفحهٔ diagnostic در شروع بعدی (مثل قبل) |
| کرش در استارتاپ (loop) | چرخهٔ مرگ بی‌پایان | حداکثر ۲ auto-restart ← صفحهٔ diagnostic با هشدار loop ← Reset با کاربر |
| trace روی دیسک | plaintext | AES-256-GCM (کلید Keystore) |
| کرش بعد از بازیابی دستی | شمارش نبود | شمارنده در onStart صفر شده → auto-restart سالم |

## محدودهٔ تست — صداقت

- `CrashLog` و تغییرات `DidbanApplication`/`MainActivity` **build/اجرا روی اندروید نشدند** (SDK در دسترس نیست) → **این بخش‌ها فقط از نظر کد بررسی شدند**
- منطق سیاست loop (`CrashPolicy`) کامل با JVM تست شده است؛ اما خودِ crash/kill/restart روی دستگاه (به‌خصوص race grace period در نسخه‌های مختلف اندروید) **نیازمند تست روی دستگاه/AVD**: سناریوهای پیشنهادی — (۱) کرش مصنوعی با `throw` در composition → صفحهٔ diagnostic؛ (۲) کرش مکرر در استارتاپ (مثلاً کرش در `onCreate` با flag) → بعد از ۲ restart خودکار، قطع و نمایش هشدار loop؛ (۳) Reset & Launch → برگشت عادی
- رفتار legacy (trace plaintext نسخه‌های قبل) روی دستگاه تست نشد — فقط از نظر کد
