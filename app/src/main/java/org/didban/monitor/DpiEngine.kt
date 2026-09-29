package org.didban.monitor

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

// ─────────────────────────────────────────────────────────────────────────────
// DPI / censorship forensics engine.
//
// Why this exists: the previous diagnosis (CensorshipTester.diagnoseDeep) could
// report "healthy" for a target that a real client could not use at all. Three
// structural reasons, all of them fixed here:
//
//   1. TLS was only attempted on a hardcoded port whitelist
//      (443, 8443, 2053, 2083, 2087, 2096, 9443). A REALITY service on 2887 or
//      31049 never got a handshake at all, so the tool reported "healthy" from
//      a bare connect(). Probes here are port-agnostic: if you ask for TLS on
//      a port, TLS is attempted on that port.
//
//   2. Not a single payload byte was ever exchanged after the handshake. The
//      dominant Iranian mobile-operator pattern is "accept then blackhole" or
//      "accept then RST after data": the on-path box completes the TCP
//      handshake (so connect() succeeds) and then kills the flow once real
//      bytes move. PostHandshakeProbe below measures exactly that window.
//
//   3. The probe's TLS fingerprint was Conscrypt's, not the client's, and the
//      run had no controls — so it could never tell "no filtering" apart from
//      "I am blind on this network". ClientHelloForge sends hellos with a
//      chosen fingerprint, and RunValidator refuses to trust a run whose
//      negative control came back clean.
//
// Everything in this file is plain JVM code (no Android APIs above API 26 and
// no kotlinx.coroutines) so it can be compiled and unit-tested on the JVM.
// Callers wrap the blocking calls in Dispatchers.IO.
// ─────────────────────────────────────────────────────────────────────────────

// ── Fingerprints ─────────────────────────────────────────────────────────────

/**
 * ClientHello personalities. These are faithful at the level DPI boxes
 * fingerprint on (cipher-suite list and order, offered groups, GREASE,
 * extension set and order, session-id length) — they are NOT byte-exact
 * copies of a specific browser build, and the verdicts below never claim
 * more than "the operator treats these two hellos differently".
 */
enum class TlsFingerprint(val label: String) {
    /** Chrome 124+ : GREASE, X25519Kyber768 post-quantum key share, padding. */
    CHROME_MODERN("Chrome (modern, PQ)"),

    /** Chrome ~102 : GREASE, no post-quantum key share, padding. */
    CHROME_LEGACY("Chrome (legacy, no PQ)"),

    /** Firefox: no GREASE, distinct cipher order, no PQ. */
    FIREFOX("Firefox"),

    /** Safari/iOS: no GREASE, no padding. */
    SAFARI("Safari"),

    /** Legacy Chromium Edge: GREASE, no PQ, no padding, thinner extension set. */
    EDGE("Edge (legacy)"),

    /** Go/Java stack: no GREASE, no PQ, no padding, no browser extension set. */
    GO_JAVA("Go / Java"),

    /** Chrome-modern shape with a 32-byte session id, i.e. what REALITY sends. */
    REALITY_LIKE("REALITY-like (Chrome + 32B session id)")
}

internal data class FingerprintSpec(
    val grease: Boolean,
    val pqKeyShare: Boolean,
    val sessionIdBytes: Int,
    val cipherSuites: List<Int>,
    val extraGroups: List<Int>,
    val signatureAlgorithms: List<Int>,
    val extensionOrder: List<Int>,
    val padding: Int
)

private const val EXT_SERVER_NAME = 0x0000
private const val EXT_STATUS_REQUEST = 0x0005
private const val EXT_SUPPORTED_GROUPS = 0x000a
private const val EXT_EC_POINT_FORMATS = 0x000b
private const val EXT_SIGNATURE_ALGORITHMS = 0x000d
private const val EXT_ALPN = 0x0010
private const val EXT_SCT = 0x0012
private const val EXT_PADDING = 0x0015
private const val EXT_EXTENDED_MASTER_SECRET = 0x0017
private const val EXT_COMPRESS_CERTIFICATE = 0x001b
private const val EXT_RECORD_SIZE_LIMIT = 0x001c
private const val EXT_DELEGATED_CREDENTIAL = 0x0022
private const val EXT_SESSION_TICKET = 0x0023
private const val EXT_SUPPORTED_VERSIONS = 0x002b
private const val EXT_PSK_KEY_EXCHANGE_MODES = 0x002d
private const val EXT_KEY_SHARE = 0x0033
private const val EXT_RENEGOTIATION_INFO = 0xff01

private const val GROUP_X25519 = 0x001d
private const val GROUP_SECP256R1 = 0x0017
private const val GROUP_SECP384R1 = 0x0018
/** X25519Kyber768Draft00 — the group modern Chrome offers first. */
private const val GROUP_X25519_KYBER768 = 0x11ec

private val GREASE_VALUES = intArrayOf(
    0x0a0a, 0x1a1a, 0x2a2a, 0x3a3a, 0x4a4a, 0x5a5a,
    0x6a6a, 0x7a7a, 0x8a8a, 0x9a9a, 0xaaaa, 0xbaba,
    0xcaca, 0xdada, 0xeaea, 0xfafa
)

private val CHROME_CIPHERS = listOf(
    0x1301, 0x1302, 0x1303, 0xc02b, 0xc02f, 0xc02c, 0xc030,
    0xcca9, 0xcca8, 0xc013, 0xc014, 0x009c, 0x009d, 0x002f, 0x0035, 0x000a
)
private val FIREFOX_CIPHERS = listOf(
    0x1301, 0x1303, 0x1302, 0xc02b, 0xc02f, 0xcca9, 0xcca8, 0xc02c,
    0xc030, 0xc00a, 0xc009, 0xc013, 0xc014, 0x009c, 0x009d, 0x002f, 0x0035, 0x000a
)
private val SAFARI_CIPHERS = listOf(
    0x1301, 0x1302, 0x1303, 0xc02c, 0xc02b, 0xc030, 0xc02f, 0xcca9,
    0xcca8, 0xc014, 0xc013, 0x009d, 0x009c, 0x0035, 0x002f, 0x000a
)
private val GO_CIPHERS = listOf(
    0x1301, 0x1302, 0x1303, 0xc02f, 0xc030, 0xc02b, 0xc02c, 0xcca8,
    0xcca9, 0xc013, 0xc014, 0x009c, 0x009d, 0x002f, 0x0035, 0x000a
)

