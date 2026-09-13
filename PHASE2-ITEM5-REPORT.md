# گزارش آیتم ۵ فاز ۲ — حذف دادهٔ fake از سه feature (C5)

تاریخ: ۲۰۲۶-۰۹-۱۳ | push به `main`

## ۱. وضعیت قبل (C5)

| صفحه | دادهٔ دروغ |
|---|---|
| **BandwidthBenchmark** | پینگ/جیتر/پکت‌لاس واقعی بود (TCP واقعی)، اما دانلود/آپلود با `Random.nextFloat()` **کاملاً ساختگی** بود — عددی که کاربر می‌دید هیچ ارتباطی با سرور نداشت. |
| **SecurityScreen** | لیست IPهای بلاک‌شده fail2ban **hardcoded** (سه IP ثابت با زمان‌های دروغ). دکمهٔ unban واقعی بود ولی روی دادهٔ دروغ عمل می‌کرد. |
| **SystemdScreen** | لیست ۷ unit و وضعیت `active (running)` **hardcoded** — حتی اگر سروری nginx نداشت، نمایش داده می‌شد. |

**تصمیم (طبق برنامهٔ تأییدشده «real or removed»):** هر سه **واقعی‌سازی** شد — زیرساخت (SSH + TOFU از آیتم ۳، HTTP ایجنت) موجود بود و همه برای ادمن واقعی سرور ارزش دارند.

## ۲. پیاده‌سازی

### الف) ایجنت Go — endpoint واقعی بنچمارک (جدید: `agent/bandwidth.go`)
- **`GET /api/bandwidth/download?bytes=N`**: استریم بایت شبه‌تصادفی (بلاک ۶۴KB، flush) — پیش‌فرض 10MiB، سقف 200MiB، حداقل 64KB.
- **`POST /api/bandwidth/upload`**: sink که بدنه را مصرف و تعداد بایت‌ها را گزارش می‌کند (سقف 200MiB با `MaxBytesReader`).
- هر دو پشت `auth` (Bearer token).
- **۷ واحدتست Go** (سایز دقیق، سقف، 400 برای مقادیر نامعتبر، 401 بدون توکن، 405 روش نادرست، echo بایت آپلود، خواندن واقعی content-length) — همه سبز.

### ب) اپ — اندازه‌گیری واقعی بنچمارک
- **`ApiClient.openStreamingCall(server, path, body)`**: Call استریمینگ با **همان pinning TLS fingerprint** و timeoutهای بلند (120s) — refactor: `pinnedBuilder` مشترک شد.
- **`BandwidthBenchmarkScreen`**:
  - دانلود: دریافت 100MiB از endpoint ایجنت، شمارش بایت‌های واقعی در حین stream، نمایش live سرعت هر 250ms.
  - آپلود: `BenchmarkUploadBody` (100MiB دادهٔ تصادفی) با progress callback.
  - سرعت نهایی = `bytes * 8 / secs / 1e6` (Mbps واقعی).
  - **سرور قدیمی بدون endpoint → 404/405 → پیام واضح** «نسخه ایجنت پشتیبانی نمی‌کند؛ به‌روزرسانی کنید» — **عدد دروغی نمایش داده نمی‌شود**.
  - حذف state مردهٔ `sourceServerIndex` (پیشین، بی‌استفاده).

### ج) SecurityScreen — fail2ban واقعی
- `refreshBannedIps()`: `fail2ban-client status` → لیست jailها → برای هر jail (حداکثر ۱۰، معتبرسازی‌شده با `validateJail` + quoting) → `fail2ban-client status <jail>` → پارس `Banned IP list` (اعتبارسنجی IPv4).
- UI: دکمهٔ «بازخوانی» + وضعیت‌های صادقانه: درحال‌بارگذاری / «fail2ban نصب نیست» / خطا / خالی‌واقعی.
- بعد از unban موفق، لیست از سرور واقعی دوباره خوانده می‌شود (نه فقط delete محلی).

