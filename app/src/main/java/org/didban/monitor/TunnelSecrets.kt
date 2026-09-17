package org.didban.monitor

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Pure secret management for tunnels (H3). No Android dependencies — JVM-testable.
 *
 * The rule that fixes the "redeploy breaks the pair" bug: a tunnel's secret
 * is generated ONCE (when blank), written back into the persisted model, and
 * reused by every later code generation / redeploy. Cores that need two
 * secrets (SpoofTunnel) derive a deterministic pair from the single stored
 * secret, so regeneration always reproduces the same pair.
 */
object TunnelSecrets {

    /** Cryptographically random alphanumeric token (shell/config safe charset). */
    fun generateRandomToken(length: Int = 16): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val rnd = SecureRandom()
        val sb = StringBuilder()
        for (i in 0 until length) {
            sb.append(chars[rnd.nextInt(chars.length)])
        }
        return sb.toString()
    }

    /**
     * Deterministic 32-hex-char key expansion: SHA-256(seed|domain) truncated.
     * Same seed+domain always yields the same key.
     */
    fun deriveKey(seed: String, domain: String): String {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest("$seed|$domain".toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }.take(32)
    }

    /**
     * Returns a stable secret: the stored [current] one if non-blank;
     * otherwise generates one and reports it via [onGenerated] so the caller
     * can persist it into the model.
     */
    fun ensureToken(current: String, onGenerated: (String) -> Unit): String {
        if (current.isBlank()) {
            val generated = generateRandomToken(24)
            onGenerated(generated)
            return generated
        }
        return current
    }


}
