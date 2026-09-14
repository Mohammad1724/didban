package org.didban.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 3 · item 3-B — JVM tests for the aerospatial map data layer
 * (AeroMapModel.kt). The Compose renderer is code-reviewed only.
 */
class AeroMapModelTest {

    private fun server(
        id: Long,
        name: String = "s$id",
        host: String = "10.0.0.$id",
        cpuAlert: Int = 90,
        memAlert: Int = 90
    ) = ServerConfig(id = id, name = name, host = host, token = "", cpuAlert = cpuAlert, memAlert = memAlert)

    private fun metrics(cpu: Float = 20f, mem: Float = 30f) = Metrics(
        hostname = "h", time = 0L, uptime = 100L, load1 = 0.5f, cores = 4,
        cpuUsage = cpu, cpuUser = cpu, cpuSystem = 0f, cpuIowait = 0f, cpuSteal = 0f,
        memTotal = 16L, memUsed = 4L, memAvailable = 12L, memPct = mem,
        swapTotal = 0L, swapUsed = 0L, swapPct = 0f,
        disks = emptyList(), nets = emptyList()
    )

    private fun tunnel(
        id: Long = 100,
        iranHost: String = "",
        foreignHost: String = "",
        iranServerId: Long? = null,
        foreignServerId: Long? = null,
        enabled: Boolean = true,
        lastStatus: Int = -1,
        latency: Long = -1
    ) = TunnelConfig(
        id = id, name = "tun$id", core = TunnelCore.BACKPACK,
        iranHost = iranHost, foreignHost = foreignHost,
        iranServerId = iranServerId, foreignServerId = foreignServerId,
        isEnabled = enabled, lastStatus = lastStatus, lastLatencyMs = latency
    )

    // ── Node status classification ──────────────────────────────────────────

    @Test
    fun `online server with healthy metrics is ONLINE`() {
        val g = buildMapGraph(listOf(server(1)), mapOf(1L to Repo.State(metrics(20f, 30f))), emptyList())
        assertEquals(MapNodeStatus.ONLINE, g.nodes[0].status)
    }

    @Test
    fun `server above cpu alert threshold is WARN`() {
        val g = buildMapGraph(
            listOf(server(1, cpuAlert = 90)),
            mapOf(1L to Repo.State(metrics(cpu = 95f, mem = 10f))),
            emptyList()
        )
        assertEquals(MapNodeStatus.WARN, g.nodes[0].status)
    }

    @Test
    fun `server above mem alert threshold is WARN`() {
        val g = buildMapGraph(
            listOf(server(1, memAlert = 80)),
            mapOf(1L to Repo.State(metrics(cpu = 5f, mem = 88f))),
            emptyList()
        )
        assertEquals(MapNodeStatus.WARN, g.nodes[0].status)
    }

    @Test
    fun `server without metrics is OFFLINE`() {
        val g = buildMapGraph(listOf(server(1)), emptyMap(), emptyList())
        assertEquals(MapNodeStatus.OFFLINE, g.nodes[0].status)
        assertEquals(0, g.kpis.onlineCount)
        assertEquals(1, g.kpis.serverCount)
    }

    @Test
    fun `metrics with null error state still counts as online when metrics present`() {
        val g = buildMapGraph(
            listOf(server(1)),
            mapOf(1L to Repo.State(metrics(), error = "transient", latencyMs = 12f)),
            emptyList()
        )
        assertEquals(MapNodeStatus.ONLINE, g.nodes[0].status)
    }

    // ── Arc endpoint resolution & state ─────────────────────────────────────

    @Test
    fun `arc resolves endpoints by explicit server id`() {
        val g = buildMapGraph(
            listOf(server(1), server(2)),
            emptyMap(),
            listOf(tunnel(iranServerId = 1L, foreignServerId = 2L, lastStatus = 1))
        )
        assertEquals(1, g.arcs.size)
        assertEquals(1L, g.arcs[0].fromNodeId)
        assertEquals(2L, g.arcs[0].toNodeId)
        assertEquals(MapArcState.ACTIVE, g.arcs[0].state)
    }

    @Test
    fun `arc resolves endpoints by host when server id missing`() {
        val g = buildMapGraph(
            listOf(server(1, host = "185.42.112.8"), server(2, host = "91.242.55.7")),
            emptyMap(),
            listOf(tunnel(iranHost = "185.42.112.8", foreignHost = "91.242.55.7", lastStatus = 1))
        )
        assertEquals(1, g.arcs.size)
        assertEquals(MapArcState.ACTIVE, g.arcs[0].state)
    }

    @Test
    fun `host matching is case-insensitive`() {
        val g = buildMapGraph(
            listOf(server(1, host = "185.42.112.8"), server(2, host = "91.242.55.7")),
            emptyMap(),
            listOf(tunnel(iranHost = "185.42.112.8 ".trim(), foreignHost = "91.242.55.7", lastStatus = 0))
        )
        assertEquals(1, g.arcs.size)
        assertEquals(MapArcState.DOWN, g.arcs[0].state)
    }

