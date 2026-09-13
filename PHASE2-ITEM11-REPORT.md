# گزارش آیتم ۱۱ فاز ۲ — H7: سه poller موازی + OkHttpClient تازه در هر request

## مشکل (طبق ممیزی فاز ۱)

هر سه حلقهٔ `MonitorService` (بک‌گراند)، `ServersScreen` (۱۰ ثانیه) و `DashboardScreen` (۱۰ ثانیه + history)
مستقل از هم روی `/api/metrics` زدن، و علاوه بر آن **هر request یک `OkHttpClient` تازه** می‌ساخت
(`ApiClient.clientFor` → `OkHttpClient.Builder().build()` در هر فراخوانی). یعنی:

- با N سرور: تا ۳N درخواست در هر بازه، بدون reuse اتصال (هر client، ConnectionPool + dispatcher + TLS manager خودش را داشت)
- handshake TLS تازه برای تقریباً هر request → مصرف دیتا/باتری و latency اضافه
- thread churn مداوم روی دستگاه‌های ضعیف
- سه منبع جدا برای «latency» و «online/offline» که با هم هم‌خوانی نداشتند

## راه‌حل (رویشه، نه پچ سطحی)

### ۱) استخر client سراسری — `HttpClientPool` + `ClientCache` + `CertFingerprint`

- **یک `ConnectionPool` مشترک** (۸ اتصال idle، TTL ۵ دقیقه) → keep-alive و session-resumption TLS بین همهٔ صفحه‌ها/حلقه‌ها
- **یک `OkHttpClient` بلندعمر برای هر کلید سرور** — کلید: `(host, port, useTls, fingerprint_هموارشده)` — در یک LRU محدوده‌دار (ظرفیت ۱۶)؛ کلاینتِ evict‌شده dispatcherش shut-down می‌شود (ConnectionPool مشترک هرگز evict نمی‌شود)
- کلید و LRU و نرمال‌سازی fingerprint **منطق خالص** هستند (بدون وابستگی به Android) و با تست JVM پوشش دیده‌اند
- **مکانیزم TOFU حفظ شد**: trust manager همان منطق قبلی (فینگربرداشت SHA-256 از `cert.encoded`، مقایسه با pin، خطای mismatch) اما حالا fingerprint مشاهده‌شده را در یک `AtomicReference` per-client می‌نویسد؛ `ApiClient.lastSeenFingerprint` بعد از هر request از همان holder می‌خواند. فرمت ذخیره‌شده **بدون تغییر** است (hex کوچک بدون جداکننده) → سرورهای pin‌شدهٔ موجود کاربر بدون هیچ تغییری کار می‌کنند
- `hostnameVerifier` همچنان خاموش است (طراحی: سلف‌سایند + pin — امن‌سازی با فینگربرداشت)
- client استریمینگ (تست پهنای باند، ۱۲۰ ثانیه) همان pool و TM را به‌اشتراک می‌گذارد ولی short-lived است و `releaseStreaming()` dispatcher آن را آزاد می‌کند (هر دو جای مصرف در `BandwidthBenchmarkScreen` در `finally` صدا می‌زنند)
- **تازگی کلید**: `PollingCoordinator` کلید هر سرور را در هر tick با ذخیرهٔ قبلی مقایسه می‌کند؛ با re-pin / تغییر پورت / toggle TLS / حذف سرور، کلاینتِ قدیمی evict می‌شود تا هرگز با pin قدیمی اتصال نکند (این کار متمرکز است و همهٔ مسیرهای UI را می‌گیرد)

### ۲) یک poller واحد — `PollingCoordinator`

- یک حلقهٔ process-lifetime (شروع در `DidbanApplication.onCreate`) همهٔ سرورها را تغذیه می‌کند و نتیجه را در `Repo` می‌نویسد؛ **همهٔ صفحه‌ها همان منبع را مشاهده می‌کنند**
- زمان‌بندی per-server با **backoff**: سرور سالم در فاصلهٔ کاربر (`poll_sec`)، سرور مُرده حداکثر هر ۲ دقیقه (سقف `MAX_BACKOFF_MS`)؛ یک موفقیت، فاصله را فوراً برمی‌گرداند
- **منطق alertها دست‌نخورده از `MonitorService` جابه‌جا شده** (محل اعلان محلی با cooldown ۱۰ دقیقه، SERVER_DOWN / SERVER_RECOVERED / CPU / RAM spike)؛ دروازهٔ روشن/خاموش دقیقاً مثل قبل: alert فقط وقتی موتور بک‌گراند (`MonitorService.isRunning`) روشن باشد
- وقتی موتور خاموش است، polling فقط در حالت **foreground اپ** انجام می‌شود (`ProcessState` از onStart/onStop فعالیت) → معنای قبلی «خاموش = بدون polling بک‌گراند» حفظ شده
- `MonitorService` حالا فقط: نگه‌داشتن process در بک‌گراند (اعلان foreground)، نمایش `isRunning`، و چرخهٔ `UptimeEngine` (H1) — همچنان `START_STICKY`

### ۳) صفحه‌ها

- **ServersScreen**: حلقهٔ poll حذف شد؛ sparklineهای CPU از آپدیت‌های `Repo` ساخته می‌شوند (مشاهده‌گر، نه poller)
- **DashboardScreen**: metrics/latency/error از `Repo` (همان منبع لیست سرورها)؛ حلقهٔ صفحه فقط endpointِ `history` (مختص داشبورد) را در فاصلهٔ کاربر تازه می‌کند؛ دکمهٔ Refresh → `requestNow` (بررسی فوری در tick بعد) + history
- `BandwidthBenchmarkScreen`: release client استریمینگ بعد از هر call

