# دیدبان — Didban 👁️

**سرورهای لینوکسی‌ات را از جیبت ببین. ساده، شیک — و بهت می‌گوید دیشب «چه چیزی» CPU را خورد.**

🇬🇧 [English](README.md) | 🇮🇷 فارسی

---

## چرا دیدبان؟

هر ادمین سروری این سؤال ساعت ۲ نصفه‌شب را می‌شناسد: **«دیشب CPU صد درصد بود — چی بود؟»** دیدبان جوابش را می‌دهد. یک ایجنت سبک روی هر سرور اجرا می‌شود، اسپایک‌های منابع را **همراه با پروسه‌های مقصر** ثبت می‌کند، هشدارهای آنی به **تلگرام** می‌فرستد، امکان **بستن پروسه‌های مخرب با ۱ کلیک** را می‌دهد، و با **Check-Host جهانی** وضعیت دسترسی به سرور را می‌سنجد.

- 📊 **متریک زنده** — CPU (شامل %steal!)، رم/سواپ، دیسک، ترافیک شبکه، لود، آپتایم
- 🕵️ **لاگ اسپایک** — هر اسپایک CPU/رم سمت سرور ثبت می‌شود همراه با **پروسه‌های مقصر** — حتی وقتی گوشی‌ات خاموش است
- ✈️ **هشدارهای آنی تلگرام** — ارسال پیام دقیق در زمان وقوع اسپایک همراه با پروسه‌های مقصر (پشتیبانی از پروکسی SOCKS5/HTTP)
- 🛑 **مدیریت و بستن پروسه‌ها** — جستجو، بررسی و بستن امن پروسه‌ها (SIGTERM / SIGKILL) از داخل اپلیکیشن یا API
- 🌐 **تست دسترسی جهانی (Check-Host)** — بررسی آنلاین وضعیت سرور (Ping / HTTP / TCP / DNS) از بیش از ۲۰ نود در جهان (آلمان، آمریکا، ایران، فرانسه و...)
- 📈 **تاریخچه** — نمودار ۲۴ ساعته با تفکیک دقیقه
- 📱 **اپ اندروید** — دوزبانه (فارسی/انگلیسی)، تم تیره، نصب خودکار ۱-کلیکه با SSH
- 🔐 **امن به‌صورت پیش‌فرض** — HTTPS با cert خوداموقع + توکن؛ اپ fingerprint سرتیفیکیت را پین می‌کند (مثل SSH)
- 🪶 **سبک** — یک باینری استاتیک Go (~۸ مگابایت)، بدون هیچ وابستگی جانبی، ~۱۰ مگ رم

## معماری

```
┌────────────── گوشی تو ─────────────────┐
│  اپ اندروید دیدبان                     │
│  • داشبورد زنده + نمودار                │
│  • لاگ اسپایک: «چی CPU رو برد بالا؟»   │
│  • خاتمه پروسه‌ها (SIGTERM / SIGKILL)  │
│  • تست جهانی وضعیت سرور (Check-Host)    │
└───────────────┬────────────────────────┘
                │ HTTPS + Bearer token + cert pinning
┌───────────────▼────────────────────────┐
│  didban-agent (روی هر سرور)            │
│  • /api/metrics  /api/processes        │
│  • /api/events   /api/history          │
│  • /api/processes/kill                 │
│  • ارسال هشدارهای فوری تلگرام          │
│  • ثبت اسپایک ۲۴ ساعته روی دیسک        │
└────────────────────────────────────────┘
```

## نصب (یک دستور برای هر سرور)

روی هر سرور اجرا کن (اوبونتو/دبیان، هر معماری — amd64/arm64/arm/386):

```bash
curl -fsSL https://raw.githubusercontent.com/Mohammad1724/didban/main/agent/install.sh -o didban-install.sh
sudo bash didban-install.sh
```

اگه فایروال داری، پورت رو باز کن:

```bash
sudo ufw allow 8686
```

نصب‌کننده باینری را از آخرین Release گیت‌هاب می‌گیرد، توکن می‌سازد، سرویس systemd هاردند نصب می‌کند و **آدرس**، **توکن** و **fingerprint سرتیفیکیت** را چاپ می‌کند — این سه را برای اپ اندروید نگه دارید.

تست نهایی:

```bash
curl -sk https://IP_سرور:8686/api/metrics -H "Authorization: Bearer توکن"
```

<details>
<summary>روش جایگزین: کلون کردن ریپو</summary>

```bash
git clone https://github.com/Mohammad1724/didban.git
cd didban/agent
sudo bash install.sh
```
</details>

<details>
<summary>روش جایگزین: اجرای دستی برای تست (بدون نصب)</summary>

```bash
cd agent && go build -o didban-agent .
./didban-agent -addr 127.0.0.1:8686 -token mytoken -data ./data -plain
```
</details>

