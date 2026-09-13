# گزارش آیتم ۱۵ فاز ۲ — H11: Cloudflare بدون pagination

## مشکل (طبق ممیزی فاز ۱)

`CloudflareService.listRecords` یک درخواست با `per_page=100` می‌زد و **فقط صفحهٔ اول** را برمی‌گرداند:

- دامنه‌هایی با **بیش از ۱۰۰ رکورد DNS** ناقص دیده می‌شدند (رکوردهای ۱۰۱+ در UI نبودند → کاربر فکر می‌کرد موجود نیستند و شاید دوقلو می‌ساخت)
- `listZones` هم همین مشکل را با `per_page=50` داشت (حساب‌های بزرگ‌تر از ۵۰ zone)
- API ابری Cloudflare v4 با `page` + `per_page` صفحه‌بندی می‌کند و در `result_info` میدان‌های `page/per_page/count/total_count` را گزارش می‌دهد — اپ اصلاً از آن استفاده نمی‌کرد

## راه‌حل

**`CfPagination`** (منطق خالص، JVM-testable) + **حلقهٔ صفحه‌گرد** در هر دو endpoint:

```
page = 1
while page <= MAX_PAGES(100):
    fetch(…?per_page=N&page=page)
    append items
    next = CfPagination.nextPage(page, N, pageItemCount, total_count) ?: break
    page = next
```

قوانین توقف در `CfPagination.nextPage`:
1. صفحهٔ ناقص (کمتر از `per_page`) → آخرین صفحه (همیشه معتبر، حتی بدون `result_info`)
2. `page × per_page ≥ total_count` → کامل پوشش داده شده → توقف
3. `total_count` موجود نباشد (APIهای قدیمی/نیمه‌پشتیبانی) → فالبک به شرط ۱
4. `MAX_PAGES = 100` به‌عنوان **نیم‌امنیت** در برابر API شطرنج‌باز (حلقهٔ بی‌پایان)

تغییرات جانبی (در همان مسیر جدید):
- `fetchJson` مشترک برای دو list-fn (حذف تکرار fetch/parse/خطا)
- **خطای بهتر برای non-2xx**: API ابری Cloudflare حتی برای 401/403/429 همان envelope JSON را برمی‌گرداند؛ قبل: `"HTTP 403: {json طولانی…}"` → حالا: پیام خوانای خود API (مثلاً `Invalid API token`) — فقط در مسیر list (خطاهای `saveRecord`/`deleteRecord` دست‌نخورده ماندند؛ scope H11 = pagination)
- پارامتر `apiBase` با default واقعی — فقط برای اینکه تست JVM walker را به یک stub محلی اشاره کند (production همیشه default)

## تغییرات

| فایل | تغییر |
|---|---|
| `CfPagination.kt` | جدید — تصمیم توقف + query (منطق خالص) |
| `CloudflareManager.kt` | هر دو list-fn: حلقهٔ تمام صفحات؛ `fetchJson`/`totalCountOf` مشترک؛ پیام خطای non-2xx |
| `CfPaginationTest.kt` | جدید — ۶ تست خالص |
| `CloudflareManagerTest.kt` | جدید — ۵ تست integration روی **stub محلی Cloudflare v4** (HTTP واقعی + OkHttp + JSON واقعی) |

## شواهد

- **JVM: `OK (136 tests)`** (قبل: ۱۲۵ — ۱۱ تست جدید)
- stub testها سناریوهای واقعی را ثابت می‌کنند:
  - ۱۲۰ zone → دقیقاً صفحات ۱,۲,۳ درخواست و ۱۲۰ رکورد برگشت
  - ۲۵۰ رکورد → صفحات ۱,۲,۳ (صفحهٔ ۳ عمداً **بدون `result_info`** → توقف با partial-page signal)
  - ۳۰ رکورد → فقط صفحهٔ ۱ (درخواست صفحهٔ ۲ = باگ)
  - ۱۵۰ رکورد **بدون هیچ `result_info`** → صفحات ۱,۲ و توقف
  - `bad-token` → 403 → پیام `Invalid API token` (نه خام HTTP body)
- بالانس ساختار: سالم | callerها (`ToolkitScreens`) بدون تغییر (default param)

## صداقت — چه چیزی تست نشد

- `CloudflareManager` در JVM **با stub کامل تست شد** (نه با API زنده) — رفتار واقعی Cloudflare (rate-limit 429، تغییر فرمت envelope) روی stub شبیه‌سازی نشد
- ساید اندروید (ToolkitScreens) build/اجرا نشد → **فقط code-review شد**؛ تست پیشنهادی روی دستگاه: یک zone با >۱۰ رکورد → همهٔ رکوردها در لیست؛ zone با رکورد صفر → لیست خالی بدون خطا

## وضعیت فاز ۲

آیتم‌های ۱ تا ۱۵ تمام (شامل H4، H7، H8، H9، H10، H11). بعدی طبق ترتیب: **H12** (اگر در گزارش ممیزی فاز ۱ بمانده باشد — در گام بعد می‌خوانم و پیشنهاد می‌دهم).
