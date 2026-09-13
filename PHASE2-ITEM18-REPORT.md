# گزارش آیتم ۱۸ فاز ۲ — M10: خروجی SSH بی‌سقف → OOM دستگاه

## مشکل (طبق ممیزی فاز ۱)

`SshEngine.execute()` خروجی stdout/stderr هر دستور را در `ByteArrayOutputStream` **بدون هیچ سقفی** نگه می‌داشت — `cat /dev/urandom` یا `journalctl` بی‌پایان → پر شدن RAM گوشی و kill شدن اپ. دو مشکل فرعی در همان تابع: در timeout، `exitStatus=-1` بدون هیچ تمایزی با خروج واقعی (کاربر نمی‌فهمد دستور آویزان بوده)؛ و `executeBatch` برای N سرور، N session هم‌زمان باز می‌کرد. لایهٔ سوم: بافر متنی ترمینال (`terminalOutput`) در هر دستور `+=` می‌شد و بی‌حد رشد می‌کرد. (خوانندهٔ SFTP از قبل cap ۴MB داشت ✓ — دست‌نخورده ماند.)

## راه‌حل

### ۱. `CappedOutputStream` (منطق خالص، JVM-testable) — `CappedStream.kt` جدید

- هر بایت **شمارش** می‌شود (`totalBytes`) ولی بعد از سقف فقط **prefix نگه داشته** می‌شود (`retainedBytes`) — بافر هرگز فراتر از سقف نمی‌رود؛ writer بالادستی (thread داخلی JSch) هرگز نمی‌افتد/مسدود نمی‌شود
- سقف: **`MAX_SSH_OUTPUT_BYTES = 1MB` به‌ازای هر stream** — چند برابر بزرگ‌ترین خروجی واقعیِ دستورات admin، ولی bounded و device-safe
- truncation **سکوت‌آمیز نیست**: خط `…[خروجی بریده شد — فقط 1024KB از NKB نگه داشته شد]` به خود خروجی اضافه می‌شود

### ۲. `SshEngine.kt`

- هر دو stream از `CappedOutputStream` تغذیه می‌کنند (همهٔ callerها از این یک تابع رد می‌شوند: ترمینال، batch، Security، Systemd، SinglePort)
- **تشخیص timeout**: `timedOut = !channel.isClosed` بعد از wait-loop → `errorMessage = "زمان‌بندی تمام شد (N ثانیه) — خروجی ممکن است ناقص باشد"` و `isSuccess=false` (قبل: exitCode=-1 خاموش)
- **`executeBatch`**: `Semaphore(5)` — ۵۰ target دیگر ۵۰ session هم‌زمان (هرکدام تا ۲MB stream) نمی‌شود

### ۳. `SshSetup.kt`

- همان cap روی خروجی installer (banner در عمل چند KB است؛ cap = دفاع در عمق در برابر curl error/verbose bash بزرگ)
- timeout ۵ دقیقه‌ای حالا پیام روشن دارد: `زمان‌بندی نصب تمام شد (۵ دقیقه)` به‌جای `installer output could not be parsed` گمراه‌کننده

### ۴. `SshTerminalScreen.kt`

- `appendTerminal()` با سقف **64KB** روی بافر خودِ ترمینال (قدیمی‌ترین بخش حذف می‌شود با مارکر) — ۱۰ دستور × خروجی capped دیگر یک رشتهٔ Compose بی‌حد نمی‌سازد

## تغییرات

| فایل | تغییر |
|---|---|
| `CappedStream.kt` | **جدید** — `CappedOutputStream` + `MAX_SSH_OUTPUT_BYTES` |
| `SshEngine.kt` | cap هر دو stream + مارکر truncation + تشخیص timeout + `Semaphore(5)` در batch |
| `SshSetup.kt` | cap streamها + مارکر + پیام timeout روشن |
| `SshTerminalScreen.kt` | `appendTerminal()` با سقف 64KB (۳ call-site) |
| `CappedOutputStreamTest.kt` | **جدید** — ۷ تست خالص |

## شواهد

- **JVM: `OK (143 tests)`** (قبل: ۱۳۶ — ۷ تست جدید `CappedOutputStreamTest`: زیر/بالا/دقیقاً سقف، cut وسطِ array، writes بعد از سقف = فقط شمارش بدون رشد، Writer، ردِ cap نامعتبر)
- **`SshEngine.kt` و `SshSetup.kt` این بار در بیلد JVM type-check شدند** (با stub JVM-only برای `HostKeyTrustStore` که Android است — stub خارج از ریپازیتوری) → ادیت‌های من روی این دو فایل از نظر type صحیح است؛ بیلد اندروید کامل اینجا ممکن نیست
- بالانس ساختاری ۴ فایل: سالم | ارجاع باقی‌مانده به `ByteArrayOutputStream` در Ssh*: صفر | `terminalOutput +=` فقط داخل `appendTerminal`

### تحلیل رگرسیون callerها

| caller | تأثیر |
|---|---|
| ترمینال | `cat /dev/urandom` → 1MB + مارکر؛ بافر صفحه 64KB — هدف مستقیم مرمی |
| batch (N سرور) | حداکثر ۵ session هم‌زمان؛ هرکدام capped |
| Security/Systemd/SinglePort | خروجی‌های واقعی (<100KB) → **بدون هیچ تغییری** در رفتار |
| SshSetup | banner installer (~۱KB) → cap هرگز trigger نمی‌شود؛ فقط timeout روشن‌تر |
| مسیر موفق (exitCode=0) | `errorMessage` فقط در timeout پر می‌شود — بدون تغییر برای بقیه |

## صداقت — چه چیزی تست نشد

- خودِ wiring داخل `SshEngine.execute` (اتصال SSH واقعی + cap روی stream زنده) در JVM **فقط type-check شد** — اجرای واقعی نیاز به سرور SSH دارد. تست دستگاه پیشنهادی: (الف) در ترمینال `head -c 500000000 /dev/zero | tr '\0' 'a'` → خروجی باید دقیقاً 1MB + مارکر بریدگی باشد و اپ زنده بماند؛ (ب) `sleep 60` → بعد از ۲۵ ثانیه پیام timeout روشن؛ (ج) batch روی ۱۰+ سرور → حداکثر ۵ اتصال هم‌زمان در `ss -tnp`
- `SshTerminalScreen` (Compose) compile JVM ندارد → فقط code-review؛ تغییر ۳ call-site ساده

## وضعیت فاز ۲

آیتم‌های ۱ تا ۱۸ تمام. باقی‌مانده از جدول security ممیزی: **H14** (نازلودینگ TLS ساکت در نصب SSH — رتبه ۱۳)، **M17** (KDF/zeroing/auto-lock Vault — رتبه ۱۵) + موارد عملکردی (H12، H16، H18، H19).
