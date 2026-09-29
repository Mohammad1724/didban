package org.didban.monitor

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A whole DPI run, end to end, with no sockets.
 *
 * The engine's decision tree is covered by `DpiEngineTest`; these tests cover
 * the half that was missing — collecting the input — and, more importantly,
 * the cases a user actually arrives with, where a wrong answer is worse than
 * no answer:
 *
 *  - a dead address must not be reported as a filtered *port*;
 *  - a dead port on a live address must not be reported as "no filtering";
 *  - a run on a network that cannot see filtering must say so.
 */
private fun answered(latencyMs: Long = 120) = HelloReply(HelloOutcome.SERVER_HELLO, latencyMs = latencyMs)
private fun reset(latencyMs: Long = 120) = HelloReply(HelloOutcome.RESET, latencyMs = latencyMs)
private fun open(port: Int, latencyMs: Long = 120) =
    PortProbe(port, connectOk = true, reset = false, refused = false, latencyMs = latencyMs)

/** Unreachable, and killed rather than refused — "refused" would count as alive. */
private fun dead(port: Int, latencyMs: Long = 3000) =
    PortProbe(port, connectOk = false, reset = true, refused = false, latencyMs = latencyMs)

class DpiRunTest {

    private val host = "45.74.158.186"

    // ── fakes ────────────────────────────────────────────────────────────────

    private class FakeTransport(
        private val connect: (String, Int) -> PortProbe = { _, port -> open(port) },
        private val reply: (String, Int, Int) -> HelloReply = { _, _, _ -> answered() }
    ) : DpiTransport {
        val exchanges = mutableListOf<String>()
        private val seen = HashMap<String, Int>()
        override fun connect(host: String, port: Int, timeoutMs: Int): PortProbe = connect(host, port)
        override fun exchange(host: String, port: Int, hello: ByteArray, timeoutMs: Int): HelloReply {
            val key = "$host:$port"
            exchanges += key
            val index = seen[key] ?: 0
            seen[key] = index + 1
            return reply(host, port, index)
        }
    }

    /**
     * Controls by default behave like Iran's operators: the anchor answers and
     * the known-filtered target does not, so a run is trusted unless a test
     * says otherwise.
     */
    private fun replies(fingerprint: (TlsFingerprint) -> HelloReply = { answered() }): (String, Int, Int) -> HelloReply =
        { target, _, index ->
            when (target) {
                DpiRun.ANCHOR_HOST -> answered()
                "www.youtube.com" -> reset()
                else -> fingerprint(TlsFingerprint.values().getOrElse(index) { TlsFingerprint.CHROME_MODERN })
            }
        }

    private fun config(port: Int = 2887) = DpiRun.Config(host = host, port = port, sni = "www.microsoft.com")

    private fun DpiTransport.run(
        config: DpiRun.Config = config(),
        remote: suspend (String, Int) -> RemoteVantage? = { _, _ -> null },
        postHandshake: suspend (String, Int, String?) -> PostHandshakeResult? = { _, _, _ -> null }
    ): DpiAssessment = runBlocking { DpiRun.run(config, this@run, remote, postHandshake) }

    // ── the cases that matter ────────────────────────────────────────────────

    @Test
    fun `a dead address is a filtered address, not a filtered port`() {
        val transport = FakeTransport(connect = { _, port -> dead(port) }, reply = replies())
        val assessment = transport.run(remote = { _, _ -> RemoteVantage(nodesReached = 3, nodesOpen = 3) })

        assertEquals(DpiConclusion.FILTERED_ADDRESS, assessment.verdict)
        assertEquals(Confidence.HIGH, assessment.confidence)
        // Fingerprints are not probed on a port that never answered: every
        // hello would "fail" and the engine would call it a filtered port.
        assertTrue("nothing should handshake with a dead port", transport.exchanges.none { it.startsWith(host) })
        assertTrue("the skipped differential must be declared", assessment.limitations.any { it.contains("fingerprint", ignoreCase = true) })
    }

    @Test
    fun `a dead port on a live address is a filtered port, not a clean run`() {
        val transport = FakeTransport(
            connect = { _, port -> if (port == 2887) dead(port) else open(port) },
            reply = replies()
        )
        val assessment = transport.run()

        assertEquals(DpiConclusion.FILTERED_PORT, assessment.verdict)
        assertTrue(assessment.summary.contains("2887"))
    }

