package org.didban.monitor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

// ── Common Port Database ─────────────────────────────────────────────────────

val COMMON_PORTS = listOf(
    21 to "FTP",
    22 to "SSH",
    23 to "Telnet",
    25 to "SMTP",
    53 to "DNS",
    80 to "HTTP",
    110 to "POP3",
    143 to "IMAP",
    443 to "HTTPS",
    465 to "SMTPS",
    587 to "Submission",
    993 to "IMAPS",
    995 to "POP3S",
    1433 to "MSSQL",
    1521 to "Oracle",
    2052 to "Cloudflare HTTP",
    2053 to "Cloudflare HTTPS",
    2082 to "cPanel",
    2083 to "cPanel SSL",
    3000 to "Node/Grafana",
    3306 to "MySQL",
    3389 to "RDP",
    5432 to "PostgreSQL",
    5900 to "VNC",
    6379 to "Redis",
    8000 to "HTTP Alt",
    8080 to "HTTP Proxy",
    8443 to "HTTPS Alt",
    8686 to "Didban Agent",
    9000 to "Portainer",
    9090 to "Prometheus",
    27017 to "MongoDB"
)

data class PortScanResult(
    val port: Int,
    val service: String,
    val isOpen: Boolean,
    val latencyMs: Long
)

// ── Port Scanner ─────────────────────────────────────────────────────────────

object PortScanner {
    suspend fun scanSinglePort(host: String, port: Int, timeoutMs: Int = 1200): PortScanResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val serviceName = COMMON_PORTS.firstOrNull { it.first == port }?.second ?: "Unknown"
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                val lat = System.currentTimeMillis() - t0
                PortScanResult(port, serviceName, isOpen = true, latencyMs = lat)
            }
        } catch (_: Exception) {
            PortScanResult(port, serviceName, isOpen = false, latencyMs = -1)
        }
    }

    suspend fun scanPorts(
        host: String,
        ports: List<Int>,
        concurrency: Int = 15,
        timeoutMs: Int = 1200,
        onResult: (PortScanResult) -> Unit
    ) = withContext(Dispatchers.IO) {
        val chunks = ports.chunked(concurrency)
        for (chunk in chunks) {
            coroutineScope {
                val tasks = chunk.map { port ->
                    async {
                        val res = scanSinglePort(host, port, timeoutMs)
                        onResult(res)
                        res
                    }
                }
                tasks.awaitAll()
            }
        }
    }
}

// ── SSL / TLS Certificate Inspector ──────────────────────────────────────────

data class SslCertInfo(
    val host: String,
    val port: Int,
    val subject: String,
    val issuer: String,
    val validFrom: String,
    val validTo: String,
    val daysRemaining: Long,
    val isExpired: Boolean,
    val sans: List<String>,
    val serialNumber: String,
    val sigAlg: String,
    val fingerprintSha256: String
)

object SslInspector {
    suspend fun inspect(host: String, port: Int = 443, timeoutMs: Int = 8000): SslCertInfo = withContext(Dispatchers.IO) {
        var certChain: Array<X509Certificate>? = null

        val tm = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                certChain = chain
            }
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(tm), null)

        val socket = sslContext.socketFactory.createSocket() as SSLSocket
        try {
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            socket.soTimeout = timeoutMs
            socket.startHandshake()

            val chain = certChain ?: throw Exception("No certificate returned from server")
            val cert = chain[0]

            val now = Date()
            val expiry = cert.notAfter
            val diffMs = expiry.time - now.time
            val days = diffMs / (1000 * 60 * 60 * 24)

            val sha = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
            val fp = sha.joinToString(":") { String.format("%02X", it) }

            val sans = mutableListOf<String>()
            try {
                val altNames = cert.subjectAlternativeNames
                if (altNames != null) {
                    for (item in altNames) {
                        if (item.size >= 2 && item[1] is String) {
                            sans.add(item[1] as String)
                        }
                    }
                }
            } catch (_: Exception) {}

            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

            SslCertInfo(
                host = host,
                port = port,
                subject = cert.subjectX500Principal.name,
                issuer = cert.issuerX500Principal.name,
                validFrom = fmt.format(cert.notBefore),
                validTo = fmt.format(cert.notAfter),
                daysRemaining = days,
                isExpired = days < 0,
                sans = sans,
                serialNumber = cert.serialNumber.toString(16).uppercase(Locale.US),
                sigAlg = cert.sigAlgName,
                fingerprintSha256 = fp
            )
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }
}

// ── Censorship & DPI / Handshake Diagnostic ──────────────────────────────────

data class CensorshipDiagnosticResult(
    val host: String,
    val port: Int,
    val tcpReachable: Boolean,
    val tlsReachable: Boolean,
    val isFiltered: Boolean,
    val diagnosis: String,
    val latencyMs: Long,
    val details: String
)

object CensorshipTester {
    suspend fun diagnose(host: String, port: Int = 443, timeoutMs: Int = 4000): CensorshipDiagnosticResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        var tcpOk = false
        var tlsOk = false
        var isFiltered = false
        var diagnosis = ""
        var detail = ""