## تنظیم هشدارهای تلگرام

برای دریافت پیام در تلگرام هنگام مصرف بالا یا افتادن پروسه‌ها، مقادیر زیر را در فایل `/etc/didban/agent.conf` قرار دهید:

```bash
# در فایل /etc/didban/agent.conf
DIDBAN_TG_TOKEN="123456789:ABCdefGHIjklMNOpqrsTUVwxyz"
DIDBAN_TG_CHAT_ID="-100123456789"
# پروکسی اختیاری (اگر دسترسی به تلگرام روی سرور نیاز به تونل دارد):
# DIDBAN_TG_PROXY="socks5://127.0.0.1:1080"
```

سپس سرویس را ری‌استارت کنید:
```bash
sudo systemctl restart didban-agent
```

تست ارسال پیام به تلگرام:
```bash
curl -sk -X POST https://IP_سرور:8686/api/alerts/telegram/test -H "Authorization: Bearer توکن"
```

## API

همه‌ی مسیرهای `/api/*` نیاز به `Authorization: Bearer <token>` دارند (یا `?token=`).

| متد | مسیر | توضیح |
|---|---|---|
| `GET` | `/health` | زنده بودن (بدون توکن) |
| `GET` | `/api/metrics` | CPU (شامل %steal)، رم، سواپ، دیسک، شبکه، لود، آپتایم |
| `GET` | `/api/processes` | ۲۵ پروسه‌ی برتر بر اساس CPU |
| `POST` | `/api/processes/kill` | خاتمه دادن امن به پروسه (`{"pid": 1234, "signal": "SIGTERM"}`) |
| `POST` | `/api/alerts/telegram/test` | ارسال پیام تست به بات تلگرام |
| `GET` | `/api/events?limit=50` | رویدادهای اسپایک با پروسه‌های مقصر |
| `GET` | `/api/history?hours=24` | تاریخچه‌ی دقیقه‌ای برای نمودار |

### نمونه ارسال دستور خاتمه پروسه:
```bash
curl -sk -X POST https://IP_سرور:8686/api/processes/kill \
  -H "Authorization: Bearer توکن" \
  -H "Content-Type: application/json" \
  -d '{"pid": 351964, "signal": "SIGTERM"}'
```

```json
{
  "pid": 351964,
  "name": "gzip",
  "signal": "SIGTERM",
  "success": true,
  "message": "Signal SIGTERM sent to gzip (PID 351964)"
}
```

## تنظیمات

| فلگ | متغیر محیطی | پیش‌فرض | توضیح |
|---|---|---|---|
| `-addr` | `DIDBAN_ADDR` | `:8686` | آدرس گوش دادن |
| `-token` | `DIDBAN_TOKEN` | خودکار | توکن |
| `-data` | `DIDBAN_DATA` | `/var/lib/didban` | پوشه داده (رویدادها، cert، توکن) |
| `-plain` | `DIDBAN_PLAIN=1` | خاموش | غیرفعال کردن TLS (**توصیه نمی‌شود**) |
| `-cpu-th` | `DIDBAN_CPU_TH` | `70` | آستانه اسپایک CPU (٪) |
| `-mem-th` | `DIDBAN_MEM_TH` | `90` | آستانه اسپایک رم (٪) |
| `-steal-th` | `DIDBAN_STEAL_TH` | `10` | آستانه دزدی CPU / Steal (٪) |
| `-disk-th` | `DIDBAN_DISK_TH` | `90` | آستانه هشدار پر شدن دیسک (٪) |
| `-tg-token` | `DIDBAN_TG_TOKEN` | خالی | توکن ربات تلگرام |
| `-tg-chat` | `DIDBAN_TG_CHAT_ID` | خالی | شناسه چت / گروه تلگرام |
| `-tg-proxy` | `DIDBAN_TG_PROXY` | خالی | آدرس پروکسی SOCKS5/HTTP برای تلگرام |
| `-tg-alerts` | `DIDBAN_TG_ALERTS` | `1` | فعال بودن هشدارهای تلگرام |

## امنیت

- HTTPS با cert خوداموقع (۱۰ سال، همه IPهای سرور در SAN)
- اپ اندروید fingerprint سرتیفیکیت را پین می‌کند — trust on first use، دقیقاً مثل SSH
- احراز هویت Bearer با مقایسه‌ی زمان-ثابت، توکن با دسترسی 0600
- محافظت در برابر خاتمه پروسه‌های سیستمی: پروسه‌های PID 1 و خود دیدبان غیرقابل Kill هستند
- هاردنینگ systemd: فایل‌سیستم فقط-خواندنی به‌جز پوشه داده

## لایسنس

MIT — [LICENSE](LICENSE)
