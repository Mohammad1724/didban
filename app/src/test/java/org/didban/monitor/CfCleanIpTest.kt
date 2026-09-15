package org.didban.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-layer tests for the Cloudflare clean-IP scanner.
 *
 * The probe itself needs a network; everything that decides *which* addresses
 * to try, *whether* a result is usable and *how* results rank is pure, and
 * those are the parts worth pinning down — a wrong health rule silently turns
 * a scan into noise.
 */
class CfCleanIpTest {

    // ── CIDR maths ─────────────────────────────────────────────────────────

    @Test
    fun `ip parsing and formatting round-trip`() {
        for (ip in listOf("0.0.0.0", "104.16.1.1", "255.255.255.255", "173.245.48.0")) {
            val v = Cidr.parseIp(ip)
            assertNotNull(ip, v)
            assertEquals(ip, Cidr.formatIp(v!!))
        }
    }

    @Test
    fun `malformed addresses are rejected rather than coerced`() {
        for (bad in listOf("", "104.16.1", "104.16.1.1.1", "256.0.0.1", "-1.0.0.0",
            "104.16.a.1", "104.16.1.1 ", "example.com")) {
            assertNull("should not parse: $bad", Cidr.parseIp(bad))
        }
    }

    @Test
    fun `cidr size and membership`() {
        val r = Cidr.parse("104.16.0.0/13")
        assertNotNull(r)
        assertEquals(1L shl 19, r!!.size)
        assertTrue(r.contains(Cidr.parseIp("104.16.0.0")!!))
        assertTrue(r.contains(Cidr.parseIp("104.23.255.255")!!))
        assertFalse(r.contains(Cidr.parseIp("104.24.0.0")!!))
        assertFalse(r.contains(Cidr.parseIp("8.8.8.8")!!))
    }

    @Test
    fun `host prefix is masked so an unmasked input still matches`() {
        // A pasted "104.16.5.7/13" is a common mistake; masking keeps it usable
        // instead of silently matching nothing.
        val r = Cidr.parse("104.16.5.7/13")
        assertNotNull(r)
        assertEquals(Cidr.parseIp("104.16.0.0"), r!!.network)
        assertTrue(r.contains(Cidr.parseIp("104.16.1.1")!!))
    }

    @Test
    fun `edge prefixes behave`() {
        val all = Cidr.parse("0.0.0.0/0")!!
        assertTrue(all.contains(Cidr.parseIp("8.8.8.8")!!))
        assertEquals(1L shl 32, all.size)
        val host = Cidr.parse("1.2.3.4/32")!!
        assertEquals(1L, host.size)
        assertTrue(host.contains(Cidr.parseIp("1.2.3.4")!!))
        assertFalse(host.contains(Cidr.parseIp("1.2.3.5")!!))
        assertNull(Cidr.parse("1.2.3.4/33"))
        assertNull(Cidr.parse("1.2.3.4/-1"))
        assertNull(Cidr.parse("1.2.3.4"))
    }

    @Test
    fun `known cloudflare edges are recognised and google dns is not`() {
        val cf = CloudflareRanges.V4
        assertTrue(Cidr.inAny("104.16.1.1", cf))
        assertTrue(Cidr.inAny("172.64.0.1", cf))
        assertTrue(Cidr.inAny("162.158.0.1", cf))
        assertFalse(Cidr.inAny("8.8.8.8", cf))
        assertFalse(Cidr.inAny("1.1.1.1", cf))     // Cloudflare DNS, different range
        assertFalse(Cidr.inAny("not-an-ip", cf))
        // an IPv6 literal must not blow up the IPv4 matcher
        assertFalse(Cidr.inAny("2400:cb00::1", cf))
    }

    @Test
    fun `port classification matches the published cloudflare port sets`() {
        for (p in listOf(443, 2053, 2083, 2087, 2096, 8443)) {
            assertTrue("$p should be a TLS port", CloudflareRanges.isTlsPort(p))
            assertFalse("$p should not be an HTTP port", CloudflareRanges.isHttpPort(p))
        }
        for (p in listOf(80, 8080, 8880, 2052, 2082, 2086, 2095)) {
            assertTrue("$p should be an HTTP port", CloudflareRanges.isHttpPort(p))
            assertFalse("$p should not be a TLS port", CloudflareRanges.isTlsPort(p))
        }
        assertFalse(CloudflareRanges.isTlsPort(22))
        assertFalse(CloudflareRanges.isHttpPort(22))
    }

    // ── candidate planning ─────────────────────────────────────────────────

