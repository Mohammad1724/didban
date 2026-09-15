package org.didban.monitor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import java.io.BufferedReader

/**
 * REALITY donor (dest / SNI) validation.
 *
 * A REALITY inbound steals its TLS handshake from a real website, so the donor
 * has to satisfy properties that are easy to get wrong and fail silently: the
 * handshake completes, the client just cannot tunnel. This probes a candidate
 * and reports exactly which properties hold.
 *
 * Criteria encoded here, from the Xray/XTLS documentation and operator write-ups:
 *  1. TLS 1.3 — REALITY negotiates 1.3; an older donor cannot be impersonated.
 *  2. Valid public certificate — the disguise only works if the chain verifies.
 *  3. The SNI must appear in the certificate's SANs. Wildcards are not accepted
 *     in `serverNames`, so a donor whose cert only covers `*.example.com` while
 *     you configure `www.example.com` is still fine, but the reverse is not.
 *  4. HTTP/2 (ALPN `h2`) — otherwise the advertised ALPN does not match what
 *     the real site returns.
 *  5. No redirect on `/` — a 301/302 donor breaks the handshake. This is why
 *     country-localised names like `www.microsoft.co.uk` are bad donors.
 *  6. Not behind a CDN, and specifically not behind Cloudflare — if the donor
 *     resolves to a CDN edge, the Xray server becomes a port-forwarder for that
 *     CDN and can be abused as an open proxy by strangers.
 *  7. Reachable *from the server*, not only from the phone. REALITY fetches the
 *     handshake from the donor on every connection, so donor latency is added
 *     to every proxied connection. This is why the same check is worth running
 *     from an agent as well as locally.
 *
 * Post-quantum key exchange (X25519MLKEM768 / ML-DSA-65) is a documented donor
 * pitfall — it breaks current uTLS clients — but the negotiated named group is
 * not exposed by the JSSE/Conscrypt public API, so this file reports it as
 * UNKNOWN rather than guessing. [RealityAssessment] says so explicitly and
 * points at the server-side check that can answer it.
 */

/** One probed donor. */
data class RealityProbeResult(
    val sni: String,
    val port: Int,
    val resolvedIp: String,
    val dnsMs: Long,
    val tcpMs: Long,
    val tlsMs: Long,
    val totalMs: Long,
    val tlsVersion: String,
    val alpn: String,
    val cipher: String,
    val certSubject: String,
    val certIssuer: String,
    val certSans: List<String>,
    val certDaysRemaining: Long,
    val certPublicKeyAlg: String,
    val certChainBytes: Int,
    val certValid: Boolean,
    val certError: String,
    val sniMatchesSan: Boolean,
    val httpStatus: Int,
    val redirectLocation: String,
    val serverHeader: String,
    val behindCloudflare: Boolean,
    val behindCdn: Boolean,
    /**
     * False when this Android version cannot negotiate TLS 1.3 or advertise
     * ALPN at all (both are API 29+). Without it, an old device would report
     * a perfectly good donor as broken.
     */
    val platformTls13Capable: Boolean = true,
    val platformAlpnCapable: Boolean = true,
    val error: String = ""
) {
    val reachable: Boolean get() = error.isEmpty() && tlsVersion.isNotEmpty()
    val isTls13: Boolean get() = tlsVersion.equals("TLSv1.3", ignoreCase = true)
    val hasH2: Boolean get() = alpn.equals("h2", ignoreCase = true)
    val isRedirect: Boolean get() = httpStatus in 300..399
    val certExpired: Boolean get() = certDaysRemaining < 0
    val certExpiringSoon: Boolean get() = certDaysRemaining in 0..20
}

enum class RealityVerdict { GOOD, USABLE, RISKY, REJECT }

/**
 * The judgement, kept separate from the probe so the rules can be tested
 * without a network.
 */
data class RealityAssessment(
    val verdict: RealityVerdict,
    val score: Int,               // 0..100
    val blockers: List<String>,   // hard fails: do not use this donor
    val warnings: List<String>,   // works, but there is a real downside
    val notes: List<String>       // informational
)

