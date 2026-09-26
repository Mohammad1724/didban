package org.didban.monitor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(CommandSpacing.md),
        contentPadding = PaddingValues(vertical = CommandSpacing.md)
    ) {
        item { CommandSectionTitle(title) }
        item {
            Text(
                subtitle,
                color = CommandColors.textSecondary,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
            )
        }
        rows.forEachIndexed { index, (content, icon) ->
            item(key = "network-index-row-$index") {
                CommandToolLink(
                    title = content.first,
                    detail = content.second,
                    icon = icon,
                    testTag = "network-index-row-$index",
                    onClick = content.third
                )
            }
        }
        item { Spacer(Modifier.height(CommandSpacing.xl)) }
    }
}
