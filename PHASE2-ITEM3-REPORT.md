# گزارش آیتم ۳ فاز ۲ — SSH TOFU (Trust-On-First-Use) — رفع C3

تاریخ: ۲۰۲۶-۰۹-۱۳ | push به `main`

## ۱. وضعیت قبل (C3)

هر سه نقطهٔ ساخت session در `SshEngine`، `SshSetup` و `SftpEngine` تنظیم `StrictHostKeyChecking=no` داشتند و **کلید میزبان هرگز بررسی نمی‌شد**. یعنی:
- حملهٔ MITM (مان در میانه) با ارائهٔ کلید خودش، کامل موفق می‌شد — کاربر، دستور، رمز و داده‌ها به مهاجم می‌رفت.
- حتی اگر کلید سرور واقعی عوض می‌شد (ری‌ایمیج)، اپ بدون هیچ نشانی کلید جدید را قبول می‌کرد.

توجه: کانال exec پروتکل SSH در سمت سرور همواره از shell راه‌انداز می‌شود و JSch در این نسخه (mwiede 0.2.18) مکانیزم known_hosts خالص را در جریان ما اعمال نمی‌کند؛ بنابراین بررسی باید **بعد از key exchange و قبل از ارسال اولین دستور** در سمت کلاینت انجام شود — دقیقاً همان چیزی که این آیتم پیاده می‌کند.

## ۲. معماری پیاده‌سازی

### `HostKeyTrust.kt` (جدید — هستهٔ خالص، بدون وابستگی Android، کاملاً JVM-testable)
- **`HostKeyFingerprint`**: fingerprint = hex SHA-256ِ بایت‌های خام کلید (همان قرارداد hex SHA-256 که اپ برای pin کردن گواهی TLS ایجنت در `ApiClient` استفاده می‌کند) + `verify` با مقایسهٔ **constant-time**.
- **`HostKeyStore`** (interface) + **`hostKeyIdentity`** (norm host کوچک‌نویس + نرمال‌سازی پورت → 22).
- **`HostKeyPolicy`** (interface): `suspend verify(host, port, rawKey): Boolean`.
- **`AutoTrustPolicy`**: TOFU غیرتعاملی — اولین تماس: ذخیره و قبول؛ بعداً: enforce سخت، تفاوت کلید = abort. برای پایپ‌لاین‌های پس‌زمینه (SinglePortEngine، SFTP، batch پیش‌فرض).
- **`ConfirmingHostKeyPolicy`**: TOFU تعاملی — اولین تماس: suspend تا کاربر در دیالوگ تأیید کند؛ کلید عوض‌شده: suspend تا کاربر صریحاً «اعتماد را بازنشانی» کند (با برچسبِ مخاطره‌آمیز بودن).
- **`verifySessionHostKey(session, host, port, policy)`**: کلید از `session.hostKey` (API نسخهٔ 0.2.18) خوانده می‌شود، base64→بایت، fingerprint محاسبه و با policy مقایسه می‌شود. **Fail-closed**: اگر JSch کلید ندهد یا policy رد کند، session قطع و خطای خوانا برمی‌گردد.

### `HostKeyTrustStore.kt` (جدید — پیاده‌سازی Android)
- SharedPreferences (نام `didban_hostkeys`)؛ init یک‌بار از `DidbanApplication.onCreate`.
- **دلیل ذخیرهٔ plain (نه encrypted) مستند شد:** fingerprint یک **trust anchor عمومی** است، نه secret — دقیقاً مثل `known_hosts` گیت/SSH که plain روی دیسک است؛ باید کاربر بتواند آن را تلفنی بخواند و تطبیق دهد. (برخلاف توکن/کلید خصوصی که در C4 encrypted می‌شوند.)

### `HostKeyTrustDialog.kt` (جدید — Compose)
دو حالت:
- **سرور جدید:** نمایش host:port + fingerprint SHA-256 + توصیهٔ تطبیق از کانال مستقل؛ دکمه‌های «اعتماد و اتصال»/«لغو».
- **کلید تغییر کرده:** عنوان قرمز «کلید میزبان تغییر کرده»، توضیح MITM vs ری‌ایمیج، دکمهٔ «بازنشانی اعتماد (مخاطره‌آمیز)»/«قطع اتصال».

### اتصال به engineها
| نقطه | سیاست |
|---|---|
| `SshEngine.execute` | پارامتر جدید `hostKeyPolicy` (پایانی، پیش‌فرض `AutoTrustPolicy`)؛ بررسی بین `connect` و `openChannel`؛ شکست = `SshExecResult` با exit -1 و خطای خوانا |
| `SshEngine.executeBatch` | انتقال policy به هر exec |
| `SshSetup.installAgent` | بررسی قبل از اجرای installer |
| `SftpEngine.createSession` | (suspend شد) بررسی قبل از هر عمل SFTP؛ شکست = throw |
| `SinglePortEngine` (۳ فراخوانی) | به‌صورت خودکار `AutoTrustPolicy` پیش‌فرض |