object RealityCriteria {

    /** CDN fingerprints that make a donor a port-forwarding liability. */
    private val CDN_SERVER_HEADERS = listOf("cloudflare", "akamaighost", "akamai", "fastly", "varnish", "amazonguard")

    /** Response headers that betray a CDN even when `server` is generic. */
    private val CDN_HEADERS = listOf("cf-ray", "x-amz-cf-id", "x-amz-cf-pop", "x-fastly", "x-akamai", "x-served-by", "x-cache")

    fun isCdnServerHeader(value: String): Boolean {
        val v = value.lowercase()
        return CDN_SERVER_HEADERS.any { v.contains(it) }
    }

    fun isCdnHeaderName(name: String): Boolean {
        val n = name.lowercase()
        return CDN_HEADERS.any { n.startsWith(it) }
    }

    /**
     * Does [sni] appear in the certificate's subject alternative names?
     * A wildcard SAN covers exactly one label, per RFC 6125 — `*.example.com`
     * matches `www.example.com` but not `a.b.example.com` and not `example.com`.
     */
    fun sniMatchesSans(sni: String, sans: List<String>): Boolean {
        val name = sni.trim().removeSuffix(".").lowercase()
        if (name.isEmpty()) return false
        return sans.any { san ->
            val s = san.trim().removePrefix("DNS:").removeSuffix(".").lowercase()
            when {
                s.isEmpty() -> false
                s == name -> true
                s.startsWith("*.") -> {
                    val suffix = s.substring(2)
                    name.endsWith(".$suffix") && name.removeSuffix(".$suffix").indexOf('.') < 0
                }
                else -> false
            }
        }
    }

    fun evaluate(r: RealityProbeResult): RealityAssessment {
        val blockers = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val notes = mutableListOf<String>()

        if (r.error.isNotEmpty()) {
            return RealityAssessment(RealityVerdict.REJECT, 0,
                listOf("Probe failed: ${r.error}"), emptyList(), emptyList())
        }

        // ── hard requirements ──────────────────────────────────────────────
        if (!r.isTls13) {
            if (r.platformTls13Capable) {
                blockers.add("TLS 1.3 is required for REALITY; this donor negotiated ${r.tlsVersion.ifEmpty { "nothing" }}")
            } else {
                warnings.add("This Android version cannot negotiate TLS 1.3, so the donor's 1.3 support is unverified — re-test from a server")
            }
        }
        if (!r.certValid) blockers.add("Certificate does not validate: ${r.certError.ifEmpty { "unknown reason" }}")
        if (r.certExpired) blockers.add("Certificate expired ${-r.certDaysRemaining} days ago")
        if (!r.sniMatchesSan) blockers.add("The SNI is not covered by the certificate's SANs — the disguise gives itself away")
        if (r.isRedirect) blockers.add("Redirects with HTTP ${r.httpStatus} to ${r.redirectLocation.ifEmpty { "another host" }} — redirects break the REALITY handshake")
        if (r.behindCloudflare) blockers.add("Resolves to a Cloudflare edge: your server would become an open Cloudflare port-forwarder")

        // ── real downsides ─────────────────────────────────────────────────
        if (!r.hasH2) {
            if (r.platformAlpnCapable) {
                warnings.add("No HTTP/2 (ALPN \"${r.alpn.ifEmpty { "none" }}\") — the advertised ALPN will not match the real site")
            } else {
                notes.add("ALPN cannot be negotiated on this Android version, so HTTP/2 support is unverified")
            }
        }
        if (r.behindCdn && !r.behindCloudflare) warnings.add("Appears to sit behind a CDN (${r.serverHeader.ifEmpty { "header fingerprint" }}) — donor latency and fingerprints will not be stable")
        if (r.certExpiringSoon) warnings.add("Certificate expires in ${r.certDaysRemaining} days; the donor will need replacing")
        if (r.httpStatus in 400..499) {
            notes.add("HEAD returned HTTP ${r.httpStatus}; many sites refuse HEAD or data-centre IPs, and REALITY only borrows the TLS layer, so this is not a defect")
        }
        if (r.totalMs > 900) warnings.add("Handshake took ${r.totalMs} ms from this device — REALITY pays this on every connection")

        // ── informational ──────────────────────────────────────────────────
        notes.add("Post-quantum key exchange cannot be detected from Android's TLS stack; verify on the server before trusting this donor")
        notes.add("Certificate chain is ${r.certChainBytes} bytes, ${r.certPublicKeyAlg} key")
        if (r.hasH2 && r.isTls13) notes.add("TLS 1.3 + h2 negotiated, which is the combination REALITY expects")

        val score = score(r, blockers.size, warnings.size)
        val verdict = when {
            blockers.isNotEmpty() -> RealityVerdict.REJECT
            warnings.isEmpty() -> RealityVerdict.GOOD
            warnings.size <= 2 -> RealityVerdict.USABLE
            else -> RealityVerdict.RISKY
        }
        return RealityAssessment(verdict, score, blockers, warnings, notes)
    }

