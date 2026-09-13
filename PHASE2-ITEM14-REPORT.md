# گزارش آیتم ۱۴ فاز ۲ — H10: الگوریتم‌های ضعیف SSH در engineهای exec

## مشکل (طبق ممیزی فاز ۱)

هر سه engineٔ SSH (SshEngine، SshSetup، SftpEngine) لیست الگوریتم‌های **پیشنهادی (negotiation)** خود را جداگانه hardcode کرده بودند:

| engine | الگوریتم ضعیفی که هنوز offer می‌شد |
|---|---|
| `SshEngine` | kex: `group-exchange-sha1`، `group14-sha1`، `group1-sha1` (SHA-1 / 1024-bit)؛ سیفر: `aes128-cbc`، `3des-cbc` (CBC → padding-oracle)؛ host key: `ssh-rsa` (امضای SHA-1)، `ssh-dss` (DSA 1024-bit) |
| `SshSetup` | دقیقاً همان لیست‌ها (copy-paste) |
| `SftpEngine` | همهٔ ضعیف‌ها: `arcfour/arcfour128/arcfour256`، `3des-cbc`، `blowfish-cbc`، `cast128-cbc`، `aes128/192/256-cbc` + همان kex/host-key ضعیف — هرچند `CheckCiphers` تمیز داشت (یعنی در practice سیفر ضعیف reject می‌شد ولی offer عمومی ضعیف بود) |

نتیجه: ناهماسانی سه‌گانه + در SshEngine/SshSetup هرگز `CheckCiphers` نبود → در برابر سرورهای قدیمی، negotiate واقعی می‌توانست `3des-cbc`/`aes128-cbc`/`group1-sha1` باشد.

## راه‌حل

**`SshAlgorithms` مشترک** (منطق خالص، JVM-testable) — یک سفید‌لیست مدرن برای همهٔ engineها:

- **kex:** `curve25519-sha256`، `curve25519-sha256@libssh.org`، `ecdh-sha2-nistp256/384/521`، `group-exchange-sha256`، `group16-sha512`، `group18-sha512`، `group14-sha256` — حذف: هر سه گروه SHA-1
- **host key:** `ssh-ed25519`، `ecdsa-sha2-nistp256/384/521`، `rsa-sha2-512/256` — حذف: `ssh-rsa` (SHA-1) و `ssh-dss`
- **سیفر:** `chacha20-poly1305@openssh.com`، `aes128/192/256-ctr`، `aes128/256-gcm@openssh.com` — حذف: arcfour family (شکسته)، 3DES، همهٔ CBC، blowfish، cast
- **`FORBIDDEN`** — فهرست الگوریتم‌های ممنوعه (شامل `group1-sha256` که با SHA-256 هم 1024-bit است) + `assertNoWeakAlgorithms()` که در تست JVM اجرا می‌شود → هر ویرایش آینده که اسم ضعیفی برگرداند، **build را شکست می‌دهد**
- `SftpEngine` همچنان `CheckCiphers` را دارد (defense in depth) — حالا دقیقاً برابر سفید‌لیست مشترک

### سازگاری (trade-off آگاهانه)
- هر اسم باقی‌مانده **از قبل در یکی از لیست‌های production بود** (فقط حذف اسم ضعیف شده) → ریسک «اسم ناشناخته برای JSch 0.2.18» صفر
- نقطهٔ مشترک سفید‌لیست ≈ **OpenSSH ≥ 7.2 (۲۰۱۶)**: سرورهای قدیمی‌تر از این دیگر negotiate نمی‌شوند. این آستانهٔ آگاهانه است: `ssh-dss` از ۲۰۱۵ و `3des-cbc`/`ssh-rsa` از ۲۰۱۷/۲۰۲۲ در OpenSSH پیش‌فرض غیرفعال‌اند؛ ایجنت دیدبان هم روی سرورهای مدرن نصب می‌شود

## تغییرات

| فایل | تغییر |
|---|---|
| `SshAlgorithms.kt` | جدید — سفید‌لیست مشترک + FORBIDDEN + guard |
| `SshEngine.kt` | سه val محلی → `SshAlgorithms.*Config()` |
| `SshSetup.kt` | همان |
| `SftpEngine.kt` | سه لیست محلی + `CheckCiphers` → `SshAlgorithms.*Config()` |
| `SshAlgorithmsTest.kt` | جدید — ۸ تست |

ایجنت Go و بقیه دست‌نخورده.

## شواهد

- **JVM: `OK (125 tests)`** (قبل: ۱۱۷ — ۸ تست جدید `SshAlgorithmsTest`):
  - guard واقعی: `assertNoWeakAlgorithms` + **mutation check** (افزودن `group1-sha1` به kex → require شکست بخورد)
  - هر اسم FORBIDDEN از همهٔ لیست‌ها غایب؛ اولویت‌ها درست (curve25519/ed25519/chacha20 اول)
  - بدون تکراری/فاصله؛ فرمت join برای JSch (باکامای، بدون space)
- grep سراسری: هیچ الگوریتم ضعیفی در production code باقی نمانده (فقط در کامنت‌های توضیحی)
- بالانس ساختار چهار فایل: سالم
- همهٔ `setConfig`های سه engine حالا از `SshAlgorithms` می‌آیند (۱۲ setConfig یکسان)

## صداقت — چه چیزی تست نشد

- ساید اندروید (سه engine) در این محیط **build/اجرا نشد → فقط code-review شد**
- **negotiation واقعی** (دست‌دست با یک سرور OpenSSH واقعی برای اطمینان از موفقیت handshake با لیست جدید) در این sandbox قابل اجرا نبود (سرور SSH زنده در دسترس نیست) — تست پیشنهادی روی دستگاه: (۱) exec + sftp + SshSetup روی سرورهای مختلف (OpenSSH 8.x لینوکس مدرن، macOS sshd)؛ (۲) آگاهانه روی یک سرور خیلی قدیمی (<7.2) → باید **شکست روشن** negotiation بدهد، نه negotiate ضعیف

## وضعیت فاز ۲

آیتم‌های ۱ تا ۱۴ تمام (شامل H4، H7، H8، H9، H10). بعدی: **H11 — Cloudflare بدون pagination** (`CloudflareManager.listRecords()`؛ پیچیدگی کم).
