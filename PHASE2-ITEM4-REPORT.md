# گزارش آیتم ۴ فاز ۲ — رمزنگاری secret ها + قفل backup (C4)

تاریخ: ۲۰۲۶-۰۹-۱۳ | push به `main`

## ۱. وضعیت قبل (C4)

- **`android:allowBackup="true"`** در Manifest: کل SharedPreferences — شامل همهٔ زیر — از طریق backup ابری گوگل یا `adb backup` قابل برداشت بود.
- **secret ها plaintext در SharedPreferences:**
  | کلید | محتویا | اهمیت |
  |---|---|---|
  | `servers` (JSON) | توکن Bearer ایجنت هر سرور | دسترسی کامل به API سرور (metrics، process kill، docker، tunnel deploy) |
  | `didban_tunnels` (JSON) | توکن احراز تونل‌ها | دسترسی به تونل‌ها |
  | `cf_token` | API توکن Cloudflare | دسترسی کامل به اکانت CF (DNS، Zones، R2) |
  | `tg_bot_token` | توکن ربات تلگرام | کنترل ربات |
  | `discord_webhook` | URL webhook (خودش اعتبار است) | ارسال پیام در کانال |
- `BackupEngine.restore` هم `cf_token` را مستقیم plaintext می‌نوشت (bypass از لایهٔ Prefs).

## ۲. مرز «secret vs setting» (مستند در کد)

**Secret:** هر چیزی که دسترسی به سیستم دوربی می‌دهد → ۵ کلید بالا.
**Non-secret (plain می‌مانند):** hostname/port، آستانه‌ها، fingerprint (trust-anchor عمومی)، chat id، flagهای UI، lang/theme/poll، uptime targets (بدون token). Vault کاربر خودش با رمز کاربر AES-GCM رمزنگاری می‌شود (دو بار رمزنگاری معنی ندارد).

## ۳. پیاده‌سازی

### `SecureCipher.kt` (جدید — هستهٔ خالص، کاملاً JVM-testable)
AES-256-GCM با IV تصادفی ۱۲ بایتی در هر فراخوانی؛ خروجی `base64(iv || ct || tag)`. کلید با `SecretKey` تزریق می‌شود → بدون Android قابل تست.

### `SecureStorage.kt` (جدید — glue اندروید)
- کلید AES-256 در **Android Keystore** (alias `didban_app_secrets`) — non-exportable، با پاک‌شدن دادهٔ اپ پاک می‌شود.
- `getSecret(ctx, prefs, key)`: می‌خواند از `<key>_enc` و **مهاجرت شفاف**: در اولین دسترسی بعد از آپدیت، مقدار legacy plaintext رمزنگاری و نسخهٔ plain حذف می‌شود. اگر رمزنگاری شکست (بسیار بعید)، مقدار legacy برگردانده می‌شود — **داده هرگز از دست نمی‌رود**.
- `putSecret`: ذخیرهٔ رمزنگاری‌شده + حذف نسخهٔ legacy.
- `removeSecret`: حذف هر دو نسخه.

### `Prefs.kt`
پنج کلید secret از طریق `SecureStorage` می‌روند؛ امضای عمومی تمام توکن‌های Prefs دست‌نخورده → **هیچ caller‌ای تغییر نمی‌خواهد**.

### `BackupEngine.kt`
restoreی `cf_token` از `editor.putString` مستقیم به `Prefs.setCfToken` (یعنی رمزنگاری‌شده) تغییر کرد. بقیهٔ restore از قبل از Prefs می‌رفت.

### `AndroidManifest.xml`
`android:allowBackup="false"` — هیچ بخشی از دادهٔ اپ دیگر قابل backup نیست.

## ۴. شواهد تست

### ۴.۱ واحدتست‌های Kotlin — اجرا و سبز روی JVM
```
$ kotlinc ... -d out
$ java ... JUnitCore SecurityValidationTest HostKeyTrustTest SecureCipherTest
JUnit version 4.13.2
..................................
OK (34 tests)
```
`SecureCipherTest` (۸ تست): round-trip (عادی/فارسی+چینی/۱۰۰KB/خالی)، **IV تازه در هر بار** (دو ciphertext متفاوت از یک plaintext)، **تشخیص دستکاری** (flip یک بایت tag → reject)، رد payload truncated، **کلید اشتباه → reject**، اندازهٔ حداقلی.

### ۴.۲ بررسی بصری
- `grep` جامع: **هیچ** `getString/putString` مستقیم روی ۵ کلید secret باقی نمانده (همه از Prefs/SecureStorage).
- widget/MonitorService/سایر processها فقط از Prefs می‌خوانند → شفاف.
- `editor` در restore هنوز برای vault/settings استفاده می‌شود (تخلف نداشت).

## ۵. آنچه با این محیط قابل تست نبود (صریح)

**این بخش به دلیل نبود Android SDK و gradle wrapper، امکان build/اجرای کامل اپ، واحدتست از مسیر Gradle، و مهم‌تر از همه **تست واقعیِ مهاجرت و Keystore روی device** را نداشت.**
- هستهٔ رمزنگاری (SecureCipher): **کاملاً test-executed** روی JVM — سبز.
- `SecureStorage` (Keystore + migration)، تغییرات `Prefs`/`BackupEngine`/Manifest: فقط از نظر کد بررسی شدند؛ رفتار Keystore و مسیر مهاجرت در اولین اجرای واقعی روی دستگاه تأیید می‌شود.

## ۶. تحلیل رگرسیون و سناریوهای مرزی

- **آپدیت روی نصب قبلی:** اولین خواندن → مهاجرت شفاف؛ کاربر هیچ چیز نمی‌بیند.
- **پاک‌سازی دادهٔ اپ:** Keystore key + prefs هر دو پاک می‌شوند → سازگار.
- **فکتوری‌ریست با ماندن داده (نادر):** key از دست می‌رود → decrypt شکست → legacy هم حذف شده → مقدار خالی (کاربر باید توکن را دوباره وارد کند). این trade-off ذاتی طراحی Keystore-bound است (security-crypto هم همین را دارد).
- **دو process هم‌زمان در مهاجرت:** هر دو همان مقدار را encrypt می‌کنند → نتیجه یکسان، بدون خرابی.
- `usesCleartextTraffic="true"` دست‌نخورده ماند: فلگ `useTls` هر سرور کنترل واقعی را دارد (تست LAN با HTTP ساده یک workflow معتبر است). در آیتم‌های بعدی (Hardening API/CI) می‌توان آن را دقیق‌تر کرد.

## ۷. گام بعدی (آیتم ۵)

C5 — حذف/واقعی‌کردن سه feature با دادهٔ fake: BandwidthBenchmark (حذف یا واقعی‌سازی)، SecurityScreen (لیست fail2ban واقعی)، SystemdScreen (وضعیت واقعی unitها).
