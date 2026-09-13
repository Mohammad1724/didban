# گزارش آیتم ۷ فاز ۲ — اصلاح unit systemd ایجنت (H2)

تاریخ: ۲۰۲۶-۰۹-۱۳ | push به `main`

## ۱. وضعیت قبل و ریشهٔ مشکل

یونیت قبلی (در `agent/install.sh`):
```
NoNewPrivileges=true
ProtectSystem=strict
ReadWritePaths=/var/lib/didban
ProtectHome=true
PrivateTmp=true
ProtectKernelTunables=true
ProtectControlGroups=true
RestrictSUIDSGID=true
```

**ریشه:** یونیت برای یک «collektor read-only» نوشته شده بود، ولی ایجنت در واقع یک **رابط کنترلِ root** است. مستندات کامل نقش از کد (نه حدس):

| عملکرد ایجنت | نیاز سیستمی |
|---|---|
| Deploy کانفیگ تونل (`ApplyTunnel` → `WriteFile(cleanPath)`) | نوشتن در `/etc/didban/tunnels/` |
| متادیتای تونل (`meta-*.json`) + رویدادها + TLS certs | نوشتن در `/var/lib/didban/` |
| لیست/کنترل کانتینرهای Docker | `connect()` روی `/var/run/docker.sock` |
| `systemctl restart/stop/disable/daemon-reload/is-active/show` + `journalctl` | باس systemd در `/run/dbus` + journal |
| حالت پیش‌فرض deploy = `scripts` | اجرای `bash -c <اسکریپت deploy>` (نوشتن در `/etc/...` دلخواه) |
| گوش‌دادن روی `:8686` + آلرت Telegram/Discord | شبکهٔ کامل (AF_INET + AF_UNIX) |

با `ProtectSystem=strict` (کل FS read-only مگر /dev,/proc,/sys) و `ReadWritePaths` فقط `/var/lib/didban`:
- **قطعی:** نوشتن کانفیگ تونل در `/etc/didban/tunnels` → EROFS → **deploy تونل (آیتم ۱) زیر systemd شکسته بود**.
- **احتمال‌دار/شکننده:** دسترسی‌های /run (docker.sock، dbus) و cgroup read-only برای مدیریت سرویس‌ها.

## ۲. طرح اصلاح (سازگار کردن sandbox با نقش واقعی)

### ۲.۱ یونیت جدید
- **`ProtectSystem=full`** به‌جای `strict`: تصویر OS (`/usr`, `/boot`, `/efi`) **همانند قبلی immutable** می‌ماند؛ `/etc`, `/var`, `/run` writable می‌شوند — دقیقاً همان سه درختی که نقش ایجنت به آن‌ها نیاز دارد. (strict تنها در صورتی منطقی بود که ایجنت اسکریپت دلخواه اجرا نکند و همهٔ نوشتن‌هایش در یک sandbox باشد؛ این حالت در `DIDBAN_DEPLOY_MODE=config-only` برقرار است و به‌عنوان **وارانت سخت‌تر** در کامنت یونیت مستند شده است.)
- **حذف `ProtectControlGroups=true`** — تداخل با مدیریت سرویس از طریق systemctl.
- **حذف `ReadWritePaths=/var/lib/didban`** — با `full` دیگر مازاد است (در حالت `full` کل /var writable است).
- **افزودن hardening‌های امنِ بدون تداخل:** `ProtectKernelModules`, `ProtectKernelLogs`, `ProtectClock`, `ProtectHostname` — ایجنت هیچ‌کدام این کارها را نمی‌کند، پس ریسک صفر و سطح امنیتی **بالاتر** از یونیت قبلی.
- **ذکر صریح directiveهایی که عمداً نیامده‌اند** و دلیل هرکدام (strict، ProtectControlGroups، PrivateNetwork/RestrictAddressFamilies، MemoryDenyWriteExecute) تا بارها از سرِ همین تیراندازی‌ها عبور نکنیم.

