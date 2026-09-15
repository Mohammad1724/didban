package org.didban.monitor

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
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.TravelExplore
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
    onOpenDns: () -> Unit,
    onOpenCfScanner: () -> Unit,
    onOpenRealitySni: () -> Unit
) {
    CommandNetworkIndex(
        title = copy.networkTools,
        subtitle = copy.netIndexBody,
        rows = listOf(
            Triple(copy.netQReachable, copy.netQReachableTools, onOpenRadar) to Icons.Rounded.Public,
            Triple(copy.netQNetworkLayer, copy.netQNetworkLayerTools, onOpenSuite) to Icons.Rounded.NetworkCheck,
            Triple(copy.netQTls, copy.netQTlsTools, onOpenSuite) to Icons.Rounded.Security,
            Triple(copy.netQDns, copy.netQDnsTools, onOpenDns) to Icons.Rounded.Dns,
            Triple(copy.netQQuality, copy.netQQualityTools, onOpenSuite) to Icons.Rounded.Speed,
            Triple(copy.netQCfEdge, copy.netQCfEdgeTools, onOpenCfScanner) to Icons.Rounded.TravelExplore,
            Triple(copy.netQRealityDonor, copy.netQRealityDonorTools, onOpenRealitySni) to Icons.Rounded.VerifiedUser
        ),
        copy = copy
    )
}

@Composable
fun CommandDnsIndexScreen(
    copy: CommandCopy,
    onOpenCloudflare: () -> Unit,
    onOpenNetwork: () -> Unit
) {
    CommandNetworkIndex(
        title = copy.dns,
        subtitle = copy.dnsIndexBody,
        rows = listOf(
            Triple(copy.dnsZoneRecords, copy.dnsZoneRecordsBody, onOpenCloudflare) to Icons.Rounded.Dns,
            Triple(copy.dnsInspect, copy.dnsInspectBody, onOpenNetwork) to Icons.Rounded.NetworkCheck
        ),
        copy = copy
    )
}

@Composable
private fun CommandNetworkIndex(
    title: String,
    subtitle: String,
    rows: List<Pair<Triple<String, String, () -> Unit>, androidx.compose.ui.graphics.vector.ImageVector>>,
    copy: CommandCopy
) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(CommandSpacing.md)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = CommandSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(48.dp)
                        .background(CommandColors.accent.copy(alpha = 0.10f), androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                        .border(1.dp, CommandColors.accent.copy(alpha = 0.32f), androidx.compose.foundation.shape.RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.NetworkCheck, contentDescription = null, tint = CommandColors.accent, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(CommandSpacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(title, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall, color = CommandColors.textPrimary)
                    Text(subtitle, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = CommandColors.textSecondary)
                }
                CommandTelemetryPill(copy.sources, CommandHealthTone.INFO)
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
