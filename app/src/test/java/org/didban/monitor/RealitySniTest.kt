package org.didban.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rules for accepting a REALITY donor (dest / SNI).
 *
 * These are the properties that decide whether a stolen handshake actually
 * impersonates the target, so each one is pinned individually: a donor that is
 * fast but redirects, or whose certificate does not cover the SNI, produces a
 * working-looking config that never tunnels.
 */
class RealitySniTest {

    /** A donor that passes everything measurable from an Android device. */
    private fun good(
        tlsVersion: String = "TLSv1.3",
        alpn: String = "h2",
        certValid: Boolean = true,
        certError: String = "",
        sans: List<String> = listOf("www.microsoft.com", "microsoft.com"),
        sni: String = "www.microsoft.com",
        httpStatus: Int = 200,
        redirectLocation: String = "",
        resolvedIp: String = "13.107.21.200",
        behindCloudflare: Boolean = false,
        behindCdn: Boolean = false,
        serverHeader: String = "Kestrel",
        certDaysRemaining: Long = 300,
        totalMs: Long = 150,
        platformTls13Capable: Boolean = true,
        platformAlpnCapable: Boolean = true,
        error: String = "",
        certChainBytes: Int = 3600,
        certPublicKeyAlg: String = "RSA"
    ) = RealityProbeResult(
        sni = sni, port = 443, resolvedIp = resolvedIp,
        dnsMs = 10, tcpMs = 30, tlsMs = 60, totalMs = totalMs,
        tlsVersion = tlsVersion, alpn = alpn, cipher = "TLS_AES_256_GCM_SHA384",
        certSubject = "CN=$sni", certIssuer = "CN=Microsoft Azure TLS Issuing CA 01",
        certSans = sans, certDaysRemaining = certDaysRemaining,
        certPublicKeyAlg = certPublicKeyAlg, certChainBytes = certChainBytes,
        certValid = certValid, certError = certError,
        sniMatchesSan = RealityCriteria.sniMatchesSans(sni, sans),
        httpStatus = httpStatus, redirectLocation = redirectLocation,
        serverHeader = serverHeader,
        behindCloudflare = behindCloudflare, behindCdn = behindCdn,
        platformTls13Capable = platformTls13Capable,
        platformAlpnCapable = platformAlpnCapable,
        error = error
    )

    // ── target parsing ─────────────────────────────────────────────────────

    @Test
    fun `targets are normalised to a hostname and a port`() {
        assertEquals("www.microsoft.com" to 443, RealitySniScanner.parseTarget("www.microsoft.com"))
        assertEquals("www.microsoft.com" to 443, RealitySniScanner.parseTarget("  WWW.Microsoft.COM  "))
        assertEquals("example.com" to 8443, RealitySniScanner.parseTarget("example.com:8443"))
        assertEquals("example.com" to 443, RealitySniScanner.parseTarget("https://example.com/some/path"))
        assertEquals("example.com" to 2053, RealitySniScanner.parseTarget("http://example.com:2053"))
        // a trailing root dot is stripped so SAN matching is not defeated by it
        assertEquals("example.com" to 443, RealitySniScanner.parseTarget("example.com."))
    }

    @Test
    fun `targets that cannot be a donor are rejected`() {
        // An IP literal has no certificate name to match, so it cannot be an SNI.
        assertNull(RealitySniScanner.parseTarget("1.2.3.4"))
        assertNull(RealitySniScanner.parseTarget("1.2.3.4:443"))
        assertNull(RealitySniScanner.parseTarget(""))
        assertNull(RealitySniScanner.parseTarget("   "))
        assertNull(RealitySniScanner.parseTarget("localhost"))     // no dot
        assertNull(RealitySniScanner.parseTarget("example.com:99999"))
        assertNull(RealitySniScanner.parseTarget("example.com:abc"))
        assertNull(RealitySniScanner.parseTarget("example.com:0"))
    }

    @Test
    fun `discouraged donors are documented with a reason`() {
        assertTrue(RealitySniScanner.DISCOURAGED.containsKey("www.google.com"))
        assertTrue(RealitySniScanner.DISCOURAGED.containsKey("speed.cloudflare.com"))
        RealitySniScanner.DISCOURAGED.values.forEach { assertTrue(it.isNotBlank()) }
        // the suggested list must not contain anything we also discourage
        assertTrue(RealitySniScanner.SUGGESTED.none { RealitySniScanner.DISCOURAGED.containsKey(it) })
        RealitySniScanner.SUGGESTED.forEach {
            val parsed = RealitySniScanner.parseTarget(it)
            assertNotNull("$it should be a usable target", parsed)
        }
    }

    // ── SAN matching (RFC 6125 wildcard rules) ─────────────────────────────