    /**
     * 0..100 over the properties that are actually measurable here. A donor
     * with blockers scores 0 regardless of how fast it is — speed cannot
     * compensate for a handshake that will not impersonate.
     */
    fun score(r: RealityProbeResult, blockerCount: Int, warningCount: Int): Int {
        if (blockerCount > 0) return 0
        var s = 0
        s += if (r.isTls13) 30 else 0
        s += if (r.certValid) 20 else 0
        s += if (r.sniMatchesSan) 15 else 0
        s += if (r.hasH2) 15 else 0
        s += if (!r.behindCdn) 10 else 0
        s += when {
            r.totalMs <= 200 -> 10
            r.totalMs <= 500 -> 7
            r.totalMs <= 900 -> 4
            else -> 0
        }
        s -= warningCount * 3
        return s.coerceIn(0, 100)
    }
}

/** Where the probe runs from — changes what the answer means. */
enum class RealityVantage(val label: String) {
    /** From this device: tells you what a client would experience. */
    PHONE("phone"),

    /**
     * From an agent: tells you whether the *server* can steal the handshake.
     * This is the one that decides if the donor actually works.
     */
    SERVER("server")
}

object RealitySniScanner {

    /** Well-known donors worth testing first, and the ones to avoid. */
    val SUGGESTED = listOf(
        "www.microsoft.com",
        "www.apple.com",
        "www.icloud.com",
        "updates.cdn-apple.com",
        "addons.mozilla.org",
        "www.samsung.com",
        "www.cisco.com",
        "www.asus.com",
        "www.amd.com",
        "www.nvidia.com",
    )

    /**
     * Names practitioners report as bad donors: Google properties increasingly
     * negotiate post-quantum key exchange, `www.google.com` is singled out in
     * the Xray guidance, and Cloudflare-fronted names turn your server into a
     * public port-forwarder.
     */
    val DISCOURAGED = mapOf(
        "www.google.com" to "Called out in the Xray REALITY guidance as an example only, not a donor to use",
        "dl.google.com" to "Documented for illustration only; most Google properties now negotiate post-quantum TLS",
        "speed.cloudflare.com" to "Cloudflare-fronted: your server would forward traffic for Cloudflare",
    )

    /** Normalises `example.com:8443` / `https://example.com` into an SNI and a port. */
    fun parseTarget(raw: String, defaultPort: Int = 443): Pair<String, Int>? {
        var t = raw.trim()
        if (t.isEmpty()) return null
        t = t.removePrefix("https://").removePrefix("http://")
        t = t.substringBefore('/')
        val port = if (':' in t) t.substringAfterLast(':').toIntOrNull() ?: return null else defaultPort
        val host = t.substringBeforeLast(':').removeSuffix(".").lowercase()
        if (host.isEmpty() || port !in 1..65535) return null
        // an IP literal cannot be an SNI donor: there is no certificate name to match
        if (Cidr.parseIp(host) != null) return null
        if (!host.contains('.')) return null
        return host to port
    }

