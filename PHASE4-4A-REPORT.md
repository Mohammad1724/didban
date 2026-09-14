# فاز ۴ — گام 4-A: نگهبان تونل (Tunnel Watchdog) در سمت سرور

تاریخ: ۲۰۲۶-۰۹-۱۴

## ۱. مسئله

وضعیت تونل‌ها فقط وقتی شناخته می‌شد که اپلیکیشن گوشی باز باشد (حلقهٔ تست fleet). وقتی اپ بسته بود، هیچ چیز روی سرور تونل‌ها را زیر نظر نداشت: نه dead-man's-switch، نه تشخیص crash-loop، نه «سرویس زنده است ولی پورتش مرده». این گام آن نقطهٔ کور را با یک watchdog داخل خود agent بست.

## ۲. آنچه ساخته شد

### سمت agent (Go)
- **`watchdog.go` (جدید):** حلقه‌ای در agent که هر بازه (پیش‌فرض ۳۰ ثانیه) هر سرویس تونلِ ثبت‌شده را بررسی می‌کند:
  - `systemctl is-active` + `MainPID` + `ActiveEnterTimestamp` + `NRestarts` (بدون journal-scrape — همان سبک سبکِ queryهای موجود)،
  - پروب TCP روی پورت listen (از طریق 127.0.0.1؛ هر listen روی 0.0.0.0 روی loopback هم پذیرا است).
- **ماشین حالت (خالص و کاملاً تست‌شده):**
  | وضعیت | قاعده |
  |---|---|
  | `down` | سرویس غیرفعال (inactive/failed/activating/نامشخص) |
  | `crash_loop` | جهش restart (≥۲ در یک بازه) یا (≥۳ restart کل و uptime کمتر از ۲×بازه) |
  | `degraded` | سرویس active است ولی پورت listen پاسخ نمی‌دهد |
  | `up` | active و (اگر پورت دارد) listen می‌کند |
- **آلرت‌ها:** فقط روی **تغییر وضعیت** — از `AlertDispatcher` موجود (Telegram/Discord/Webhook) + لاگ در `events.jsonl` (رویدادهای `tunnel_down/up/degraded/crash_loop`). **اولین مشاهده بعد از استارت agent سکوت است** (معماری همان docker-monitor) تا تونلی که عمداً خاموش است با هر restartِ agent دوباره اسپم نشود.
- **API جدید:** `GET /api/tunnel/watchdog` (احراز هویت همان بقیهٔ endpointها) → `{enabled, interval_ms, tunnels:[...], transitions:[...]}` (حلقهٔ ۵۰ ترنزیشن اخیر).
- **پیکربندی:** `DIDBAN_WATCHDOG_ENABLED` (پیش‌فرض روشن) و `DIDBAN_WATCHDOG_INTERVAL_SEC` (پیش‌فرض ۳۰؛ محدودهٔ ۵..۶۰؛ مقدار نامعتبر = شکست روشن استارت، همان قرارداد thresholdها). خط Watchdog به بنر اضافه شد.

### قرارداد (افزوده‌شده، بدون شکستن)
- `TunnelApplyReq` / `TunnelMeta`: فیلد `port` (۰..۶۵۳۵؛  = این نقش listen نمی‌کند).
  - agent جدید + اپ قدیم → port=۰ → watchdog فقط سرویس را چک می‌کند (بدون پروب پورت).
  - agent قدیم + اپ جدید → فیلد اضافه نادیده گرفته می‌شود. صفر شکست پس‌سازگاری.
- هر **generator** پورت listen واقعی نقش‌های خودش را اعلام می‌کند (منبع واحد حقیقت = همان جایی که bind نوشته می‌شود):

| Core | پورت ایران | پورت خارج |
|---|---|---|
| BackPack | corePort | — |
| Paqet | — | corePort |
| Narnia | — (docker host-net، پورت ثابت ندارد) | — |
| Spoof-Tunnel | اولین پورت فوروارد | corePort |
| Backhaul / Rathole / GOST | corePort | — |
| Chisel / FRP | — | corePort (سرورِ خارج listen می‌کند) |
| IPTables | — | — (فرآیندی نیست) |

