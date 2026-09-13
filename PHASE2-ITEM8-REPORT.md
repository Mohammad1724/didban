# گزارش آیتم ۸ فاز ۲ — پایش آپ‌تایم واقعی در پس‌زمینه + اجرای interval کاربر (H1)

تاریخ: ۲۰۲۶-۰۹-۱۳ | push به `main`

## ۱. وضعیت قبل (از کد)

```kotlin
// UptimeScreen — حلقهٔ چک داخل Compose
LaunchedEffect(Unit) {
    while (true) {
        for (target in targets) { if (!target.isPaused) UptimeEngine.checkTarget(target, ctx) }
        saveTargets()
        delay(15_000)          // ← interval ثابت ۱۵ ثانیه
    }
}
```

| باگ | اثبات |
|---|---|
| **پایش فقط با تب باز** | حلقه در `LaunchedEffect` صفحه بود → با ترک تب، disposed و پایش قطع می‌شد. با وجود عنوان «24/7 Heartbeat» |
| **interval کاربر نادیده‌گرفته می‌شد** | `target.intervalSec` (قابل تنظیم ۵ تا ۳۶۰) فقط در UI چاپ می‌شد («Interval: 30s»); چرخهٔ واقعی همیشه ۱۵s بود |
| **race state management** | صفحه کپی لوکال از Prefs داشت + حلقه هر ۱۵s write می‌کرد؛ باگ‌های read-modify-write (مثلاً restore از بکاپ) محتمل بود |
| **کد مرده** | `UptimeEngine.liveTargets` تعریف شده بود ولی هرگز استفاده نمی‌شد |
| **چک‌های سری** | چک‌ها پشت‌سرهم اجرا می‌شدند (هرکدام تا ۸s) → N مانیتور = تا 8N ثانیه هر چرخه |

## ۲. معماری جدید

**اصل:** یک منبع واحد واقعیت (single source of truth) + یک runtime بلندعمر.

```
MonitorService (foreground service، سوییچ «پایش» در تب سرورها)
   └─> UptimeEngine (singleton، scheduler)
          ├─> UptimeScheduler (خالص، pure، JVM-testable) — جدول nextCheckAt
          ├─> liveTargets: StateFlow — UI فقط observe می‌کند
          └─> checkTarget (probe HTTP/TCP/PING/KEYWORD/SSL + transitions + alert)
UptimeScreen: collectAsState() — دیگر حلقهٔ خودش را ندارد
```

### `UptimeScheduler.kt` (جدید — خالص، بدون وابستگی Android)
- `dueNow(now, ids, pausedIds)` — چه مانیتورهایی الان نوبتشان است (paused هرگز).
- `markChecked(id, now, intervalSec)` — نوبت بعد = الان + **interval کاربر** (clamp: **حداقل ۵s، حداکثر 3600s** — یعنی تایپ «1 ثانیه» در UI دیگر network/disk را flood نمی‌کند).
- `reschedule(id)` (هدف جدید/ویرایش‌شده/فعال‌شده → چک فوری) + `prune` (هدف حذف‌شده).
- **`@Synchronized` روی همهٔ متد‌های stateful** — دسترسی هم‌زمان از IO loop + coroutineهای چک + main (UI).

### `UptimeEngine.kt` (بازنویسی بخش runner)
- `liveTargets: StateFlow` — **منبع واحد واقعیت**. `monitoring: StateFlow<Boolean>` برای وضعیت موتور.
- `start(ctx)`/`stop()` — توسط `MonitorService` (idempotent).
- **Scheduler loop** (tick ۲s در IO): reconcile با disk (فقط اگر مجموعهٔ idها تغییر کرده — restore بکاپ بدون ازبین‌رفتن heartbeatها) → شناسایی dueها → **اجرای موازی** با `Semaphore(5)` + guard `inFlight` (سینک‌شده) تا یک مانیتور هرگز هم‌زمان دوباره چک نشود.
- **mutate‌های UI:** `upsert` / `remove` / `togglePause` / `checkNow` — هرکدام normalize (clamp interval) + persist + emit.
- `commit()`: emit با list-instance جدید (recomposition درست) + `Prefs.saveUptimeTargets`.
- منطق `checkTarget` (probe + UP↔DOWN transitions + اعلان + AlertEngine) **بدون تغییر** — فقط محل راه‌اندازی‌اش عوض شد.

