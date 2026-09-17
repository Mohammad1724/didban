package org.didban.monitor

import java.security.MessageDigest

/**
 * SSH host-key trust (Trust-On-First-Use).
 *
 * The app's own API client already pins the agent's TLS certificate by
 * SHA-256; SSH connections must get the same treatment. JSch cannot enforce
 * known_hosts itself in our flow, so every session verifies the presented
 * host key against a local trust store right after the key exchange:
 *
 *  - First contact: the key is recorded only after explicit user confirmation.
 *    Non-interactive pipelines reject hosts that have not been confirmed.
 *  - Later contacts: the key must match exactly; any change aborts the
 *    connection (possible MITM, or the server was reimaged).
 *
 * The fingerprint is the hex SHA-256 of the raw host key bytes - the same
 * convention (hex SHA-256) the app already uses for agent certificate pinning.
 * A fingerprint is a public trust anchor, not a secret: it is what the user
 * must be able to read out loud on a phone call, so it is stored in plain
 * SharedPreferences (exactly like git/ssh known_hosts).
 */

// ── Pure logic (JVM-testable, no Android imports) ───────────────────────────

object HostKeyFingerprint {

    /** Hex SHA-256 of the raw host key bytes. */
    fun of(rawKey: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(rawKey).joinToString("") { "%02x".format(it) }

    /** Constant-time comparison of a stored fingerprint with a presented key. */
    fun verify(storedFingerprint: String, rawKey: ByteArray): Boolean {
        val expected = storedFingerprint.lowercase().trim()
        val actual = of(rawKey)
        if (expected.length != actual.length) return false
        var diff = 0
        for (i in expected.indices) {
            diff = diff or (expected[i].code xor actual[i].code)
        }
        return diff == 0
    }
}

/** Storage abstraction so the trust logic is unit-testable without Android. */
interface HostKeyStore {
    fun getFingerprint(host: String, port: Int): String?
    fun storeFingerprint(host: String, port: Int, fingerprint: String)
    fun forget(host: String, port: Int)
}

/** Normalizes the (host, port) identity: case-insensitive host. */
internal fun hostKeyIdentity(host: String, port: Int): String =
    "hostkey|${host.trim().lowercase()}|${if (port > 0) port else 22}"

data class HostKeyPrompt(
    val host: String,
    val port: Int,
    val fingerprint: String,
    val keyChanged: Boolean
)

/**
 * Policy applied to a session right after the SSH key exchange.
 * Returning false aborts the connection with a host-key error.
 */
interface HostKeyPolicy {
    suspend fun verify(host: String, port: Int, rawKey: ByteArray): Boolean
}

/**
 * Non-interactive policy: only an already confirmed key is accepted. Unknown
 * hosts fail closed so a background job cannot establish trust while the
 * network is under an active MITM attack.
 */
class StoredHostKeyPolicy(private val store: HostKeyStore) : HostKeyPolicy {
    override suspend fun verify(host: String, port: Int, rawKey: ByteArray): Boolean {
        val stored = store.getFingerprint(host, port) ?: return false
        return HostKeyFingerprint.verify(stored, rawKey)
    }
}

/**
 * Interactive TOFU: first contact suspends until the user confirms a dialog;
 * a changed key suspends until the user explicitly confirms replacement.
 */
class ConfirmingHostKeyPolicy(
    private val store: HostKeyStore,
    private val onPrompt: suspend (prompt: HostKeyPrompt) -> Boolean
) : HostKeyPolicy {
    override suspend fun verify(host: String, port: Int, rawKey: ByteArray): Boolean {
        val fp = HostKeyFingerprint.of(rawKey)
        val stored = store.getFingerprint(host, port)
        return if (stored == null) {
            val approved = onPrompt(HostKeyPrompt(host, port, fp, keyChanged = false))
            if (approved) store.storeFingerprint(host, port, fp)
            approved
        } else {
            if (HostKeyFingerprint.verify(stored, rawKey)) return true
            val approved = onPrompt(HostKeyPrompt(host, port, fp, keyChanged = true))
            if (approved) store.storeFingerprint(host, port, fp)
            approved
        }
    }
}