private val CHROME_SIG_ALGS = listOf(
    0x0403, 0x0804, 0x0805, 0x0806, 0x0401, 0x0501, 0x080a, 0x080b,
    0x0808, 0x0402, 0x0601, 0x0503, 0x0201, 0x0203
)
private val FIREFOX_SIG_ALGS = listOf(
    0x0403, 0x0503, 0x0603, 0x0804, 0x0805, 0x0806, 0x0401, 0x0501,
    0x0601, 0x0201, 0x0203
)
private val GO_SIG_ALGS = listOf(
    0x0403, 0x0804, 0x0401, 0x0503, 0x0805, 0x0501, 0x0806, 0x0601, 0x0201, 0x0203
)

private val CHROME_EXTENSIONS = listOf(
    EXT_SERVER_NAME, EXT_EXTENDED_MASTER_SECRET, EXT_RENEGOTIATION_INFO,
    EXT_SUPPORTED_GROUPS, EXT_EC_POINT_FORMATS, EXT_SESSION_TICKET, EXT_ALPN,
    EXT_STATUS_REQUEST, EXT_SCT, EXT_KEY_SHARE, EXT_SUPPORTED_VERSIONS,
    EXT_SIGNATURE_ALGORITHMS, EXT_PSK_KEY_EXCHANGE_MODES, EXT_RECORD_SIZE_LIMIT,
    EXT_COMPRESS_CERTIFICATE, EXT_PADDING
)
private val FIREFOX_EXTENSIONS = listOf(
    EXT_SERVER_NAME, EXT_EXTENDED_MASTER_SECRET, EXT_RENEGOTIATION_INFO,
    EXT_SUPPORTED_GROUPS, EXT_EC_POINT_FORMATS, EXT_SESSION_TICKET, EXT_ALPN,
    EXT_STATUS_REQUEST, EXT_DELEGATED_CREDENTIAL, EXT_KEY_SHARE,
    EXT_SUPPORTED_VERSIONS, EXT_SIGNATURE_ALGORITHMS, EXT_PSK_KEY_EXCHANGE_MODES,
    EXT_RECORD_SIZE_LIMIT, EXT_PADDING
)
private val SAFARI_EXTENSIONS = listOf(
    EXT_SERVER_NAME, EXT_EXTENDED_MASTER_SECRET, EXT_RENEGOTIATION_INFO,
    EXT_SUPPORTED_GROUPS, EXT_EC_POINT_FORMATS, EXT_ALPN, EXT_STATUS_REQUEST,
    EXT_SCT, EXT_KEY_SHARE, EXT_SUPPORTED_VERSIONS, EXT_SIGNATURE_ALGORITHMS,
    EXT_PSK_KEY_EXCHANGE_MODES
)
private val EDGE_EXTENSIONS = listOf(
    EXT_SERVER_NAME, EXT_EXTENDED_MASTER_SECRET, EXT_RENEGOTIATION_INFO,
    EXT_SUPPORTED_GROUPS, EXT_EC_POINT_FORMATS, EXT_SESSION_TICKET, EXT_ALPN,
    EXT_STATUS_REQUEST, EXT_SCT, EXT_KEY_SHARE, EXT_SUPPORTED_VERSIONS,
    EXT_SIGNATURE_ALGORITHMS, EXT_PSK_KEY_EXCHANGE_MODES
)
private val GO_EXTENSIONS = listOf(
    EXT_SERVER_NAME, EXT_EXTENDED_MASTER_SECRET, EXT_RENEGOTIATION_INFO,
    EXT_SUPPORTED_GROUPS, EXT_EC_POINT_FORMATS, EXT_SESSION_TICKET, EXT_ALPN,
    EXT_SCT, EXT_KEY_SHARE, EXT_SUPPORTED_VERSIONS, EXT_SIGNATURE_ALGORITHMS,
    EXT_PSK_KEY_EXCHANGE_MODES
)

internal fun specFor(fp: TlsFingerprint): FingerprintSpec = when (fp) {
    TlsFingerprint.CHROME_MODERN -> FingerprintSpec(
        grease = true, pqKeyShare = true, sessionIdBytes = 0,
        cipherSuites = CHROME_CIPHERS,
        extraGroups = listOf(GROUP_X25519_KYBER768),
        signatureAlgorithms = CHROME_SIG_ALGS,
        extensionOrder = CHROME_EXTENSIONS, padding = 96
    )
    TlsFingerprint.REALITY_LIKE -> FingerprintSpec(
        grease = true, pqKeyShare = true, sessionIdBytes = 32,
        cipherSuites = CHROME_CIPHERS,
        extraGroups = listOf(GROUP_X25519_KYBER768),
        signatureAlgorithms = CHROME_SIG_ALGS,
        extensionOrder = CHROME_EXTENSIONS, padding = 96
    )
    TlsFingerprint.CHROME_LEGACY -> FingerprintSpec(
        grease = true, pqKeyShare = false, sessionIdBytes = 32,
        cipherSuites = CHROME_CIPHERS,
        extraGroups = emptyList(),
        signatureAlgorithms = CHROME_SIG_ALGS,
        extensionOrder = CHROME_EXTENSIONS, padding = 96
    )
    TlsFingerprint.FIREFOX -> FingerprintSpec(
        grease = false, pqKeyShare = false, sessionIdBytes = 32,
        cipherSuites = FIREFOX_CIPHERS,
        extraGroups = emptyList(),
        signatureAlgorithms = FIREFOX_SIG_ALGS,
        extensionOrder = FIREFOX_EXTENSIONS, padding = 1
    )
    TlsFingerprint.SAFARI -> FingerprintSpec(
        grease = false, pqKeyShare = false, sessionIdBytes = 0,
        cipherSuites = SAFARI_CIPHERS,
        extraGroups = emptyList(),
        signatureAlgorithms = CHROME_SIG_ALGS,
        extensionOrder = SAFARI_EXTENSIONS, padding = 0
    )
    TlsFingerprint.EDGE -> FingerprintSpec(
        grease = true, pqKeyShare = false, sessionIdBytes = 32,
        cipherSuites = CHROME_CIPHERS,
        extraGroups = emptyList(),
        signatureAlgorithms = CHROME_SIG_ALGS,
        extensionOrder = EDGE_EXTENSIONS, padding = 0
    )
    TlsFingerprint.GO_JAVA -> FingerprintSpec(
        grease = false, pqKeyShare = false, sessionIdBytes = 0,
        cipherSuites = GO_CIPHERS,
        extraGroups = emptyList(),
        signatureAlgorithms = GO_SIG_ALGS,
        extensionOrder = GO_EXTENSIONS, padding = 0
    )
}

