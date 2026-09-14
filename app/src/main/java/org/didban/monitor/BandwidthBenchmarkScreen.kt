@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.didban.monitor

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import kotlin.math.min

// 100 MiB per direction; the agent clamps any larger request server-side.
private const val BENCH_BYTES = 104_857_600L

data class BenchmarkResult(
    val downloadMbps: Float,
    val uploadMbps: Float,
    val pingMs: Long,
    val jitterMs: Long,
    val packetLossPct: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@Composable
fun BandwidthBenchmarkScreen(t: Str) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { ApiClient() }

    val servers = remember { Prefs.loadServers(ctx) }
    var targetServerIndex by remember { mutableStateOf(if (servers.size > 1) 1 else 0) }

    var isRunning by remember { mutableStateOf(false) }
    var testProgress by remember { mutableStateOf(0f) }
    var currentSpeedMbps by remember { mutableStateOf(0f) }
    var benchmarkResult by remember { mutableStateOf<BenchmarkResult?>(null) }

    fun runBenchmark() {
        if (servers.isEmpty()) {
            Toast.makeText(ctx, "حداقل یک سرور برای بنچمارک ثبت کنید", Toast.LENGTH_SHORT).show()
            return
        }

        val targetServer = servers.getOrNull(targetServerIndex) ?: return
        isRunning = true
        testProgress = 0f
        currentSpeedMbps = 0f
        benchmarkResult = null
        val startWall = System.currentTimeMillis()

        scope.launch {
            // 1. Measure Ping & Jitter
            val pings = mutableListOf<Long>()
            var lostPackets = 0
            val targetHost = targetServer.host.trim()
            val targetPort = if (targetServer.port > 0) targetServer.port else 80

            for (i in 1..5) {
                testProgress = (i * 0.09f).coerceAtMost(0.45f)
                val t0 = System.currentTimeMillis()
                try {
                    withContext(Dispatchers.IO) {
                        Socket().use { s ->
                            s.connect(InetSocketAddress(targetHost, targetPort), 2000)
                        }
                    }
                    val lat = System.currentTimeMillis() - t0
                    pings.add(lat)
                } catch (_: Exception) {
                    lostPackets++
                }
                delay(150)
            }

            val avgPing = if (pings.isNotEmpty()) pings.average().toLong() else 999L
            val jitter = if (pings.size > 1) {
                pings.zipWithNext { a, b -> Math.abs(a - b) }.average().toLong()
            } else 2L
            val lossPct = (lostPackets * 100) / 5

            // 2. Measure REAL download throughput from the agent's streaming
            //    endpoint (100 MiB; the agent caps it server-side).
            val downloadMbps: Float
            try {
                val call = api.openStreamingCall(targetServer, "/api/bandwidth/download?bytes=$BENCH_BYTES")
                val t0 = System.nanoTime()
                var lastUi = System.currentTimeMillis()
                var bytes = 0L
                call.execute().use { resp ->
                    if (!resp.isSuccessful) {
                        if (resp.code == 404 || resp.code == 405) {
                            throw ApiException("agent-bandwidth-unsupported")
                        }
                        throw ApiException("HTTP ${resp.code}")
                    }
                    val source = resp.body?.source() ?: throw ApiException("empty body")
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = source.read(buf)
                        if (n == -1) break
                        bytes += n
                        testProgress = (0.45f + 0.35f * bytes / BENCH_BYTES).coerceAtMost(0.8f)
                        val now = System.currentTimeMillis()
                        if (now - lastUi > 250) {
                            lastUi = now
                            currentSpeedMbps = (bytes * 8.0 / (now - startWall) / 1e6).toFloat()
                        }
                    }
                }
                val secs = (System.nanoTime() - t0) / 1e9
                downloadMbps = (bytes * 8.0 / secs / 1e6).toFloat()
            } catch (e: Exception) {
                isRunning = false
                val msg = if (e.message == "agent-bandwidth-unsupported") {
                    "نسخه ایجنت سرور از تست پهنای باند پشتیبانی نمی‌کند؛ ایجنت را به‌روزرسانی کنید."
                } else {
                    "تست دانلود ناموفق بود: ${e.message ?: "خطای شبکه"}"
                }
                Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                return@launch
            } finally {
                // H7: the streaming client is short-lived — release its
                // dispatcher once the call has completed (or failed).
                api.releaseStreaming()
            }

            // 3. Measure REAL upload throughput to the agent's sink endpoint.
            val uploadMbps: Float
            try {
                val t0 = System.nanoTime()
                var lastUi = System.currentTimeMillis()
                val body = BenchmarkUploadBody(BENCH_BYTES) { written ->
                    val now = System.currentTimeMillis()
                    if (now - lastUi > 250) {
                        lastUi = now
                        currentSpeedMbps = (written * 8.0 / (now - startWall) / 1e6).toFloat()
                        testProgress = (0.8f + 0.2f * written / BENCH_BYTES).coerceAtMost(1f)
                    }
                }
                val call = api.openStreamingCall(targetServer, "/api/bandwidth/upload", body)
                call.execute().use { resp ->
                    if (!resp.isSuccessful) throw ApiException("HTTP ${resp.code}")
                }
                val secs = (System.nanoTime() - t0) / 1e9
                uploadMbps = (BENCH_BYTES * 8.0 / secs / 1e6).toFloat()
            } catch (e: Exception) {
                isRunning = false
                val msg = if (e.message == "agent-bandwidth-unsupported") {
                    "نسخه ایجنت سرور از تست پهنای باند پشتیبانی نمی‌کند؛ ایجنت را به‌روزرسانی کنید."
                } else {
                    "تست آپلود ناموفق بود: ${e.message ?: "خطای شبکه"}"
                }
                Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                return@launch
            } finally {
                api.releaseStreaming()
            }

            testProgress = 1f
            currentSpeedMbps = downloadMbps
            benchmarkResult = BenchmarkResult(
                downloadMbps = downloadMbps,
                uploadMbps = uploadMbps,
                pingMs = avgPing,
                jitterMs = jitter,
                packetLossPct = lossPct
            )
            isRunning = false
            Toast.makeText(ctx, "تست پهنای باند با موفقیت تکمیل شد! 🚀", Toast.LENGTH_SHORT).show()
        }
    }



    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        // Hero Bento
        item {
            ModernCard(padding = 16.dp, cornerRadius = 22.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(
                        icon = Icons.Rounded.Speed,
                        tint = Ds.accent,
                        background = Ds.accentDim,
                        size = 40.dp,
                        iconSize = 22.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            t.bandwidthBenchmark,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ds.textPrimary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "سنجش دقیق پهنای باند واقعی، لیتنسی، جیتر و پکت‌لاس تانل ایران-خارج",
                            fontSize = 11.sp,
                            color = Ds.textSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // Source & Destination Node Picker
        item {
            ModernCard(padding = 16.dp, cornerRadius = 20.dp) {
                Text("انتخاب سرور مقصد برای بنچمارک:", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Ds.textSecondary)
                Spacer(Modifier.height(8.dp))

                if (servers.isEmpty()) {
                    Text("هیچ سروری ثبت نشده است.", fontSize = 12.sp, color = Ds.warn)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(servers.indices.toList()) { idx ->
                            val s = servers[idx]
                            val isSelected = targetServerIndex == idx
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) Ds.accent.copy(alpha = 0.18f) else Ds.surfaceLow)
                                    .border(
                                        BorderStroke(1.dp, if (isSelected) Ds.accent else Ds.hairline),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { targetServerIndex = idx }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    s.name,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Ds.accent else Ds.textPrimary
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                PrimaryButton(
                    text = if (isRunning) "⚡ در حال سنجش پهنای باند..." else "🚀 شروع تست بنچمارک شبکه",
                    onClick = { runBenchmark() },
                    enabled = !isRunning && servers.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Live Gauge / Progress Pod
        if (isRunning) {
            item {
                ModernCard(padding = 16.dp, cornerRadius = 20.dp) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "${currentSpeedMbps.toInt()} Mbps",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Ds.accent
                        )
                        Text("Real-Time Throughput Stream", fontSize = 11.sp, color = Ds.textSecondary)

                        LinearProgressIndicator(
                            progress = testProgress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = Ds.accent,
                            trackColor = Ds.surfaceLow
                        )
                    }
                }
            }
        }

        // Benchmark Results Pod
        benchmarkResult?.let { res ->
            item {
                ModernCard(padding = 16.dp, cornerRadius = 22.dp) {
                    Text(
                        "📊 نتایج نهایی بنچمارک کیفیت شبکه:",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ds.textPrimary
                    )

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BentoMicroPod(
                            title = "دانلود تانل",
                            value = "${res.downloadMbps.toInt()}",
                            unit = "Mbps",
                            color = Ds.ok,
                            modifier = Modifier.weight(1f)
                        )
                        BentoMicroPod(
                            title = "آپلود تانل",
                            value = "${res.uploadMbps.toInt()}",
                            unit = "Mbps",
                            color = Ds.accent,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BentoMicroPod(
                            title = "پینگ RTT",
                            value = "${res.pingMs}",
                            unit = "ms",
                            color = if (res.pingMs < 80) Ds.ok else Ds.warn,
                            modifier = Modifier.weight(1f)
                        )
                        BentoMicroPod(
                            title = "جیتر (Jitter)",
                            value = "${res.jitterMs}",
                            unit = "ms",
                            color = Ds.violet,
                            modifier = Modifier.weight(1f)
                        )
                        BentoMicroPod(
                            title = "پکت‌لاس",
                            value = "${res.packetLossPct}",
                            unit = "%",
                            color = if (res.packetLossPct == 0) Ds.ok else Ds.danger,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(90.dp)) }
    }
}

/** Streams [total] random bytes (repeated 64 KiB block) and reports progress. */
private class BenchmarkUploadBody(
    private val total: Long,
    private val onProgress: (Long) -> Unit
) : RequestBody() {
    private val block = ByteArray(64 * 1024).also { SecureRandom().nextBytes(it) }
    override fun contentType() = "application/octet-stream".toMediaType()
    override fun contentLength() = total
    override fun writeTo(sink: BufferedSink) {
        var written = 0L
        while (written < total) {
            val n = min(block.size.toLong(), total - written)
            sink.write(block, 0, n.toInt())
            written += n
            onProgress(written)
        }
    }
}
