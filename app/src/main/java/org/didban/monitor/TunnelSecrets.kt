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

    /**
     * Best-effort recovery of the REAL credential from a live process command
     * line (auto-discovery, M17).
     *
     * Only a few cores put their token on the command line at all — Chisel
     * does, on both sides: `chisel server ... --auth user:token` /
     * `chisel client --auth user:token ...`. The agent truncates cmdlines to
     * 100 chars, so extraction works when the flag fits; otherwise it simply
     * returns null.
     *
     * Every other core returns null ON PURPOSE: their token lives in a config
     * file the scanner cannot read, and the old code papered over that with
     * the fake "auto-detected" placeholder, which ensureToken then treated as
     * a real secret. A null result keeps the tunnel "token unknown" — the
     * deploy gate blocks it until the user enters the actual token.
     */
    /**
     * Item 29: recover the real credential of a container-deployed tunnel
     * from its env (the agent exposes ONLY allowlisted keys, so this map is
     * already secret-safe). Narnia ships its key as PASSWORD — the same env
     * contract as upstream Narnia.sh. Every other core returns null: their
     * docker images take config files, not env tokens.
     */
    fun tokenFromEnv(core: TunnelCore, env: Map<String, String>): String? {
        if (core != TunnelCore.NARNIA) return null
        val token = env["PASSWORD"] ?: return null
        return if (token.isNotBlank() && token.length <= 256) token else null
    }

    fun extractTokenFromCmd(core: TunnelCore, cmd: String): String? {
        if (core != TunnelCore.CHISEL) return null
        val idx = cmd.indexOf("--auth")
        if (idx < 0) return null
        val rest = cmd.substring(idx + "--auth".length).trimStart()
        val fields = rest.split(Regex("\\s+"))
        val value = fields.firstOrNull()?.trim('"', '\'') ?: return null
        // The agent appends "…" when a cmdline exceeded its 100-char cap —
        // if the auth value is the LAST token, its tail was cut and the
        // "token" is a prefix, not the secret: refuse it.
        if (fields.size <= 1 && rest.trimEnd().endsWith("…")) return null
        // chisel auth format is user:token — chisel itself splits at the
        // FIRST colon, so the password (token) is everything after it.
        val token = if (value.contains(":")) value.substringAfter(":") else value
        return if (token.isNotBlank() && token.length <= 256) token else null
    }
}
