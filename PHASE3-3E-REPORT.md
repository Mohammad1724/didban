# فاز ۳ — گام 3-E: برابری (Parity) با نسخهٔ قدیم + Onboarding + سیم‌کشی پالت

تاریخ: ۲۰۲۶-۰۹-۱۴

## ۱. ممیزی برابری — هیچ صفحه‌ای از نسخهٔ قدیم حذف نشده

**روش:** فهرست کامل ۶۵ فایل در `org/didban/monitor` + ردیابی نقطهٔ ورود همهٔ `Screen`ها (grep فراخوانی‌ها در کل درو) + نگاشت ۵ تب نوار پایین قدیمی بر ساختار v3.

### نگاشت تب‌های قدیمی → v3 (یک‌به‌یک)

| تب قدیمی | مقصد در v3 | وضعیت |
|---|---|---|
| Servers | HUD «مدیریت سرورها» → اورلی کامل `ServersScreen` (همان نسخه، بدون تغییر) | ✅ دسترس‌پذیر |
| Tunnels | صفحهٔ ۱ دک (AeroTunnelFleet + شیت فاز ۲) | ✅ دسترس‌پذیر |
| Uptime | صفحهٔ ۲ دک (AeroUptimePage) | ✅ دسترس‌پذیر |
| Network | صفحهٔ ۳ دک (NetworkCloudScreen جاسازی‌شده) | ✅ دسترس‌پذیر |
| Vault | صفحهٔ ۴ دک (VaultToolsScreen جاسازی‌شده) | ✅ دسترس‌پذیر |
| Dashboard | جایگزین‌شده با نقشهٔ فضایی + کاکپیت (مصوب 3-A) | ✅ مطابق طرح |
| Settings (آیکون) | آیکون ⚙ HUD → `SettingsScreen` بدون تغییر | ✅ دسترس‌پذیر |

### صفحات ابزار — همه در جایی که قبلاً بودند

| صفحه | محل فیزیکی (بدون تغییر) | مسیر در v3 |
|---|---|---|
| SshTerminal / BatchExec / SinglePort / Sftp / ProxyTester / BandwidthBenchmark / Systemd / Security | ساب‌تب‌های DevLabScreen در `ToolkitScreens.kt` | دک → Vault/Tools → DevLab (یا مدیریت سرورها → سرور → تب DevLab) |
| WorldPortProbe | داخل NetworkHub (`ToolkitScreens.kt`) | دک → صفحهٔ Network |
| Cloudflare / Alerts | داخل NetworkCloudScreen | دک → صفحهٔ Network |
| Vault / Backup | داخل VaultToolsScreen | دک → صفحهٔ Vault |
| SshSetup | فقط درون مایه (Wizard) ServersScreen | مدیریت سرورها → افزودن → روش SSH |

**نتیجه:** صفر کاهش قابلیت. تمام ۱۲ صفحهٔ ابزار + تنظیمات + افزودن سرور در v3 زنده‌اند؛ هیچ کد قدیمی حذف نشده (تنها `TunnelScreen.kt` در 3-D حذف شد — جایگزینش AeroTunnelFleet + شیت کامل فاز ۲ است).

## ۲. شکاف‌های یافت‌شده و رفع‌شده در این گام

### شکاف ۱: Onboarding نبود (اولین اجرا)
ساختار v3 برای کاربر تازه‌کار شفاف نیست (نقشه + سوییپ + پالت). راه‌حل:

- `AeroOnboarding.kt` (316 خط، خودبسنده): ۴ صفحه — نقشهٔ زنده / سوییپ افقی (دک) / شیت‌های سیار / پالت فرمان.
- نشان‌ها (gliph) به‌صورت Canvas دست‌ساز با رنگ‌های خود تم (نه تصویر) — در هر دو تم درست دیده می‌شوند.
- فقط یک بار: فلگ `onboarding_seen_v3` در Prefs (پیش‌فرض false).
- رد شدن با دکمهٔ «رد شدن» یا کلید بازگشت (BackHandler) — هر دو فلگ را می‌نویسند.
- ۱۱ رشتهٔ جدید در Strings (fa + en): onb1Title..onb4Body, onbSkip, onbNext, onbStart.
- اتصال در `DidbanApp`: روی‌هم‌نشین در لایهٔ ۵ (بالای همه)، با `statusBarsPadding` + `navigationBarsPadding`.

### شکاف ۲: پالت فرمان، صفحات دک را نمی‌شناخت
۴ دستور جدید در پالت (کنار فرمان‌های موجود: refresh, manage, auto-deploy, lang, theme):

| دستور | آیکون | اثر |
|---|---|---|
| navTunnels | SwapHoriz | `pagerState.animateScrollToPage(1)` |
| navUptime | Timer | `animateScrollToPage(2)` |
| navNetwork | Public | `animateScrollToPage(3)` |
| navVault | Security | `animateScrollToPage(4)` |

## ۳. فهرست تغییرات

| فایل | تغییر |
|---|---|
| `AeroOnboarding.kt` | جدید — 316 خط (۴ صفحه، gliph‌های Canvas، نقاط پیشرفت، BackHandler) |
| `Prefs.kt` | +2 تابع: isOnboardingSeen / setOnboardingSeen (کلید `onboarding_seen_v3`) |
| `Strings.kt` | +11 کلید onboarding (Fa + En هر دو تکمیل — assert در زمان ساخت) |
| `MainActivity.kt` | +متغیر وضعیت onboardingSeen؛ اتصال اورلی Onboarding در DidbanApp |
| `AeroCanvasHome.kt` | +4 دستور ناوبری دک در پالت + ۳ ایمپورت آیکون |

موتور (Go/agent)، قراردادها و صفحات فاز ۲: بدون تغییر.

## ۴. شواهد تست

| ردیف | نتیجه |
|---|---|
| کامپایل بستهٔ JVM (MAIN) | OK — بستهٔ 19 فایل؛ در این گام `AeroTokens.kt`، `AeroMapModel.kt` و `Strings.kt` هم به بسته اضافه شدند، یعنی ۱۱ رشتهٔ جدید و فلگ onboarding این‌بار **کامپایل‌تأیید** شدند |
| تست‌های JVM | OK (126 tests) |
| `go build ./...` (agent) | OK |
| `go test -race -count=1` | ok (1.825s) |

**نکتهٔ صداقت:** لایهٔ Compose (AeroOnboarding، ویرایش DidbanApp، ویرایش پالت AeroCanvasHome) در این محیط امکان تست واقعی نداشت و فقط از نظر کد بررسی شد (تعادل براکت، ایمپورت‌ها، قراردادهای Compose — AnimatedContent/tween/BackHandler — و بررسی چشمی).

## ۵. گام بعد (3-F)

ممیزی تم تیره (Cockpit) + RTL + کاهش حرکت (reduce-motion) در سراسر صفحات v3 و اجرای کامل ماتریس نهایی.