    @Test
    fun `random plan is reproducible for a seed and stays inside the ranges`() {
        val ranges = CloudflareRanges.V4
        val a = CfIpPlan.randomV4(ranges, 200, seed = 42)
        val b = CfIpPlan.randomV4(ranges, 200, seed = 42)
        assertEquals("same seed must give the same plan", a, b)
        assertEquals(200, a.size)
        assertEquals("plan must be deduplicated", a.size, a.distinct().size)
        a.forEach { ip ->
            assertNotNull(ip, Cidr.parseIp(ip))
            assertTrue("$ip escaped the published ranges", Cidr.inAny(ip, ranges))
        }
        val c = CfIpPlan.randomV4(ranges, 200, seed = 43)
        assertFalse("different seeds should differ", a == c)
    }

    @Test
    fun `plan sorts ascending so results are stable to read`() {
        val plan = CfIpPlan.randomV4(CloudflareRanges.V4, 300, seed = 7)
        val numeric = plan.map { Cidr.parseIp(it)!! }
        assertEquals(numeric.sorted(), numeric)
    }

    @Test
    fun `empty and degenerate plans are empty, not an error`() {
        assertEquals(emptyList<String>(), CfIpPlan.randomV4(emptyList(), 10, 1))
        assertEquals(emptyList<String>(), CfIpPlan.randomV4(CloudflareRanges.V4, 0, 1))
        assertEquals(emptyList<String>(), CfIpPlan.randomV4(listOf("garbage"), 10, 1))
        // a /32 holds one address; asking for 50 must not spin or invent hosts
        val tiny = CfIpPlan.randomV4(listOf("1.2.3.4/32"), 50, 1)
        assertTrue(tiny.size <= 1)
    }

    @Test
    fun `explicit cidr expansion respects the cap`() {
        val out = CfIpPlan.expand(listOf("198.51.100.0/24"), cap = 10)
        assertEquals(10, out.size)
        assertEquals("198.51.100.0", out.first())
        assertEquals("198.51.100.9", out.last())
        assertEquals(2, CfIpPlan.expand(listOf("198.51.100.0/31"), cap = 10).size)
        assertEquals(emptyList<String>(), CfIpPlan.expand(listOf("nonsense"), cap = 10))
    }

    @Test
    fun `pasted lists accept ips and cidrs, skip comments and junk`() {
        val text = """
            # my candidate edges
            104.16.1.1
            172.64.0.0/30

            not-an-ip
            999.1.1.1
            104.16.1.1
        """.trimIndent()
        val out = CfIpPlan.parseList(text, cap = 100)
        assertTrue("104.16.1.1" in out)
        // /30 expands to 4 addresses
        assertTrue("172.64.0.0" in out && "172.64.0.3" in out)
        assertFalse(out.any { it == "not-an-ip" || it == "999.1.1.1" })
        assertEquals("duplicates collapse", out.size, out.distinct().size)
    }

    // ── health rule ────────────────────────────────────────────────────────

    private fun result(
        mode: CfProbeMode = CfProbeMode.HTTP,
        port: Int = 443,
        latencies: List<Long> = listOf(50, 60),
        attempts: Int = 2,
        tlsOk: Boolean = true,
        httpStatus: Int = 200,
        colo: String = "FRA"
    ) = CfProbeResult(
        ip = "104.16.1.1", port = port, mode = mode, latenciesMs = latencies,
        attempts = attempts, tlsOk = tlsOk, httpStatus = httpStatus, colo = colo
    )

    @Test
    fun `a result with no successful attempt is never healthy`() {
        assertFalse(CfHealth.isHealthy(result(latencies = emptyList())))
        assertEquals(1.0, result(latencies = emptyList()).loss, 1e-9)
        assertEquals(0.0, result(latencies = emptyList()).avgLatencyMs, 1e-9)
    }

    @Test
    fun `loss above half disqualifies even a fast edge`() {
        val r = result(latencies = listOf(20), attempts = 3)
        assertEquals(2.0 / 3.0, r.loss, 1e-9)
        assertFalse("66% loss must not be reported as clean", CfHealth.isHealthy(r))
        val half = result(latencies = listOf(20), attempts = 2)
        assertEquals(0.5, half.loss, 1e-9)
        assertTrue("exactly 50% loss is still usable", CfHealth.isHealthy(half))
    }

    @Test
    fun `tcp mode needs only a completed dial`() {
        assertTrue(CfHealth.isHealthy(result(mode = CfProbeMode.TCP, tlsOk = false, httpStatus = 0, colo = "")))
    }

    @Test
    fun `tls mode requires a completed handshake`() {
        assertTrue(CfHealth.isHealthy(result(mode = CfProbeMode.TLS, tlsOk = true)))
        assertFalse(CfHealth.isHealthy(result(mode = CfProbeMode.TLS, tlsOk = false)))
    }

