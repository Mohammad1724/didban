package org.didban.monitor

/** Subpanels of one server workspace, never separate navigation-menu pages. */
internal enum class ServerPane { LIST, DETAILS, ADD, EDIT;
    val editing: Boolean get() = this == ADD || this == EDIT
}

internal data class CommandDestination(
    val route: CommandRoute,
    val serverId: Long? = null,
    val serverPane: ServerPane = ServerPane.LIST
) {
    fun encode(): String = "${route.key}|${serverId ?: ""}|${serverPane.name}"

    fun canonical(): CommandDestination = when (route) {
        CommandRoute.OVERVIEW, CommandRoute.INCIDENTS -> CommandDestination(CommandRoute.FLEET)
        CommandRoute.SERVER_DOSSIER -> CommandDestination(CommandRoute.FLEET, serverId,
            if (serverId == null) ServerPane.LIST else ServerPane.DETAILS)
        CommandRoute.MANAGE_SERVERS -> CommandDestination(CommandRoute.FLEET, serverId,
            if (serverId == null) ServerPane.ADD else ServerPane.EDIT)
        CommandRoute.FLEET -> when (serverPane) {
            ServerPane.LIST, ServerPane.ADD -> copy(serverId = null)
            ServerPane.DETAILS, ServerPane.EDIT -> if (serverId == null) CommandDestination(CommandRoute.FLEET) else this
        }
        else -> copy(serverPane = ServerPane.LIST)
    }

    companion object {
        fun decode(value: String): CommandDestination? {
            val parts = value.split('|')
            if (parts.size !in 2..3) return null
            val route = CommandRoute.values().firstOrNull { it.key == parts[0] } ?: return null
            val id = if (parts[1].isEmpty()) null else parts[1].toLongOrNull() ?: return null
            val pane = parts.getOrNull(2)?.let { key -> ServerPane.values().firstOrNull { it.name == key } }
                ?: ServerPane.LIST
            return CommandDestination(route, id, pane).canonical()
        }
    }
}

/** Saved history contains route keys, panel types and IDs only, never credentials. */
internal data class CommandNavigation private constructor(val entries: List<CommandDestination>) {
    val current: CommandDestination get() = entries.last()
    val previous: CommandDestination? get() = entries.getOrNull(entries.lastIndex - 1)

    private fun visit(destination: CommandDestination): CommandNavigation {
        val next = destination.canonical()
        val existing = entries.indexOfLast { it == next }
        if (existing >= 0) return CommandNavigation(entries.take(existing + 1))
        val retained = if (entries.size >= 64) listOf(entries.first()) + entries.takeLast(62) else entries
        return CommandNavigation(retained + next)
    }

    fun navigate(route: CommandRoute, serverId: Long? = current.serverId): CommandNavigation = when (route) {
        CommandRoute.OVERVIEW, CommandRoute.INCIDENTS, CommandRoute.FLEET -> visit(CommandDestination(CommandRoute.FLEET))
        CommandRoute.SERVER_DOSSIER -> if (serverId == null) navigate(CommandRoute.FLEET) else openServer(serverId)
        CommandRoute.MANAGE_SERVERS -> editServer(serverId)
        else -> visit(CommandDestination(route, serverId))
    }

    // Choosing a card changes one inspector; it must not build a stack of inspectors.
    fun openServer(id: Long): CommandNavigation = navigate(CommandRoute.FLEET)
        .visit(CommandDestination(CommandRoute.FLEET, id, ServerPane.DETAILS))

    fun editServer(id: Long?): CommandNavigation {
        val base = when {
            id != null && (current.serverId != id || current.serverPane != ServerPane.DETAILS) -> openServer(id)
            current.route != CommandRoute.FLEET -> navigate(CommandRoute.FLEET)
            else -> this
        }
        return base.visit(CommandDestination(CommandRoute.FLEET, id, if (id == null) ServerPane.ADD else ServerPane.EDIT))
    }

    fun back(): CommandNavigation = if (entries.size > 1) CommandNavigation(entries.dropLast(1)) else this

    fun closePane(): CommandNavigation {
        if (current.serverPane == ServerPane.DETAILS) return navigate(CommandRoute.FLEET)
        val prior = back()
        return if (prior.current.route == CommandRoute.FLEET && !prior.current.serverPane.editing) prior
            else prior.navigate(CommandRoute.FLEET, null)
    }

    fun finishEditing(id: Long): CommandNavigation = openServer(id)
    fun clearScope(): CommandNavigation = if (current.route == CommandRoute.FLEET) navigate(CommandRoute.FLEET, null)
        else if (current.serverId == null) this else back().navigate(current.route, null)

    fun removeServer(id: Long): CommandNavigation = restore(entries.filterNot { it.serverId == id }.map { it.encode() })
    fun save(): List<String> = entries.map { it.encode() }

    companion object {
        fun root() = CommandNavigation(listOf(CommandDestination(CommandRoute.FLEET)))
        fun restore(saved: List<String>): CommandNavigation = saved.mapNotNull(CommandDestination::decode)
            .fold(root()) { navigation, destination ->
                when (destination.serverPane) {
                    ServerPane.DETAILS -> navigation.openServer(destination.serverId!!)
                    ServerPane.EDIT -> navigation.editServer(destination.serverId)
                    ServerPane.ADD -> navigation.editServer(null)
                    ServerPane.LIST -> navigation.navigate(destination.route, destination.serverId)
                }
            }
    }
}

internal enum class CommandBackAction { CLOSE_HELP, CLOSE_NAVIGATION, CLOSE_PANE, POP, EXIT }

internal fun CommandNavigation.backAction(navigationOpen: Boolean, helpVisible: Boolean): CommandBackAction = when {
    helpVisible -> CommandBackAction.CLOSE_HELP
    navigationOpen -> CommandBackAction.CLOSE_NAVIGATION
    current.route == CommandRoute.FLEET && current.serverPane != ServerPane.LIST -> CommandBackAction.CLOSE_PANE
    previous != null -> CommandBackAction.POP
    else -> CommandBackAction.EXIT
}

internal fun commandMenuRoutes(workspace: CommandWorkspace): List<CommandRoute> = CommandRoute.values().filter {
    it.workspace == workspace && it !in setOf(
        CommandRoute.OVERVIEW, CommandRoute.INCIDENTS, CommandRoute.SERVER_DOSSIER, CommandRoute.MANAGE_SERVERS,
        CommandRoute.TUNNELS_EDITOR, CommandRoute.UPTIME_EDITOR, CommandRoute.NETWORK_TOOLS_EDITOR, CommandRoute.DNS_EDITOR
    )
}
