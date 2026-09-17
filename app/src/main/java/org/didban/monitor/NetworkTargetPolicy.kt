package org.didban.monitor

import okhttp3.Dns
import java.net.InetAddress
import java.net.URI

/** Security policy for outbound requests that must never reach local services. */
object NetworkTargetPolicy {
    private const val MAX_URL_LENGTH = 4_096

    fun requirePublicHttps(raw: String, allowedHosts: Set<String>? = null): URI {
        require(raw.length in 1..MAX_URL_LENGTH) { "URL length is invalid" }
        val uri = URI(raw.trim())
        require(uri.scheme.equals("https", ignoreCase = true)) { "HTTPS is required" }
        require(uri.rawUserInfo == null && uri.rawFragment == null) { "URL credentials and fragments are not allowed" }
        val host = uri.host?.trimEnd('.')?.lowercase() ?: error("URL host is missing")
        require(host.isNotBlank()) { "URL host is missing" }
        if (allowedHosts != null) require(host in allowedHosts) { "URL host is not allowed" }
        require(uri.port == -1 || uri.port == 443) { "Only HTTPS port 443 is allowed" }
        return uri
    }

    fun isPublicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress) return false
        val b = address.address
        // IPv4 carrier-grade NAT and documentation/benchmark ranges should not
        // be used as an escape hatch into provider-specific control planes.
        if (b.size == 4) {
            val a = b[0].toInt() and 0xff
            val c = b[1].toInt() and 0xff
            val d = b[2].toInt() and 0xff
            if (a == 100 && c in 64..127) return false
            if (a == 192 && c == 0 && d in setOf(0, 2)) return false
            if (a == 198 && (c in 18..19 || (c == 51 && d == 100))) return false
            if (a == 203 && c == 0 && d == 113) return false
        } else if (b.size == 16) {
            // IPv6 unique-local fc00::/7.
            if ((b[0].toInt() and 0xfe) == 0xfc) return false
        }
        return true
    }
}

/** Resolves at connection time and rejects every mixed/private DNS answer. */
object PublicOnlyDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = Dns.SYSTEM.lookup(hostname)
        require(addresses.isNotEmpty() && addresses.all(NetworkTargetPolicy::isPublicAddress)) {
            "Destination resolves to a private or reserved address"
        }
        return addresses
    }
}
