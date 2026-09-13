package org.didban.monitor

/**
 * Pure, Android-free input validation for the Security screen SSH flows.
 *
 * The SSH exec channel always runs its command string through the remote
 * user's shell, so dynamic values can never be passed "as argv" — the only
 * sound defense is (1) strict allowlist validation before execution and
 * (2) shell-quoting every dynamic argument so shell metacharacters can never
 * be interpreted, even if a new caller forgets to validate.
 *
 * These functions are dependency-free on purpose: they run in plain JVM unit
 * tests (no Android framework required).
 */
object SecurityValidation {

    /** Ports usable in firewall rules (1-65535). */
    private val PORT_PATTERN = Regex("\\d+")

    /** Fail2ban jail names: conservative alphanumerics, dash, underscore. */
    private val JAIL_PATTERN = Regex("[A-Za-z0-9_-]{1,64}")

    /**
     * Validates a user-supplied TCP port string.
     *
     * Returns the port number when the input is exactly a decimal integer in
     * 1..65535 (surrounding whitespace is tolerated), otherwise null.
     * Anything containing letters, signs, separators or shell metacharacters
     * is rejected.
     */
    fun validatePort(input: String): Int? {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || !PORT_PATTERN.matches(trimmed)) return null
        val value = trimmed.toIntOrNull() ?: return null
        return if (value in 1..65535) value else null
    }

    /**
     * Validates a fail2ban jail name. Only safe unit/identifier characters are
     * allowed, so the value can be embedded in an SSH command after quoting.
     */
    fun validateJail(name: String): Boolean = JAIL_PATTERN.matches(name)

    /**
     * Strict IPv4 validation: four dot-separated decimal octets in 0..255,
     * no leading zeros, no signs, no empty or extra segments.
     */
    fun isValidIpv4(ip: String): Boolean {
        val parts = ip.split(".")
        if (parts.size != 4) return false
        return parts.all { part ->
            if (part.isEmpty() || part.length > 3) return@all false
            if (part.any { it !in '0'..'9' }) return@all false
            if (part.length > 1 && part.startsWith("0")) return@all false
            part.toInt() <= 255
        }
    }

    /**
     * Shell-quotes a single argument for safe inclusion in a POSIX sh command
     * string. Wraps the value in single quotes and escapes embedded single
     * quotes as '\''. The result cannot alter command structure, expand
     * variables, or execute anything.
     *
     * Example: shellQuote("443/tcp") == "'443/tcp'"
     *          shellQuote("a'b")      == "'a'\\''b'"
     */
    fun shellQuote(arg: String): String = "'" + arg.replace("'", "'\\''") + "'"
}
