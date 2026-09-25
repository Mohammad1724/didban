package org.didban.monitor

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

data class ParsedProxyConfig(
    val rawUri: String,
    val protocol: String, // VLESS, VMESS, TROJAN, SHADOWSOCKS, WIREGUARD
    val remark: String,
    val host: String,
    val port: Int,
    val id: String,
    val transport: String = "tcp",
    val security: String = "none",
    val sni: String = "",
    val path: String = "",
    var pingMs: Long = -1L,
    var tlsSuccess: Boolean = false,
    var testStatus: String = "Ready"
)

enum class ProxyFailureKind {
    INVALID_URL,
    HTTP_STATUS,
    NETWORK
}

data class ProxyFailure(
    val kind: ProxyFailureKind,
    val detail: String = ""
) {
    fun localized(copy: CommandCopy): String = when (kind) {
        ProxyFailureKind.INVALID_URL -> copy.wtSubscriptionInvalidUrl
        ProxyFailureKind.HTTP_STATUS -> copy.wtSubscriptionHttpFailure.replace("%1", detail)
        ProxyFailureKind.NETWORK -> copy.wtSubscriptionNetworkFailure.replace("%1", detail.ifBlank { copy.wtSubscriptionFailed })
    }
}

class ProxySubscriptionException(val failure: ProxyFailure) : Exception(failure.kind.name)

data class SubscriptionInfo(
    val uploadBytes: Long = 0L,
    val downloadBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val expireTimestamp: Long = 0L,
    val configs: List<ParsedProxyConfig> = emptyList()
) {
    val usedBytes: Long get() = uploadBytes + downloadBytes
    val usedFormatted: String get() = formatBytes(usedBytes)

    fun totalFormatted(copy: CommandCopy): String =
        if (totalBytes > 0) formatBytes(totalBytes) else copy.wtUnlimited

    /** Legacy Persian projection kept for callers outside the command screen. */
    @Deprecated("Use totalFormatted(copy) so the active locale is explicit")
    val totalFormatted: String get() = totalFormatted(CommandCopyFa)

    fun expireDateFormatted(copy: CommandCopy): String = if (expireTimestamp > 0) {
        try {
            SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(expireTimestamp * 1000L))
        } catch (_: Exception) { copy.unknownState }
    } else copy.wtUnlimited

    /** Legacy Persian projection kept for callers outside the command screen. */
    @Deprecated("Use expireDateFormatted(copy) so the active locale is explicit")
    val expireDateFormatted: String get() = expireDateFormatted(CommandCopyFa)

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 GB"
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return "${String.format(Locale.US, "%.2f", gb)} GB"
    }
}

object ProxyEngine {

