package org.didban.monitor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class ApiException(message: String) : Exception(message)

/**
 * HTTP client for one or more agents.
 *
 * TLS model (trust-on-first-use, like SSH):
 *  - If the server has a pinned fingerprint, the connection only succeeds when
 *    the served certificate matches it (SHA-256 of the DER certificate).
 *  - If no fingerprint is set yet, any certificate is accepted (lenient) and
 *    [lastSeenFingerprint] can be saved back to the server config ("pin").
 */
class ApiClient {

    var lastSeenFingerprint: String? = null
        private set

    private fun clientFor(server: ServerConfig): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
        if (server.useTls) {
            val pinned = server.fingerprint.trim().lowercase().replace(":", "")
            val tm = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}

                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                    if (chain.isEmpty()) throw CertificateException("empty certificate chain")
                    val sha = MessageDigest.getInstance("SHA-256").digest(chain[0].encoded)
                    val hex = sha.joinToString("") { String.format("%02x", it) }
                    lastSeenFingerprint = hex
                    if (pinned.isNotEmpty() && pinned != hex) {
                        throw CertificateException("certificate fingerprint mismatch")
                    }
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
            val ssl = SSLContext.getInstance("TLS")
            ssl.init(null, arrayOf<TrustManager>(tm), null)
            builder.sslSocketFactory(ssl.socketFactory, tm)
            builder.hostnameVerifier { _, _ -> true }
        }
        return builder.build()
    }

    private fun get(server: ServerConfig, path: String): JSONObject {
        val scheme = if (server.useTls) "https" else "http"
        val url = "$scheme://${server.host}:${server.port}$path"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${server.token}")
            .build()
        try {
            clientFor(server).newCall(request).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                if (!resp.isSuccessful) throw ApiException("HTTP ${resp.code}")
                return JSONObject(body)
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            throw ApiException(e.message ?: "network error")
        }
    }

    suspend fun metrics(server: ServerConfig): Metrics =
        withContext(Dispatchers.IO) { Metrics.fromJson(get(server, "/api/metrics")) }

    suspend fun processes(server: ServerConfig): List<ProcInfo> =
        withContext(Dispatchers.IO) { JsonParse.processes(get(server, "/api/processes")) }

    suspend fun events(server: ServerConfig, limit: Int = 50): List<SpikeEvent> =
        withContext(Dispatchers.IO) { JsonParse.events(get(server, "/api/events?limit=$limit")) }

    suspend fun history(server: ServerConfig, hours: Int = 24): List<HistPoint> =
        withContext(Dispatchers.IO) { JsonParse.history(get(server, "/api/history?hours=$hours")) }

}
