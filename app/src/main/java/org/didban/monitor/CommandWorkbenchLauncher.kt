package org.didban.monitor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp

internal data class WorkbenchLauncherItem(
    val key: String,
    val title: String,
    val summary: String,
    val icon: ImageVector,
    val route: CommandRoute
)

private fun routeLauncherItem(copy: CommandCopy, route: CommandRoute): WorkbenchLauncherItem {
    val language = if (copy === CommandCopyFa) "fa" else "en"
    return WorkbenchLauncherItem(
        key = route.key,
        title = route.commandLabel(copy),
        summary = route.helpContent(language).summary,
        icon = route.navIcon(),
        route = route
    )
}

/** Canonical network tile keys, shared with tests to pin the launcher order. */
internal val networkLauncherItemKeys = listOf(
    "network-reachability", "network-layer", "network-dns",
    "network-cloudflare", "network-reality"
)

private fun networkLauncherItems(copy: CommandCopy): List<WorkbenchLauncherItem> = listOf(
    WorkbenchLauncherItem("network-reachability", copy.netQReachable, copy.netQReachableTools, Icons.Rounded.Public, CommandRoute.CHECK_HOST),
    WorkbenchLauncherItem("network-layer", copy.netQNetworkLayer, copy.netQNetworkLayerTools, Icons.Rounded.NetworkCheck, CommandRoute.NETWORK_TOOLS_EDITOR),
    WorkbenchLauncherItem("network-dns", copy.netQDns, copy.netQDnsTools, Icons.Rounded.Dns, CommandRoute.DNS),
    WorkbenchLauncherItem("network-cloudflare", copy.netQCfEdge, copy.netQCfEdgeTools, Icons.Rounded.TravelExplore, CommandRoute.CF_SCANNER),
    WorkbenchLauncherItem("network-reality", copy.netQRealityDonor, copy.netQRealityDonorTools, Icons.Rounded.VerifiedUser, CommandRoute.REALITY_SNI)
)

/**
 * Launcher sections, shared with tests: network diagnostics first, then the
 * remaining utilities. They render as two labeled sections of one page.
 */
internal fun workbenchLauncherSections(copy: CommandCopy): Pair<List<WorkbenchLauncherItem>, List<WorkbenchLauncherItem>> {
    val networkItems = networkLauncherItems(copy)
    val utilityItems = workbenchUtilityRoutes.map { routeLauncherItem(copy, it) }
    return networkItems to utilityItems
}

@Composable
internal fun CommandWorkbenchLauncherScreen(
    copy: CommandCopy,
    onNavigate: (CommandRoute, ServerConfig?) -> Unit
) {
    val (networkItems, utilityItems) = workbenchLauncherSections(copy)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = workbenchLauncherColumns(maxWidth)

        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("workbench-launcher"),
            verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm),
            contentPadding = PaddingValues(horizontal = CommandSpacing.md, vertical = CommandSpacing.sm)
        ) {
            item {
                Text(
                    copy.uiToolsIntro,
                    color = CommandColors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = CommandSpacing.xs)
                )
            }
            item { CommandSectionTitle(copy.networkTools) }

            networkItems.chunked(columns).forEach { rowItems ->
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm),
                        verticalAlignment = Alignment.Top
                    ) {
                        rowItems.forEach { item ->
                            CommandLauncherTile(
                                title = item.title,
                                summary = item.summary,
                                icon = item.icon,
                                modifier = Modifier.weight(1f),
                                testTag = "workbench-tile-${item.key}",
                                onClick = { onNavigate(item.route, null) }
                            )
                        }
                        repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            item { CommandSectionTitle(copy.toolsOtherHeader) }

            utilityItems.chunked(columns).forEach { rowItems ->
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm),
                        verticalAlignment = Alignment.Top
                    ) {
                        rowItems.forEach { item ->
                            CommandLauncherTile(
                                title = item.title,
                                summary = item.summary,
                                icon = item.icon,
                                modifier = Modifier.weight(1f),
                                testTag = "workbench-tile-${item.key}",
                                onClick = { onNavigate(item.route, null) }
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

internal fun workbenchLauncherColumns(width: Dp): Int = when {
    width >= CommandBreakpoints.rail -> 4
    width >= CommandBreakpoints.formStack -> 3
    else -> 2
}
