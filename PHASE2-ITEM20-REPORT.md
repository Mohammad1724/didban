# گزارش آیتم ۲۰ فاز ۲ — M17: KDF/zeroing/auto-lock Vault

## مشکل (طبق ممیزی فاز ۱)

`EncryptedVault.kt` سه ضعف داشت:
1. **PBKDF2 با 100k تکرار** — حداقل فعلی OWASP برای PBKDF2-HMAC-SHA256: **600k** (برخورد brute-force ۶ برابر آسان‌تر)
2. **`password.toCharArray()` هرگز zero نمی‌شد** — متریال password تا GC در memory می‌ماند
3. **بدون auto-lock** — notes رمزگشایی‌شده (+ خودِ master password در state Compose) تا کشتن process در حافظه می‌ماندند (دکمهٔ Lock دستی وجود داشت، ولی قفل خودکار نه)

## راه‌حل

### ۱. `EncryptedVault.kt` — KDF قوی‌تر **با سازگاری کامل**

- `ITERATIONS = 600_000` (استاندارد OWASP)
- **فرمت نسخه‌شده v2** — چون 100k→600k کلیدهای قدیمی را می‌شکست:
  - `v2: [magic "DID2":4][version:1][iterations:4 BE][salt:16][iv:12][ct+tag]`
  - `v1 (قدیمی): [salt:16][iv:12][ct+tag]`
  - **تعداد تکرار داخل خودِ payload سفر می‌کند** — dataهای قدیمی با 100k همان‌طور decrypt می‌شوند، و تنظیم آتیِ KDF دیگر backupها را گروگان نمی‌گیرد
  - magic ۴بایتی = ابهام‌زدایی قطعی (احتمال ۴ بایت اولِ saltِ v1ِ تصادفی مساوی magic: 1/2³²)
- **zeroing متریال password**: کپی `char[]` → `PBEKeySpec.clearPassword()` بلافاصله بعد از derive + صفر کردن آرایهٔ محلی در `finally`
- `android.util.Base64` → `java.util.Base64` (minSdk 26 ✓، کدینگ یکسان) — فایل حالا **JVM-testable** است

### ۲. `ToolkitScreens.kt` — auto-lock واقعی

- تابع مشترک `lock()`: پاک کردن `notes` + `password` + `revealedNoteId` (String در JVM zero نمی‌شود؛ حذف reference = زودترین GC ممکن) — دکمهٔ Lock موجود هم حالا از همین تابع استفاده می‌کند (قبلاً `revealedNoteId` را پاک نمی‌کرد)
- **`DisposableEffect` + `LifecycleEventObserver`**: رویداد `ON_STOP` (برو به background) → auto-lock
- (تغییر tab از قبل state را با ترک composition رها می‌کرد؛ خلأ دقیقاً همان background بود)

## تغییرات

| فایل | تغییر |
|---|---|
| `EncryptedVault.kt` | 600k + فرمت v2 با magic/iterations داخل payload + `deriveKey` با zeroing + `java.util.Base64` |
| `ToolkitScreens.kt` | `lock()` مشترک + auto-lock روی ON_STOP + importهای lifecycle |
| `EncryptedVaultTest.kt` | **جدید** — ۷ تست JVM |

## شواهد

- **JVM: `OK (150 tests)`** (۷ تست جدید) — مهم‌ترین‌ها:
  - **`legacy 100k payloads stay decryptable`**: payload v1 ساخته‌شده با الگوریتم قدیمی (PBKDF2@100k، بدون magic) → با کد جدید decrypt می‌شود ← **vaultها و backupهای موجود کاربر‌ها شکسته نمی‌شوند**
  - `new payload starts with the versioned magic`: bytes 0-3 = `DID2`، version=1، iterations=600000 خوانده شد
  - round-trip 600k، wrong-password → GCM auth failure، tamper → rejection، truncated/garbage → `IllegalArgumentException`
- سازگاری فرمت در `Prefs`/`BackupEngine` خودکار است: canary/notes/backupهای v1 با همان `decrypt` خوانده می‌شوند؛ هر نوشتهٔ جدید v2 است
- بالانس ساختاری هر دو فایل Kotlin: سالم

### تحلیل رگرسیون

| مصرف‌کننده | تأثیر |
|---|---|
| Vault موجود (canary + notes v1) | بدون تغییر — decrypt legacy ✓ (تست‌شده) |
| Backupهای exportشده قبلی | import سالم ✓ (همان مسیر decrypt) |
| Backupهای جدید | v2 با 600k |
| UI Vault | دکمه Lock: همان رفتار + پاک شدن `revealedNoteId`؛ قفل خودکار روی background = تغییر رفتار **به‌سمت امن** (کاربر پس از بازگشت password وارد می‌کند — همان UX قفل دستی) |
| زمان unlock/save | 600k ≈ ۰.۵–۲s روی گوشی (یک‌بار در unlock و در هر save صریح) — قابل‌قبول برای vault |

## صداقت — چه چیزی تست نشد

- **zeroing**: از نظر کد پیاده‌شده و type-check شده، ولی صفرشدن واقعی char[] قابل مشاهدهٔ مستقیم در تست JVM نیست (محدودیت ذاتی) — `PBEKeySpec.clearPassword()` API رسمی JDK است
- **auto-lock**: مسیر Compose/lifecycle در JVM compile نمی‌شود → فقط code-review؛ تست دستگاه: vault را unlock کنید → Home بزنید → برگردید → باید قفل باشد
- بیلد اندروید کامل در این sandbox ممکن نیست

## وضعیت فاز ۲

آیتم‌های ۱ تا ۲۰ تمام — **جدول security ممیزی فاز ۱ کامل شد** (C1, C2, C3, C4, C5, C6, H1–H5(+H5-1), H7–H15, H20, M10, M16, M17). باقی‌مانده: موارد عملکردی — H12 (growth بی‌حد `events.jsonl` + history فقط memory)، H16 (matching تونل‌ها با substring)، H18 (Narnia `ip_forward`/fallback)، H19 (IPTables generator بدون validation).
