package org.didban.monitor

import okhttp3.ResponseBody
import okio.Buffer

class ResponseTooLargeException(val limitBytes: Long) : Exception("Response exceeds the ${limitBytes}-byte safety limit")

/** Reads the decoded OkHttp response stream with a hard in-memory ceiling. */
object BoundedResponseReader {
    const val SMALL_BYTES = 512L * 1024L
    const val STANDARD_BYTES = 2L * 1024L * 1024L
    const val LARGE_BYTES = 4L * 1024L * 1024L

    fun readUtf8(body: ResponseBody?, maxBytes: Long = STANDARD_BYTES): String {
        if (body == null) return ""
        require(maxBytes > 0) { "maxBytes must be positive" }
        val declared = body.contentLength()
        if (declared > maxBytes) throw ResponseTooLargeException(maxBytes)

        val source = body.source()
        val buffer = Buffer()
        var total = 0L
        while (true) {
            // Read one byte beyond the boundary so chunked/unknown-length
            // responses cannot silently look like valid truncated JSON.
            val remaining = maxBytes - total
            val request = minOf(8192L, remaining + 1L)
            val read = source.read(buffer, request)
            if (read == -1L) break
            total += read
            if (total > maxBytes) throw ResponseTooLargeException(maxBytes)
        }
        return buffer.readString(Charsets.UTF_8)
    }
}
