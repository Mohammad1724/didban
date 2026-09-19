package org.didban.monitor

internal enum class FleetFilter { ALL, OFFLINE, ATTENTION, UNKNOWN }
internal enum class FleetHealth { OFFLINE, ATTENTION, UNKNOWN, HEALTHY }

internal fun fleetHealth(server: ServerConfig, state: Repo.State?, now: Long): FleetHealth = when {
    state?.error != null -> FleetHealth.OFFLINE
    state?.metrics == null || state.updated <= 0L -> FleetHealth.UNKNOWN
    now - state.updated > 120_000L -> FleetHealth.ATTENTION
    state.metrics.cpuUsage >= server.cpuAlert || state.metrics.memPct >= server.memAlert -> FleetHealth.ATTENTION
    else -> FleetHealth.HEALTHY
}

internal fun visibleFleet(
    servers: List<ServerConfig>, states: Map<Long, Repo.State>, query: String, filter: FleetFilter, now: Long
): List<ServerConfig> = servers.filter { server ->
    val health = fleetHealth(server, states[server.id], now)
    val matchesFilter = when (filter) {
        FleetFilter.UNKNOWN -> health == FleetHealth.UNKNOWN
        FleetFilter.ALL -> true
        FleetFilter.OFFLINE -> health == FleetHealth.OFFLINE
        FleetFilter.ATTENTION -> health == FleetHealth.OFFLINE || health == FleetHealth.ATTENTION
    }
    matchesFilter && (server.name.contains(query.trim(), ignoreCase = true) || server.host.contains(query.trim(), ignoreCase = true))
}.sortedWith(compareBy<ServerConfig> { fleetHealth(it, states[it.id], now).ordinal }.thenBy { it.name.lowercase() }.thenBy { it.id })

/** Optimistic concurrency checks keep a stale editor from overwriting imports/other edits. */
internal object ServerRecordEdits {
    fun save(current: List<ServerConfig>, original: ServerConfig?, replacement: ServerConfig): List<ServerConfig> {
        if (original == null) {
            check(current.none { it.id == replacement.id }) { "Server ID already exists" }
            return current + replacement
        }
        check(original.id == replacement.id && current.firstOrNull { it.id == original.id } == original) { "Server record changed" }
        return current.map { if (it.id == original.id) replacement else it }
    }

    fun delete(current: List<ServerConfig>, expected: ServerConfig): List<ServerConfig> {
        check(current.firstOrNull { it.id == expected.id } == expected) { "Server record changed" }
        return current.filterNot { it.id == expected.id }
    }
}

/** No SavedStateHandle/Bundle: the draft contains credentials and must stay in RAM. */
internal data class ServerConnectionDraft(
    val id: Long,
    val name: String = "",
    val host: String = "",
    val port: String = "8686",
    val token: String = "",
    val adminToken: String = "",
    val fingerprint: String = "",
    val cpuAlert: String = "90",
    val memAlert: String = "90",
    val quickConnect: String = ""
) {
    fun server(): ServerConfig = ServerConfig(id, name.trim().ifBlank { host.trim() }, host.trim(),
        port.toIntOrNull() ?: 0, token.trim(), adminToken.trim(), true, fingerprint.trim(),
        cpuAlert.toIntOrNull() ?: 0, memAlert.toIntOrNull() ?: 0)

    // Never let data-class diagnostics accidentally print credentials.
    override fun toString(): String = "ServerConnectionDraft(id=$id, credentials=[REDACTED])"

    companion object {
        fun from(server: ServerConfig) = ServerConnectionDraft(server.id, server.name, server.host,
            server.port.toString(), server.token, server.adminToken, server.fingerprint,
            server.cpuAlert.toString(), server.memAlert.toString())
    }
}

internal enum class ConnectionValidation { NAME, HOST, PORT, READ_TOKEN, ADMIN_TOKEN, SAME_TOKENS, TLS, FINGERPRINT, THRESHOLD }

internal fun connectionValidation(server: ServerConfig): ConnectionValidation? = when {
    server.name.isBlank() -> ConnectionValidation.NAME
    !TunnelFieldValidation.isHost(server.host) -> ConnectionValidation.HOST
    server.port !in 1..65535 -> ConnectionValidation.PORT
    !AgentTokenPolicy.isValid(server.token) -> ConnectionValidation.READ_TOKEN
    !AgentTokenPolicy.isValid(server.adminToken) -> ConnectionValidation.ADMIN_TOKEN
    server.token == server.adminToken -> ConnectionValidation.SAME_TOKENS
    !server.useTls -> ConnectionValidation.TLS
    !CertFingerprint.isValidSha256(server.fingerprint) -> ConnectionValidation.FINGERPRINT
    server.cpuAlert !in 1..100 || server.memAlert !in 1..100 -> ConnectionValidation.THRESHOLD
    else -> null
}