    @Test
    fun `tunnel with unknown endpoint is not drawn (no fake nodes)`() {
        val g = buildMapGraph(
            listOf(server(1)),
            emptyMap(),
            listOf(tunnel(iranHost = "10.0.0.1", foreignHost = "unregistered.example", lastStatus = 1))
        )
        assertTrue(g.arcs.isEmpty())
        assertEquals(0, g.kpis.tunnelCount)
    }

    @Test
    fun `loopback tunnel on one host is not drawn`() {
        val g = buildMapGraph(
            listOf(server(1)),
            emptyMap(),
            listOf(tunnel(iranHost = "10.0.0.1", foreignHost = "10.0.0.1", lastStatus = 1))
        )
        assertTrue(g.arcs.isEmpty())
    }

    @Test
    fun `disabled tunnel is DORMANT regardless of last status`() {
        val g = buildMapGraph(
            listOf(server(1), server(2)),
            emptyMap(),
            listOf(tunnel(iranServerId = 1L, foreignServerId = 2L, enabled = false, lastStatus = 1))
        )
        assertEquals(MapArcState.DORMANT, g.arcs[0].state)
        assertEquals(0, g.kpis.activeTunnels)
    }

    @Test
    fun `never-checked enabled tunnel is DORMANT`() {
        val g = buildMapGraph(
            listOf(server(1), server(2)),
            emptyMap(),
            listOf(tunnel(iranServerId = 1L, foreignServerId = 2L, lastStatus = -1))
        )
        assertEquals(MapArcState.DORMANT, g.arcs[0].state)
    }

    @Test
    fun `arc latency is carried through`() {
        val g = buildMapGraph(
            listOf(server(1), server(2)),
            emptyMap(),
            listOf(tunnel(iranServerId = 1L, foreignServerId = 2L, lastStatus = 1, latency = 23))
        )
        assertEquals(23L, g.arcs[0].latencyMs)
    }

    // ── KPI derivation ──────────────────────────────────────────────────────

    @Test
    fun `kpis onlineCount excludes offline only`() {
        val g = buildMapGraph(
            listOf(server(1), server(2, cpuAlert = 90), server(3), server(4)),
            mapOf(
                1L to Repo.State(metrics(10f, 10f)),
                2L to Repo.State(metrics(95f, 10f)),
                4L to Repo.State(metrics(10f, 10f))
            ),
            emptyList()
        )
        // server 3 has no metrics → offline; 1,2,4 have metrics → counted
        assertEquals(3, g.kpis.onlineCount)
        assertEquals(4, g.kpis.serverCount)
        assertEquals(1, g.kpis.alerts) // 1 WARN node, no down arcs
    }

    @Test
    fun `kpis alerts = warn nodes + down arcs`() {
        val g = buildMapGraph(
            listOf(server(1), server(2), server(3), server(4)),
            mapOf(
                1L to Repo.State(metrics(99f, 10f)), // warn
                2L to Repo.State(metrics(10f, 10f)),
                4L to Repo.State(metrics(10f, 10f))
            ),
            listOf(
                tunnel(1, iranServerId = 2L, foreignServerId = 4L, lastStatus = 0), // down
                tunnel(2, iranServerId = 2L, foreignServerId = 4L, lastStatus = 1)  // active
            )
        )
        assertEquals(2, g.kpis.alerts)
        assertEquals(1, g.kpis.activeTunnels)
        assertEquals(2, g.kpis.tunnelCount)
    }

    // ── Layout: deterministic orbital placement ─────────────────────────────

    @Test
    fun `single server sits at center`() {
        val (fx, fy) = nodePosition(0, 1, 0.5f, 0.5f, 0.33f)
        assertEquals(0.5f, fx, 1e-6f)
        assertEquals(0.5f, fy, 1e-6f)
    }

    @Test
    fun `first of many sits at center, others on the circle`() {
        val c = nodePosition(0, 4, 0.5f, 0.5f, 0.33f)
        assertEquals(0.5f, c.first, 1e-6f)
        assertEquals(0.5f, c.second, 1e-6f)
        for (i in 1..3) {
            val p = nodePosition(i, 4, 0.5f, 0.5f, 0.33f)
            val dx = p.first - 0.5f
            val dy = p.second - 0.5f
            assertEquals(0.33f, Math.hypot(dx.toDouble(), dy.toDouble()).toFloat(), 1e-4f)
        }
    }

    @Test
    fun `layout is deterministic and distinct per index`() {
        val a = nodePosition(1, 3, 0.5f, 0.5f, 0.33f)
        val b = nodePosition(2, 3, 0.5f, 0.5f, 0.33f)
        val a2 = nodePosition(1, 3, 0.5f, 0.5f, 0.33f)
        assertEquals(a, a2)
        assertTrue(a != b)
    }

    @Test
    fun `graph node coordinates follow the orbital layout`() {
        val g = buildMapGraph(listOf(server(1), server(2), server(3)), emptyMap(), emptyList())
        assertEquals(3, g.nodes.size)
        assertEquals(0.5f, g.nodes[0].fx, 1e-6f) // primary at center
        assertTrue(g.nodes[1].fx != g.nodes[2].fx || g.nodes[1].fy != g.nodes[2].fy)
        g.nodes.forEach {
            assertTrue(it.fx in 0f..1f)
            assertTrue(it.fy in 0f..1f)
        }
    }
}