### د) SystemdScreen — unitهای واقعی
- `refreshServices()`: `systemctl list-units --type=service --state=running,failed --no-pager --no-legend --output=plain` → پارس (حداکثر ۴۰) → وضعیت واقعی `active (running)` / `failed (failed)` با آیکون/رنگ متفاوت.
- دکمه‌های **Start / Stop / Restart / Logs** (Logs قبلاً بود؛ Start/Stop اضافه شدند — همگام با `controlService` موجود).
- `unit` در دستورات با `shellQuote` (دفاع عمقی از آیتم ۲).
- بعد از هر اقدام موفق، لیست به‌روز می‌شود و وضعیت جدید در toast نمایش داده می‌شود؛ اقدام ناموفق → خطای واقعی.

### ه) `OutputParsers.kt` (جدید — خالص، JVM-testable)
`fail2banJails`، `fail2banBannedIps`، `systemdUnits` — هر توکن خروجی سرور با allowlist اعتبارسنجی می‌شود (لایهٔ سوم دفاعی). **۱۰ واحدتست** با خروجی نمونهٔ واقعی fail2ban/systemctl (شامل: خطوط نامعتبر، ورودی‌های زهرآلود `sshd;reboot`/`x/y`/`999.1.1.1`، سقف ۴۰ خط).

## ۳. شواهد تست

| لایه | شواهد |
|---|---|
| Go (32 تست کل) | `go test -race` سبز؛ شامل ۷ تست جدید bandwidth |
| Kotlin (44 تست کل) | JVM run: **OK (44 tests)**؛ شامل ۱۰ تست جدید OutputParsers با دادهٔ واقعی |
| **تست زندهٔ endpoint ایجنت** | `curl` دانلود 30MB: `size=31457280 code=200` (سرعت 4.6GB/s روی loopback کانتینر)؛ آپلود 30MB: `{"received_bytes":31457280}`؛ بدون توکن: 401؛ درخواست 1GB → clamp روی 200MB |

## ۴. آنچه با این محیط قابل تست نبود (صریح)

**این بخش به دلیل نبود Android SDK و gradle wrapper و نبود سرور SSH/fail2ban واقعی در محیط، امکان build اپ، اجرای تست‌ها از مسیر Gradle، و تست زندهٔ UI سه صفحه (اندازه‌گیری روی لینک واقعی، fetch fail2ban، fetch systemd) را نداشت.**
- منطق پارس (خروجی fail2ban/systemctl): **test-executed روی JVM با دادهٔ واقعی** — سبز.
- endpoint ایجنت: **test-executed + تست زنده با curl** — سبز.
- سیم‌کشی Compose (سه صفحه + ApiClient): فقط از نظر کد بررسی شدند (بررسی diff + توازن ساختاری + تطبیق با API موجود) و در اولین build/اجرای واقعی تأیید می‌شوند.

## ۵. تحلیل رگرسیون

- **سرورهای با ایجنت قدیمی:** بنچمارک → پیام به‌روزرسانی (نه عدد دروغ). دو صفحهٔ SSH: بدون تأثیر (فقط fetch اضافه شده).
- **کنتراکت‌ها:** endpointهای جدید additive؛ امضای `SshEngine`/`ApiClient` فقط پارامتر پیش‌فرض/متد جدید گرفته‌اند.
- **رفتار unban:** همان، ولی حالا روی دادهٔ واقعی + refresh بعدی.
- **دادهٔ fake حذف شد:** هیچ hardcoded IP/unit در این سه صفحه باقی نمانده (تأیید با grep).

## ۶. گام بعدی (آیتم ۶)

H5 — Hardening API ایجنت: حذف `?token=` (فقط Bearer)، rate-limit، access log، سقف بدنه (جزییاتش در آیتم ۱ برای tunnel اعمال شده — این آیتم برای همهٔ endpointها).
