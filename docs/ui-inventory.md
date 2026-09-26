# فهرست UX/UI فاز دوم

تاریخ بازبینی: ۲۰۲۶-۰۹-۲۳

## تصمیم معماری مرجع

- **مرجع نهایی محصول: `CommandTheme` + `CommandTokens.kt`**.
- `CommandTheme` تنها theme مجاز برای مسیر اصلی `CommandCenterApp` است و منبع canonical رنگ، typography، spacing، radius، elevation و RTL است.
- `DidbanTheme`/`Ds`، `AeroTheme`/`AeroTokens` و `Ui.kt` فقط compatibility/legacy هستند؛ route جدید نباید به آن‌ها وابسته شود.
- shell مشترک در `CommandShell.kt` مالک top bar، back، primary navigation، content area و responsive rail/bottom bar است.
- primitiveهای مشترک در `CommandUi.kt` و `CommandGlass.kt` مالک سطح، row، دکمه، field-state، status، loading و empty/error هستند.

## نقشهٔ مسیرها و الگوی مناسب

| primary | route | screen/owner | الگوی اصلی | وضعیت مهاجرت |
| --- | --- | --- | --- | --- |
| Servers | `FLEET` | `CommandServerHub.kt` | dashboard + dense list | canonical shell؛ نیازمند تکمیل state contract |
| Servers | `SERVER_DOSSIER` | `CommandServerHub.kt` | detail | canonical shell |
| Servers | `MANAGE_SERVERS` | `CommandManageServersScreen.kt` | form + destructive dialog | copy migration در فاز ۲ |
| Servers | `DOCKER`, `SERVICES`, `SSH`, `SFTP`, `PROCESSES` | `Command*` | scoped tool/form | مالکیت سرور حفظ شود؛ server selector مشترک |
| Servers | `TUNNELS` | `CommandTunnelEditorScreen.kt` | form + confirmation | hardcoded action labels باقی‌مانده |
| Monitoring | `UPTIME` | `CommandDiagnoseScreens.kt` | dense list + status | loading/empty/error مشترک |
| Monitoring | `RADAR` | `CommandDiagnoseScreens.kt` | comparison matrix | state contract مشترک |
| Tools | `WORKBENCH_HOME` | `CommandWorkbenchLauncher.kt` | icon launcher + category tabs | canonical؛ فقط ابزار عمومی |
| Tools | `PROXY`, `SHARE`, `SINGLE_PORT`, `BATCH`, `DEVELOPER_LAB` | `CommandWorkbench*` | form/tool | copy migration در فاز ۲ |
| Network | `NETWORK_TOOLS` | `CommandNetworkIndexScreens.kt` | dense network index | canonical مالک ابزارهای network |
| Network | `CHECK_HOST` | `CommandCheckHostScreen.kt` | public probe form/result | مستقل از server؛ بدون افزودن server |
| Network | `CF_SCANNER`, `REALITY_SNI` | `CommandCfScannerScreen.kt`, `CommandRealitySniScreen.kt` | long-running tool | shared loading/error در فاز ۲ |
| Network | `DNS` | `CommandNetworkIndexScreens.kt` | network index | route index |
| Network | `DNS_EDITOR` | `CommandDnsManagerScreen.kt` | credential + CRUD form | copy migration در فاز ۲ |
| Settings | `SETTINGS` | `CommandSettingsScreen.kt` | grouped settings | canonical؛ state ownership بازبینی شود |
| Protect | `VAULT`, `ALERTS`, `BACKUP`, `SECURITY` | `CommandProtectScreens.kt`, `CommandSecurityScreen.kt` | form + destructive confirmation | copy/state migration در فاز ۲ |

## primitive inventory

| primitive | canonical owner | استفاده |
| --- | --- | --- |
| page shell/top bar/back/help | `CommandShell.kt`, `CommandPageChrome` | همهٔ routeها |
| surface/card | `CommandSurface` / `CommandLayerSurface` | هر سطح معنایی، نه هر آیتم لیست |
| dense row | `CommandToolLink`, `CommandMetricLine`, row + `CommandRule` | ابزارهای network، node، record، service |
| primary/secondary/text action | `CommandPrimaryButton`, `CommandSecondaryButton`, `CommandTextButton` | hierarchy ثابت |
| field | Material 3 field تحت `CommandTheme` | فرم‌ها؛ حداقل ۴۸dp |
| status | `CommandStatusMark`, `CommandTelemetryPill` | health/unknown/offline |
| state block | `CommandStateBlock`, `CommandEmptyState` | error, offline, empty, retry |
| loading | `CommandLoadingState`, `CommandInlineLoading` | قرارداد مشترک فاز ۲ |
| confirmation | `CommandConfirmDialog` / `CommandDestructiveDialog` | action confirmation؛ destructive action همیشه danger |
| launcher tile | `WorkbenchLauncherTile` | فقط Tools عمومی |

