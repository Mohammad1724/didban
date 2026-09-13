# گزارش آیتم ۲۲ فاز ۲ — H16: matching نادرست سرورهای تونل با substring

## مشکل (طبق ممیزی فاز ۱)

`TunnelEngine.autoDeployTunnel` سرور مقصد تانل را این‌گونه پیدا می‌کرد:

```kotlin
(cfg.iranServerId != null && s.id == cfg.iranServerId) ||
(cfg.iranHost.isNotBlank() && (s.host.trim() == cfg.iranHost.trim() || cfg.iranHost.trim().contains(s.host.trim())))
```

وقتی `iranServerId`/`foreignServerId` ذخیره‌شده null (یا قدیمی) بود، fallback یک **substring match** بود: `tunnelField.contains(serverHost)`. پیامدها:

1. **deploy روی ماشین اشتباه.** با دو سرور ثبت‌شدهٔ `example.com` و `sub.example.com`، فیلد تانل `sub.example.com` با سرور `example.com` هم match می‌شد (`firstOrNull` ترتیب لیست را می‌گیرد)؛ یعنی ایجنت هم config می‌نوشت **و هم install script را روی سرور اشتباه اجرا می‌کرد**. برای IP هم همین: فیلد `10.0.0.100` با سرور `10.0.0.1` match می‌شد.
2. **host خالیِ سرور همه‌چیز را می‌بلعید.** `"".contains("")` همیشه true است — سرور ثبت‌شده‌ای با host خالی با هر فیلد تانلی match می‌شد.
3. **ناسازگی deploy با control (latent).** `controlRemoteTunnel` فقط `==` case-sensitive داشت. تانلی که فیلد host آن با case متفاوت یا با پورت صریح (`host:443`) ذخیره شده باشد، deploy می‌شد (substring match) ولی start/stop/delete آن **بی‌صدا هیچ‌کاری نمی‌کرد**.

`discoverTunnels` از قبل match دقیق (`==`) داشت — دست نخورده ماند.

## رفع (ریشه‌ای، با یک قاعده مشترک)

تابع خالص جدید `TunnelFieldValidation.hostMatchesServer(tunnelField, serverHost)`:

- trim + lowercase دو طرف (hostname و IPv6 case-insensitive هستند)؛
- host خالیِ سرور **هرگز** match نمی‌شود؛
- match دقیق بر host برنده است؛
- پورت صریح در فیلد تانل جدا و فقط بخش host مقایسه می‌شود (`10.0.0.1:443` ↔ `10.0.0.1`، `[2001:db8::1]:443` ↔ `2001:db8::1`)؛
- `:non-digits` در انتها هرگز به‌عنوان پورت تفسیر نمی‌شود، پس آدرس IPv6 خام از طریق colon-splitting match نادرست نمی‌دهد (`2001:db8::1` در برابر `2001:db8::2` → نخواند).

اعمال روی **هر چهار** محل resolution:

- `autoDeployTunnel` — انتخاب سرور ایران + خارج (قبلاً substring `contains` بود)؛
- `controlRemoteTunnel` — انتخاب سرور ایران + خارج (قبلاً `==` case-sensitive بود؛ حالا دقیقاً هم‌مسیر deploy است، یعنی control همیشه همان ماشینی را هدف می‌گیرد که تانل روی آن deploy شده).

مسیر اصلی مبتنی بر `id` دست‌نخورده است؛ guardهای `isNotBlank` حذف شدند چون `hostMatchesServer` خودش blank را هم پردازش می‌کند (واحدتست شده).

## شواهد

- **compile روی JVM** از کل closure وابستگی‌های واقعی شامل `TunnelEngine.kt` اصلاح‌شده (TunnelFieldValidation، TunnelModel، TunnelSecrets، SecureCipher، SecureStorage، EncryptedVault، Prefs، Model، CertFingerprint، ClientCache، HttpClientPool، ApiClient) — بدون خطا، 73 کلاس (kotlinc 2.0.21 روی JDK 11؛ `Context`/`SharedPreferences` اندروید و `UptimeTarget` فقط به‌صورت stub برای compile — داخل ریپازیتوری نیستند).
- **تست‌های واحد: OK (16 تست)** در `TunnelFieldValidationTest` — ۱۱ تست قدیمی + ۵ تست جدید:
  - match دقیق / با فاصله / case-insensitive؛
  - **هیچ substring match‌ای** (همان regression H16: `sub.example.com` در برابر `example.com`، `10.0.0.100` در برابر `10.0.0.1`، هر دو جهت)؛
  - host خالیِ سرور هرگز match نمی‌شود؛
  - پورت صریح (`host:443`، `example.com:8443` در برابر `EXAMPLE.COM`)؛
  - IPv6 (`[2001:db8::1]:443`، case-insensitive، بدون match نادرست روی IPv6 خام).

## تحلیل regression

- هر resolution درستِ قبلی، درست می‌ماند: matcher جدید supersetِ دقیقِ exact-match است (فقط case variant‌ها و host-part از `host:port` را اضافه می‌کند — هر دو همان ماشین) و subsetِ `contains` قبلی است (فقط match‌های ماشین‌اشتباه را حذف می‌کند).
- رفتار ترتیبی `firstOrNull` دست‌نخورده است.
- API سمت ایجنت و schema متادیتای تونل دست‌نخورده — نیازی به migration سمت سرور نیست.

## خلأهای صادقانه

- در این محیط Android SDK وجود ندارد: تغییر با compile از closure واقعی + تست واحد تأیید شده، **نه** با اجرای روی دستگاه/امولاتور.
- auto-deploy end-to-end در برابر ایجنت زنده در اینجا انجام نشد (نیاز به دو سرور واقعی)؛ regression با تست‌های واحد بالا سنجاق شده است.
