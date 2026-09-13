# گزارش آیتم ۱ فاز ۲ — سخت‌سازی tunnel apply (C1 + C2)

تاریخ: ۲۰۲۶-۰۹-۱۳ | کامیت: `01f9742` (push به `main`)

## ۱. وضعیت قبل (بر اساس ممیزی فاز ۱ + تست زنده)

دو آسیب‌پذیری بحرانی در مسیر `POST /api/tunnel/apply` تأیید زنده شده بودند:

- **C1 — اجرای اسکریپت دلخواه (RCE):** فیلد `exec_script` بدون هیچ محدودیتی با `bash -c` اجرا می‌شد. هر کلاینتی که توکن داشته باشد (یا توکن دزدیده باشد) می‌توانست هر دستوری روی سرور اجرا کند.
- **C2 — نوشتن فایل در مسیر دلخواه:** فیلد `config_path` هر مسیر مطلق را می‌پذیرفت؛ در تست زنده فایل در مسیر خارج از هر سان‌باکسی (مثلاً `/tmp/...`) نوشته شد. ترکیب C1+C2 یعنی دسترسی کامل ریشه با یک درخواست HTTP.

مشکلات جانبی همزمان:
- `id`/`service_name` بدون اعتبارسنجی در نام فایل (`meta-<id>.json`) و نام واحد systemd استفاده می‌شدند (طرح مسیر).
- `status`/`delete` برای شناسه ناشناخته `200 success:true` برمی‌گرداندند (خطای ۴۰۴ نداشتند).
- اسکریپت و پیکربندی بدون سقف اندازه بودند.
- `systemctl`/`journalctl` بدون تایم‌اوت → D-Bus آویزان می‌توانست هندلر را برای همیشه قفل کند.
- در `StartTunnel`/`StopTunnel` نتیجه `loadMeta` نادیده گرفته می‌شد و در نبود متادیتا **پانیک نیل‌پوینتر** (افت کامل اِیجنت) رخ می‌داد.
- توکن کامل در هر ری‌استارت در journal systemd چاپ می‌شد (نشت تدریجی راز).
- هیچ رکوردی (audit) از deployment های دارای اسکریپت نمی‌ماند.

## ۲. تصمیمات معماری (تأییدشده در گفتگو)

اجرای اسکریپت **به‌طور طراحی‌شده حفظ می‌شود** (توکن = اعتبار ادمین سرور؛ هم‌ترازی با جریان SSH) ولی محصور می‌شود:

1. `config_path` فقط داخل سان‌باکس `/etc/didban/tunnels` (قابل تنظیم با `--config-dir` / `DIDBAN_TUNNEL_CONFIG_DIR`).
2. حالت جدید **`DeployMode`**: `scripts` (پیش‌فرض) و `config-only` — در حالت دومージェント هیچ‌گاه shell اجرا نمی‌کند.
3. رویدادهای audit: `tunnel_deploy_start` / `tunnel_deploy_ok` / `tunnel_deploy_failed` همراه با ۸ بایت اول SHA-256 اسکریپت.
4. اعتبارسنجی سخت `id` و `service_name` با `^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$`.
5. سقف‌های اندازه: بدنه درخواست ۲MB، محتوا ۱MB، اسکریپت ۶۴KB، فیلدهای متا ۱۲۸/۳۲/۱۶.
6. وضعیت‌های درست: id نامعتبر/مسیر خارج سان‌باکس → 400، تونل ناشناخته → 404، خطای داخلی → 500.
7. تایم‌اوت ۱۰ ثانیه روی همه فراخوانی‌های systemctl/journalctl.
8. توکن کامل فقط در **اولین** استارت چاپ می‌شود (نشانگر `dataDir/token.shown`)؛ بعد از آن فقط پیشوند ۸ کاراکتری.

## ۳. تغییرات کد

| فایل | تغییر |
|---|---|
| `agent/tunnel_manager.go` | بازنویسی: sentinel errorها (`errInvalidTunnelID`, `errInvalidServiceName`, `errConfigPathOutside`, `errTunnelNotFound`)، `ValidateTunnelID`، `resolveServiceName`، `resolveConfigPath` (canonicalize + prefix + بررسی sym-link escape)، `loadMetaChecked` با propagation خطا (رفع پانیک نیل)، `systemCmdContext` با cancel صحیح و 10s، `DeployMode`، سقف‌ها، پاک‌سازی فایل‌های تونل فقط از داخل سان‌باکس هنگام delete |
| `agent/api.go` | `MaxBytesReader` 2MB روی apply، رویدادهای audit با sha256، `writeTunnelError` با نگاشت 400/404/500 روی همه ۶ هندلر تونل |
| `agent/monitor.go` | `RecordEventLocal` (ثبت رویداد در حافظه+دیسک بدون ارسال alert) |
| `agent/main.go` | فلگ‌های `--deploy-mode`/`--config-dir` (+متغیرهای محیطی)، اعتبارسنجی mode بعد از `flag.Parse` (باگ مرتب‌سازی در تست زنده پیدا و رفع شد)، نمایش mode در بنر، منطق پیشوند توکن با فایل نشانگر |
| `agent/docker.go`, `agent/netinfo.go` | فقط gofmt (یافته‌های فاز ۱) |
| `README.md` | بخش «Tunnel deployment security» |
| `agent/tunnel_manager_test.go` | **جدید** — ۲۴ تست |

