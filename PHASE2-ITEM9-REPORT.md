# گزارش آیتم ۹ فاز ۲ — persist شدن توکن/کلید تونل (H3)

تاریخ: ۲۰۲۶-۰۹-۱۳ | push به `main`

## ۱. ریشه (از کد)

در `TunnelEngine.generateCode` هر core:

```kotlin
val token = cfg.token.ifBlank { generateRandomToken(24) }   // ← توکنِ جدید، فقط محلی
```

توکن تولیدشده **هرگز به `cfg.token` (مدل persist‌شده) برنمی‌گشت**. یعنی:

1. کاربر تانل می‌سازد (token خالی) → `autoDeployTunnel` → `generateCode` → توکن T1 تولید → هر دو سرور با T1 deploy می‌شوند. `cfg.token` باز هم خالی روی دیسک می‌ماند.
2. کاربر بعداً **redeploy** می‌زند → `generateCode` دوباره → **T2 جدید** → جفت شکسته: سمتی که قبلاً deploy شده (یا نسخهٔ درحال‌اجرا) هنوز T1 دارد.
3. **خلاف‌گرانه‌تر:** `SpoofTunnel` حتی `cfg.token` را نمی‌خواند و **هر بار** دو کلید جدید `generateRandomToken(32)` می‌ساخت — حتی با token تنظیم‌شده، بازتولید کد = جفت شکسته.
4. **باگ امنیتی پنهان:** ۴ core (Backhaul/Rathole/Chisel/FRP) با **secret سخت‌کدشدهٔ شناخته‌شده** (`didban_chisel_secret` و...) fallback داشتند — همهٔ deployهای جهان با همان کلید مشترک.

## ۲. اصلاح (ریشه‌ای)

**اصل:** مدل `TunnelConfig` منبع واحد واقعیت برای secret است؛ secret **یک‌بار** تولید می‌شود، به مدل برمی‌گردد، و همهٔ تولید کد/redeploy بعدی **همان** را استفاده می‌کنند.

### `TunnelSecrets.kt` (جدید — خالص، بدون Android، JVM-testable)
- `generateRandomToken(length)` — SecureRandom، کاراکترهای امن برای shell/کانفیگ.
- `deriveKey(seed, domain)` — بسط قطعی کلید: `SHA-256(seed|domain)` (32 هگز) — برای coreهایی که دو secret از یک seed persist‌شده نیاز دارند.
- `ensureToken(current, onGenerated)` — اگر خالی: تولید + گزارش به فراخوان (برای نگارش در مدل)؛ اگر پر: **بدون تغییر**.

### `TunnelEngine.kt`
- `ensureToken(cfg)` — write-back به `cfg.token` (یک‌بار، idempotent).
- **هر ۸ core** حالا از `ensureToken(cfg)` استفاده می‌کنند (BackPack/Paqet/Narnia/Backhaul/Rathole/Chisel/FRP).
- **SpoofTunnel:** `serverPrivKey = deriveKey(ensureToken(cfg), "server")` + `clientPrivKey = deriveKey(ensureToken(cfg), "client")` — جفت **قطعی و پایدار** از یک token persist‌شده (بدون تغییر مدل).
- **secret‌های سخت‌کدشده حذف شدند** — ۴ core حالا توکن تصادفی ۲۴ کاراکتری پایدار دارند.
- `persistTunnel(ctx, cfg)` (جدید) — upsert در لیست persist‌شده.
- **`autoDeployTunnel`:** بعد از deploy، `persistTunnel(ctx, cfg)` — تضمین اینکه secret + syncStatus از reload بعدی (`refreshTunnels()` که از disk می‌خواند) **زنده می‌ماند**.

### `TunnelScreen.kt`
- دیالوگ «نمایش کد» (`ViewTunnelCodeDialog`): `generateCode` ممکن است secret را materialize کند → `LaunchedEffect` بلافاصله `persistTunnel` — یعنی کاربری که دستور را کپی و دستی اجرا می‌کند، بعداً اگر از اپ redeploy کند، **همان** کلید را می‌گیرد (نه کلید تازه).

## ۳. شواهد تست

| لایه | شواهد |
|---|---|
| **JVM: `TunnelSecretsTest` (۸ تست جدید)** | طول/کاراکتر/بلاتکراری توکن؛ قطعی `deriveKey` + فرمت 32 هگز؛ تفاوت domain/seed؛ **ensureToken**: خالی→تولید+گزارش، پر→بدون تولید/گزارش؛ **سناریوی دقیق H3**: redeploy با token persist‌شده → seed/جفت کلید **تطابق کامل** + دو کلید متمایز — **OK (61 tests کل)** |
| ساختاری | توازن brace/paren = 0/0 در TunnelEngine/TunnelScreen/TunnelSecrets؛ grep: همهٔ ۸ core روی `ensureToken(cfg)`، هیچ fallback سخت‌کدشده‌ای باقی نمانده |

## ۴. آنچه با این محیط قابل تست نبود (صریح)

**این بخش به دلیل نبود Android SDK/gradle و نبود دو سرور واقعی (ایران+خارج)، امکان build اپ و تست زندهٔ deploy/redeploy واقعی تونل را نداشت.**
- منطق secret (تولید/پایداری/بسط): **test-executed روی JVM** — سبز.
- سیم‌کشی (persistTunnel در deploy/preview، write-back در generatorها): فقط از نظر کد بررسی شد (بازبینی کامل diff + تطبیق با API موجود) و در اولین build/اجرای واقعی تأیید می‌شود.

## ۵. تحلیل رگرسیون

- **تانل‌های موجود با token خالی** (که با نسخهٔ قدیمی deploy شده‌اند): secret در آن‌ها گم شده بود (ذاتِ باگ). با آپدیت، اولین generateCode/redeploy یک توکن پایدار تولید + persist می‌کند؛ `autoDeployTunnel` هر دو سمت را با همان توکن deploy می‌کند → جفت دوباره منسجم می‌شود.
- **تانل‌هایی که کاربر token دستی تنظیم کرده:** بدون تغییر (همان token استفاده می‌شود) — چرخش کلید دستی هنوز ممکن است (تغییر فیلد token).
- **auto-detected** (کشف خودکار): رشتهٔ ثابت «auto-detected» است و پایدار است (نه مشکل H3) — بدون تغییر، خارج از scope.
- **agent:** بدون تغییر — token متعلق به اپ است و agent فقط کانفیگ را می‌نویسد/بازنویسی می‌کند؛ `restart` (controlRemoteTunnel) هیچ secret جدیدی نمی‌سازد.
- **کنتراکتها:** `TunnelConfig`/JSON بدون تغییر فیلد؛ `generateRandomToken` عمومی برای فرم حفظ شده (delegation).
- **امنیت:** ۴ secret شناخته‌شدهٔ سراسری حذف شدند → هر deploy حالا کلید یکتا دارد.

## ۶. گام بعدی (آیتم ۱۰)

H4 — Injection در اسکریپت‌های heredoc تولیدشده (مقدارهای کاربر/کشف‌شده داخل اسکریپت‌های shell بدون quoting).
