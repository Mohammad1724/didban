package org.didban.monitor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class NetworkTargetPolicyTest {
    @Test
    fun `public HTTPS URL is accepted`() {
        val uri = NetworkTargetPolicy.requirePublicHttps("https://example.com/path")
        assertTrue(uri.host == "example.com")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `HTTP URL is rejected`() {
        NetworkTargetPolicy.requirePublicHttps("http://example.com/path")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `URL credentials are rejected`() {
        NetworkTargetPolicy.requirePublicHttps("https://user:pass@example.com/path")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `nonstandard HTTPS port is rejected`() {
        NetworkTargetPolicy.requirePublicHttps("https://example.com:8443/path")
    }

    @Test
    fun `local metadata and private addresses are rejected`() {
        listOf("127.0.0.1", "10.0.0.1", "172.16.0.1", "192.168.1.1", "169.254.169.254", "100.64.0.1", "::1", "fc00::1")
            .forEach { assertFalse(it, NetworkTargetPolicy.isPublicAddress(InetAddress.getByName(it))) }
    }

    @Test
    fun `ordinary public addresses are accepted`() {
        assertTrue(NetworkTargetPolicy.isPublicAddress(InetAddress.getByName("1.1.1.1")))
        assertTrue(NetworkTargetPolicy.isPublicAddress(InetAddress.getByName("2606:4700:4700::1111")))
    }
}