## مالکیت و جلوگیری از duplication

- `WORKBENCH_HOME` فقط Proxy، Share، Single-Port، Batch و Developer Lab را نمایش می‌دهد.
- `NETWORK_TOOLS` تنها خانهٔ Check-Host، TLS، DNS، quality، Cloudflare و Reality/SNI است.
- routeهای `CF_SCANNER`, `REALITY_SNI`, `CHECK_HOST` و `DNS` نباید در launcher عمومی به‌عنوان tile تکرار شوند.
- میان‌بر به route مجاز است؛ رندر هم‌زمان یک ابزار در دو index مجاز نیست.

## state contract برای همهٔ featureها

هر feature باید یکی از این حالت‌های صریح را نمایش دهد:

1. `loading`: کاربر می‌داند چه چیزی در حال انجام است و action اصلی قفل/قابل لغو است.
2. `data/success`: نتیجه با status و زمان/منبع قابل فهم ارائه می‌شود.
3. `empty`: علت خلأ و اقدام بعدی مشخص است.
4. `offline/error`: اتفاق، اثر و اقدام بعدی؛ با retry در صورت امکان.
5. `destructive confirmation`: نام دقیق resource، اثر local/remote و action danger.

## slice stage 4 — route ownership و responsive launcher

- `CommandRouteOwner` مالک canonical هر route را صریح می‌کند؛ ابزارهای scoped مثل SSH، SFTP، Bandwidth، Tunnel و Security به Server Workspace تعلق دارند، و Network Tools/DNS/Check-Host/Cloudflare/Reality به Network Tools index.
- launcher عمومی فقط `workbenchUtilityRoutes` را render می‌کند؛ Network Tools به‌صورت route link باقی می‌ماند و آیتم‌های network-specific را در grid عمومی تکرار نمی‌کند.
- تب Network Tools از نظر accessibility به‌عنوان navigation action و نه tab انتخابی علامت‌گذاری می‌شود؛ تب‌های واقعی Workbench همچنان state انتخابی دارند.
- grid لانچر بر اساس عرض مشترک design system در ۳۲۰dp دو ستون، در عرض فرم سه ستون و در عرض rail چهار ستون می‌شود؛ حداقل ارتفاع tile و border از token می‌آیند.
- ردیف‌های Network Tools نقش button، فلش RTL-aware و spacing/radius مشترک دارند؛ indexهای عمومی نیز دیگر spacing دستی صفحه‌ای ندارند.

## slice stage 4 — accessibility و responsive audit

- primitiveهای وضعیت اکنون علاوه بر رنگ، glyph معنایی، label و `stateDescription` دارند؛ success، attention، offline، unknown و info برای screen reader و تشخیص بدون رنگ قابل تفکیک‌اند.
- loadingهای مشترک با live region محترمانه اعلام می‌شوند و اندازه/ضخامت indicator از `CommandMetrics` می‌آید؛ status، telemetry، border و gaugeهای مشترک دیگر مقدارهای هندسی صفحه‌ای ندارند.
- تب‌های واقعی Workbench `Role.Tab` و state انتخابی خود را حفظ می‌کنند؛ میان‌بر Network Tools `Role.Button` است، test tag مستقل دارد و در عرض ۳۲۰dp و font scale بزرگ حداقل ۴۸dp باقی می‌ماند.
- tileهای launcher با label و summary به‌صورت یک action قابل‌فهم merge می‌شوند؛ ردیف‌های Network Tools نیز button semantics، content description ترکیبی و حداقل ارتفاع dense-row دارند.
- هدر Network Tools در عرض کمتر از breakpoint فرم به دو ردیف تبدیل می‌شود تا عنوان، توضیح و source pill در RTL/LTR و فونت بزرگ روی هم نیفتند.
- راهنمای صفحه از `CommandCopy` برای عنوان، بخش‌ها، Copy/Copied، Tip و Warning استفاده می‌کند؛ برچسب‌های قابل مشاهدهٔ راهنما دیگر داخل Composable hardcode نیستند.
- تست deterministic جدید در `CommandAccessibilityUiTest.kt` قرارداد RTL، font scale ۱٫۵، role تب/دکمه، action بودن tile و touch target مشترک را پوشش می‌دهد.