    /**
     * @param alpnCapable overrides the platform capability probe. ALPN and
     * TLS 1.3 both need API 29; injecting this keeps [probe] runnable in a
     * plain JVM test, where reading android.os.Build throws.
     */
    suspend fun probe(
        sni: String,
        port: Int = 443,
        timeoutMs: Int = 8000,
        alpnCapable: Boolean = TlsCapability.alpn
    ): RealityProbeResult =
        withContext(Dispatchers.IO) {
            val started = System.nanoTime()
            var dnsMs = -1L
            var tcpMs = -1L
            var tlsMs = -1L
            var ip = ""
            var plain: Socket? = null
            var tls: SSLSocket? = null
            try {
                val t0 = System.nanoTime()
                val addrs = InetAddress.getAllByName(sni)
                dnsMs = (System.nanoTime() - t0) / 1_000_000
                if (addrs.isEmpty()) throw java.net.UnknownHostException("DNS returned no address")
                ip = addrs[0].hostAddress ?: ""

                val t1 = System.nanoTime()
                plain = Socket()
                plain.tcpNoDelay = true
                runCatching { plain.setSoLinger(true, 0) }
                plain.connect(InetSocketAddress(addrs[0], port), timeoutMs)
                tcpMs = (System.nanoTime() - t1) / 1_000_000

                // STRICT validation on purpose: a donor whose certificate does
                // not chain to a public CA cannot be impersonated convincingly,
                // and we want to report that rather than paper over it.
                val chainHolder = arrayOfNulls<Array<X509Certificate>>(1)
                val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                tmf.init(null as java.security.KeyStore?)
                val factory = SSLContext.getInstance("TLS")
                factory.init(null, tmf.trustManagers, java.security.SecureRandom())

                val t2 = System.nanoTime()
                tls = factory.socketFactory.createSocket(plain, sni, port, true) as SSLSocket
                tls.soTimeout = timeoutMs
                tls.useClientMode = true
                val tls13Capable = TlsCapability.tls13
                val params: SSLParameters = tls.sslParameters
                params.serverNames = listOf(SNIHostName(sni))
                val alpnApplied = alpnCapable &&
                    TlsCapability.applyAlpn(params, arrayOf("h2", "http/1.1"))
                // REALITY needs 1.3; ask for it explicitly so a donor that only
                // offers 1.2 is reported honestly instead of silently downgraded.
                params.protocols = if (tls13Capable) arrayOf("TLSv1.3", "TLSv1.2")
                    else tls.supportedProtocols
                tls.sslParameters = params
                // A handshake failure is reported by the outer catch, which
                // fills certError/certValid from the exception itself.
                val certError = ""
                tls.startHandshake()
                tlsMs = (System.nanoTime() - t2) / 1_000_000
                chainHolder[0] = tls.session?.peerCertificates
                    ?.filterIsInstance<X509Certificate>()?.toTypedArray()

                val session = tls.session
                val chain = chainHolder[0] ?: emptyArray()
                val leaf = chain.firstOrNull()
                val sans = leaf?.subjectAlternativeNames
                    ?.mapNotNull { if (it.size >= 2 && it[1] is String) it[1] as String else null }
                    .orEmpty()
                val alpn = if (alpnApplied) TlsCapability.negotiatedAlpn(tls) else ""

                // The redirect check runs on its own connection advertising
                // HTTP/1.1 only. Reusing `tls` was wrong: once ALPN negotiates
                // h2 the server expects binary framing, so a plain-text HEAD is
                // answered with a close and every h2 donor looked like it had
                // no redirect information at all.
                val http = httpCheck(addrs[0], sni, port, timeoutMs, alpnCapable)

                RealityProbeResult(
                    sni = sni, port = port, resolvedIp = ip,
                    dnsMs = dnsMs, tcpMs = tcpMs, tlsMs = tlsMs,
                    totalMs = (System.nanoTime() - started) / 1_000_000,
                    tlsVersion = session?.protocol ?: "",
                    alpn = alpn,
                    cipher = session?.cipherSuite ?: "",
                    certSubject = leaf?.subjectX500Principal?.name ?: "",
                    certIssuer = leaf?.issuerX500Principal?.name ?: "",
                    certSans = sans,
                    certDaysRemaining = leaf?.let { (it.notAfter.time - Date().time) / 86_400_000L } ?: 0L,
                    certPublicKeyAlg = leaf?.let { keyAlgName(it.publicKey.algorithm) } ?: "",
                    certChainBytes = chain.sumOf { runCatching { it.encoded.size }.getOrDefault(0) },
                    certValid = certError.isEmpty(),
                    certError = certError,
                    sniMatchesSan = RealityCriteria.sniMatchesSans(sni, sans),
                    httpStatus = http.status,
                    redirectLocation = http.location,
                    serverHeader = http.server,
                    behindCloudflare = Cidr.inAny(ip, CloudflareRanges.V4) ||
                        (RealityCriteria.isCdnServerHeader(http.server) && http.server.contains("cloudflare", true)),
                    behindCdn = RealityCriteria.isCdnServerHeader(http.server) || http.cdnHeader,
                    platformTls13Capable = tls13Capable,
                    platformAlpnCapable = alpnApplied,
                )
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Throwable) {
                RealityProbeResult(
                    sni = sni, port = port, resolvedIp = ip,
                    dnsMs = dnsMs, tcpMs = tcpMs, tlsMs = tlsMs,
                    totalMs = (System.nanoTime() - started) / 1_000_000,
                    tlsVersion = "", alpn = "", cipher = "",
                    certSubject = "", certIssuer = "", certSans = emptyList(),
                    certDaysRemaining = 0, certPublicKeyAlg = "", certChainBytes = 0,
                    certValid = false, certError = e.message ?: e.javaClass.simpleName,
                    sniMatchesSan = false, httpStatus = 0, redirectLocation = "",
                    serverHeader = "", behindCloudflare = false, behindCdn = false,
                    error = describe(e)
                )
            } finally {
                runCatching { tls?.close() }
                runCatching { plain?.close() }
            }
        }

