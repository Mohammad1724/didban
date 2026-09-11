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
import kotlinx.coroutines.launch

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

    val services = remember {
        mutableStateListOf(
            SystemdServiceItem("didban-agent.service", "Didban Monitoring Daemon", "active (running)", true),
            SystemdServiceItem("x-ui.service", "X-UI Proxy Panel", "active (running)", true),
            SystemdServiceItem("gost.service", "GOST Multi-Protocol Tunnel", "active (running)", true),
            SystemdServiceItem("nginx.service", "Nginx HTTP & Reverse Proxy", "active (running)", true),
            SystemdServiceItem("docker.service", "Docker Application Container Engine", "active (running)", true),
            SystemdServiceItem("ssh.service", "OpenBSD Secure Shell Server", "active (running)", true),
            SystemdServiceItem("cron.service", "Regular Background Program Daemon", "active (running)", true)
        )
    }

    fun controlService(unit: String, action: String) {
        if (servers.isEmpty()) return
        if (sshPassword.isBlank()) {
            Toast.makeText(ctx, "لطفاً رمز عبور SSH را وارد کنید", Toast.LENGTH_SHORT).show()
            return
        }

        val server = servers.getOrNull(selectedServerIndex) ?: return
        val cmd = if (action == "logs") {
            "journalctl -u $unit -n 30 --no-pager"
        } else {
            "systemctl $action $unit && systemctl is-active $unit"
        }

        isActionInProgress = true
        scope.launch {
            val res = SshEngine.execute(server.host, 22, "root", sshPassword, cmd, 15)
            isActionInProgress = false
            if (action == "logs") {
                logOutput = res.stdout.ifBlank { res.stderr.ifBlank { "No logs found for $unit" } }
            } else {
                Toast.makeText(ctx, "عملیات $action روی $unit انجام شد", Toast.LENGTH_SHORT).show()
                logOutput = res.stdout
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

        // Services List
        items(services) { item ->
            ModernCard(padding = 14.dp, cornerRadius = 18.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Ds.ok, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(item.unitName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                            Text(item.description, fontSize = 10.5.sp, color = Ds.textTertiary)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
}