    @Test
    fun `http mode on a tls port requires both the handshake and a real answer`() {
        assertFalse("a bare TCP dial on 443 proves nothing",
            CfHealth.isHealthy(result(mode = CfProbeMode.HTTP, tlsOk = false, httpStatus = 200, colo = "FRA")))
        assertFalse("handshake but no answer",
            CfHealth.isHealthy(result(mode = CfProbeMode.HTTP, tlsOk = true, httpStatus = 0, colo = "")))
        assertTrue(CfHealth.isHealthy(result(mode = CfProbeMode.HTTP, tlsOk = true, httpStatus = 200, colo = "")))
        // a colo proves the trace body came from a Cloudflare edge even if the
        // status line was odd
        assertTrue(CfHealth.isHealthy(result(mode = CfProbeMode.HTTP, tlsOk = true, httpStatus = 0, colo = "FRA")))
        assertFalse("5xx is not clean",
            CfHealth.isHealthy(result(mode = CfProbeMode.HTTP, tlsOk = true, httpStatus = 503, colo = "")))
    }

    @Test
    fun `http mode on a plain port does not demand a tls handshake`() {
        assertTrue(CfHealth.isHealthy(
            result(mode = CfProbeMode.HTTP, port = 8080, tlsOk = false, httpStatus = 200, colo = "FRA")))
    }

    // ── statistics and ranking ─────────────────────────────────────────────

    @Test
    fun `jitter is the population standard deviation of the samples`() {
        val steady = result(latencies = listOf(50, 50, 50))
        assertEquals(0.0, steady.jitterMs, 1e-9)
        val spread = result(latencies = listOf(40, 60))
        assertEquals(10.0, spread.jitterMs, 1e-9)
        assertEquals(0.0, result(latencies = listOf(40)).jitterMs, 1e-9)
        assertEquals(40L, spread.minLatencyMs)
        assertEquals(60L, spread.maxLatencyMs)
    }

    @Test
    fun `ranking prefers a steady edge over a faster lossy one`() {
        val steady = result(latencies = listOf(90, 90, 90), attempts = 3)
        val lossy = result(latencies = listOf(40, 40), attempts = 6)   // 67% loss
        assertTrue(CfRanker.score(steady) < CfRanker.score(lossy))
        assertEquals(Double.MAX_VALUE, CfRanker.score(lossy), 0.0)
    }

    @Test
    fun `an edge that answered with a colo outranks an equal one that did not`() {
        val withColo = result(colo = "FRA")
        val without = result(colo = "")
        assertTrue(CfRanker.score(without) > CfRanker.score(withColo))
    }

    @Test
    fun `best filters unhealthy results and sorts ascending`() {
        val good = result(latencies = listOf(80, 80))
        val better = result(latencies = listOf(30, 30))
        val bad = result(latencies = emptyList())
        val ranked = CfRanker.best(listOf(good, bad, better))
        assertEquals(listOf(better, good), ranked)
        assertEquals(emptyList<CfProbeResult>(), CfRanker.best(listOf(bad)))
    }

    // ── config sanitising and SNI rotation ─────────────────────────────────

    @Test
    fun `absurd scan settings are clamped instead of trusted`() {
        val wild = CfScanConfig(count = 10_000_000, tries = 1000, timeoutMs = 1, concurrency = 100_000)
        val c = CfScanConfig.sanitize(wild)
        assertEquals(CfScanConfig.MAX_COUNT, c.count)
        assertEquals(8, c.tries)
        assertEquals(CfScanConfig.MIN_TIMEOUT_MS, c.timeoutMs)
        assertEquals(CfScanConfig.MAX_CONCURRENCY, c.concurrency)
        val slow = CfScanConfig.sanitize(CfScanConfig(timeoutMs = 999_999))
        assertEquals(CfScanConfig.MAX_TIMEOUT_MS, slow.timeoutMs)
    }

    @Test
    fun `an explicit sni override wins and rotation covers every front`() {
        assertEquals("my.example", CloudflareIpScanner.chooseSni(0, "  my.example  "))
        assertEquals("my.example", CloudflareIpScanner.chooseSni(7, "my.example"))
        val seen = (0 until CloudflareRanges.SNIS.size * 3)
            .map { CloudflareIpScanner.chooseSni(it, "") }
            .toSet()
        assertEquals(CloudflareRanges.SNIS.toSet(), seen)
        // a negative attempt index must not throw
        assertNotNull(CloudflareIpScanner.chooseSni(-1, ""))
    }

    @Test
    fun `plan pairs every address with the configured port`() {
        val cfg = CfScanConfig(port = 8443)
        val plan = CloudflareIpScanner.plan(listOf("104.16.1.1", "172.64.0.1"), cfg)
        assertEquals(2, plan.size)
        assertTrue(plan.all { it.port == 8443 })
    }
}
