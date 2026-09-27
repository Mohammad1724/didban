package org.didban.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

/**
 * Covers the parts of the DPI engine that must never regress:
 * the ClientHello forge (structure and per-fingerprint shape), the reply
 * parser, and — most importantly — the verdict logic, because a verdict that
 * says "healthy" for a filtered target is worse than no verdict at all.
 */
class DpiEngineTest {

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun hello(fp: TlsFingerprint, sni: String? = "example.com"): ByteArray =
        ClientHelloForge.build(sni, fp, SecureRandom(ByteArray(1) { 7 }))

    private fun ok(fp: TlsFingerprint) = FingerprintProbe(fp, HelloReply(HelloOutcome.SERVER_HELLO))
    private fun reset(fp: TlsFingerprint) = FingerprintProbe(fp, HelloReply(HelloOutcome.RESET))

    private fun reply(outcome: HelloOutcome, alert: Int = -1) = HelloReply(outcome, alertDescription = alert)

    /** A minimal but structurally real ServerHello. */
    private fun serverHelloBytes(): ByteArray {
        val body = ByteArray(2 + 32 + 1 + 32 + 2 + 1)
        body[0] = 0x03; body[1] = 0x03
        body[34] = 32 // session id length
        body[67] = 0x13; body[68] = 0x01 // TLS_AES_128_GCM_SHA256
        val hs = byteArrayOf(0x02, 0x00.toByte(), 0x00.toByte(), body.size.toByte()) + body
        return byteArrayOf(0x16, 0x03, 0x03, (hs.size shr 8).toByte(), hs.size.toByte()) + hs
    }

    // ── ClientHello forge ────────────────────────────────────────────────────

    @Test
    fun `forged hello carries a valid TLS record header`() {
        val bytes = hello(TlsFingerprint.CHROME_MODERN)
        assertEquals(0x16.toByte(), bytes[0])
        assertEquals(0x03.toByte(), bytes[1])
        assertEquals(0x01.toByte(), bytes[2])
        val recordLength = ((bytes[3].toInt() and 0xff) shl 8) or (bytes[4].toInt() and 0xff)
        assertEquals(bytes.size - 5, recordLength)
        assertEquals(0x01.toByte(), bytes[5]) // handshake type: client_hello
    }

    @Test
    fun `forged hello declares TLS 1-2 in the legacy version field`() {
        val bytes = hello(TlsFingerprint.FIREFOX)
        assertEquals(0x03.toByte(), bytes[9])
        assertEquals(0x03.toByte(), bytes[10])
    }

    @Test
    fun `session id length follows the fingerprint`() {
        assertEquals(32, sessionIdLength(hello(TlsFingerprint.REALITY_LIKE)))
        assertEquals(32, sessionIdLength(hello(TlsFingerprint.CHROME_LEGACY)))
        assertEquals(0, sessionIdLength(hello(TlsFingerprint.GO_JAVA)))
        assertEquals(0, sessionIdLength(hello(TlsFingerprint.CHROME_MODERN)))
    }

    @Test
    fun `modern chrome offers the post-quantum group and legacy chrome does not`() {
        val modern = hello(TlsFingerprint.CHROME_MODERN)
        val legacy = hello(TlsFingerprint.CHROME_LEGACY)
        assertTrue(modern.containsGroup(0x11ec))
        assertFalse(legacy.containsGroup(0x11ec))
    }

    @Test
    fun `every fingerprint produces a distinct hello`() {
        val shapes = TlsFingerprint.values().map { String(hello(it)) }.toSet()
        assertEquals(TlsFingerprint.values().size, shapes.size)
    }

    @Test
    fun `sni is carried when supplied and omitted when not`() {
        val withSni = hello(TlsFingerprint.CHROME_MODERN, "static.cdn.prismic.io")
        assertTrue(withSni.containsAscii("static.cdn.prismic.io"))
        val without = hello(TlsFingerprint.CHROME_MODERN, null)
        assertFalse(without.containsAscii("static.cdn.prismic.io"))
    }