// ── Byte writer ──────────────────────────────────────────────────────────────

private class Buf {
    private val out = ByteArrayOutputStream()
    fun b(v: Int): Buf { out.write(v and 0xff); return this }
    fun u16(v: Int): Buf { b(v shr 8); b(v); return this }
    fun u24(v: Int): Buf { b(v shr 16); b(v shr 8); b(v); return this }
    fun raw(a: ByteArray): Buf { out.write(a); return this }
    fun u16Prefixed(a: ByteArray): Buf { u16(a.size); raw(a); return this }
    fun toBytes(): ByteArray = out.toByteArray()
}

private fun u16List(values: List<Int>): ByteArray {
    val b = Buf()
    b.u16(values.size * 2)
    values.forEach { b.u16(it) }
    return b.toBytes()
}

private fun ext(type: Int, body: ByteArray): ByteArray {
    val b = Buf()
    b.u16(type)
    b.u16Prefixed(body)
    return b.toBytes()
}

// ── ClientHello forge ────────────────────────────────────────────────────────

/**
 * Builds a TLS 1.3 ClientHello carrying [fingerprint]'s shape.
 *
 * The key-share material is random: X25519 accepts any 32-byte string as a
 * public key, and this probe never completes a handshake, so no private key
 * is needed. That keeps the whole forge dependency-free and fast.
 */
object ClientHelloForge {

    fun build(
        sni: String?,
        fingerprint: TlsFingerprint,
        random: SecureRandom = SecureRandom()
    ): ByteArray {
        val spec = specFor(fingerprint)
        val grease = GREASE_VALUES[random.nextInt(GREASE_VALUES.size)]

        val body = Buf()
        body.u16(0x0303) // legacy_version == TLS 1.2 in every real 1.3 hello

        val clientRandom = ByteArray(32)
        random.nextBytes(clientRandom)
        body.raw(clientRandom)

        val sessionId = ByteArray(spec.sessionIdBytes)
        if (spec.sessionIdBytes > 0) random.nextBytes(sessionId)
        body.b(sessionId.size)
        body.raw(sessionId)

        val ciphers = buildList {
            if (spec.grease) add(grease)
            addAll(spec.cipherSuites)
        }
        body.raw(u16List(ciphers))

        body.b(1) // compression_methods length
        body.b(0) // null

        val groups = buildList {
            if (spec.grease) add(grease)
            addAll(spec.extraGroups)
            add(GROUP_X25519)
            add(GROUP_SECP256R1)
            add(GROUP_SECP384R1)
        }

        val extensions = spec.extensionOrder.mapNotNull { type -> extensionBody(type, spec, sni, groups, grease, random) }
        val extBlob = Buf()
        extBlob.u16(extensions.sumOf { it.size })
        extensions.forEach { extBlob.raw(it) }
        body.raw(extBlob.toBytes())

        val handshake = Buf()
        handshake.b(0x01) // client_hello
        handshake.u24(body.toBytes().size)
        handshake.raw(body.toBytes())

        val record = Buf()
        record.b(0x16) // handshake
        record.u16(0x0301) // legacy record version
        record.u16Prefixed(handshake.toBytes())
        return record.toBytes()
    }

    private fun extensionBody(
        type: Int,
        spec: FingerprintSpec,
        sni: String?,
        groups: List<Int>,
        grease: Int,
        random: SecureRandom
    ): ByteArray? {
        when (type) {
            EXT_SERVER_NAME -> {
                if (sni.isNullOrBlank()) return null
                val host = sni.toByteArray(Charsets.US_ASCII)
                // The extension body is a ServerNameList: a 2-byte length
                // covering every entry. Omitting it makes strict servers
                // reject the entire hello with decode_error.
                val entry = Buf()
                entry.b(0x00) // name_type: host_name
                entry.u16Prefixed(host)
                val list = Buf()
                list.u16Prefixed(entry.toBytes())
                return ext(type, list.toBytes())
            }
            EXT_EXTENDED_MASTER_SECRET -> return ext(type, ByteArray(0))
            EXT_RENEGOTIATION_INFO -> return ext(type, byteArrayOf(0x00))
            EXT_SUPPORTED_GROUPS -> return ext(type, u16List(groups))
            EXT_EC_POINT_FORMATS -> return ext(type, byteArrayOf(0x01, 0x00))
            EXT_SESSION_TICKET -> return ext(type, ByteArray(0))
            EXT_ALPN -> {
                val list = Buf()
                val protocols = listOf("h2", "http/1.1")
                list.u16(protocols.sumOf { it.length + 1 })
                protocols.forEach {
                    list.b(it.length)
                    list.raw(it.toByteArray(Charsets.US_ASCII))
                }
                return ext(type, list.toBytes())
            }
            EXT_STATUS_REQUEST -> return ext(type, byteArrayOf(0x01, 0x00, 0x00, 0x00, 0x00))
            EXT_SCT -> return ext(type, ByteArray(0))
            EXT_DELEGATED_CREDENTIAL -> return ext(type, u16List(listOf(0x0403, 0x0503, 0x0603, 0x0804, 0x0805, 0x0806)))
            EXT_KEY_SHARE -> {
                // Only groups we can produce real key material for are given a
                // share. The post-quantum group is advertised in
                // supported_groups — which is what JA3/JA4 hash and therefore
                // what a DPI box fingerprints — but deliberately NOT given a
                // key share: we cannot generate a Kyber768 key without a PQ
                // provider, and offering a random blob there makes servers that
                // prefer the group (Cloudflare, several CDNs) fail the whole
                // hello with decode_error. A false "blocked" on the modern
                // fingerprint would be worse than no signal at all.
                val blobs = mutableListOf<ByteArray>()
                if (spec.grease) blobs.add(u16PrefixedBlob(grease, randomBytes(32, random)))
                blobs.add(u16PrefixedBlob(GROUP_X25519, randomBytes(32, random)))
                val payload = Buf()
                payload.u16(blobs.sumOf { it.size })
                blobs.forEach { payload.raw(it) }
                return ext(type, payload.toBytes())
            }
            // supported_versions is a 1-byte-length vector of 2-byte versions,
            // so advertising TLS 1.3 + TLS 1.2 is length 4, not 2. Writing the
            // version COUNT here leaves trailing bytes inside the extension and
            // servers then fail to parse the next one (decode_error).
            EXT_SUPPORTED_VERSIONS -> return ext(type, byteArrayOf(0x04, 0x03, 0x04, 0x03, 0x03))
            EXT_SIGNATURE_ALGORITHMS -> return ext(type, u16List(spec.signatureAlgorithms))
            // PskKeyExchangeModes is a 1-byte-length vector: <1..255>.
            // Sending two identical modes here makes strict servers (Cloudflare,
            // and several CDNs) reject the whole hello with decode_error.
            EXT_PSK_KEY_EXCHANGE_MODES -> return ext(type, byteArrayOf(0x01, 0x01))
            EXT_RECORD_SIZE_LIMIT -> return ext(type, byteArrayOf(0x40, 0x01))
            // CertificateCompressionAlgorithm values are 2-byte, so the body is
            // a 1-byte BYTE length followed by 2-byte algorithms. Sending
            // "length 1 + 0x02" leaves an odd byte and servers reject the hello.
            EXT_COMPRESS_CERTIFICATE -> return ext(type, byteArrayOf(0x02, 0x00, 0x02))
            EXT_PADDING -> {
                if (spec.padding <= 0) return null
                return ext(type, ByteArray(spec.padding))
            }
            else -> return null // unknown extension: not emitted
        }
    }

