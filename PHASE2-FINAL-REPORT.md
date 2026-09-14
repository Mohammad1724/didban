# گزارش نهایی فاز ۲ — دیدبان: تثبیت ریشه‌ای موتور تانل و ایجنت (مایلی‌استون)

**تاریخ:** پایان فاز ۲ — **محدوده:** آیتم‌های ۶ تا ۲۹ + مرحله‌ی ۱ — **شعبه:** `main`

---

## ۱. خلاصه

فاز ۲ روی یک اصل کار کرد: **بدون اصلاح ریشه، هیچ چیز را «درست» ندانیم.** هر آیتم یک ریشه‌ی
واقعی داشت (حق‌الزحمه‌ی injection، ریس‌کاندیشن، قرارداد شکسته بین اپ و ایجنت، اعتبارنامه‌ی
اختراعی…) و هر اصلاح با تست سازه‌وار تثبیت شد. در پایان:

- **موتور تانل** (۱۰ هسته × deploy/status/control/delete/discovery) یک قرارداد واحد و
  قابل اعتماد با ایجنت دارد.
- **ایجنت** یک API امن و sandbox شده برای مدیریت تانل است (Bearer-only، rate-limit،
  checksum برای باینری، env allowlist، delete کامل).
- **کل ماتریس تست سبز:** JVM **54** + Go **`-race`** (هر دو از صفر اجرا و تأیید شده).

## ۲. معماری نهایی — قراردادها (نتیجه‌ی جمع‌بندی آیتم‌ها)

| قرارداد | وضعیت نهایی | منبع |
|---|---|---|
| **نام unit** | `didban-tunnel-<id>` در **هر ۱۰ هسته** — دقیقاً همانی که ایجنت query می‌کند | آیتم ۲۵ |
| **مسیر تنظیم** | `/etc/didban/tunnels/<id>/<file>` — داخل sandbox ایجنت؛ یک فایل واحد (اسکرپت + unit + payload هم‌مسیر) | آیتم ۲۶ |
| **API تانل** | ۷ endpoint (`apply/start/stop/restart/delete/status/list`) — Bearer-only، body-cap، rate-limit | آیتم ۶ + ۶ |
| **اعتبارنامه** | یک‌بار ساخته، persistent، بازتولید یکسان (deriveKey برای Spoof)؛ **هرگز fabrication** — discovery یا واقعی بازیابی می‌کند یا خالی + دروازه‌ی deploy | H3 + آیتم‌های ۲۸/۲۹ |
| **حذف** | unit + تنظیم + meta + residue (اسکریپت/لاگ/drop-in/زنجیره) + باینری مشترک با refcount + مسیرهای legacy | آیتم ۲۷ |
| **اعتبارسنجی** | فیلدهای در مسیر shell/systemd قبل از تولید کد validate می‌شوند + دروازه‌ی Tانل‌کشف‌شده | H4 + آیتم ۲۸ |
| **کشف** | پروس + Docker (با تشخیص Narnia)؛ نقش ایران/خارج با یک تابع دقیق و test‌شده (ASCII + فارسی)؛ توکن واقعی از cmdline (Chisel) یا env allowlist (Narnia) | آیتم‌های ۲۸/۲۹ + مرحله ۱ |
| **نصب ایجنت** | SHA-256 release قبل از نصب؛ unit با sandbox متناسب نقش؛ env threshold knobs | آیتم‌های ۱۳/۷ |

## ۳. فهرست آیتم‌ها و commitها (فاز ۲)

