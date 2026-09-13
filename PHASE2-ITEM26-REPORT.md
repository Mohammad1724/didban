# گزارش آیتم ۲۶ — مسیرهای per-tunnel برای فایل‌های تنظیم (حذف تصادف هم-core روی یک هاست)

**فایل‌های تغییرکرده:** `TunnelEngine.kt`، `TunnelModel.kt`، `agent/tunnel_manager.go`، `agent/tunnel_manager_test.go` + تست جدید `TunnelConfigPathTest.kt`

## مشکل (ریشه)

هر هسته فایل تنظیمش را در یک **مسیر ثابت** می‌نوشت:

| هسته | مسیر ثابت (ایران / خارج) |
|---|---|
| BackPack | `/etc/backpack/server.toml` / `client.toml` |
| Paqet | `/etc/paqet/client.yaml` / `server.yaml` |
| SpoofTunnel | `/etc/spoof-tunnel/client.json` / `server.json` |
| Backhaul | `/etc/backhaul/config.toml` / `config.toml` (هر دو یکی!) |
| Rathole | `/etc/rathole/client.toml` / `server.toml` |
| FRP | `/etc/frp/frpc.toml` / `frps.toml` |

**دو تانل هم-core روی یک هاست** (مثلاً دو BackPack روی یک سرور) هر دو همین فایل را
overwrite می‌کردند؛ unit هر تانل به همان مسیر اشاره داشت، پس در عمل **هر دو تانل به
تنظیم تانلی می‌رسیدند که آخرش deploy شده** — تانل قبلی بی‌صدا خراب می‌شد.

یک مشکل ساختاری دوم هم همین‌جا پیدا شد: ایجنت یک **کپی دوم** از تنظیم در sandbox خودش
(`resolveConfigPath` فقط داخل `/etc/didban/tunnels` می‌نویسد) می‌ساخت؛ درحالی‌که فایل
**واقعی** در مسیر ثابت هسته بود. یعنی:
- دو کپی از یک تنظیم روی هاست (یکی که unit می‌خواند، یکی که ایجنت نگه می‌دارد)،
- در `DeleteTunnel` فقط کپی sandbox حذف می‌شد و فایل واقعی روی هاست **می‌ماند** (residue)،
- در `deploy-mode=config-only` (حالت سخت‌شده) نوشتن تنظیم بی‌فایده بود چون unit مسیر دیگری می‌خواند.

## طراحی (یک منبع‌وحققت)

مسیر واقعی تنظیم حالا **داخل همان sandbox ایجنت** و **per-tunnel** است:

```
/etc/didban/tunnels/<id>/server.toml   (نام فایل: server/client بر اساس نقش)
```

نتیجه‌ی هم‌زمانِ هر سه لایه:
1. **بدون تصادف:** هر تانل زیردستگاری `<id>` خودش است.
2. **یک فایل به‌جای دو:** payload ایجنت (`config_path`) و اسکرپت install هر دو دقیقاً
   همان مسیر را می‌نویسند/می‌خوانند (محتوا یکی است؛ نوشتن تکراری = idempotent).
3. **حذف واقعی:** `DeleteTunnel` حالا فایل واقعی را حذف می‌کند و چون layout است
   `<root>/<id>/`، دایرکتوری per-tunnel هم پاک می‌شود.
4. **بونس:** `deploy-mode=config-only` حالا واقعاً کار می‌کند (فایل دقیقاً جایی می‌نشیند
   که unit می‌خواند).

تصمیمات عمدی:
- **باینری‌ها مشترک می‌مانند** (`/usr/local/bin/<core>`) — یک باینری می‌تواند هر تعداد
  unit را سرویس کند (الگوی استاندارد؛ مثل nginx)؛ نصب تکراری idempotent است.
- مسیرهای داخل **کانتینر** در docker-compose (مثل `command: server -c /etc/backpack/server.toml`
  و volume‌های `./x.toml:/etc/...`) دست‌نخورده‌اند — آن‌ها مسیر داخلی تصویر کانتینرند،
  نه مسیر هاست.
- **IPTables** دست‌نخورده است (اسکرپت rules از قبل per-tunnel با هوش بود:
  `/etc/didban/iptables-<hash>-rules.sh`).
- **GOST/Chisel/Narnia** فایل تنظیم ندارند (دستور داخل unit یا env کانتینر) → مسیر خالی.

## تغییرات

### اپ (Kotlin)
- `GeneratedTunnelCode`: دو فیلد جدید `iranConfigPath` / `foreignConfigPath`
  (پیش‌فرض خالی) — **منبع‌وحققت** برای هر دو مصرف‌کننده.
- ۶ ژنراتور (BackPack، Paqet، SpoofTunnel، Backhaul، Rathole، FRP):
  `sudo mkdir -p /etc/didban/tunnels/<id>` + نوشتن تنظیم به مسیر per-tunnel +
  `ExecStart` unit با همان مسیر.
