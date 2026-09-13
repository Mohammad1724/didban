package org.didban.monitor

import android.content.Context
import android.content.SharedPreferences

/**
 * SharedPreferences-backed [HostKeyStore]. The fingerprint is a public trust
 * anchor (like git/ssh known_hosts), not a secret, so plain SharedPreferences
 * is the right home for it. Initialize once from Application.onCreate.
 */
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
}