## slice stage 4 — final product-owner audit

- فرم DNS در ردیف Record از `CommandResponsiveRow` استفاده می‌کند؛ در ۳۲۰dp و font scale بزرگ، status، Edit و Delete دیگر در یک ردیف فشردهٔ غیرقابل‌استفاده قرار نمی‌گیرند.
- ردیف actionهای پروندهٔ سرور و انتخاب server در SSH/SFTP در narrow layout به stack یا horizontal scroll قابل‌پیش‌بینی تبدیل شده‌اند؛ دکمه‌ها حداقل touch target مشترک را حفظ می‌کنند.
- SFTP file rows اکنون `Role.Button`، label ترکیبی، test tag و حداقل ارتفاع dense-row دارند؛ عنوان فایل و Copy action در narrow layout با `CommandResponsiveRow` جدا می‌شوند.
- عرض kind column و ارتفاع ویرایشگر SFTP به `CommandMetrics` منتقل شد؛ labelهای داخلی clipboard برای Proxy/SFTP نیز از `CommandCopy` می‌آیند.
- `CommandFinalAuditUiTest.kt` حالت‌های empty محلی DNS، Tunnel، Manage Servers و SFTP را در عرض ۳۲۰dp و font scale ۱٫۵ بدون شبکه قفل می‌کند.
- indexهای Network Tools و DNS اکنون از `CommandSectionTitle` و tile launcher مشترک استفاده می‌کنند؛ hero سفارشی، grouped-card و عنوان تکراری حذف شده و hierarchy آن‌ها با launcher اصلی یکسان است.

## audit backlog — non-blocking

- بررسی screenshot/state در CI برای این batch ثبت شد؛ artifact گزارش Android شامل `app/build/reports/redesign/**` است.
- warningهای مربوط به Node 20 و مهاجرت `ubuntu-latest` با pinهای Node 24 و runner ثابت `ubuntu-24.04` برطرف شدند؛ همهٔ actionها همچنان با SHA کامل pin هستند.
- پاک‌سازی گستردهٔ برچسب‌های operational/telemetry، جایگزینی geometryهای قدیمی در routeهای خارج از این audit و بازبینی عمیق state ownership می‌تواند در batch مستقل ادامه پیدا کند؛ هیچ‌کدام blocker این release نیستند.

## slice بعدی — Workbench copy و responsive

- `CommandWorkbenchTools.kt`: labelهای artifactهای Single-Port، وضعیت Proxy، Subscription و SFTP از `CommandCopy` می‌آیند؛ شناسهٔ داخلی artifactها از label قابل مشاهده جدا شد.
- فرم Single-Port و ردیف user/port در SFTP در عرض کمتر از `CommandBreakpoints.formStack` عمودی می‌شوند؛ نام‌های فنی VLESS، VMess، Trojan، SS، HAProxy و Docker Compose حفظ شده‌اند.
- Network Tools و DNS در این بازبینی primitiveهای state/loading و فرم responsive خود را داشتند؛ بازبینی بعدی `CommandTunnelEditorScreen.kt` روی ردیف‌های چندفیلدی باقی‌مانده متمرکز می‌شود.

## slice بعدی — Tunnel Editor responsive contract

- `CommandTunnelEditorScreen.kt`: endpoint، token، advanced-parameter، spoof/virtual-IP و action rows در عرض کمتر از `CommandBreakpoints.formStack` به stack عمودی می‌روند.
- در عرض بزرگ، نسبت‌های قبلی fieldها حفظ می‌شود؛ در عرض کوچک هیچ field یا action اصلی به فشردگی اجباری وابسته نیست.
- confirmation عملیاتی این صفحه از قبل primitive مشترک داشت؛ این slice فقط رفتار responsive فرم و hierarchy action را تکمیل می‌کند.

## slice بعدی — shared responsive form contract

