package org.didban.monitor

import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * Memory ceiling for command output on this device (M10).
 *
 * A remote command (`cat /dev/urandom`, an unbounded `journalctl`, …) must
 * not be able to exhaust the phone's RAM: every exec stream is read through
 * a [CappedOutputStream] with this ceiling. 1 MB of text is far beyond any
 * real admin command output (status listings, `top -b -n 1`, log tails)
 * while 1 MB per stream is a bounded, device-safe worst case.
 */
const val MAX_SSH_OUTPUT_BYTES: Int = 1024 * 1024

/**
 * An [OutputStream] that stops retaining data after [maxBytes].
 *
 * Every byte is counted in [totalBytes], but bytes beyond the cap are
 * dropped, so a writer can never make the buffer grow past [maxBytes] —
 * an unbounded remote stream cannot OOM the device (M10). Writes after the
 * cap still succeed (they are counted, then discarded) so upstream writers
 * (JSch channel readers) are never blocked or broken.
 */
class CappedOutputStream(private val maxBytes: Int) : OutputStream() {
    private val buf = ByteArrayOutputStream()

    init {
        require(maxBytes > 0) { "maxBytes must be positive" }
    }

    /** Total bytes written, including bytes dropped beyond the cap. */
    var totalBytes: Long = 0
        private set

    /** True once more than [maxBytes] bytes were written. */
    val isTruncated: Boolean get() = totalBytes > maxBytes

    /** Bytes retained so far (== [totalBytes] while not truncated). */
    val retainedBytes: Int get() = buf.size()

    override fun write(b: Int) {
        totalBytes++
        if (buf.size() < maxBytes) buf.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        totalBytes += len
        if (buf.size() < maxBytes) {
            val space = maxBytes - buf.size()
            buf.write(b, off, if (len <= space) len else space)
        }
    }

    /** The retained (possibly prefix-truncated) payload. */
    fun toByteArray(): ByteArray = buf.toByteArray()

    /** The retained payload as a UTF-8 string (replaces the old `ByteArrayOutputStream.toString`). */
    fun toUtf8String(): String = buf.toByteArray().decodeToString()
}
