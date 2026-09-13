# گزارش آیتم ۶ فاز ۲ — Hardening API ایجنت (H5)

تاریخ: ۲۰۲۶-۰۹-۱۳ | push به `main`

## ۱. وضعیت قبل

- **`?token=` در URL پذیرفته می‌شد** → توکن در لاگ‌های proxy/وب‌سرور، تاریخچه مرورگر و هدر Referer نشت می‌کرد.
- **بدون rate-limit** → با یک توکن دزدیده‌شده، loop از process-kill / docker-restart / tunnel-apply / bandwidth قابل ساخت بود.
- **بدون access-log** → هیچ ردی از دسترسی‌ها برای عیب‌یابی/بررسی نفوذ نمی‌ماند.
- **سقف بدنه فقط برای tunnel apply** بود؛ سایر endpointها بدون سقف (خواب‌برداری حافظه با بدنهٔ چند مگابایتی).

## ۲. تحلیل پیش‌نیاز (عدم regression)

بررسی کامل مصرف‌کنندگان:
- **اپ Android:** `ApiClient` فقط `Authorization: Bearer` می‌فرستد — **هیچ** URL HTTP با `?token=` نمی‌سازد (تأیید با grep).
- **لینک `didban://...?token=...`**: onboarding یک‌باره است — URL توسط **اپ** محلی parse می‌شود و HTTP به ایجنت نیست؛ **تغییر نیافته و باید بماند** (تنها راه bootstrap اپ).
- **Status page**: server-side render است، fetch سمت کلاینت ندارد.
- نتیجه: حذف `?token=` از HTTP **صفر regression** برای اپ/لینک موبایل.

## ۳. پیاده‌سازی

### `agent/ratelimit.go` (جدید)
- Token bucket per-IP: **10 req/s پایدار + burst 20**.
- Reap اتوماتیک bucketهای idle (>5 دقیقه) + سقف 10k IP (evict قدیمی‌ها در flood) → map بی‌مرز نمی‌شود.
- بدون وابستگی خارجی.

### `agent/api.go`
- **`auth()`**: فقط Bearer header. `?token=` کاملاً حذف شد (با مستندسازی دلیل در کد).
- **`harden()` middleware** (روی کل mux):
  1. سقف بدنهٔ **سراسری 2MB** (MaxBytesReader) — سقف تکراری tunnel apply حذف شد.
  2. rate-limit (به‌جز `/health` که pure liveness است) → **429 + `Retry-After: 1`**.
  3. **access log**: `access <ip> <method> <path> <status> <duration>` — **بدون query string، بدون هدر، بدون بدنه** (نشت secret در لاگ غیرممکن). IP = فقط peer مستقیم (X-Forwarded-For اعتماد نمی‌کند؛ ایجنت مستقیم روی LAN/WAN گوش می‌دهد).
- `statusRecorder` برای ثبت status واقعی.

## ۴. شواهد تست

### ۴.۱ واحدتست (Go, -race) — سبز
| تست | نتیجه |
|---|---|
| `TestAuth_BearerAccepted_QueryTokenRejected` | Bearer 200 / query-token **401** / bearer غلط 401 |
| `TestRateLimit_BurstIsAllowedThen429` | burst 20 respected، بعد 429 + Retry-After |
| `TestRateLimit_DistinctClientsAreIndependent` | client A محدود → client B آزاد |
| `TestGlobalBodyCap_AppliesToAllEndpoints` | بدنه 2MB+ روی `/api/processes/kill` → **400** |
| `TestAccessLog_DoesNotLeakSecrets` | لاین access با method/path/status؛ **secret query و توکن در لاگ نیستند** |

suite کامل Go: **سبز** (go test -race).

### ۴.۲ تست زنده (باینری + curl)
```
?token=live-h5            → 401
Bearer                    → 200
30 درخواست سریعی        → 19×200 سپس 429 (refill 10/s بین درخواست‌ها)
access log sample:        access 127.0.0.1 GET /api/metrics 200 199µs
گرفتن "live-h5" از لاگ:   0 مورد (تمیز)
```
(پروب سقف بدنه در محیط زنده به‌خاطر rate-limit قبلی 429 گرفت؛ رفتار 400 آن در واحدتست پوشش داده شده است.)

## ۵. تحلیل رگرسیون

- اپ/لینک موبایل: بدون تغییر (فقط Bearer؛ deep-link onboarding دست‌نخورده).
- رفتار 2xx/4xx endpointها تغییر نکرده؛ فقط حالت جدید 429 در ترافیک بیش‌ازحد.
- لایهٔ tunnel apply (آیتم ۱): سقف بدنه‌اش حالا از middleware سراسری می‌آید (مقدار یکسان: 2MB) — رفتار همسان.
- `/health` بدون rate-limit ماند (liveness سبک).

## ۶. گام بعدی (آیتم ۷)

H2 — اصلاح unit systemd ایجنت: `ReadWritePaths` که همزمان با محدودیت‌های سخت‌گیرانه (Strict/ProtectSystem) باعث شکست نوشتن docker.sock و کانفیگ تونل می‌شد.