### اتصال به صحنه‌های تعاملی (۴ صفحه)
`SshTerminalScreen`، `SecurityScreen` (۳ فراخوانی)، `SystemdScreen`، `BatchExecScreen` — هرکدام:
- `ConfirmingHostKeyPolicy` + `HostKeyPromptFlow`-like با `Channel` (RENDEZVOUS) + `Mutex` gate.
- **بهینه‌سازی race-free:** `Mutex` تضمین می‌کند حتی با چند connection همزمان (مثلاً batch روی چند سرور جدید، یا دوبار کلیک پشت‌سرهم) **هرگز دو دیالوگ هم‌زمان** نباشد؛ promptها به‌ ترتیب نمایش داده می‌شوند.
- نمایش state از طریق `withContext(Dispatchers.Main)`؛ دیالوگ با `trySend` به policy نتیجه را برمی‌گرداند.

## ۳. شواهد تست

### ۳.۱ واحدتست‌های Kotlin — اجرا و سبز (روی JVM)
Android SDK/gradle در این محیط نیست، اما هستهٔ TOFU کاملاً مستقل از Android است. با kotlinc 2.0.21 + jsch 0.2.18 + coroutines 1.8.1 + junit 4.13.2 کامپایل و اجرا شد:

```
JUnit version 4.13.2
..........................
Time: 0.13
OK (26 tests)
```

`HostKeyTrustTest.kt` (۱۴ تست) پوشش می‌دهد:
- fingerprint: قطعی، ۶۴ کاراکتر hex، دو کلید متفاوت → fingerprint متفاوت، verify درست/اشتباه، case-insensitivity.
- identity: host case-insensitive، نرمال‌سازی پورت (0/‌نادرست → 22)، تفکیک hostها.
- `AutoTrustPolicy`: ثبت در اولین تماس، enforce در بارهای بعدی، **رد کلید متفاوت + بدون تغییر store**، استقلال hostها.
- `ConfirmingHostKeyPolicy`: تأیید اولیه (با ثبت)، رد اولیه (بدون ثبت)، بدون prompt برای کلید شناخته‌شده، **prompt با `keyChanged=true` + بازنشانی**، **رد بازنشانی → حفظ trust اصلی**، `forget`.

(۱۲ تست قبلی `SecurityValidationTest` هم در همان run سبز ماندند.)

### ۳.۲ بررسی بصری (code-review)
- diff هر engine و هر صحنه مروری شد؛ `finally`/disconnect امن (double-disconnect در try/catch).
- پارامتر جدید `hostKeyPolicy` پایانی و با پیش‌فرض است → **هیچ فراخوانی موجود break نمی‌شود** (تأیید با grep روی همهٔ call-sites).
- هر سه نقطهٔ `jsch.getSession` (SshEngine/SshSetup/SftpEngine) حالا `verifySessionHostKey` دارند (تأیید با grep).
- `Ds.danger/textSecondary/textTertiary` در Theme موجودند؛ دیالوگ از الگوی `AlertDialog` هم‌خانوادهٔ بقیهٔ اپ پیروی می‌کند.

## ۴. آنچه با این محیط قابل تست نبود (صریح)

**این بخش به دلیل نبود Android SDK و gradle wrapper، امکان build/اجرای کامل اپ، واحدتست‌ها از مسیر Gradle، و همچنین تست تعاملیِ واقعیِ دیالوگ TOFU را نداشت.**
- هستهٔ TOFU (fingerprint + policy + store-logic): **کاملاً test-executed** روی JVM — سبز.
- `verifySessionHostKey` و تغییرات `SshEngine`/`SshSetup`/`SftpEngine`/`HostKeyTrustStore`: فقط از نظر کد بررسی شدند (API واقعی jsch 0.2.18 با reflection/`javap`-like probe تأیید شد: `session.hostKey` → `getKey()` که base64 line-wrapped برمی‌گرداند، و کد decode با `Base64.getMimeDecoder` نوشته شد). باید در اولین build/اجرای واقعی شما (AS یا CI) تأیید شوند.
- **تست واقعی MITM/تغییر کلید** (مثلاً با sshd محلی + تغییر کلید) نیازمند device/emulator است و در این محیط انجام نشد.

## ۵. تحلیل رگرسیون

- رفتار پیش‌فرض برای سرورهای **قبلاً شناخته‌شدهٔ واقعی**: اولین اجرا بعد از آپدیت = اولین تماس → trust ثبت می‌شود؛ از آن به بعد enforce. یک‌بار prompt (در صحنه‌های تعاملی) یا silent (در پایپ‌لاین) — قابل انتظار و یک‌بار.
- خطاها خوانا برمی‌گردند؛ `SshExecResult.isSuccess=false` + `errorMessage` — UIهای موجود که این فیلدها را نشان می‌دهند بدون تغییر کار می‌کنند.
- کنتراکت‌ها شکست: پارامترها فقط افزوده‌اند (با پیش‌فرض)، امضای عمومی `SshExecResult` دست‌نخورده.

## ۶. گام بعدی (آیتم ۴)

C4 — `allowBackup=false` + ذخیرهٔ رمزنگاری‌شدهٔ توکن/کلیدها (plaintext secrets).
