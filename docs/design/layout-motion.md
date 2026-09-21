# چیدمان و حرکت — موج دوم (۲۰۲۶-۰۹-۲۱)

موج اول فقط **هویت رنگی** را عوض کرد (`3d8a58e`). مالک بعد از نصب، درست تشخیص داد:

> «فقط رنگ‌بندی تغییر کرده بود… بعضی جاها کادرها هنوز مربع بود و خیلی زشته.»

این موج همان چیزی است که جا افتاده بود: **شکل کادرها، ترکیب‌بندی صفحه، و حرکت**.

## ۱. ریشهٔ «کادرهای مربع»

سه علت واقعی پیدا شد، هیچ‌کدام رنگ نبود:

| علت | جزئیات | اصلاح |
|---|---|---|
| `Shapes` ناقص بود | فقط `small/medium/large` ست شده بود؛ `extraSmall` روی پیش‌فرض **۴dp** M3 می‌ماند، پس تمام `OutlinedTextField`ها، منوها و دیالوگ‌ها تیز بودند | هر پنج اسلات ست شد: `extraSmall 12 · small 14 · medium 18 · large 22 · extraLarge 28` |
| شعاع کارت‌ها/دکمه‌ها کم بود | `CommandSurface`، `CommandPrimaryButton`، `MetricTile`، `StateBlock` روی ۸–۱۲dp بودند | مقیاس `CommandRadii` (زیر) |
| چیپ‌های M3 | `FilterChip` گوشهٔ **۸dp داخلی خودش** را دارد و از `Shapes` تبعیت نمی‌کند | `shape = RoundedCornerShape(CommandRadii.pill)` روی تمام چیپ‌ها |

## ۲. مقیاس گوشه‌ها — `CommandRadii`

```kotlin
object CommandRadii {
    val hero = 24.dp      // کارت قهرمان داشبورد
    val card = 22.dp      // CommandSurface، شیت‌ها، جزئیات
    val tile = 18.dp      // کارت سرور، کاشی متریک، بلوک وضعیت
    val control = 16.dp   // دکمه‌ها، تب‌ها، کنترل سگمنتی
    val field = 14.dp     // فیلدهای ورودی
    val icon = 13.dp      // مربع آیکون، سوییچ‌ها
    val pill = 999.dp     // چیپ، نشان، نوارها
    val bar = 999.dp      // پیشرفت‌سنج‌ها
}
```

قاعدهٔ قفل‌شده در تست: **هیچ سطحی زیر ۱۲dp** و ترتیب `hero ≥ card > tile > control > field > icon` حفظ شود.

## ۳. داشبورد جدید (`CommandServerHub`)

ترکیب‌بندی صفحهٔ «سرورهای من» که کاربر می‌بیند عوض شد:

```
عنوان صفحه + راهنما
«افزودن سرور» / «تازه‌سازی» + بازخورد تازه‌سازی
جست‌وجو (field 14) + چیپ‌های فیلتر (pill) + شمارش گره
┌───────────────────────────────────────────┐
│  نمای کلی            [گیج طلایی/سبز]      │  ← CommandHeroCard
│  سالم / نیازمند توجه        ۱۰۰ / از ۱۰۰   │
│  وضعیت زنده و کنترل سرورها در یک‌جا       │
│  [افزودن سرور ←]   [نشان وضعیت]           │
│  نوار تقسیم وضعیت (سالم/توجه/آفلاین/نامعلوم)│
│  ۱ گره · CPU ۳۴٪                          │
└───────────────────────────────────────────┘
[ نوار هشدار اولین گره مشکل‌دار ]   ← CommandNoticeRow
کارت گره‌ها (tile 18)
```

اجزای تازه در `CommandDashboard.kt`:

- `CommandHeroCard` + `CommandHeroGauge` — گرادیان زمرد، حلقهٔ طلایی، جاروی متحرک و نقطهٔ سر متحرک روی مقدار.
- `CommandStatusSegments` — نوار تقسیم سهم سالم/توجه/آفلاین/نامعلوم.
- `CommandStatStrip` / `CommandStatCell` — میانگین CPU، حافظه و تأخیر.
- `CommandConsumeBar` — درجهٔ سلامت با برچسب درصدی و پوشش داده.
- `CommandChipRow` / `CommandChipItem` — ردیف گره‌های خیلی فشرده.
- `CommandNoticeRow` — هشدار تک‌خطی با فشار برای رفتن به گره.

منطق خالص و تست‌شده: `commandStatusSegments(counts)` (همیشه جمعاً ۱)، `commandHealthFraction(score)`، `commandPercentLabel(fraction)`.

## ۴. حرکت — `CommandMotion`

| ثابت | مقدار | کاربرد |
|---|---|---|
| `entranceMs` | ۲۴۰ | محو+بالا آمدن هر بخش |
| `staggerMs` / `staggerMaxMs` | ۴۵ / ۱۸۰ | پله‌ای شدن کارت‌های فهرست |
| `fillMs` | ۷۰۰ | پر شدن گیج و نوارها |
| `countUpMs` | ۶۲۰ | شمارش عدد سلامت |
| `entranceOffsetDp` | ۱۲ | جابه‌جایی عمودی ورود |
| `pressScale` | ۰٫۹۷۵ | فنر فشار روی کنترل‌ها |

- `Modifier.commandEntrance(index)`، `rememberCommandFill`، `rememberCommandCountUp`، `Modifier.commandPressable`.
- **احترام به reduce-motion**: `CommandMotion.scaledMs(reduceMotion, ms)` در حالت کاهش حرکت صفر برمی‌گرداند؛ هیچ انیمیشنی پخش نمی‌شود.

## ۵. تست‌ها

`CommandLayoutTest` (۱۲ تست تازه) قفل می‌کند:

- هیچ شعاع سطحی زیر ۱۲dp نیست و ترتیب مقیاس حفظ می‌شود؛
- `pill`/`bar` کاملاً گردند؛
- تأخیر پله‌ای صعودی و سقف‌دار است؛
- بودجهٔ ورود هر کارت ≤ ۵۰۰ms؛
- reduce-motion همهٔ مدت‌ها را صفر می‌کند؛
- جمع تقسیم وضعیت = ۱، بدون داده خالی، تقسیم بر صفر ندارد؛
- `commandHealthFraction` کلمپ می‌شود و `null` برای دادهٔ ناموجود برمی‌گرداند.

```
:app:testDebugUnitTest   →  538 تست، ۰ شکست، ۰ خطا، ۳ رد‌شده
```

رندرها: `docs/design/emerald-v2-layout/` — ۱۴ تصویر واقعی Compose، شامل `fa-light-large-text-hub` (۳۲۰dp با فونت ۱۵۰٪) و مقایسهٔ قبل/بعد داشبورد در `compare-hub-before-after.png`.

## ۶. چیزهایی که در همین موج درست شد

- `CommandOverviewScreen`/`CommandIncidentsScreen`/`CommandFleetScreen` متصل به هیچ مسیری نبودند (نقشهٔ مسیرها به `CommandServerHub` می‌رود) و تست `ServerWorkspaceTest` هم همین را تضمین می‌کند؛ داشبورد واقعی روی `CommandServerHub` بازنویسی شد.
- متن شمارش از «۱ سرورها» به «۱ گره» («۱ node») تصحیح شد؛ کلیدهای `averageCpu`/`averageMemory`/`online`/`coverage` دوباره در صفحه مصرف می‌شوند تا `CommandCopyCoverageTest` سبز بماند.
