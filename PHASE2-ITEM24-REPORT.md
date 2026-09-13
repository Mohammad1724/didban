# گزارش آیتم ۲۴ فاز ۲ — H19: generator IPTables — بدون validation + غیر-idempotent

## مشکل (طبق ممیزی فاز ۱)

`TunnelEngine.generateIptables()`:

1. **بدون validation:** اگر `foreignHost` خالی باشد، placeholder `KHAREJ_IP` به‌عنوان literal داخل rule می‌رفت: `--to-destination KHAREJ_IP:80` → deploy خراب با خطای مبهم. (و اگر hostname داده می‌شد، iptables اصلاً DNS ندارد — rule باز هم خراب.)
2. **غیر-idempotent:** `iptables -t nat -A ...` بدون هیچ چک — هر deploy دوباره، rule‌های دوقلو. حذف پورت از لیست → rule یتیم می‌ماند.
3. **`tee -a /etc/sysctl.conf`:** هر deploy خط تکراری `net.ipv4.ip_forward=1` به sysctl.conf کاربر اضافه می‌کرد.
4. **پایداری reboot شکسته:** persistence فقط با `apt-get install iptables-persistent ... || true` — اگر package نصب/به‌روز نبود، rule‌ها بعد از reboot می‌رفتند و هیچ بک‌آپی وجود نداشت.
5. (یافتهٔ جانبی) هیچ سرویس/systemd نبود ولی ایجنت status را روی `didban-tunnel-<id>` query می‌کرد → deploy موفق هم «stopped/failed» گزارش می‌شد و start/stop/delete هیچ‌کار نمی‌کردند.

## رفع (ریشه‌ای)

| مؤلفه | حالا |
|---|---|
| **validation** | `validateForDeploy` برای IPTABLES: `foreignHost` **اجباری** + **IPv4 سخت‌گیرانه** (iptables DNS ندارد). در `generateIptables` هم یک `require(isIpv4)` دفاعی — placeholder هرگز وارد rule نمی‌شود |
| **rule‌ها** | دو **زنجیره اختصاصی per-tunnel**: `didban-tun-<hash>` (PREROUTING/DNAT) و `didban-tunp-<hash>` (POSTROUTING/MASQUERADE) — هر بار **flush و بازتولید** → deploy تکراری = همان set؛ پورت حذف‌شده self-heal می‌شود. hash = ۱۶ هگزادسیمال از SHA-256(id) چون نام زنجیره iptables حداکثر ۲۸ کاراکتر است ولی id تا ۶۴ |
| **jump rules** | check-then-add (`-C ... \|\| -I`) — بدون دوقلو |
| **ip_forward** | **sysctl.d drop-in ایدمپوتان** (`99-didban-iptables.conf`) + `sysctl -w` فوری — دیگر `tee -a /etc/sysctl.conf` و `apt-get iptables-persistent` وجود ندارند |
| **systemd** | یونیت **oneshot/RemainAfterExit** با اسمی که ایجنت query می‌کند (`didban-tunnel-<id>.service`): `ExecStart` = اسکریپت rule‌ها (در هر boot و restart اعمال می‌شود → status درست می‌گیرد)، `ExecStop` = flush + حذف jumps + حذف زنجیره‌ها (یعنی stop و **delete ایجنت** rule‌ها را پاک می‌کنند — قبل‌تر rule برای همیشه می‌ماند) |

## شواهد

- **compile روی kotlinc 1.9.24 (toolchain واقعی اپ)** — closure کامل شامل `TunnelEngine.kt` — بدون خطا.
- **تست‌های واحد: OK (31 tests)** — 5 تست جدید در `TunnelIptablesGeneratorTest` + 15 تست Narnia + 11 تست field-validation:
  - foreignHost خالی → reject با پیام روشن؛ hostname → reject (iptables has no DNS)؛
  - محتوای تولیدشده: بدون `KHAREJ_IP`، بدون `tee -a`، بدون `iptables-persistent`، sysctl.d، زنجیره‌های ≤28 کاراکتر، flush-قبل-بازتولید، decode یونیت b64 (oneshot + ExecStop cleanup)؛
  - **e2e با emulator وضعیت‌دار iptables** (stub با state روی دیسک): اجرای واقعی اسکریپت نصب → آرته‌فکت‌ها روی دیسک → اجرای اسکریپت rule دو بار → **state دقیقاً یکسان (2 DNAT + 2 MASQ + 1 jump در هر سمت — هیچ rule دوقلوای نیست)** → شبیه‌سازی ExecStop → زنجیره‌ها و jumps پاک؛
  - **self-heal:** deploy با پورت 80 سپس deploy مجدد با پورت 443 روی همان تانل → rule پورت 80 ناپدید، rule 443 وجود دارد.

## تحلیل regression

- core IPTables قبلاً در حالت خالی/hostname **کار نمی‌کرد** (rule خراب) — حالا reject با پیام روشن می‌شود؛ حالت سالم (IPv4) قبلاً rule می‌ساخت ولی idempotent نبود — حالا همان mapping با semantics یکسان (DNAT tcp/udp + MASQUERADE محدوده‌دار به `-d foreignIp --dport`) اما پاک و قابل تکرار.
- `autoDeployTunnel`/`controlRemoteTunnel`/`deleteTunnel` ایجنت بدون تغییر قرارداد — فقط حالا یونیتی که query می‌کنند **وجود دارد** (status درست، start=بازاعمال rule، stop/delete=پاک‌سازی rule).
- `discoverTunnels` دست‌نخورده (IPTables process ندارد و قبل‌تر هم discover نمی‌شد).

## خلأهای صادقانه

- Android SDK در این محیط نیست: تأیید با compile closure (Kotlin 1.9.24) + 31 تست (شامل e2e با emulator iptables) بود، نه اجرای روی دستگاه/سرور واقعی.
- emulator iptables یک بازآفرینی کوچک (case روی عملیات‌های استفاده‌شده: `-nL/-N/-F/-A/-C/-I/-D/-X`) است، نه iptables کامل — برای اثبات idempotency/cleanup کافی بود ولی رفتار edge-case‌های واقعی iptables (مثلاً policy rules دیگر کاربر روی PREROUTING) پوشش نمی‌شود؛ jump rule ما با `-I` (سری اول) اضافه و با `-D` (match کامل) حذف می‌شود و rule‌های دیگر کاربر دست نمی‌خورد.
- حذف فایل rule اسکریپت (`/etc/didban/iptables-<hash>-rules.sh`) و sysctl.d در DeleteTunnel انجام نمی‌شود — بخشی از آیتم عمومی «cleanup ناقص در Delete» (پیگیری ثبت‌شده).

## یادداشت ابزار

- هاسپ JVM در این جلسه از بین رفته بود ( `/tmp` پایداری ندارد)؛ بازنشانی شد و این‌بار در `/home/user/.ktest` ماندگار شد (kotlinc 1.9.24 + jars + stubها + `build.sh`/`test.sh`) — جلسه‌های بعدی بدون دانلود مجدد.
