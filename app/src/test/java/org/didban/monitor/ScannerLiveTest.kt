package org.didban.monitor

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Live network verification for the two scanner engines.
 *
 * Skipped unless the marker file /tmp/didban-live-net exists, so CI and normal
 * runs never depend on outbound connectivity — but the engines can still be
 * proven against real edges and real certificates on demand:
 *
 *     touch /tmp/didban-live-net
 *     gradle testDebugUnitTest --tests '*ScannerLiveTest*'
 *
 * These exist because compiling network code proves nothing: a wrong SNI
 * parameter, a swallowed handshake exception or an inverted health rule all
 * build fine and silently return garbage.
 */
class ScannerLiveTest {

    private fun live() = File("/tmp/didban-live-net").exists()

    @Test
    fun `cloudflare edges answer cdn-cgi trace from this network`() = runBlocking {
        assumeTrue("live network check disabled", live())
        val cfg = CfScanConfig(
            mode = CfProbeMode.HTTP, port = 443, count = 12, tries = 1,
            timeoutMs = 6000, concurrency = 6
        )
        val ips = CfIpPlan.randomV4(CloudflareRanges.V4, cfg.count, seed = 20260915)
        assertEquals(cfg.count, ips.size)

        val results = CloudflareIpScanner.scan(CloudflareIpScanner.plan(ips, cfg), cfg)
        assertEquals("every candidate must produce a result", ips.size, results.size)

        results.forEach { r ->
            assertEquals(cfg.tries, r.attempts)
            assertTrue("latency samples cannot exceed attempts", r.latenciesMs.size <= r.attempts)
            assertTrue(r.latenciesMs.all { it >= 0 })
            assertTrue(r.loss in 0.0..1.0)
            // a colo can only come from a real trace body
            assertTrue(r.colo.isEmpty() || r.colo.matches(Regex("[A-Za-z0-9]{2,6}")))
        }

        val healthy = results.filter { it.healthy }
        println("LIVE cf: ${healthy.size}/${results.size} clean; " +
            "colos=${healthy.map { it.colo }.filter { it.isNotEmpty() }.distinct()}")
        // Not asserting a minimum count: on a filtered network zero is a
        // legitimate answer. What must hold is that the run completed, every
        // candidate was accounted for, and any winner really did answer.
        healthy.forEach {
            assertTrue("healthy HTTP result needs a trace answer",
                it.httpStatus in 200..399 || it.colo.isNotEmpty())
        }
    }

    @Test
    fun `a microsoft donor passes and a cloudflare donor is rejected`() = runBlocking {
        assumeTrue("live network check disabled", live())

        // alpnCapable is injected because android.os.Build is not available in
        // a plain JVM test.
        val good = RealitySniScanner.probe("www.microsoft.com", 443, 12000, alpnCapable = true)
        println("LIVE reality good: tls=${good.tlsVersion} alpn=${good.alpn} " +
            "cert=${good.certValid} sans=${good.certSans.size} ip=${good.resolvedIp} " +
            "http=${good.httpStatus} err=${good.error}")
        assertEquals("probe must not error out", "", good.error)
        assertTrue("must resolve to an address", good.resolvedIp.isNotEmpty())
        assertTrue("must complete a TLS handshake", good.tlsVersion.isNotEmpty())
        assertTrue("certificate must validate under the platform trust store", good.certValid)
        assertTrue("the donor's certificate must cover its own name", good.sniMatchesSan)
        assertTrue("a real certificate has SANs", good.certSans.isNotEmpty())
        assertTrue("issuer must be reported", good.certIssuer.isNotEmpty())
        assertTrue("chain length must be measured", good.certChainBytes > 0)
        assertTrue(good.certDaysRemaining > 0)
        assertTrue(good.dnsMs >= 0 && good.tcpMs >= 0 && good.tlsMs >= 0)

        val a = RealityCriteria.evaluate(good)
        assertTrue("www.microsoft.com should be usable, blockers were ${a.blockers}",
            a.verdict != RealityVerdict.REJECT)

        // The CDN rule, end to end: a Cloudflare-fronted name must be caught
        // by the resolved-IP check and rejected as a port-forwarding risk.
        val cdn = RealitySniScanner.probe("speed.cloudflare.com", 443, 12000, alpnCapable = true)
        println("LIVE reality cdn: ip=${cdn.resolvedIp} cf=${cdn.behindCloudflare} " +
            "server='${cdn.serverHeader}' tls=${cdn.tlsVersion}")
        if (cdn.error.isEmpty()) {
            assertTrue("speed.cloudflare.com must be detected as Cloudflare-fronted",
                cdn.behindCloudflare)
            val ca = RealityCriteria.evaluate(cdn)
            assertEquals(RealityVerdict.REJECT, ca.verdict)
            assertTrue(ca.blockers.any { it.contains("port-forwarder") })
        }
    }

    @Test
    fun `an unreachable donor is reported as an error, never as a good donor`() = runBlocking {
        assumeTrue("live network check disabled", live())
        // .invalid is reserved by RFC 2606 and can never resolve
        val r = RealitySniScanner.probe("nonexistent-didban-test.invalid", 443, 6000, alpnCapable = true)
        println("LIVE reality bad: err=${r.error} tls=${r.tlsVersion}")
        assertTrue("a failed probe must carry an error", r.error.isNotEmpty())
        assertEquals("", r.tlsVersion)
        assertTrue(!r.reachable)
        val a = RealityCriteria.evaluate(r)
        assertEquals(RealityVerdict.REJECT, a.verdict)
        assertEquals(0, a.score)
    }
}
