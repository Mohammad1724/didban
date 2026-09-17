package org.didban.monitor

import android.content.Context
import android.content.SharedPreferences

/**
 * SharedPreferences-backed [HostKeyStore]. The fingerprint is a public trust
 * anchor (like git/ssh known_hosts), not a secret, so plain SharedPreferences
 * is the right home for it. Initialize once from Application.onCreate.
 */
data class TrustedHostKey(
    val host: String,
    val port: Int,
    val fingerprint: String
)

object HostKeyTrustStore : HostKeyStore {

    private const val PREFS_NAME = "didban_hostkeys"
    private lateinit var prefs: SharedPreferences
    var initialized: Boolean = false
        private set

    fun init(context: Context) {
        if (initialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        initialized = true
    }

    private fun requireInit() {
        if (!initialized) {
            throw IllegalStateException("HostKeyTrustStore.init(context) must be called from Application.onCreate")
        }
    }

    override fun getFingerprint(host: String, port: Int): String? {
        requireInit()
        return prefs.getString(hostKeyIdentity(host, port), null)
    }

    override fun storeFingerprint(host: String, port: Int, fingerprint: String) {
        requireInit()
        prefs.edit().putString(hostKeyIdentity(host, port), fingerprint).apply()
    }

    override fun forget(host: String, port: Int) {
        requireInit()
        prefs.edit().remove(hostKeyIdentity(host, port)).apply()
    }

    /** Returns the public trust anchors currently stored on this device. */
    fun entries(): List<TrustedHostKey> {
        requireInit()
        return prefs.all.mapNotNull { (key, value) ->
            if (!key.startsWith("hostkey|") || value !is String) return@mapNotNull null
            val parts = key.split('|')
            val host = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val port = parts.getOrNull(2)?.toIntOrNull() ?: return@mapNotNull null
            TrustedHostKey(host, port, value)
        }.sortedWith(compareBy({ it.host }, { it.port }))
    }

    /** Removes every stored SSH host-key trust anchor after explicit confirmation. */
    fun clearAll() {
        requireInit()
        check(prefs.edit().clear().commit()) { "SSH trust-store purge could not be persisted" }
    }
}
