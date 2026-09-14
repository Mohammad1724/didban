# فاز ۴ — گام 4-B: پایش چند نقطه‌ای (Multi-point Probing)

تاریخ: ۲۰۲۶-۰۹-۱۴

## ۱. مسئله

گوشی فقط **یک نقطهٔ مشاهده** است: وقتی گوشی به هدف نمی‌رسد، نمی‌توان «هدف قطع است» را از «شبکهٔ گوشی قطع است» جدا کرد (و بالعکس). گام 4-B سرورهای خودِ کاربر را به نقاط پروب تبدیل می‌کند: همان هدف‌های Uptime همزمان از گوشی **و** از هر سروری که agent روی آن می‌چرخد چک می‌شوند و صفحهٔ Uptime یک ماتریس host×نقطه نشان می‌دهد. وابستگی به check-host.net (نقطه‌های ثالث) هم از این راه کم‌رنگ می‌شود.

## ۲. آنچه ساخته شد

### سمت agent (Go) — `probe.go` (جدید)
- **هستهٔ پروب:** دو mode:
  - `tcp` — dial روی `host:port` (timeout ۴ ثانیه)
  - `http` — GET با اندازه‌گیری latency؛ status < 400 = up (timeout ۶ ثانیه، بدنه حداکثر ۱MB رد می‌شود؛ scheme خودکار: پورت ۴۴۳ → https)
- **اعتبارسنجی سخت (fail-loud):** نام ۱..۶۴، host ۱..۲۵۳، پورت ۱..۶۵۳۵، mode فقط tcp/http، scheme فقط http/https، path حداکثر ۲۵۶؛ **کل باچ** اعتبارسنجی می‌شود (all-or-nothing)؛ سقف ۵۰ target و رد_duplicates.
- **ماشین حالت + حافظه:** state = up/down؛ **اولین مشاهده ساکت است** (همان قرارداد 4-A و docker-monitor: targetی که عمداً خاموش است با هر restart اسپم نمی‌شود)؛ آلرت فقط روی **تغییر وضعیت** از مسیر `AlertDispatcher` موجود (رویدادهای `probe_down`/`probe_up` در `events.jsonl` + Telegram/Discord/Webhook).
- **حلقهٔ پس‌زمینه:** بازهٔ پیش‌فرض ۶۰ ثانیه، ماکزیمم ۵ پروب هم‌زمان.
- **پایداری:** ست targetها روی `<data-dir>/probes.json` ذخیره می‌شود؛ agent بعد از restart همان ست را می‌خواند (فایل خراب/غیرموجود = شروع خالی، نه crash).
- **API (همان احراز Bearer بقیهٔ endpointها):**
  - `GET /api/probe` → `{enabled, interval_ms, hostname, points:[{target, state, observed, last{up,latency_ms,detail,at}, history[≤30], transitions[≤20]}]}`
  - `PUT|POST /api/probe/targets` → `{targets:[...]}` — جایگزینی اتمیک ست (مالکیت با اپ)
  - `POST /api/probe/now` → `{"target":"نام"}` (یا بدنهٔ خالی = همه) — پروب فوری
- **پیکربندی:** `DIDBAN_PROBE_ENABLED` (پیش‌فروش روشن) و `DIDBAN_PROBE_INTERVAL_SEC` (پیش‌فرض ۶۰؛ محدودهٔ ۱۰..۳۶۰۰؛ مقدار نامعتبر = شکست روشن استارت، همان قرارداد thresholdها). خط Probe به بنر اضافه شد.