### `MonitorService.kt`
- `onStartCommand` → `UptimeEngine.start(applicationContext)` — پایش آپ‌تایم حالا در **همان موتور بلندعمر** پایش سرورها اجرا می‌شود.
- `onDestroy` → `UptimeEngine.stop()`.

### `UptimeScreen.kt`
- حلقهٔ `LaunchedEffect` و `saveTargets()` **حذف** شد. صفحه فقط `collectAsState()` می‌کند.
- همهٔ mutateها به متد‌های موتور می‌روند (pause/add/edit/delete/Test Now).
- **نوار وضعیت صادقانه** در hero card: «⚙️ پایش پس‌زمینه فعال — چک‌ها طبق interval هر مانیتور» یا «⏸ پایش پس‌زمینه خاموش — از تب سرورها روشن کنید» — کاربر دیگر نمی‌داند پایش واقعاً فعال است یا نه (مشکل UX اصلی H1).
- **اصلاح کلیدی UI:** `remember(target.heartbeats.size)` → `remember(target.lastChecked)` — sparkline/telemetry حالا هر چک به‌روز می‌شد (قبلاً وقتی heartbeatها به سقف ۳۰ می‌رسیدند، size دیگر عوض نمی‌شد و نمودار فریز می‌ماند!).

## ۳. شواهد تست

| لایه | شواهد |
|---|---|
| **JVM: `UptimeSchedulerTest` (۹ تست جدید)** | clamp (0/3/−120→5، 99999→3600)، due فوری برای id نامعلوم، excluded بودن paused، عدم-due بودن پیش از انقضای interval، due دقیقاً در deadline، clamp در schedule، reschedule فوری، prune بدون اثر بر بقیه — **OK (53 tests کل)** |
| ساختاری | توازن brace/paren = 0/0 در هر ۴ فایل ویرایش‌شده؛ `saveTargets`/`delay(15_000)`/`Prefs` از صفحه حذف (grep صفر) |

## ۴. آنچه با این محیط قابل تست نبود (صریح)

**این بخش به دلیل نبود Android SDK و gradle wrapper، امکان build اپ، اجرای Compose/Service روی دستگاه، و مشاهدهٔ زندهٔ foreground service و چک‌های واقعی شبکه را نداشت.**
- منطق سیدولینگ: **تست‌شده روی JVM** (9 تست) — سبز.
- سیم‌کشی Compose/Service (`collectAsState`، `MonitorService` → engine): فقط از نظر کد بررسی شد (بازبینی کامل diff + APIهای موجود + توازن ساختاری) و در اولین build/اجرای واقعی تأیید می‌شود.

## ۵. تحلیل رگرسیون و تصمیمات

- **تغییر رفتار (مستند):** پایش آپ‌تایم حالا با سوییچ «پایش» موتور (تب سرورها) روشن/خاموش می‌شود — همان سوییچ که پایش سرورها را کنترل می‌کند. منطقی و یکپارچه؛ و UI حالا صادقانه وضعیتش را نشان می‌دهد (قبلاً کاربر فکر می‌کرد 24/7 پایش دارد).
- **بکاپ/ریستور:** reconcile بر اساس مجموعهٔ idها — restore بدون overwrite روی heartbeatهای حافظه (تا زمانی که idها فرق نکنند).
- **Widget/Toolkit:** باز هم از Prefs می‌خوانند — موتور بعد از هر چک/mutation persist می‌کند، پس داده‌های widget تازه می‌ماند (دقیقاً مثل قبل، فقط منبع نوشتن واحد شد).
- **باتری/CPU:** پیش از این حلقه ۱۵s فقط با تب باز + `saveTargets` هر 15s. حالا: tick 2s فقط یک اسکن حافظهٔ کوچک در IO؛ چک‌ها موازی و سقف‌دار؛ persist فقط بعد از چک‌های واقعی یا mutate (نه هر ۱۵s کورکورانه).
- **امنیت/پایداری:** `inFlight` و `nextCheckAt` race-free شدند (سینک)؛ سقف ۵ چک موازی؛ interval clamp.
- کد Go/ایجنت: در این آیتم بدون تغییر.

## ۶. گام بعدی (آیتم ۹)

H3 — توکن/کلید تونل persist نمی‌شود → redeploy جفت‌نره (xray/inbound) را می‌شکند.
