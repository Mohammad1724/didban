# گزارش آیتم ۲۵ — یکدست‌سازی نام Unit تانل‌ها روی همه‌ی هسته‌ها (didban-tunnel-<id>)

**تاریخ:** ۱۴۰/۰۶/۴ — **فایل تغییرکرده:** `TunnelEngine.kt` (فقط اسکرپت‌های install) + تست جدید `TunnelUnitNamingTest.kt`

## مشکل (ریشه)

ایجنت دیدبان برای همه‌ی عملیات وضعیت/کنترل، unit با نام `didban-tunnel-<id>` را query می‌کند
(`tunnelStart`/`tunnelStop`/`tunnelRestart`/`tunnelDelete` و بررسی وضعیت). اما از ۱۰ هسته‌ی
تانل، ۸ هسته (BackPack، Paqet، SpoofTunnel، Backhaul، Rathole، GOST، Chisel، FRP) در اسکرپت
install خود unit با نام ثابت اختصاصی‌شان می‌ساختند:

| هسته | نام unit که اسکرپت قبلی می‌ساخت |
|---|---|
| BackPack | `backpack-server` / `backpack-client` |
| Paqet | `paqet-server` / `paqet-client` |
| SpoofTunnel | `spoof-tunnel-server` / `spoof-tunnel-client` |
| Backhaul | `backhaul-server` / `backhaul-client` |
| Rathole | `rathole-server` / `rathole-client` |
| GOST | `gost` |
| Chisel | `chisel` |
| FRP | `frps` / `frpc` |

نتیجه‌ی عملی بعد از deploy موفق:
- **Status در اپ اشتباه می‌گفت «سرویس وجود ندارد/خاموش»** — چون `didban-tunnel-<id>` اصلاً وجود نداشت.
- **Start / Stop / Restart / Delete از اپ بی‌اثر بودند** — ایجنت روی نام اشتباه دستوری اجرا می‌کرد.
- فقط Narnia (آیتم ۲۳) و IPTables (آیتم ۲۴) که تازه با همین قرارداد بازسری شده بودند درست کار می‌کردند.

## اصلاح (ریشه‌ای، بدون تغییر رفتاری)

در هر دو اسکرپت install هر هسته (ایران + خارج؛ ۱۶ جایگزینی در مجموع):

1. مسیر فایل unit: `/etc/systemd/system/didban-tunnel-${cfg.id}.service`
2. فعال‌سازی: `systemctl enable --now didban-tunnel-${cfg.id}`
3. نمایش وضعیت: `systemctl status didban-tunnel-${cfg.id} --no-pager`

**دقیقاً آنچه تغییر نکرد (عمدی):**
- کلیدهای service در YAML docker-compose (مثل `backpack-server:`) — نام‌های کامپوزر، نه unit.
- مسیرهای باینری و فایل‌های تنظیم (مثل `/etc/backpack/server.toml`) — خارج از قرارداد unit.
- منطق دانلود/استقرار، توکن، پورت‌ها — دست‌نخورده.

مغایرت‌سازی نهایی با grep: هیچ نام ثابت قدیمی (backpack-server، gost، chisel، frpc، …) در
هیچ مسیر unit یا دستور systemctl باقی نمانده است.

## تست (سازه‌وار)

تست جدید `TunnelUnitNamingTest.kt` — دو تست، پوشش هر ۱۰ هسته × هر دو اسکرپت:

1. **`every core installs a unit named didban-tunnel-id`** — برای هر هسته، هر اسکرپت install
   باید unit با نامی که ایجنت query می‌کند بسازد: یا به‌صورت inline
   (`/etc/systemd/system/didban-tunnel-42.service` + `enable --now didban-tunnel-42` +
   `status didban-tunnel-42 --no-pager`) یا از طریق متغیر (استایل Narnia/IPTables:
   `UNIT=didban-tunnel-42` + `enable $UNIT` + `status $UNIT --no-pager`)؛ و هیچ unit با
   نام ثابت قدیمی باقی نمانده باشد.
2. **`every generated install script passes bash -n`** — هر اسکرپت تولیدشده باید از نظر
   سینتکس bash سالم باشد (ضد پس‌ریزش از تغییرات رشته‌ای در بلوک‌های raw-string).

**نتیجه:**
- کامپایل ماژن: `MAIN OK` (Kotlin 1.9.24)
- کل سوت JVM: **`OK (33 tests)`** — ۱۱ اعتبارسنجی فیلد + ۱۵ Narnia + ۵ IPTables + ۲ یکدست‌سازی unit

> توجه: بخش‌های مربوط به اجرای واقعی unit روی لینوکس (systemctl enable/enable --now)
> در این سازه‌وار امکان تست واقعی نداشت و فقط از نظر کد بررسی شد؛ اما همان الگو در آیتم‌های
> ۲۳ و ۲۴ روی امولاتور سیم‌بندی iptables به‌صورت end-to-end اثبات شده است.

## تأثیر (کارکردی)

با این تغییر، برای **هر deploy جدید** در هر ۱۰ هسته:
- نمایش وضعیت تانل در اپ = وضعیت واقعی سرویس
- دکمه‌های Start / Stop / Restart / Delete واقعاً روی سرویس مستقر اثر می‌گذارند

توجه: تانل‌هایی که **قبلاً** deploy شده‌اند (با نام‌های ثابت قدیمی) خودبه‌خود اصلاح نمی‌شوند؛
برای گرفتن نام unit یکدست باید دوباره deploy شوند (redeploy روی همان سرور unit قدیمی را
جایگزین می‌کند — حذف دستی unit قدیمی بعد از deploy جدید توصیه می‌شود).

## محدودیت‌های باقی‌مانده (غیرمرتبط با این آیتم، ثبت‌شده در گزارش‌های قبلی)

1. **تصادف مسیر تنظیم در core یکسان روی یک هاست:** دو تانل Backpack روی یک سرور، هر دو
   `/etc/backpack/server.toml` می‌نویسند (و به‌طور کلی باینری مشترک). نام‌های unit دیگر
   نمی‌افتند (هر تانل `didban-tunnel-<id>` خودش را دارد) ولی مسیر تنظیم همچنان مشترک است.
   پیگیری جداگانه: مسیرهای تنظیم/باینری per-tunnel.
2. **تمیزکاری کامل در Delete:** با یکدست‌شدن نام، حذف **فایل unit** حالا درست انجام می‌شود،
   اما باینری/تنظیم/اسکرپت‌های کمکی (مثل `/etc/didban/...-rules.sh`) روی سرور می‌مانند.
   پیگیری جداگانه: اسکریپت حذف کامل per-core.

## نتیجه‌گیری

یک قرارداد واحد برای نام unit بین ژنراتور و ایجنت: `didban-tunnel-<id>`. دیگر هیچ هسته‌ای
نام اختصاصی نمی‌سازد؛ وضعیت و کنترل تانل‌ها برای همه‌ی هسته‌ها از اپ قابل اتکا می‌شود.