    private fun u16PrefixedBlob(group: Int, key: ByteArray): ByteArray {
        val b = Buf()
        b.u16(group)
        b.u16Prefixed(key)
        return b.toBytes()
    }

    private fun randomBytes(n: Int, random: SecureRandom): ByteArray {
        val a = ByteArray(n)
        random.nextBytes(a)
        return a
    }
}

// ── Server reply parsing ─────────────────────────────────────────────────────

enum class HelloOutcome {
    SERVER_HELLO,
    HELLO_RETRY,
    APPLICATION_DATA,
    ALERT_HANDSHAKE_FAILURE,
    ALERT_UNRECOGNIZED_NAME,
    ALERT_PROTOCOL_VERSION,
    ALERT_INTERNAL_ERROR,
    ALERT_OTHER,
    RESET,
    TIMEOUT,
    CLOSED,
    UNKNOWN;

    /** A reply that means "the far end (or something in front of it) kept talking TLS". */
    val accepted: Boolean
        get() = this == SERVER_HELLO || this == HELLO_RETRY || this == APPLICATION_DATA
}

data class HelloReply(
    val outcome: HelloOutcome,
    val alertDescription: Int = -1,
    val serverVersion: String = "",
    val cipherSuite: Int = -1,
    val latencyMs: Long = -1,
    val bytesRead: Int = 0
) {
    val accepted: Boolean get() = outcome.accepted
}

object TlsRecordReader {

    fun parse(bytes: ByteArray, latencyMs: Long = -1): HelloReply {
        if (bytes.isEmpty()) return HelloReply(HelloOutcome.CLOSED, latencyMs = latencyMs)
        if (bytes.size < 5) return HelloReply(HelloOutcome.UNKNOWN, bytesRead = bytes.size, latencyMs = latencyMs)

        val type = bytes[0].toInt() and 0xff
        return when (type) {
            0x15 -> parseAlert(bytes, latencyMs)
            0x16 -> parseHandshake(bytes, latencyMs)
            0x17 -> HelloReply(HelloOutcome.APPLICATION_DATA, bytesRead = bytes.size, latencyMs = latencyMs)
            else -> HelloReply(HelloOutcome.UNKNOWN, bytesRead = bytes.size, latencyMs = latencyMs)
        }
    }

    private fun parseAlert(bytes: ByteArray, latencyMs: Long): HelloReply {
        if (bytes.size < 7) return HelloReply(HelloOutcome.ALERT_OTHER, bytesRead = bytes.size, latencyMs = latencyMs)
        val description = bytes[6].toInt() and 0xff
        val outcome = when (description) {
            40 -> HelloOutcome.ALERT_HANDSHAKE_FAILURE
            112 -> HelloOutcome.ALERT_UNRECOGNIZED_NAME
            70 -> HelloOutcome.ALERT_PROTOCOL_VERSION
            80 -> HelloOutcome.ALERT_INTERNAL_ERROR
            else -> HelloOutcome.ALERT_OTHER
        }
        return HelloReply(outcome, alertDescription = description, bytesRead = bytes.size, latencyMs = latencyMs)
    }

    private fun parseHandshake(bytes: ByteArray, latencyMs: Long): HelloReply {
        if (bytes.size < 6) return HelloReply(HelloOutcome.UNKNOWN, bytesRead = bytes.size, latencyMs = latencyMs)
        val hsType = bytes[5].toInt() and 0xff
        if (hsType != 0x02) return HelloReply(HelloOutcome.UNKNOWN, bytesRead = bytes.size, latencyMs = latencyMs)

        // ServerHello body starts after the 4-byte handshake header.
        val p = 9
        if (bytes.size < p + 2) {
            return HelloReply(HelloOutcome.SERVER_HELLO, bytesRead = bytes.size, latencyMs = latencyMs)
        }
        val version = "0x%02x%02x".format(bytes[p].toInt() and 0xff, bytes[p + 1].toInt() and 0xff)
        val sidLenIndex = p + 2 + 32
        if (bytes.size <= sidLenIndex) {
            return HelloReply(HelloOutcome.SERVER_HELLO, serverVersion = version, bytesRead = bytes.size, latencyMs = latencyMs)
        }
        val sidLen = bytes[sidLenIndex].toInt() and 0xff
        val cipherIndex = sidLenIndex + 1 + sidLen
        val cipher = if (bytes.size >= cipherIndex + 2) {
            ((bytes[cipherIndex].toInt() and 0xff) shl 8) or (bytes[cipherIndex + 1].toInt() and 0xff)
        } else -1
        return HelloReply(
            outcome = HelloOutcome.SERVER_HELLO,
            serverVersion = version,
            cipherSuite = cipher,
            bytesRead = bytes.size,
            latencyMs = latencyMs
        )
    }
}

