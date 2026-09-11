@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.didban.monitor

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

data class SshSnippet(
    val title: String,
    val command: String,
    val category: String
)

@Composable
fun SshTerminalScreen(t: Str, initialServerId: Long? = null) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val servers = remember { Prefs.loadServers(ctx) }
    var selectedServerIndex by remember {
        mutableStateOf(
            if (initialServerId != null) {
                servers.indexOfFirst { it.id == initialServerId }.coerceAtLeast(0)
            } else 0
        )
    }

    var sshUser by remember { mutableStateOf("root") }
    var sshPortStr by remember { mutableStateOf("22") }
    var sshPassword by remember { mutableStateOf("") }
    var customCommand by remember { mutableStateOf("") }
    var terminalOutput by remember { mutableStateOf("Didban Secure SSH Terminal Ready.\nSelect a node, enter password, and run any command or preset.\n") }
    var isExecuting by remember { mutableStateOf(false) }

    val snippets = remember {
        listOf(
            SshSnippet("📦 کانتینرها", "docker ps -a", "Docker"),
            SshSnippet("📊 پردازش‌ها", "top -b -n 1 | head -n 25", "Process"),
            SshSnippet("🌐 پورت‌ها", "ss -tulpn", "Network"),
            SshSnippet("💾 دیسک و رم", "df -h && free -m", "System"),
            SshSnippet("🛡️ فایروال", "nft list ruleset 2>/dev/null || iptables -L -n -v", "Security"),
            SshSnippet("🔄 ایجنت دیدبان", "systemctl status didban-agent --no-pager", "Service"),
            SshSnippet("📜 لاگ X-UI", "journalctl -u x-ui -n 35 --no-pager 2>/dev/null || echo 'No x-ui unit'", "Service"),
            SshSnippet("📜 لاگ Gost", "journalctl -u gost -n 35 --no-pager 2>/dev/null || echo 'No gost unit'", "Service"),
            SshSnippet("⚡ آپ‌تایم و کِرنل", "uptime && uname -a", "System")
        )
    }

    val quickKeys = remember {
        listOf("Tab", "Ctrl+C", "|", "grep", "sudo", "systemctl", "docker", "tail", "~", "/", "-", "_", "&&", "clear")
    }

    fun executeCmd(cmdToRun: String) {
        if (servers.isEmpty()) {
            Toast.makeText(ctx, "ابتدا یک سرور در دیدبان ثبت کنید", Toast.LENGTH_SHORT).show()
            return
        }
        if (sshPassword.isBlank()) {
            Toast.makeText(ctx, "لطفاً رمز عبور SSH را وارد کنید", Toast.LENGTH_SHORT).show()
            return
        }
        if (cmdToRun.isBlank()) return

        val server = servers.getOrNull(selectedServerIndex) ?: return
        val port = sshPortStr.toIntOrNull() ?: 22

        isExecuting = true
        terminalOutput += "\n[didban@${server.name} ~]# $cmdToRun\n⏳ در حال اجرا...\n"

        scope.launch {
            val res = SshEngine.execute(
                host = server.host,
                sshPort = port,
                user = sshUser.ifBlank { "root" },
                password = sshPassword,
                command = cmdToRun,
                timeoutSec = 25
            )
            isExecuting = false
            val text = buildString {
                if (res.stdout.isNotBlank()) append(res.stdout).append("\n")
                if (res.stderr.isNotBlank()) append("⚠️ Error: ").append(res.stderr).append("\n")
                append("── [Exit: ${res.exitCode} · ${res.durationMs}ms] ──\n")
            }
            terminalOutput += text
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
                        icon = Icons.Rounded.Terminal,
                        tint = Ds.accent,
                        background = Ds.accentDim,
                        size = 40.dp,
                        iconSize = 22.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            t.sshTerminal,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ds.textPrimary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "اجرای مستقیم و امن دستورات لینوکس روی سرور بدون نیاز به نرم‌افزار جانبی",
                            fontSize = 11.sp,
                            color = Ds.textSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // Server & Credentials Config Card
        item {
            ModernCard(padding = 16.dp, cornerRadius = 20.dp) {
                if (servers.isEmpty()) {
                    Text("هیچ سروری در دیدبان یافت نشد.", fontSize = 12.sp, color = Ds.warn)
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

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        InputField(
                            value = sshUser,
                            onValueChange = { sshUser = it },
                            label = "User",
                            placeholder = "root",
                            modifier = Modifier.weight(1f)
                        )
                        InputField(
                            value = sshPortStr,
                            onValueChange = { sshPortStr = it },
                            label = "SSH Port",
                            placeholder = "22",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    InputField(
                        value = sshPassword,
                        onValueChange = { sshPassword = it },
                        label = "رمز عبور SSH:",
                        placeholder = "Root / Sudo Password",
                        isPassword = true
                    )
                }
            }
        }

        // DevOps Snippets Library
        item {
            ModernCard(padding = 14.dp, cornerRadius = 18.dp) {
                Text(
                    t.devopsSnippets,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ds.textPrimary
                )
                Spacer(Modifier.height(8.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(snippets) { snip ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Ds.surfaceLow)
                                .border(BorderStroke(1.dp, Ds.hairline), RoundedCornerShape(10.dp))
                                .clickable(enabled = !isExecuting) {
                                    customCommand = snip.command
                                    executeCmd(snip.command)
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                snip.title,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = Ds.accent
                            )
                        }
                    }
                }
            }
        }

        // Quick Keys Bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quickKeys.forEach { key ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Ds.surfaceElevated)
                            .border(BorderStroke(1.dp, Ds.hairline), RoundedCornerShape(8.dp))
                            .clickable {
                                if (key == "clear") {
                                    terminalOutput = ""
                                } else if (key == "Tab") {
                                    customCommand += "  "
                                } else if (key == "Ctrl+C") {
                                    terminalOutput += "\n^C\n"
                                } else {
                                    customCommand += if (customCommand.endsWith(" ") || customCommand.isEmpty()) key else " $key"
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(key, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Ds.textPrimary)
                    }
                }
            }
        }

        // Command Input Field & Run Button
        item {
            ModernCard(padding = 12.dp, cornerRadius = 18.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InputField(
                        value = customCommand,
                        onValueChange = { customCommand = it },
                        label = "دستور خط فرمان (Bash Command):",
                        placeholder = "مثلاً apt update -y یا htop",
                        modifier = Modifier.weight(1f)
                    )

                    PrimaryButton(
                        text = if (isExecuting) "..." else "اجرا",
                        onClick = { executeCmd(customCommand) },
                        enabled = !isExecuting && customCommand.isNotBlank(),
                        modifier = Modifier.padding(top = 18.dp)
                    )
                }
            }
        }

        // Live Obsidian Terminal Output Well
        item {
            ModernCard(padding = 14.dp, cornerRadius = 18.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isExecuting) Ds.warn else Ds.ok)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "خروجی کنسول (Standard Output):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ds.textSecondary
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SoftButton(
                            text = t.copy,
                            onClick = {
                                clipboard.setText(AnnotatedString(terminalOutput))
                                Toast.makeText(ctx, t.copied, Toast.LENGTH_SHORT).show()
                            }
                        )
                        SoftButton(
                            text = "پاکسازی",
                            onClick = { terminalOutput = "" }
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Ds.surfaceLow)
                        .border(BorderStroke(1.dp, Ds.hairline), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            Text(
                                text = terminalOutput.ifBlank { "— No output —" },
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp,
                                color = Ds.ok,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(90.dp)) }
    }
}
