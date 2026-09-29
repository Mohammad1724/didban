package org.didban.monitor

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Runs one DPI diagnosis end to end.
 *
 * `DpiAssessmentEngine` only *interprets* a [DpiRunInput] — it never touches
 * the network. This object is the missing half: it collects everything that
 * input needs, in the order the engine's decision tree expects:
 *
 *  1. a reference anchor (what a real round trip to the outside costs here);
 *  2. TCP reachability of the target port;
 *  3. a port matrix on the same address, to tell "address filtered" from
 *     "port filtered";
 *  4. a ClientHello differential (seven fingerprints);
 *  5. a post-handshake probe (idle survival, then real payload);
 *  6. positive and negative controls, so a run whose network cannot see
 *     filtering is not trusted;
 *  7. an optional remote vantage (Check-Host-style nodes outside the country).
 *
 * Two deliberate choices, both about not over-reading a dead port:
 *
 *  - The fingerprint differential runs **only if TCP reached the target
 *    port**. On a port that refuses connections every hello "fails", which
 *    the engine would read as `ALL_BLOCKED` and report as a filtered port —
 *    even when the whole address is dead. Skipping it leaves the differential
 *    `NOT_RUN`, the matrix speaks for itself, and the engine reports
 *    `FILTERED_ADDRESS`. The limitation is stated in the assessment either
 *    way.
 *  - The post-handshake probe is skipped for the same reason: it would sit
 *    through its timeouts to prove what TCP already said.
 *
 * Everything that talks to the network is injected ([DpiTransport],
 * [remote], [postHandshake]), so a whole run is testable without sockets.
 */
object DpiRun {

    /**
     * A control target used to sanity-check the network this run is on.
     *
     * @param expectFiltered true for a target this operator is expected to
     *   block: if it sails through, the probe is on a path that is not
     *   applying filtering (a VPN, say) and the run is not trustworthy.
     */
    data class ControlTarget(val host: String, val port: Int, val expectFiltered: Boolean)

    class Config(
        val host: String,
        val port: Int,
        val sni: String? = null,
        val vpnActive: Boolean = false,
        /** Ports probed on the same address; the target port is always added. */
        val matrixPorts: List<Int> = DEFAULT_MATRIX_PORTS,
        /** A host outside the country, used to price a real round trip. */
        val anchor: ControlTarget = ControlTarget(ANCHOR_HOST, 443, expectFiltered = false),
        val controls: List<ControlTarget> = defaultControls(),
        val connectTimeoutMs: Int = 4000,
        val fingerprintTimeoutMs: Int = 4000
    ) {
        val normalizedSni: String? get() = sni?.takeIf { it.isNotBlank() }
    }

    const val ANCHOR_HOST = "1.1.1.1"

    /**
     * Ports that a tunnel service is realistically moved to. Enough to say
     * "this port" versus "this address" without turning a diagnosis into a
     * port scan: three probes plus the target port.
     */
    val DEFAULT_MATRIX_PORTS: List<Int> = listOf(443, 80, 8443)

    /**
     * The known-blocked control is what makes a run honest: if a target that
     * is filtered in Iran answers normally here, the probe is not on the
     * operator's path and every clean result is meaningless. It is a default,
     * not a constant of nature — pass your own list if the assumption stops
     * holding.
     */
    fun defaultControls(): List<ControlTarget> = listOf(
        ControlTarget(ANCHOR_HOST, 443, expectFiltered = false),
        ControlTarget("www.youtube.com", 443, expectFiltered = true)
    )

    suspend fun run(
        config: Config,
        transport: DpiTransport = SocketDpiTransport,
        /** Nodes outside the country; null (or throwing) means "not run". */
        remote: suspend (String, Int) -> RemoteVantage? = { _, _ -> null },
        postHandshake: suspend (String, Int, String?) -> PostHandshakeResult? =
            { host, port, sni -> PostHandshakeProbe.run(host, port, sni) }
    ): DpiAssessment {
        val sni = config.normalizedSni

        val anchorProbe = transport.connect(config.anchor.host, config.anchor.port, config.connectTimeoutMs)
        val anchorMs = if (anchorProbe.connectOk) anchorProbe.latencyMs else -1

        val targetProbe = transport.connect(config.host, config.port, config.connectTimeoutMs)
        val targetMs = if (targetProbe.connectOk) targetProbe.latencyMs else -1
        val reachable = targetProbe.connectOk

        val ports = (config.matrixPorts + config.port).distinct()
        val matrix = ports.map { port ->
            if (port == config.port) targetProbe else transport.connect(config.host, port, config.connectTimeoutMs)
        }

        // See the class doc: no point fingerprinting a port that never answered.
        val probes = if (reachable) {
            TlsFingerprint.values().map { fingerprint ->
                val hello = ClientHelloForge.build(sni, fingerprint)
                FingerprintProbe(fingerprint, transport.exchange(config.host, config.port, hello, config.fingerprintTimeoutMs))
            }
        } else {
            emptyList()
        }

        val post = if (reachable) {
            runCatching { postHandshake(config.host, config.port, sni) }.getOrNull()
        } else {
            null
        }

        val controlReplies = config.controls.map { target ->
            target to transport.exchange(
                target.host,
                target.port,
                ClientHelloForge.build(null, TlsFingerprint.CHROME_MODERN),
                config.connectTimeoutMs
            )
        }
        val positive = controlReplies.firstOrNull { !it.first.expectFiltered }
            ?.let { (target, reply) -> ControlResult("${target.host}:${target.port}", false, reply) }
        val negative = controlReplies.firstOrNull { it.first.expectFiltered }
            ?.let { (target, reply) -> ControlResult("${target.host}:${target.port}", true, reply) }

        val vantage = runCatching { remote(config.host, config.port) }.getOrNull()

        return DpiAssessmentEngine.assess(
            DpiRunInput(
                host = config.host,
                port = config.port,
                sni = sni,
                portMatrix = matrix,
                matrixOutcome = PortMatrix.assess(matrix),
                fingerprints = probes,
                differential = FingerprintDifferential.assess(probes),
                postHandshake = post,
                positiveControl = positive,
                negativeControl = negative,
                remote = vantage,
                vpnActive = config.vpnActive,
                referenceAnchorMs = anchorMs,
                targetLatencyMs = targetMs
            )
        )
    }
}

/** The two network primitives a run needs, so a run is testable without sockets. */
interface DpiTransport {
    fun connect(host: String, port: Int, timeoutMs: Int): PortProbe
    fun exchange(host: String, port: Int, hello: ByteArray, timeoutMs: Int): HelloReply
}

/** Real transport. Blocking: callers must run it off the main thread. */
object SocketDpiTransport : DpiTransport {

    override fun connect(host: String, port: Int, timeoutMs: Int): PortProbe {
        val t0 = System.currentTimeMillis()
        val latency = { System.currentTimeMillis() - t0 }
        return try {
            Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
            PortProbe(port, connectOk = true, reset = false, refused = false, latencyMs = latency())
        } catch (io: IOException) {
            val message = io.message.orEmpty()
            PortProbe(
                port = port,
                connectOk = false,
                reset = message.contains("reset", ignoreCase = true),
                refused = message.contains("refused", ignoreCase = true),
                latencyMs = latency()
            )
        }
    }

    override fun exchange(host: String, port: Int, hello: ByteArray, timeoutMs: Int): HelloReply =
        SocketHelloTransport.exchange(host, port, hello, timeoutMs)
}