        // Step 1: Raw TCP Socket Test
        try {
            Socket().use { s ->
                s.connect(InetSocketAddress(host, port), timeoutMs)
                tcpOk = true
            }
        } catch (e: SocketTimeoutException) {
            tcpOk = false
            isFiltered = true
            diagnosis = "🔴 IP / Port Completely Blocked (TCP SYN Timeout)"
            detail = "Packets are being dropped by firewall/ISP routing blackhole."
        } catch (e: IOException) {
            val msg = e.message ?: ""
            if (msg.contains("reset", ignoreCase = true) || msg.contains("RST", ignoreCase = true)) {
                tcpOk = false
                isFiltered = true
                diagnosis = "🔴 DPI Filtered (TCP Reset RST Injection)"
                detail = "The ISP's Deep Packet Inspection (DPI) firewall actively injected a TCP RST packet to kill the connection."
            } else if (msg.contains("refused", ignoreCase = true)) {
                tcpOk = false
                isFiltered = false
                diagnosis = "🟡 Port Closed (Connection Refused)"
                detail = "Server is reachable but no service is listening on port $port."
            } else {
                tcpOk = false
                isFiltered = true
                diagnosis = "🔴 TCP Connection Failed: $msg"
                detail = msg
            }
        }

        // Step 2: TLS Handshake Test (if TCP succeeded)
        if (tcpOk) {
            try {
                val cert = SslInspector.inspect(host, port, timeoutMs)
                tlsOk = true
                isFiltered = false
                diagnosis = "🟢 Clean & Reachable (No DPI Filter Detected)"
                detail = "TCP Handshake and TLS negotiation completed smoothly with certificate '${cert.subject}'."
            } catch (e: Exception) {
                tlsOk = false
                isFiltered = true
                diagnosis = "🔴 TLS Interception / SNI Filtered"
                detail = "TCP connected, but TLS Handshake was killed or timed out (${e.message})."
            }
        }

        val lat = System.currentTimeMillis() - t0
        CensorshipDiagnosticResult(
            host = host,
            port = port,
            tcpReachable = tcpOk,
            tlsReachable = tlsOk,
            isFiltered = isFiltered,
            diagnosis = diagnosis,
            latencyMs = lat,
            details = detail
        )
    }
}

// ── Multi-Provider IP & GeoIP Info (HTTPS with Multi-Fallback) ──────────────

data class GeoIpData(
    val ip: String,
    val country: String,
    val countryCode: String,
    val flag: String,
    val region: String,
    val city: String,
    val isp: String,
    val org: String,
    val asn: String,
    val timezone: String,
    val lat: Double,
    val lon: Double,
    val provider: String
)

object IpInfoService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun lookup(targetInput: String = ""): GeoIpData = withContext(Dispatchers.IO) {
        val clean = targetInput.trim().removePrefix("https://").removePrefix("http://").substringBefore("/")

        // Step 1: If input is a domain name, resolve to IP first
        var resolvedIp = clean
        if (clean.isNotEmpty() && !clean.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$"))) {
            try {
                val inet = InetAddress.getByName(clean)
                resolvedIp = inet.hostAddress ?: clean
            } catch (_: Exception) {
                // If DNS fails, continue with original input
            }
        }

        // Attempt 1: HTTPS via ipwho.is (Secure, fast, rich data)
        try {
            val httpsUrl = if (resolvedIp.isEmpty()) "https://ipwho.is/" else "https://ipwho.is/$resolvedIp"
            val req = Request.Builder().url(httpsUrl).header("User-Agent", "Didban/1.0").build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val j = JSONObject(body)
                    if (j.optBoolean("success", true)) {
                        val cc = j.optString("country_code", "")
                        val conn = j.optJSONObject("connection")
                        val tz = j.optJSONObject("timezone")
                        return@withContext GeoIpData(
                            ip = j.optString("ip", resolvedIp),
                            country = j.optString("country", "Unknown"),
                            countryCode = cc,
                            flag = CheckHostService.flagForCountry(cc),
                            region = j.optString("region", ""),
                            city = j.optString("city", ""),
                            isp = conn?.optString("isp", "") ?: j.optString("isp", ""),
                            org = conn?.optString("org", "") ?: j.optString("org", ""),
                            asn = conn?.optString("asn", "")?.let { if (it.isNotBlank() && !it.startsWith("AS", ignoreCase = true)) "AS$it" else it } ?: "",
                            timezone = tz?.optString("id", "") ?: "",
                            lat = j.optDouble("latitude", 0.0),
                            lon = j.optDouble("longitude", 0.0),
                            provider = "ipwho.is (HTTPS)"
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        // Attempt 2: ip-api.com (Fallback with cleartext enabled)
        try {
            val apiUrl = if (clean.isEmpty()) "http://ip-api.com/json" else "http://ip-api.com/json/$clean"
            val req = Request.Builder().url(apiUrl).build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val j = JSONObject(body)
                    if (j.optString("status") != "fail") {
                        val cc = j.optString("countryCode", "")
                        return@withContext GeoIpData(
                            ip = j.optString("query", resolvedIp),
                            country = j.optString("country", "Unknown"),
                            countryCode = cc,
                            flag = CheckHostService.flagForCountry(cc),
                            region = j.optString("regionName", ""),
                            city = j.optString("city", ""),
                            isp = j.optString("isp", ""),
                            org = j.optString("org", ""),
                            asn = j.optString("as", ""),
                            timezone = j.optString("timezone", ""),
                            lat = j.optDouble("lat", 0.0),
                            lon = j.optDouble("lon", 0.0),
                            provider = "ip-api"
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        throw Exception("امکان دریافت موقعیت و اطلاعات برای این آدرس مقدور نبود (بررسی کنید اینترنت و نام دامنه معتبر باشد)")
    }
}

// ── TCP & Socket Pinger ──────────────────────────────────────────────────────

object TcpPinger {
    suspend fun ping(host: String, port: Int = 80, timeoutMs: Int = 2000): Long = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        Socket().use { s ->
            s.connect(InetSocketAddress(host, port), timeoutMs)
            System.currentTimeMillis() - t0
        }
    }
}
