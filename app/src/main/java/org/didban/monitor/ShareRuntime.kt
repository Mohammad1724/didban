package org.didban.monitor

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.Socket

/**
 * وضعیت زندهٔ ابزار «اشتراک اینترنت با VPN».
 *
 * سرویس پیش‌زمینه نمی‌تواند [StateFlow] را از راه binding به رابط کاربری بدهد
 * بی‌آنکه چرخهٔ عمر پیچیده شود؛ این شیء همه‌جا یکتا است و هر دو طرف (سرویس و
 * صفحه) فقط همین را می‌خوانند/می‌نویسند.
 */
internal object ShareRuntime {

    data class Status(
        val running: Boolean = false,
        val port: Int = 1080,
        val clients: Int = 0,
        val uploadBytes: Long = 0L,
        val downloadBytes: Long = 0L,
        val rejected: Long = 0L,
        val peers: Map<String, Long> = emptyMap(),
        val vpnActive: Boolean = false,
        val vpnLabel: String? = null,
        val addresses: List<String> = emptyList(),
        val eventKey: String? = null,
        val failure: String? = null
    ) {
        val proxyAddress: String? get() = addresses.firstOrNull()?.let { "$it:$port" }
    }

    private val state = MutableStateFlow(Status())
    val status: StateFlow<Status> = state

    fun publish(transform: (Status) -> Status) {
        state.value = transform(state.value)
    }

    fun reset(port: Int) {
        state.value = Status(port = port)
    }
}

/**
 * تشخیص شبکهٔ VPN فعال و آدرس‌های محلی گوشی.
 *
 * هیچ روت یا API سیستمی لازم نیست: کلید کار این است که هر سوکت خروجی صریحاً
 * به شبکهٔ VPN بایند شود تا ترافیک دستگاه‌های متصل از تونل بیرون برود.
 */
internal object ShareNetworks {

    fun vpnNetwork(context: Context): Network? {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return null
        // Fast path: with a full-tunnel VPN the active network already is the tunnel.
        runCatching {
            manager.activeNetwork?.takeIf {
                manager.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            }
        }.getOrNull()?.let { return it }
        // Otherwise look for any VPN network. getAllNetworks() is deprecated on
        // API 31+ in favour of callbacks, but there is no callback-free way to ask
        // "is a VPN up right now?", and a per-screen callback registry would leak
        // registrations across screens.
        @Suppress("DEPRECATION")
        return runCatching {
            manager.allNetworks.firstOrNull { network ->
                manager.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            }
        }.getOrNull()
    }

    /** نام بستهٔ اپ VPN در صورت امکان؛ اگر API اجازه ندهد null می‌ماند. */
    fun vpnOwnerLabel(context: Context, network: Network?): String? {
        if (network == null) return null
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val uid = runCatching { manager.getNetworkCapabilities(network)?.ownerUid }.getOrNull() ?: return null
        return runCatching {
            context.packageManager.getPackagesForUid(uid)?.firstOrNull()
        }.getOrNull()
    }

    /**
     * آدرس‌های محلی قابل اعلام به مشتری‌ها: اول اینترفیس هات‌اسپات، بعد بقیهٔ
     * آدرس‌های خصوصی. آدرس عمومی هرگز نمایش داده نمی‌شود.
     */
    fun localAddresses(): List<String> {
        val found = mutableListOf<Pair<Int, String>>()
        runCatching {
            for (iface in NetworkInterface.getNetworkInterfaces() ?: return@runCatching) {
                if (!iface.isUp || iface.isLoopback) continue
                val name = iface.name.lowercase()
                for (address in iface.inetAddresses) {
                    if (address !is Inet4Address) continue
                    val text = address.hostAddress ?: continue
                    if (!ShareProtocol.isPrivateAddress(text)) continue
                    val rank = when {
                        name.startsWith("ap") || name.contains("softap") || name.contains("swlan") -> 0
                        name.startsWith("wlan") -> 1
                        name.startsWith("usb") || name.startsWith("rndis") -> 2
                        else -> 3
                    }
                    found += rank to text
                }
            }
        }
        return found.sortedBy { it.first }.map { it.second }.distinct()
    }

    /** اینترفیس هات‌اسپات فعال است؟ (بدون نیاز به دسترسی سیستمی) */
    fun hotspotInterfaceName(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList()
            ?.firstOrNull { iface ->
                iface.isUp && !iface.isLoopback && iface.inetAddresses.toList().any { it is Inet4Address } &&
                    iface.name.lowercase().let { n ->
                        n.startsWith("ap") || n.contains("softap") || n.contains("swlan") || n.contains("wlan1")
                    }
            }?.name
    }.getOrNull()
}

/**
 * اتصال خروجی از راه شبکهٔ VPN.
 *
 * اگر VPN روشن نباشد و کاربر «الزام VPN» را خاموش نکرده باشد، هیچ سوکتی باز
 * نمی‌شود؛ این تنها محافظ واقعی در برابر لو رفتن ناخواستهٔ ترافیک است، چون
 * نمی‌توانیم بستهٔ forward شده را کنترل کنیم و فقط سوکت خودمان را می‌سازیم.
 */
internal class AndroidShareDialer(
    private val context: Context,
    private val requireVpn: Boolean
) : ShareDialer {

    override fun open(host: String, port: Int): Socket {
        val vpn = ShareNetworks.vpnNetwork(context)
        if (vpn == null) {
            if (requireVpn) throw ShareDialer.NoVpnException()
            return Socket().apply { connect(java.net.InetSocketAddress(host, port), CONNECT_TIMEOUT_MS) }
        }
        val address = if (isLiteralAddress(host)) null else runCatching { vpn.getAllByName(host).firstOrNull() }.getOrNull()
        val target = address?.hostAddress ?: host
        val socket = vpn.socketFactory.createSocket() as Socket
        socket.tcpNoDelay = true
        socket.connect(java.net.InetSocketAddress(target, port), CONNECT_TIMEOUT_MS)
        return socket
    }

    override fun describe(): String = if (ShareNetworks.vpnNetwork(context) != null) "vpn" else "direct"

    private fun isLiteralAddress(host: String): Boolean =
        host.any { it == ':' } || host.count { it == '.' } == 3 && host.all { it.isDigit() || it == '.' }

    companion object {
        const val CONNECT_TIMEOUT_MS = 12_000
    }
}
