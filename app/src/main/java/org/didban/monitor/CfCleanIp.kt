package org.didban.monitor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

/**
 * Cloudflare "clean IP" discovery.
 *
 * A clean IP is a Cloudflare edge address that still completes a TLS handshake
 * and answers `/cdn-cgi/trace` from the user's own network — on filtered
 * networks many edges are throttled or dropped, so the reachable subset changes
 * per ISP and over time. That is why this runs on the phone and not on an
 * agent: the answer that matters is "clean from *here*".
 *
 * Design notes carried over from the reference scanners (SenPaiScanner's Go
 * `internal/prober` and asha_scanner's Kotlin port of it):
 * - dial the literal IP, never resolve it, so probing does not touch DNS;
 * - `setSoLinger(true, 0)` — thousands of probes would otherwise pile up in
 *   TIME_WAIT and exhaust local ports;
 * - split the timeout budget dial 1/4, TLS 1/2, HTTP 1/4;
 * - rotate the SNI between attempts so one probe run does not look like a
 *   single client hammering one name;
 * - read `colo=` out of the trace body to learn which Cloudflare data centre
 *   answered, which is how PoP reachability is reported.
 *
 * Everything that decides *which* IPs to try and *whether a result is good* is
 * a pure function in this file so it can be unit-tested without a network.
 */

/** How a candidate edge is probed. */
enum class CfProbeMode { TCP, TLS, HTTP }

/** Cloudflare's published ranges — https://www.cloudflare.com/ips/ */
object CloudflareRanges {
    val V4 = listOf(
        "173.245.48.0/20",
        "103.21.244.0/22",
        "103.22.200.0/22",
        "103.31.4.0/22",
        "141.101.64.0/18",
        "108.162.192.0/18",
        "190.93.240.0/20",
        "188.114.96.0/20",
        "197.234.240.0/22",
        "198.41.128.0/17",
        "162.158.0.0/15",
        "104.16.0.0/13",
        "104.24.0.0/14",
        "172.64.0.0/13",
        "131.0.72.0/22",
    )

    val V6 = listOf(
        "2400:cb00::/32",
        "2606:4700::/32",
        "2803:f800::/32",
        "2405:b500::/32",
        "2405:8100::/32",
        "2a06:98c0::/29",
        "2c0f:f248::/32",
    )

    /** Ports Cloudflare terminates TLS on. */
    val TLS_PORTS = listOf(443, 2053, 2083, 2087, 2096, 8443)

    /** Ports Cloudflare terminates plain HTTP on. */
    val HTTP_PORTS = listOf(80, 8080, 8880, 2052, 2082, 2086, 2095)

    fun isTlsPort(port: Int): Boolean = port in TLS_PORTS

    fun isHttpPort(port: Int): Boolean = port in HTTP_PORTS

    /**
     * Well-known Cloudflare-fronted names, rotated across probe attempts.
     * `speed.cloudflare.com` is first because it is built for measurement and
     * answers `/cdn-cgi/trace` without caching surprises.
     */
    val SNIS = listOf(
        "speed.cloudflare.com",
        "cloudflare.com",
        "www.cloudflare.com",
        "cp.cloudflare.com",
        "dash.cloudflare.com",
    )
}

/** Minimal CIDR handling for IPv4 — pure so the plan and CDN checks are testable. */
object Cidr {

    data class V4(val network: Long, val prefix: Int) {
        val size: Long get() = 1L shl (32 - prefix)
        fun contains(ip: Long): Boolean =
            prefix == 32 && ip == network || (ip and mask()) == network
        fun mask(): Long = if (prefix == 0) 0L else (-1L shl (32 - prefix)) and 0xFFFFFFFFL
        fun first(): Long = network
        fun last(): Long = network + size - 1
    }

    fun parseIp(ip: String): Long? {
        val parts = ip.split('.')
        if (parts.size != 4) return null
        var out = 0L
        for (p in parts) {
            val v = p.toIntOrNull() ?: return null
            if (v < 0 || v > 255) return null
            out = (out shl 8) or v.toLong()
        }
        return out
    }

