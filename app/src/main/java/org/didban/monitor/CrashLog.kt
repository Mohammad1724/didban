package org.didban.monitor

import android.content.Context

/**
 * Android glue for the H8 crash record + diagnostic trace.
 *
 *  - The record (timestamp + consecutive count, [CrashPolicy]) is written
 *    with a SYNCHRONOUS commit: the process may die milliseconds later, so
 *    `apply()` is not safe here.
 *  - The diagnostic trace is stored ENCRYPTED at rest (SecureStorage =
 *    AndroidKeyStore AES-256-GCM, non-exportable key). A crash trace can
 *    carry sensitive data (paths, in-flight values), so plaintext at rest
 *    is never acceptable. If encryption itself fails, only a placeholder
 *    is stored — never the plaintext trace.
 *  - Pre-upgrade prefs may still hold a plaintext trace; [loadTrace] falls
 *    back to it (it is the user's own data on the user's own device) and
 *    the next reset replaces it with an encrypted record.
 */
object CrashLog {

    private const val PREFS = "didban"
    private const val KEY_TRACE = "last_crash_trace"
    private const val KEY_COUNT = "crash_count"
    private const val KEY_LAST_AT = "crash_last_at"
    private const val ENCRYPTION_FAILED = "trace unavailable (encryption failed)"

    /** Read the persisted crash record (null = no recorded crash). */
    fun readRecord(ctx: Context): CrashPolicy.CrashRecord? {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastAt = sp.getLong(KEY_LAST_AT, 0L)
        if (lastAt == 0L) return null
        return CrashPolicy.CrashRecord(lastAt, sp.getInt(KEY_COUNT, 1))
    }

    /**
     * Persist the crash: loop record + encrypted trace. Synchronous
     * commits throughout; every step is individually guarded so a failure
     * in one never suppresses the rest.
     */
    fun saveCrash(ctx: Context, throwable: Throwable) {
        val now = System.currentTimeMillis()
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        try {
            val record = CrashPolicy.recordCrash(now, readRecord(ctx))
            sp.edit()
                .putLong(KEY_LAST_AT, record.lastCrashAt)
                .putInt(KEY_COUNT, record.consecutiveCount)
                .commit()
        } catch (_: Throwable) {
        }
        try {
            val trace = SecretRedactor.redact(throwable.stackTraceToString())
                .take(CrashPolicy.MAX_TRACE_CHARS)
            val payload = try {
                SecureStorage.encrypt(trace)
            } catch (_: Throwable) {
                ENCRYPTION_FAILED
            }
            sp.edit().putString(KEY_TRACE, payload).commit()
        } catch (_: Throwable) {
        }
    }

    /**
     * The diagnostic trace for the crash screen. Decrypts encrypted
     * payloads; tolerates a legacy plaintext value from pre-upgrade
     * installs. Returns null when there is no trace.
     */
    fun loadTrace(ctx: Context): String? {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TRACE, null) ?: return null
        if (raw == ENCRYPTION_FAILED) return raw
        val trace = try {
            SecureStorage.decrypt(raw)
        } catch (_: Throwable) {
            raw // legacy plaintext from before the encryption fix
        }
        // Redact again on read so legacy traces and future pattern additions
        // are protected before display or clipboard export.
        return SecretRedactor.redact(trace).take(CrashPolicy.MAX_TRACE_CHARS)
    }

    /**
     * The startup survived (activity reached onStart): clear the
     * consecutive-crash counter. The trace itself is kept — the diagnostic
     * screen is the app's deliberate "show me the last crash" surface.
     */
    fun markStartupOk(ctx: Context) {
        try {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_COUNT)
                .remove(KEY_LAST_AT)
                .commit()
        } catch (_: Throwable) {
        }
    }

    /** Full reset (crash screen "Reset & Launch"). */
    fun clear(ctx: Context) {
        try {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_TRACE)
                .remove(KEY_COUNT)
                .remove(KEY_LAST_AT)
                .remove("last_crash_msg") // legacy plaintext field from pre-H8 builds
                .commit()
        } catch (_: Throwable) {
        }
    }
}
