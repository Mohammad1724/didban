# گزارش آیتم ۲۷ — تمیزکاری کامل هنگام Delete (حذف residue از سرور)

**فایل‌های تغییرکرده:** `agent/tunnel_manager.go`، `agent/tunnel_manager_test.go` — (بدون تغییر در کد اپ؛ حذف از ابتدا از ایجنت فرمان می‌گیرد)

## مشکل (ریشه)

`DeleteTunnel` ایجنت فقط سه چیز پاک می‌کرد: unit، کپی تنظیم داخل sandbox و meta.
اما هر deploy چیزهای دیگری هم روی هاست گذاشته بود که برای همیشه می‌ماندند:

| هسته | residue بعد از Delete (پیش از این آیتم) |
|---|---|
| همه‌ی هسته‌های باینری | `/usr/local/bin/<core>` |
| Narnia | اسکریپت firewall `/etc/didban/narnia-<id>-{iran,foreign}-fw.sh`، لاگ `/var/log/didban-narnia-<id>.log`، drop-in `/etc/sysctl.d/99-didban-narnia.conf` |
| IPTables | اسکریپت rules `/etc/didban/iptables-<hash>-rules.sh`، drop-in `/etc/sysctl.d/99-didban-iptables.conf` (زنجیره‌ها با ExecStop unit پاک می‌شدند، ولی اگر unit از قبل پاک شده بود، **زنجیره‌ها روی هاست می‌ماندند**) |
| استقرارهای قدیمی (پیش از آیتم ۲۶) | فایل‌های تنظیم مسیر ثابت: `/etc/backpack/*.toml`، `/etc/paqet/*.yaml`، `/etc/spoof-tunnel/*.json`، `/etc/backhaul/config.toml`، `/etc/rathole/*.toml`، `/etc/frp/*.toml` |

در عمل: بعد از حذف تانل، ایجنت و اپ «حذف شد» می‌گفتند، ولی هاست هنوز فایل‌ها،
اسکریپت‌ها و حتی قوانین NAT تانلِ حذف‌شده را در خود نگه داشته بود — و هر deploy
بعدیِ هسته‌ی دیگری هم این‌ها را نمی‌شناخت.

## طراحی: «فقط آنچه می‌تواند حساب‌رسی شود، پاک می‌شود»

اصول کلیدی:
1. **برجسته‌ها (per-tunnel) همیشه پاک می‌شوند:** اسکریپت fw/log/rules — هر کدام فقط
   به یک `<id>` (یا hash آن) تعلق دارند؛ حذفشان بی‌خطر است.
2. **اشتراکی‌ها فقط با شمارش مرجع (refcount) پاک می‌شوند:** باینری مشترک
   (`/usr/local/bin/<core>`) و drop-in‌های sysctl مشترک — فقط وقتی **هیچ** تانل هم-core
   دیگر روی همان هاست (طبق metaهای ایجنت) باقی نمانده باشد.
3. **FRP استثناست:** `frpc` و `frps` دو باینری مجزا برای دو role‌اند → refcount
   هم-core **و هم-role**.
4. **Best-effort:** نداشتن فایل یا نداشتن `docker`/`iptables` روی هاست، حذف را شکست
   نمی‌دهد (unit + config + meta که اصلی‌ترین‌ها هستند، قبلاً پاک شده‌اند).

نقشه‌ی کامل residue و قواعد حذف در کامنت بالای `cleanupCoreResidue` مستند شده است.

## تغییرات (Go)

- `coreBinaryPath(core, role)` و `legacyConfigPaths(core, role)`: نقشه‌ی مسیرهای
  باینری و مسیرهای تنظیم قدیمی (pre-Item-26) بر اساس هسته/نقش.
- `iptablesChainHash(id)`: بازتولید دقیق نمک زنجیره از سمت اپ — ۸ بایت اول
  SHA-256(`"<id>"`) به hex. **تست پین‌شده** (`TestIptablesChainHashMatchesApp` با
  بردار `73475cb40a568e8d` برای id=42) — این بردار هم با `sha256sum` و هم با
  یک اجرای JVM (همان الگوریتم Kotlin: `MessageDigest` + `%02x`) تأیید شده؛ دو
  سمت هرگز نمی‌توانند از هم باز شوند.