### سمت اپ (Kotlin)
- `GeneratedTunnelCode`: +`listenPortIran/listenPortForeign` — هر ۱۰ generator مقدارش را ست کرد.
- `autoDeployTunnel`: فیلد `port` در payload هر دو نقش (ایران/خارج).
- `ApiClient.tunnelWatchdog()` + کلاس‌های `WatchdogStatus/WatchdogTunnelState` + **پارسر تست‌شده** `TunnelEngine.parseWatchdog` (مقاوم به شکل `enabled:false` و فیلدهای ناقصِ agent قدیم).
- **جمع‌بندی چند-نقشی:** `worstWatchdogState` — شدت: up < unknown < degraded < down < crash_loop؛ «down» واقعی هرگز توسط «unknown» پنهان نمی‌شود، ولی «unknown» «up» را پنهان می‌کند (نمی‌توانیم برای نقشی که هنوز مشاهده نشده گواهی بدهیم).
- **فلیت:** بج سپری 🛡 کنار وضعیت هر تونل — فقط برای وضعیت‌های نامطلوب (down/crash_loop = danger، degraded = warn، unknown = خنثی)؛ پروب هر ۶۰ ثانیه از هر دو سرورِ صاحب تونل؛ agent دست‌نیافتنی/خاموش = بدون بج، بدون خطا (تست latency سمت گوشی دست‌نخورده).
- ۷ رشتهٔ جدید (fa + en).

## ۳. شواهد تست

| ردیف | نتیجه |
|---|---|
| کامپایل بستهٔ JVM (19 فایل) | MAIN OK — کد جدید (TunnelEngine/ApiClient/TunnelModel/Strings) این‌بار **کامپایل‌تأیید** شدند |
| تست‌های JVM | **OK (136 tests)** — ۱۰ تست جدید: قرارداد پارسر، ترتیب شدت، و جدول پورت هر ۱۰ core |
| `go build ./...` + `go vet` | OK / clean |
| `go test -race -count=1` | **ok (5.905s)** — ۹ تست جدید: ماشین حالت (down/up/degraded/crash_loop + ضد false-positive)، port round-trip در apply/meta، رد کردن port نامعتبر، سکوت تیک اول، snapshot قبل از تیک، و checkPort با listener واقعی |

**نکتهٔ صداقت:** سه بخش امکان تست واقعی نداشت و فقط از نظر کد بررسی شد: (۱) `WatchInputs` — اجرای `systemctl` واقعی فقط روی سرور زنده معنا دارد؛ (۲) بج و حلقهٔ ۶۰ ثانیه فلیت — لایهٔ Compose در این محیط (بدون SDK اندروید) قابل اجرا نیست؛ (۳) مسیر HTTP کامل `/api/tunnel/watchdog` — handler یک خطی روی snapshot است ولی تست یکپارچهٔ HTTP در این گام ساخته نشد (در 4-B با پروب یکپارچه می‌شود).

## ۴. فهرست تغییرات

| فایل | تغییر |
|---|---|
| `agent/watchdog.go` | جدید — ماشین حالت + حلقه + snapshot + ترنزیشن‌ها |
| `agent/watchdog_test.go` | جدید — ۹ تست |
| `agent/tunnel_manager.go` | +`Port` در req/meta (با اعتبارسنجی) + `WatchInputs` + `ListMeta` |
| `agent/api.go` | +endpoint `/api/tunnel/watchdog` |
| `agent/main.go` | +env/flag + استارت watchdog + خط بنر |
| `agent/{api_hardening,bandwidth}_test.go` | امضای newAPI (4 آرگومان) |
| `TunnelModel.kt` | +`listenPortIran/Foreign` در GeneratedTunnelCode + کلاس‌های Watchdog |
| `TunnelEngine.kt` | ۱۰ generator + فیلد `port` در payload + `parseWatchdog` + `worstWatchdogState` |
| `ApiClient.kt` | +`tunnelWatchdog` |
| `Strings.kt` | +۷ کلید (fa+en) |
| `AeroTunnelFleet.kt` | بج سپر + حلقهٔ ۶۰ ثانیه (code-review-only) |

موتور تونل (جنگو/استقرار/کانترلات): بدون تغییر — فقط یک فیلد افزوده‌شده به قرارداد.

## ۵. گام بعد (4-B)

مانیتورینگ چندنقطه‌ای: سرورهای خودِ کاربر به‌عنوان checkpoint (endpoint `/api/probe` + مانیتور پس‌زمینه در agent + ماتریس host×نقطه در صفحهٔ Uptime).
