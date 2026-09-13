package org.didban.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.OutputStreamWriter

class CappedOutputStreamTest {

    @Test
    fun `writes under cap are fully retained`() {
        val s = CappedOutputStream(10)
        s.write("12345".toByteArray())
        s.write("6789".toByteArray())
        assertEquals("123456789", s.toByteArray().toString(Charsets.UTF_8))
        assertEquals(9L, s.totalBytes)
        assertFalse(s.isTruncated)
    }

    @Test
    fun `writes beyond cap stop retaining but keep counting`() {
        val s = CappedOutputStream(10)
        s.write(ByteArray(25) { 'a'.code.toByte() })
        assertEquals(10, s.retainedBytes)
        assertEquals(25L, s.totalBytes)
        assertTrue(s.isTruncated)
        assertEquals("aaaaaaaaaa", s.toByteArray().toString(Charsets.UTF_8))
    }

    @Test
    fun `exactly at cap is not truncated`() {
        val s = CappedOutputStream(10)
        s.write(ByteArray(10) { 'b'.code.toByte() })
        assertFalse(s.isTruncated)
        assertEquals(10, s.retainedBytes)
    }

    @Test
    fun `array write crossing the cap is clipped at the boundary`() {
        val s = CappedOutputStream(6)
        s.write(ByteArray(4) { 'a'.code.toByte() })      // 4 retained
        s.write(ByteArray(10) { 'b'.code.toByte() })  // only 2 of 10 fit
        assertEquals(6, s.retainedBytes)
        assertEquals(14L, s.totalBytes)
        assertTrue(s.isTruncated)
        assertEquals("aaaabbbb".substring(0, 6), s.toByteArray().toString(Charsets.UTF_8))
    }

    @Test
    fun `writes after cap keep succeeding and never grow the buffer`() {
        val s = CappedOutputStream(100)
        s.write(ByteArray(1000) { 'x'.code.toByte() })
        // keep writing: buffer must stay bounded, total must grow
        val before = s.retainedBytes
        s.write(ByteArray(1_000_000) { 'y'.code.toByte() })
        assertEquals(before, s.retainedBytes)
        assertEquals(1_001_000L, s.totalBytes)
        assertEquals(100, s.toByteArray().size)
    }

    @Test
    fun `works through an OutputStreamWriter`() {
        val s = CappedOutputStream(20)
        OutputStreamWriter(s).use { w -> w.write("hello world, this is longer than twenty") }
        assertTrue(s.isTruncated)
        assertEquals(20, s.toByteArray().size)
    }

    @Test
    fun `zero or negative cap is rejected`() {
        try {
            CappedOutputStream(0)
            throw AssertionError("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }
}