    fun formatIp(v: Long): String =
        "${(v shr 24) and 0xFF}.${(v shr 16) and 0xFF}.${(v shr 8) and 0xFF}.${v and 0xFF}"

    fun parse(cidr: String): V4? {
        val (ip, prefix) = cidr.split('/', limit = 2).let {
            if (it.size != 2) return null else it[0] to (it[1].toIntOrNull() ?: return null)
        }
        if (prefix < 0 || prefix > 32) return null
        val net = parseIp(ip) ?: return null
        val masked = net and (if (prefix == 0) 0L else (-1L shl (32 - prefix)) and 0xFFFFFFFFL)
        return V4(masked, prefix)
    }

    /** True when [ip] (dotted quad) falls inside any of [cidrs]. */
    fun inAny(ip: String, cidrs: List<String>): Boolean {
        val v = parseIp(ip) ?: return false
        return cidrs.any { c -> parse(c)?.contains(v) == true }
    }
}

/** One candidate edge to probe. */
data class CfCandidate(val ip: String, val port: Int)

/**
 * Builds the list of addresses to try.
 *
 * Random sampling inside the published ranges is what makes a scan finish at
 * all — 104.16.0.0/13 alone is half a million addresses. [seed] makes a run
 * reproducible, which is what the unit tests rely on.
 */
object CfIpPlan {

    /**
     * [count] random addresses drawn from [ranges], deduplicated and in
     * ascending order so two runs with the same seed produce the same plan.
     */
    fun randomV4(ranges: List<String>, count: Int, seed: Long, rng: Random = Random(seed)): List<String> {
        val parsed = ranges.mapNotNull { Cidr.parse(it) }.filter { it.prefix <= 30 }
        if (parsed.isEmpty() || count <= 0) return emptyList()
        val totalWeight = parsed.sumOf { it.size.toDouble() }
        val out = LinkedHashSet<String>()
        var guard = count.toLong() * 40 + 1000
        while (out.size < count && guard-- > 0) {
            // pick a range in proportion to its size, then an offset inside it
            var pick = rng.nextDouble() * totalWeight
            var chosen = parsed[0]
            for (r in parsed) {
                pick -= r.size.toDouble()
                if (pick <= 0) {
                    chosen = r
                    break
                }
            }
            val span = chosen.size
            val offset = if (span <= 1) 0L else (rng.nextDouble() * (span - 1)).toLong()
            out.add(Cidr.formatIp(chosen.first() + offset))
        }
        return out.sortedBy { Cidr.parseIp(it) ?: 0L }
    }

    /** Expands explicit CIDRs the user typed into individual addresses (capped). */
    fun expand(cidrs: List<String>, cap: Int): List<String> {
        val out = LinkedHashSet<String>()
        for (c in cidrs) {
            val r = Cidr.parse(c.trim()) ?: continue
            var ip = r.first()
            val last = r.last()
            while (ip <= last && out.size < cap) {
                out.add(Cidr.formatIp(ip))
                ip++
            }
            if (out.size >= cap) break
        }
        return out.toList()
    }

    /** Parses a pasted/loaded list: one IP or CIDR per line, `#` comments allowed. */
    fun parseList(text: String, cap: Int): List<String> {
        val cidrs = mutableListOf<String>()
        val singles = mutableListOf<String>()
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            if ('/' in line) cidrs.add(line) else if (Cidr.parseIp(line) != null) singles.add(line)
        }
        val out = LinkedHashSet<String>()
        out.addAll(singles)
        out.addAll(expand(cidrs, cap))
        return out.take(cap).toList()
    }
}