    @Test
    fun `exact san matches, case and trailing dot insensitive`() {
        val sans = listOf("www.example.com", "example.com")
        assertTrue(RealityCriteria.sniMatchesSans("www.example.com", sans))
        assertTrue(RealityCriteria.sniMatchesSans("WWW.Example.COM", sans))
        assertTrue(RealityCriteria.sniMatchesSans("www.example.com.", sans))
        assertTrue(RealityCriteria.sniMatchesSans("example.com", sans))
        assertFalse(RealityCriteria.sniMatchesSans("other.com", sans))
        assertFalse(RealityCriteria.sniMatchesSans("", sans))
    }

    @Test
    fun `a wildcard covers exactly one label`() {
        val sans = listOf("*.example.com")
        assertTrue(RealityCriteria.sniMatchesSans("www.example.com", sans))
        assertTrue(RealityCriteria.sniMatchesSans("mail.example.com", sans))
        assertFalse("wildcard must not cover the apex", RealityCriteria.sniMatchesSans("example.com", sans))
        assertFalse("wildcard must not span a dot", RealityCriteria.sniMatchesSans("a.b.example.com", sans))
        assertFalse("wildcard must not match a suffix of a label",
            RealityCriteria.sniMatchesSans("notexample.com", sans))
    }

    @Test
    fun `dns prefixed san entries are handled`() {
        // Some stacks hand back "DNS:www.example.com" rather than the bare name.
        assertTrue(RealityCriteria.sniMatchesSans("www.example.com", listOf("DNS:www.example.com")))
        assertFalse(RealityCriteria.sniMatchesSans("www.example.com", listOf("DNS:other.com")))
    }

    // ── verdicts ───────────────────────────────────────────────────────────

    @Test
    fun `a fully compliant donor is good and scores high`() {
        val a = RealityCriteria.evaluate(good())
        assertEquals(RealityVerdict.GOOD, a.verdict)
        assertEquals(emptyList<String>(), a.blockers)
        assertEquals(emptyList<String>(), a.warnings)
        assertTrue("score should be high, was ${a.score}", a.score >= 90)
    }

    @Test
    fun `tls 1_2 is a hard blocker`() {
        val a = RealityCriteria.evaluate(good(tlsVersion = "TLSv1.2"))
        assertEquals(RealityVerdict.REJECT, a.verdict)
        assertEquals(0, a.score)
        assertTrue(a.blockers.any { it.contains("TLS 1.3") })
    }

    @Test
    fun `an invalid certificate is a hard blocker`() {
        val a = RealityCriteria.evaluate(good(certValid = false, certError = "anchor not trusted"))
        assertEquals(RealityVerdict.REJECT, a.verdict)
        assertTrue(a.blockers.any { it.contains("anchor not trusted") })
    }

    @Test
    fun `an expired certificate is a hard blocker`() {
        val a = RealityCriteria.evaluate(good(certDaysRemaining = -5))
        assertEquals(RealityVerdict.REJECT, a.verdict)
        assertTrue(a.blockers.any { it.contains("expired") })
    }

    @Test
    fun `an sni the certificate does not cover is a hard blocker`() {
        val a = RealityCriteria.evaluate(
            good(sni = "login.microsoft.com", sans = listOf("www.microsoft.com")))
        assertEquals(RealityVerdict.REJECT, a.verdict)
        assertFalse("sniMatchesSan should be false", a.blockers.isEmpty())
        assertTrue(a.blockers.any { it.contains("SANs") })
    }

    @Test
    fun `a redirecting donor is rejected because it breaks the handshake`() {
        val a = RealityCriteria.evaluate(
            good(httpStatus = 301, redirectLocation = "https://www.microsoft.com/en-us/"))
        assertEquals(RealityVerdict.REJECT, a.verdict)
        assertTrue(a.blockers.any { it.contains("Redirect") })
    }

    @Test
    fun `a cloudflare fronted donor is rejected as an open forwarder risk`() {
        val a = RealityCriteria.evaluate(good(resolvedIp = "104.16.1.1", behindCloudflare = true))
        assertEquals(RealityVerdict.REJECT, a.verdict)
        assertTrue(a.blockers.any { it.contains("port-forwarder") })
    }

    @Test
    fun `missing http2 is a warning, not a blocker`() {
        val a = RealityCriteria.evaluate(good(alpn = "http/1.1"))
        assertEquals(emptyList<String>(), a.blockers)
        assertTrue(a.warnings.any { it.contains("HTTP/2") })
        assertEquals(RealityVerdict.USABLE, a.verdict)
        assertTrue(a.score < RealityCriteria.evaluate(good()).score)
    }

    @Test
    fun `a non-cloudflare cdn is a warning`() {
        val a = RealityCriteria.evaluate(good(serverHeader = "AkamaiGHost", behindCdn = true))
        assertEquals(emptyList<String>(), a.blockers)
        assertTrue(a.warnings.any { it.contains("CDN") })
    }