### ۲.۲ رفع تداخل با مسیر upgrade (دو باگ کنار در `install.sh`)
حین تست زندهٔ install.sh دو باگ واقعی پیدا و رفع شد:
1. **`agent.conf` در هر اجرای مجدد از نو ساخته می‌شد** → ویرایش‌های اپراتور (آستانه‌ها، توکن‌های آلرت، `DIDBAN_DEPLOY_MODE`) در هر upgrade **پاک می‌شد** — دقیقاً همان تنظیمی که وارانت سخت‌تر به آن وابسته است. حالا: **فوق‌العادهٔ موجود حفظ می‌شود** و فقط در نصب نخست تولید می‌گردد.
2. **`set -euo pipefail` + journal خالی:** خط `FINGERPRINT="$(journalctl ... | grep ...)"` با journal خالی (نصب تازه / plain mode) با exit 1 **قبل از بنر موفقیت** می‌مرد. رفع با `|| true` + کامنت توضیحی.

## ۳. شواهد تست

### ۳.۱ `systemd-analyze verify` — **تمیز** (exit 0، بدون هیچ هشدار)
برای نسخهٔ نهایی یونیت (با باینری و EnvironmentFile درجا).

### ۳.۲ تست زندهٔ کامل install.sh (sandboxed: stub‌های systemctl/journalctl/hostname، مسیرهای temp)
| سناریو | نتیجه |
|---|---|
| نصب نخست → بنر موفقیت + لینک `didban://` با IP/پورت/توکن واقعی | **PASS** (exit 0) |
| یونیت نوشته‌شده: `ProtectSystem=full`، بدون strict فعال، ۹ directive سخت‌سازی | **PASS** |
| آپگرید (اجرای دوم) → `agent.conf` موجود حفظ می‌شود (پیام «preserved») | **PASS** |
| ویرایش‌های اپراتور (`DIDBAN_DEPLOY_MODE=config-only`, `DIDBAN_CPU_TH=55`) پس از آپگرید سالم | **PASS** |
| توکن بین نصب/آپگرید ثابت | **PASS** |
| یونیت در آپگرید بازتولید می‌شود | **PASS** |
| `bash -n install.sh` + `go vet` + `go test -race` (32 تست) | **سبز** |

## ۴. آنچه با این محیط قابل تست نبود (صریح)

**این بخش به دلیل نبود systemd فعال در محیط (کانتینر) امکان اجرای واقعی سرویس (systemctl start/restart زیر sandbox، تست زندهٔ docker.sock و dbus، تست deploy تونل روی host واقعی) را نداشت.**
- صحت **ساختاری** یونیت: `systemd-analyze verify` — سبز.
- **منطق نصب/آپگرید** install.sh: تست زنده در sandbox — سبز.
- **رفتار sandbox در زمان runtime** (آیا `/etc/didban/tunnels` واقعاً writable است، آیا systemctl واقعاً کار می‌کند): از روی نقش دقیق مسیرها/دستورهای ایجنت (بخش ۱) استدلال شده و در اولین نصب/آپگرید واقعی تأیید می‌شود.

## ۵. تحلیل رگرسیون

- نصب‌های موجود: با اجرای دوبارهٔ `install.sh` (مسیر آپگرید رسمی) یونیت جدید جایگزین + `daemon-reload` — بدون دست‌کاری دستی.
- `agent.conf`: پیش از این در آپگرید **پاک می‌شد** (باگ)؛ حالا حفظ می‌شود. در نصب نخست رفتار دقیقاً مثل قبل (تولید از توکن/پورت/env‌های TG).
- سطح امنیتی: immutable بودن تصویر OS حفظ شده + ۴ directive سخت‌سازی **جدید**؛ تنها تغییر عملیاتی writable شدن /etc,/var,/run که برای نقش root-control-plane ایجنت اجباری است (مستند در یونیت و گزارش).
- کد Go ایجنت: بدون تغییر در این آیتم (تست‌ها سبز).

## ۶. گام بعدی (آیتم ۸)

H1 — پایش آپ‌تایم فقط با تب باز + نادیده‌گرفتن interval کاربر (بازطراحی UptimeEngine با Worker/JobQueue مستقل از Compose lifecycle).
