package org.didban.monitor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CommandNetworkIndexScreen(
    copy: CommandCopy,
    onOpenSuite: () -> Unit,
    onOpenRadar: () -> Unit,
    onOpenDns: () -> Unit
) {
    val fa = copy == CommandCopy.fa
    CommandNetworkIndex(
        title = copy.networkTools,
        subtitle = if (fa) "هر ابزار با سؤال تشخیصی خودش شروع می‌شود." else "Start with the diagnostic question, not a tool list.",
        rows = listOf(
            Triple(if (fa) "آیا مقصد از این Server قابل دسترسی است؟" else "Is the target reachable from this server?", if (fa) "TCP، HTTP، SSL و Check-Host" else "TCP, HTTP, SSL and Check-Host", onOpenRadar) to Icons.Rounded.Public,
            Triple(if (fa) "آیا مشکل در لایهٔ شبکه است؟" else "Is the problem in the network layer?", if (fa) "DPI، Port Scanner و TCP Ping" else "DPI, port scanner and TCP ping", onOpenSuite) to Icons.Rounded.NetworkCheck,
            Triple(if (fa) "گواهی و هویت TLS درست است؟" else "Is the TLS identity valid?", if (fa) "Subject، Chain، SAN و Fingerprint" else "Subject, chain, SAN and fingerprint", onOpenSuite) to Icons.Rounded.Security,
            Triple(if (fa) "DNS چه پاسخی می‌دهد؟" else "What does DNS return?", if (fa) "Recordها و Cloudflare" else "Records and Cloudflare", onOpenDns) to Icons.Rounded.Dns,
            Triple(if (fa) "کیفیت اتصال چقدر است؟" else "What is the connection quality?", if (fa) "Latency، Loss، Jitter و Bandwidth" else "Latency, loss, jitter and bandwidth", onOpenSuite) to Icons.Rounded.Speed
        )
    )
}

@Composable
fun CommandDnsIndexScreen(
    copy: CommandCopy,
    onOpenCloudflare: () -> Unit,
    onOpenNetwork: () -> Unit
) {
    val fa = copy == CommandCopy.fa
    CommandNetworkIndex(
        title = copy.dns,
        subtitle = if (fa) "تشخیص پاسخ DNS از مدیریت Record جداست." else "DNS diagnosis is separate from record management.",
        rows = listOf(
            Triple(if (fa) "مدیریت Zone و Record" else "Manage zones and records", if (fa) "ساخت، ویرایش و حذف Recordهای واقعی Cloudflare" else "Create, edit and delete real Cloudflare records", onOpenCloudflare) to Icons.Rounded.Dns,
            Triple(if (fa) "بررسی پاسخ DNS" else "Inspect DNS resolution", if (fa) "Resolve، Reverse DNS و Recordهای عمومی" else "Resolve, reverse DNS and public records", onOpenNetwork) to Icons.Rounded.NetworkCheck
        )
    )
}

@Composable
private fun CommandNetworkIndex(
    title: String,
    subtitle: String,
    rows: List<Pair<Triple<String, String, () -> Unit>, androidx.compose.ui.graphics.vector.ImageVector>>
) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(CommandSpacing.md)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = CommandSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.NetworkCheck, contentDescription = null, tint = CommandColors.accent, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(CommandSpacing.sm))
                Column {
                    Text(title, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall, color = CommandColors.textPrimary)
                    Text(subtitle, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = CommandColors.textSecondary)
                }
            }
        }
        item {
            CommandSurface(Modifier.fillMaxWidth()) {
                Column {
                    rows.forEachIndexed { index, (content, icon) ->
                        if (index > 0) CommandRule()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(onClick = content.third)
                                .padding(horizontal = CommandSpacing.md, vertical = CommandSpacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(icon, contentDescription = null, tint = CommandColors.accent, modifier = Modifier.size(23.dp))
                            Spacer(Modifier.width(CommandSpacing.sm))
                            Column(Modifier.weight(1f)) {
                                Text(content.first, color = CommandColors.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.height(CommandSpacing.xxs))
                                Text(content.second, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                            }
                            Text("›", color = CommandColors.accent, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(CommandSpacing.xl)) }
    }
}