    /**
     * Regression guard for two bugs found against live servers: server_name
     * was missing its ServerNameList length, and supported_versions carried the
     * version COUNT instead of the byte length, leaving two trailing bytes that
     * made servers fail on the following extension (decode_error). A hello that
     * merely "looks structured" passes without this walk.
     */
    @Test
    fun `every extension is fully consumed with no trailing bytes`() {
        for (fp in TlsFingerprint.values()) {
            val hello = hello(fp)
            for ((type, body) in extensionsOf(hello)) {
                when (type) {
                    0x0000 -> {
                        val listLen = u16(body, 0)
                        assertEquals("$fp server_name list length", body.size - 2, listLen)
                        assertEquals("entry name_type", 0, body[2].toInt())
                        val hostLen = u16(body, 3)
                        assertEquals("host length", body.size - 5, hostLen)
                    }
                    0x002b -> {
                        val bytes = body[0].toInt() and 0xff
                        assertEquals("$fp supported_versions byte length", body.size - 1, bytes)
                        assertEquals("two versions advertised", 2, bytes / 2)
                        assertEquals(0x0304, u16(body, 1))
                        assertEquals(0x0303, u16(body, 3))
                    }
                    0x0033 -> {
                        assertEquals("$fp key_share entries length", body.size - 2, u16(body, 0))
                        var q = 2
                        while (q < body.size) {
                            val keyLen = u16(body, q + 2)
                            q += 4 + keyLen
                        }
                        assertEquals("$fp key_share walk", body.size, q)
                    }
                    0x0010 -> {
                        val listLen = u16(body, 0)
                        assertEquals("$fp alpn list length", body.size - 2, listLen)
                        var q = 2
                        while (q < body.size) {
                            q += 1 + (body[q].toInt() and 0xff)
                        }
                        assertEquals("$fp alpn walk", body.size, q)
                    }
                    0x002d -> {
                        val modes = body[0].toInt() and 0xff
                        assertEquals("$fp psk modes length", body.size - 1, modes)
                        assertEquals("psk_dhe_ke", 1, body[1].toInt() and 0xff)
                    }
                    0x000a -> assertEquals("$fp supported_groups length", body.size - 2, u16(body, 0))
                    0x000d -> assertEquals("$fp signature_algorithms length", body.size - 2, u16(body, 0))
                    0x000b -> assertEquals("$fp ec_point_formats length", body.size - 1, body[0].toInt() and 0xff)
                }
            }
        }
    }

    @Test
    fun `certificate compression algorithms are encoded as two byte values`() {
        // Regression: "length 1 + 0x02" left an odd byte and servers rejected
        // the hello outright, which read as "filtered" for every Chrome shape.
        for (fp in TlsFingerprint.values()) {
            val body = extensionsOf(hello(fp)).firstOrNull { it.first == 0x001b }?.second ?: continue
            val declared = body[0].toInt() and 0xff
            assertEquals("$fp compress_certificate length", body.size - 1, declared)
            assertEquals("$fp algorithm count is whole", 0, declared % 2)
        }
    }

    @Test
    fun `the post-quantum group is advertised but never given a bogus key share`() {
        // We cannot produce a real Kyber768 key, and a random blob in key_share
        // makes PQ-preferring servers reject the hello (decode_error). The group
        // is still advertised in supported_groups, which is what JA3/JA4 hash.
        val hello = hello(TlsFingerprint.CHROME_MODERN)
        val exts = extensionsOf(hello)
        val groups = exts.first { it.first == 0x000a }.second
        val groupList = (0 until (u16(groups, 0) / 2)).map { u16(groups, 2 + it * 2) }
        assertTrue("0x11ec must be advertised", groupList.contains(0x11ec))

        val shares = exts.first { it.first == 0x0033 }.second
        var q = 2
        val shareGroups = mutableListOf<Int>()
        while (q < shares.size) {
            shareGroups += u16(shares, q)
            q += 4 + u16(shares, q + 2)
        }
        assertFalse("no Kyber key share may be offered", shareGroups.contains(0x11ec))
        assertTrue("a usable X25519 share is offered", shareGroups.contains(0x001d))
    }

    @Test
    fun `chrome greases and go does not`() {
        assertTrue(hello(TlsFingerprint.CHROME_MODERN).containsGrease())
        assertFalse(hello(TlsFingerprint.GO_JAVA).containsGrease())
    }

    // ── Reply parsing ────────────────────────────────────────────────────────

