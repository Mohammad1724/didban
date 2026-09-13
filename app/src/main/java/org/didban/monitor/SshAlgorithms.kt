package org.didban.monitor

/**
 * The single SSH algorithm whitelist for every JSch session in the app
 * (H10) — SshEngine, SshSetup and SftpEngine must all negotiate from the
 * same modern set.
 *
 * Removed compared to the old per-engine lists:
 *  - ciphers: arcfour/arcfour128/arcfour256 (broken stream cipher),
 *    3des-cbc, blowfish-cbc, cast128-cbc, aes*-cbc (CBC mode -> SSH
 *    padding-oracle; aes128-cbc/3des-cbc are disabled by default in
 *    modern OpenSSH anyway)
 *  - kex: diffie-hellman-group1-sha1 (1024-bit, SHA-1),
 *    group14-sha1 (SHA-1), group-exchange-sha1 (SHA-1)
 *  - host keys: ssh-rsa (SHA-1 signatures, disabled by default in
 *    OpenSSH >= 8.8), ssh-dss (1024-bit DSA, disabled since 7.0)
 *
 * What remains covers any OpenSSH server from the last ~8 years:
 * curve25519/ecdh kex, rsa-sha2/ecdsa/ed25519 host keys, chacha20-poly1305
 * and aes-*-ctr/gcm ciphers.
 *
 * Order = preference (first match wins).
 *
 * Pure logic — unit-tested on the JVM ([SshAlgorithmsTest]).
 */
object SshAlgorithms {

    val KEX: List<String> = listOf(
        "curve25519-sha256",
        "curve25519-sha256@libssh.org",
        "ecdh-sha2-nistp256",
        "ecdh-sha2-nistp384",
        "ecdh-sha2-nistp521",
        "diffie-hellman-group-exchange-sha256",
        "diffie-hellman-group16-sha512",
        "diffie-hellman-group18-sha512",
        "diffie-hellman-group14-sha256"
    )

    val HOST_KEY: List<String> = listOf(
        "ssh-ed25519",
        "ecdsa-sha2-nistp256",
        "ecdsa-sha2-nistp384",
        "ecdsa-sha2-nistp521",
        "rsa-sha2-512",
        "rsa-sha2-256"
    )

    val CIPHER: List<String> = listOf(
        "chacha20-poly1305@openssh.com",
        "aes128-ctr",
        "aes192-ctr",
        "aes256-ctr",
        "aes128-gcm@openssh.com",
        "aes256-gcm@openssh.com"
    )

    /** JSch `kex` config value. */
    fun kexConfig(): String = KEX.joinToString(",")

    /** JSch `server_host_key` / `PubkeyAcceptedAlgorithms` config value. */
    fun hostKeyConfig(): String = HOST_KEY.joinToString(",")

    /** JSch `cipher.s2c` / `cipher.c2s` (and `CheckCiphers`) config value. */
    fun cipherConfig(): String = CIPHER.joinToString(",")

    /**
     * Algorithms that must never appear in any whitelist (known-broken,
     * broken-by-design, or deprecated by the IETF/OpenSSH consensus).
     * Enforced by [assertNoWeakAlgorithms] — called from the JVM tests so a
     * future edit that reintroduces a weak name fails the build.
     */
    val FORBIDDEN: Set<String> = setOf(
        // stream ciphers (arcfour family is cryptographically broken)
        "arcfour", "arcfour128", "arcfour256",
        // CBC-mode ciphers (SSH padding oracle, RFC 4253 mode)
        "3des-cbc", "blowfish-cbc", "cast128-cbc",
        "aes128-cbc", "aes192-cbc", "aes256-cbc",
        // SHA-1 or 1024-bit key exchange groups
        "diffie-hellman-group1-sha1",
        "diffie-hellman-group14-sha1",
        "diffie-hellman-group-exchange-sha1",
        "diffie-hellman-group1-sha256", // still 1024-bit even with SHA-256
        // weak host key algorithms
        "ssh-dss",
        "ssh-rsa"
    )

    /** Throws if any whitelist contains a forbidden (weak) algorithm. */
    fun assertNoWeakAlgorithms() {
        (KEX + HOST_KEY + CIPHER).forEach { algo ->
            require(algo !in FORBIDDEN) { "weak SSH algorithm in whitelist: $algo" }
        }
    }
}
