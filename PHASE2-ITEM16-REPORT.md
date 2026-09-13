# گزارش آیتم ۱۶ فاز ۲ — H13: install.sh بدون اعتبارسنجی binary + کانفیگ فریبنده

## مشکل (طبق ممیزی فاز ۱)

1. **binary بدون امضا نصب می‌شد:** `install.sh` باینری را از GitHub Releases دانلود و مستقیم `install` می‌کرد — هرچند CI فایل `didban-agent-linux-<arch>.tar.gz.sha256` تولید و همان release می‌کرد، installer **هیچ‌وقت** آن را نمی‌خواند. MITM یا release دستکاری‌شده → اجرای باینری جانشین به‌عنوان **ریشه** + نصب سرویس systemd دائمی.
2. **کانفیگ فریبنده:** `agent.conf` خطوط `# DIDBAN_CPU_TH=70` و سه همتای دیگر را نشان می‌داد، ولی agent **هیچ‌کدام** از این envها را نمی‌خواند (فقط flagهای `--cpu-th` و…) — کاربر اگر خط را uncomment کند، **سکوت‌آمیز هیچ اتفاقی نمی‌افتد**.

## راه‌حل

### ۱. verify sha256 اجباری قبل از install (`install.sh`)

- `sha256_of FILE` — با زنجیرهٔ `sha256sum` → `shasum -a 256` → `openssl dgst -sha256` (سرمهمی با توزیع‌های مینیمال)
- `verify_release TARBALL SHA256_FILE`:
  - hash باید ۶۴ کاراکتر hex باشد (فایل خراب/دستکاری‌شده → رد)
  - **نام فایل داخل .sha256 باید با نام دانلود مطابقت کند** (حمایت از .sha256 متعلق به فایل دیگر)
  - mismatch → **abort کامل قبل از extract و install** — فایل دستکاری‌شده حتی unpack هم نمی‌شود
  - دانلود نشدن خودِ `.sha256` → abort (باینری ریشه بدون امضا نصب نمی‌شود؛ no silent downgrade)
- مسیر local-binary (`./didban-agent` کنار اسکریپت) بدون دانلود/verify می‌ماند — همان‌طور که طراحی است (سورس محلی اپراتور)
- `DIDBAN_BIN_DEST` (default: `/usr/local/bin/didban-agent`) — مقصد install قابل تنظیم برای چیدمان‌های سفارشی و تست
- ساختار: `main()` + گارد `BASH_SOURCE` → اسکریپت source-able می‌شود و e2e قابل تست بدون root شد

### ۲. env fallback واقعی برای thresholds (`main.go`)

- helper `floatEnvOr(key, def)`: env خالی → default؛ **env با مقدار غیرعددی → خطای روشن در استارت (exit 1)** — نه default خاموش
- چهار threshold (`cpu-th/mem-th/steal-th/disk-th`) حالا از `DIDBAN_CPU_TH/MEM_TH/STEAL_TH/DISK_TH` می‌خوانند (flag اولویت بالاتر دارد)
- با این کار، خطوط نمونهٔ `agent.conf` که installer می‌نویسد، **واقعی** کار می‌کنند

## تغییرات

| فایل | تغییر |
|---|---|
| `agent/install.sh` | بازنویسی ساختاری + `sha256_of`/`verify_release`/`require_root` + verify اجباری در مسیر دانلود + `BIN_DEST` |
| `agent/main.go` | `floatEnvOr` + ثبت table-driven چهار threshold با env fallback |
| `agent/install_test.sh` | **جدید** — ۹ تست bash |
| `agent/main_test.go` | **جدید** — `TestFloatEnvOr` (set/empty/invalid) |

## شواهد

### ۱. Bash: `install_test: 9 passed, 0 failed` (اجرا شده در این sandbox)

| تست | اثبات |
|---|---|
| valid checksum accepted | hash واقعی → پذیرفتن + پیام `SHA-256 verified` |
| tampered file rejected | تغییر ۱ بایت → abort با `SHA-256 mismatch` + نمایش expected/actual |
| wrong-filename rejected | .sha256 برای فایل دیگر → `refusing to install` |
| malformed / empty checksum rejected | فرمت نادرست / فایل خالی → abort |
| local binary: no download | `./didban-agent` محلی → **هیچ** درخواست curl |
| download: verified before install | e2e با stub curl: verify **قبل** از extract/install |
| tampered download: nothing installed | e2e: mismatch → abort، **نه** install، باینری روی دیسک نیست |
| missing checksum asset: aborted | e2e: دانلود .sha256 شکست → `refusing to install an unverified binary` |

### ۲. Go: `ok didban-agent 1.267s` با `-race -count=1` + `TestFloatEnvOr` PASS

### ۳. تست زنده روی باینری کامپایل‌شده

| پروب | نتیجه |
|---|---|
| بدون env → `-h` | `cpu-th … (default 70)` |
| `DIDBAN_CPU_TH=55` | `(default 55)` ← env واقعاً اثر می‌گذارد |
| `DIDBAN_MEM_TH=82.5` | `(default 82.5)` ← اعشاری |
| `DIDBAN_CPU_TH=abc` | `Error: invalid DIDBAN_CPU_TH="abc": expected a number, e.g. 70` + **exit 1** |

`gofmt` و `go vet` تمیز.

## صداقت — حد و مرز این verify

- sha256 از **همان release** (همان origin HTTPS) می‌آید؛ بنابراین corruption، MITM روی مسیر، و asset ناسازگار را می‌گیرد — ولی release **کاملاً** دزدیده/جانشین‌شده (bin + sha256 هر دو) را نمی‌گیرد. سطح بعدی: امضای GPG / GitHub artifact attestation — اگر خواستید، آیتم جداگانه می‌شود.
- e2e با stub روی HTTP واقعی اجرا نشد (stub curl/tar داخل sandbox)؛ تست پیشنهادی روی دستگاه واقعی: (الف) نصب معمول → خط `SHA-256 verified` باید در output باشد؛ (ب) منفی: با `DIDBAN_REPO` به یک mirror که asset دستکاری‌شده بدهد → installer باید قبل از install بایستد.
- `main.go` در JVM/اسکیپ بدون اندروید: کامپایل + go test + اجرای زندهٔ `-h` روی همین بیلد شد؛ سرویس systemd روی host واقعی تست نشد (فقط code-review).

## وضعیت فاز ۲

آیتم‌های ۱ تا ۱۶ تمام (C1, C2, C6, C3, C4, C5, H5/H5-1, H2, H1, H3, H4, H7, H8, H9, H10, H11, H13). باقی‌مانده از جدول security ممیزی: **H20** (LocalServer بدون auth روی wildcard — رتبه ۱۱)، **M10** (stdout SSH بی‌سقف → OOM دستگاه — رتبه ۱۲)، **H14** (نازلودینگ TLS ساکت در نصب SSH — رتبه ۱۳)، **M17** (KDF/zeroing/auto-lock Vault — رتبه ۱۵) + موارد عملکردی (H12 و…).
