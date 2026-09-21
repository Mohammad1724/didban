@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package org.didban.monitor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * ابزار «اشتراک اینترنت با VPN».
 *
 * کاربر VPN را روی گوشی روشن می‌کند، هات‌اسپات را روشن می‌کند، و این ابزار یک
 * پروکسی روی خودِ گوشی بالا می‌آورد که خروجی‌اش از تونل VPN می‌رود. دستگاه‌های
 * دیگر (لپ‌تاپ، گوشی دوم) آدرس این پروکسی را می‌گیرند و از همان VPN استفاده
 * می‌کنند.
 *
 * صادقانه: webOS تلویزیون LG تنظیم پروکسی ندارد؛ برای تلویزیون بدون روت
 * راهی جز گزینهٔ رام («Allow clients to use VPNs») یا روت نیست. این محدودیت
 * در همین صفحه به کاربر گفته می‌شود، نه پنهان.
 */
@Composable
fun CommandShareScreen(copy: CommandCopy, onBack: () -> Unit, onHelp: () -> Unit) {
    // onHelp در پوستهٔ مسیر به سرآیند وصل است؛ اینجا نگه داشته می‌شود تا
    // امضای صفحه با بقیهٔ ابزارها یکی بماند.
    @Suppress("UNUSED_PARAMETER") val help = onHelp
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val status by ShareRuntime.status.collectAsState()
    var saved by remember { mutableStateOf(Prefs.getShareConfig(context)) }
    var draft by remember { mutableStateOf(saved) }
    var portText by rememberSaveable { mutableStateOf(saved.port.toString()) }
    var copied by remember { mutableStateOf(false) }
    // وضعیت هات‌اسپات تعیین می‌کند دستگاه‌ها اصلاً می‌توانند وصل شوند یا نه.
    val hotspot = remember(status.running) { ShareNetworks.hotspotInterfaceName() }

    val fieldError = when (draft.errorKey) {
        "port" -> copy.shareErrorPort
        "protocol" -> copy.shareErrorProtocol
        "username" -> copy.shareErrorUsername
        "password" -> copy.shareErrorPassword
        else -> null
    }
    val dirty = draft != saved
    val vpnMissing = !status.vpnActive && draft.requireVpn

    Column(Modifier.fillMaxSize()) {
        // سرآیند مسیر را پوسته می‌سازد؛ اینجا فقط دکمهٔ بازگشت لازم است تا
        // عنوان و «راهنما» دو بار تکرار نشوند.
        Row(Modifier.fillMaxWidth().padding(horizontal = CommandSpacing.sm, vertical = CommandSpacing.xs)) {
            CommandBackButton(copy.back, onBack)
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(CommandSpacing.md),
            verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)
        ) {
            item {
                CommandHeroCard(
                    eyebrow = copy.shareEyebrow,
                    title = if (status.running) copy.shareRunning else copy.shareStopped,
                    body = copy.shareHeroBody,
                    score = if (status.running) 100 else null,
                    gaugeLabel = copy.shareLive,
                    statusLabel = if (status.vpnActive) copy.shareVpnOn else copy.shareVpnOff,
                    statusTone = if (status.vpnActive) CommandHealthTone.HEALTHY else CommandHealthTone.ATTENTION,
                    segments = commandStatusSegments(
                        listOf(status.clients, if (status.running) 1 else 0, status.rejected.toInt())
                    ),
                    badgeIcon = Icons.Rounded.Cast,
                    actionLabel = if (status.running) copy.shareStop else copy.shareStart,
                    onAction = {
                        if (status.running) {
                            Prefs.setShareRequested(context, false)
                            ShareService.stop(context)
                            ShareRuntime.publish { it.copy(running = false) }
                        } else {
                            Prefs.setShareConfig(context, draft)
                            saved = draft
                            Prefs.setShareRequested(context, true)
                            ShareService.start(context)
                        }
                    }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            status.proxyAddress?.let { "${copy.shareAddress}: $it" } ?: copy.shareAddressPending,
                            color = CommandHeroInk.strong,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Telemetry),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (hotspot != null) copy.shareHotspotOn else copy.shareHotspotOff,
                            color = if (hotspot != null) CommandHeroInk.body else CommandHeroInk.muted,
                            style = MaterialTheme.typography.labelSmall
                        )
                        status.vpnLabel?.let {
                            Text(
                                "${copy.shareVpnApp}: $it",
                                color = CommandHeroInk.muted,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            item {
                CommandStatStrip(
                    cells = listOf(
                        CommandStatCell(copy.shareClientsShort, status.clients.toString(), null,
                            if (status.clients > 0) CommandHealthTone.HEALTHY else CommandHealthTone.INFO),
                        CommandStatCell(copy.shareUpload, shareAmount(status.uploadBytes), null, CommandHealthTone.INFO),
                        CommandStatCell(copy.shareDownload, shareAmount(status.downloadBytes), null, CommandHealthTone.INFO)
                    )
                )
            }

            if (vpnMissing) {
                item {
                    CommandStateBlock(
                        copy.shareVpnMissing,
                        copy.shareVpnMissingBody,
                        CommandHealthTone.ATTENTION
                    )
                }
            }

            status.eventKey?.let { key ->
                val message = shareEventMessage(key, copy)
                if (message != null) item {
                    CommandNoticeRow(
                        title = message,
                        body = copy.shareEventsHint,
                        tone = if (key == "auth-failed" || key == "rejected") CommandHealthTone.ATTENTION else CommandHealthTone.INFO,
                        onClick = { ShareRuntime.publish { it.copy(eventKey = null) } }
                    )
                }
            }

            item {
                CommandSectionTitle(copy.shareSettings, copy.shareSettingsHint)
                CommandSurface(raised = true) {
                    Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                        OutlinedTextField(
                            portText,
                            { text ->
                                portText = text.filter { it.isDigit() }.take(5)
                                draft = draft.copy(port = portText.toIntOrNull() ?: 0)
                            },
                            Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(copy.sharePort) },
                            supportingText = { Text(copy.sharePortHint) },
                            isError = fieldError == copy.shareErrorPort,
                            shape = RoundedCornerShape(CommandRadii.field)
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                            CommandSecondaryButton(copy.shareSocks, { draft = draft.copy(socks = !draft.socks) },
                                icon = if (draft.socks) Icons.Rounded.PlayArrow else null)
                            CommandSecondaryButton(copy.shareHttp, { draft = draft.copy(http = !draft.http) },
                                icon = if (draft.http) Icons.Rounded.PlayArrow else null)
                        }
                        ShareToggle(copy.shareAuth, copy.shareAuthHint, draft.requireAuth) {
                            draft = draft.copy(requireAuth = it)
                        }
                        if (draft.requireAuth) {
                            OutlinedTextField(
                                draft.username, { draft = draft.copy(username = it) }, Modifier.fillMaxWidth(),
                                singleLine = true, label = { Text(copy.shareUsername) },
                                shape = RoundedCornerShape(CommandRadii.field)
                            )
                            OutlinedTextField(
                                draft.password, { draft = draft.copy(password = it) }, Modifier.fillMaxWidth(),
                                singleLine = true, label = { Text(copy.sharePassword) },
                                supportingText = { Text(copy.sharePasswordHint) },
                                isError = fieldError == copy.shareErrorPassword,
                                shape = RoundedCornerShape(CommandRadii.field)
                            )
                        }
                        ShareToggle(copy.shareLanOnly, copy.shareLanOnlyHint, draft.lanOnly) {
                            draft = draft.copy(lanOnly = it)
                        }
                        ShareToggle(copy.shareRequireVpn, copy.shareRequireVpnHint, draft.requireVpn) {
                            draft = draft.copy(requireVpn = it)
                        }
                        if (fieldError != null) Text(fieldError, color = CommandColors.danger, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                            CommandPrimaryButton(copy.shareSave, {
                                Prefs.setShareConfig(context, draft)
                                saved = draft
                                if (status.running) {
                                    ShareService.stop(context)
                                    ShareService.start(context)
                                }
                            }, enabled = dirty && fieldError == null)
                            CommandTextButton(copy.shareReset, {
                                draft = saved
                                portText = saved.port.toString()
                            })
                        }
                    }
                }
            }

            item {
                CommandSectionTitle(copy.shareUse, copy.shareUseHint)
                CommandSurface {
                    Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.xs)) {
                        val address = status.proxyAddress ?: copy.shareAddressPending
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                address,
                                Modifier.weight(1f),
                                color = CommandColors.textPrimary,
                                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Telemetry),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            CommandIconButton(Icons.Rounded.ContentCopy, copy.shareCopyAddress, {
                                clipboard.setText(AnnotatedString(address))
                                copied = true
                            })
                        }
                        if (copied) Text(copy.shareAddressCopied, color = CommandColors.success, style = MaterialTheme.typography.bodySmall)
                        listOf(copy.shareStep1, copy.shareStep2, copy.shareStep3, copy.shareStep4).forEachIndexed { index, step ->
                            Text("${index + 1}. $step", color = CommandColors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            item {
                CommandStateBlock(
                    copy.shareTvTitle,
                    copy.shareTvBody,
                    CommandHealthTone.UNKNOWN
                )
            }

            if (status.peers.isNotEmpty()) {
                item {
                    CommandSectionTitle(copy.shareClients, copy.shareClientsHint)
                    CommandSurface {
                        Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.xs)) {
                            status.peers.entries.forEach { (peer, bytes) ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        peer, Modifier.weight(1f), color = CommandColors.textPrimary,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Telemetry),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                    val total = status.uploadBytes + status.downloadBytes
                                    Text(
                                        "${shareBytes(bytes)} · ${sharePercentLabel(bytes, total)}",
                                        color = CommandColors.textSecondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    Box(Modifier.fillMaxWidth().heightIn(min = 8.dp)) { }
                    Text(copy.shareNoClients, color = CommandColors.textTertiary, style = MaterialTheme.typography.bodySmall)
                }
            }

            item { Spacer(Modifier.height(CommandSpacing.xl)) }
        }
    }
}

@Composable
private fun ShareToggle(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = CommandColors.textPrimary, style = MaterialTheme.typography.bodyLarge)
            Text(hint, color = CommandColors.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** ترجمهٔ رویداد موتور به پیام کاربر؛ null یعنی رویداد نمایشی ندارد. */
internal fun shareEventMessage(key: String, copy: CommandCopy): String? = when (key) {
    "started" -> copy.shareEventStarted
    "stopped" -> copy.shareEventStopped
    "auth-failed" -> copy.shareEventAuthFailed
    "rejected" -> copy.shareEventRejected
    "no-vpn" -> copy.shareEventNoVpn
    "dial-failed" -> copy.shareEventDialFailed
    "bind-failed" -> copy.shareErrorStart
    "unsupported-command" -> copy.shareEventUnsupported
    else -> null
}

/** صفر را «—» نشان می‌دهد؛ «B 0» در چیدمان راست‌به‌چپ گیج‌کننده است. */
internal fun shareAmount(bytes: Long): String = if (bytes <= 0L) "\u2014" else Fmt.bytes(bytes)

/** درصد مصرف‌شدهٔ یک سهم برای نمایش؛ فقط برای تست‌پذیری خالص. */
internal fun sharePercentLabel(part: Long, total: Long): String {
    if (total <= 0L) return "0%"
    return "${((part.toDouble() / total.toDouble()) * 100).roundToInt()}%"
}
