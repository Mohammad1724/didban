package org.didban.monitor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class ApiException(message: String) : Exception(message)

/**
 * HTTP client for one or more agents.
 *
 * TLS model (trust-on-first-use, like SSH):
 *  - If the server has a pinned fingerprint, the connection only succeeds when
 *    the served certificate matches it (SHA-256 of the DER certificate).
 *  - If no fingerprint is set yet, any certificate is accepted (lenient) and
 *    [lastSeenFingerprint] can be saved back to the server config ("pin").
 *
 * H7: this class no longer builds OkHttpClients. [HttpClientPool] owns one
 * long-lived client per server (host, port, TLS, fingerprint) on a shared
 * connection pool — connection reuse, one TLS handshake per server per
 * connection, no per-request thread churn. The class only shapes requests
 * and responses. [lastSeenFingerprint] reflects the certificate the pooled
 * client last observed on the server it last talked to (the holder is
 * per-server, so it is correct even though the client is shared).
 */
class ApiClient {

    /** Certificate fingerprint observed on the last request (lowercase hex). */
    var lastSeenFingerprint: String? = null
        private set

    // Short-lived streaming clients opened by [openStreamingCall]; the
    // bandwidth screen calls [releaseStreaming] when a call completes.
    private val openStreaming = java.util.Collections.synchronizedList(mutableListOf<OkHttpClient>())

    /**
     * Opens a streaming call against the agent (used by the bandwidth test).
     * Same auth + certificate pinning as [get]; longer read/write timeouts
     * because the body is large by design. Caller must close the response
     * and call [releaseStreaming] afterwards.
     */
    fun openStreamingCall(server: ServerConfig, path: String, body: RequestBody? = null): okhttp3.Call {
        val scheme = if (server.useTls) "https" else "http"
        val url = "$scheme://${server.host}:${server.port}$path"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${server.token}")
            .apply { if (body != null) method("POST", body) }
            .build()
        val client = HttpClientPool.streamingClient(server)
        openStreaming.add(client)
        return client.newCall(request)
    }

    /** Releases the dispatchers of the streaming clients opened by this instance. */
    fun releaseStreaming() {
        val clients = synchronized(openStreaming) {
            openStreaming.toList().also { openStreaming.clear() }
        }
        clients.forEach { HttpClientPool.releaseStreaming(it) }
    }

    private fun get(server: ServerConfig, path: String): JSONObject {
        val scheme = if (server.useTls) "https" else "http"
        val url = "$scheme://${server.host}:${server.port}$path"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${server.token}")
            .build()
        val pooled = HttpClientPool.standardClient(server)
        try {
            pooled.client.newCall(request).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                if (!resp.isSuccessful) throw ApiException("HTTP ${resp.code}: $body")
                return JSONObject(body)
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            throw ApiException(e.message ?: "network error")
        } finally {
            lastSeenFingerprint = pooled.fingerprint?.get()?.takeIf { it.isNotEmpty() }
        }
    }

    private fun post(server: ServerConfig, path: String, jsonBody: JSONObject): JSONObject {
        val scheme = if (server.useTls) "https" else "http"
        val url = "$scheme://${server.host}:${server.port}$path"
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val reqBody = jsonBody.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${server.token}")
            .post(reqBody)
            .build()
        val pooled = HttpClientPool.standardClient(server)
        try {
            pooled.client.newCall(request).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    var errMsg = "HTTP ${resp.code}"
                    try {
                        val j = JSONObject(body)
                        if (j.has("error")) errMsg = j.getString("error")
                    } catch (_: Exception) {}
                    throw ApiException(errMsg)
                }
                return JSONObject(body)
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            throw ApiException(e.message ?: "network error")
        } finally {
            lastSeenFingerprint = pooled.fingerprint?.get()?.takeIf { it.isNotEmpty() }
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

    suspend fun sockets(server: ServerConfig): SocketsData =
        withContext(Dispatchers.IO) { JsonParse.sockets(get(server, "/api/network/sockets")) }

    suspend fun dockerContainers(server: ServerConfig): DockerSummaryData =
        withContext(Dispatchers.IO) { JsonParse.docker(get(server, "/api/docker/containers")) }

    suspend fun dockerRestart(server: ServerConfig, id: String): JSONObject =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply { put("id", id) }
            post(server, "/api/docker/restart", body)
        }

    suspend fun dockerStop(server: ServerConfig, id: String): JSONObject =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply { put("id", id) }
            post(server, "/api/docker/stop", body)
        }

    suspend fun killProcess(server: ServerConfig, pid: Int, signal: String = "SIGTERM"): ProcessKillResponse =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("pid", pid)
                put("signal", signal)
            }
            JsonParse.killResult(post(server, "/api/processes/kill", body))
        }

    suspend fun testTelegram(server: ServerConfig): JSONObject =
        withContext(Dispatchers.IO) {
            post(server, "/api/alerts/test", JSONObject())
        }

    // ── Tunnel Management APIs (Smite / Marzban style auto-sync) ────────────

    suspend fun tunnelApply(server: ServerConfig, req: JSONObject): JSONObject =
        withContext(Dispatchers.IO) {
            post(server, "/api/tunnel/apply", req)
        }

    suspend fun tunnelStart(server: ServerConfig, id: String): JSONObject =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply { put("id", id) }
            post(server, "/api/tunnel/start", body)
        }

    suspend fun tunnelStop(server: ServerConfig, id: String): JSONObject =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply { put("id", id) }
            post(server, "/api/tunnel/stop", body)
        }

    suspend fun tunnelRestart(server: ServerConfig, id: String): JSONObject =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply { put("id", id) }
            post(server, "/api/tunnel/restart", body)
        }

    suspend fun tunnelDelete(server: ServerConfig, id: String): JSONObject =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply { put("id", id) }
            post(server, "/api/tunnel/delete", body)
        }

    suspend fun tunnelStatus(server: ServerConfig, id: String): JSONObject =
        withContext(Dispatchers.IO) {
            get(server, "/api/tunnel/status?id=$id")
        }

    suspend fun tunnelList(server: ServerConfig): JSONObject =
        withContext(Dispatchers.IO) {
            get(server, "/api/tunnel/list")
        }

    // Phase 4 · 4-A: the agent-side tunnel watchdog view (per-tunnel
    // state + port health + restart counters).
    suspend fun tunnelWatchdog(server: ServerConfig): JSONObject =
        withContext(Dispatchers.IO) {
            get(server, "/api/tunnel/watchdog")
        }

    // ── Phase 4 · 4-B: multi-point probing ──────────────────────────────────

    /** Probe snapshot from this agent (one probe point). */
    suspend fun probeStatus(server: ServerConfig): JSONObject =
        withContext(Dispatchers.IO) {
            get(server, "/api/probe")
        }

    /** Replace the agent's registered probe target set (idempotent sync). */
    suspend fun probeTargetsSync(server: ServerConfig, payload: JSONObject): JSONObject =
        withContext(Dispatchers.IO) {
            post(server, "/api/probe/targets", payload)
        }

    /** Immediate probe: one target by name, or all when name is blank. */
    suspend fun probeNow(server: ServerConfig, target: String = ""): JSONObject =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply { if (target.isNotEmpty()) put("target", target) }
            post(server, "/api/probe/now", body)
        }
}
