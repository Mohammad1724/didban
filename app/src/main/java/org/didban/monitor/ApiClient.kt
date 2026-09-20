package org.didban.monitor

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class ApiException(message: String, val statusCode: Int? = null) : Exception(message)

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

    /**
     * Explicit TOFU capture for the server editor's "fetch fingerprint"
     * flow: performs a TLS handshake against the tokenless /health
     * endpoint WITHOUT pin verification and returns the observed leaf
     * SHA-256.
     *
     * Security rules (all deliberate):
     *  - the request carries NO Authorization header, so no token is ever
     *    sent to an unverified peer;
     *  - the client is one-shot and never cached ([HttpClientPool.captureClient]);
     *  - the body is closed unread — status and content are irrelevant, the
     *    handshake alone publishes the fingerprint;
     *  - cancellation aborts the socket and releases the client, like metrics polling.
     *
     * Throws [ApiException] when the handshake never produces a valid pin.
     */
    suspend fun captureFingerprint(host: String, port: Int): String = suspendCancellableCoroutine { continuation ->
        val captured = runCatching { HttpClientPool.captureClient(host, port) }.getOrNull()
        if (captured == null) {
            if (continuation.isActive) continuation.resumeWithException(ApiException("fingerprint capture failed"))
            return@suspendCancellableCoroutine
        }
        val (client, holder) = captured
        val call = try {
            client.newCall(Request.Builder().url("https://$host:$port/health").build())
        } catch (e: Exception) {
            HttpClientPool.releaseCapture(client)
            if (continuation.isActive) continuation.resumeWithException(ApiException("fingerprint capture failed"))
            return@suspendCancellableCoroutine
        }
        continuation.invokeOnCancellation {
            runCatching { call.cancel() }
            HttpClientPool.releaseCapture(client)
        }
        // The handshake already published the pin (or failed trying): close
        // everything, read nothing, and report what the holder saw.
        fun finish() {
            HttpClientPool.releaseCapture(client)
            if (!continuation.isActive) return
            val observed = holder.get().orEmpty()
            if (CertFingerprint.isValidSha256(observed)) continuation.resume(observed)
            else continuation.resumeWithException(ApiException("fingerprint capture failed"))
        }
        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) = finish()
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                runCatching { response.close() }
                finish()
            }
        })
    }

    /**
     * Deterministic pin-rotation detector. The trust manager records every
     * observed leaf in the pooled holder, so when a request fails and the
     * observed fingerprint differs from the pinned one, the failure IS a
     * pin rejection: the agent was reinstalled (new self-signed
     * certificate) or the entry points at a different machine.
     *
     * Comparing holder-vs-pin (instead of sniffing the cause chain) is
     * deliberate: the socket layer wraps the TLS alert and may drop the
     * original cause. Internal so JVM tests can cover the matrix.
     */
    internal fun pinRejectionOrNull(server: ServerConfig, observed: String?): FingerprintMismatchException? {
        val pinned = CertFingerprint.normalizeFingerprint(server.fingerprint)
        if (!server.useTls || pinned.isEmpty() || observed.isNullOrEmpty() || observed != pinned) return null
        return FingerprintMismatchException(pinned, observed)
    }

    private fun get(server: ServerConfig, path: String): JSONObject {
        val scheme = if (server.useTls) "https" else "http"
        val url = "$scheme://${server.host}:${server.port}$path"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${server.token}")
            .build()
        val pooled = HttpClientPool.standardClient(server)
        val responseLimit = if (path.startsWith("/api/events") || path.startsWith("/api/history"))
            BoundedResponseReader.LARGE_BYTES else BoundedResponseReader.STANDARD_BYTES
        try {
            pooled.client.newCall(request).execute().use { resp ->
                val body = BoundedResponseReader.readUtf8(resp.body, responseLimit)
                // Agent/proxy bodies are untrusted and may echo request data;
                // never propagate the raw body into UI errors or crash traces.
                if (!resp.isSuccessful) throw ApiException("HTTP ${resp.code}", resp.code)
                return JSONObject(body)
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            // A failed request that observed a different certificate than the
            // pinned one IS a pin rejection — report it typed instead of a raw TLS alert.
            throw pinRejectionOrNull(server, pooled.fingerprint?.get())
                ?: ApiException(SecretRedactor.redact(e.message ?: "network error", listOf(server.token, server.adminToken)).take(300))
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
            .header("Authorization", "Bearer ${server.adminToken}")
            // OkHttp retries reuse this immutable Request and therefore the
            // same key; a separate user action builds a fresh key.
            .header("X-Idempotency-Key", java.util.UUID.randomUUID().toString())
            .post(reqBody)
            .build()
        val pooled = HttpClientPool.standardClient(server)
        try {
            pooled.client.newCall(request).execute().use { resp ->
                val body = BoundedResponseReader.readUtf8(resp.body, BoundedResponseReader.STANDARD_BYTES)
                if (!resp.isSuccessful) {
                    var errMsg = "HTTP ${resp.code}"
                    try {
                        val j = JSONObject(body)
                        if (j.has("error")) errMsg = j.getString("error")
                    } catch (_: Exception) {}
                    throw ApiException(SecretRedactor.redact(errMsg, listOf(server.token, server.adminToken)).take(300))
                }
                return JSONObject(body)
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            // A failed request that observed a different certificate than the
            // pinned one IS a pin rejection — report it typed instead of a raw TLS alert.
            throw pinRejectionOrNull(server, pooled.fingerprint?.get())
                ?: ApiException(SecretRedactor.redact(e.message ?: "network error", listOf(server.token, server.adminToken)).take(300))
        } finally {
            lastSeenFingerprint = pooled.fingerprint?.get()?.takeIf { it.isNotEmpty() }
        }
    }

    /** Metrics polling must release its socket when a manual batch times out. */
    private suspend fun metricsJson(server: ServerConfig): JSONObject = suspendCancellableCoroutine { continuation ->
        val scheme = if (server.useTls) "https" else "http"
        val pooled = HttpClientPool.standardClient(server)
        val request = Request.Builder()
            .url("$scheme://${server.host}:${server.port}/api/metrics")
            .header("Authorization", "Bearer ${server.token}")
            .build()
        val call = pooled.client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        fun fail(error: Exception) {
            lastSeenFingerprint = pooled.fingerprint?.get()?.takeIf { it.isNotEmpty() }
            if (continuation.isActive) continuation.resumeWithException(
                // Holder-vs-pin decides: a rotation surfaces typed, every
                // other failure keeps the redacted ApiException.
                pinRejectionOrNull(server, pooled.fingerprint?.get())
                    ?: ApiException(SecretRedactor.redact(error.message ?: "network error", listOf(server.token, server.adminToken)).take(300))
            )
        }
        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) = fail(e)
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                try {
                    response.use {
                        if (!it.isSuccessful) throw ApiException("HTTP ${it.code}")
                        val body = BoundedResponseReader.readUtf8(it.body, BoundedResponseReader.STANDARD_BYTES)
                        val json = JSONObject(body)
                        lastSeenFingerprint = pooled.fingerprint?.get()?.takeIf { it.isNotEmpty() }
                        if (continuation.isActive) continuation.resume(json)
                    }
                } catch (e: Exception) {
                    fail(e)
                } finally {
                    lastSeenFingerprint = pooled.fingerprint?.get()?.takeIf { it.isNotEmpty() }
                }
            }
        })
    }

    suspend fun metrics(server: ServerConfig): Metrics =
        withContext(Dispatchers.IO) { Metrics.fromJson(metricsJson(server)) }

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
