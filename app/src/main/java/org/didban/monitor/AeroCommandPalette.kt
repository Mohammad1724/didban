package org.didban.monitor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tersearch
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Phase 3 · item 3-D — the v3 Command Palette (layer 4).
 *
 * One searchable surface over every global action: per-server cockpit
 * opens, per-tunnel test/deploy/toggle, theme, language, manage servers,
 * global refresh. Launched from the HUD; dismissed with Back or scrim tap.
 *
 * UI-only: verified by code review (no Android/Compose compile here).
 */

data class PaletteCommand(
    val id: String,
    val label: String,
    val hint: String = "",
    val icon: ImageVector,
    val action: () -> Unit
)

@Composable
fun AeroCommandPalette(
    t: Str,
    commands: List<PaletteCommand>,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }

    BackHandler { onDismiss() }

    val filtered = if (query.isBlank()) {
        commands
    } else {
        commands.filter { c ->
            c.label.contains(query, ignoreCase = true) || c.hint.contains(query, ignoreCase = true)
        }.take(9)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(onClick = onDismiss)
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(top = 88.dp, start = 18.dp, end = 18.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .v3Surface()
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Tersearch,
                        contentDescription = null,
                        tint = Ds.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        t.paletteTitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Ds.textPrimary
                    )
                }
                Hairline()
                SearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = t.paletteHint,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                )
                Hairline()

                if (filtered.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 22.dp), contentAlignment = Alignment.Center) {
                        Text("—", fontSize = 13.sp, color = Ds.textTertiary)
                    }
                } else {
                    Column(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                        filtered.forEachIndexed { i, cmd ->
                            if (i > 0) v3RowDivider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        cmd.action()
                                        onDismiss()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(cmd.icon, contentDescription = null, tint = Ds.textSecondary, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    cmd.label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Ds.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (cmd.hint.isNotBlank()) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        cmd.hint,
                                        fontSize = 9.5.sp,
                                        fontFamily = Telemetry,
                                        color = Ds.textTertiary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
