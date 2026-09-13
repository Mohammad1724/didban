package org.didban.monitor

/**
 * Pure validation for tunnel fields that reach a shell context or a systemd
 * ExecStart line (H4). Config-file CONTENT no longer needs this: it is
 * written via base64 and never interpreted. What DOES need it:
 *   - values passed as shell arguments (iptables --to-destination ...),
 *   - values embedded in systemd ExecStart lines (systemd argument parsing),
 *   - host/port values interpolated into generated scripts.
 *
 * No Android dependencies - JVM-testable.
 */
object TunnelFieldValidation {

    // DNS hostname: labels of alnum/hyphen, no leading/trailing hyphen,
    // or a plain IPv4 address. No whitespace, quotes, separators, shell
    // metacharacters, no newlines (multi-line values are the heredoc
    // early-close / script-termination attack).
    private val LABEL = "[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
    private val HOSTNAME_RE = Regex("^$LABEL(?:\\.$LABEL){0,127}$")
    private val IPV4_RE = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")

    // Strict IPv4 (for values used in kernel-level targets, e.g. Narnia's
    // virtual IPs in iptables DNAT rules).
    fun isIpv4(value: String): Boolean {
        val m = IPV4_RE.matchEntire(value) ?: return false
        return m.groupValues.drop(1).all { it.toInt() in 0..255 }
    }

    /** DNS hostname or IPv4 - nothing else. */
    fun isHost(value: String): Boolean =
        value.length in 1..253 && (isIpv4(value) || HOSTNAME_RE.matches(value))

    fun isValidPort(port: Int): Boolean = port in 1..65535

    /**
     * Token format for values that land in CLI arguments (systemd
     * ExecStart / --auth / -k): alnum plus _ and -, no spaces/quotes.
     */
    fun isValidToken(value: String): Boolean =
        value.length in 1..128 && value.all { it in "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-" }

    /** Returns an error message, or null when [value] is a safe host. */
    fun checkHost(value: String, label: String): String? =
        if (isHost(value)) null else "$label نامعتبر است (hostname یا IPv4؛ بدون فاصله/کاراکترพิเศษ): $value"

    /** Returns an error message, or null when [value] is a strict IPv4. */
    fun checkIpv4(value: String, label: String): String? =
        if (isIpv4(value)) null else "$label باید IPv4 معتبر باشد: $value"

    /** Returns an error message, or null when [value] is a safe token. */
    fun checkToken(value: String, label: String): String? =
        if (isValidToken(value)) null else "$label باید ۱ تا ۱۲۸ کاراکتر از [A-Za-z0-9_-] باشد: $value"

    /**
     * H16: strict match between a tunnel's host field and a registered
     * server's host.
     *
     * autoDeploy previously fell back to `tunnelField.contains(serverHost)`
     * (substring), so a tunnel field "sub.example.com" matched a registered
     * server "example.com" and "10.0.0.100" matched "10.0.0.1" — the agent
     * then wrote configs and ran install scripts on the WRONG machine.
     *
     * Rules:
     *  - both sides are trimmed and lowercased (hostnames and IPv6 are
     *    case-insensitive);
     *  - a blank server host never matches ("" is a substring of everything);
     *  - an exact host match wins;
     *  - the tunnel field may carry an explicit port ("host:443" or
     *    "[2001:db8::1]:443"); in that case the host part is compared
     *    against the server host. A trailing colon+number that is not a port
     *    (non-digits) is never treated as one, so no false matches arise
     *    from raw IPv6 addresses.
     */
    fun hostMatchesServer(tunnelField: String, serverHost: String): Boolean {
        val s = serverHost.trim().lowercase()
        if (s.isEmpty()) return false
        val t = tunnelField.trim().lowercase()
        if (t.isEmpty()) return false
        if (t == s) return true
        val colon = t.lastIndexOf(':')
        if (colon > 0) {
            val portPart = t.substring(colon + 1)
            if (portPart.isNotEmpty() && portPart.all { it.isDigit() }) {
                var hostPart = t.substring(0, colon)
                if (hostPart.length >= 2 && hostPart.startsWith('[') && hostPart.endsWith(']')) {
                    hostPart = hostPart.substring(1, hostPart.length - 1)
                }
                if (hostPart.isNotEmpty() && hostPart == s) return true
            }
        }
        return false
    }
}