## تغییرات

| فایل | نوع |
|---|---|
| `HttpClientPool.kt` | جدید — استخر client + TM پینینگ (انتقال‌یافته از ApiClient) |
| `ClientCache.kt` | جدید — `ClientKey` + `BoundedLru` (منطق خالص) |
| `CertFingerprint.kt` | جدید — فینگربرداشت + نرمال‌سازی (منطق خالص) |
| `PollSchedule.kt` | جدید — backoff (منطق خالص) |
| `PollingCoordinator.kt` | جدید — poller واحد + alertها |
| `ProcessState.kt` | جدید — ردیابی foreground |
| `ApiClient.kt` | بازنویسی — clientها از استخر؛ API عمومی دست‌نخورده |
| `MonitorService.kt` | لاغر — بدون poll loop (۱۲۹ خط حذفی خالص) |
| `DashboardScreen.kt` / `ServersScreen.kt` | مصرف‌کنندهٔ Repo؛ حلقه‌های poll حذف |
| `BandwidthBenchmarkScreen.kt` | release streaming |
| `DidbanApplication.kt` / `MainActivity.kt` | شروع coordinator + hookهای foreground |
| `Model.kt` | فقط کامنت |

ایجنت Go دست‌نخورده است.

## شواهد

- **JVM: `OK (109 tests)`** (قبل: ۷۲ — ۳۷ تست جدید):
  - `ClientCacheTest` (۱۱): LRU/evict/callback/سقف/آزمایش concurrency
  - `PollScheduleTest` (۵): backoff + سقف + floor
  - `CertFingerprintTest` (۶): vector شناخته‌شده با openssl + فرمت‌ها
  - `HttpClientPoolTest` (۱۵): **TM پینینگ مستقیم** (TOFU capture، پذیرش pin، رد mismatch با پیام، empty chain، round-trip) + **OkHttp واقعی روی سرور HTTP محلی** (پارس metrics، 404→ApiException، error field در post، استریمینگ + release، cache per-key، evict + dispatcher shutdown، pool مشترک standard/streaming، holder فقط برای TLS)
- ساختار: بالانس براکت/پرانتز همهٔ فایل‌های دست‌خورده، بررسی ارجاع‌های قدیمی (صفر)
- **Go agent: دست‌نخورده** (تست‌های قبلی `ok didban-agent` معتبر می‌مانند)

## چه چیزی باقی ماند و چه چیزی تغییر کرد (صریح)

**دست‌نخورده:** timeoutها (8s connect / 15s read / 120s streaming)، فرمت pin، `hostnameVerifier` off، متن و cooldown اعلان‌ها، `START_STICKY`، چرخهٔ `UptimeEngine`.

**تغییرهای رفتاری آگاهانه:**
1. صفحه‌ها حالا در **فاصلهٔ انتخابی کاربر** (poll_sec) تازه می‌شوند، نه ۱۰ ثانیهٔ hardcode — یعنی تنظیم کاربر واقعاً مؤثر است و کل صفحه‌ها هم‌گام‌اند
2. **backoff**: سرور مُرده دیگر هر چرخه probe نمی‌شود (حداکثر هر ۲ دقیقه) → کشف recovery تا ۲ دقیقه دیرتر (در قبال جلوگیری از کوبیدن سرور مُرده و توقف چرخه polling)
3. خطای mismatch حالا پیشوند expected/observed را نشان می‌دهد (قبلاً فقط «certificate fingerprint mismatch» — برای عیب‌یابی عملیاتی کافی نبود)
4. sparkline CPU مقدار تکراری پیاپی را دوباره اضافه نمی‌کند (خط مصنوعی دیگر صاف نمی‌شد)
5. پنجرهٔ latency در داشبورد ۱۲۰ نقطه است اما با فاصلهٔ کاربر پر می‌شود (پنجرهٔ زمانی بلندتر)

## محدودهٔ تست — صداقت

- فایل‌های سمت Android (`PollingCoordinator`، `MonitorService`، صفحه‌ها، `ProcessState`) در این محیط **build/اجرا نشدند** (SDK اندروید در دسترس نیست) → **این بخش‌ها فقط از نظر کد بررسی شدند** (بالانس، ارجاع‌ها، تطبیق با الگوهای قبلی مثل `UptimeEngine`)
- JDK این sandbox از نسخهٔ کامل جاوا **کم‌جا** است (`SSLSocketServerSocket` و ساخت socket سرور TLS اصلاً وجود ندارد و حتی parse خام DER شکست می‌خورد) → **handshake TLS واقعی روی سرور زنده در sandbox قابل اجرا نبود**؛ به‌جای آن، همان trust manager که OkHttp در هر handshake صدا می‌زند مستقیم تست شده (۵ تست) و لایهٔ OkHttp روی سرور HTTP واقعی تست شده است
- مسیر کامل TLS (OkHttp ↔ سرور TLS واقعی) و رفتار حلقهٔ coordinator روی دستگاه: **نیازمند تست روی دستگاه/AVD در مرحلهٔ بعد**

## ریسک باقی‌مانده / پیشنهاد

- حلقهٔ latency در `TunnelScreen` (۱۵ ثانیه، تست socket تونل) و حلقهٔ benchmark یک‌بارمصرف **خارج از scope این آیتم** بودند (دیتای متفاوت، نه `/api/metrics`) و دست‌نخورده ماندند — اگر بخواهید، می‌توانند در آیتم بعدی به همین الگو (observer over Repo) تبدیل شوند
- تست روی دستگاه: با روشن/خاموش بودن موتور بک‌گراند، باز کردن داشبورد/لیست، و re-pin کردن یک سرور (که باید بلافاصله با کلید جدید جواب دهد)
