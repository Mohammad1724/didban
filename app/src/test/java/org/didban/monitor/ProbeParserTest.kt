package org.didban.monitor

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 4 · 4-B: parser + spec-mapper contract tests.
 * The parser must survive old agents (missing fields) exactly like
 * [TunnelEngine.parseWatchdog] does for the 4-A contract.
 */
class ProbeParserTest {

    private fun fullSnapshot(): JSONObject = JSONObject()
        .put("enabled", true)
        .put("interval_ms", 60000)
        .put("hostname", "node-1")
        .put(
            "points",
            org.json.JSONArray()
                .put(
                    JSONObject()
                        .put(
                            "target",
                            JSONObject()
                                .put("name", "web")
                                .put("mode", "http")
                                .put("host", "example.com")
                                .put("port", 443)
                        )
                        .put("state", "up")
                        .put("observed", true)
                        .put(
                            "last",
                            JSONObject()
                                .put("up", true)
                                .put("latency_ms", 42)
                                .put("detail", "HTTP 200")
                                .put("at", "2026-09-14T10:00:00Z")
                        )
                        .put(
                            "history",
                            org.json.JSONArray()
                                .put(JSONObject().put("up", true))
                                .put(JSONObject().put("up", true))
                                .put(JSONObject().put("up", false))
                                .put(JSONObject().put("up", true))
                        )
                        .put("transitions", org.json.JSONArray())
                )
        )

    @Test
    fun parsesFullSnapshot() {
        val snap = ProbeSnapshot.parse(fullSnapshot())
        assertTrue(snap.enabled)
        assertEquals(60000, snap.intervalMs)
        assertEquals("node-1", snap.hostname)
        assertEquals(1, snap.points.size)
        val p = snap.points[0]
        assertEquals("web", p.name)
        assertEquals("http", p.mode)
        assertEquals("example.com", p.host)
        assertEquals(443, p.port)
        assertEquals("up", p.state)
        assertTrue(p.observed)
        assertEquals(42, p.lastLatencyMs)
        assertEquals("HTTP 200", p.lastDetail)
        // 2026-09-14T10:00:00Z in epoch millis
        assertEquals(1789380000000L, p.lastChecked)
        assertEquals(75f, p.historyUptimePct, 0.001f)
        assertEquals(p, snap.point("web"))
        assertNull(snap.point("nope"))
    }

    @Test
    fun toleratesMissingEverything() {
        val snap = ProbeSnapshot.parse(JSONObject())
        assertTrue(snap.enabled) // absent = assume enabled (newer agent)
        assertEquals(0, snap.intervalMs)
        assertEquals("", snap.hostname)
        assertTrue(snap.points.isEmpty())
    }

    @Test
    fun toleratesPartialPoint() {
        val json = JSONObject()
            .put(
                "points",
                org.json.JSONArray()
                    .put(JSONObject().put("target", JSONObject().put("name", "x")))
                    .put(JSONObject().put("target", JSONObject())) // empty name → skipped
                    .put("not-an-object")
            )
        val snap = ProbeSnapshot.parse(json)
        assertEquals(1, snap.points.size)
        val p = snap.points[0]
        assertEquals("x", p.name)
        assertEquals("", p.state)
        assertFalse(p.observed)
        assertEquals(0f, p.historyUptimePct, 0.001f)
        assertEquals(0L, p.lastChecked)
    }

    @Test
    fun emptyHistoryMeansZeroPercent() {
        val json = JSONObject()
            .put(
                "points",
                org.json.JSONArray()
                    .put(
                        JSONObject()
                            .put("target", JSONObject().put("name", "y"))
                            .put("state", "down")
                            .put("observed", true)
                    )
            )
        val p = ProbeSnapshot.parse(json).points[0]
        assertEquals("down", p.state)
        assertEquals(0f, p.historyUptimePct, 0.001f)
    }

    // ── Spec mapping (parity with UptimeEngine.checkTarget) ────────────────

    private fun target(type: String, target: String, port: Int = 0, name: String = "t") =
        UptimeTarget(id = 1, name = name, type = type, target = target, port = port)

    @Test
    fun httpUrlTargetMapsToHttpSpec() {
        val spec = ProbeSpecs.forTarget(target("HTTP", "https://shop.example.com/store?x=1"))!!
        assertEquals("http", spec.getString("mode"))
        assertEquals("shop.example.com", spec.getString("host"))
        assertEquals(443, spec.getInt("port"))
        assertEquals("https", spec.getString("scheme"))
        assertEquals("/store?x=1", spec.getString("path"))
        assertEquals("t", spec.getString("name"))
    }

    @Test
    fun bareHostHttpTargetDefaultsToHttps443() {
        val spec = ProbeSpecs.forTarget(target("HTTP", "example.com"))!!
        assertEquals("https", spec.getString("scheme"))
        assertEquals(443, spec.getInt("port"))
        assertEquals("example.com", spec.getString("host"))
    }

    @Test
    fun httpTargetWithExplicitPort() {
        val spec = ProbeSpecs.forTarget(target("HTTP", "http://10.0.0.5:8080/healthz"))!!
        assertEquals("http", spec.getString("scheme"))
        assertEquals(8080, spec.getInt("port"))
        assertEquals("/healthz", spec.getString("path"))
    }

    @Test
    fun tcpAndPingMapToTcpMode() {
        assertEquals("tcp", ProbeSpecs.forTarget(target("TCP", "db.example.com", 5432))!!.getString("mode"))
        assertEquals(5432, ProbeSpecs.forTarget(target("TCP", "db.example.com", 5432))!!.getInt("port"))
        // PING on the phone is a TCP socket (default 80) — same here.
        val ping = ProbeSpecs.forTarget(target("PING", "router.local"))!!
        assertEquals("tcp", ping.getString("mode"))
        assertEquals(80, ping.getInt("port"))
    }

    @Test
    fun sslMapsToTcpReachabilityOnly() {
        val spec = ProbeSpecs.forTarget(target("SSL", "tls.example.com"))!!
        assertEquals("tcp", spec.getString("mode"))
        assertEquals(443, spec.getInt("port"))
    }

    @Test
    fun unknownTypeOrBlankHostYieldsNull() {
        assertNull(ProbeSpecs.forTarget(target("FTP", "example.com")))
        assertNull(ProbeSpecs.forTarget(target("HTTP", "   ")))
    }

    @Test
    fun targetsPayloadSkipsPausedAndCapsAt50() {
        val list = (1..52).map {
            UptimeTarget(
                id = it.toLong(),
                name = "t$it",
                type = "TCP",
                target = "h$it",
                port = it
            )
        }.toMutableList()
        list[0].isPaused = true
        val payload = ProbeSpecs.targetsPayload(list)
        val arr = payload.getJSONArray("targets")
        assertEquals(50, arr.length())
        // paused one excluded → first entry is t2
        assertEquals("t2", arr.getJSONObject(0).getString("name"))
    }

    @Test
    fun specNameFallsBackToHost() {
        assertEquals("my name", ProbeSpecs.specName(target("TCP", "h", name = "my name")))
        assertEquals("h.example", ProbeSpecs.specName(target("TCP", "h.example", name = " ")))
    }
}