// ── Transport ────────────────────────────────────────────────────────────────

interface HelloTransport {
    /** Sends [hello] to [host]:[port] and returns whatever came back first. */
    fun exchange(host: String, port: Int, hello: ByteArray, timeoutMs: Int): HelloReply
}

/** Real transport. Blocking: callers must run it off the main thread. */
object SocketHelloTransport : HelloTransport {
    override fun exchange(host: String, port: Int, hello: ByteArray, timeoutMs: Int): HelloReply {
        val t0 = System.currentTimeMillis()
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                socket.soTimeout = timeoutMs
                val latency = System.currentTimeMillis() - t0
                socket.getOutputStream().write(hello)
                socket.getOutputStream().flush()
                val buffer = ByteArray(8192)
                val read = try {
                    socket.getInputStream().read(buffer)
                } catch (timeout: SocketTimeoutException) {
                    return HelloReply(HelloOutcome.TIMEOUT, latencyMs = latency)
                }
                if (read <= 0) return HelloReply(HelloOutcome.CLOSED, latencyMs = latency)
                TlsRecordReader.parse(buffer.copyOf(read), latency)
            }
        } catch (io: IOException) {
            val msg = io.message.orEmpty()
            val outcome = when {
                msg.contains("reset", ignoreCase = true) ||
                    msg.contains("ECONNRESET", ignoreCase = true) ||
                    msg.contains("broken pipe", ignoreCase = true) -> HelloOutcome.RESET
                msg.contains("refused", ignoreCase = true) -> HelloOutcome.CLOSED
                msg.contains("timed out", ignoreCase = true) -> HelloOutcome.TIMEOUT
                else -> HelloOutcome.UNKNOWN
            }
            HelloReply(outcome, latencyMs = System.currentTimeMillis() - t0)
        }
    }
}

// ── Fingerprint differential ─────────────────────────────────────────────────

data class FingerprintProbe(val fingerprint: TlsFingerprint, val reply: HelloReply)

enum class DifferentialOutcome {
    /** Every hello was answered — no fingerprint preference visible. */
    ALL_ACCEPTED,
    /** No hello got through: the port (or the whole path) is being killed at TLS. */
    ALL_BLOCKED,
    /** Only the PQ-capable modern-Chrome hello passed. */
    MODERN_CHROME_ONLY,
    /** Chrome-shaped hellos passed, Firefox/Safari/Go did not. */
    CHROME_WHITELIST,
    /** Real browsers passed, the Go/Java hello did not. */
    NON_BROWSER_BLOCKED,
    /** Mixed results with no clean pattern — DPI behaviour is intermittent here. */
    INCONSISTENT,
    NOT_RUN
}

object FingerprintDifferential {

    fun assess(probes: List<FingerprintProbe>): DifferentialOutcome {
        if (probes.isEmpty()) return DifferentialOutcome.NOT_RUN
        val ok = probes.filter { it.reply.accepted }.map { it.fingerprint }.toSet()
        if (ok.isEmpty()) return DifferentialOutcome.ALL_BLOCKED
        if (ok.size == probes.size) return DifferentialOutcome.ALL_ACCEPTED

        val modernChromeOk = ok.contains(TlsFingerprint.CHROME_MODERN) || ok.contains(TlsFingerprint.REALITY_LIKE)
        val legacyChromeOk = ok.contains(TlsFingerprint.CHROME_LEGACY) || ok.contains(TlsFingerprint.EDGE)
        if (modernChromeOk && !legacyChromeOk && TlsFingerprint.CHROME_LEGACY in probes.map { it.fingerprint }) {
            return DifferentialOutcome.MODERN_CHROME_ONLY
        }
        val browsers = setOf(
            TlsFingerprint.CHROME_MODERN, TlsFingerprint.REALITY_LIKE,
            TlsFingerprint.CHROME_LEGACY, TlsFingerprint.FIREFOX,
            TlsFingerprint.SAFARI, TlsFingerprint.EDGE
        )
        val browsersRun = probes.map { it.fingerprint }.filter { it in browsers }.toSet()
        if (browsersRun.isNotEmpty() && browsersRun.all { it in ok } && TlsFingerprint.GO_JAVA !in ok) {
            return DifferentialOutcome.NON_BROWSER_BLOCKED
        }
        val chromeShape = setOf(
            TlsFingerprint.CHROME_MODERN, TlsFingerprint.REALITY_LIKE,
            TlsFingerprint.CHROME_LEGACY, TlsFingerprint.EDGE
        )
        val chromeRun = probes.map { it.fingerprint }.filter { it in chromeShape }.toSet()
        if (chromeRun.isNotEmpty() && chromeRun.all { it in ok }) {
            return DifferentialOutcome.CHROME_WHITELIST
        }
        return DifferentialOutcome.INCONSISTENT
    }
}

// ── Post-handshake survival ──────────────────────────────────────────────────

/**
 * What happened after the handshake finished — the window the old tester never
 * looked at. Iranian mobile operators routinely allow a full TLS handshake and
 * then reset the flow as soon as application data moves (or even while it sits
 * idle), which is exactly why "handshake OK" is not evidence of a usable path.
 */
data class PostHandshakeResult(
    val handshakeOk: Boolean,
    val handshakeMs: Long = -1,
    val certCn: String = "",
    val certIssuer: String = "",
    val sanMatch: String = "",
    /** How long the connection sat idle before something killed it. */
    val idleSurvivedMs: Long = 0,
    val idleReset: Boolean = false,
    /** Payload pushed after the handshake. */
    val bytesSent: Int = 0,
    val bytesReceived: Int = 0,
    val dataReset: Boolean = false,
    val timeToFirstByteMs: Long = -1,
    val dataSurvivedMs: Long = 0
) {
    /** True when the flow died only after the handshake looked successful. */
    val killedAfterHandshake: Boolean
        get() = handshakeOk && (idleReset || dataReset)
}

object PostHandshakeProbe {

