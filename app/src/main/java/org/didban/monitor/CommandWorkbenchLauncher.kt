package org.didban.monitor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp

private data class WorkbenchTab(
    val label: String,
    val routes: List<CommandRoute>,
    val destination: CommandRoute? = null
)

private val connectionRoutes = listOf(
    CommandRoute.PROXY,
    CommandRoute.SHARE,
    CommandRoute.SINGLE_PORT
)

@Composable
internal fun CommandWorkbenchLauncherScreen(
    copy: CommandCopy,
    onNavigate: (CommandRoute, ServerConfig?) -> Unit
) {
    val tabs = listOf(
        WorkbenchTab(copy.toolsTabAll, workbenchUtilityRoutes),
        WorkbenchTab(copy.networkTools, emptyList(), destination = CommandRoute.NETWORK_TOOLS),
        WorkbenchTab(copy.toolsTabConnection, connectionRoutes),
        WorkbenchTab(copy.toolsTabDevelopment, listOf(CommandRoute.DEVELOPER_LAB))
    )
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val visibleRoutes = tabs[selectedTab.coerceIn(tabs.indices)].routes
        val columns = workbenchLauncherColumns(maxWidth)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
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

        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(CommandSpacing.xs)
            ) {
                tabs.forEachIndexed { index, tab ->
                    val active = index == selectedTab
                    val destination = tab.destination
                    val tabModifier = Modifier
                        .testTag("workbench-tab-$index")
                        .heightIn(min = CommandMetrics.touchTarget)
                        .clip(RoundedCornerShape(CommandRadii.pill))
                        .semantics {
                            if (destination != null) stateDescription = copy.networkTools
                        }
                        .then(
                            if (destination != null) {
                                Modifier.clickable(role = Role.Button) { onNavigate(destination, null) }
                            } else {
                                Modifier.selectable(selected = active, role = Role.Tab) { selectedTab = index }
                            }
                        )
                    Surface(
                        color = if (active) CommandColors.accent else CommandColors.surface,
                        contentColor = if (active) CommandColors.onAccent else CommandColors.textSecondary,
                        shape = RoundedCornerShape(CommandRadii.pill),
                        modifier = tabModifier
                    ) {
                        Text(
                            tab.label,
                            modifier = Modifier.padding(horizontal = CommandSpacing.md, vertical = CommandSpacing.xs),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

            visibleRoutes.chunked(columns).forEach { rowRoutes ->
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm),
                        verticalAlignment = Alignment.Top
                    ) {
                        rowRoutes.forEach { route ->
                            WorkbenchLauncherTile(
                                copy = copy,
                                route = route,
                                modifier = Modifier.weight(1f),
                                onClick = { onNavigate(route, null) }
                            )
                        }
                        repeat(columns - rowRoutes.size) { Spacer(Modifier.weight(1f)) }
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

@Composable
private fun WorkbenchLauncherTile(
    copy: CommandCopy,
    route: CommandRoute,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val language = if (copy === CommandCopyFa) "fa" else "en"
    val summary = route.helpContent(language).summary
    Column(
        modifier
            .heightIn(min = CommandMetrics.launcherTileMinHeight)
            .clip(RoundedCornerShape(CommandRadii.card))
            .testTag("workbench-tile-${route.key}")
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = listOf(route.commandLabel(copy), summary).joinToString(" · ")
            }
            .padding(horizontal = CommandSpacing.xs, vertical = CommandSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Box(
            Modifier
                .size(CommandMetrics.launcherIcon)
                .clip(RoundedCornerShape(CommandRadii.icon))
                .background(CommandColors.infoSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                route.navIcon(),
                contentDescription = null,
                tint = CommandColors.accent,
                modifier = Modifier.size(CommandMetrics.iconLarge)
            )
        }
        Spacer(Modifier.height(CommandSpacing.xs))
        Text(
            route.commandLabel(copy),
            color = CommandColors.textPrimary,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            summary,
            color = CommandColors.textTertiary,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
