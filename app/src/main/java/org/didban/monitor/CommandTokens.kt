package org.didban.monitor

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Blank-canvas design tokens.
 *
 * این سیستم عمداً از `Ds` و Tokenهای Aero جدا است. `Ds` تا پایان مهاجرت
 * صفحه‌های قدیمی برای Compatibility باقی می‌ماند؛ هیچ صفحهٔ جدیدی نباید از
 * آن استفاده کند.
 */
data class CommandPalette(
    val canvas: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val border: Color,
    val borderStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val onAccent: Color,
    val success: Color,
    val successSurface: Color,
    val warning: Color,
    val warningSurface: Color,
    val danger: Color,
    val dangerSurface: Color,
    val info: Color,
    val infoSurface: Color,
    val focus: Color
)

val CommandLightPalette = CommandPalette(
    canvas = Color(0xFFF3F0E9),
    surface = Color(0xFFFFFDF8),
    surfaceRaised = Color(0xFFF8F6F0),
    border = Color(0xFFD8D2C7),
    borderStrong = Color(0xFFAFA79A),
    textPrimary = Color(0xFF1B2420),
    textSecondary = Color(0xFF65716A),
    textTertiary = Color(0xFF879089),
    accent = Color(0xFFB45532),
    onAccent = Color(0xFFFFFFFF),
    success = Color(0xFF197A5A),
    successSurface = Color(0xFFE4F2EB),
    warning = Color(0xFFA66A1F),
    warningSurface = Color(0xFFF8EDD8),
    danger = Color(0xFFB42318),
    dangerSurface = Color(0xFFFBE7E5),
    info = Color(0xFF315C83),
    infoSurface = Color(0xFFE7EFF7),
    focus = Color(0xFF315C83)
)

val CommandDarkPalette = CommandPalette(
    canvas = Color(0xFF111715),
    surface = Color(0xFF18211E),
    surfaceRaised = Color(0xFF202B27),
    border = Color(0xFF35433D),
    borderStrong = Color(0xFF60736A),
    textPrimary = Color(0xFFE8EEE9),
    textSecondary = Color(0xFFA8B6AF),
    textTertiary = Color(0xFF81928A),
    accent = Color(0xFFD58A5B),
    onAccent = Color(0xFF21150F),
    success = Color(0xFF59C596),
    successSurface = Color(0xFF17372B),
    warning = Color(0xFFD0A047),
    warningSurface = Color(0xFF3A2E19),
    danger = Color(0xFFF17870),
    dangerSurface = Color(0xFF3B201F),
    info = Color(0xFF8CB7D9),
    infoSurface = Color(0xFF1C3040),
    focus = Color(0xFF8CB7D9)
)

val LocalCommandPalette = staticCompositionLocalOf { CommandLightPalette }

object CommandColors {
    val canvas: Color @Composable get() = LocalCommandPalette.current.canvas
    val surface: Color @Composable get() = LocalCommandPalette.current.surface
    val surfaceRaised: Color @Composable get() = LocalCommandPalette.current.surfaceRaised
    val border: Color @Composable get() = LocalCommandPalette.current.border
    val borderStrong: Color @Composable get() = LocalCommandPalette.current.borderStrong
    val textPrimary: Color @Composable get() = LocalCommandPalette.current.textPrimary
    val textSecondary: Color @Composable get() = LocalCommandPalette.current.textSecondary
    val textTertiary: Color @Composable get() = LocalCommandPalette.current.textTertiary
    val accent: Color @Composable get() = LocalCommandPalette.current.accent
    val onAccent: Color @Composable get() = LocalCommandPalette.current.onAccent
    val success: Color @Composable get() = LocalCommandPalette.current.success
    val successSurface: Color @Composable get() = LocalCommandPalette.current.successSurface
    val warning: Color @Composable get() = LocalCommandPalette.current.warning
    val warningSurface: Color @Composable get() = LocalCommandPalette.current.warningSurface
    val danger: Color @Composable get() = LocalCommandPalette.current.danger
    val dangerSurface: Color @Composable get() = LocalCommandPalette.current.dangerSurface
    val info: Color @Composable get() = LocalCommandPalette.current.info
    val infoSurface: Color @Composable get() = LocalCommandPalette.current.infoSurface
    val focus: Color @Composable get() = LocalCommandPalette.current.focus
}

object CommandSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 40.dp
}

private fun CommandPalette.materialColors(dark: Boolean): ColorScheme = if (dark) {
    darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = successSurface,
        onPrimaryContainer = textPrimary,
        secondary = info,
        onSecondary = canvas,
        background = canvas,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceRaised,
        onSurfaceVariant = textSecondary,
        outline = border,
        outlineVariant = border
    )
} else {
    lightColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = infoSurface,
        onPrimaryContainer = textPrimary,
        secondary = info,
        onSecondary = Color.White,
        background = canvas,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceRaised,
        onSurfaceVariant = textSecondary,
        outline = border,
        outlineVariant = border
    )
}

private fun commandTypography(language: String): Typography {
    val bodyFamily = if (language == "fa") Vazirmatn else Inter
    val base = TextStyle(
        fontFamily = bodyFamily,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None
        )
    )
    return Typography(
        displayLarge = base.copy(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
        headlineSmall = base.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
        titleLarge = base.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = base.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.copy(fontSize = 15.sp, lineHeight = 23.sp),
        bodyMedium = base.copy(fontSize = 14.sp, lineHeight = 22.sp),
        bodySmall = base.copy(fontSize = 12.sp, lineHeight = 18.sp),
        labelLarge = base.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = base.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
        labelSmall = base.copy(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
    )
}

@Composable
fun CommandTheme(
    themeMode: String,
    language: String,
    content: @Composable () -> Unit
) {
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val dark = when (themeMode) {
        "dark" -> true
        "auto" -> systemDark
        else -> false
    }
    val palette = if (dark) CommandDarkPalette else CommandLightPalette

    CompositionLocalProvider(
        LocalCommandPalette provides palette,
        LocalLayoutDirection provides if (language == "fa") LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        MaterialTheme(
            colorScheme = palette.materialColors(dark),
            typography = commandTypography(language),
            shapes = Shapes(
                small = RoundedCornerShape(6.dp),
                medium = RoundedCornerShape(8.dp),
                large = RoundedCornerShape(12.dp)
            ),
            content = content
        )
    }
}

data class CommandCopy(
    val observe: String,
    val fleet: String,
    val operate: String,
    val diagnose: String,
    val workbench: String,
    val protect: String,
    val overview: String,
    val incidents: String,
    val servers: String,
    val serverDossier: String,
    val manageServers: String,
    val tunnels: String,
    val docker: String,
    val processes: String,
    val services: String,
    val radar: String,
    val uptime: String,
    val networkTools: String,
    val dns: String,
    val ssh: String,
    val batch: String,
    val sftp: String,
    val singlePort: String,
    val proxy: String,
    val developerLab: String,
    val vault: String,
    val alerts: String,
    val backup: String,
    val settings: String,
    val allSystems: String,
    val sync: String,
    val refreshing: String,
    val refresh: String,
    val addServer: String,
    val addTunnel: String,
    val addMonitor: String,
    val attention: String,
    val healthy: String,
    val offline: String,
    val waitingForData: String,
    val noServersTitle: String,
    val noServersBody: String,
    val openServer: String,
    val activeAttention: String,
    val recentActivity: String,
    val affectedServers: String,
    val noAttention: String,
    val noAttentionBody: String,
    val unknownState: String,
    val updated: String,
    val lastSeen: String,
    val latency: String,
    val cpu: String,
    val memory: String,
    val uptimeValue: String,
    val load: String,
    val incidentsFromLiveState: String,
    val noIncidentsTitle: String,
    val noIncidentsBody: String,
    val selectServer: String,
    val back: String,
    val close: String,
    val legacyBridgeTitle: String,
    val legacyBridgeBody: String,
    val openExistingTool: String,
    val stagedWorkspace: String,
    val language: String,
    val theme: String,
    val light: String,
    val dark: String,
    val automatic: String,
    val pollInterval: String,
    val save: String,
    val saved: String,
    val english: String,
    val persian: String,
    val currentServer: String,
    val noServerSelected: String,
    val capabilities: String,
    val notAvailable: String,
    val retry: String,
    val dataIsStale: String,
    val start: String,
    val stop: String,
    val restart: String,
    val test: String,
    val edit: String,
    val run: String,
    val operationDone: String,
    val operationFailed: String,
    val target: String,
    val host: String,
    val port: String,
    val mode: String,
    val addTarget: String,
    val sources: String,
    val runProbe: String
) {
    companion object {
        val fa = CommandCopy(
            observe = "پایش",
            fleet = "ناوگان",
            operate = "عملیات",
            diagnose = "تشخیص",
            workbench = "کارگاه",
            protect = "حفاظت",
            overview = "نمای کلی",
            incidents = "رخدادها",
            servers = "سرورها",
            serverDossier = "پروندهٔ سرور",
            manageServers = "مدیریت اتصال‌ها",
            tunnels = "تونل‌ها",
            docker = "Docker",
            processes = "پردازش‌ها",
            services = "سرویس‌ها",
            radar = "Radar سرور",
            uptime = "پایش دسترس‌پذیری",
            networkTools = "ابزارهای شبکه",
            dns = "DNS و Cloudflare",
            ssh = "SSH",
            batch = "اجرای گروهی",
            sftp = "SFTP",
            singlePort = "Single-Port",
            proxy = "Proxy Inspector",
            developerLab = "Developer Lab",
            vault = "Vault",
            alerts = "کانال‌های هشدار",
            backup = "Backup و Restore",
            settings = "تنظیمات",
            allSystems = "همهٔ سامانه‌ها",
            sync = "همگام‌سازی",
            refreshing = "در حال تازه‌سازی",
            refresh = "تازه‌سازی",
            addServer = "افزودن سرور",
            addTunnel = "افزودن تونل",
            addMonitor = "افزودن Monitor",
            attention = "نیازمند توجه",
            healthy = "سالم",
            offline = "آفلاین",
            waitingForData = "در انتظار دادهٔ واقعی",
            noServersTitle = "هنوز سروری متصل نیست",
            noServersBody = "برای شروع، یک Agent واقعی را به ناوگان اضافه کنید. دادهٔ نمونه نمایش داده نمی‌شود.",
            openServer = "باز کردن پرونده",
            activeAttention = "صف انتظار عملیات",
            recentActivity = "وضعیت‌های اخیر",
            affectedServers = "سرورهای درگیر",
            noAttention = "مورد فوری وجود ندارد",
            noAttentionBody = "در دادهٔ فعلی، وضعیت نیازمند توجهی از Agentها دریافت نشده است.",
            unknownState = "نامشخص",
            updated = "به‌روزرسانی",
            lastSeen = "آخرین مشاهده",
            latency = "تأخیر",
            cpu = "CPU",
            memory = "حافظه",
            uptimeValue = "Uptime",
            load = "Load",
            incidentsFromLiveState = "این فهرست از وضعیت زنده و Thresholdهای فعلی ساخته شده است؛ تاریخچهٔ کامل رخداد در مرحلهٔ Incident History اضافه می‌شود.",
            noIncidentsTitle = "رخداد فعالی ثبت نشده است",
            noIncidentsBody = "وقتی Agent یا Uptime وضعیت غیرعادی واقعی گزارش کند، اینجا قابل پیگیری خواهد بود.",
            selectServer = "انتخاب سرور",
            back = "بازگشت",
            close = "بستن",
            legacyBridgeTitle = "قابلیت در حال انتقال",
            legacyBridgeBody = "این مسیر برای حفظ دسترسی به functionality واقعی به‌صورت موقت از سطح قدیمی استفاده می‌کند. ظاهر نهایی آن در مرحلهٔ همین Workspace بازطراحی می‌شود.",
            openExistingTool = "باز کردن قابلیت موجود",
            stagedWorkspace = "این Workspace در مرحلهٔ بعدی منتقل می‌شود.",
            language = "زبان",
            theme = "تم",
            light = "روشن",
            dark = "تیره",
            automatic = "خودکار",
            pollInterval = "فاصلهٔ پایش، ثانیه",
            save = "ذخیره",
            saved = "ذخیره شد",
            english = "English",
            persian = "فارسی",
            currentServer = "سرور انتخاب‌شده",
            noServerSelected = "بدون محدودهٔ سرور",
            capabilities = "قابلیت‌ها",
            notAvailable = "در Agent فعلی در دسترس نیست",
            retry = "تلاش دوباره",
            dataIsStale = "داده قدیمی است",
            start = "شروع",
            stop = "توقف",
            restart = "راه‌اندازی مجدد",
            test = "آزمون",
            edit = "ویرایش",
            run = "اجرا",
            operationDone = "عملیات با موفقیت انجام شد",
            operationFailed = "عملیات ناموفق بود",
            target = "هدف",
            host = "Host",
            port = "Port",
            mode = "Mode",
            addTarget = "افزودن هدف",
            sources = "منابع Probe",
            runProbe = "اجرای Probe"
        )

        val en = fa.copy(
            observe = "Observe",
            fleet = "Fleet",
            operate = "Operate",
            diagnose = "Diagnose",
            workbench = "Workbench",
            protect = "Protect",
            overview = "Overview",
            incidents = "Incidents",
            servers = "Servers",
            serverDossier = "Server dossier",
            manageServers = "Manage connections",
            tunnels = "Tunnels",
            processes = "Processes",
            services = "Services",
            radar = "Server Radar",
            uptime = "Uptime",
            networkTools = "Network tools",
            dns = "DNS and Cloudflare",
            ssh = "SSH",
            batch = "Batch execution",
            developerLab = "Developer Lab",
            vault = "Vault",
            alerts = "Alert delivery",
            backup = "Backup and Restore",
            settings = "Settings",
            allSystems = "All systems",
            sync = "Sync",
            refreshing = "Refreshing",
            refresh = "Refresh",
            addServer = "Add server",
            addTunnel = "Add tunnel",
            addMonitor = "Add monitor",
            attention = "Needs attention",
            healthy = "Healthy",
            offline = "Offline",
            waitingForData = "Waiting for real data",
            noServersTitle = "No server is connected yet",
            noServersBody = "Connect a real Agent to begin. Sample data is never shown.",
            openServer = "Open dossier",
            activeAttention = "Operations queue",
            recentActivity = "Recent state",
            affectedServers = "Affected servers",
            noAttention = "Nothing needs attention",
            noAttentionBody = "No abnormal state has been reported by the current Agents.",
            unknownState = "Unknown",
            updated = "Updated",
            lastSeen = "Last seen",
            latency = "Latency",
            memory = "Memory",
            uptimeValue = "Uptime",
            load = "Load",
            incidentsFromLiveState = "This list is derived from live state and current thresholds; full incident history will be added in the Incident History stage.",
            noIncidentsTitle = "No active incident",
            noIncidentsBody = "Real abnormal state from an Agent or Uptime monitor will appear here.",
            selectServer = "Select server",
            back = "Back",
            close = "Close",
            legacyBridgeTitle = "Capability migration in progress",
            legacyBridgeBody = "This temporary bridge preserves access to the real functionality. Its final presentation will be rebuilt with this workspace.",
            openExistingTool = "Open existing capability",
            stagedWorkspace = "This workspace will be migrated in the next stage.",
            language = "Language",
            theme = "Theme",
            light = "Light",
            dark = "Dark",
            automatic = "Automatic",
            pollInterval = "Poll interval, seconds",
            save = "Save",
            saved = "Saved",
            english = "English",
            persian = "فارسی",
            currentServer = "Selected server",
            noServerSelected = "No server scope",
            capabilities = "Capabilities",
            notAvailable = "Not available from this Agent",
            retry = "Retry",
            dataIsStale = "Data is stale",
            start = "Start",
            stop = "Stop",
            restart = "Restart",
            test = "Test",
            edit = "Edit",
            run = "Run",
            operationDone = "Operation completed",
            operationFailed = "Operation failed",
            target = "Target",
            host = "Host",
            port = "Port",
            mode = "Mode",
            addTarget = "Add target",
            sources = "Probe sources",
            runProbe = "Run probe"
        )

        fun forLanguage(language: String): CommandCopy = if (language == "fa") fa else en
    }
}