/** Result of probing one candidate. */
data class CfProbeResult(
    val ip: String,
    val port: Int,
    val mode: CfProbeMode,
    val latenciesMs: List<Long>,
    val attempts: Int,
    val tlsOk: Boolean,
    val httpStatus: Int,
    val colo: String,
    val tlsVersion: String = "",
    val cipher: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    val successes: Int get() = latenciesMs.size
    val loss: Double get() = if (attempts == 0) 1.0 else 1.0 - successes.toDouble() / attempts
    val avgLatencyMs: Double get() = if (latenciesMs.isEmpty()) 0.0 else latenciesMs.average()
    val minLatencyMs: Long get() = latenciesMs.minOrNull() ?: 0L
    val maxLatencyMs: Long get() = latenciesMs.maxOrNull() ?: 0L
    val jitterMs: Double
        get() {
            if (latenciesMs.size < 2) return 0.0
            val mean = avgLatencyMs
            return Math.sqrt(latenciesMs.sumOf { (it - mean) * (it - mean) } / latenciesMs.size)
        }
    val healthy: Boolean get() = CfHealth.isHealthy(this)
}

/**
 * Whether a probe result counts as a usable edge.
 *
 * Deliberately strict about the mode the user asked for: in HTTP mode on a TLS
 * port a completed TCP dial is not enough, the trace response has to come back.
 */
object CfHealth {
    fun isHealthy(r: CfProbeResult): Boolean {
        if (r.latenciesMs.isEmpty()) return false
        if (r.loss > 0.5) return false
        if (r.avgLatencyMs <= 0.0) return false
        return when (r.mode) {
            CfProbeMode.TCP -> true
            CfProbeMode.TLS -> r.tlsOk
            CfProbeMode.HTTP -> {
                if (CloudflareRanges.isTlsPort(r.port) && !r.tlsOk) return false
                r.httpStatus in 200..399 || r.colo.isNotEmpty()
            }
        }
    }
}

/**
 * Ranks healthy edges. Lower is better.
 *
 * Latency dominates, but loss and jitter are not ignored: an edge that answers
 * in 40 ms half the time is worse than a steady 90 ms one for a tunnel that
 * has to hold a session open.
 */
object CfRanker {
    fun score(r: CfProbeResult): Double {
        if (!r.healthy) return Double.MAX_VALUE
        val latency = r.avgLatencyMs
        val lossPenalty = r.loss * 400.0
        val jitterPenalty = r.jitterMs * 0.5
        val attemptBonus = if (r.attempts > r.successes) 10.0 else 0.0
        // A colo means the trace body really came from a Cloudflare edge rather
        // than something else answering on that port, so it is worth a small
        // preference.
        val coloBonus = if (r.colo.isEmpty()) 15.0 else 0.0
        return latency + lossPenalty + jitterPenalty + attemptBonus + coloBonus
    }

    fun best(results: List<CfProbeResult>, limit: Int = 50): List<CfProbeResult> =
        results.filter { it.healthy }.sortedBy { score(it) }.take(limit)
}

/** Scan settings. Defaults are conservative on purpose. */
data class CfScanConfig(
    val mode: CfProbeMode = CfProbeMode.HTTP,
    val port: Int = 443,
    val count: Int = 256,
    val tries: Int = 2,
    val timeoutMs: Long = 3000,
    val concurrency: Int = 48,
    val sniOverride: String = "",
    val seed: Long = System.currentTimeMillis(),
    /** Split the HTTP request into small chunks — helps on DPI-heavy paths. */
    val fragment: Boolean = false
) {
    companion object {
        const val MAX_COUNT = 4096
        const val MAX_CONCURRENCY = 128
        const val MIN_TIMEOUT_MS = 800L
        const val MAX_TIMEOUT_MS = 15000L

        /** Clamps user input into ranges that cannot turn into a self-DoS. */
        fun sanitize(c: CfScanConfig): CfScanConfig = c.copy(
            count = c.count.coerceIn(1, MAX_COUNT),
            tries = c.tries.coerceIn(1, 8),
            timeoutMs = c.timeoutMs.coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS),
            concurrency = c.concurrency.coerceIn(1, MAX_CONCURRENCY),
            sniOverride = c.sniOverride.trim().take(253)
        )
    }
}

/** Live progress of a running scan. */
data class CfScanProgress(
    val total: Int,
    val done: Int,
    val healthyFound: Int,
    val currentBest: CfProbeResult?
)

/** Low-level socket/TLS helpers used by the scanner. */
object CfTransport {

