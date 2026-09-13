package org.didban.monitor

import kotlin.text.Regex

/**
 * Parsers for raw command outputs (fail2ban-client, systemctl). Kept pure so
 * they are unit-testable on the JVM with realistic sample output, and every
 * token is allowlist-validated before it may be embedded in a command
 * (defense in depth on top of shell-quoting, item 2 / C6).
 */
object OutputParsers {

    /** Jail names from `fail2ban-client status` (the "Jail list:" line). */
    fun fail2banJails(output: String): List<String> =
        output.lineSequence()
            .firstOrNull { it.contains("Jail list") }
            ?.substringAfter("Jail list:")
            ?.split(Regex("\\s+"))
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() && SecurityValidation.validateJail(it) }
            ?.take(10)
            ?: emptyList()

    /** Banned IPs from `fail2ban-client status <jail>` (the "Banned IP list:" line). */
    fun fail2banBannedIps(output: String): List<String> =
        output.lineSequence()
            .firstOrNull { it.contains("Banned IP list") }
            ?.substringAfter("Banned IP list:")
            ?.split(Regex("\\s+"))
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() && SecurityValidation.isValidIpv4(it) }
            ?: emptyList()

    /** One line of `systemctl list-units --output=plain` (5+ columns). */
    data class SystemdUnitLine(
        val unit: String,
        val description: String,
        val active: String, // "active", "failed", ...
        val sub: String     // "running", "dead", "failed", ...
    )

    /** Running/failed service units from `systemctl list-units` plain output. */
    fun systemdUnits(output: String): List<SystemdUnitLine> =
        output.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val p = line.trim().split(Regex("\\s+"), limit = 5)
                if (p.size < 4) return@mapNotNull null
                SystemdUnitLine(p[0], if (p.size >= 5) p[4] else "", p[2], p[3])
            }
            .take(40)
            .toList()
}