    private val httpClient = OkHttpClient.Builder()
        .dns(PublicOnlyDns)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    /**
     * Parses a raw proxy config URL (VLESS, VMess, Trojan, SS).
     */
    fun parseConfig(uriStr: String): ParsedProxyConfig? {
        val trimmed = uriStr.trim()
        if (trimmed.isBlank()) return null

        try {
            when {
                trimmed.startsWith("vless://", ignoreCase = true) -> {
                    val raw = trimmed.removePrefix("vless://")
                    val remark = if (raw.contains("#")) URLDecoder.decode(raw.substringAfter("#"), "UTF-8") else "VLESS Config"
                    val mainPart = raw.substringBefore("#")
                    val uuid = mainPart.substringBefore("@")
                    val rest = mainPart.substringAfter("@")
                    val hostPort = rest.substringBefore("?")
                    val host = hostPort.substringBefore(":")
                    val port = hostPort.substringAfter(":").toIntOrNull() ?: 443

                    val queryMap = parseQuery(mainPart.substringAfter("?", ""))
                    val sec = queryMap["security"] ?: "none"
                    val sni = queryMap["sni"] ?: queryMap["host"] ?: host
                    val type = queryMap["type"] ?: "tcp"
                    val path = queryMap["path"] ?: ""

                    return ParsedProxyConfig(
                        rawUri = trimmed,
                        protocol = "VLESS",
                        remark = remark,
                        host = host,
                        port = port,
                        id = uuid,
                        transport = type,
                        security = sec,
                        sni = sni,
                        path = path
                    )
                }

                trimmed.startsWith("trojan://", ignoreCase = true) -> {
                    val raw = trimmed.removePrefix("trojan://")
                    val remark = if (raw.contains("#")) URLDecoder.decode(raw.substringAfter("#"), "UTF-8") else "Trojan Config"
                    val mainPart = raw.substringBefore("#")
                    val pass = mainPart.substringBefore("@")
                    val rest = mainPart.substringAfter("@")
                    val hostPort = rest.substringBefore("?")
                    val host = hostPort.substringBefore(":")
                    val port = hostPort.substringAfter(":").toIntOrNull() ?: 443

                    val queryMap = parseQuery(mainPart.substringAfter("?", ""))
                    val sec = queryMap["security"] ?: "tls"
                    val sni = queryMap["sni"] ?: queryMap["host"] ?: host
                    val type = queryMap["type"] ?: "tcp"

                    return ParsedProxyConfig(
                        rawUri = trimmed,
                        protocol = "Trojan",
                        remark = remark,
                        host = host,
                        port = port,
                        id = pass,
                        transport = type,
                        security = sec,
                        sni = sni
                    )
                }

                trimmed.startsWith("vmess://", ignoreCase = true) -> {
                    val b64 = trimmed.removePrefix("vmess://").trim()
                    val jsonStr = String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
                    val j = JSONObject(jsonStr)

                    return ParsedProxyConfig(
                        rawUri = trimmed,
                        protocol = "VMess",
                        remark = j.optString("ps", "VMess Config"),
                        host = j.optString("add", ""),
                        port = j.optInt("port", 443),
                        id = j.optString("id", ""),
                        transport = j.optString("net", "tcp"),
                        security = j.optString("tls", "none"),
                        sni = j.optString("sni", j.optString("host", "")),
                        path = j.optString("path", "")
                    )
                }

                trimmed.startsWith("ss://", ignoreCase = true) -> {
                    val raw = trimmed.removePrefix("ss://")
                    val remark = if (raw.contains("#")) URLDecoder.decode(raw.substringAfter("#"), "UTF-8") else "Shadowsocks"
                    val mainPart = raw.substringBefore("#")
                    val b64Part = mainPart.substringBefore("@")

                    var host = ""
                    var port = 8388
                    if (mainPart.contains("@")) {
                        val hostPort = mainPart.substringAfter("@")
                        host = hostPort.substringBefore(":")
                        port = hostPort.substringAfter(":").toIntOrNull() ?: 8388
                    }

                    return ParsedProxyConfig(
                        rawUri = trimmed,
                        protocol = "Shadowsocks",
                        remark = remark,
                        host = host,
                        port = port,
                        id = b64Part
                    )
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun parseQuery(q: String): Map<String, String> {
        if (q.isBlank()) return emptyMap()
        return q.split("&").mapNotNull {
            val parts = it.split("=", limit = 2)
            if (parts.size == 2) parts[0] to URLDecoder.decode(parts[1], "UTF-8") else null
        }.toMap()
    }

    /**
     * Probes real-world TCP & TLS Handshake latency for the config.
     */
    suspend fun probeConfig(cfg: ParsedProxyConfig): Pair<Long, Boolean> = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        var socket: Socket? = null
        var tlsSocket: SSLSocket? = null

        try {
            socket = Socket()
            socket.connect(InetSocketAddress(cfg.host.trim(), cfg.port), 4000)

            if (cfg.security.equals("tls", ignoreCase = true) || cfg.port == 443) {
                val tlsHost = cfg.sni.trim().ifBlank { cfg.host.trim() }
                val factory = SSLContext.getDefault().socketFactory

                tlsSocket = factory.createSocket(socket, tlsHost, cfg.port, true) as SSLSocket
                tlsSocket.soTimeout = 4000
                val parameters = tlsSocket.sslParameters
                parameters.endpointIdentificationAlgorithm = "HTTPS"
                runCatching { javax.net.ssl.SNIHostName(tlsHost) }.getOrNull()?.let {
                    parameters.serverNames = listOf(it)
                }
                tlsSocket.sslParameters = parameters
                tlsSocket.startHandshake()
            }

            val elapsed = System.currentTimeMillis() - t0
            Pair(elapsed, true)
        } catch (_: Exception) {
            Pair(-1L, false)
        } finally {
            try { tlsSocket?.close() } catch (_: Exception) {}
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Inspects a subscription URL (Marzban / X-UI / Panel Sub Link).
     */
    suspend fun fetchSubscription(subUrl: String): SubscriptionInfo = withContext(Dispatchers.IO) {
        val cleanUrl = subUrl.trim()
        val validatedUrl = try {
            NetworkTargetPolicy.requirePublicHttps(cleanUrl).toASCIIString()
        } catch (_: Exception) {
            throw ProxySubscriptionException(ProxyFailure(ProxyFailureKind.INVALID_URL))
        }
        val req = Request.Builder()
            .url(validatedUrl)
            .header("User-Agent", "v2rayNG/1.8.12 (Didban Sentinel)")
            .build()

        try {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw ProxySubscriptionException(ProxyFailure(ProxyFailureKind.HTTP_STATUS, resp.code.toString()))
                }
                val userInfoHeader = resp.header("Subscription-Userinfo") ?: resp.header("subscription-userinfo") ?: ""
                var up = 0L
                var down = 0L
                var total = 0L
                var expire = 0L

                if (userInfoHeader.isNotBlank()) {
                    userInfoHeader.split(";").forEach { item ->
                        val parts = item.trim().split("=")
                        if (parts.size == 2) {
                            when (parts[0].trim().lowercase()) {
                                "upload" -> up = parts[1].trim().toLongOrNull() ?: 0L
                                "download" -> down = parts[1].trim().toLongOrNull() ?: 0L
                                "total" -> total = parts[1].trim().toLongOrNull() ?: 0L
                                "expire" -> expire = parts[1].trim().toLongOrNull() ?: 0L
                            }
                        }
                    }
                }

                val bodyStr = BoundedResponseReader.readUtf8(resp.body, BoundedResponseReader.LARGE_BYTES)
                val decoded = try {
                    String(Base64.decode(bodyStr.trim(), Base64.DEFAULT), Charsets.UTF_8)
                } catch (_: Exception) {
                    bodyStr
                }

                val configs = decoded.lines()
                    .mapNotNull { parseConfig(it.trim()) }

                SubscriptionInfo(
                    uploadBytes = up,
                    downloadBytes = down,
                    totalBytes = total,
                    expireTimestamp = expire,
                    configs = configs
                )
            }
        } catch (e: ProxySubscriptionException) {
            throw e
        } catch (e: Exception) {
            // Subscription URLs commonly carry the account token in their
            // path/query; never propagate the URL through an exception.
            throw ProxySubscriptionException(
                ProxyFailure(
                    ProxyFailureKind.NETWORK,
                    SecretRedactor.redact(e.message ?: "network error", listOf(cleanUrl)).take(300)
                )
            )
        }
    }
}
