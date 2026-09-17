package org.didban.monitor

import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient

/**
 * Process-wide OkHttp client cache (H7).
 *
 * Before H7 every ApiClient call built a fresh OkHttpClient (new
 * ConnectionPool, new dispatcher, new TLS stack per request), so every
 * request paid a full TCP + TLS handshake and the screens' poll loops
 * churned threads continuously. Now:
 *
 *  - one shared [ConnectionPool] (8 idle connections, 5 min) — keep-alive
 *    and TLS session resumption work across every screen and loop;
 *  - one long-lived OkHttpClient per [ClientKey] (host, port, TLS mode,
 *    normalized fingerprint), cached in a bounded LRU; evicted clients
 *    have their dispatcher shut down;
 *  - the TOFU pinning trust manager publishes the observed fingerprint to a
 *    per-client [AtomicReference] holder (previously a per-ApiClient field
 *    mutated from inside the TLS handshake).
 *
 * Timeouts are unchanged from the per-request era: 8 s connect / 15 s read
 * for standard calls; the long streaming client (bandwidth test) gets
 * 120 s read/write, shares the pool and trust manager, and is short-lived —
 * release it via [releaseStreaming].
 *
 * JVM-testable: [ClientKey], the LRU cache, fingerprint normalization and
 * the pinning behavior itself are covered by [HttpClientPoolTest].
 */
object HttpClientPool {

    /** Cached clients beyond this number are evicted LRU-first. */
    const val MAX_CACHED_CLIENTS = 16

    private const val CONNECT_TIMEOUT_SEC = 8L
    private const val READ_TIMEOUT_SEC = 15L
    private const val STREAMING_TIMEOUT_SEC = 120L
    private const val IDLE_CONNECTIONS = 8
    private const val IDLE_TTL_MIN = 5L

    private val sharedPool = ConnectionPool(IDLE_CONNECTIONS, IDLE_TTL_MIN, TimeUnit.MINUTES)

    private val cache = BoundedLru<ClientKey, PooledClient>(MAX_CACHED_CLIENTS) { _, pooled ->
        // The client is no longer needed: stop its dispatcher threads.
        // The ConnectionPool is shared on purpose — never evicted here.
        release(pooled.client)
    }

    /** A cached standard client plus the fingerprint holder its trust manager writes. */
    data class PooledClient(
        val client: OkHttpClient,
        /** Last fingerprint observed on this server (null for plain HTTP). */
        val fingerprint: AtomicReference<String>?
    )

    /** The pool key for a server (fingerprint in normalized form). */
    fun keyFor(server: ServerConfig): ClientKey =
        ClientKey(server.host, server.port, server.useTls, CertFingerprint.normalizeFingerprint(server.fingerprint))

    /**
     * Cached standard client for a server. Repeated calls for the same key
     * return the same instance; the fingerprint holder (TLS only) is written
     * by the trust manager on every handshake.
     */
    fun standardClient(server: ServerConfig): PooledClient {
        // Android blocks cleartext process-wide (see the manifest). For TLS,
        // never create a credential-bearing client without a verified pin.
        require(!server.useTls || CertFingerprint.isValidSha256(server.fingerprint)) {
            "A verified SHA-256 certificate fingerprint is required"
        }
        val key = keyFor(server)
        cache.get(key)?.let { return it }
        val holder: AtomicReference<String>? =
            if (server.useTls) AtomicReference<String>(CertFingerprint.normalizeFingerprint(server.fingerprint))
            else null
        val built = try {
            baseBuilder(server, holder)
                .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
                .build()
        } catch (e: Exception) {
            throw IllegalStateException("Failed to build HTTP client for ${server.host}", e)
        }
        val pooled = PooledClient(built, holder)
        cache.put(key, pooled)
        // Two threads racing on the same fresh key may each build a client;
        // the losing one is released immediately.
        if (cache.get(key) !== pooled) release(pooled.client)
        return pooled
    }

    /**
     * Short-lived client for long streaming (bandwidth test): shares the
     * connection pool and the pinning trust manager, but uses 120 s
     * read/write timeouts. Call [releaseStreaming] when the call completes.
     */
    fun streamingClient(server: ServerConfig): OkHttpClient {
        val pooled = standardClient(server) // same key + fingerprint holder
        val built = try {
            baseBuilder(server, pooled.fingerprint)
                .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
                .readTimeout(STREAMING_TIMEOUT_SEC, TimeUnit.SECONDS)
                .writeTimeout(STREAMING_TIMEOUT_SEC, TimeUnit.SECONDS)
                .build()
        } catch (e: Exception) {
            throw IllegalStateException("Failed to build streaming client for ${server.host}", e)
        }
        return built
    }

    /** Releases a short-lived streaming client's dispatcher (no-op for the shared pool). */
    fun releaseStreaming(client: OkHttpClient) {
        release(client)
    }

    /**
     * Drop the cached client for one key. Call whenever the key inputs can
     * change (re-pin, port, TLS toggle) or when a server is deleted —
     * otherwise the stale client would keep enforcing the old pin.
     */
    fun evict(key: ClientKey) {
        cache.remove(key)
    }

    /** Convenience for [evict] — drop the client for this server's current key. */
    fun evictForServer(server: ServerConfig) {
        evict(keyFor(server))
    }

    private fun release(client: OkHttpClient) {
        try {
            client.dispatcher.executorService.shutdown()
        } catch (_: Exception) {
        }
        // client.connectionPool is the shared pool — must never be evicted.
    }

    // ── TLS / pinning (moved from ApiClient; behavior preserved) ──────────

    private fun baseBuilder(
        server: ServerConfig,
        holder: AtomicReference<String>?
    ): OkHttpClient.Builder {
        val builder = OkHttpClient.Builder().connectionPool(sharedPool)
        if (server.useTls) {
            val pinned = CertFingerprint.normalizeFingerprint(server.fingerprint)
            val tm = trustManager(pinned, holder)
            val ssl = SSLContext.getInstance("TLS")
            ssl.init(null, arrayOf<javax.net.ssl.TrustManager>(tm), null)
            builder.sslSocketFactory(ssl.socketFactory, tm)
            // Hostname verification stays off by design: agents run with
            // self-signed certificates whose subject may not match the
            // address dialed. Security comes from fingerprint pinning.
            builder.hostnameVerifier { _, _ -> true }
        }
        return builder
    }

    /**
     * The pinning trust manager for one connection posture. [pinned] must
     * already be normalized ([CertFingerprint.normalizeFingerprint]); an
     * empty value means "no pin yet" (TOFU — accept and record).
     *
     * Internal (not private) so the JVM tests can exercise the exact
     * security logic OkHttp invokes on every TLS handshake.
     */
    internal fun trustManager(
        pinned: String,
        holder: AtomicReference<String>?
    ): X509TrustManager {
        return object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                // Server-side client certificates are not used.
            }

            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                if (chain.isEmpty()) throw CertificateException("empty certificate chain")
                val fp = CertFingerprint.normalized(chain[0])
                // TOFU: publish the observed fingerprint so the UI can offer
                // one-tap pinning (previously: ApiClient.lastSeenFingerprint).
                holder?.set(fp)
                if (pinned.isNotEmpty() && pinned != fp) {
                    throw CertificateException(
                        "certificate fingerprint mismatch (expected ${pinned.take(12)}…, got ${fp.take(12)}…)"
                    )
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
    }
}