    /** A short HEAD is enough to see a redirect without downloading a page. */
    private data class Head(val status: Int, val location: String, val server: String, val cdnHeader: Boolean)

    /**
     * Opens a separate HTTP/1.1-only TLS connection and issues a HEAD.
     * Failure here is not fatal: a donor that refuses HEAD is still usable,
     * TLS properties are what decide.
     */
    private fun httpCheck(
        addr: InetAddress,
        sni: String,
        port: Int,
        timeoutMs: Int,
        alpnCapable: Boolean
    ): Head {
        var plain: Socket? = null
        var tls: SSLSocket? = null
        return try {
            plain = Socket()
            plain.tcpNoDelay = true
            runCatching { plain.setSoLinger(true, 0) }
            plain.connect(InetSocketAddress(addr, port), timeoutMs)
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(null as java.security.KeyStore?)
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, tmf.trustManagers, java.security.SecureRandom())
            tls = ctx.socketFactory.createSocket(plain, sni, port, true) as SSLSocket
            tls.soTimeout = timeoutMs
            tls.useClientMode = true
            val params: SSLParameters = tls.sslParameters
            params.serverNames = listOf(SNIHostName(sni))
            if (alpnCapable) TlsCapability.applyAlpn(params, arrayOf("http/1.1"))
            tls.sslParameters = params
            tls.startHandshake()
            headRequest(tls, sni, timeoutMs)
        } catch (_: Throwable) {
            Head(0, "", "", false)
        } finally {
            runCatching { tls?.close() }
            runCatching { plain?.close() }
        }
    }

    private fun headRequest(socket: Socket, host: String, timeoutMs: Int): Head {
        return try {
            socket.soTimeout = timeoutMs.coerceAtMost(6000)
            val req = "HEAD / HTTP/1.1\r\nHost: $host\r\nUser-Agent: Mozilla/5.0\r\n" +
                "Accept: */*\r\nConnection: close\r\n\r\n"
            socket.getOutputStream().write(req.toByteArray(Charsets.US_ASCII))
            socket.getOutputStream().flush()
            val reader: BufferedReader = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
            val statusLine = reader.readLine() ?: return Head(0, "", "", false)
            val status = statusLine.split(' ').getOrNull(1)?.toIntOrNull() ?: 0
            var location = ""
            var server = ""
            var cdn = false
            var line: String?
            var read = 0
            while (reader.readLine().also { line = it } != null && read++ < 64) {
                val l = line!!.trim()
                if (l.isEmpty()) break
                val idx = l.indexOf(':')
                if (idx <= 0) continue
                val name = l.substring(0, idx).trim()
                val value = l.substring(idx + 1).trim()
                when {
                    name.equals("location", true) -> location = value
                    name.equals("server", true) -> server = value
                    RealityCriteria.isCdnHeaderName(name) -> cdn = true
                }
            }
            Head(status, location, server, cdn)
        } catch (_: Throwable) {
            // A donor that refuses HEAD is not disqualified — TLS is what matters.
            Head(0, "", "", false)
        }
    }

    private fun keyAlgName(alg: String): String = when {
        alg.contains("RSA", true) -> "RSA"
        alg.contains("EC", true) -> "EC"
        alg.contains("EdDSA", true) -> "EdDSA"
        else -> alg
    }

    private fun describe(e: Throwable): String = when (e) {
        is java.net.SocketTimeoutException -> "Timed out"
        is java.net.UnknownHostException -> "DNS did not resolve"
        is java.net.ConnectException -> "TCP connect refused or blocked"
        is javax.net.ssl.SSLHandshakeException -> "TLS handshake rejected: ${e.message ?: ""}"
        is javax.net.ssl.SSLException -> "TLS error: ${e.message ?: ""}"
        else -> e.message ?: e.javaClass.simpleName
    }
}

