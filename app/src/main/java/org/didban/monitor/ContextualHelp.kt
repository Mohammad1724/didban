package org.didban.monitor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.key
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Structured, bilingual help shown from every command route. */
data class HelpCommand(val label: String, val value: String)

data class CommandHelpContent(
    val summary: String,
    val steps: List<String>,
    val tip: String? = null,
    val warning: String? = null,
    val commands: List<HelpCommand> = emptyList(),
    val prerequisite: String? = null
)

/** Visible, labelled help entry point with a 48dp minimum touch target. */
@Composable
internal fun CommandHelpButton(language: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp).testTag("page-help")) {
        Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(CommandSpacing.xs))
        Text(if (language == "fa") "راهنما" else "Help", maxLines = 1)
    }
}

@Composable
fun CommandHelpDialog(route: CommandRoute, language: String, copy: CommandCopy, onDismiss: () -> Unit) {
    val content = route.helpContent(language)
    val fa = language == "fa"
    val clipboard = LocalClipboardManager.current
    var copiedCommand by remember(route, language) { mutableStateOf<String?>(null) }
    key(route, language) {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f).testTag("page-guide"),
                shape = RoundedCornerShape(20.dp),
                color = CommandColors.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, CommandColors.borderStrong)
            ) {
                Column(Modifier.padding(CommandSpacing.lg)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = null, tint = CommandColors.accent, modifier = Modifier.size(28.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(if (fa) "راهنمای صفحه" else "Page guide", color = CommandColors.textTertiary, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                            Text(route.commandLabel(copy), color = CommandColors.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                        }
                        IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, if (fa) "بستن" else "Close", tint = CommandColors.textSecondary) }
                    }
                    CommandRule(Modifier.padding(vertical = CommandSpacing.md))
                    LazyColumn(Modifier.weight(1f).testTag("help-body"), verticalArrangement = Arrangement.spacedBy(CommandSpacing.md)) {
                        item { Text(if (fa) "این بخش برای چیست؟" else "What is this for?", color = CommandColors.textPrimary, fontWeight = FontWeight.Bold) }
                        item { Text(content.summary, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge) }
                        content.prerequisite?.let { need -> item { HelpNote(Icons.Rounded.Lightbulb, if (fa) "چه چیزی لازم دارم؟" else "What do I need?", need, CommandColors.info) } }
                        item { Text(if (fa) "چطور شروع کنم؟" else "How do I start?", color = CommandColors.textPrimary, fontWeight = FontWeight.Bold) }
                        itemsIndexed(content.steps) { index, step ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text("${index + 1}", color = CommandColors.accent, modifier = Modifier.padding(end = CommandSpacing.sm), fontWeight = FontWeight.Bold)
                                SelectionContainer {
                                    Text(step, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        if (content.commands.isNotEmpty()) {
                            item { Text(if (fa) "دستورهای آماده" else "Ready-to-copy commands", color = CommandColors.textPrimary, fontWeight = FontWeight.Bold) }
                            itemsIndexed(content.commands) { _, command ->
                                CommandSurface(Modifier.fillMaxWidth(), raised = true) {
                                    Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(command.label, color = CommandColors.textPrimary, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                            CommandTextButton(
                                                if (copiedCommand == command.value) {
                                                    if (fa) "کپی شد" else "Copied"
                                                } else {
                                                    if (fa) "کپی" else "Copy"
                                                },
                                                {
                                                    clipboard.setText(AnnotatedString(command.value))
                                                    copiedCommand = command.value
                                                },
                                                Icons.Rounded.ContentCopy
                                            )
                                        }
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                            SelectionContainer {
                                                Text(command.value, color = CommandColors.info, style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontFamily = Telemetry))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item { Text(if (fa) "نکات و محدودیت‌ها" else "Tips and limitations", color = CommandColors.textPrimary, fontWeight = FontWeight.Bold) }
                        content.tip?.let { tip -> item { HelpNote(Icons.Rounded.Lightbulb, if (fa) "نکته" else "Tip", tip, CommandColors.info) } }
                        content.warning?.let { warning -> item { HelpNote(Icons.Rounded.WarningAmber, if (fa) "هشدار" else "Warning", warning, CommandColors.warning) } }
                        item { Spacer(Modifier.height(CommandSpacing.sm)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpNote(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, color: Color) {
    CommandSurface(Modifier.fillMaxWidth(), raised = true) {
        Row(Modifier.padding(CommandSpacing.md), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(21.dp))
            Spacer(Modifier.size(CommandSpacing.sm))
            Column {
                Text(title, color = color, fontWeight = FontWeight.Bold, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(CommandSpacing.xxs))
                Text(body, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