    private val trustedContext: SSLContext by lazy {
        // The scanner still dials the candidate IP directly, but a reachable
        // endpoint only counts when it presents a publicly trusted certificate
        // for the requested Cloudflare SNI. This prevents a captive portal or
        // active interceptor from being reported as a clean edge.
        SSLContext.getDefault()
    }

    fun dial(ip: String, port: Int, connectTimeoutMs: Int): Socket {
        val socket = Socket()
        socket.tcpNoDelay = true
        runCatching { socket.setSoLinger(true, 0) }
        // getByName on a literal does not hit a resolver
        val addr = InetAddress.getByName(ip)
        socket.connect(InetSocketAddress(addr, port), connectTimeoutMs)
        return socket
    }

    fun handshake(plain: Socket, ip: String, port: Int, sni: String, alpn: List<String>, timeoutMs: Int): SSLSocket {
        require(sni.isNotBlank()) { "a verified TLS probe requires an SNI hostname" }
        val ssl = trustedContext.socketFactory.createSocket(plain, sni, port, true) as SSLSocket
        ssl.soTimeout = timeoutMs
        ssl.useClientMode = true
        val params = ssl.sslParameters
        params.endpointIdentificationAlgorithm = "HTTPS"
        params.serverNames = listOf(SNIHostName(sni))
        if (alpn.isNotEmpty()) TlsCapability.applyAlpn(params, alpn.toTypedArray())
        ssl.sslParameters = params
        ssl.startHandshake()
        return ssl
    }

    data class Trace(val status: Int, val colo: String)

    private val coloRegex = Regex("colo=([A-Za-z0-9]+)")

    /** `GET /cdn-cgi/trace` and pull the status line plus the `colo=` field. */
    fun trace(socket: Socket, hostHeader: String, timeoutMs: Int, fragment: Boolean): Trace? {
        socket.soTimeout = timeoutMs
        val host = hostHeader.ifBlank { CloudflareRanges.SNIS[0] }
        val req = buildString {
            append("GET /cdn-cgi/trace HTTP/1.1\r\n")
            append("Host: ").append(host).append("\r\n")
            append("User-Agent: Mozilla/5.0\r\n")
            append("Accept: */*\r\n")
            append("Connection: close\r\n\r\n")
        }
        val bytes = req.toByteArray(Charsets.US_ASCII)
        val out = socket.getOutputStream()
        if (fragment) {
            var i = 0
            while (i < bytes.size) {
                val len = minOf(2, bytes.size - i)
                out.write(bytes, i, len)
                out.flush()
                i += len
                Thread.sleep(1L + Random.nextLong(4))
            }
        } else {
            out.write(bytes)
            out.flush()
        }
        val reader: BufferedReader = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
        val statusLine = reader.readLine() ?: return null
        val status = statusLine.split(' ').getOrNull(1)?.toIntOrNull() ?: 0
        var colo = ""
        val body = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            body.append(line).append('\n')
            coloRegex.find(line!!)?.let { colo = it.groupValues[1] }
            if (body.length > 8192) break
        }
        return Trace(status, colo)
    }
}

/**
 * Runs a scan. Emits progress and returns every result (healthy or not) so the
 * UI can show loss statistics, not just the winners.
 */
object CloudflareIpScanner {

    suspend fun scan(
        candidates: List<CfCandidate>,
        config: CfScanConfig,
        onProgress: (CfScanProgress) -> Unit = {}
    ): List<CfProbeResult> {
        val cfg = CfScanConfig.sanitize(config)
        val gate = Semaphore(cfg.concurrency)
        val results = java.util.Collections.synchronizedList(mutableListOf<CfProbeResult>())
        val done = java.util.concurrent.atomic.AtomicInteger()
        val found = java.util.concurrent.atomic.AtomicInteger()
        var best: CfProbeResult? = null
        val bestLock = Any()

        // coroutineScope only joins its children once the block returns, so the
        // results must be read *after* it — returning them from inside the block
        // hands back an empty list, because the children have not run yet.
        coroutineScope {
            for (c in candidates) {
                coroutineContext.ensureActive()
                launch {
                    gate.withPermit {
                        val r = probe(c.ip, c.port, cfg)
                        results.add(r)
                        val d = done.incrementAndGet()
                        if (r.healthy) {
                            found.incrementAndGet()
                            synchronized(bestLock) {
                                if (best == null || CfRanker.score(r) < CfRanker.score(best!!)) best = r
                            }
                        }
                        if (d % 8 == 0 || d == candidates.size) {
                            synchronized(bestLock) {
                                onProgress(CfScanProgress(candidates.size, d, found.get(), best))
                            }
                        }
                    }
                }
            }
        }
        return results.toList()
    }

