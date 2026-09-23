# Didban UI design system

این سند قرارداد لایهٔ بصری Command است. هر صفحهٔ جدید باید از این قرارداد استفاده کند؛ ظاهر متفاوت مجاز است، اما قانون متفاوت نه.

## منبع واحد اجرا

- Theme: `CommandTheme`
- رنگ‌ها: `CommandColors` و `CommandPalette` در `CommandTokens.kt`
- فاصله‌ها: `CommandSpacing`
- شعاع‌ها: `CommandRadii`
- اندازه‌های تعاملی: `CommandMetrics` و نقاط شکست `CommandBreakpoints`
- elevation: `CommandElevation`؛ glass به‌صورت آگاهانه shadow renderer ندارد و elevation فقط قرارداد معنایی است.
- کامپوننت‌های مشترک: `CommandUi.kt`, `CommandGlass.kt`, `CommandRedesign.kt`
- متن قابل مشاهده: `CommandCopy`؛ از متن hardcoded در صفحه‌ها استفاده نشود.

`DidbanTheme`، `Ds`، `Ui.kt` و `AeroTheme` در مسیر اصلی محصول منبع طراحی جدید نیستند. تا پایان مهاجرت فقط به‌عنوان legacy/compatibility باقی می‌مانند و صفحهٔ جدید نباید به آن‌ها وابسته شود.

## قواعد هندسی

| مورد | قرارداد |
| --- | --- |
| صفحه | `CommandSpacing.md` برای حاشیهٔ اصلی |
| فاصلهٔ کوچک | `CommandSpacing.xs` |
| فاصلهٔ بخش‌ها | `CommandSpacing.md` تا `lg` |
| سطح اصلی | `CommandSurface` |
| ردیف متراکم | حداقل ارتفاع `CommandMetrics.compactRowMinHeight` |
| کنترل قابل لمس | حداقل `CommandMetrics.touchTarget` |
| دکمهٔ اصلی/فرعی | `CommandPrimaryButton` / `CommandSecondaryButton` |
| عنوان بخش | `CommandSectionTitle` |
| وضعیت | `CommandStateBlock` یا `CommandStatusMark` |
| loading | `CommandLoadingState` / `CommandInlineLoading` |
| confirmation | `CommandConfirmDialog` / `CommandDestructiveDialog` |

هیچ صفحه‌ای نباید برای کنترل تعاملی کمتر از ۴۸dp تعیین کند. مقدار دستی فقط وقتی مجاز است که از token بزرگ‌تر باشد و دلیلش در کد روشن باشد.

## الگوهای صفحه

### Launcher
برای ابزارهای زیاد و مستقل: آیکون‌محور، grid یا فهرست کوتاه، با توضیح یک‌خطی. کارت شیشه‌ای بلند برای هر ابزار ممنوع.

### Dense list
برای ابزارهای شبکه، nodeها، سرویس‌ها و دادهٔ تکراری: ردیف، جداکننده و وضعیت. هر آیتم کارت مستقل نسازید.

### Form
ورودی‌ها در بخش‌های کوتاه گروه‌بندی شوند. هر صفحه یک action اصلی و actionهای فرعی محدود داشته باشد.

### Detail/dashboard
ابتدا خلاصه و وضعیت، سپس متریک‌ها و در پایان جزئیات بازشونده. دادهٔ خام نباید اولین چیزی باشد که کاربر می‌بیند.

## رنگ و معنا

- accent: action اصلی و انتخاب فعال
- success: پاسخ سالم/موفق
- warning: نیازمند توجه
- danger: خطا یا اقدام مخرب
- info: اطلاعات و منبع
- متن خاکستری برای دادهٔ ثانویه است، نه برای متن اصلی قابل خواندن.

رنگ را برای تزئین و وضعیت با هم قاطی نکنید؛ یک کارت سبز نباید هم‌زمان معنی انتخاب‌شده و سالم‌بودن را بدهد مگر هر دو با متن/نشان مشخص باشند.

## متن و زبان

- فارسی و انگلیسی هر دو از `CommandCopy` می‌آیند.
- نام‌های فنی تثبیت‌شده مثل `IP`, `DNS`, `TLS`, `TCP`, `SNI` حفظ می‌شوند.
- واژه‌های رابط کاربری مثل Save، Start، Lookup، Connections و Evidence در حالت فارسی نباید hardcoded بمانند.
- متن خطا باید سه جزء داشته باشد: اتفاق، اثر/دلیل قابل فهم، اقدام بعدی.

## دسترس‌پذیری و حالت‌های صفحه

هر صفحه باید حالت‌های زیر را صریح طراحی کند:

- loading
- success/data
- empty
- offline/error
- retry/cancel در صورت امکان

تمام آیکون‌های تعاملی content description داشته باشند، تب‌ها role مناسب داشته باشند و layout با فونت بزرگ، RTL و عرض ۳۲۰dp هم قابل استفاده بماند.

## مالکیت مسیرها

- `WORKBENCH_HOME`: ابزارهای عمومی و مدیریتی؛ Proxy، اشتراک VPN، Single-Port، Developer Lab و Batch.
- `NETWORK_TOOLS`: Check-Host و ابزارهای شبکه؛ TLS، DNS، کیفیت اتصال، Cloudflare و Reality/SNI.
- یک route یا ابزار نباید در دو launcher فهرست شود. مسیر میان‌بر مجاز است، کپی آیتم مجاز نیست.
