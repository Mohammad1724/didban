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

## audit backlog

- انتقال hardcodedهای قابل مشاهده در `CommandDnsManagerScreen.kt`, `CommandNetworkToolsScreen.kt`, `CommandManageServersScreen.kt`, `CommandWorkbenchTools.kt` و `CommandTunnelEditorScreen.kt` به `CommandCopy`.
- جایگزینی برچسب‌های operational و telemetry با copy/glossary.
- جایگزینی `RoundedCornerShape`/`dp`های دستی در shell و primitiveها با `CommandRadii`, `CommandSpacing`, `CommandMetrics`.
- تست عرض ۳۲۰dp، font scale ۱٫۵، RTL/LTR، text truncation و touch target حداقل ۴۸dp.
- بررسی stateهایی که در Composable با `remember` نگه‌داری می‌شوند؛ draftهای حساس نباید `rememberSaveable` شوند و state عملیاتی باید با route/server scope پاک شود.