| # | موضوع | Commit |
|---|---|---|
| ۶ | Hardening API ایجنت: Bearer-only، rate-limit، access-log، body-cap | `00b3655` |
| ۷ | Sandbox unit ایجنت مطابق نقش واقعی (ProtectSystem) | `c67ae47` |
| ۸ | مانیتورینگ uptime واقعی با interval اختصاصی هر target | `0e346a6` |
| ۹ | persistent کردن توکن تولیدشده (redeploy جفت را نمی‌شکند) | `e970406` |
| ۱۰ (H4) | نوشتن تنظیم با base64 + اعتبارسنجی فیلد → حذف heredoc injection | `7cc1b2f` |
| ۱۱ (H7) | کلاینت OkHttp به‌ازای هر سرور + یک PollingCoordinator | `03cc1ad` |
| ۱۲ (H8) | بازیابی crash قابل اتکا + محدود کردن crash-loop + trace رمزنگاری‌شده | `1699315` |
| ۱۳ (H9) | زنده‌ماندن rotation و process-death (rememberSaveable navigation) | `a102623` |
| ۱۴ (H10) | یک whitelist الگوریتم مدرن برای هر سه موتور SSH | `0561de0` |
| ۱۵ (H11) |fetchAll صفحات Cloudflare + پیام خطای روشن | `7ef82ab` |
| ۱۶ (H13) | verify SHA-256 release قبل از نصب + env threshold knobs | `ee194ee` |
| ۱۷ (H20) | حذف dead code: LocalHttpServer و QrGenerator جعلی (ریشه‌ی H20) | `75daf76` |
| ۱۸ (M10) | سقف ۱MB برای خروجی هر stream SSH (جلوگیری OOM) | `359d82c` |
| ۱۹ (H14) | هرگز downgrade خاموش به plaintext — race TLS نصب | `b9de468` |
| ۲۰ (M17) | PBKDF2 600k (فرمت نسخه‌دار) + صفرشدن password + auto-lock | `5446732` |
| ۲۱ (H12) | سقف events.jsonl + ماندگاری تاریخچه‌ی ۷ روزه | `c11bff4` |
| ۲۲ (H16) | تطبیق دقیق سرور — deploy روی ماشین اشتباه دیگر نمی‌افتد | `73b6df2` |
| ۲۳ (H18) | Narnia از مسیر رسمی Docker + ip_forward ماندگار + fail-fast | `9d0e156` |
| ۲۴ (H19) | IPTables: foreignHost اجباری IPv4 + زنجیره‌های idempotent + unit واقعی | `200a159` |
| ۲۵ | یکدست‌سازی نام unit در همه‌ی هسته‌ها (`didban-tunnel-<id>`) | `cb09e1e` |
| ۲۶ | مسیرهای تنظیم per-tunnel (حذف تصادف هم-core روی یک هاست) | `4d6be29` |
| ۲۷ | تمیزکاری کامل هنگام Delete (residue + refcount + درمان زنجیره) | `ec465de` |
| ۲۸ (M17) | حذف placeholder «auto-detected» — دروازه‌ی deploy + بازیابی واقعی | `59adcf4` |
| ۲۹ | env کانتینرها در API ایجنت (allowlist) + کشف Narnia + توکن واقعی | `d775907` |
| ۳۰ | حدس ایران/خارج: یک تابع دقیق و test‌شده (ASCII + فارسی) | `a866dd6` |

## ۴. ماتریس تست نهایی (اجرای تازه در این مرحله)

**JVM (Kotlin 1.9.24, JUnit 4) — `OK (54 tests)`:**
| فایل | تست‌ها | پوشش |
|---|---|---|
| TunnelFieldValidationTest | ۱۳ | hostname/IPv4/port/token، hostMatches، **حدس ایران** |
| TunnelNarniaGeneratorTest | ۱۵ | تولید Narnia (docker+systemd)، idempotency، fail-fast |
| TunnelIptablesGeneratorTest | ۵ | زنجیره‌های hash، idempotency، واحد oneshot |
| TunnelUnitNamingTest | ۲ | قرارداد نام unit هر ۱۰ هسته + `bash -n` همه‌ی اسکرپت‌ها |
| TunnelConfigPathTest | ۳ | مسیر per-tunnel، عدم نشت متقاطع، هسته‌های بدون فایل |
| TunnelDiscoveryTest | ۱۶ | استخراج توکن cmdline/env، مهاجرت placeholder، دروازه‌ی deploy |