    @Test
    fun `a server hello is parsed as accepted`() {
        val r = TlsRecordReader.parse(serverHelloBytes(), 120)
        assertEquals(HelloOutcome.SERVER_HELLO, r.outcome)
        assertTrue(r.accepted)
        assertEquals(0x1301, r.cipherSuite)
    }

    @Test
    fun `alert 112 is read as unrecognized name`() {
        val alert = byteArrayOf(0x15, 0x03, 0x03, 0x00, 0x02, 0x02, 112.toByte())
        assertEquals(HelloOutcome.ALERT_UNRECOGNIZED_NAME, TlsRecordReader.parse(alert).outcome)
    }

    @Test
    fun `alert 40 is read as handshake failure`() {
        val alert = byteArrayOf(0x15, 0x03, 0x03, 0x00, 0x02, 0x02, 40)
        assertEquals(HelloOutcome.ALERT_HANDSHAKE_FAILURE, TlsRecordReader.parse(alert).outcome)
    }

    @Test
    fun `an empty read is a closed connection`() {
        assertEquals(HelloOutcome.CLOSED, TlsRecordReader.parse(ByteArray(0)).outcome)
    }

    @Test
    fun `reset messages are recognised`() {
        assertTrue(PostHandshakeProbe.isResetMessage("Connection reset by peer"))
        assertTrue(PostHandshakeProbe.isResetMessage("Broken pipe"))
        assertFalse(PostHandshakeProbe.isResetMessage("Read timed out"))
    }

    // ── Fingerprint differential ─────────────────────────────────────────────

    @Test
    fun `all hellos answered means no fingerprint preference`() {
        val probes = TlsFingerprint.values().map { ok(it) }
        assertEquals(DifferentialOutcome.ALL_ACCEPTED, FingerprintDifferential.assess(probes))
    }

    @Test
    fun `no hello answered means the port is being killed`() {
        val probes = TlsFingerprint.values().map { reset(it) }
        assertEquals(DifferentialOutcome.ALL_BLOCKED, FingerprintDifferential.assess(probes))
    }

    @Test
    fun `modern chrome passing where legacy chrome fails is flagged`() {
        val probes = listOf(
            ok(TlsFingerprint.CHROME_MODERN),
            ok(TlsFingerprint.REALITY_LIKE),
            reset(TlsFingerprint.CHROME_LEGACY),
            reset(TlsFingerprint.FIREFOX),
            reset(TlsFingerprint.GO_JAVA)
        )
        assertEquals(DifferentialOutcome.MODERN_CHROME_ONLY, FingerprintDifferential.assess(probes))
    }

    @Test
    fun `go style hello being rejected while browsers pass is flagged`() {
        val probes = listOf(
            ok(TlsFingerprint.CHROME_MODERN),
            ok(TlsFingerprint.FIREFOX),
            ok(TlsFingerprint.SAFARI),
            reset(TlsFingerprint.GO_JAVA)
        )
        assertEquals(DifferentialOutcome.NON_BROWSER_BLOCKED, FingerprintDifferential.assess(probes))
    }

    @Test
    fun `an empty differential is not run`() {
        assertEquals(DifferentialOutcome.NOT_RUN, FingerprintDifferential.assess(emptyList()))
    }

    // ── Port matrix ──────────────────────────────────────────────────────────

    @Test
    fun `every port dead means the address is unreachable`() {
        val probes = listOf(
            PortProbe(22, connectOk = false, reset = true, refused = false, latencyMs = -1),
            PortProbe(2887, connectOk = false, reset = false, refused = false, latencyMs = -1)
        )
        assertEquals(PortMatrixOutcome.ALL_DEAD, PortMatrix.assess(probes))
    }

    @Test
    fun `a refused port still proves the address is alive`() {
        val probes = listOf(
            PortProbe(22, connectOk = false, reset = false, refused = true, latencyMs = 40),
            PortProbe(2887, connectOk = true, reset = false, refused = false, latencyMs = 40)
        )
        assertEquals(PortMatrixOutcome.ALL_OPEN, PortMatrix.assess(probes))
    }

    @Test
    fun `mixed results mean selective filtering`() {
        val probes = listOf(
            PortProbe(2187, connectOk = true, reset = false, refused = false, latencyMs = 5),
            PortProbe(2887, connectOk = false, reset = true, refused = false, latencyMs = -1)
        )
        assertEquals(PortMatrixOutcome.SELECTIVE, PortMatrix.assess(probes))
    }

