# گزارش آیتم ۱۰ فاز ۲ — Injection در اسکریپت‌های heredoc تولیدی (H4)

تاریخ: ۲۰۲۶-۰۹-۱۳ | push به `main`

## ۱. ریشه (از کد)

اسکریپت‌های deploy این‌گونه ساخته می‌شدند:
```
cat << 'EOF' > /etc/<core>/config
$iranConfig          ← فیلدهای کاربر (token/iranHost/wsPath/...) مستقیم interpolate می‌شدند
EOF
```
اگر هر فیلد حاوی **خطی دقیقاً به شکل `EOF`** (نیولاین + EOF) باشد: heredoc زود بسته می‌شود و **بقیهٔ محتوا به‌عنوان shell روی سرور (root) اجرا می‌شود**. همچنین در ۲ core فیلدهای کاربر مستقیم در **دستورهای shell خارج از هر heredoc** بودند:
- **Narnia:** `iptables ... --to-destination $vIpKharej:...` (virtual IP کاربر، در بستر shell)
- **IPTables:** `iptables ... --to-destination $foreignIp:...` (foreignHost کاربر، در بستر shell)

## ۲. اصلاح (دو لایه، مطابق راه‌حل ممیزی)

### لایهٔ ۱ — Base64 برای همهٔ محتوای فایل‌ها (18 موضع)
به‌جای heredoc، اسکریپت حالا می‌نویسد:
```
printf '%s' '<base64>' | base64 -d > /etc/<core>/<file>
```
- ۱۲ فایل کانفیگ (BackPack×2, Paqet×2, Spoof×2, Backhaul×2, Rathole×2, FRP×2)
- ۶ فایل unit systemd که ExecStart آن‌ها مقدار کاربر داشت (Narnia×2, GOST×2, Chisel×2) — محتوای unit به متغیر Kotlin استخراج و base64 شد.
- base64 **یک خط** از `[A-Za-z0-9+/=]` است: نه نیولاین (early-close غیرممکن)، نه کاراکتر shell (داخل `'...'` کاملاً inert).
- ۱۲ heredoc باقی‌مانده فقط **unit‌های ثابت‌محتوا** هستند (بدون هیچ فیلد کاربر — ایمن).
- `base64 -d` از coreutils است و روی همهٔ لینوکس موجود است.

### لایهٔ ۲ — اعتبارسنجی فیلدهایی که در بستر shell/ExecStart می‌مانند
**`TunnelFieldValidation.kt`** (جدید، خالص، JVM-testable):
- `isHost` — hostname DNS معتبر یا IPv4؛ هیچ فاصله/ویرگول/نیولاین/متاکاراکتر shell.
- `isIpv4` — IPv4 سخت‌گیرانه (برای مقاصد kernel مثل DNAT).
- `isValidToken` — `[A-Za-z0-9_-]{1,128}` (برای آرگومان‌های CLI مثل `--auth`/`-k`).
- `validateForDeploy(cfg)` در `TunnelEngine` — **به‌ترتیب core** فقط آنچه واقعاً در بستر shell/ExecStart می‌رود را چک می‌کند:
  | Core | فیلدهای چک‌شده |
  |---|---|
  | IPTables | foreignHost (iptables) |
  | Narnia | foreignHost (ExecStart)، **virtualIpKharej (iptables shell!)**، token (ExecStart) |
  | GOST | foreignHost (ExecStart) |
  | Chisel | foreignHost (ExecStart)، token (`--auth`) |
  | بقیه | — (همهٔ مقادیر کاربر در base64 هستند و تفسیر نمی‌شوند) |
- `generateCode` با خطای اعتبارسنجی **throw** می‌کند؛ `autoDeployTunnel` → نتیجهٔ ناموفق با پیام فارسی شفاف («هیچ چیزی deploy نشد»)+ دیالوگ نمایش کد متن خطا را نشان می‌دهد.

## ۳. شواهد تست

| لایه | شواهد |
|---|---|
| **JVM: `TunnelFieldValidationTest` (۱۱ تست جدید)** | پذیرش hostname/IPv4؛ رد `$(reboot)`/`` `id` ``/`; rm -rf /`/`&&`/فاصله/خالی/253+؛ **رد صحنهٔ early-close** (`1.2.3.4\nEOF\nrm -rf /`)؛ سخت‌گیرانه بودن IPv4 (256.x, 3/5 octet)؛ مرزهای پورت؛ charset توکن (رد quote/`$`/نیولاین/129+)؛ پیام‌های خطا — **OK (72 tests کل)** |
| **Smoke-test shell (زنده)** | همان الگوی تولیدی `printf '%s' '<b64>' \| base64 -d > file` با payload حمله (EOF+نیولاین+`rm -rf`+`$(...)`): (۱) round-trip بایت‌به‌بایت یکسان، (۲) **هیچ کدی اجرا نشد**، (۳) سمانتیک زنجیرهٔ `&&` حفظ شد، (۴) round-trip unit نرنیا OK |
| ساختاری | توازن brace/paren = 0/0؛ grep: ۱۸ خط base64-write، ۱۲ heredoc باقی‌مانده فقط unit ثابت |
| Go ایجنت | بدون تغییر؛ `go test` سبز (تأیید) |

## ۴. آنچه با این محیط قابل تست نبود (صریح)

**این بخش به دلیل نبود Android SDK/gradle و دو سرور واقعی، امکان build اپ و اجرای زندهٔ deploy کامل (download باینری‌های تونل، اجرای systemd روی host) را نداشت.**
- الگوی shell base64: **زنده تست شد** (smoke-test بالا) — سبز.
- منطق اعتبارسنجی: **JVM test-executed** — سبز.
- سیم‌کشی Compose/Engine (throw/catch، نمایش خطا): فقط از نظر کد بررسی شد و در اولین build/اجرای واقعی تأیید می‌شود.

## ۵. تحلیل رگرسیون

- **سازگاری با سرورهای موجود:** اسکریپت‌ها در هر deploy از نو اجرا می‌شوند؛ `base64 -d` سراسری است؛ محتوای فایل‌ها **byte-identical** با قبل (فقط روش نوشتن عوض شد).
- **رفتار deploy سالم:** بدون تغییر (همان مراحل mkdir/download/enable/status).
- **تغییر رفتار (مستند):** مقادیر نامعتبر که قبلاً «deploy خراب بدون خطای روشن» می‌شدند، حالا **قبل از deploy** با پیام شفاف رد می‌شوند.
- **Docker compose strings** (فقط نمایش): بدون تغییر؛ فیلدهای داخلشان با همان اعتبارسنجی لایهٔ ۲ پوشش دارند.
- **agent:** بدون تغییر — `multi_ports` فقط در meta ذخیره می‌شود و در shell استفاده نمی‌شود (تأیید با grep)؛ `config_path`/`service_name` از `cfg.id` (Long) ساخته می‌شوند (امن) + اعتبارسنجی سمت agent (آیتم ۱).
- **GOST/tcp quirk پیشین** (ExecStart=کامنت در حالت tcp برای سمت خارج) — از قبل وجود داشت و injection نیست؛ خارج از scope این آیتم (در گزارش بعدی‌ها).

## ۶. گام بعدی (آیتم ۱۱)

H7 — سه poller موازی + OkHttpClient تازه در هر request (بازطراحی pooling/تک‌لایه‌سازی poll).
