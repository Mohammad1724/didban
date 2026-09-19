package org.didban.monitor

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal enum class BenchmarkStage { LATENCY, DOWNLOAD, UPLOAD }
internal data class BenchmarkProgress(val stage: BenchmarkStage, val fraction: Float, val mbps: Float = 0f)
internal class BenchmarkFailure(val stage: BenchmarkStage, cause: Exception) : Exception(cause.message, cause)

internal fun benchmarkMbps(bytes: Long, elapsedNanos: Long): Float =
    if (elapsedNanos <= 0) 0f else (bytes * 8.0 * 1_000 / elapsedNanos).toFloat()

/** All socket/stream work runs off the UI thread. Cancel closes the active HTTP call. */
internal class BandwidthBenchmark(
    private val bytes: Long = 100L * 1024 * 1024,
    private val pingCount: Int = 5,
    private val pingDelayMs: Long = 150,
    private val api: ApiClient = ApiClient()
) {
    suspend fun run(server: ServerConfig, progress: (BenchmarkProgress) -> Unit): BenchmarkResult = withContext(Dispatchers.IO) {
        require(bytes in 65_536L..200L * 1024 * 1024)
        require(pingCount > 0)
        var stage = BenchmarkStage.LATENCY
        try {
            val pings = mutableListOf<Long>()
            repeat(pingCount) { index ->
                currentCoroutineContext().ensureActive()
                val start = System.nanoTime()
                try {
                    Socket().use { it.connect(InetSocketAddress(server.host.trim(), server.port), 2_000) }
                    pings += (System.nanoTime() - start) / 1_000_000
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: IOException) { /* Failed TCP handshake, not a fabricated latency sample. */ }
                progress(BenchmarkProgress(stage, 0.2f * (index + 1) / pingCount))
                delay(pingDelayMs)
            }
            if (pings.isEmpty()) throw ConnectException("No successful TCP handshake")
            val ping = pings.average().toLong()
            val jitter = if (pings.size > 1) pings.zipWithNext { a, b -> kotlin.math.abs(a - b) }.average().toLong() else null
            val loss = ((pingCount - pings.size) * 100) / pingCount

            stage = BenchmarkStage.DOWNLOAD
            progress(BenchmarkProgress(stage, 0.2f))
            val downloadStart = System.nanoTime()
            val download = try {
                api.openStreamingCall(server, "/api/bandwidth/download?bytes=$bytes").consume { response ->
                    checkStatus(response)
                    val source = response.body?.source() ?: throw ApiException("Empty bandwidth response")
                    var received = 0L
                    var lastUi = downloadStart
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = source.read(buffer)
                        if (count == -1) break
                        received += count
                        if (received > bytes) throw ApiException("Unexpected bandwidth download size")
                        val now = System.nanoTime()
                        if (now - lastUi >= 250_000_000) {
                            progress(BenchmarkProgress(BenchmarkStage.DOWNLOAD, 0.2f + 0.4f * received / bytes,
                                benchmarkMbps(received, now - downloadStart)))
                            lastUi = now
                        }
                    }
                    if (received != bytes) throw ApiException("Incomplete bandwidth download")
                    benchmarkMbps(received, System.nanoTime() - downloadStart)
                }
            } finally { api.releaseStreaming() }

            stage = BenchmarkStage.UPLOAD
            progress(BenchmarkProgress(stage, 0.6f))
            val uploadStart = System.nanoTime()
            var lastUi = uploadStart
            val body = BenchmarkUploadBody(bytes) { written ->
                val now = System.nanoTime()
                if (now - lastUi >= 250_000_000) {
                    progress(BenchmarkProgress(BenchmarkStage.UPLOAD, 0.6f + 0.4f * written / bytes,
                        benchmarkMbps(written, now - uploadStart)))
                    lastUi = now
                }
            }
            val upload = try {
                api.openStreamingCall(server, "/api/bandwidth/upload", body).consume { response ->
                    checkStatus(response)
                    val ack = JSONObject(BoundedResponseReader.readUtf8(response.body, 16 * 1024L))
                    if (ack.optLong("received_bytes", -1) != bytes) throw ApiException("Incomplete bandwidth upload")
                    benchmarkMbps(bytes, System.nanoTime() - uploadStart)
                }
            } finally { api.releaseStreaming() }
            progress(BenchmarkProgress(stage, 1f))
            BenchmarkResult(download, upload, ping, jitter, loss)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { throw BenchmarkFailure(stage, failure) }
    }

    private fun checkStatus(response: Response) {
        if (!response.isSuccessful) throw ApiException("HTTP ${response.code}", response.code)
    }
}

/** Consume on OkHttp's worker, never on the calling UI coroutine; bound to cancellation. */
private suspend fun <T> Call.consume(read: (Response) -> T): T = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }
        override fun onResponse(call: Call, response: Response) {
            try {
                response.use {
                    if (!continuation.isActive) return
                    val value = read(it)
                    if (continuation.isActive) continuation.resume(value)
                }
            } catch (failure: Exception) {
                if (continuation.isActive) continuation.resumeWithException(failure)
            }
        }
    })
}

private class BenchmarkUploadBody(private val total: Long, private val progress: (Long) -> Unit) : RequestBody() {
    private val block = ByteArray(64 * 1024).also { SecureRandom().nextBytes(it) }
    override fun contentType() = "application/octet-stream".toMediaType()
    override fun contentLength() = total
    override fun writeTo(sink: BufferedSink) {
        var written = 0L
        while (written < total) {
            val count = minOf(block.size.toLong(), total - written).toInt()
            sink.write(block, 0, count)
            written += count
            progress(written)
        }
    }
}