/**
 * ALPN and TLS 1.3 availability, discovered at runtime instead of assumed.
 *
 * `SSLParameters.setApplicationProtocols` and `SSLSocket.getApplicationProtocol`
 * are API 29 while minSdk is 26, so calling them directly is both a lint error
 * and a NoSuchMethodError on older devices. They are reached reflectively and
 * every failure is reported as "could not verify" rather than as a defect of
 * the remote side — an old phone must not be able to condemn a good donor.
 */
internal object TlsCapability {

    /** True when this platform's TLS stack can negotiate TLS 1.3 at all. */
    val tls13: Boolean by lazy {
        try {
            // getSupportedProtocols lives on SSLSocket, not on the factory, so
            // an unconnected socket is created purely to ask it.
            val probe = SSLContext.getInstance("TLS").socketFactory.createSocket() as SSLSocket
            try {
                probe.supportedProtocols.any { it == "TLSv1.3" }
            } finally {
                runCatching { probe.close() }
            }
        } catch (t: Throwable) {
            false
        }
    }

    /** True when ALPN can be advertised on this platform. */
    val alpn: Boolean by lazy { probeAlpn() }

    private fun probeAlpn(): Boolean = try {
        SSLParameters::class.java.getMethod("setApplicationProtocols", Array<String>::class.java)
        SSLSocket::class.java.getMethod("getApplicationProtocol")
        true
    } catch (t: Throwable) {
        false
    }

    /** Advertises [protocols]; returns false when the platform cannot. */
    fun applyAlpn(params: SSLParameters, protocols: Array<String>): Boolean = try {
        val m = SSLParameters::class.java.getMethod("setApplicationProtocols", Array<String>::class.java)
        m.invoke(params, protocols)
        true
    } catch (t: Throwable) {
        false
    }

    /** Reads the negotiated protocol, or "" when unavailable. */
    fun negotiatedAlpn(socket: SSLSocket): String = try {
        val m = SSLSocket::class.java.getMethod("getApplicationProtocol")
        (m.invoke(socket) as? String).orEmpty()
    } catch (t: Throwable) {
        ""
    }
}
