# گزارش آیتم ۲۳ فاز ۲ — H18: Narnia — ip_forward دائمی نمی‌شود + fallback شکسته

## مشکل (طبق ممیزی فاز ۱ + ریشه‌یابی عمیق‌تر در این آیتم)

ممیزی دو خلأ گزارش کرده بود؛ بررسی کد و **مقایسه با مخزن/اسکریپت رسمی Narnia** ریشه را عمیق‌تر نشان داد — کل مسیر deploy قدیمی کار نکرده بود:

1. **مسیر binary خام از ابتدا مرده بود.** اسکریپت نصب سعی می‌کرد `releases/latest/download/narnia-linux-<arch>` را دانلود کند، اما مخزن Dnt3e/Narnia **صفر release دارد** (0 tag) — این URL هرگز جواب نمی‌داد.
2. **Fallback اجرا نمی‌شد.** «fallback» فقط `Narnia.sh` را در `/tmp` دانلود می‌کرد و **هرگز اجرا نمی‌کرد**؛ بعد `chmod +x /usr/local/bin/narnia 2>/dev/null || true` شکست را بلعید → سرویس systemd با binary موجود-غیرراه‌اندازی‌شده enable می‌شد → همیشه `failed` بدون هیچ سرنخی.
3. **اسم image در docker-compose اشتباه بود.** ما `dnt3e/narnia:latest` می‌دادیم که در Docker Hub **404** است؛ image واقعی `stormotron/narnia` (تگ‌های `0.0.3` و `latest`) است. compose ما `--device /dev/net/tun` را هم نداشت و از CLI flags استفاده می‌کرد درحالی‌که image با **env-varها** پیکربندی می‌شود (مکانیزم رسمی).
4. **ناسازگی اسم یونیت.** اسکریپت یونیت `narnia.service` می‌ساخت ولی ایجنت وضعیت/کنترل را روی `didban-tunnel-<id>` می‌پرسید — حتی اگر binary وجود داشت، اپ «stopped» گزارش می‌داد.
5. (آنچه ممیزی دیده بود) **`ip_forward` فقط runtime** بود: `echo 1 > /proc/sys/net/ipv4/ip_forward` — بعد از reboot NAT/فورواردینگ می‌رفت.

## رفع (ریشه‌ای: هم‌راستا با مکانیزم رسمی upstream)

`generateNarnia()` بازنویسی شد؛ deploy حالا دقیقاً از مسیر رسمی (همان‌کاری `Narnia.sh` upstream) می‌گذرد:

| مؤلفه | قبلی | حالا |
|---|---|---|
| توزیع | دانلود binary که وجود ندارد | **Docker** — image پین‌شده `stormotron/narnia:0.0.3` (همان‌تایی اسکریپت رسمی؛ سایز 5.8MB → داخل budget 75 ثانیه‌ای exec ایجنت) |
| پیکربندی container | CLI flags (entrypoint این‌طور نمی‌خواند) | **env-varهای رسمی**: `INTERFACE`، `PASSWORD`، `SERVER`/`REMOTE_IP`، `OPERATING_MODE`، `MTU` + `--device /dev/net/tun` + `--net=host` |
| مدیریت | `narnia.service` (نام ناسازگار) | **یونیت `didban-tunnel-<id>.service`** — همان‌ی که ایجنت query می‌کند → status/start/stop/delete از این‌به‌بعد درست است |
| `ip_forward` | فقط `echo` به /proc | **drop-in ایدمپوتان `/etc/sysctl.d/99-didban-narnia.conf`** + `sysctl -w` فوری |
| NAT (طرف ایران) | `iptables -A` یک‌باره (reboot → پاک) | اسکریپت fw اختصاصی **`/etc/didban/narnia-<id>-iran-fw.sh`** که **هر start یونیت** اجرا می‌شود: انتظار TAP (تا 30s) → `ip link up` + MTU → DNAT با **حذف-قبل-افزودن** (یدمپوتان، بدون rule دوقلو) → MASQUERADE محدود به default-iface و TAP (نه `MASQUERADE` بدون شرط) → FORWARD ACCEPT دوطرفه — دقیقاً الگوی `apply_firewall` رسمی |
| خطا | `|| true` می‌بلعید، سرویس شکسته enable می‌شد | **fail-fast صریح**: بدون docker / pull ناموفق / `/dev/net/tun` موجود نیست → `exit 1` با پیام روشن؛ آخرین خط `systemctl status` rc واقعی را به ایجنت برمی‌گرداند |
| docker-compose (تب دستی) | image 404 + بدون TUN | image واقعی + TUN + env-varها + یادداشت TAP |

