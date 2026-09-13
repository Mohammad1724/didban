# گزارش آیتم ۲۱ فاز ۲ — H12: رشد بی‌حد `events.jsonl` + history فقط memory

## مشکل (طبق ممیزی فاز ۱)

1. **`events.jsonl` بی‌حد رشد می‌کرد:** `EventLog.Add` فقط append می‌زد؛ cap فقط روی memory (500 event) بود، نه فایل. بعد از ماه‌ها spike، فایل ده‌ها MB → فشار دیسک + `load()` کند در هر ری‌استارت.
2. **history فقط memory بود:** `m.hist` (10080 نقطه = ۷ روز) در هر restart **پاک می‌شد** — نمودار ۷ روزه از صفر شروع می‌شد و README هم این را نمی‌گفت.

## راه‌حل

### ۱. `events.go` — فایل bounded با compact اتمیک

- `eventFileMaxBytes = 1 MiB` — بعد از هر append، اگر فایل از بودجه گذشت، **`compactLocked()`** فایل را با همان 500 eventِ حافظه‌ای بازنویسی می‌کند (temp file + `os.Rename` اتمیک → crash در وسط compact، فایل قبلی را سالم می‌گذارد)
- `NewEventLog`: (الف) فایل `.tmp` مانده از compactِ crashed را پاک می‌کند؛ (ب) **در upgrade** — فایل بزرگِ رهاشده از نسخه‌های قدیمی (بدون rotation) — بلافاصله reclaim می‌شود
- نتیجه: فایل هرگز فراتر از ~1MiB نمی‌رود و همواره eventهای جدید را دارد (کاهش = فقط compact، بدون حذف دادهٔ حافظه‌ای)

### ۲. `monitor.go` — history ماندگار در `history.jsonl`

- هر `appendHistory()` یک خط JSON به `history.jsonl` می‌نویسد (همان fieldهای `HistPoint`: t/cpu/mem/rx/tx)
- **compact با amortization**: فایل فقط وقتی از `histFileMaxLines = 10080×1.2` بگذرد بازنویسی می‌شود — با یک counter تک‌نویسنده (`histLines`). یعنی rewrite هر ~۳۴ ساعت یک‌بار ≈ **چند KB نوشتن دیسک در روز**، نه هر دقیقه
- `loadHistory()` در استارت: نقاط قدیمی‌تر از ۷ روز prune می‌شوند، تا 10080 نگه داشته می‌شوند؛ **فایل خراب/ناقص نمی‌ریزد** (خط به خط parse، خطا = skip)
- `History(d)` و API `/api/history?hours=` بدون تغییر — حالا داده از دیسک restore می‌شود

### ۳. `README.md` — بخش «Data & persistence»

جدول فایل‌ها + سقف‌ها + رفتار restart — شفاف‌سازی مورد ممیزی.

## تغییرات

| فایل | تغییر |
|---|---|
| `agent/events.go` | `eventFileMaxBytes` + `compactLocked` (atomic) + reclaim/`.tmp`-cleanup در `NewEventLog` + compact-on-append |
| `agent/monitor.go` | `histPath`/`histLines` + `loadHistory`/`appendHistoryFileLocked`/`compactHistoryLocked` + constهای سقف |
| `agent/events_test.go` | **جدید** — ۳ تست |
| `agent/history_test.go` | **جدید** — ۴ تست |
| `README.md` | بخش Data & persistence |

## شواهد

### تست‌های خودکار — `go test -race -count=1` → `ok didban-agent 1.81s`

| تست | اثبات |
|---|---|
| `TestEventLogFileStaysBounded` | 5000 event (~1.25MB) → فایل ≤ 1MiB+512، memory ≤ 500، **جدیدترین event روی دیسک** |
| `TestEventLogReclaimsOversizedFileOnLoad` | فایل 1.25MB از نسخهٔ قدیمی → بعد از load: memory=500 و **فایل به 500 خط تقلیل** |
| `TestEventLogRemovesStaleTmp` | `.tmp` مانده از crash → پاک می‌شود |
| `TestHistoryPersistsAcrossRestart` | 2 نقطه → Monitor دوم (restart) → **همان 2 نقطه** در memory و `History(24h)` |
| `TestHistoryFileStaysBounded` | 12146 append → فایل ≤ 12096 خط، memory = 10080، API 7d سالم |
| `TestHistoryLoadPrunesOldPoints` | 2 نقطهٔ ۸روزه + 3 جدید → فقط 3 load می‌شود |
| `TestHistoryLoadToleratesCorruptFile` | فایل garbage → استارت سالم با chart خالی (نه crash) |

### تست زنده روی باینری واقعی

- build → اجرا با `DIDBAN_DATA=$(mktemp -d)` → بعد از ۶ ثانیه: `history.jsonl` (۱ نقطه) + `events.jsonl` + `token` ساخته شدند
- **restart persistence زنده:** بعد از ری‌استارت، history از ۱ → **۲ خط** (نقطهٔ قبلی load + نقطهٔ جدید) — دقیقاً همان رفتار که قبل از H12 وجود نداشت

`gofmt`/`go vet` تمیز.

## تحلیل رگرسیون

| مصرف‌کننده | تأثیر |
|---|---|
| `/api/events` | بدون تغییر (همان 500 event، newest-first) |
| `/api/history?hours=` | بدون تغییر در contract — حالا بعد از restart populated می‌ماند |
| دیسک | events ≤ ~1MiB؛ history ≤ ~100KB + compaction ≈ KBهای روزانه |
| استارت agent | `load()` events + `loadHistory` — هر دو یک‌بار و سریع (فایل‌ها bounded) |
| تست‌های موجود (`NewMonitor(cfg)`) | `histPath` فقط وقتی `DataDir != ""` فعال می‌شود؛ همهٔ تست‌ها `t.TempDir` دارند — سبز |

## صداقت

- compaction events/history **atomically** (temp+rename) پیاده و تست شده، ولی خودِ crash-in-flight (kill وسط rename) به‌صورت آزمون خودکار شبیه‌سازی نشد — منطک از `TestEventLogRemovesStaleTmp` (پاک‌شدن `.tmp` بعد از «crash» فرضی) پوشش داده شده
- رفتار زنده (بیلد/اجرا/restart) روی این sandbox با `DIDBAN_PLAIN` و data-dir موقت تست شد؛ systemd/production host نه

## وضعیت فاز ۲

آیتم‌های ۱ تا ۲۱ تمام. باقی‌مانده از ممیزی (عملکردی): **H16** (matching تونل‌ها با substring `contains`)، **H18** (Narnia `ip_forward`/fallback)، **H19** (IPTables generator بدون validation + غیر-idempotent).
