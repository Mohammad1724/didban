package org.didban.monitor

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger

internal fun scannerFixture(host: String, port: Int) = RealityProbeResult(
    host, port, "203.0.113.1", 1, 2, 3, 6, "TLSv1.3", "h2", "fixture", "fixture", "fixture",
    listOf(host), 90, "RSA", 1000, true, "", true, 200, "", "fixture", false, false
)

class ScannerCatalogTest {
    @Test fun `large offline catalog has unique valid candidates across four balanced categories`() {
        val all = ScannerCatalog.domains(ScannerCatalog.Group.ALL)
        assertTrue(all.size >= 200)
        assertTrue(all.size <= ScannerCatalog.MAX_SNI_TARGETS)
        assertEquals(all.size, all.distinct().size)
        assertTrue(all.all(ScannerCatalog::validDomain))
        assertTrue(all.none { it in RealitySniScanner.DISCOURAGED })
        assertEquals(4, ScannerCatalog.groups.size)
        assertTrue(ScannerCatalog.groups.values.all { it.size >= 40 })
        ScannerCatalog.groups.values.forEach { assertTrue(it.first() in all.take(4)) }
        all.forEach { assertEquals(it to 443, RealitySniScanner.parseTarget(it)) }
    }

    @Test fun `preset SNI plan respects selected category port and bounded count`() {
        val group = ScannerCatalog.Group.DEVELOPMENT
        assertEquals(7, ScannerCatalog.sniPlan(group, 7, 8443).size)
        assertTrue(ScannerCatalog.sniPlan(group, 7, 8443).all { it.first in ScannerCatalog.domains(group) && it.second == 8443 })
        assertTrue(ScannerCatalog.sniPlan(group, 99999, 443).size <= 256)
        assertTrue(ScannerCatalog.sniPlan(group, 10, 0).isEmpty())
    }

    @Test fun `range selection only uses the published IPv4 snapshot`() {
        assertEquals(15, CloudflareRanges.V4.size)
        assertTrue(CloudflareRanges.V4.all { Cidr.parse(it) != null })
        val selected = ScannerCatalog.selectedRanges("104.16.0.0/13,192.168.0.0/16,104.16.0.0/13")
        assertEquals(listOf("104.16.0.0/13"), selected)
        val ips = CfIpPlan.randomV4(selected, 128, 42)
        assertEquals(128, ips.size)
        assertTrue(ips.all { Cidr.inAny(it, selected) })
        assertTrue(CfIpPlan.randomV4(ScannerCatalog.selectedRanges(""), 128, 42).isEmpty())
    }

    @Test fun `custom domains normalize deduplicate and reject malformed hosts`() {
        val parsed = ScannerCatalog.parseSniList("""
            # comment
            https://EXAMPLE.com/path
            example.com
            example.com:8443
            127.0.0.1
            https://user:password@evil.example
            -bad.example
            no-domain
        """.trimIndent(), 443)
        assertEquals(listOf("example.com" to 443, "example.com" to 8443), parsed.targets)
        assertEquals(4, parsed.rejected)
        assertFalse(parsed.truncated)
    }

    @Test fun `custom domains honor the run cap and expose truncation`() {
        val parsed = ScannerCatalog.parseSniList((1..300).joinToString("\n") { "node$it.example" }, 9443, 100)
        assertEquals(100, parsed.targets.size)
        assertTrue(parsed.targets.all { it.second == 9443 })
        assertTrue(parsed.truncated)
    }

    @Test fun `file reader accepts UTF8 BOM but rejects oversized and nontext data`() {
        assertEquals("example.com\n", ScannerCatalog.readText(ByteArrayInputStream("\uFEFFexample.com\n".toByteArray())))
        assertThrows(IllegalArgumentException::class.java) {
            ScannerCatalog.readText(ByteArrayInputStream(ByteArray(ScannerCatalog.MAX_TEXT_BYTES + 1)))
        }
        assertThrows(java.nio.charset.CharacterCodingException::class.java) {
            ScannerCatalog.readText(ByteArrayInputStream(byteArrayOf(0xC3.toByte(), 0x28)))
        }
    }

    @Test fun `batch uses at most four workers and reports monotonic progress`() = runBlocking {
        val active = AtomicInteger(); val peak = AtomicInteger(); val counts = mutableListOf<Int>()
        val targets = ScannerCatalog.sniPlan(ScannerCatalog.Group.ALL, 24, 8443)
        val results = scanSniCandidates(targets, { host, port ->
            val now = active.incrementAndGet(); peak.updateAndGet { maxOf(it, now) }
            try { delay(5); scannerFixture(host, port) } finally { active.decrementAndGet() }
        }) { counts += it.size }
        assertEquals(24, results.size)
        assertEquals((1..24).toList(), counts)
        assertEquals(4, peak.get())
        assertEquals(targets.toSet(), results.map { it.first.sni to it.first.port }.toSet())
    }

    @Test fun `cancelling a batch prevents queued targets from starting`() = runBlocking {
        val started = AtomicInteger()
        val job = launch {
            scanSniCandidates(ScannerCatalog.sniPlan(ScannerCatalog.Group.ALL, 50, 443), { _, _ ->
                started.incrementAndGet(); awaitCancellation()
            }) { fail("cancelled probes must not produce results") }
        }
        yield(); yield()
        job.cancelAndJoin()
        assertTrue(started.get() <= 4)
        assertTrue(job.isCancelled)
    }
}