    /** Builds the candidate list for a config (random plan, or an explicit list). */
    fun plan(ips: List<String>, config: CfScanConfig): List<CfCandidate> =
        ips.map { CfCandidate(it, config.port) }

    suspend fun probe(ip: String, port: Int, config: CfScanConfig): CfProbeResult = withContext(Dispatchers.IO) {
        val cfg = CfScanConfig.sanitize(config)
        val latencies = ArrayList<Long>(cfg.tries)
        var tlsOk = false
        var httpStatus = 0
        var colo = ""
        var tlsVersion = ""
        var cipher = ""
        var attempts = 0

        val total = cfg.timeoutMs.toInt().coerceAtLeast(800)
        val dialTo = (total / 4).coerceAtLeast(200)
        val tlsTo = (total / 2).coerceAtLeast(300)
        val httpTo = (total / 4).coerceAtLeast(200)
        val useTls = CloudflareRanges.isTlsPort(port) ||
            (cfg.mode != CfProbeMode.TCP && !CloudflareRanges.isHttpPort(port))

        repeat(cfg.tries) { attempt ->
            attempts++
            val sni = chooseSni(attempt, cfg.sniOverride)
            val start = System.nanoTime()
            var socket: Socket? = null
            var ssl: SSLSocket? = null
            try {
                socket = CfTransport.dial(ip, port, dialTo)
                when (cfg.mode) {
                    CfProbeMode.TCP -> latencies.add(elapsedMs(start))
                    CfProbeMode.TLS -> {
                        if (useTls) {
                            ssl = CfTransport.handshake(socket, ip, port, sni, emptyList(), tlsTo)
                            tlsOk = true
                            tlsVersion = ssl.session?.protocol ?: ""
                            cipher = ssl.session?.cipherSuite ?: ""
                            latencies.add(elapsedMs(start))
                        } else {
                            latencies.add(elapsedMs(start))
                        }
                    }
                    CfProbeMode.HTTP -> {
                        val target: Socket = if (useTls) {
                            ssl = CfTransport.handshake(socket, ip, port, sni, listOf("http/1.1"), tlsTo)
                            tlsOk = true
                            tlsVersion = ssl.session?.protocol ?: ""
                            cipher = ssl.session?.cipherSuite ?: ""
                            ssl
                        } else socket
                        val resp = CfTransport.trace(target, sni, httpTo, cfg.fragment)
                        if (resp != null) {
                            httpStatus = resp.status
                            if (resp.colo.isNotEmpty()) colo = resp.colo
                            if (resp.status in 200..399 || resp.colo.isNotEmpty()) {
                                latencies.add(elapsedMs(start))
                            }
                        }
                    }
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (_: Throwable) {
                // a failed attempt is recorded as loss, not as an error
            } finally {
                runCatching { ssl?.close() }
                runCatching { socket?.close() }
            }
            if (attempt < cfg.tries - 1) delay(10L + Random.nextLong(40))
        }

        CfProbeResult(
            ip = ip, port = port, mode = cfg.mode, latenciesMs = latencies,
            attempts = attempts, tlsOk = tlsOk, httpStatus = httpStatus, colo = colo,
            tlsVersion = tlsVersion, cipher = cipher
        )
    }

    /** Explicit override wins; otherwise rotate the Cloudflare fronts. */
    fun chooseSni(attempt: Int, override: String): String {
        val o = override.trim()
        if (o.isNotEmpty()) return o
        val list = CloudflareRanges.SNIS
        return list[Math.floorMod(attempt, list.size)]
    }

    private fun elapsedMs(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000
}