    fun run(
        host: String,
        port: Int,
        sni: String?,
        connectTimeoutMs: Int = 4000,
        idleWatchMs: Long = 4000,
        payloadBytes: Int = 8192,
        dataWatchMs: Long = 6000
    ): PostHandshakeResult {
        val chainRef = arrayOf<Array<X509Certificate>?>(null)
        val tm = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) { chainRef[0] = chain }
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, arrayOf<TrustManager>(tm), null)

        val t0 = System.currentTimeMillis()
        return try {
            (ctx.socketFactory.createSocket() as SSLSocket).use { socket ->
                socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
                socket.soTimeout = connectTimeoutMs
                if (!sni.isNullOrBlank()) {
                    val params = socket.sslParameters
                    params.serverNames = listOf(SNIHostName(sni))
                    socket.sslParameters = params
                }
                socket.startHandshake()
                val handshakeMs = System.currentTimeMillis() - t0

                val chain = chainRef[0]
                val leaf = chain?.firstOrNull()
                val cn = leaf?.let { Regex("CN=([^,]+)").find(it.subjectX500Principal.name)?.groupValues?.get(1)?.trim() } ?: ""
                val issuer = leaf?.let { Regex("CN=([^,]+)").find(it.issuerX500Principal.name)?.groupValues?.get(1)?.trim() } ?: ""
                val sanMatch = leaf?.let { cert ->
                    val sans = try { cert.subjectAlternativeNames } catch (_: Exception) { null }
                    when {
                        sans == null -> ""
                        sni.isNullOrBlank() -> ""
                        sans.any { it.size >= 2 && it[1] == sni } -> "match"
                        else -> "mismatch"
                    }
                } ?: ""

                // 1. Sit idle: a RST here is pure on-path intervention.
                val idleStart = System.currentTimeMillis()
                socket.soTimeout = idleWatchMs.toInt().coerceAtLeast(500)
                val idleDied = readWouldBlock(socket)
                val idleSurvived = System.currentTimeMillis() - idleStart
                if (idleDied) {
                    return PostHandshakeResult(
                        handshakeOk = true, handshakeMs = handshakeMs, certCn = cn,
                        certIssuer = issuer, sanMatch = sanMatch,
                        idleSurvivedMs = idleSurvived, idleReset = true
                    )
                }

                // 2. Push payload: the "accept then RST after data" signature.
                val payload = ByteArray(payloadBytes)
                CryptoSecurity.fillRandom(payload)
                val dataStart = System.currentTimeMillis()
                var dataReset = false
                var received = 0
                var ttfb = -1L
                try {
                    socket.getOutputStream().write(payload)
                    socket.getOutputStream().flush()
                    socket.soTimeout = dataWatchMs.toInt().coerceAtLeast(500)
                    val buf = ByteArray(8192)
                    val n = socket.getInputStream().read(buf)
                    when {
                        n > 0 -> { received = n; ttfb = System.currentTimeMillis() - dataStart }
                        n == -1 -> dataReset = false // clean close is not a reset
                    }
                } catch (io: IOException) {
                    dataReset = isResetMessage(io.message)
                }
                PostHandshakeResult(
                    handshakeOk = true, handshakeMs = handshakeMs, certCn = cn,
                    certIssuer = issuer, sanMatch = sanMatch,
                    idleSurvivedMs = idleSurvived, idleReset = false,
                    bytesSent = payloadBytes, bytesReceived = received,
                    dataReset = dataReset, timeToFirstByteMs = ttfb,
                    dataSurvivedMs = System.currentTimeMillis() - dataStart
                )
            }
        } catch (io: IOException) {
            PostHandshakeResult(handshakeOk = false)
        } catch (se: Exception) {
            PostHandshakeResult(handshakeOk = false)
        }
    }

    /** Returns true when the peer closed or reset while we were only watching. */
    private fun readWouldBlock(socket: SSLSocket): Boolean = try {
        val buf = ByteArray(1)
        val n = socket.getInputStream().read(buf)
        n <= 0
    } catch (timeout: SocketTimeoutException) {
        false // nothing arrived: the flow is still alive
    } catch (io: IOException) {
        isResetMessage(io.message)
    }

    internal fun isResetMessage(message: String?): Boolean {
        val msg = message.orEmpty()
        return msg.contains("reset", ignoreCase = true) ||
            msg.contains("ECONNRESET", ignoreCase = true) ||
            msg.contains("broken pipe", ignoreCase = true)
    }
}

// ── Port matrix ──────────────────────────────────────────────────────────────

data class PortProbe(
    val port: Int,
    val connectOk: Boolean,
    val reset: Boolean,
    val refused: Boolean,
    val latencyMs: Long
)

enum class PortMatrixOutcome {
    /** Nothing on this address answers: the address itself is unreachable from here. */
    ALL_DEAD,
    /** Everything answers — the address is alive on this operator. */
    ALL_OPEN,
    /** Some ports answer and some do not: filtering is port- or protocol-specific. */
    SELECTIVE,
    NOT_RUN
}

object PortMatrix {
    fun assess(probes: List<PortProbe>): PortMatrixOutcome {
        if (probes.isEmpty()) return PortMatrixOutcome.NOT_RUN
        val alive = probes.count { it.connectOk || it.refused }
        return when {
            alive == 0 -> PortMatrixOutcome.ALL_DEAD
            alive == probes.size -> PortMatrixOutcome.ALL_OPEN
            else -> PortMatrixOutcome.SELECTIVE
        }
    }
}

// ── Run validation (controls) ────────────────────────────────────────────────

data class ControlResult(val target: String, val expectFiltered: Boolean, val reply: HelloReply)

enum class ValidationState {
    /** Controls behaved: filtering would have been visible if it were applied. */
    VALID,
    /** The known-filtered control sailed through: this network is not applying
     *  filtering the probe can see (VPN, or an operator that is not filtering). */
    BLIND_NETWORK,
    /** Even the always-allowed control failed: there is no usable path at all. */
    BROKEN_NETWORK,
    NOT_RUN
}

object RunValidator {
    fun validate(positive: ControlResult?, negative: ControlResult?): ValidationState {
        if (positive == null && negative == null) return ValidationState.NOT_RUN
        if (positive != null && !positive.reply.accepted) return ValidationState.BROKEN_NETWORK
        if (negative != null && negative.reply.accepted) return ValidationState.BLIND_NETWORK
        return ValidationState.VALID
    }
}

// ── Remote vantage ───────────────────────────────────────────────────────────