**Go (ایجنت) — `go build` + `go test -race -count=1` سبز:**
اعتبارسنجی ID/service/path، sandbox config (شامل symlink-escape)، deploy-mode،
حذف کامل (refcount، per-role FRP، drop-in، legacy، زنجیره‌های hash پین‌شده)،
**docker env allowlist با داکمن جعلی روی سوکت unix واقعی**، hardening API.

## ۵. ممیزی قرارداد بین‌لایه‌ها (این مرحله)

بررسی و تأیید صریحِ ناسازگاری‌های محتمل بین آیتم‌ها:
- ✅ ۷ endpoint تانل: مسیرهای اپ = مسیرهای ایجنت، دقیقاً.
- ✅ `service_name` payload = `didban-tunnel-<id>` = نام unit تولیدشده (آیتم ۲۵) و
  مطابق الگوی اعتبارسنجی ایجنت (`^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$`).
- ✅ هر مقدار `config_path` (دو شاخه) داخل sandbox `/etc/didban/tunnels` است — ایجنت
  هیچ‌کدام را reject نمی‌کند.
- ✅ `meta.Core` (اسم‌های enum) با کلیدهای `coreBinaryPath` و `cleanupCoreResidue` (آیتم ۲۷)
  یکی‌به‌یکی می‌خوانند (شامل NARNIA/IPTABLES).
- ✅ hash زنجیره‌های IPTables در دو زبان (برداری پین‌شده در تست Go + تأیید JVM).
- ✅ `tokenFromEnv` (آیتم ۲۹) + دروازه‌ی M17 (آیتم ۲۸) هماهنگ: توکن واقعی → deploy آزاد،
  خالی → block با پیام روشن.
- ✅ عملیات start/stop/delete روی تانل‌های کشف‌شده: ایجنت `meta` ندارد → خطای تمیز
  `tunnel not found` (رفتار درست: چیزی که deploy نکرده، کنترل نمی‌شود).

## ۶. محدودیت‌های باقی‌مانده (صریح و ثبت‌شده)

1. **نقش صریح در پروفایل سرور** (فیلد region/role + UI) — فعلاً حدس نام + اصلاح دستی
   در ویرایش. کاندیدای اول فاز ۳.
2. **نصب‌های دستی (بدون ایجنت) برای delete نامرئی‌اند** — ایجنت فقط آنچه ثبت کرده پاک
   می‌کند (تصمیم عمدی، مستند در آیتم ۲۷).
3. **اگر `DIDBAN_TUNNEL_CONFIG_DIR` از پیش‌فرض جابه‌جا شده باشد**، مسیرهای hardcode شده
   اپ با sandbox آن ایجنت نمی‌خوانند (محدودیت پیشین؛ رفعش نیاز به اعلام config-dir در
   پاسخ ایجنت است — ثبت‌شده در آیتم ۲۶).
4. **لایه‌ی UI (Compose) و سیم‌بندی آندروید** در این سازه‌وار کامپایل/اجرا نشدند
   (بدون Android SDK) — فقط code-review + تست‌های خالص JVM برای منطق پشتشان.
   شفاف‌سازی استاندارد هر آیتم: «این بخش امکان تست واقعی نداشت و فقط از نظر کد
   بررسی شد».
5. **E2E روی هاست واقعی** انجام نشده (دسترسی به سرور واقعی در این محیط نیست) —
   امولاتورهای سیم‌بندی (iptables stateful، داکمن جعلی) نزدیک‌ترین جانشین بودند.

## ۷. آمادگی فاز ۳

موتور زیر (تولید کد، deploy، کنترل، حذف، کشف، ایجنت) **ثابت، آزمایش‌شده و
مستند** است. فاز ۳ می‌تواند بدون نگرانی رگرسیونِ موتور، بازطراحی UI/UX از صفر
(حس اپ تجاری: IA، design system، حالت‌های خالی/اسکلتون، motion) را انجام دهد —
با شروع از **پیشنهاد طراحی** برای تأیید، طبق برنامه‌ی مرحله‌ی ۳.