- `otherMetaCount(id, core, roleOnly, role)`: شمارش metaهای ایجنت روی **همین** هاست.
- `cleanupCoreResidue(meta)`:
  - **Narnia:** حذف اسکریپت fw نقش، لاگ (ایران)، `docker rm -f didban-tunnel-<id>`
    (کانتینر عادی با `docker stop` + `--rm` می‌میرد؛ این برای حالت گیرکرده‌ست)،
    و drop-in فقط در نبود Narnia دیگر.
  - **IPTables:** حذف اسکریپت rules + **درمان زنجیره‌ها** (F/D/X برای
    `didban-tun-<h>` و `didban-tunp-<h>` — اگر unit قبل از Delete پاک شده بود) +
    drop-in فقط در نبود IPTables دیگر.
  - **همه‌ی هسته‌ها:** حذف باینری مشترک + فایل/دایرکتوری تنظیم قدیمی، فقط وقتی
    آخرین تانل هم-core (FRP: هم-role) باشد.
- ریشه‌های layout (`binRoot`, `didbanEtc`, `sysctlRoot`, `legacyEtc`, `logRoot`)
  متغیرهای سطح پکیج‌اند تا تست‌ها بدون دست زدن به `/etc` و `/usr/local/bin` واقعی
  ریشه‌ها را به دایرکتوری موقت بفرستند.

## تست (Go، همه با `-race`)

| تست | آنچه اثبات می‌کند |
|---|---|
| `TestIptablesChainHashMatchesApp` | هم‌خوانی hash دو زبان (برداری پین‌شده) |
| `TestDeleteTunnelRemovesNarniaResidue` | fw + لاگ + drop-in با حذف Narnia پاک می‌شوند |
| `TestDeleteTunnelKeepsSharedDropInWhileSiblingRemains` | در وجود Narnia دیگر، drop-in مشترک می‌ماند و بعد از حذف هر دو پاک می‌شود |
| `TestDeleteTunnelRemovesIptablesRulesAndChains` | اسکریپت rules + drop-in پاک می‌شوند |
| `TestDeleteTunnelKeepsSharedBinaryWhileSiblingRemains` | باینری BackPack در وجود تانل دوم می‌ماند، بعد از حذف دوم پاک می‌شود |
| `TestDeleteTunnelFRPRoleSpecificBinaries` | حذف سمت foreign فقط `frps` را می‌بیند، `frpc` دست‌نخورده می‌ماند |
| `TestDeleteTunnelRemovesLegacyConfigAndDir` | فایل + دایرکتوری تنظیم قدیمی (pre-Item-26) پاک می‌شوند |

**نتیجه:** `gofmt` تمیز، `go vet` تمیز، `go build` OK، `go test -race ./...` **سبز**.

**JVM:** `MAIN OK` + سوت کامل **`OK (36 tests)`** (کد اپ در این آیتم تغییر نکرده؛
اجرا فقط برای اطمینان از بی‌پس‌ریزش بودن).

## سازگاری و محدودیت‌های باقی‌مانده

- **نصب‌های دستی (بدون ایجنت) نامرئی‌اند:** اگر کاربر خودش اسکریپت install را
  دستی اجرا کرده باشد (meta ثبت نشده)، ایجنت نمی‌داند چه چیزی تعلق به دیدبان است و
  چیزی را پاک نمی‌کند — تصمیم عمدی (قاعده‌ی «فقط آنچه قابل حساب‌رسی است»).
- **باینری ممکن است از قبل روی هاست وجود داشته باشد** (مثلاً کاربر خودش gost
  نصب کرده بود): refcount فقط تانل‌های ایجنت را می‌بیند؛ اگر ایجنت فکر کند تانل
  دیدبان آخرین مصرف‌کننده است، باینری پاک می‌شود. این ریسک باقی می‌ماند ولی با
  همان رفتار پنل‌های مشابه (Smite/Marzban) هم‌خوان است؛ باینری قابل نصب مجدد است.
- تصویر Docker Narnia (`stormotron/narnia:0.0.3`) حذف **نمی‌شود** — مشترک بین
  همه‌ی تانل‌های Narnia و حذفش هزینه‌ی pull مجدد برای بعدی است.

## نتیجه

دکمه‌ی Delete حالا واقعاً تمیزکاری کامل می‌کند: unit، تنظیم، meta، اسکریپت‌های کمکی،
لاگ‌ها، drop-in‌ها، زنجیره‌های NAT و (در آخرین تانل هسته) باینری و مسیرهای تنظیم
قدیمی — هر کدام دقیقاً زمانی که پاک‌سازی‌شان بی‌خطر است.