/** What Check-Host-style nodes outside the country saw for the same host:port. */
data class RemoteVantage(val nodesReached: Int, val nodesOpen: Int) {
    val nodesClosed: Int get() = (nodesReached - nodesOpen).coerceAtLeast(0)
    val openRatio: Double get() = if (nodesReached == 0) 0.0 else nodesOpen.toDouble() / nodesReached
}

enum class VantageOutcome {
    /** Remote nodes reach it, we do not: filtering is on our path. */
    OPERATOR_FILTERING,
    /** Nobody reaches it: the service itself is down. */
    SERVER_SIDE,
    /** We reach it, remote nodes do not — unusual, treat as inconclusive. */
    LOCAL_ONLY,
    BOTH_REACH,
    NOT_RUN
}

object RemoteVantageCompare {
    fun compare(localReachable: Boolean, remote: RemoteVantage?): VantageOutcome {
        if (remote == null || remote.nodesReached == 0) return VantageOutcome.NOT_RUN
        val remoteOpen = remote.openRatio >= 0.5
        return when {
            remoteOpen && !localReachable -> VantageOutcome.OPERATOR_FILTERING
            !remoteOpen && !localReachable -> VantageOutcome.SERVER_SIDE
            !remoteOpen && localReachable -> VantageOutcome.LOCAL_ONLY
            else -> VantageOutcome.BOTH_REACH
        }
    }
}

// ── Final assessment ─────────────────────────────────────────────────────────

enum class DpiConclusion {
    /** Nothing on this path blocked us — with the limits listed in [DpiAssessment.limitations]. */
    NO_FILTERING_SEEN,
    FILTERED_ADDRESS,
    FILTERED_PORT,
    FILTERED_FINGERPRINT,
    FILTERED_MODERN_FINGERPRINT_REQUIRED,
    FILTERED_SNI,
    FILTERED_AFTER_HANDSHAKE,
    /** Reachable from nowhere, including outside the country: not censorship. */
    SERVICE_DOWN,
    MIDDLEBOX_SUSPECTED,
    INCONCLUSIVE
}

enum class Confidence { HIGH, MEDIUM, LOW, NONE }

data class Evidence(val label: String, val detail: String)

data class DpiAssessment(
    val verdict: DpiConclusion,
    val confidence: Confidence,
    val summary: String,
    val evidence: List<Evidence>,
    /** What this run did NOT check. Shown verbatim so "no filtering" is never absolute. */
    val limitations: List<String>,
    val nextSteps: List<String>
)

data class DpiRunInput(
    val host: String,
    val port: Int,
    val sni: String? = null,
    val portMatrix: List<PortProbe> = emptyList(),
    val matrixOutcome: PortMatrixOutcome = PortMatrixOutcome.NOT_RUN,
    val fingerprints: List<FingerprintProbe> = emptyList(),
    val differential: DifferentialOutcome = DifferentialOutcome.NOT_RUN,
    val postHandshake: PostHandshakeResult? = null,
    val positiveControl: ControlResult? = null,
    val negativeControl: ControlResult? = null,
    val remote: RemoteVantage? = null,
    val vpnActive: Boolean = false,
    val referenceAnchorMs: Long = -1,
    val targetLatencyMs: Long = -1
)

object DpiAssessmentEngine {

