package org.didban.monitor

/** A destination includes its server scope: Back must restore both together. */
internal data class CommandDestination(val route: CommandRoute, val serverId: Long? = null) {
    fun encode(): String = "${route.key}|${serverId ?: ""}"

    companion object {
        fun decode(value: String): CommandDestination? {
            val parts = value.split('|')
            if (parts.size != 2) return null
            val route = CommandRoute.values().firstOrNull { it.key == parts[0] } ?: return null
            val id = if (parts[1].isEmpty()) null else parts[1].toLongOrNull() ?: return null
            return CommandDestination(route, id)
        }
    }
}

/**
 * Real visit history, not a guessed workspace parent. Revisiting an existing
 * destination pops to it, so Save/Done and repeated menu taps cannot form loops.
 * Only non-secret route keys and server IDs enter Android saved instance state.
 */
internal data class CommandNavigation private constructor(val entries: List<CommandDestination>) {
    val current: CommandDestination get() = entries.last()
    val previous: CommandDestination? get() = entries.getOrNull(entries.lastIndex - 1)

    fun navigate(route: CommandRoute, serverId: Long? = current.serverId): CommandNavigation {
        val next = CommandDestination(route, when (route) {
            CommandRoute.OVERVIEW, CommandRoute.FLEET, CommandRoute.INCIDENTS -> null
            else -> serverId
        })
        val existing = entries.indexOfLast { it == next }
        if (existing >= 0) return CommandNavigation(entries.take(existing + 1))
        // Bound the saved state while keeping a stable overview root.
        val retained = if (entries.size >= 64) listOf(entries.first()) + entries.takeLast(62) else entries
        return CommandNavigation(retained + next)
    }

    fun back(): CommandNavigation = if (entries.size > 1) CommandNavigation(entries.dropLast(1)) else this

    fun clearScope(): CommandNavigation = if (current.serverId == null) this else back().navigate(current.route, null)

    fun save(): List<String> = entries.map { it.encode() }

    companion object {
        fun root() = CommandNavigation(listOf(CommandDestination(CommandRoute.OVERVIEW)))
        fun restore(saved: List<String>): CommandNavigation = saved.mapNotNull(CommandDestination::decode)
            .fold(root()) { navigation, destination -> navigation.navigate(destination.route, destination.serverId) }
    }
}

/** The shell refresh is for metrics, not an unrelated operation on tool pages. */
internal fun CommandRoute.hasMetricsRefresh(): Boolean = this in setOf(
    CommandRoute.OVERVIEW, CommandRoute.FLEET, CommandRoute.INCIDENTS, CommandRoute.SERVER_DOSSIER
)

internal enum class CommandBackAction { CLOSE_HELP, CLOSE_NAVIGATION, POP, EXIT }

internal fun CommandNavigation.backAction(navigationOpen: Boolean, helpVisible: Boolean): CommandBackAction = when {
    helpVisible -> CommandBackAction.CLOSE_HELP
    navigationOpen -> CommandBackAction.CLOSE_NAVIGATION
    previous != null -> CommandBackAction.POP
    else -> CommandBackAction.EXIT
}
