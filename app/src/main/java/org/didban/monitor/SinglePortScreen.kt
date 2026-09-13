@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.didban.monitor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun SinglePortScreen(t: Str) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val servers = remember { Prefs.loadServers(ctx) }
    var selectedServerIndex by remember { mutableIntStateOf(0) }
    var sshPassword by remember { mutableStateOf("") }

    // Configuration state
    var bindPort by remember { mutableStateOf("443") }
    var panelDomain by remember { mutableStateOf("panel.example.com") }
    var panelLocalPort by remember { mutableStateOf("10000") }

    var subTlsDomain by remember { mutableStateOf("sub.example.com") }
    var subTlsLocalPort by remember { mutableStateOf("11000") }

    var realitySni by remember { mutableStateOf("yahoo.com, apple.com") }
    var realityLocalPort by remember { mutableStateOf("12000") }
    var realityProxyProtocol by remember { mutableStateOf(true) }

    var fallbackLocalPort by remember { mutableStateOf("13000") }

    var customRoutes = remember { mutableStateListOf<SinglePortRoute>() }

    // Modals
    var showAddRouteDialog by remember { mutableStateOf(false) }
    var newRouteName by remember { mutableStateOf("") }
    var newRouteSni by remember { mutableStateOf("") }
    var newRoutePort by remember { mutableStateOf("14000") }
    var newRouteProxyProtocol by remember { mutableStateOf(false) }

    var showConfigPreviewDialog by remember { mutableStateOf(false) }
    var showDeployDialog by remember { mutableStateOf(false) }
    var deployLogs = remember { mutableStateListOf<DeployStatusStep>() }
    var isDeploying by remember { mutableStateOf(false) }
    var deploySuccess by remember { mutableStateOf<Boolean?>(null) }

    var isGuideExpanded by remember { mutableStateOf(false) }

    fun currentConfig(): SinglePortConfig {
        return SinglePortConfig(
            bindPort = bindPort.toIntOrNull() ?: 443,
            panelDomain = panelDomain.trim(),
            panelLocalPort = panelLocalPort.toIntOrNull() ?: 10000,
            subTlsDomain = subTlsDomain.trim(),
            subTlsLocalPort = subTlsLocalPort.toIntOrNull() ?: 11000,
            realitySni = realitySni.trim(),
            realityLocalPort = realityLocalPort.toIntOrNull() ?: 12000,
            realityProxyProtocol = realityProxyProtocol,
            fallbackLocalPort = fallbackLocalPort.toIntOrNull() ?: 13000,
            customRoutes = customRoutes.toList()
        )
    }

    fun startAutoDeploy() {
        val server = servers.getOrNull(selectedServerIndex) ?: return
        if (sshPassword.isBlank()) {
            Toast.makeText(ctx, "لطفاً رمز عبور SSH سرور را وارد کنید", Toast.LENGTH_SHORT).show()
            return
        }

        deployLogs.clear()
        isDeploying = true
        deploySuccess = null
        showDeployDialog = true

        scope.launch {
            val success = SinglePortEngine.deployToRemote(
                host = server.host,
                port = 22,
                user = "root",
                password = sshPassword,
                config = currentConfig()
            ) { stepTitle, isDone, isError, detail ->
                deployLogs.add(
                    DeployStatusStep(
                        title = stepTitle,
                        isDone = isDone,
                        isError = isError,
                        detail = detail
                    )
                )
            }
            isDeploying = false
            deploySuccess = success
        }
    }

    fun copyToClipboard(label: String, text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(ctx, "📋 $label در کلیپ‌بورد کپی شد!", Toast.LENGTH_SHORT).show()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        // ── 1. Hero Bento Deck ──
        item {
            ModernCard(padding = 16.dp, cornerRadius = 22.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(
                        icon = Icons.Rounded.Tune,
                        tint = Ds.accent,
                        background = Ds.accentDim,
                        size = 42.dp,
                        iconSize = 22.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "دستیار تک‌پورت‌سازی سرور (Single-Port HAProxy)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ds.textPrimary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "هدایت تمام ترافیک پنل پاسارگاد، ساب‌سکریپشن، کانفیگ‌های TLS و REALITY تنها از روی پورت ۴۴۳ با تفکیک هوشمند SNI",
                            fontSize = 11.sp,
                            color = Ds.textSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // ── 2. Server Selection & SSH Password ──
        item {
            ModernCard(padding = 14.dp, cornerRadius = 20.dp) {
                if (servers.isEmpty()) {
                    Text("هیچ سروری ثبت نشده است. ابتدا در تب «سرورها» سرور خود را اضافه کنید.", fontSize = 12.sp, color = Ds.warn)
                } else {
                    Text("سرور مقصد برای استقرار تک‌پورت:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ds.textSecondary)
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
                        label = "رمز عبور SSH سرور (جهت استقرار خودکار):",
                        placeholder = "Root Password",
                        isPassword = true
                    )
                }
            }
        }

        // ── 3. Step-by-Step Educational Guide ──
        item {
            ModernCard(padding = 14.dp, cornerRadius = 18.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isGuideExpanded = !isGuideExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = null, tint = Ds.accent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("تک‌پورت کردن چطور کار می‌کند؟ (راهنما و اصول)", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                        }
                        Text(if (isGuideExpanded) "▲ بستن" else "▼ مطالعه", fontSize = 11.sp, color = Ds.accent)
                    }

                    AnimatedVisibility(visible = isGuideExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                            HorizontalDivider(color = Ds.hairline)

                            GuideBullet(
                                num = "۱",
                                title = "یک پورت ورودی برای همه (Port 443)",
                                desc = "HAProxy روی پورت ۴۴۳ سرور قرار می‌گیرد و بدون باز کردن رمزگذاری TLS، نام دامنه درخواستی (SNI) را بررسی می‌کند."
                            )

                            GuideBullet(
                                num = "۲",
                                title = "تفکیک هوشمند ترافیک پنل و پروکسی",
                                desc = "اگر کاربر دامنه پنل را باز کند، به پورت لوکال پنل (۱۰،۰۰۰) می‌رود. اگر کاربر فیلترشکن وصل شود، ترافیک بر اساس دامنه به اینباند مربوطه در Xray هدایت می‌شود."
                            )

                            GuideBullet(
                                num = "۳",
                                title = "پشتیبانی از REALITY و حفظ IP واقعی (Proxy Protocol)",
                                desc = "با ارسال هدر send-proxy به پورت لوکال ریلیتی، آی‌پی واقعی کاربران حفظ شده و سیستم محدودیت IP و آمار به درستی کار می‌کند."
                            )
                        }
                    }
                }
            }
        }

        // ── 4. Main Single-Port Visual Configurator ──
        item {
            ModernCard(padding = 14.dp, cornerRadius = 20.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ماتریس روتینگ هوشمند (SNI Routing Matrix):", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Ds.accentDim)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text("پورت اصلی: $bindPort", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Ds.accent)
                        }
                    }

                    HorizontalDivider(color = Ds.hairline)

                    // Route 1: PasarGuard Panel
                    RouteConfigCard(
                        title = "🌐 دامنه پنل پاسارگاد و ساب‌سکریپشن:",
                        hint = "ساب‌دامنه‌ای که برای ورود به وب‌پنل و دریافت لینک ساب استفاده می‌کنید",
                        domainValue = panelDomain,
                        onDomainChange = { panelDomain = it },
                        domainPlaceholder = "panel.example.com",
                        portValue = panelLocalPort,
                        onPortChange = { panelLocalPort = it },
                        portLabel = "پورت لوکال پنل:"
                    )

                    // Route 2: TLS / CDN Inbounds
                    RouteConfigCard(
                        title = "🔒 دامنه کانفیگ‌های TLS / CDN (VLESS / VMess):",
                        hint = "دامنه‌ای که برای کانفیگ‌های با گواهی SSL استفاده می‌کنید",
                        domainValue = subTlsDomain,
                        onDomainChange = { subTlsDomain = it },
                        domainPlaceholder = "sub.example.com",
                        portValue = subTlsLocalPort,
                        onPortChange = { subTlsLocalPort = it },
                        portLabel = "پورت لوکال اینباند TLS:"
                    )

                    // Route 3: REALITY
                    ModernCard(padding = 10.dp, cornerRadius = 14.dp, background = Ds.surfaceLow) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("⚡ دامنه‌های استتار REALITY (Camouflage SNI):", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                            Text("دامنه‌های فیک که در کانفیگ ریلیتی استفاده کرده‌اید (با کاما جدا کنید)", fontSize = 10.sp, color = Ds.textTertiary)

                            InputField(
                                value = realitySni,
                                onValueChange = { realitySni = it },
                                label = "",
                                placeholder = "yahoo.com, apple.com, microsoft.com"
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Checkbox(
                                        checked = realityProxyProtocol,
                                        onCheckedChange = { realityProxyProtocol = it },
                                        colors = CheckboxDefaults.colors(checkedColor = Ds.accent)
                                    )
                                    Text("ارسال PROXY Protocol (حفظ IP کاربر)", fontSize = 11.sp, color = Ds.textSecondary)
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("پورت ریلیتی: ", fontSize = 11.sp, color = Ds.textTertiary)
                                    Box(modifier = Modifier.width(75.dp)) {
                                        InputField(
                                            value = realityLocalPort,
                                            onValueChange = { realityLocalPort = it },
                                            label = "",
                                            placeholder = "12000"
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Route 4: Fallback
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("🛡️ پورت فالبک ضد اسکن (Default Fallback):", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                            Text("ترافیک ناشناس و اسکن‌های کور به این پورت منتقل می‌شوند", fontSize = 10.sp, color = Ds.textTertiary)
                        }
                        Box(modifier = Modifier.width(80.dp)) {
                            InputField(
                                value = fallbackLocalPort,
                                onValueChange = { fallbackLocalPort = it },
                                label = "",
                                placeholder = "13000"
                            )
                        }
                    }

                    // Custom Extra Routes
                    if (customRoutes.isNotEmpty()) {
                        Text("مسیرهای سفارشی اضافی:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ds.textSecondary)
                        customRoutes.forEachIndexed { idx, cr ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Ds.surfaceLow)
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(cr.name, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                                    Text("${cr.sni} ➔ 127.0.0.1:${cr.localPort}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Ds.accent)
                                }
                                CircleIconButton(
                                    icon = Icons.Rounded.DeleteOutline,
                                    contentDescription = "حذف مسیر",
                                    onClick = { customRoutes.removeAt(idx) },
                                    size = 28.dp,
                                    tint = Ds.danger
                                )
                            }
                        }
                    }

                    SoftButton(
                        text = "➕ افزودن مسیر / SNI سفارشی جدید",
                        onClick = { showAddRouteDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // ── 5. Action Buttons (1-Click Deploy & Script Generator) ──
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(
                    text = "⚡ استقرار خودکار ۱-کلیکه روی سرور (Auto Deploy)",
                    icon = Icons.Rounded.AutoAwesome,
                    onClick = { startAutoDeploy() },
                    enabled = !isDeploying && servers.isNotEmpty() && sshPassword.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftButton(
                        text = "📄 مشاهده haproxy.cfg",
                        onClick = { showConfigPreviewDialog = true },
                        modifier = Modifier.weight(1f)
                    )
                    SoftButton(
                        text = "📋 کپی اسکریپت Bash",
                        onClick = { copyToClipboard("اسکریپت تک‌پورت", SinglePortEngine.generateBashScript(currentConfig())) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item { Spacer(Modifier.height(90.dp)) }
    }

    // ── MODALS ──

    // 1. Add Custom Route Dialog
    if (showAddRouteDialog) {
        AlertDialog(
            onDismissRequest = { showAddRouteDialog = false },
            title = { Text("افزودن مسیر SNI جدید", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InputField(
                        value = newRouteName,
                        onValueChange = { newRouteName = it },
                        label = "عنوان مسیر (مثلاً Hysteria2 یا WireGuard):",
                        placeholder = "My Custom Core"
                    )
                    InputField(
                        value = newRouteSni,
                        onValueChange = { newRouteSni = it },
                        label = "دامنه یا SNI هدف:",
                        placeholder = "hy2.example.com"
                    )
                    InputField(
                        value = newRoutePort,
                        onValueChange = { newRoutePort = it },
                        label = "پورت لوکال مقصد (127.0.0.1):",
                        placeholder = "14000"
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = newRouteProxyProtocol,
                            onCheckedChange = { newRouteProxyProtocol = it },
                            colors = CheckboxDefaults.colors(checkedColor = Ds.accent)
                        )
                        Text("ارسال PROXY Protocol (send-proxy)", fontSize = 11.sp, color = Ds.textSecondary)
                    }
                }
            },
            confirmButton = {
                PrimaryButton(
                    text = "افزودن",
                    onClick = {
                        if (newRouteName.isNotBlank() && newRouteSni.isNotBlank()) {
                            customRoutes.add(
                                SinglePortRoute(
                                    name = newRouteName.trim(),
                                    sni = newRouteSni.trim(),
                                    localPort = newRoutePort.toIntOrNull() ?: 14000,
                                    useProxyProtocol = newRouteProxyProtocol
                                )
                            )
                            newRouteName = ""
                            newRouteSni = ""
                            newRoutePort = "14000"
                            newRouteProxyProtocol = false
                            showAddRouteDialog = false
                        }
                    }
                )
            },
            dismissButton = {
                SoftButton(text = "انصراف", onClick = { showAddRouteDialog = false })
            },
            containerColor = Ds.surfaceElevated
        )
    }

    // 2. Config Preview Dialog
    if (showConfigPreviewDialog) {
        val cfgText = SinglePortEngine.generateHaproxyCfg(currentConfig())
        AlertDialog(
            onDismissRequest = { showConfigPreviewDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("کانفیگ نهایی /etc/haproxy/haproxy.cfg", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                    SoftButton(text = "📋 کپی", onClick = { copyToClipboard("haproxy.cfg", cfgText) })
                }
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Ds.surfaceLow)
                        .padding(8.dp)
                ) {
                    Text(
                        text = cfgText,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Ds.textPrimary,
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }
            },
            confirmButton = {
                PrimaryButton(text = "بستن", onClick = { showConfigPreviewDialog = false })
            },
            containerColor = Ds.surfaceElevated
        )
    }

    // 3. Deployment Status Modal
    if (showDeployDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeploying) showDeployDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isDeploying) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Ds.accent, strokeWidth = 2.dp)
                    } else if (deploySuccess == true) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Ds.ok, modifier = Modifier.size(22.dp))
                    } else {
                        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Ds.danger, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isDeploying) "در حال استقرار تک‌پورت روی سرور..."
                        else if (deploySuccess == true) "🎉 استقرار با موفقیت انجام شد!"
                        else "❌ خطا در استقرار",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ds.textPrimary
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    deployLogs.forEach { step ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (step.isError) "❌" else if (step.isDone) "✅" else "⏳",
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    step.title,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (step.isError) Ds.danger else Ds.textPrimary
                                )
                                if (step.detail.isNotBlank()) {
                                    Text(step.detail, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Ds.textTertiary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                PrimaryButton(
                    text = if (isDeploying) "در حال اجرا..." else "تأیید و پایان",
                    onClick = { showDeployDialog = false },
                    enabled = !isDeploying
                )
            },
            containerColor = Ds.surfaceElevated
        )
    }
}

@Composable
private fun RouteConfigCard(
    title: String,
    hint: String,
    domainValue: String,
    onDomainChange: (String) -> Unit,
    domainPlaceholder: String,
    portValue: String,
    onPortChange: (String) -> Unit,
    portLabel: String
) {
    ModernCard(padding = 10.dp, cornerRadius = 14.dp, background = Ds.surfaceLow) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
            Text(hint, fontSize = 10.sp, color = Ds.textTertiary)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InputField(
                    value = domainValue,
                    onValueChange = onDomainChange,
                    label = "",
                    placeholder = domainPlaceholder,
                    modifier = Modifier.weight(1f)
                )

                Box(modifier = Modifier.width(75.dp)) {
                    InputField(
                        value = portValue,
                        onValueChange = onPortChange,
                        label = "",
                        placeholder = "10000"
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideBullet(num: String, title: String, desc: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Ds.accentDim),
            contentAlignment = Alignment.Center
        ) {
            Text(num, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Ds.accent)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
            Text(desc, fontSize = 10.5.sp, color = Ds.textSecondary, lineHeight = 15.sp)
        }
    }
}