    // ── Controls ─────────────────────────────────────────────────────────────

    @Test
    fun `a filtered control that sails through invalidates the run`() {
        val negative = ControlResult("youtube.com", expectFiltered = true, reply = reply(HelloOutcome.SERVER_HELLO))
        val positive = ControlResult("cloudflare.com", expectFiltered = false, reply = reply(HelloOutcome.SERVER_HELLO))
        assertEquals(ValidationState.BLIND_NETWORK, RunValidator.validate(positive, negative))
    }

    @Test
    fun `a failing always-allowed control means there is no usable path`() {
        val negative = ControlResult("youtube.com", expectFiltered = true, reply = reply(HelloOutcome.RESET))
        val positive = ControlResult("cloudflare.com", expectFiltered = false, reply = reply(HelloOutcome.TIMEOUT))
        assertEquals(ValidationState.BROKEN_NETWORK, RunValidator.validate(positive, negative))
    }

    @Test
    fun `controls behaving normally validate the run`() {
        val negative = ControlResult("youtube.com", expectFiltered = true, reply = reply(HelloOutcome.RESET))
        val positive = ControlResult("cloudflare.com", expectFiltered = false, reply = reply(HelloOutcome.SERVER_HELLO))
        assertEquals(ValidationState.VALID, RunValidator.validate(positive, negative))
    }

    // ── Remote vantage ───────────────────────────────────────────────────────

    @Test
    fun `remote nodes reaching a target we cannot means operator filtering`() {
        val remote = RemoteVantage(nodesReached = 12, nodesOpen = 11)
        assertEquals(VantageOutcome.OPERATOR_FILTERING, RemoteVantageCompare.compare(false, remote))
    }

    @Test
    fun `nobody reaching the target means the service is down`() {
        val remote = RemoteVantage(nodesReached = 12, nodesOpen = 0)
        assertEquals(VantageOutcome.SERVER_SIDE, RemoteVantageCompare.compare(false, remote))
    }

    // ── Verdicts ─────────────────────────────────────────────────────────────

    @Test
    fun `a handshake that survives but dies on data is post-handshake filtering`() {
        val assessment = DpiAssessmentEngine.assess(
            DpiRunInput(
                host = "198.51.100.7",
                port = 2887,
                sni = "static.cdn.prismic.io",
                postHandshake = PostHandshakeResult(
                    handshakeOk = true, handshakeMs = 130,
                    idleSurvivedMs = 4000, idleReset = false,
                    bytesSent = 8192, bytesReceived = 0,
                    dataReset = true, dataSurvivedMs = 220
                )
            )
        )
        assertEquals(DpiConclusion.FILTERED_AFTER_HANDSHAKE, assessment.verdict)
        assertEquals(Confidence.HIGH, assessment.confidence)
        assertTrue(assessment.nextSteps.any { it.contains("XHTTP") })
    }

    @Test
    fun `only modern chrome passing produces a fingerprint fix recommendation`() {
        val assessment = DpiAssessmentEngine.assess(
            DpiRunInput(
                host = "198.51.100.7",
                port = 2887,
                differential = DifferentialOutcome.MODERN_CHROME_ONLY
            )
        )
        assertEquals(DpiConclusion.FILTERED_MODERN_FINGERPRINT_REQUIRED, assessment.verdict)
        assertTrue(assessment.evidence.any { it.label == "fingerprint" })
    }

    @Test
    fun `remote nodes reaching an address that is dead locally is address filtering`() {
        val assessment = DpiAssessmentEngine.assess(
            DpiRunInput(
                host = "198.51.100.7",
                port = 22,
                matrixOutcome = PortMatrixOutcome.ALL_DEAD,
                portMatrix = listOf(PortProbe(22, false, reset = true, refused = false, latencyMs = -1)),
                remote = RemoteVantage(nodesReached = 14, nodesOpen = 13)
            )
        )
        assertEquals(DpiConclusion.FILTERED_ADDRESS, assessment.verdict)
        assertEquals(Confidence.HIGH, assessment.confidence)
    }