    @Test
    fun `a filtered port is confirmed at high confidence when remote nodes reach it`() {
        val transport = FakeTransport(
            connect = { _, port -> if (port == 2887) dead(port) else open(port) },
            reply = replies()
        )
        val assessment = transport.run(remote = { _, _ -> RemoteVantage(nodesReached = 4, nodesOpen = 4) })

        assertEquals(DpiConclusion.FILTERED_PORT, assessment.verdict)
        assertEquals(Confidence.HIGH, assessment.confidence)
    }

    @Test
    fun `only the modern Chrome hello answered`() {
        val transport = FakeTransport(reply = replies { fp ->
            if (fp == TlsFingerprint.CHROME_MODERN) answered() else reset()
        })
        val assessment = transport.run()

        assertEquals(DpiConclusion.FILTERED_MODERN_FINGERPRINT_REQUIRED, assessment.verdict)
        assertEquals(Confidence.HIGH, assessment.confidence)
        assertTrue(assessment.nextSteps.any { it.contains("Chrome", ignoreCase = true) })
    }

    @Test
    fun `browser hellos answered while the Go stack is reset`() {
        val transport = FakeTransport(reply = replies { fp ->
            if (fp == TlsFingerprint.GO_JAVA) reset() else answered()
        })
        val assessment = transport.run()

        assertEquals(DpiConclusion.FILTERED_FINGERPRINT, assessment.verdict)
    }

    @Test
    fun `a connection that only dies once data moves`() {
        val transport = FakeTransport(reply = replies())
        val assessment = transport.run(
            postHandshake = { _, _, _ ->
                PostHandshakeResult(handshakeOk = true, handshakeMs = 120, bytesSent = 8192, dataReset = true)
            }
        )

        assertEquals(DpiConclusion.FILTERED_AFTER_HANDSHAKE, assessment.verdict)
        assertEquals(Confidence.HIGH, assessment.confidence)
    }

    @Test
    fun `an answer that beats the reference anchor is a middlebox`() {
        val transport = FakeTransport(
            connect = { _, port -> if (port == 2887) open(port, latencyMs = 4) else open(port, latencyMs = 120) },
            reply = replies()
        )
        val assessment = transport.run()

        assertEquals(DpiConclusion.MIDDLEBOX_SUSPECTED, assessment.verdict)
        assertTrue(assessment.summary.contains("4ms"))
    }

    @Test
    fun `a network that cannot even reach the control says so instead of judging`() {
        val transport = FakeTransport(reply = { _, _, _ -> reset() })
        val assessment = transport.run()

        assertEquals(DpiConclusion.INCONCLUSIVE, assessment.verdict)
        assertEquals(Confidence.NONE, assessment.confidence)
    }

    @Test
    fun `a control that should be filtered answers, so the run is not trusted`() {
        val transport = FakeTransport(reply = { target, _, index ->
            if (target == "www.youtube.com") answered() else replies()(target, 443, index)
        })
        val assessment = transport.run()

        assertEquals(DpiConclusion.INCONCLUSIVE, assessment.verdict)
        assertEquals(Confidence.LOW, assessment.confidence)
        assertTrue(assessment.nextSteps.any { it.contains("VPN", ignoreCase = true) })
    }

    @Test
    fun `remote nodes that cannot reach it either mean the service is down`() {
        val transport = FakeTransport(connect = { _, port -> dead(port) }, reply = replies())
        val assessment = transport.run(remote = { _, _ -> RemoteVantage(nodesReached = 3, nodesOpen = 0) })

        assertEquals(DpiConclusion.SERVICE_DOWN, assessment.verdict)
        assertEquals(Confidence.HIGH, assessment.confidence)
    }

    @Test
    fun `a clean run still says what it did not check`() {
        val transport = FakeTransport(reply = replies())
        val assessment = transport.run(
            postHandshake = { _, _, _ ->
                PostHandshakeResult(
                    handshakeOk = true, handshakeMs = 120, bytesSent = 8192, bytesReceived = 2048,
                    idleReset = false, dataReset = false
                )
            }
        )

        assertEquals(DpiConclusion.NO_FILTERING_SEEN, assessment.verdict)
        assertEquals(Confidence.MEDIUM, assessment.confidence)
        assertTrue(
            "a clean result must never read as proof",
            assessment.limitations.any { it.contains("never 'provably unfiltered'") }
        )
        assertTrue(assessment.evidence.any { it.label == "handshake" })
        assertTrue(assessment.evidence.any { it.label == "payload" })
    }
}
