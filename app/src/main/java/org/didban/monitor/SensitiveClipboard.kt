package org.didban.monitor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import java.security.MessageDigest

/**
 * Copies credentials/configuration as sensitive clipboard data and removes
 * only that exact value after a short lifetime. The delayed task retains a
 * SHA-256 digest, never the secret itself, and will not erase newer user data.
 */
object SensitiveClipboard {
    private const val SENSITIVE_KEY = "android.content.extra.IS_SENSITIVE"
    private const val CLEAR_AFTER_MS = 60_000L
    private val handler = Handler(Looper.getMainLooper())

    fun copy(context: Context, label: String, value: String) {
        require(value.isNotEmpty()) { "Sensitive clipboard value is empty" }
        val appContext = context.applicationContext
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label.take(80), value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            clip.description.extras = PersistableBundle().apply { putBoolean(SENSITIVE_KEY, true) }
        }
        clipboard.setPrimaryClip(clip)

        val expectedDigest = digest(value)
        handler.postDelayed({
            runCatching {
                val current = clipboard.primaryClip
                    ?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)
                    ?.coerceToText(appContext)
                    ?.toString()
                if (current != null && MessageDigest.isEqual(expectedDigest, digest(current))) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) clipboard.clearPrimaryClip()
                    else clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            }
        }, CLEAR_AFTER_MS)
    }

    private fun digest(value: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
}
