package org.didban.monitor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun CommandNetworkIndexScreen(
    copy: CommandCopy,
    onOpenSuite: () -> Unit,
    onOpenCheckHost: () -> Unit,
    onOpenDns: () -> Unit,
    onOpenCfScanner: () -> Unit,
    onOpenRealitySni: () -> Unit
) {
    CommandNetworkIndex(
        title = copy.networkTools,
        subtitle = copy.netIndexBody,
        rows = listOf(
            Triple(copy.netQReachable, copy.netQReachableTools, onOpenCheckHost) to Icons.Rounded.Public,
            Triple(copy.netQNetworkLayer, copy.netQNetworkLayerTools, onOpenSuite) to Icons.Rounded.NetworkCheck,
            Triple(copy.netQTls, copy.netQTlsTools, onOpenSuite) to Icons.Rounded.Security,
            Triple(copy.netQDns, copy.netQDnsTools, onOpenDns) to Icons.Rounded.Dns,
            Triple(copy.netQQuality, copy.netQQualityTools, onOpenSuite) to Icons.Rounded.Speed,
            Triple(copy.netQCfEdge, copy.netQCfEdgeTools, onOpenCfScanner) to Icons.Rounded.TravelExplore,
            Triple(copy.netQRealityDonor, copy.netQRealityDonorTools, onOpenRealitySni) to Icons.Rounded.VerifiedUser
        )
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
        )
    )
}

@Composable
private fun CommandNetworkIndex(
    title: String,
    subtitle: String,
    rows: List<Pair<Triple<String, String, () -> Unit>, ImageVector>>
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = workbenchLauncherColumns(maxWidth)
        LazyColumn(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm),
            contentPadding = PaddingValues(horizontal = CommandSpacing.md, vertical = CommandSpacing.sm)
        ) {
            item {
                // The route-level CommandPageChrome owns the title. This keeps
                // the same intro hierarchy as the general icon launcher.
                CommandSectionTitle(title)
                Text(
                    subtitle,
                    color = CommandColors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = CommandSpacing.xs)
                )
            }
            rows.chunked(columns).forEachIndexed { rowIndex, rowItems ->
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm),
                        verticalAlignment = Alignment.Top
                    ) {
                        rowItems.forEachIndexed { columnIndex, (content, icon) ->
                            CommandLauncherTile(
                                title = content.first,
                                summary = content.second,
                                icon = icon,
                                modifier = Modifier.weight(1f),
                                testTag = "network-index-row-${rowIndex * columns + columnIndex}",
                                onClick = content.third
                            )
                        }
                        repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            item { Spacer(Modifier.height(CommandSpacing.xl)) }
        }
    }
}
