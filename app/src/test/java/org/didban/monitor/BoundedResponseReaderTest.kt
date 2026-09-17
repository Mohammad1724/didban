package org.didban.monitor

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class BoundedResponseReaderTest {
    private val text = "text/plain; charset=utf-8".toMediaType()

    @Test fun `accepts response exactly at limit`() {
        val value = "a".repeat(32)
        assertEquals(value, BoundedResponseReader.readUtf8(value.toResponseBody(text), 32))
    }

    @Test fun `rejects declared response above limit`() {
        try {
            BoundedResponseReader.readUtf8("a".repeat(33).toResponseBody(text), 32)
            fail("expected ResponseTooLargeException")
        } catch (e: ResponseTooLargeException) {
            assertEquals(32, e.limitBytes)
        }
    }

    @Test fun `rejects unknown length chunked response above limit`() {
        val payload = Buffer().writeUtf8("x".repeat(33))
        val body = object : ResponseBody() {
            override fun contentType() = text
            override fun contentLength() = -1L
            override fun source(): BufferedSource = payload
        }
        try {
            BoundedResponseReader.readUtf8(body, 32)
            fail("expected ResponseTooLargeException")
        } catch (e: ResponseTooLargeException) {
            assertEquals(32, e.limitBytes)
        }
    }

    @Test fun `null body is empty`() {
        assertEquals("", BoundedResponseReader.readUtf8(null, 1))
    }

    @Test fun `requires positive limit`() {
        try {
            BoundedResponseReader.readUtf8("x".toResponseBody(text), 0)
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
    }
}
