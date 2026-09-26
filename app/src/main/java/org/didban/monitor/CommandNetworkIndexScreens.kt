package org.didban.monitor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow

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
        subtitle = copy.netIndexBody,
        rows = listOf(
            Triple(copy.netQReachable, copy.netQReachableTools, onOpenCheckHost) to Icons.Rounded.Public,
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
        subtitle = copy.dnsIndexBody,
        rows = listOf(
            Triple(copy.dnsZoneRecords, copy.dnsZoneRecordsBody, onOpenCloudflare) to Icons.Rounded.Dns,
            Triple(copy.dnsInspect, copy.dnsInspectBody, onOpenNetwork) to Icons.Rounded.NetworkCheck
        ),
        copy = copy
    )
}

@Composable
private fun CommandNetworkIndexIntro(subtitle: String, sourceLabel: String) {
    // The route-level CommandPageChrome already owns the page title. Keep this
    // intro descriptive so Network Tools follows the same hierarchy as the
    // other index pages instead of rendering the title twice.
    CommandResponsiveRow(Modifier.padding(top = CommandSpacing.md)) {
        Text(
            subtitle,
            item(weight = 1f),
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = CommandColors.textSecondary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        CommandTelemetryPill(sourceLabel, CommandHealthTone.INFO, modifier = item().wrapContentWidth(Alignment.End))
    }
}

@Composable
private fun CommandNetworkIndex(
    subtitle: String,
    rows: List<Pair<Triple<String, String, () -> Unit>, androidx.compose.ui.graphics.vector.ImageVector>>,
    copy: CommandCopy
) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(CommandSpacing.md)) {
        item {
            CommandNetworkIndexIntro(subtitle, copy.sources)
        }
        item {
            CommandSurface(Modifier.fillMaxWidth()) {
                Column {
                    rows.forEachIndexed { index, (content, icon) ->
                        if (index > 0) CommandRule()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = CommandMetrics.compactRowMinHeight)
                                .testTag("network-index-row-$index")
                                .clickable(role = Role.Button, onClick = content.third)
                                .semantics(mergeDescendants = true) {
                                    contentDescription = listOf(content.first, content.second).joinToString(" · ")
                                }
                                .padding(horizontal = CommandSpacing.md, vertical = CommandSpacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(icon, contentDescription = null, tint = CommandColors.accent, modifier = Modifier.size(CommandMetrics.iconMedium))
                            Spacer(Modifier.width(CommandSpacing.sm))
                            Column(Modifier.weight(1f)) {
                                Text(content.first, color = CommandColors.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.height(CommandSpacing.xxs))
                                Text(content.second, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                            }
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                tint = CommandColors.accent,
                                modifier = Modifier.size(CommandMetrics.iconMedium)
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(CommandSpacing.xl)) }
    }
}
