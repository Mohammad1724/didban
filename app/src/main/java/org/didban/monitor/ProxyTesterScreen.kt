@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.didban.monitor

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun ProxyTesterScreen(t: Str) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var subTab by remember { mutableStateOf(0) } // 0: Single Config, 1: Subscription

    // Single Config State
    var configInput by remember { mutableStateOf("") }
    var parsedConfig by remember { mutableStateOf<ParsedProxyConfig?>(null) }
    var isTestingConfig by remember { mutableStateOf(false) }

    // Subscription State
    var subUrlInput by remember { mutableStateOf("") }
    var subInfo by remember { mutableStateOf<SubscriptionInfo?>(null) }
    var isFetchingSub by remember { mutableStateOf(false) }
    var isTestingSubNodes by remember { mutableStateOf(false) }

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
                        icon = Icons.Rounded.Speed,
                        tint = Ds.accent,
                        background = Ds.accentDim,
                        size = 40.dp,
                        iconSize = 22.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "تستر کانفیگ و ساب‌سکریپشن",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ds.textPrimary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "تحلیل ساختار کانفیگ‌های VLESS/Trojan، تست پینگ TLS و استعلام حجم ساب‌سکریپشن",
                            fontSize = 11.sp,
                            color = Ds.textSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // Sub-Tab Switcher
        item {
            SegmentedControl(
                items = listOf("کانفیگ تکی (Single)", "لینک ساب‌سکریپشن (Sub)"),
                selectedIndex = subTab,
                onSelect = { subTab = it }
            )
        }

        if (subTab == 0) {
            // ── Single Config Inspector ──
            item {
                ModernCard(padding = 16.dp, cornerRadius = 20.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ورود لینک کانفیگ:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)

                        SoftButton(
                            text = "جای‌گذاری از حافظه",
                            onClick = {
                                val clip = clipboard.getText()?.text ?: ""
                                if (clip.isNotBlank()) {
                                    configInput = clip
                                    parsedConfig = ProxyEngine.parseConfig(clip)
                                }
                            }
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    InputField(
                        value = configInput,
                        onValueChange = {
                            configInput = it
                            parsedConfig = ProxyEngine.parseConfig(it)
                        },
                        label = "vless://, trojan://, vmess:// یا ss://",
                        placeholder = "vless://uuid@server.com:443?..."
                    )

                    Spacer(Modifier.height(12.dp))

                    PrimaryButton(
                        text = if (isTestingConfig) "⚡ در حال سنجش اتصال TLS..." else "⚡ تست سلامت و پینگ کانفیگ",
                        onClick = {
                            val cfg = parsedConfig ?: return@PrimaryButton
                            isTestingConfig = true
                            scope.launch {
                                val (ms, ok) = ProxyEngine.probeConfig(cfg)
                                cfg.pingMs = ms
                                cfg.tlsSuccess = ok
                                cfg.testStatus = if (ok) "ONLINE ($ms ms)" else "FAILED"
                                isTestingConfig = false
                                Toast.makeText(ctx, if (ok) "✅ اتصال موفق! پینگ: $ms ms" else "❌ اتصال ناموفق یا فیلتر شده", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = parsedConfig != null && !isTestingConfig,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Parsed Config Bento Pod
            parsedConfig?.let { cfg ->
                item {
                    ModernCard(padding = 16.dp, cornerRadius = 20.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Ds.accentDim)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(cfg.protocol, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ds.accent)
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    cfg.remark,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Ds.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (cfg.pingMs > 0) {
                                Text("${cfg.pingMs} ms", fontSize = 12.sp, fontFamily = Telemetry, fontWeight = FontWeight.Bold, color = Ds.ok)
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Hairline()
                        Spacer(Modifier.height(10.dp))

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BentoMicroPod(title = "سرور میزبان", value = cfg.host, unit = "", color = Ds.textPrimary, modifier = Modifier.weight(1f))
                            BentoMicroPod(title = "پورت", value = "${cfg.port}", unit = "", color = Ds.accent, modifier = Modifier.weight(0.5f))
                            BentoMicroPod(title = "پروتکل امنیتی", value = cfg.security, unit = "", color = Ds.violet, modifier = Modifier.weight(0.7f))
                        }

                        if (cfg.sni.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            BentoMicroPod(title = "SNI / Hostname", value = cfg.sni, unit = "", color = Ds.textSecondary, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        } else {
            // ── Subscription Inspector ──
            item {
                ModernCard(padding = 16.dp, cornerRadius = 20.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("لینک ساب‌سکریپشن:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)

                        SoftButton(
                            text = "جای‌گذاری از حافظه",
                            onClick = {
                                val clip = clipboard.getText()?.text ?: ""
                                if (clip.isNotBlank()) subUrlInput = clip
                            }
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    InputField(
                        value = subUrlInput,
                        onValueChange = { subUrlInput = it },
                        label = "آدرس ساب‌سکریپشن Marzban / X-UI",
                        placeholder = "https://example.com/sub/token..."
                    )

                    Spacer(Modifier.height(12.dp))

                    PrimaryButton(
                        text = if (isFetchingSub) "در حال دریافت ساب‌سکریپشن..." else "📥 استعلام حجم و دریافت نودها",
                        onClick = {
                            isFetchingSub = true
                            scope.launch {
                                try {
                                    subInfo = ProxyEngine.fetchSubscription(subUrlInput)
                                    Toast.makeText(ctx, "ساب‌سکریپشن با موفقیت دریافت شد!", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(ctx, e.message ?: "خطا در دریافت", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isFetchingSub = false
                                }
                            }
                        },
                        enabled = subUrlInput.isNotBlank() && !isFetchingSub,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Subscription Quota Pod
            subInfo?.let { info ->
                item {
                    ModernCard(padding = 16.dp, cornerRadius = 22.dp) {
                        Text("وضعیت اشتراک و ترافیک:", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                        Spacer(Modifier.height(12.dp))

                        val progress = if (info.totalBytes > 0) (info.usedBytes.toFloat() / info.totalBytes).coerceIn(0f, 1f) else 0.5f

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("مصرف شده: ${info.usedFormatted}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ds.accent)
                                Text("کل حجم: ${info.totalFormatted}", fontSize = 12.sp, color = Ds.textSecondary)
                            }

                            LinearProgressIndicator(
                                progress = progress,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = if (progress > 0.9f) Ds.danger else Ds.accent,
                                trackColor = Ds.surfaceLow
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                        Hairline()
                        Spacer(Modifier.height(10.dp))

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BentoMicroPod(title = "تاریخ انقضا", value = info.expireDateFormatted, unit = "", color = Ds.warn, modifier = Modifier.weight(1f))
                            BentoMicroPod(title = "تعداد نودها", value = "${info.configs.size}", unit = "Node", color = Ds.ok, modifier = Modifier.weight(1f))
                        }
                    }
                }

                // Sub Nodes List
                if (info.configs.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("نودهای ساب‌سکریپشن (${info.configs.size} کانفیگ):", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)

                            PrimaryButton(
                                text = if (isTestingSubNodes) "..." else "⚡ تست پینگ تمام نودها",
                                onClick = {
                                    isTestingSubNodes = true
                                    scope.launch {
                                        info.configs.forEach { cfg ->
                                            val (ms, ok) = ProxyEngine.probeConfig(cfg)
                                            cfg.pingMs = ms
                                            cfg.tlsSuccess = ok
                                        }
                                        isTestingSubNodes = false
                                    }
                                },
                                enabled = !isTestingSubNodes
                            )
                        }
                    }

                    items(info.configs) { node ->
                        ModernCard(padding = 12.dp, cornerRadius = 16.dp) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Ds.accentDim)
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                    ) {
                                        Text(node.protocol, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Ds.accent)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(node.remark, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${node.host}:${node.port}", fontSize = 10.5.sp, color = Ds.textTertiary)
                                    }
                                }

                                if (node.pingMs > 0) {
                                    Text("${node.pingMs} ms", fontSize = 11.5.sp, fontFamily = Telemetry, fontWeight = FontWeight.Bold, color = Ds.ok)
                                } else if (node.pingMs == -1L && isTestingSubNodes) {
                                    Text("...", fontSize = 11.sp, color = Ds.warn)
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(90.dp)) }
    }
}
