# گزارش آیتم ۲۹ — env کانتینرها در API ایجنت (بازیابی توکن تانل‌های کشف‌شده از Docker)

**فایل‌های تغییرکرده:** `agent/docker.go` + تست جدید `agent/docker_test.go`، `Model.kt`، `TunnelSecrets.kt`، `TunnelEngine.kt` (اسکن Docker) + گسترش `TunnelDiscoveryTest.kt`

## مشکل (ریشه)

ادامه‌ی مستقیم آیتم ۲۸: تانل‌های **کانتینری** (مثل Narnia که با `docker run -e PASSWORD=...`
استقرار می‌کند) توکنشان را در **env کانتینر** دارند — نه در cmdline (که اسکن پروس
می‌بیند). API فعلی ایجنت (`containers/json`) اصلاً env برنمی‌گرداند، پس برای این تانل‌ها
توکن «ناشناخته» می‌ماند و کاربر مجبور بود دستی وارد کند.

همچنین اسکن Docker، **Narnia را اصلاً تشخیص نمی‌داد** (تأیید تصویر `stormotron/narnia`
در لیست نبود) — یعنی تانل Narniaی کانتینری فقط وقتی کشف می‌شد که اسم پروس داخل کانتینر
در اسکن /proc شامل «narnia» می‌شد، و حتی آن‌وقت هم کلیدش دسترس‌ناپذیر بود.

## طراحی (امنیت‌محور)

**تصمیم کلیدی: فیلتر سمت ایجنت — کلیدهای allowlist، نه env کامل.**

env کانتینرها پر از رازهای سرویس‌های **بیگانه** روی هاست است (پاسورد دیتابیس، API
key و…). اگر ایجنت env کامل را از طریق API با Bearer-token برگرداند، سطح نشت رازهای
نامرتبط به اپ (و هر مهاجمی که توکن ایجنت را بگیرد) گسترش می‌یابد. بنابراین:

1. ایجنت فقط کلیدهای allowlist را از `Config.Env` (با `inspect`) می‌خواند و برمی‌گرداند.
   allowlist فعلی: `PASSWORD` (قرارداد env خودِ Narnia.sh). افزودن هسته‌ی جدید = یک
   کلید در یک map + یک بازوی `when`.
2. تطبیق **دقیق** کلید (substring نه): `DB_PASSWORD` هرگز به‌جای `PASSWORD` نشت نمی‌کند.
3. `inspect` برای هر کانتینر یک درخواست جداست → محدود به ۵۰ کانتینر اول
   (`maxEnvInspect`) و best-effort (شکست inspect، لیست را خراب نمی‌کند).
4. اپ هم فقط برای **هسته‌ی خاص** env را می‌خواند (`tokenFromEnv`: NARNIA → PASSWORD) —
   defense-in-depth دوم.

## تغییرات

### ایجنت (Go)
- `ContainerInfo.Env map[string]string` — فقط کلیدهای allowlist
  (`dockerEnvAllowlist`)؛ `filterEnv` با تطبیق دقیق + رد مقدار خالی + خروجی nil
  (فیلد از JSON حذف می‌شود) وقتی چیزی نگه‌داشته نشد.
- `inspectEnv` — `GET /v1.41/containers/<id>/inspect` → `Config.Env` → فیلتر.
- `GetDockerContainers` بعد از لیست، env را برای حداکثر ۵۰ کانتینر اول غنی می‌کند.
- `dockerSockPath` متغیر شد تا تست با **داکمن جعلی روی سوکت unix واقعی**
  (`docker_test.go`) کار کند.

### اپ (Kotlin)
- `DockerContainerItem.env: Map<String, String>` + پارس در `JsonParse.docker`
  (سازگار با ایجنت‌های قدیمی: بدون فیلد → empty).
- `TunnelSecrets.tokenFromEnv(core, env)` — فقط NARNIA کلید `PASSWORD` را می‌خواند
  (+ sanity: خالی/بیش‌از-۲۵۶ رد).
- اسکن Docker در `discoverTunnels`:
  - تشخیص `NARNIA` از تصویر/نام (`narnia in cImage || narnia in cName`).
  - `token = TunnelSecrets.tokenFromEnv(detectedCore, container.env) ?: ""` —
    همچنان هرگز placeholder ساخته نمی‌شود (M17).

## تست

**Go (`agent/docker_test.go`، همه با `-race`):**
- `TestFilterEnv` — allowlist، تطبیق دقیق (`DB_PASSWORD` رد می‌شود)، بدون `=`، کلید خالی،
  مقدار خالی، خروجی nil برای ورودی خالی.
- `TestGetDockerContainersEnrichesFilteredEnv` — داکمن جعلی روی سوکت unix: لیست ۱
  کانتینر Narnia با env ۴ کلید → API دقیقاً `{"PASSWORD": ...}` برمی‌گرداند و بقیه
  (PATH/INTERFACE/MTU) **هرگز** نمی‌رسند.
- `TestGetDockerContainersInspectFailureIsTolerated` — داکمنی که inspect با 500 خطا می‌دهد:
  خلاصه‌ی Docker همچنان موفق + env nil.

**Kotlin (`TunnelDiscoveryTest` — ۳ تست جدید):**
- PASSWORD کانتینر Narnia → توکن واقعی.
- env هسته‌ای است: BACKPACK/GOST هیچ‌وقت PASSWORD نمی‌خوانند؛ خالی/blank/بلند → null.
- `JsonParse.docker`: پارس env موجود + نبود فیلد (ایجنت قدیمی) → empty.

**نتیجه:** Go: `gofmt`/`go vet` تمیز، `go build` OK، `go test -race -count=1` **سبز**.
JVM: `MAIN OK` + **`OK (52 tests)`**.

> شفافیت: مسیر کامل «اسکن Docker در اپ → کشف Narnia → درج توکن» تابعی suspend با
> وابستگی به ایجنت زنده است و در این سازه‌وار end-to-end اجرا نشد؛ اما هر لایه‌ی آن
> جداگانه تست واقعی دارد (پارس، فیلتر، استخراج) و سیم‌بندی میان‌شان code-review شد.

## رفتار کاربری (بعد از این آیتم)

| سناریو | قبل | بعد |
|---|---|---|
| کشف Narnia کانتینری | کشف نمی‌شد (یا فقط از طریق /proc، بدون توکن) | کشف با تصویر + **توکن واقعی از env** |
| deploy تانل Narnia کشف‌شده | لازم بود توکن دستی زده شود | مستقیم deploy می‌شود (توکن واقعی) |
| رازهای env کانتینرهای بیگانه | — | هرگز در API نیستند (فیلتر سمت ایجنت) |

## باقی‌مانده (ثبت‌شده)

- حدس `ir` در تشخیص ایران/خارج (M-grade) — تنها آیتم باز باقی‌مانده‌ی فهرست ثبت‌شده.
- هسته‌های کانتینری دیگر اگر روزی از env token استفاده کنند: یک کلید در
  `dockerEnvAllowlist` + یک بازو در `tokenFromEnv`.