- `CommandResponsiveRow` در `CommandUi.kt` اکنون مالک breakpoint، فاصله و تبدیل row به stack برای فرم‌های محصول است؛ صفحه‌ها دیگر threshold و branch محلی تکرار نمی‌کنند.
- `CommandMetrics.formAuxFieldWidth` عرض مرجع کنترل‌های کمکی wide مثل port و TTL است؛ در عرض کمتر از `CommandBreakpoints.formStack` همان کنترل full-width می‌شود.
- این primitive در فرم‌های Manage Servers، Server Editor، Tunnel Editor، Uptime، Services، Security، DNS، Network Tools، Check-Host، Cloudflare Scanner، Workbench/SFTP، Radar و Bandwidth به کار می‌رود.
- برای Server Editor و Radar تست‌های Compose در عرض‌های `320dp` و `400dp` اضافه شده‌اند؛ قرارداد breakpoint wide نیز در `CommandLayoutTest` قفل شده و Radar از probe تزریقی برای deterministic بودن state test استفاده می‌کند.
- این slice رفتار عملیاتی، ownership مسیرها و visible copy را تغییر نمی‌دهد؛ فقط قرارداد هندسی responsive را از سطح صفحه به design system منتقل می‌کند.
- Network Tools اکنون قرارداد state مشترک و قابل‌تست دارد: loading با cancel، empty result برای port scan، offline/error با retry و پاک‌سازی نتیجه هنگام تغییر mode؛ عملیات واقعی از طریق runner تزریق‌پذیر تست می‌شود.
- Check-Host نیز در همین قرارداد قرار گرفت: loading صریح، cancel موجود، خطای offline با retry و پاک‌سازی نتیجهٔ قبلی هنگام ورودی نامعتبر؛ تست UI آن بدون fleet یا درخواست زنده اجرا می‌شود.
- نتایج DPI/TLS در Network Tools دیگر متن فارسی hardcoded در engine نیستند؛ outcome تایپ‌شده به copy فارسی/انگلیسی map می‌شود تا نتیجهٔ عملیاتی نیز locale parity داشته باشد.
- DNS Manager برای zone و record خالی از `CommandEmptyState` مشترک استفاده می‌کند؛ تست UI آن بدون token یا حساب Cloudflare، empty و local error/retry را قفل می‌کند.
- عملیات DNS یک retry contextual برای همان action و cancel مشترک دارد؛ تغییرات token، zone، record و lookup هنگام busy قفل می‌شوند تا نتیجهٔ یک operation روی ورودی بعدی overwrite نشود.
- عنوان‌های عمومی Workbench و Services مثل Input و Evidence اکنون از `CommandCopy` می‌آیند؛ visible copy این دو workspace دیگر به انگلیسی hardcode نشده است.
- برچسب‌های telemetry مشترک CPU، Memory، Process ID و latency در Observe، Server Hub، Processes، Bandwidth، Reality SNI و Proxy Inspector از `CommandCopy`/formatter مشترک می‌آیند؛ واحدها و نام‌گذاری بین سطوح محصول یکسان مانده‌اند.
- یافته‌های تشخیصی REALITY دیگر `String`های انگلیسی در engine نیستند: `RealityFinding` typed است و blocker/warning/note در screen به copy فارسی/انگلیسی map می‌شود؛ فهرست String انگلیسی فقط برای سازگاری تست‌ها باقی مانده است.
- نتیجه‌های هر node در Check-Host نیز `CheckHostResult` typed شده‌اند؛ parser دیگر متن‌های `no data`، timeout، open، error و latency را برای نمایش UI تولید نمی‌کند و screen آن‌ها را با copy و formatter latency محلی می‌کند.
- خطا و موفقیت Backup/Restore به `BackupMessage` typed شده‌اند؛ `CommandBackupScreen` دیگر پیام‌های فارسی engine را مستقیم نمایش نمی‌دهد و summary، password، structure و restore result را از `CommandCopy` می‌گیرد.
- failureهای Uptime اکنون `UptimeFailure` typed هستند و همراه incident به‌صورت سازگار با داده‌های قدیمی ذخیره می‌شوند؛ متن خطا، title/body اعلان و نام channel هنگام render/dispatch از `CommandCopy` انتخاب می‌شود.
- تست و dispatch هشدارهای Telegram/Discord نیز از `CommandCopy` استفاده می‌کنند؛ event title، severity، field label و provider result در فارسی/انگلیسی یکسان و قابل‌ممیزی هستند.
- Proxy subscription خطاهای URL/HTTP/network را به `ProxyFailure` typed تبدیل می‌کند؛ quota fallbackها و تاریخ انقضا دیگر به فارسی داخل model قفل نیستند و SFTP نیز `SftpFailure` typed با نگاشت locale-aware برای browse/read/write/create/rename/chmod/delete/upload/download دارد.
- Tunnel deploy summary و validation به‌صورت compatibility-safe از `AutoDeployResult.localizedSummary(copy)` render می‌شوند؛ Security نیز fallback خطای فرمان و exit code را از `CommandCopy` می‌گیرد و raw output فقط به‌عنوان detail تشخیصی باقی می‌ماند.
