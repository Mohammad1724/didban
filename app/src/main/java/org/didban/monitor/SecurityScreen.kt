@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.didban.monitor

import android.widget.Toast
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
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Security
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

data class BannedIpItem(
    val ip: String,
    val jail: String,
    val banTime: String
)

@Composable
fun SecurityScreen(t: Str) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val servers = remember { Prefs.loadServers(ctx) }
    var selectedServerIndex by remember { mutableStateOf(0) }
    var sshPassword by remember { mutableStateOf("") }
    var firewallOutput by remember { mutableStateOf("") }
    var portToAllow by remember { mutableStateOf("") }

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

    // Real fail2ban data, fetched over SSH (item 5 / C5).
    val bannedIps = remember { mutableStateListOf<BannedIpItem>() }
    var bannedLoading by remember { mutableStateOf(false) }
    var bannedError by remember { mutableStateOf("") }
    var bannedFetched by remember { mutableStateOf(false) }

    fun inspectFirewall() {
        if (servers.isEmpty()) return
        if (sshPassword.isBlank()) {
            Toast.makeText(ctx, "رمز عبور SSH را وارد کنید", Toast.LENGTH_SHORT).show()
            return
        }
        val server = servers.getOrNull(selectedServerIndex) ?: return
        scope.launch {
            val res = SshEngine.execute(server.host, 22, "root", sshPassword, "ufw status verbose 2>/dev/null || iptables -L -n -v | head -n 25", 15, hostKeyPolicy = sshPolicy)
            firewallOutput = res.stdout.ifBlank { res.stderr }
        }
    }

    fun allowPort(portInput: String) {
        if (portInput.isBlank() || sshPassword.isBlank()) return
        val port = SecurityValidation.validatePort(portInput)
        if (port == null) {
            Toast.makeText(ctx, "شماره پورت معتبر نیست (1 تا 65535)", Toast.LENGTH_LONG).show()
            return
        }
        val server = servers.getOrNull(selectedServerIndex) ?: return
        scope.launch {
            // Port is strictly validated (digits only); the rule argument is
            // additionally shell-quoted as defense in depth.
            val rule = SecurityValidation.shellQuote("${port}/tcp")
            val res = SshEngine.execute(server.host, 22, "root", sshPassword, "ufw allow $rule && ufw reload", 15, hostKeyPolicy = sshPolicy)
            if (res.isSuccess) {
                Toast.makeText(ctx, "پورت $port با موفقیت باز شد!", Toast.LENGTH_SHORT).show()
            } else {
                val detail = res.stderr.ifBlank { res.errorMessage ?: "کد خروج $res.exitCode" }
                Toast.makeText(ctx, "باز کردن پورت $port ناموفق بود: $detail", Toast.LENGTH_LONG).show()
            }
            inspectFirewall()
        }
    }

    fun unban(item: BannedIpItem) {
        if (sshPassword.isBlank()) return
        if (!SecurityValidation.isValidIpv4(item.ip) || !SecurityValidation.validateJail(item.jail)) {
            Toast.makeText(ctx, "مقدار IP یا Jail نامعتبر است", Toast.LENGTH_LONG).show()
            return
        }
        val server = servers.getOrNull(selectedServerIndex) ?: return
        scope.launch {
            val jail = SecurityValidation.shellQuote(item.jail)
            val ip = SecurityValidation.shellQuote(item.ip)
            val res = SshEngine.execute(server.host, 22, "root", sshPassword, "fail2ban-client set $jail unbanip $ip", 15, hostKeyPolicy = sshPolicy)
            if (res.isSuccess) {
                Toast.makeText(ctx, "آدرس ${item.ip} آن‌بلاک شد", Toast.LENGTH_SHORT).show()
                refreshBannedIps()
            } else {
                val detail = res.stderr.ifBlank { res.errorMessage ?: "کد خروج $res.exitCode" }
                Toast.makeText(ctx, "آن‌بلاک ${item.ip} ناموفق بود: $detail", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun refreshBannedIps() {
        if (sshPassword.isBlank()) {
            Toast.makeText(ctx, "رمز عبور SSH را وارد کنید", Toast.LENGTH_SHORT).show()
            return
        }
        val server = servers.getOrNull(selectedServerIndex) ?: return
        bannedLoading = true
        bannedError = ""
        scope.launch {
            // 1. Jail list.
            val jailsRes = SshEngine.execute(server.host, 22, "root", sshPassword, "fail2ban-client status 2>/dev/null", 15, hostKeyPolicy = sshPolicy)
            if (!jailsRes.isSuccess) {
                bannedLoading = false
                bannedFetched = true
                bannedIps.clear()
                val out = (jailsRes.stdout + jailsRes.stderr).trim()
                bannedError = if (out.contains("not found") || jailsRes.stdout.isBlank()) {
                    "fail2ban روی این سرور نصب نیست (یا راه‌اندازی نشده)."
                } else {
                    "دریافت وضعیت fail2ban ناموفق بود: ${out.take(120)}"
                }
                return@launch
            }
            val jails = OutputParsers.fail2banJails(jailsRes.stdout)

            // 2. Banned IPs per jail (validated + quoted: item 2 / C6).
            val items = mutableListOf<BannedIpItem>()
            for (jail in jails) {
                val stRes = SshEngine.execute(server.host, 22, "root", sshPassword, "fail2ban-client status ${SecurityValidation.shellQuote(jail)} 2>/dev/null", 15, hostKeyPolicy = sshPolicy)
                if (!stRes.isSuccess) continue
                OutputParsers.fail2banBannedIps(stRes.stdout)
                    .forEach { ip -> items.add(BannedIpItem(ip, jail, "")) }
            }
            bannedIps.clear()
            bannedIps.addAll(items)
            bannedFetched = true
            bannedLoading = false
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
                        icon = Icons.Rounded.Security,
                        tint = Ds.accent,
                        background = Ds.accentDim,
                        size = 40.dp,
                        iconSize = 22.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            t.firewallSecurity,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ds.textPrimary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "پایش فایروال UFW/Iptables و مدیریت لیست سیاه Fail2ban",
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
                    Text("هیچ سروری یافت نشد.", fontSize = 12.sp, color = Ds.warn)
                } else {
                    Text("انتخاب سرور:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ds.textSecondary)
                    Spacer(Modifier.height(8.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(servers.indices.toList()) { idx ->
                            val s = servers[idx]
                            val isSelected = selectedServerIndex == idx
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) Ds.accent.copy(alpha = 0.18f) else Ds.surfaceLow)
                                    .border(BorderStroke(1.dp, if (isSelected) Ds.accent else Ds.hairline), RoundedCornerShape(10.dp))
                                    .clickable { selectedServerIndex = idx }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text(s.name, fontSize = 12.sp, color = if (isSelected) Ds.accent else Ds.textPrimary)
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

        // Quick Port Opener
        item {
            ModernCard(padding = 16.dp, cornerRadius = 20.dp) {
                Text("بازگشایی سریع پورت فایروال (UFW Allow):", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InputField(
                        value = portToAllow,
                        onValueChange = { portToAllow = it },
                        label = "شماره پورت",
                        placeholder = "مثلاً 443 یا 8080",
                        modifier = Modifier.weight(1f)
                    )

                    PrimaryButton(
                        text = "🔓 باز کردن",
                        onClick = { allowPort(portToAllow) },
                        enabled = portToAllow.isNotBlank() && sshPassword.isNotBlank(),
                        modifier = Modifier.padding(top = 18.dp)
                    )
                }
            }
        }

        // Fail2ban Blacklist (real data over SSH)
        item {
            ModernCard(padding = 16.dp, cornerRadius = 20.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("IPهای بلاک‌شده توسط Fail2ban:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary, modifier = Modifier.weight(1f))
                    SoftButton(
                        text = if (bannedLoading) "در حال بارگذاری..." else "🔄 بازخوانی",
                        onClick = { refreshBannedIps() },
                        enabled = !bannedLoading
                    )
                }
                Spacer(Modifier.height(10.dp))

                when {
                    bannedLoading -> Text("در حال پرس‌وجوی fail2ban روی سرور...", fontSize = 11.5.sp, color = Ds.textSecondary)
                    bannedError.isNotEmpty() -> Text(bannedError, fontSize = 11.5.sp, color = Ds.warn)
                    !bannedFetched -> Text("رمز SSH را وارد کرده و «بازخوانی» را بزنید تا لیست واقعیِ سرور بارگذاری شود.", fontSize = 11.5.sp, color = Ds.textSecondary)
                    bannedIps.isEmpty() -> Text("در حال حاضر هیچ آدرس IP بلاک‌شده‌ای وجود ندارد.", fontSize = 11.5.sp, color = Ds.ok)
                    else -> {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            bannedIps.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Ds.danger.copy(alpha = 0.08f))
                                        .border(BorderStroke(1.dp, Ds.danger.copy(alpha = 0.25f)), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(item.ip, fontSize = 12.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Ds.danger)
                                        Text("Jail: ${item.jail}", fontSize = 10.5.sp, color = Ds.textTertiary)
                                    }

                                    SoftButton(
                                        text = "آن‌بلاک (Unban)",
                                        onClick = { unban(item) }
                                    )
                                }
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
