package org.didban.monitor

import java.security.MessageDigest
import java.security.cert.X509Certificate

/**
 * SHA-256 fingerprint of a leaf certificate — the value the UI offers for
 * TOFU pinning and the value stored in `ServerConfig.fingerprint`.
 *
 * Format: lowercase hex without separators (e.g. `aa9c…`) — the exact form
 * the pin-capture flow has always stored, so existing pinned servers keep
 * working byte-for-byte. [normalizeFingerprint] is the companion for the
 * free-text the user types into the fingerprint field (it accepts the
 * uppercase colon-separated form as well).
 *
 * Pure logic — unit-tested on the JVM ([CertFingerprintTest]).
 */
object CertFingerprint {

    /** Lowercase hex without separators — the stored/captured form. */
    fun normalized(cert: X509Certificate): String {
        val sha = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        return sha.joinToString("") { String.format("%02x", it) }
    }

    /**
     * Normalize a fingerprint as entered/stored by the user for comparison:
     * trim, drop separators, lowercase. Empty input stays empty (= "no pin").
     */
    fun normalizeFingerprint(input: String): String =
        input.trim().lowercase().replace(":", "").replace(" ", "")
}