    @Test
    fun `a certificate about to expire warns`() {
        val a = RealityCriteria.evaluate(good(certDaysRemaining = 12))
        assertTrue(a.warnings.any { it.contains("expires in 12 days") })
    }

    @Test
    fun `a slow donor warns because reality pays the latency per connection`() {
        val a = RealityCriteria.evaluate(good(totalMs = 1400))
        assertTrue(a.warnings.any { it.contains("1400 ms") })
        assertEquals("one warning is a downside, not a disqualification",
            RealityVerdict.USABLE, a.verdict)
    }

    @Test
    fun `several compounding warnings downgrade the donor to risky`() {
        val a = RealityCriteria.evaluate(
            good(alpn = "", totalMs = 1400, serverHeader = "AkamaiGHost", behindCdn = true))
        assertTrue("expected 3 warnings, got ${a.warnings}", a.warnings.size >= 3)
        assertEquals(RealityVerdict.RISKY, a.verdict)
        assertEquals(emptyList<String>(), a.blockers)
    }

    @Test
    fun `a failed probe is rejected outright`() {
        val a = RealityCriteria.evaluate(good(error = "TCP connect refused or blocked"))
        assertEquals(RealityVerdict.REJECT, a.verdict)
        assertEquals(0, a.score)
        assertTrue(a.blockers.any { it.contains("refused") })
    }

    // ── the honesty rule: never blame the donor for the device's limits ─────

    @Test
    fun `an old device that cannot do tls13 does not condemn the donor`() {
        val a = RealityCriteria.evaluate(
            good(tlsVersion = "TLSv1.2", platformTls13Capable = false))
        assertEquals("device limits must not be reported as donor faults",
            emptyList<String>(), a.blockers)
        assertTrue(a.warnings.any { it.contains("Android version cannot negotiate TLS 1.3") })
        assertTrue(a.notes.any { it.contains("re-test from a server") || it.contains("server") })
    }

    @Test
    fun `an old device that cannot negotiate alpn does not warn about http2`() {
        val a = RealityCriteria.evaluate(good(alpn = "", platformAlpnCapable = false))
        assertTrue(a.warnings.none { it.contains("HTTP/2") })
        assertTrue(a.notes.any { it.contains("ALPN cannot be negotiated") })
    }

    @Test
    fun `post-quantum is reported as unverifiable, never guessed`() {
        val a = RealityCriteria.evaluate(good())
        assertTrue("the PQ limitation must always be stated",
            a.notes.any { it.contains("Post-quantum") && it.contains("cannot be detected") })
    }

    // ── CDN fingerprints ───────────────────────────────────────────────────

    @Test
    fun `cdn server headers are recognised case insensitively`() {
        for (h in listOf("cloudflare", "Cloudflare", "AkamaiGHost", "Fastly", "VARnish")) {
            assertTrue("$h should look like a CDN", RealityCriteria.isCdnServerHeader(h))
        }
        for (h in listOf("Kestrel", "nginx", "Apache", "gws", "")) {
            assertFalse("$h should not look like a CDN", RealityCriteria.isCdnServerHeader(h))
        }
    }

    @Test
    fun `cdn response headers are recognised by name`() {
        assertTrue(RealityCriteria.isCdnHeaderName("cf-ray"))
        assertTrue(RealityCriteria.isCdnHeaderName("CF-RAY"))
        assertTrue(RealityCriteria.isCdnHeaderName("X-Amz-Cf-Id"))
        assertTrue(RealityCriteria.isCdnHeaderName("x-served-by"))
        assertFalse(RealityCriteria.isCdnHeaderName("content-type"))
        assertFalse(RealityCriteria.isCdnHeaderName("date"))
    }

    @Test
    fun `derived flags on the probe result agree with the verdict inputs`() {
        val r = good()
        assertTrue(r.reachable)
        assertTrue(r.isTls13)
        assertTrue(r.hasH2)
        assertFalse(r.isRedirect)
        assertFalse(r.certExpired)
        assertFalse(r.certExpiringSoon)
        assertTrue(good(certDaysRemaining = 10).certExpiringSoon)
        assertTrue(good(certDaysRemaining = -1).certExpired)
        assertFalse(good(error = "boom").reachable)
        // "TLSv1.3" must match regardless of case coming from the TLS stack
        assertTrue(good(tlsVersion = "tlsv1.3").isTls13)
    }

    @Test
    fun `score never rewards speed when a blocker is present`() {
        val fast = RealityCriteria.evaluate(good(tlsVersion = "TLSv1.2", totalMs = 10))
        val slowGood = RealityCriteria.evaluate(good(totalMs = 800))
        assertEquals(0, fast.score)
        assertTrue(slowGood.score > fast.score)
    }
}