یادداشت‌های طراحی:
- fw سمت **ایران** async اجرا می‌شود (`ExecStartPost` با background + لاگ `/var/log/didban-narnia-<id>.log`) چون TAP کلاینت فقط بعد از دریافت config-packet از سرور ظاهر می‌شود؛ اجرای sync یونیت را تا 30s روی `activating` نگه می‌داشت و استاتوس ایجنت غلط می‌گرفت. fw سمت **خارج** sync است (TAP سرور بلافاصله ساخته می‌شود).
- `ExecStartPre=-docker rm -f <id>` + `--rm` روی run → هر restart تمیز است و delete ایجنت (stop + حذف یونیت) container را هم حذف می‌کند.
- محدودیت upstream (مستندشده): **یک تانل Narnia در هر host** (TAP ثابت `nvpn`).

## شواهد

- **مقایسه با واقعیت upstream** (در این جلسه): مخزن Narnia — 0 release؛ `Narnia.sh` رسمی — Docker `stormotron/narnia:0.0.3` + TUN + env + `apply_firewall`؛ Docker Hub — `dnt3e/narnia` = 404، `stormotron/narnia` = تگ‌های 0.0.3/latest (5.8MB).
- **compile روی JVM با kotlinc 1.9.24 — نسخهٔ واقعی toolchain اپ** (build.gradle: `kotlin.android 1.9.24`) — closure 13 فایلی شامل `TunnelEngine.kt` — بدون خطا، 98 کلاس. (هاسپ قبلی روی 2.0.21 بود؛ در این آیتم متوجه شدیم `trimMargin` در 2.0 به `replaceIndentByMargin` تغییرنام خورده و harness را به نسخهٔ دقیق اپ هماهنگ کردیم — کد فقط با APIهای 1.9.24 نوشته شد.)
- **تست‌های واحد: OK (26 tests)** — `TunnelNarniaGeneratorTest` (15 تست جدید) + 11 تست قبلی `TunnelFieldValidationTest`:
  - مکانیزم Docker واقعی + حذف مسیر binary مرده و fallback اجرانشده؛
  - `ip_forward` در sysctl.d (نه runtime-only)؛
  - **هیچ `|| true` روی قدم‌های بحرانی** (تنها مجاز: خط تزئینی MTU در fw)؛
  - NAT یدمپوتان و port-mapped (هر دو پروتکل tcp/udp)؛
  - decode یونیت از b64 و بررسی env هر نقش + TUN + `--net=host` + hooks؛
  - `bash -n` روی هر دو اسکریپت نصب **و** بدنهٔ fw استخراج‌شده از heredoc؛
  - **e2e عملکردی**: اجرای واقعی هر دو اسکریپت نصب با stubهای `docker/ip/iptables/systemctl/sysctl/sudo` و مسیرهای sandbox → بررسی آرته‌فکت‌های روی دیسک (fw، sysctl.d، یونیت)، ترتیب فراخوانی‌ها (pull → daemon-reload → enable → restart)؛
  - **fail-fast**: بدون docker → exit غیرصفر + پیام «docker is required».

## تحلیل regression

- مسیر قدیمی **هیچ‌وقت تانل سالم تولید نمی‌کرد** (binary مرده) — پس قابلیت سالمی جابه‌جا نشده؛ مکانیزم Docker تنها راه توزیع رسمی upstream است.
- قرارداد ایجنت دست‌نخورده: `config_content`/`exec_script`/`service_name` — فقط محتوایشان درست شد.
- اسم یونیت با query ایجنت سازگار شد → status ناپایدار/غلط Narnia برطرف شد.
- فایل‌های config (خارجی/ایران) همچنان خط معادل CLI را نگه می‌دارند (برای کشف/مرجع).

## خلأهای صادقانه

- Android SDK در این محیط نیست: تأیید با compile از closure واقعی روی **همان نسخهٔ Kotlin اپ (1.9.24)** + 26 تست (شامل e2e shell) بود، نه اجرای روی دستگاه.
- اجرای end-to-end روی دو سرور واقعی (با docker) در اینجا امکان‌پذیر نبود؛ مکانیزم تولیدشده دقیقاً الگوی `Narnia.sh` رسمی (env/TUN/image/FIREWALL) است که مرجع عملیاتی upstream محسوب می‌شود.
- حذف کامل آرته‌فکت‌ها (فایل fw + sysctl.d) در DeleteTunnel ایجنت انجام نمی‌شود — بخشی از آیتم عمومی «cleanup ناقص در Delete» که در ممیزی ثبت شده و پیگیری جداگانه می‌خواهد.

## یافتهٔ جانبی (پیگیری)

ناسازگی اسم یونیت (اسکریپت‌ها یونیت با نام ثابت مثل `gost.service`/`backpack-server.service` می‌سازند ولی ایجنت `didban-tunnel-<id>` را query می‌کند) **برای همهٔ coreهای دیگر** هم وجود دارد و فقط برای Narnia در این آیتم درست شد. پیشنهاد: آیتم جدا برای استانداردسازی نام یونیت در سایر generatorها + Extend شدن DeleteTunnel برای حذف آرته‌فکت‌های core-specific.
