# گزارش آیتم ۱۹ فاز ۲ — H14: نازلودینگ TLS ساکت در نصب SSH

## مشکل (طبق ممیزی فاز ۱)

در `SshInstallTab` سرور با `useTls = r.fingerprint != null` ذخیره می‌شد. اگر خط `Cert SHA256:` از خروجی installer parse نمی‌شد — **race معروف**: `sleep 2` ثابت در `install.sh` در برابر بنر استارت ایجنت روی hostهای کند — سرور **سکوت‌آمیز به‌عنوان HTTP ساده ذخیره می‌شد** و token از آن‌بعد plaintext روی شبکه می‌رفت. هیچ هشذاری به کاربر داده نمی‌شد (بنر «نصب موفق» سبز).
ممیزی ما یک ورودی دوم پیدا کرد: **deep link parser** (`parseDeepLinkOrLogs`) هم `useTls = fp.isNotBlank()` داشت — همان race در `fp=` خالی لینکِ چاپ‌شده، plaintext ساکت.

ریشهٔ واقعی: **فقدان fingerprint باطله به‌عنوان «plaintext» تفسیر می‌شد**، درحالی‌که ایجنت به‌طراحی **TLS می‌دهد** (`DIDBAN_PLAIN` یک opt-in صریح و غیرتوصیه‌شده است).

## راه‌حل (سه لایه، defense-in-depth)

### ۱. `install.sh` — ریشهٔ سرور: race حذف شد
- `wait_for_fingerprint()`: **retry محدود** (تا ۱۵ بار × ۱ ثانیه، قابل تنظیم با `DIDBAN_FP_RETRIES`/`DIDBAN_FP_DELAY`) به‌جای `sleep 2` ثابت — تا خط `Cert SHA256:` در journal ظاهر شود یا سقف بزند
- host سریع: صفر تأخیر اضافه (اولین تلاش موفق) | host کند: تا ۱۵ ثانیه انتظار به‌جای لینکِ `fp=` خالی

### ۲. `SshSetup.kt` — ریشهٔ اپ: retry گرفتن fingerprint
- اگر token parse شد ولی fingerprint خالی بود (install.sh قدیمی/مسئلهٔ دیگر)، **۳ تلاش با backoff (۲/۴/۶ ثانیه)** روی session SSH تازه: `journalctl … grep 'Cert SHA256'`
- ساختار مجدد بدون duplication: `openVerifiedSession()` (setup + TOFU، با `HostKeyRejected` exception) و `runCappedCommand()` (streamهای capped + deadline + `timedOut`) — `installAgent` و retry هر دو از این دو استفاده می‌کنند
- نتیجه: یا fingerprint واقعی، یا `null` **با اطلاع UI**

### ۳. `ServersScreen.kt` — هرگز plaintext بدون عمل صریح کاربر
- `SshInstallTab`: `useTls = true` ثابت (واقعیتِ ایجنت) + fingerprint خالی = **TLS بدون pin** (حالت lenient کلاینت) — نه plaintext ساکت
- **بنر سه‌حالتی**: موفق (Ok سبز) / **موفق ولی fingerprint نگرفت شد (Warn زرد با متن روشن)** / شکست (Danger) — حالت Warn یعنی «بعداً fingerprint را در ویرایش سرور تأیید کنید»
- `parseDeepLinkOrLogs`: `useTls = true` — `fp=` خالی دیگر plaintext نمی‌سازد
- (آژنت‌هایی که با `DIDBAN_PLAIN=1` نصب شده‌اند — opt-in صریح — در import با toggle TLS روشن می‌افتند؛ خاموش کردن = یک عمل آگاهانه در ویرایش سرور)

## تغییرات

| فایل | تغییر |
|---|---|
| `agent/install.sh` | `wait_for_fingerprint()` + جایگزینی `sleep 2` race در main |
| `SshSetup.kt` | بازنویسی: `openVerifiedSession`/`runCappedCommand`/`fetchFingerprintRetry` (۳× backoff) |
| `ServersScreen.kt` | `useTls=true` در هر دو ورودی + بنر Warn سه‌حالتی + import `Warning` |
| `Strings.kt` | `installDoneNoFingerprint` (fa/en) |
| `agent/install_test.sh` | +۲ تست H14 (banner تاخیردار → گرفت؛ banner بدون → bounded fail) |

## شواهد

- **Bash: `install_test: 11 passed, 0 failed`** — شامل:
  - `fingerprint picked up after delayed banner (no race)`: stub journalctl بار ۱,۲ خالی، بار ۳ خط گواهی → تابع با rc 0 همان fp را برمی‌گرداند
  - `no banner → bounded failure`: ۴ تلاش خالی → rc≠0، خروجی خالی، **بدون آویختن**
  - e2e‌های قبلی با `DIDBAN_FP_RETRIES=2/DELAY=0` سریع ماندند (۱۱ تست در ~۴.۵s)
- **JVM: `OK (143 tests)`** — `SshSetup.kt` (بازنویسی) در بیلد JVM type-check شد (stub store)
- بالانس ساختاری SshSetup/ServersScreen/Strings: سالم

## صداقت — چه چیزی تست نشد

- مسیر retry در `SshSetup` (session SSH واقعی) فقط **code-review + type-check** شد؛ تست دستگاه: (الف) نصب روی host کند/با journal دیرهنگام → باید یا fp در بنر آبی باشد یا بنر زرد Warn؛ (ب) هیچ‌گاه بنر سبز + سرور plaintext نباشد
- تغییرات Compose (`SshInstallTab`/بنر/Strings) compile JVM ندارند → code-review
- `install.sh` روی host واقعی با systemd تست نشد (sandbox بدون systemd) — منطق retry با stub journalctl تست شد

## وضعیت فاز ۲

آیتم‌های ۱ تا ۱۹ تمام. باقی‌مانده از جدول security ممیزی: **M17** (KDF/zeroing/auto-lock Vault — رتبه ۱۵) + موارد عملکردی (H12 growth بی‌حد `events.jsonl`، H16 substring matching، H18 Narnia، H19 IPTables).
