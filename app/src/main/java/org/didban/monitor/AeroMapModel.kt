package org.didban.monitor

import kotlin.math.cos
import kotlin.math.sin

/**
 * Phase 3 · item 3-B — pure data layer for the aerospatial map.
 *
 * Turns the app's existing state (servers + Repo poll states + tunnels) into a
 * drawable [MapGraph]. No Android/Compose imports — fully JVM-testable.
 * The Compose renderer (AeroCanvasHome.kt) only draws this model.
 */

enum class MapNodeStatus { ONLINE, WARN, OFFLINE }
enum class MapArcState { ACTIVE, DOWN, DORMANT }

/** A drawable node. fx/fy are normalized canvas coordinates in 0..1. */
data class MapNode(
    val serverId: Long,
    val name: String,
    val host: String,
    val status: MapNodeStatus,
    val fx: Float,
    val fy: Float,
    val cpuPct: Float? = null,
    val memPct: Float? = null
)

/** A tunnel rendered as an arc between two known server nodes. */
data class MapArc(
    val tunnelId: Long,
    val fromNodeId: Long,
    val toNodeId: Long,
    val state: MapArcState,
    val latencyMs: Long
)

/** HUD figures derived from the same graph (single source of truth). */
data class MapKpis(
    val onlineCount: Int,
    val serverCount: Int,
    val activeTunnels: Int,
    val tunnelCount: Int,
    val alerts: Int
)

data class MapGraph(
    val nodes: List<MapNode>,
    val arcs: List<MapArc>,
    val kpis: MapKpis
)

/**
 * Deterministic orbital layout: the first server sits at the center (it is the
 * "primary" node), the rest are placed on a circle starting at the top.
 * Pure function of (index, count) — stable across recompositions and testable.
 */
fun nodePosition(index: Int, count: Int, cx: Float, cy: Float, radius: Float): Pair<Float, Float> {
    if (count <= 0) return cx to cy
    if (count == 1 || index == 0) return cx to cy
    val angle = 2f * Math.PI.toFloat() * index / count - Math.PI.toFloat() / 2f
    return (cx + radius * cos(angle)) to (cy + radius * sin(angle))
}

/**
 * Build the drawable graph.
 *
 * Node status (mirrors the existing UI semantics exactly):
 *  - ONLINE  = latest poll returned metrics, under both alert thresholds
 *  - WARN    = metrics present but cpu >= server.cpuAlert or mem >= server.memAlert
 *  - OFFLINE = no metrics in the shared Repo state (poller has no data)
 *
 * Arcs:
 *  - endpoints resolved by explicit serverId first, then by host match
 *  - an arc is only drawable when BOTH endpoints resolve to known nodes
 *    (tunnels to unregistered hosts stay on the Tunnels page — no fake nodes)
 *  - ACTIVE = enabled && lastStatus 1 · DOWN = enabled && lastStatus 0
 *    · DORMANT = disabled or never checked
 *
 * KPI `alerts` = WARN nodes + DOWN arcs (anomalies needing attention).
 * Plain OFFLINE is already visible in the online counter.
 */
fun buildMapGraph(
    servers: List<ServerConfig>,
    states: Map<Long, Repo.State>,
    tunnels: List<TunnelConfig>
): MapGraph {
    val cx = 0.5f
    val cy = 0.5f
    val radius = 0.33f

    val nodes = servers.mapIndexed { i, s ->
        val metrics = states[s.id]?.metrics
        val status = when {
            metrics == null -> MapNodeStatus.OFFLINE
            metrics.cpuUsage >= s.cpuAlert || metrics.memPct >= s.memAlert -> MapNodeStatus.WARN
            else -> MapNodeStatus.ONLINE
        }
        val (fx, fy) = nodePosition(i, servers.size, cx, cy, radius)
        MapNode(
            serverId = s.id,
            name = s.name,
            host = s.host,
            status = status,
            fx = fx,
            fy = fy,
            cpuPct = metrics?.cpuUsage,
            memPct = metrics?.memPct
        )
    }

    val serverById = servers.associateBy { it.id }

    fun resolveNode(serverId: Long?, host: String): Long? {
        serverId?.let { if (serverById.containsKey(it)) return it }
        if (host.isBlank()) return null
        return servers.firstOrNull { it.host.equals(host, ignoreCase = true) }?.id
    }

    val arcs = tunnels.mapNotNull { tun ->
        val fromId = resolveNode(tun.iranServerId, tun.iranHost)
        val toId = resolveNode(tun.foreignServerId, tun.foreignHost)
        if (fromId == null || toId == null || fromId == toId) null else {
            val state = when {
                !tun.isEnabled -> MapArcState.DORMANT
                tun.lastStatus == 1 -> MapArcState.ACTIVE
                tun.lastStatus == 0 -> MapArcState.DOWN
                else -> MapArcState.DORMANT
            }
            MapArc(tun.id, fromId, toId, state, tun.lastLatencyMs)
        }
    }

    val kpis = MapKpis(
        onlineCount = nodes.count { it.status != MapNodeStatus.OFFLINE },
        serverCount = nodes.size,
        activeTunnels = arcs.count { it.state == MapArcState.ACTIVE },
        tunnelCount = arcs.size,
        alerts = nodes.count { it.status == MapNodeStatus.WARN } +
                arcs.count { it.state == MapArcState.DOWN }
    )

    return MapGraph(nodes = nodes, arcs = arcs, kpis = kpis)
}