- `autoDeployTunnel`: `config_path` payload = مسیر runtime (اگر هست) وگرنه همان فایل
  مرجع قدیمی `<id>_<role>.conf` (رفتار GOST/Chisel/Narnia/IPTables بدون تغییر).

### ایجنت (Go)
- `ApplyTunnel`: قبل از نوشتن، زنجیره‌ی والد مسیر ساخته می‌شود
  (`MkdirAll(filepath.Dir(cleanPath))`) — قبلاً فقط ریشه‌ی sandbox ساخته می‌شد و
  نوشتن در `<root>/<id>/file` (layout جدید) **impossible** بود. (نقطه‌ی ۴ ریشه‌ای‌ایتم.)
- `DeleteTunnel`: بعد از حذف فایل، اگر دایرکتوری والد دقیقاً
  `<configRoot>/<id>` باشد (id معتبر + مستقیم زیر ریشه‌ی sandbox)، حذف می‌شود —
  `os.Remove` روی دایرکتوری فقط وقتی **خالی** باشد موفق است، پس هیچ فایل بیگانه‌ای
  پاک نمی‌شود.

## تست

**Kotlin — `TunnelConfigPathTest.kt` (۳ تست جدید):**
1. هر ۶ هسته‌ی دارای فایل: مسیر اعلام‌شده زیر `/etc/didban/tunnels/42/` است؛ هر اسکرپت
   install دایرکتوری per-tunnel را می‌سازد؛ مسیر runtime **حداقل دو بار** در همان اسکرپت
   ظاهر می‌شود (خط نوشتن + `ExecStart` unit)؛ و هیچ‌کدام دیگر مسیر ثابت قدیمی هسته را
   شامل نمی‌شوند.
2. دو تانل هم-core (id 11 و 12): مسیرهایشان متفاوت است و اسکرپت‌های تانل ۲ به فایل‌های
   تانل ۱ (و برعکس) دست نمی‌زنند.
3. GOST/Chisel/Narnia/IPTables: مسیر فایل تنظیم نمی‌دهند (خالی).

**Go — `agent`:**
- تست جدید `TestApplyAndDeletePerTunnelDirectory`: apply با layout `<root>/12/server.toml`
  (دایرکتوری از قبل وجود ندارد → ایجنت باید بسازد)، delete → فایل + دایرکتوری per-tunnel
  حذف، و یک دایرکتوری همسایه‌ی غیر-tunnel-id دست‌نخورده باقی می‌ماند.
- `go build` + `go test -race ./...` → **سبز** (همه‌ی تست‌های قبلی + جدید).

**سوت کامل JVM: `OK (36 tests)`** (۳۳ قبلی + ۳ جدید).

> شفافیت: مسیر `config_path` در payload ایجنت داخل `autoDeployTunnel` است که نیاز به
> اجرای Android دارد؛ این بخش کامپایل و code-review شد ولی end-to-end روی آندروید در این
> سازه‌وار تست واقعی نداشت. منطقش یک‌خطی است (`ifEmpty`) و هر دو شاخه‌ی آن با تست‌های
> `GeneratedTunnelCode` پوشش داده شده‌اند.

## سازگاری با استقرارهای قبلی

- تانل‌های قدیمی (unit قدیمی با مسیر ثابت) بعد از **redeploy** به layout جدید مهاجرت
  می‌کنند (همان unit `didban-tunnel-<id>` جایگزین می‌شود، فایل تنظیم جدید می‌نویسد).
- فایل تنظیم قدیمیِ مسیر ثابت روی هاست می‌ماند (residue) — با پیگیری «تمیزکاری کامل
  Delete» پاک خواهد شد (آیتم بعدی).

## محدودیت‌های باقی‌مانده (ثبت‌شده)

1. **residue باینری/تنظیم/اسکریپت‌های کمکی هنگام Delete** (الان دایرکتوری per-tunnel
   تنظیم پاک می‌شود، ولی باینری و اسکریپت‌های rules/FW نarnia/ip_tables می‌مانند) —
   آیتم بعدی پیشنهادی.
2. اگر اپراتور sandbox ایجنت را با `DIDBAN_TUNNEL_CONFIG_DIR` از
   `/etc/didban/tunnels` جابه‌جا کند، مسیرهای تولیدشده (که پیش‌فرض را hardcode می‌کنند)
   با sandbox آن ایجنت نمی‌خواند — دقیقاً همان محدودیت قبلی کد (پیش از این آیتم) که
   حالا در یک نقطه متمرکز شده؛ رفعش نیاز به اعلام `config-dir` توسط ایجنت در پاسخ API است.

## نتیجه

تصادف تنظیم بین تانل‌های هم-core برداشته شد؛ «یک فایل تنظیم = یک واحد مدیریت» برای
هر تانل برقرار است (مسیر، deploy، status، delete همه روی یک فایل واقعی).
