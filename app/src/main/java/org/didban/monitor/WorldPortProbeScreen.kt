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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Public
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun WorldPortProbeScreen(t: Str) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val servers = remember { Prefs.loadServers(ctx) }
    var hostInput by remember { mutableStateOf("") }
    var portInput by remember { mutableStateOf("443") }
    var isProbing by remember { mutableStateOf(false) }
    var probeNodes by remember { mutableStateOf<List<CheckHostNode>>(emptyList()) }
    var probeRequestId by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val commonPortPresets = remember {
        listOf(
            "HTTPS (443)" to "443",
            "HTTP (80)" to "80",
            "SSH (22)" to "22",
            "X-UI (2053)" to "2053",
            "Gost (8443)" to "8443",
            "DNS (53)" to "53",
            "MySQL (3306)" to "3306"
        )
    }

    fun startWorldProbe() {
        val cleanHost = hostInput.trim().removePrefix("https://").removePrefix("http://").substringBefore("/")
        val port = portInput.trim().toIntOrNull() ?: 80

        if (cleanHost.isBlank()) {
            Toast.makeText(ctx, "لطفاً آدرس آی‌پی یا دامنه را وارد کنید", Toast.LENGTH_SHORT).show()
            return
        }

        isProbing = true
        errorMsg = null
        probeNodes = emptyList()

        scope.launch {
            try {
                val targetWithPort = "$cleanHost:$port"
                val (reqId, nodes) = CheckHostService.startCheck(target = targetWithPort, type = "tcp", maxNodes = 20)
                probeRequestId = reqId
                probeNodes = nodes

                // Poll for 15 seconds max
                for (poll in 1..8) {
                    delay(2000)
                    val allDone = CheckHostService.pollResults(reqId, "tcp", probeNodes)
                    probeNodes = probeNodes.toList() // trigger recomposition
                    if (allDone) break
                }
            } catch (e: Exception) {
                errorMsg = e.message
            } finally {
                isProbing = false
            }
        }
    }

    val okCount = probeNodes.count { it.state == 1 }
    val failCount = probeNodes.count { it.state == 2 }
    val totalDetermined = okCount + failCount

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
                        icon = Icons.Rounded.Public,
                        tint = Ds.accent,
                        background = Ds.accentDim,
                        size = 40.dp,
                        iconSize = 22.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "اسکنر پورت‌های ورودی از دید جهان",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ds.textPrimary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "سنجش دسترسی‌پذیری پورت‌های سرور از ۲۰ نقطه جغرافیایی دنیا جهت کشف مسدودیت فایروال",
                            fontSize = 11.sp,
                            color = Ds.textSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // Target Config Pod
        item {
            ModernCard(padding = 16.dp, cornerRadius = 20.dp) {
                // Saved Servers Fast Select
                if (servers.isNotEmpty()) {
                    Text("انتخاب سریع از سرورهای ثبت‌شده:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ds.textSecondary)
                    Spacer(Modifier.height(6.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(servers) { s ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (hostInput == s.host) Ds.accent.copy(alpha = 0.18f) else Ds.surfaceLow)
                                    .border(BorderStroke(1.dp, if (hostInput == s.host) Ds.accent else Ds.hairline), RoundedCornerShape(8.dp))
                                    .clickable {
                                        hostInput = s.host
                                        portInput = "${s.port}"
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(s.name, fontSize = 11.5.sp, color = if (hostInput == s.host) Ds.accent else Ds.textPrimary)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InputField(
                        value = hostInput,
                        onValueChange = { hostInput = it },
                        label = "آی‌پی یا دامنه مقصد:",
                        placeholder = "e.g. 1.1.1.1 or server.com",
                        modifier = Modifier.weight(1.3f)
                    )

                    InputField(
                        value = portInput,
                        onValueChange = { portInput = it },
                        label = "پورت ورودی:",
                        placeholder = "443",
                        modifier = Modifier.weight(0.7f)
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Port Presets
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(commonPortPresets) { (label, p) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (portInput == p) Ds.accentDim else Ds.surfaceLow)
                                .border(BorderStroke(1.dp, if (portInput == p) Ds.accent else Ds.hairline), RoundedCornerShape(8.dp))
                                .clickable { portInput = p }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(label, fontSize = 11.sp, color = if (portInput == p) Ds.accent else Ds.textSecondary)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                PrimaryButton(
                    text = if (isProbing) "🌐 در حال اسکن پورت از سراسر دنیا..." else "🚀 شروع اسکن جهانی پورت ورودی",
                    onClick = { startWorldProbe() },
                    enabled = !isProbing && hostInput.isNotBlank() && portInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Summary Stats Pod
        if (probeNodes.isNotEmpty()) {
            item {
                ModernCard(padding = 16.dp, cornerRadius = 20.dp) {
                    Text(
                        "خلاصه وضعیت دسترسی‌پذیری جهانی پورت $portInput:",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ds.textPrimary
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BentoMicroPod(
                            title = "پورت‌های باز (Open)",
                            value = "$okCount",
                            unit = "Nodes",
                            color = Ds.ok,
                            modifier = Modifier.weight(1f)
                        )
                        BentoMicroPod(
                            title = "مسدود / بسته (Blocked)",
                            value = "$failCount",
                            unit = "Nodes",
                            color = if (failCount > 0) Ds.danger else Ds.textSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        BentoMicroPod(
                            title = "دسترسی جهانی",
                            value = if (totalDetermined > 0) "${(okCount * 100) / totalDetermined}%" else "...",
                            unit = "",
                            color = Ds.accent,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // World Nodes Results List
            items(probeNodes) { node ->
                val isOk = node.state == 1
                val isFail = node.state == 2
                val color = if (isOk) Ds.ok else if (isFail) Ds.danger else Ds.textSecondary

                ModernCard(padding = 12.dp, cornerRadius = 14.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Text(node.flag, fontSize = 22.sp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    node.location,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Ds.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    node.nodeKey,
                                    fontSize = 10.sp,
                                    fontFamily = Telemetry,
                                    color = Ds.textTertiary
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isOk) Ds.okDim else if (isFail) Ds.dangerDim else Ds.surfaceLow)
                                .border(BorderStroke(1.dp, color.copy(alpha = 0.35f)), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = if (isOk) "OPEN 🟢" else if (isFail) "BLOCKED 🔴" else "PROBING ⏳",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = color
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(90.dp)) }
    }
}
