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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class SystemdServiceItem(
    val unitName: String,
    val description: String,
    var status: String = "active (running)",
    var isRunning: Boolean = true
)

@Composable
fun SystemdScreen(t: Str) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val servers = remember { Prefs.loadServers(ctx) }
    var selectedServerIndex by remember { mutableStateOf(0) }
    var sshPassword by remember { mutableStateOf("") }
    var logOutput by remember { mutableStateOf("") }
    var isActionInProgress by remember { mutableStateOf(false) }

    // ── SSH host-key trust (TOFU): prompts are serialized so concurrent
    // connections can never show two dialogs at once.
    var hostKeyPrompt by remember { mutableStateOf<HostKeyPrompt?>(null) }
    val hostKeyChannel = remember { Channel<Boolean>(Channel.RENDEZVOUS) }
    val hostKeyGate = remember { Mutex() }
    val sshPolicy = remember {
        ConfirmingHostKeyPolicy(HostKeyTrustStore) { prompt ->
            hostKeyGate.withLock {
                withContext(Dispatchers.Main) { hostKeyPrompt = prompt }
                hostKeyChannel.receive()
            }
        }
    }

    // Real systemd data, fetched over SSH (item 5 / C5).
    val services = remember { mutableStateListOf<SystemdServiceItem>() }
    var servicesLoading by remember { mutableStateOf(false) }
    var servicesError by remember { mutableStateOf("") }
    var servicesFetched by remember { mutableStateOf(false) }

    fun refreshServices() {
        if (sshPassword.isBlank()) {
            Toast.makeText(ctx, "لطفاً رمز عبور SSH را وارد کنید", Toast.LENGTH_SHORT).show()
            return
        }
        val server = servers.getOrNull(selectedServerIndex) ?: return
        servicesLoading = true
        servicesError = ""
        scope.launch {
            val res = SshEngine.execute(
                server.host, 22, "root", sshPassword,
                "systemctl list-units --type=service --state=running,failed --no-pager --no-legend --output=plain 2>/dev/null",
                20, hostKeyPolicy = sshPolicy
            )
            if (!res.isSuccess) {
                servicesLoading = false
                servicesFetched = true
                services.clear()
                servicesError = "دریافت لیست سرویس‌ها ناموفق بود: ${(res.stderr.ifBlank { res.errorMessage ?: "خطای SSH" }).take(120)}"
                return@launch
            }
            val parsed = OutputParsers.systemdUnits(res.stdout).map { u ->
                    SystemdServiceItem(u.unit, u.description, "${u.active} (${u.sub})", u.active == "active")
                }
            services.clear()
            services.addAll(parsed)
            servicesFetched = true
            servicesLoading = false
        }
    }

    fun controlService(unit: String, action: String) {
        if (servers.isEmpty()) return
        if (sshPassword.isBlank()) {
            Toast.makeText(ctx, "لطفاً رمز عبور SSH را وارد کنید", Toast.LENGTH_SHORT).show()
            return
        }

        val server = servers.getOrNull(selectedServerIndex) ?: return
        // Unit names come from parsed server output; still quoted (C6 defense).
        val qunit = SecurityValidation.shellQuote(unit)
        val cmd = if (action == "logs") {
            "journalctl -u $qunit -n 30 --no-pager"
        } else {
            "systemctl $action $qunit && systemctl is-active $qunit"
        }

        isActionInProgress = true
        scope.launch {
            val res = SshEngine.execute(server.host, 22, "root", sshPassword, cmd, 15, hostKeyPolicy = sshPolicy)
            isActionInProgress = false
            if (action == "logs") {
                logOutput = res.stdout.ifBlank { res.stderr.ifBlank { "No logs found for $unit" } }
            } else if (res.isSuccess) {
                val state = res.stdout.lineSequence().lastOrNull { it.isNotBlank() }?.trim() ?: "unknown"
                Toast.makeText(ctx, "عملیات $action روی $unit انجام شد ($state)", Toast.LENGTH_SHORT).show()
                logOutput = res.stdout
                refreshServices()
            } else {
                Toast.makeText(ctx, "عملیات $action ناموفق بود: ${res.stderr.ifBlank { res.errorMessage ?: "کد ${res.exitCode}" }.take(100)}", Toast.LENGTH_LONG).show()
            }
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
                        icon = Icons.Rounded.Refresh,
                        tint = Ds.accent,
                        background = Ds.accentDim,
                        size = 40.dp,
                        iconSize = 22.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            t.systemdManager,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ds.textPrimary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "مشاهده، راه‌اندازی مجدد و بررسی لاگ‌های زنده تمام سرویس‌های Systemd",
                            fontSize = 11.sp,
                            color = Ds.textSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // Server Picker & Auth
        item {
            ModernCard(padding = 16.dp, cornerRadius = 20.dp) {
                if (servers.isEmpty()) {
                    Text("هیچ سروری ثبت نشده است.", fontSize = 12.sp, color = Ds.warn)
                } else {
                    Text("انتخاب سرور مقصد:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ds.textSecondary)
                    Spacer(Modifier.height(8.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(servers.indices.toList()) { idx ->
                            val s = servers[idx]
                            val isSelected = selectedServerIndex == idx
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) Ds.accent.copy(alpha = 0.18f) else Ds.surfaceLow)
                                    .border(
                                        BorderStroke(1.dp, if (isSelected) Ds.accent else Ds.hairline),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { selectedServerIndex = idx }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    s.name,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Ds.accent else Ds.textPrimary
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    InputField(
                        value = sshPassword,
                        onValueChange = { sshPassword = it },
                        label = "رمز عبور SSH:",
                        placeholder = "Root Password",
                        isPassword = true
                    )
                }
            }
        }

        // Services List (real data over SSH)
        item {
            ModernCard(padding = 14.dp, cornerRadius = 18.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("سرویس‌های در حال اجرا (Systemd):", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Ds.textSecondary, modifier = Modifier.weight(1f))
                    SoftButton(
                        text = if (servicesLoading) "در حال بارگذاری..." else "🔄 بازخوانی",
                        onClick = { refreshServices() },
                        enabled = !servicesLoading
                    )
                }
                Spacer(Modifier.height(10.dp))
                when {
                    servicesLoading -> Text("در حال دریافت لیست واقعی سرویس‌ها...", fontSize = 11.5.sp, color = Ds.textSecondary)
                    servicesError.isNotEmpty() -> Text(servicesError, fontSize = 11.5.sp, color = Ds.warn)
                    !servicesFetched -> Text("رمز SSH را وارد کرده و «بازخوانی» را بزنید تا سرویس‌های واقعی سرور بارگذاری شوند.", fontSize = 11.5.sp, color = Ds.textSecondary)
                    services.isEmpty() -> Text("هیچ سرویس فعالی یافت نشد.", fontSize = 11.5.sp, color = Ds.ok)
                }
            }
        }

        items(services) { item ->
            ModernCard(padding = 14.dp, cornerRadius = 18.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            if (item.isRunning) Icons.Rounded.CheckCircle else Icons.Rounded.Close,
                            contentDescription = null,
                            tint = if (item.isRunning) Ds.ok else Ds.danger,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(item.unitName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                            if (item.description.isNotEmpty()) {
                                Text(item.description, fontSize = 10.5.sp, color = Ds.textTertiary)
                            }
                            Text(item.status, fontSize = 9.5.sp, color = if (item.isRunning) Ds.ok else Ds.danger)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SoftButton(
                            text = "▶ Start",
                            onClick = { controlService(item.unitName, "start") }
                        )
                        SoftButton(
                            text = "⏹ Stop",
                            onClick = { controlService(item.unitName, "stop") }
                        )
                        SoftButton(
                            text = "🔄 Restart",
                            onClick = { controlService(item.unitName, "restart") }
                        )
                        SoftButton(
                            text = "📜 Logs",
                            onClick = { controlService(item.unitName, "logs") }
                        )
                    }
                }
            }
        }

        // Log Output Box
        if (logOutput.isNotEmpty()) {
            item {
                ModernCard(padding = 14.dp, cornerRadius = 18.dp) {
                    Text("گزارش و لاگ سرویس (Journalctl):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ds.textSecondary)
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Ds.surfaceLow)
                            .padding(10.dp)
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            item {
                                Text(logOutput, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Ds.accent, lineHeight = 15.sp)
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(90.dp)) }
    }

    hostKeyPrompt?.let { prompt ->
        HostKeyTrustDialog(
            prompt = prompt,
            onDecision = { approved ->
                hostKeyPrompt = null
                hostKeyChannel.trySend(approved)
            }
        )
    }
}