    fun assess(input: DpiRunInput): DpiAssessment {
        val evidence = mutableListOf<Evidence>()
        val limitations = mutableListOf<String>()
        val steps = mutableListOf<String>()

        val validation = RunValidator.validate(input.positiveControl, input.negativeControl)
        val vantage = RemoteVantageCompare.compare(
            localReachable = input.portMatrix.any { it.port == input.port && it.connectOk },
            remote = input.remote
        )

        // Honest baseline: a censorship probe can only ever report what it saw.
        limitations += "Only the path from this device, on this operator, at this moment was measured."
        if (input.vpnActive) {
            limitations += "A VPN is active: the probe measured the tunnel's exit, not the operator's path."
        }
        if (input.fingerprints.isEmpty()) {
            limitations += "No TLS fingerprint differential was run, so ClientHello-pattern filtering cannot be ruled out."
        }
        if (input.postHandshake == null) {
            limitations += "No post-handshake data was exchanged, so accept-then-reset filtering cannot be ruled out."
        }
        limitations += "A clean result means 'no filtering observed', never 'provably unfiltered'."

        input.postHandshake?.let { post ->
            if (post.handshakeOk) {
                evidence += Evidence(
                    "handshake",
                    "TLS completed in ${post.handshakeMs}ms" +
                        (if (post.certCn.isNotBlank()) " — certificate CN=${certOrUnknown(post)}" else "")
                )
                if (post.idleReset) evidence += Evidence("idle", "connection reset after ${post.idleSurvivedMs}ms idle with no data sent")
                if (post.dataReset) evidence += Evidence("payload", "connection reset after sending ${post.bytesSent} bytes")
                if (!post.idleReset && !post.dataReset) {
                    evidence += Evidence(
                        "payload",
                        "${post.bytesSent} bytes sent, ${post.bytesReceived} received, alive after ${post.dataSurvivedMs}ms"
                    )
                }
            } else {
                evidence += Evidence("handshake", "TLS handshake did not complete")
            }
        }

        when (input.differential) {
            DifferentialOutcome.MODERN_CHROME_ONLY -> {
                evidence += Evidence("fingerprint", "only the modern Chrome hello (with post-quantum key share) was answered")
                steps += "Set the client's uTLS fingerprint to a current Chrome (chrome_psk / Chrome 124+). " +
                    "An older 'chrome' or 'edge' fingerprint is being rejected here."
            }
            DifferentialOutcome.CHROME_WHITELIST -> {
                evidence += Evidence("fingerprint", "Chrome-shaped hellos passed while other browsers were reset")
                steps += "The operator is whitelisting Chrome-shaped ClientHellos; use a Chrome fingerprint."
            }
            DifferentialOutcome.NON_BROWSER_BLOCKED -> {
                evidence += Evidence("fingerprint", "browser hellos passed, the Go/Java hello was reset")
                steps += "Use a browser fingerprint; non-browser TLS stacks are filtered on this operator."
            }
            DifferentialOutcome.ALL_BLOCKED -> {
                evidence += Evidence("fingerprint", "no hello of any shape was answered on port ${input.port}")
            }
            DifferentialOutcome.INCONSISTENT -> {
                evidence += Evidence("fingerprint", "results differed between attempts with no stable pattern")
                limitations += "Fingerprint results were unstable; repeat the run before acting on it."
            }
            else -> Unit
        }

        if (input.remote != null && input.remote.nodesReached > 0) {
            evidence += Evidence(
                "remote",
                "${input.remote.nodesOpen} of ${input.remote.nodesReached} nodes outside the country reached ${input.host}:${input.port}"
            )
        }
        if (input.referenceAnchorMs > 0 && input.targetLatencyMs >= 0 &&
            input.targetLatencyMs < input.referenceAnchorMs * 35 / 100 &&
            input.targetLatencyMs < 20
        ) {
            evidence += Evidence(
                "latency",
                "target answered in ${input.targetLatencyMs}ms while the reference anchor takes ${input.referenceAnchorMs}ms"
            )
        }

        // ── Decision order matters: rule out non-censorship causes first. ──
        if (validation == ValidationState.BROKEN_NETWORK) {
            return DpiAssessment(
                DpiConclusion.INCONCLUSIVE,
                Confidence.NONE,
                "The control target was unreachable too, so this run says nothing about filtering.",
                evidence + Evidence("control", "control target ${input.positiveControl?.target} did not answer"),
                limitations,
                listOf("Reconnect to the network and run the diagnosis again.")
            )
        }

        if (vantage == VantageOutcome.SERVER_SIDE) {
            return DpiAssessment(
                DpiConclusion.SERVICE_DOWN,
                Confidence.HIGH,
                "Nodes outside the country cannot reach ${input.host}:${input.port} either — this is not operator filtering.",
                evidence,
                limitations,
                listOf("Check the service and firewall on the server itself; the path is fine.")
            )
        }

        if (input.postHandshake?.killedAfterHandshake == true) {
            return DpiAssessment(
                DpiConclusion.FILTERED_AFTER_HANDSHAKE,
                Confidence.HIGH,
                "The TLS handshake succeeded but the connection was reset as soon as it was used — " +
                    "the classic accept-then-kill pattern.",
                evidence,
                limitations,
                buildList {
                    add("The handshake is not the problem: change what comes after it (transport/flow), not the port.")
                    add("Try the same server without XHTTP (plain VLESS over REALITY), and compare.")
                    if (input.differential == DifferentialOutcome.MODERN_CHROME_ONLY) {
                        add("Also switch the uTLS fingerprint to a current Chrome.")
                    }
                }
            )
        }

        if (input.differential == DifferentialOutcome.MODERN_CHROME_ONLY) {
            return DpiAssessment(
                DpiConclusion.FILTERED_MODERN_FINGERPRINT_REQUIRED,
                Confidence.HIGH,
                "Only the modern Chrome ClientHello (with a post-quantum key share) was answered; " +
                    "older Chrome/Edge/Firefox hellos were rejected.",
                evidence,
                limitations,
                steps.ifEmpty { listOf("Set the client fingerprint to a current Chrome (Chrome 124+ / chrome_psk).") }
            )
        }

        when (input.differential) {
            DifferentialOutcome.CHROME_WHITELIST,
            DifferentialOutcome.NON_BROWSER_BLOCKED -> {
                return DpiAssessment(
                    DpiConclusion.FILTERED_FINGERPRINT,
                    Confidence.HIGH,
                    "The operator answers some TLS fingerprints and kills others on this port.",
                    evidence,
                    limitations,
                    steps
                )
            }
            DifferentialOutcome.ALL_BLOCKED -> {
                if (vantage == VantageOutcome.OPERATOR_FILTERING || input.matrixOutcome == PortMatrixOutcome.SELECTIVE) {
                    return DpiAssessment(
                        DpiConclusion.FILTERED_PORT,
                        Confidence.HIGH,
                        "TCP reaches the address on other ports but port ${input.port} answers no TLS at all.",
                        evidence,
                        limitations,
                        listOf("Move the service to a different port on the same address and re-test.")
                    )
                }
                return DpiAssessment(
                    DpiConclusion.FILTERED_ADDRESS,
                    if (vantage == VantageOutcome.OPERATOR_FILTERING) Confidence.HIGH else Confidence.MEDIUM,
                    "No port on this address answers from here.",
                    evidence,
                    limitations,
                    listOf("Change the server address; this address is blocked on this operator.")
                )
            }
            else -> Unit
        }

        if (input.matrixOutcome == PortMatrixOutcome.ALL_DEAD && vantage == VantageOutcome.OPERATOR_FILTERING) {
            return DpiAssessment(
                DpiConclusion.FILTERED_ADDRESS,
                Confidence.HIGH,
                "The whole address is unreachable from here while remote nodes reach it.",
                evidence,
                limitations,
                listOf("The address is filtered on this operator; move the service to a new address.")
            )
        }

        if (input.matrixOutcome == PortMatrixOutcome.ALL_DEAD) {
            return DpiAssessment(
                DpiConclusion.FILTERED_ADDRESS,
                Confidence.MEDIUM,
                "No port on ${input.host} answers from this network.",
                evidence,
                limitations,
                listOf("Confirm with a remote probe (Check-Host) before blaming the operator.")
            )
        }

        if (validation == ValidationState.BLIND_NETWORK) {
            return DpiAssessment(
                DpiConclusion.INCONCLUSIVE,
                Confidence.LOW,
                "The known-filtered control target was answered normally, so this network is not applying " +
                    "filtering the probe can see. Results from this run are not trustworthy.",
                evidence + Evidence(
                    "control",
                    "control ${input.negativeControl?.target} answered ${input.negativeControl?.reply?.outcome}"
                ),
                limitations,
                listOf("Turn off any VPN and re-run on the operator's own path.")
            )
        }

        return DpiAssessment(
            DpiConclusion.NO_FILTERING_SEEN,
            if (input.fingerprints.isNotEmpty() && input.postHandshake != null) Confidence.MEDIUM else Confidence.LOW,
            "No filtering was observed on this path for ${input.host}:${input.port}" +
                (if (input.sni.isNullOrBlank()) "" else " with SNI ${input.sni}") + ".",
            evidence,
            limitations,
            listOf(
                "If the real client still fails, the difference is above TLS: check the transport, the SNI/cover " +
                    "domain, and the server's own logs."
            )
        )
    }

    private fun certOrUnknown(post: PostHandshakeResult): String = post.certCn.ifBlank { "(none)" }
}