### سمت اپ (Kotlin)
- **`Probe.kt` (جدید):** مدل `ProbePoint/ProbeSnapshot` + **پارسر مقاوم** (فیلدهای جاافتاده/agent قدیم، همان قرارداد `parseWatchdog`) + `ProbeSpecs` با **هم‌ترازی دقیق با `UptimeEngine.checkTarget`**:
  | نوع هدف گوشی | نگاشت روی agent |
  |---|---|
  | HTTP/KEYWORD/HTTPS (URL کامل یا https://host) | mode=http + scheme/port/path از URL |
  | TCP / PING (PING در گوشی هم سوکت TCP است!) | mode=tcp، پورت ?: 80 |
  | SSL (بررسی اعتبارسنجی گواهی سمت گوشی می‌ماند) | mode=tcp فقط قابل‌رسایی، پورت ?: 443 |
- **`ApiClient`:** `probeStatus` / `probeTargetsSync` / `probeNow`.
- **صفحهٔ Uptime — بخش «پایش چند نقطه‌ای»:** ماتریس host×نقطه؛ ستون اول همیشه **گوشی** (از نفسِ موتور Uptime) و یک ستون برای هر سرور. هر ۶۰ ثانیه: ۱) سینک اتمپوترنت targetها روی هر agent ۲) خواندن snapshot. سرور دست‌نیافتنی = ستون کدر با برچسب «دست‌نیافتنی» — **بدون خطا و بدون spam** (همان قرارداد بج watchdog فلیت). کپی درایو، بدون جابجایی چیدمان، اسکرول افقی برای تعداد زیاد سرور.
- ۴ رشتهٔ جدید (fa + en).

### قرارداد (افزوده‌شده، بدون شکستن)
- agent قدیم + اپ جدید → `GET /api/probe` خطای HTTP می‌دهد → اپ آن ستون را «دست‌نیافتنی» نشان می‌دهد؛ هیچ crash یا آلودگی داده‌ای.
- agent جدید + اپ قدیم → پروب فقط با ست پیش‌فرضِ خالی می‌چرخد (مختصر و بی‌اثر تا اپ ست را ثبت کند).

## ۳. شواهد تست

| ردیف | نتیجه |
|---|---|
| `go build ./...` + `go vet` | OK / clean |
| `go test -race -count=1` | **ok — ۱۶ تست جدید** (اعتبارسنجی، tcp/http/https با listener واقعی، ماشین حالت: سکوت اول/آلرت down/آلرت up، سقف history و transitions، all-or-nothing باچ، persistence round-trip + فایل خراب، **تست یکپارچهٔ HTTP** هر سه endpoint شامل 401/404/405) — و **۵ اجرا متوالی سبز** (یک flake شناسایی و رفع شد: close ناهماهنگ listener در تست) |
| Android `assembleDebug` | BUILD SUCCESSFUL |
| Android `testDebugUnitTest` | **247/247 سبز** (۱۲ تست جدید: `ProbeParserTest`) |

**نکتهٔ صداقت:** دو بخش فقط از نظر کد بررسی شدند: (۱) UI ماتریس روی دستگاه واقعی (امولیتور در دسترس نبود)؛ (۲) مدارک کامل alert پروب از طریق providerهای واقعی (Telegram/Discord) — مسیر `RecordEvent` مشترک با watchdog است و قبلاً پوشش داده شده.

## ۴. فهرست تغییرات

| فایل | تغییر |
|---|---|
| `agent/probe.go` | جدید — هستهٔ پروب + ماشین حالت + حلقه + persistence |
| `agent/probe_test.go` | جدید — ۱۶ تست |
| `agent/api.go` | +۳ endpoint پروب + `pm` در `API`/`newAPI` |
| `agent/main.go` | +env/استارت/بنر + `pm` در `newAPI` |
| `agent/{api_hardening,bandwidth,tunnel_manager}_test.go` | امضای `newAPI` (۵ آرگومان) |
| `Probe.kt` | جدید — مدل + پارسر + نگاشت spec |
| `ApiClient.kt` | +۳ متد پروب |
| `UptimeScreen.kt` | بخش ماتریس چند نقطه‌ای + حلقهٔ ۶۰ ثانیه |
| `Strings.kt` | +۴ کلید (fa+en) |
| `ProbeParserTest.kt` | جدید — ۱۲ تست |

## ۵. وضعیت فاز ۴

- **4-A** (واچ‌داگ تونل) ✅ — قبل
- **4-B** (پایش چند نقطه‌ای) ✅ — این گام
- فاز ۴ با این گام کامل می‌شود؛ گام‌های بعدی پیشنهادی: تأیید runtime روی دستگاه واقعی، Compose UI test.
