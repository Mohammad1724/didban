package org.didban.monitor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
private fun CommandNetworkIndexHeader(title: String, subtitle: String, sourceLabel: String) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = CommandSpacing.md)) {
        val narrow = maxWidth < CommandBreakpoints.formStack
        if (narrow) {
            Column(verticalArrangement = Arrangement.spacedBy(CommandSpacing.xs)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    NetworkIndexHeaderIcon()
                    Spacer(Modifier.width(CommandSpacing.sm))
                    Column(Modifier.weight(1f)) {
                        Text(title, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall, color = CommandColors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(subtitle, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = CommandColors.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
                CommandTelemetryPill(sourceLabel, CommandHealthTone.INFO, Modifier.align(Alignment.End))
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                NetworkIndexHeaderIcon()
                Spacer(Modifier.width(CommandSpacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(title, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall, color = CommandColors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = CommandColors.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                CommandTelemetryPill(sourceLabel, CommandHealthTone.INFO)
            }
        }
    }
}

@Composable
private fun NetworkIndexHeaderIcon() {
    Box(
        Modifier
            .size(CommandMetrics.touchTarget)
            .background(CommandColors.accent.copy(alpha = 0.10f), androidx.compose.foundation.shape.RoundedCornerShape(CommandRadii.icon))
            .border(CommandMetrics.borderWidth, CommandColors.accent.copy(alpha = 0.32f), androidx.compose.foundation.shape.RoundedCornerShape(CommandRadii.icon)),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.NetworkCheck, contentDescription = null, tint = CommandColors.accent, modifier = Modifier.size(CommandMetrics.iconMedium))
    }
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
            CommandNetworkIndexHeader(title, subtitle, copy.sources)
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
                                    contentDescription = listOf(content.first, content.second)
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