## ۴. شواهد تست

### ۴.۱ تست‌های خودکار (`go test -race -count=1`)

```
ok  didban-agent  1.2s   — 24/24 PASS
```

پوشش: اعتبارسنجی id (جدول‌دار، شامل `../etc`، `a b`، `a.b`، ۶۵ کاراکتر)، نام سرویس، سان‌باکس مسیر (خارج مطلق، traversal، نسبی، خالی)، **sym-link escape**، نوشتن فایل فقط داخل سان‌باکس، ثبت متادیتا، بلاک اسکریپت در config-only (با اثبات عدم اجرای اسکریپت + عدم ثبت متادیتا)، اجرای اسکریپت در scripts-mode (با فایل marker)، سقف‌های 64KB/1MB/128، 404/400/401 روی لایه HTTP با httptest، سقف 2MB بدنه، و وجود رویدادهای audit با sha256.

### ۴.۲ تست زنده روی باینری واقع‌جدا (`/tmp/didban-agent`, port 8787/8788)

| پروب | نتیجه |
|---|---|
| apply با `config_path=/tmp/.../outside/pwned.toml` | **400** و `ls` نشان داد **هیچ فایلی** خارج سان‌باکس ساخته نشده |
| apply با `config_path=/tmp/.../configs/../../etc/pwned.toml` | **400** |
| `status?id=../etc/passwd` | **400** |
| `status?id=999` (ناشناخته) | **404** (قبلاً 200/success) |
| apply با id خالی | **400** (قبلاً 500) |
| apply با `config_content` و `config_path` خالی | **400** |
| بدون توکن | **401** |
| apply معتبر + اسکریپت marker | 200؛ marker ساخته شد؛ فایل پیکربندی داخل سان‌باکس نوشته شد؛ `success:false` فقط به‌خاطر نبود systemd در container (در سرور واقعی سرویس توسط اسکریپت ساخته می‌شود و active می‌گردد) |
| `/api/events` | `tunnel_deploy_start ... script_sha256=3dfe0ae16025fbcb` و رویداد نتیجه |
| بدنه 3MB | **400** |
| `--deploy-mode config-only` (port 8788) | اسکریپت **اجرا نشد** (marker وجود نداشت)، پیام `install scripts are disabled on this agent (deploy-mode=config-only)`، فایل پیکربندی همچنان داخل سان‌باکس نوشته شد |
| `--deploy-mode bogus` | ریجکت در استارت: `invalid deploy mode "bogus"` |
| ری‌استارت agent | بنر: `Token: live-tok…` و `Mobile Link: token was printed on the first start` |

### ۴.۳ باگ‌های پیدا‌شده در حین تست و رفع‌شده

1. **پانیک نیل** در StartTunnel/StopTunnel وقتی متادیتا نبود (رفع: propagation `errTunnelNotFound` → 404).
2. **اعتبارسنجی deploy-mode قبل از `flag.Parse()`** — مقدار فلگ هرگز چک نمی‌شد (رفع: جابه‌جایی بعد از parse؛ با تست زنده `--deploy-mode bogus` اثبات شد).
3. `config_path` خالی خطای sentinel برنمی‌گرداند → در API به 500 می‌افتاد (رفع: wrap در `errConfigPathOutside` → 400).
4. نشت context در `systemCmdContext` (cancel رها‌شده) (رفع: بازگشت `(ctx, cancel)` و `defer cancel` در همه ۸ صحنه).

## ۵. تحلیل رگرسیون

- اپلیکیشن Android **نیاز به تغییر ندارد**: `TunnelEngine` قبلاً `config_path` داخل همان سان‌باکس، id عددی (Long) و سرویس `didban-tunnel-<id>` می‌فرستد — همه با قرارداد جدید سازگارند.
- `delete` همچنان idempotent با 200 است (رفتار اپ که انتظار `success:true` دارد حفظ شد).
- اسکریپت اجرا می‌شود فقط در حالت `scripts` (پیش‌فرض) → deploymentهای موجود بدون تغییر کار می‌کنند.
- lock `tm.mu` همچنان روی مسیرهای متناوب نگه داشته می‌شود؛ حالا با سقف 10s، بدترین حالت بلوک شدن هندلرها از «بی‌نهایت» به «~30s» (سه فراخوانی) محدود شده.

## ۶. بازمانده‌ها

- این بخش کاملاً تست زنده داشت؛ هیچ قسمت «فقط بررسی کد» ندارد.
- گام بعدی (آیتم ۲): C6 — اعتبارسنجی پورت + اجرای argv در جریان SecurityScreen.
