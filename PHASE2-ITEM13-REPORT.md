# گزارش آیتم ۱۳ فاز ۲ — H9: از دست رفتن کامل وضعیت در چرخش صفحه

## مشکل (طبق ممیزی فاز ۱)

در `DidbanApp` همهٔ وضعیت ناوبری با `remember { mutableStateOf(...) }` بود:
`openServer` (آبجکت `ServerConfig`)، `currentNav` (تب فعال)، `lang`، `themeMode`.
با چرخش صفحه (rotation) یا process-death:
- داشبورد سرور باز بود؟ → **بسته می‌شد** و کاربر به فلوت بازمی‌گشت
- در تب Tunnels/Uptime/Network/Vault بود؟ → به **تب ۰ (فروت)** برمی‌گشت
- هیچ ViewModel/SavedStateHandle/rememberSaveable وجود نداشت

ممیزی نشان داد `lang` و `themeMode` در واقع با هر تغییر به Prefs هم persist می‌شدند (پس با rotation از prefs دوباره می‌آمدند)؛ **دو متغیر واقعاً از‌دست‌رفته `openServer` و `currentNav` بودند.**

## راه‌حل (حداقل‌مقیاس، بدون وابستگی جدید)

**`rememberSaveable`** (از همان `compose.runtime` که در پروژه است — بدون ViewModel/lifecycle-dependency جدید که در این محیط قابل build/verify نیست):

1. **`openServer` به `openServerId: Long?` تبدیل شد** — id (یک primitive قابل‌ذخیره) در SavedStateBundle حفظ می‌شود و آبجکت سرور در هر composition از لیست persist‌شده resolve می‌شود:
   ```kotlin
   var openServerId by rememberSaveable { mutableStateOf<Long?>(null) }
   val openServer: ServerConfig? = openServerId?.let { id ->
       Prefs.loadServers(ctx).firstOrNull { it.id == id }
   }
   ```
   مزایای فرعی:
   - اگر کاربر سروری را ویرایش کند (نام/پورت/pin)، داشبورد بعد از هر recomposition **دادهٔ به‌روز** را نشان می‌دهد (نه آبجکت قدیمیِ در حافظه)
   - اگر سرور حذف شده باشد، `openServer == null` → به‌جای ارجاعِ آویزان، به‌نرمی به فلوت برمی‌گردد (یک `LaunchedEffect` id آویزان را هم پاک می‌کند تا در bundle نماند)
2. **`currentNav`** — `rememberSaveable { mutableStateOf(0) }` → تب فعال بعد از rotation/process-death برمی‌گردد
3. **`lang`/`themeMode`** — به `rememberSaveable` تغییر کردند (هم‌راستا با بقیه؛ هر دو از قبل در Prefs هم بودند، پس رفتاری تغییر نمی‌کند ولی مسیر بازگردانی یکنواخت می‌شود)
4. همهٔ assignmentها به‌صورت `openServerId = ...` بازنویسی شدند (onOpen، onBack، BackHandler، deep-link)
5. `lastBackPressTime` (شمارندهٔ «دوباره بزن برای خروج») عمداً `remember` ساده ماند — وضعیت گذرای UI است و نباید persist شود

**عمق scope:** آیتم ممیزی وضعیت **ناوبری سطح اپ** است. دیالوگ‌ها/فیلدهای داخل هر صفحه (مثلاً متن جست‌وجوی ServersScreen) با rotation هنوز از نو شروع می‌شوند — خارج از scope این آیتم (اگر خواستید، در فاز ۳ با معماری صفحه‌ای جدید یکجا حل می‌شود).

## چرا ViewModel + SavedStateHandle نبود؟

- اضافه‌کردن `lifecycle-viewmodel-compose` یک **وابستگی جدید** است که در این محیط قابل build/verify نیست (ریسک regression بدون شواهد) — برخلاف `rememberSaveable` که از `compose.runtime` موجود است
- `rememberSaveable` دقیقاً همان مشکل را حل می‌کند (survival در rotation **و** process-death از طریق savedInstanceState Bundle فعالیت) — همان گزینهٔ دوم پیشنهادی ممیزی: «حداقل rememberSaveable + persist در process-death»

## تغییرات

| فایل | تغییر |
|---|---|
| `MainActivity.kt` | `DidbanApp`: چهار متغیر ناوبری → `rememberSaveable`؛ `openServer` به resolve-by-id از لیست persist‌شده؛ پاک‌سازی id آویزان؛ import `rememberSaveable` (+29/−8، فقط همین فایل) |

ایجنت Go و بقیهٔ فایل‌ها دست‌نخورده.

## شواهد

- **JVM: `OK (117 tests)`** — بدون افت (این آیتم کامپوننت خالص JVM ندارد؛ منطق resolution یک‌خطی `firstOrNull { it.id == id }` است)
- بالانس براکت/پرانتز `MainActivity.kt`: سالم
- ارجاع‌های باقی‌مانده به pattern قدیم: صفر (grep `openServer =` → همه `openServerId =`)
- `rememberSaveable` برای `Int`/`String`/`Long?` همه native-saveable هستند (نیاز به Saver سفارشی ندارد)

## صداقت — چه چیزی تست نشد

- این تغییر **کاملاً سمت اندروید/Compose** است و در این محیط (بدون SDK اندروید) **build/اجرا نشد → فقط از نظر کد بررسی شد**
- تست‌های پیشنهادی روی دستگاه/AVD:
  1. در تب Tunnels → چرخش → باید در همان تب بماند
  2. داشبورد سرور باز → چرخش → داشبورد همان سرور باز بماند (metrics از Repo/coordinator که در H7 ساخته شد، سریع برمی‌گردد)
  3. داشبورد باز → force-stop + بازکردن (process death) → داشبورد برگردد
  4. deep-link از اعلان (tap روی نوتیفیکیشن سرور) → چرخش → همان سرور باقی بماند
  5. (حاشیه) حذف سرور درحالی‌که id در bundle است → بازگشت نرم به فلوت

## وضعیت فاز ۲

آیتم‌های ۱ تا ۳ تمام. بعدی طبق ترتیب: **H10 — الگوریتم‌های ضعیف SSH در engineهای exec** (`SshEngine`/`SshSetup`: arcfour/3des-cbc/blowfish/aes-*-cbc + kexهای SHA-1 + ssh-dss 1024-bit؛ درحالی‌که `SftpEngine` با whitelist تمیز کار می‌کند — ناهمسان؛ پیچیدگی کم).
