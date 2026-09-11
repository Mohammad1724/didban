@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.didban.monitor

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun BatchExecScreen(t: Str) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val servers = remember { Prefs.loadServers(ctx) }
    val selectedServerIds = remember { mutableStateListOf<Long>() }

    var sshPortStr by remember { mutableStateOf("22") }
    var sshPassword by remember { mutableStateOf("") }
    var batchCommand by remember { mutableStateOf("apt update -y") }
    var isExecuting by remember { mutableStateOf(false) }
    var batchResults by remember { mutableStateOf<List<BatchServerResult>>(emptyList()) }
    val expandedCards = remember { mutableStateListOf<Long>() }

    val batchPresets = remember {
        listOf(
            "apt update && apt upgrade -y",
            "docker ps -a",
            "systemctl restart didban-agent",
            "free -m && df -h",
            "reboot"
        )
    }

    fun runBatch() {
        if (selectedServerIds.isEmpty()) {
            Toast.makeText(ctx, "حداقل یک سرور را انتخاب کنید", Toast.LENGTH_SHORT).show()
            return
        }
        if (sshPassword.isBlank()) {
            Toast.makeText(ctx, "لطفاً رمز عبور SSH مشترک را وارد کنید", Toast.LENGTH_SHORT).show()
            return
        }
        if (batchCommand.isBlank()) return

        val selected = servers.filter { it.id in selectedServerIds }
        val port = sshPortStr.toIntOrNull() ?: 22

        isExecuting = true
        scope.launch {
            val targets = selected.map { Triple(it, port, sshPassword) }
            val results = SshEngine.executeBatch(targets, batchCommand, timeoutSec = 35)
            batchResults = results
            isExecuting = false
            Toast.makeText(ctx, "اجرای همزمان روی ${results.size} سرور به پایان رسید", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        // Hero Bento
        item {
            ModernCard(padding = 16.dp, cornerRadius = 22.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(
                        icon = Icons.Rounded.Bolt,
                        tint = Ds.accent,
                        background = Ds.accentDim,
                        size = 40.dp,
                        iconSize = 22.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            t.batchExec,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ds.textPrimary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "اجرای همزمان و مقایسه‌ای دستورات روی کل ناوگان سرورها",
                            fontSize = 11.sp,
                            color = Ds.textSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BentoMicroPod(
                        title = "سرورهای منتخب",
                        value = "${selectedServerIds.size} / ${servers.size}",
                        unit = "Nodes",
                        color = Ds.accent,
                        modifier = Modifier.weight(1f)
                    )
                    BentoMicroPod(
                        title = "وضعیت اجرا",
                        value = if (isExecuting) "Running" else if (batchResults.isNotEmpty()) "Done" else "Idle",
                        unit = if (isExecuting) "⏳" else "⚡",
                        color = if (isExecuting) Ds.warn else Ds.ok,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Target Server Selector Multi-Check
        item {
            ModernCard(padding = 16.dp, cornerRadius = 20.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "انتخاب سرورهای هدف:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ds.textPrimary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SoftButton(
                            text = "انتخاب همه",
                            onClick = {
                                selectedServerIds.clear()
                                selectedServerIds.addAll(servers.map { it.id })
                            }
                        )
                        SoftButton(
                            text = "لغو همه",
                            onClick = { selectedServerIds.clear() }
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    servers.forEach { s ->
                        val isChecked = s.id in selectedServerIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isChecked) Ds.accent.copy(alpha = 0.08f) else Ds.surfaceLow)
                                .border(
                                    BorderStroke(1.dp, if (isChecked) Ds.accent.copy(alpha = 0.35f) else Ds.hairline),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    if (isChecked) selectedServerIds.remove(s.id) else selectedServerIds.add(s.id)
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(s.name, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                                Text("${s.host}:${s.port}", fontSize = 10.5.sp, color = Ds.textTertiary)
                            }

                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = {
                                    if (it) selectedServerIds.add(s.id) else selectedServerIds.remove(s.id)
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Ds.accent,
                                    uncheckedColor = Ds.hairline
                                )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                InputField(
                    value = sshPassword,
                    onValueChange = { sshPassword = it },
                    label = "رمز عبور SSH سرورها:",
                    placeholder = "Root Password",
                    isPassword = true
                )
            }
        }

        // Batch Command Presets & Input
        item {
            ModernCard(padding = 16.dp, cornerRadius = 20.dp) {
                Text(
                    "دستورات آماده برای ناوگان:",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ds.textSecondary
                )

                Spacer(Modifier.height(8.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(batchPresets) { p ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Ds.surfaceLow)
                                .border(BorderStroke(1.dp, Ds.hairline), RoundedCornerShape(10.dp))
                                .clickable { batchCommand = p }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(p, fontSize = 11.5.sp, color = Ds.accent)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                InputField(
                    value = batchCommand,
                    onValueChange = { batchCommand = it },
                    label = "دستور جهت ارسال همزمان:",
                    placeholder = "مثلاً reboot یا apt update"
                )

                Spacer(Modifier.height(14.dp))

                PrimaryButton(
                    text = if (isExecuting) "⚡ در حال اجرای همزمان روی ${selectedServerIds.size} سرور..." else "⚡ اجرای همزمان روی تمام سرورها",
                    onClick = { runBatch() },
                    enabled = !isExecuting && selectedServerIds.isNotEmpty() && batchCommand.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Results Matrix
        if (batchResults.isNotEmpty()) {
            item {
                Text(
                    "نتایج تفکیک‌شده ناوگان (${batchResults.size} سرور):",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ds.textPrimary
                )
            }

            items(batchResults) { res ->
                val isExpanded = res.serverId in expandedCards
                val isOk = res.result.isSuccess
                ModernCard(
                    padding = 14.dp,
                    cornerRadius = 18.dp,
                    border = BorderStroke(1.dp, if (isOk) Ds.ok.copy(alpha = 0.35f) else Ds.danger.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isExpanded) expandedCards.remove(res.serverId) else expandedCards.add(res.serverId)
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isOk) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = if (isOk) Ds.ok else Ds.danger,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(res.serverName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                                Text("${res.host} · Exit: ${res.result.exitCode} (${res.result.durationMs}ms)", fontSize = 10.5.sp, color = Ds.textTertiary)
                            }
                        }

                        Icon(
                            if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = null,
                            tint = Ds.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(Modifier.padding(top = 10.dp)) {
                            Hairline()
                            Spacer(Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Ds.surfaceLow)
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = if (res.result.stdout.isNotBlank()) res.result.stdout else res.result.stderr.ifBlank { "No output returned." },
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = if (isOk) Ds.textPrimary else Ds.danger,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(90.dp)) }
    }
}