    @Test
    fun `a service nobody can reach is reported as down, not filtered`() {
        val assessment = DpiAssessmentEngine.assess(
            DpiRunInput(
                host = "198.51.100.7",
                port = 2887,
                matrixOutcome = PortMatrixOutcome.ALL_DEAD,
                remote = RemoteVantage(nodesReached = 14, nodesOpen = 0)
            )
        )
        assertEquals(DpiConclusion.SERVICE_DOWN, assessment.verdict)
    }

    @Test
    fun `a clean run never claims absolute safety`() {
        val assessment = DpiAssessmentEngine.assess(
            DpiRunInput(
                host = "198.51.100.7",
                port = 2887,
                sni = "www.pleadcourt.org",
                differential = DifferentialOutcome.ALL_ACCEPTED,
                postHandshake = PostHandshakeResult(
                    handshakeOk = true, handshakeMs = 120,
                    idleSurvivedMs = 4000, idleReset = false,
                    bytesSent = 8192, bytesReceived = 96,
                    dataReset = false, dataSurvivedMs = 6000
                )
            )
        )
        assertEquals(DpiConclusion.NO_FILTERING_SEEN, assessment.verdict)
        assertTrue(assessment.limitations.any { it.contains("never") })
        assertTrue(assessment.nextSteps.any { it.contains("transport") })
    }

    @Test
    fun `a run measured through a vpn says so`() {
        val assessment = DpiAssessmentEngine.assess(
            DpiRunInput(host = "198.51.100.7", port = 2887, vpnActive = true)
        )
        assertTrue(assessment.limitations.any { it.contains("VPN") })
    }

    @Test
    fun `missing probes are listed as limitations`() {
        val assessment = DpiAssessmentEngine.assess(DpiRunInput(host = "198.51.100.7", port = 2887))
        assertTrue(assessment.limitations.any { it.contains("fingerprint") })
        assertTrue(assessment.limitations.any { it.contains("post-handshake") })
    }

    // ── Byte helpers ─────────────────────────────────────────────────────────

    /** Walks the extension block the way a server would: type -> length -> body. */
    private fun extensionsOf(hello: ByteArray): List<Pair<Int, ByteArray>> {
        var p = 9 // record header (5) + handshake header (4)
        p += 2 // legacy_version
        p += 32 // client_random
        p += 1 + (hello[p].toInt() and 0xff) // session id
        p += 2 + u16(hello, p) // cipher suites
        p += 1 + (hello[p].toInt() and 0xff) // compression methods
        val end = p + 2 + u16(hello, p)
        p += 2
        val out = mutableListOf<Pair<Int, ByteArray>>()
        while (p < end) {
            val type = u16(hello, p)
            val len = u16(hello, p + 2)
            out += type to hello.copyOfRange(p + 4, p + 4 + len)
            p += 4 + len
        }
        assertEquals("extension block must end exactly at the hello end", hello.size, p)
        return out
    }

    private fun u16(bytes: ByteArray, at: Int): Int =
        ((bytes[at].toInt() and 0xff) shl 8) or (bytes[at + 1].toInt() and 0xff)

    private fun sessionIdLength(hello: ByteArray): Int {
        val sidIndex = 11 + 32 // legacy_version(2) + client_random(32) after the 9-byte prefix
        return hello[sidIndex].toInt() and 0xff
    }

    private fun ByteArray.containsGroup(group: Int): Boolean {
        val hi = (group shr 8) and 0xff
        val lo = group and 0xff
        for (i in 0..size - 2) {
            if ((this[i].toInt() and 0xff) == hi && (this[i + 1].toInt() and 0xff) == lo) return true
        }
        return false
    }

    private fun ByteArray.containsGrease(): Boolean {
        val values = intArrayOf(0x0a0a, 0x1a1a, 0x2a2a, 0x3a3a, 0x4a4a, 0x5a5a, 0x6a6a, 0x7a7a,
            0x8a8a, 0x9a9a, 0xaaaa, 0xbaba, 0xcaca, 0xdada, 0xeaea, 0xfafa)
        return values.any { g ->
            val hi = (g shr 8) and 0xff
            val lo = g and 0xff
            (0..size - 2).any { i -> (this[i].toInt() and 0xff) == hi && (this[i + 1].toInt() and 0xff) == lo }
        }
    }

    private fun ByteArray.containsAscii(value: String): Boolean =
        String(this, Charsets.US_ASCII).contains(value)
}
