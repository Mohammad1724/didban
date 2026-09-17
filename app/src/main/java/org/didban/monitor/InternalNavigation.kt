package org.didban.monitor

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Process-scoped capability for notification deep links.
 *
 * MainActivity must be exported as the launcher activity, so an unrelated app
 * can start it and attach arbitrary extras. Navigation extras are honored only
 * when they came from an immutable PendingIntent created by this process.
 */
object InternalNavigation {
    const val ACTION_OPEN_SERVER = "org.didban.monitor.action.OPEN_SERVER"
    const val EXTRA_SERVER_ID = "org.didban.monitor.extra.SERVER_ID"
    private const val EXTRA_CAPABILITY = "org.didban.monitor.extra.NAV_CAPABILITY"

    private val capability: String = ByteArray(32)
        .also { SecureRandom().nextBytes(it) }
        .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    fun openServerIntent(context: Context, serverId: Long): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_SERVER
            setPackage(context.packageName)
            // PendingIntent identity ignores extras. A unique data URI keeps
            // alerts for different 64-bit server IDs from overwriting each other.
            data = Uri.parse("didban-internal://server/$serverId")
            putExtra(EXTRA_SERVER_ID, serverId)
            putExtra(EXTRA_CAPABILITY, capability)
        }

    fun trustedServerId(intent: Intent?): Long? {
        if (intent?.action != ACTION_OPEN_SERVER) return null
        val supplied = intent.getStringExtra(EXTRA_CAPABILITY) ?: return null
        if (!constantTimeEquals(capability, supplied)) return null
        return intent.getLongExtra(EXTRA_SERVER_ID, -1L).takeIf { it > 0 }
    }

    internal fun constantTimeEquals(expected: String, supplied: String): Boolean =
        MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            supplied.toByteArray(Charsets.UTF_8)
        )
}
