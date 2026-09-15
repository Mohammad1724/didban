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
    val violet: Color,
    val track: Color,
    val focus: Color
)

val CommandLightPalette = CommandPalette(
    canvas = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFF1F5F9),
    border = Color(0xFFE2E8F0),
    borderStrong = Color(0xFFCBD5E1),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    textTertiary = Color(0xFF94A3B8),
    accent = Color(0xFF0284C7),
    onAccent = Color(0xFFFFFFFF),
    success = Color(0xFF059669),
    successSurface = Color(0xFFE7F7F0),
    warning = Color(0xFFD97706),
    warningSurface = Color(0xFFFFF7E6),
    danger = Color(0xFFDC2626),
    dangerSurface = Color(0xFFFFECEC),
    info = Color(0xFF2563EB),
    infoSurface = Color(0xFFEFF6FF),
    violet = Color(0xFF7C3AED),
    track = Color(0xFFE2E8F0),
    focus = Color(0xFF2563EB)
)

val CommandDarkPalette = CommandPalette(
    canvas = Color(0xFF07090E),
    surface = Color(0xFF0F131D),
    surfaceRaised = Color(0xFF151C28),
    border = Color(0xFF171F2C),
    borderStrong = Color(0xFF26334A),
    textPrimary = Color(0xFFF1F5F9),
    textSecondary = Color(0xFF94A3B8),
    textTertiary = Color(0xFF54627A),
    accent = Color(0xFF00E5FF),
    onAccent = Color(0xFF001318),
    success = Color(0xFF10B981),
    successSurface = Color(0xFF0C2926),
    warning = Color(0xFFF59E0B),
    warningSurface = Color(0xFF30230D),
    danger = Color(0xFFEF4444),
    dangerSurface = Color(0xFF32161D),
    info = Color(0xFF3B82F6),
    infoSurface = Color(0xFF101F3A),
    violet = Color(0xFFA855F7),
    track = Color(0xFF151C28),
    focus = Color(0xFF00E5FF)
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
    val violet: Color @Composable get() = LocalCommandPalette.current.violet
    val track: Color @Composable get() = LocalCommandPalette.current.track
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
                small = RoundedCornerShape(8.dp),
                medium = RoundedCornerShape(12.dp),
                large = RoundedCornerShape(16.dp)
            ),
            content = content
        )
    }
}

/**
 * Every user-facing string in the Command shell.
 *
 * An interface with two implementing objects, not a data class: a data
 * class with this many properties generates a constructor past the JVM's
 * 255-parameter limit, which compiles and then dies at class-load time
 * with ClassFormatError. It also means a key omitted from the English
 * table is a compile error here instead of a silent Persian fallback.
 */
interface CommandCopy {
    val observe: String
    val fleet: String
    val operate: String
    val diagnose: String
    val workbench: String
    val protect: String
    val overview: String
    val incidents: String
    val servers: String
    val serverDossier: String
    val manageServers: String
    val tunnels: String
    val docker: String
    val processes: String
    val services: String
    val radar: String
    val uptime: String
    val networkTools: String
    val dns: String
    val ssh: String
    val batch: String
    val sftp: String
    val singlePort: String
    val proxy: String
    val developerLab: String
    val vault: String
    val alerts: String
    val backup: String
    val settings: String
    val allSystems: String
    val sync: String
    val refreshing: String
    val refresh: String
    val addServer: String
    val addTunnel: String
    val addMonitor: String
    val attention: String
    val healthy: String
    val offline: String
    val waitingForData: String
    val noServersTitle: String
    val noServersBody: String
    val openServer: String
    val activeAttention: String
    val recentActivity: String
    val affectedServers: String
    val noAttention: String
    val noAttentionBody: String
    val unknownState: String
    val updated: String
    val lastSeen: String
    val latency: String
    val cpu: String
    val memory: String
    val uptimeValue: String
    val load: String
    val incidentsFromLiveState: String
    val noIncidentsTitle: String
    val noIncidentsBody: String
    val selectServer: String
    val back: String
    val close: String
    val openExistingTool: String
    val stagedWorkspace: String
    val language: String
    val theme: String
    val light: String
    val dark: String
    val automatic: String
    val pollInterval: String
    val save: String
    val saved: String
    val english: String
    val persian: String
    val currentServer: String
    val noServerSelected: String
    val capabilities: String
    val notAvailable: String
    val retry: String
    val dataIsStale: String
    val start: String
    val stop: String
    val restart: String
    val test: String
    val edit: String
    val run: String
    val operationDone: String
    val operationFailed: String
    val target: String
    val host: String
    val port: String
    val mode: String
    val addTarget: String
    val sources: String
    val runProbe: String
    val online: String
    val averageCpu: String
    val averageMemory: String
    val telemetry: String
    val coverage: String
    val scoreOutOf: String
    val nodes: String
    val pressAgainToExit: String
    val dnsNoToken: String
    val dnsZonesLoadFailed: String
    val dnsRecordsLoadFailed: String
    val dnsRecordFieldsRequired: String
    val dnsRecordCreated: String
    val dnsRecordSaveFailed: String
    val dnsLookupFailed: String
    val dnsTokenSaved: String
    val dnsTokenBody: String
    val dnsNoZonesYet: String
    val dnsZoneEmpty: String
    val dnsNoPublicAnswer: String
    val dnsDeleteTitle: String
    val dnsRecordDeleted: String
    val dnsRecordDeleteFailed: String
    val dnsRecordUpdated: String
    val dnsRecordsLoaded: String
    val dnsDeleteBody: String
    val setPollRange: String
    val setPollSaved: String
    val setBody: String
    val setAppearanceBody: String
    val setPollBody: String
    val setDangerBody: String
    val setPurge: String
    val setTrustEmpty: String
    val setRuntimeBody: String
    val setConnectivityBody: String
    val setResetVaultBody: String
    val setPurgeTrustBody: String
    val setVaultReady: String
    val setVaultLockedHint: String
    val setResetVaultTitle: String
    val setPurgeTrustTitle: String
    val setResetVaultAction: String
    val setPurgeTrustAction: String
    val setTrustCount: String
    val tunSaved: String
    val tunCodeGenerated: String
    val tunCodeFailed: String
    val tunDeploying: String
    val tunDeployFailed: String
    val tunProbeFailed: String
    val tunActionFailedGeneric: String
    val tunEmptyBody: String
    val tunDiscoveryBody: String
    val tunActionsBody: String
    val tunDeleteBody: String
    val tunDeleted: String
    val tunTokenEmpty: String
    val tunTokenSet: String
    val tunDeleteTitle: String
    val tunDeployTitle: String
    val tunDeployBody: String
    val tunActionDone: String
    val tunActionFailed: String
    val deploy: String
    val vaultBadMaster: String
    val vaultUnlockFailed: String
    val vaultSaveFailed: String
    val lock: String
    val vaultOpen: String
    val vaultLocked: String
    val vaultOpenBody: String
    val vaultLockedBody: String
    val vaultLockNow: String
    val vaultUnlock: String
    val vaultSecrets: String
    val vaultAddSecret: String
    val vaultEmpty: String
    val vaultEmptyBody: String
    val hide: String
    val revealTemporarily: String
    val copySecret: String
    val deleteSecret: String
    val secretTitle: String
    val secretContent: String
    val backupCreate: String
    val backupBody: String
    val backupPasswordOptional: String
    val backupCreateEncrypted: String
    val backupCreated: String
    val backupOutput: String
    val backupVerifyRestore: String
    val backupText: String
    val backupValid: String
    val backupInvalid: String
    val alertsChannelsBody: String
    val alertsEnableTelegram: String
    val alertsEnableDiscord: String
    val alertsTriggers: String
    val alertsTriggerServerDown: String
    val alertsTriggerCpuSpike: String
    val alertsTriggerTunnelDown: String
    val backupSummary: String
    val netIndexBody: String
    val netQReachable: String
    val netQReachableTools: String
    val netQNetworkLayer: String
    val netQNetworkLayerTools: String
    val netQTls: String
    val netQTlsTools: String
    val netQDns: String
    val netQDnsTools: String
    val netQQuality: String
    val netQQualityTools: String
    val dnsIndexBody: String
    val dnsZoneRecords: String
    val dnsZoneRecordsBody: String
    val dnsInspect: String
    val dnsInspectBody: String
    val copyAction: String
    val selected: String
    val notSelected: String
    val batchSharedPassword: String
    val batchSummary: String
    val succeeded: String
    val failed: String
    val noOutput: String
    val hostKeyChanged: String
    val hostKeyNew: String
    val hostKeyChangedBody: String
    val hostKeyFirstBody: String
    val resetTrust: String
    val trustAndConnect: String
    val srvNameRequired: String
    val srvHostInvalid: String
    val srvTokenRequired: String
    val srvFingerprintInvalid: String
    val srvSavedPolled: String
    val srvConnecting: String
    val srvFingerprintSuggested: String
    val srvConnectFailed: String
    val srvDossierHint: String
    val srvDeleteTitle: String
    val srvDeleteBody: String
    val srvLocalDeleted: String
    val delete: String
    val upTargetRequired: String
    val upKeywordRequired: String
    val upSaved: String
    val upTargetFirst: String
    val upCheckDone: String
    val upCheckFailed: String
    val upNoMonitorYet: String
    val upEditorBody: String
    val upDeleteTitle: String
    val upDeleteBody: String
    val upDeleted: String
    val and: String
    val serversSearchPlaceholder: String
    val tunnelsBody: String
    val devLabBody: String
    val vaultConfigUnparseable: String
    val hostKeysStatus: String
    val sftpBody: String
    val netNoHost: String
    val netNoOpenPorts: String
    val netOnlyAnsweredPorts: String
    val netProbeBody: String
    val svcNoPassword: String
    val svcFetching: String
    val svcReadingJournal: String
    val svcRunningAction: String
    val svcConfirmTitle: String
    val svcConfirmBody: String
    val cancel: String
    val hubObserveBody: String
    val hubFileTransfer: String
    val hubInspectGenerate: String
    val hubDevTools: String
    val hubProtectBody: String
    val hubSecretTrust: String
    val hubAlertDelivery: String
    val hubDataRecovery: String
    val hubAppBehaviour: String
    val bandwidth: String
    val bandwidthBody: String
    val bandwidthRunning: String
    val bandwidthResult: String
    val bandwidthUnsupported: String
    val download: String
    val upload: String
    val jitter: String
    val packetLoss: String
    val networkError: String
    val security: String
    val securityFirewall: String
    val securityFirewallBody: String
    val securityInspect: String
    val securityPortToAllow: String
    val securityAllow: String
    val securityPortOpened: String
    val securityBadPort: String
    val securitySshPassword: String
    val securityNeedPassword: String
    val securityBans: String
    val securityBansBody: String
    val securityNoBans: String
    val securityNoBansBody: String
    val securityNoFail2ban: String
    val securityUnban: String
    val securityUnbanned: String
    val securityBadValue: String
    val radarSyncAll: String
    val radarSyncAllBody: String
    val radarSynced: String
    val radarNoTargets: String
    val radarNoTargetsBody: String
    val vantagePhone: String

    companion object {
        val fa: CommandCopy = CommandCopyFa
        val en: CommandCopy = CommandCopyEn

        fun forLanguage(language: String): CommandCopy = if (language == "fa") fa else en
    }
}

/** Persian (default). */
internal object CommandCopyFa : CommandCopy {
    override val observe = "پایش"
    override val fleet = "ناوگان"
    override val operate = "عملیات"
    override val diagnose = "تشخیص"
    override val workbench = "کارگاه"
    override val protect = "حفاظت"
    override val overview = "نمای کلی"
    override val incidents = "رخدادها"
    override val servers = "سرورها"
    override val serverDossier = "پروندهٔ سرور"
    override val manageServers = "مدیریت اتصال‌ها"
    override val tunnels = "تونل‌ها"
    override val docker = "Docker"
    override val processes = "پردازش‌ها"
    override val services = "سرویس‌ها"
    override val radar = "Radar سرور"
    override val uptime = "پایش دسترس‌پذیری"
    override val networkTools = "ابزارهای شبکه"
    override val dns = "DNS و Cloudflare"
    override val ssh = "SSH"
    override val batch = "اجرای گروهی"
    override val sftp = "SFTP"
    override val singlePort = "Single-Port"
    override val proxy = "Proxy Inspector"
    override val developerLab = "Developer Lab"
    override val vault = "Vault"
    override val alerts = "کانال‌های هشدار"
    override val backup = "Backup و Restore"
    override val settings = "تنظیمات"
    override val allSystems = "همهٔ سامانه‌ها"
    override val sync = "همگام‌سازی"
    override val refreshing = "در حال تازه‌سازی"
    override val refresh = "تازه‌سازی"
    override val addServer = "افزودن سرور"
    override val addTunnel = "افزودن تونل"
    override val addMonitor = "افزودن Monitor"
    override val attention = "نیازمند توجه"
    override val healthy = "سالم"
    override val offline = "آفلاین"
    override val waitingForData = "در انتظار دادهٔ واقعی"
    override val noServersTitle = "هنوز سروری متصل نیست"
    override val noServersBody = "برای شروع، یک Agent واقعی را به ناوگان اضافه کنید. دادهٔ نمونه نمایش داده نمی‌شود."
    override val openServer = "باز کردن پرونده"
    override val activeAttention = "صف انتظار عملیات"
    override val recentActivity = "وضعیت‌های اخیر"
    override val affectedServers = "سرورهای درگیر"
    override val noAttention = "مورد فوری وجود ندارد"
    override val noAttentionBody = "در دادهٔ فعلی، وضعیت نیازمند توجهی از Agentها دریافت نشده است."
    override val unknownState = "نامشخص"
    override val updated = "به‌روزرسانی"
    override val lastSeen = "آخرین مشاهده"
    override val latency = "تأخیر"
    override val cpu = "CPU"
    override val memory = "حافظه"
    override val uptimeValue = "Uptime"
    override val load = "Load"
    override val incidentsFromLiveState = "این فهرست از وضعیت زنده و Thresholdهای فعلی ساخته شده است؛ تاریخچهٔ کامل رخداد در مرحلهٔ Incident History اضافه می‌شود."
    override val noIncidentsTitle = "رخداد فعالی ثبت نشده است"
    override val noIncidentsBody = "وقتی Agent یا Uptime وضعیت غیرعادی واقعی گزارش کند، اینجا قابل پیگیری خواهد بود."
    override val selectServer = "انتخاب سرور"
    override val back = "بازگشت"
    override val close = "بستن"
    override val openExistingTool = "باز کردن قابلیت موجود"
    override val stagedWorkspace = "این Workspace در مرحلهٔ بعدی منتقل می‌شود."
    override val language = "زبان"
    override val theme = "تم"
    override val light = "روشن"
    override val dark = "تیره"
    override val automatic = "خودکار"
    override val pollInterval = "فاصلهٔ پایش، ثانیه"
    override val save = "ذخیره"
    override val saved = "ذخیره شد"
    override val english = "English"
    override val persian = "فارسی"
    override val currentServer = "سرور انتخاب‌شده"
    override val noServerSelected = "بدون محدودهٔ سرور"
    override val capabilities = "قابلیت‌ها"
    override val notAvailable = "در Agent فعلی در دسترس نیست"
    override val retry = "تلاش دوباره"
    override val dataIsStale = "داده قدیمی است"
    override val start = "شروع"
    override val stop = "توقف"
    override val restart = "راه‌اندازی مجدد"
    override val test = "آزمون"
    override val edit = "ویرایش"
    override val run = "اجرا"
    override val operationDone = "عملیات با موفقیت انجام شد"
    override val operationFailed = "عملیات ناموفق بود"
    override val target = "هدف"
    override val host = "Host"
    override val port = "Port"
    override val mode = "Mode"
    override val addTarget = "افزودن هدف"
    override val sources = "منابع Probe"
    override val runProbe = "اجرای Probe"
    override val online = "آنلاین"
    override val averageCpu = "میانگین CPU"
    override val averageMemory = "میانگین حافظه"
    override val telemetry = "تله‌متری"
    override val coverage = "پوشش داده"
    override val scoreOutOf = "از ۱۰۰"
    override val nodes = "گره"
    override val pressAgainToExit = "برای خروج، دوباره Back بزنید"
    override val dnsNoToken = "Cloudflare API token وارد نشده است."
    override val dnsZonesLoadFailed = "Zoneها بارگذاری نشدند."
    override val dnsRecordsLoadFailed = "Recordها بارگذاری نشدند."
    override val dnsRecordFieldsRequired = "Zone، name و content اجباری هستند."
    override val dnsRecordCreated = "Record ساخته شد."
    override val dnsRecordSaveFailed = "ذخیرهٔ Record ناموفق بود."
    override val dnsLookupFailed = "DNS lookup ناموفق بود."
    override val dnsTokenSaved = "Token در SecureStorage ذخیره شد؛ برای دریافت Zoneها Refresh را بزنید."
    override val dnsTokenBody = "Token در UI نمایش داده نمی‌شود و هیچ Zone یا Record ساختگی ساخته نمی‌شود."
    override val dnsNoZonesYet = "پس از واردکردن Token، Zoneهای واقعی اینجا ظاهر می‌شوند."
    override val dnsZoneEmpty = "این Zone Record واقعی ندارد یا هنوز load نشده است."
    override val dnsNoPublicAnswer = "پاسخ DNS عمومی برای این domain دریافت نشد."
    override val dnsDeleteTitle = "Delete DNS record؟"
    override val dnsRecordDeleted = "Record حذف شد."
    override val dnsRecordDeleteFailed = "حذف Record ناموفق بود."
    override val dnsRecordUpdated = "Record ویرایش شد."
    override val dnsRecordsLoaded = "%1 record واقعی دریافت شد."
    override val dnsDeleteBody = "%1 %2 حذف واقعی از Cloudflare خواهد شد."
    override val setPollRange = "Poll interval باید بین ۵ تا ۳۶۰۰ ثانیه باشد."
    override val setPollSaved = "Poll interval ذخیره شد؛ از Poll بعدی اعمال می‌شود."
    override val setBody = "کنترل رفتار، مشاهده‌پذیری و سطح اعتماد دستگاه"
    override val setAppearanceBody = "تغییرات این بخش بلافاصله در Shell اعمال می‌شوند."
    override val setPollBody = "فاصلهٔ درخواست‌های واقعی Agent و محدودیت‌های آن."
    override val setDangerBody = "این عملیات به داده‌های رمزنگاری‌شده یا trust anchorهای SSH دست می‌زنند."
    override val setPurge = "پاک‌سازی"
    override val setTrustEmpty = "Trust Store خالی است."
    override val setRuntimeBody = "اطلاعات runtime؛ هیچ وضعیت ساختگی در این بخش تولید نمی‌شود."
    override val setConnectivityBody = "برای بررسی کامل connectivity از ابزارهای SSH، SFTP و Probe در Workbench استفاده کنید."
    override val setResetVaultBody = "این کار Master Password، canary و تمام Noteهای رمزنگاری‌شدهٔ Vault را حذف می‌کند و قابل بازگشت نیست."
    override val setPurgeTrustBody = "تمام SSH host keyهای ذخیره‌شده حذف می‌شوند؛ اتصال بعدی هر سرور دوباره نیازمند Trust است."
    override val setVaultReady = "Secretها بدون Master Password خوانده نمی‌شوند."
    override val setVaultLockedHint = "برای ذخیرهٔ Secret ابتدا Vault را باز کنید."
    override val setResetVaultTitle = "Reset Vault؟"
    override val setPurgeTrustTitle = "پاک‌سازی Trust Store؟"
    override val setResetVaultAction = "حذف Vault"
    override val setPurgeTrustAction = "حذف Trustها"
    override val setTrustCount = "%1 host key ثبت شده؛ fingerprintها Secret نیستند."
    override val tunSaved = "تنظیمات تونل ذخیره شد؛ هنوز هیچ deploy یا تغییر remote انجام نشده است."
    override val tunCodeGenerated = "کد بر اساس تنظیمات واقعی ساخته شد و token پایدار ذخیره شد."
    override val tunCodeFailed = "تولید کد ناموفق بود."
    override val tunDeploying = "در حال deploy از طریق Agent واقعی..."
    override val tunDeployFailed = "Deploy ناموفق بود."
    override val tunProbeFailed = "Probe ناموفق بود."
    override val tunActionFailedGeneric = "Remote action ناموفق بود."
    override val tunEmptyBody = "هنوز تونلی ذخیره نشده است. فرم زیر برای ساخت اولین تنظیمات آماده است."
    override val tunDiscoveryBody = "این tunnel از Discovery آمده است؛ deploy بدون token واقعی مسدود خواهد بود."
    override val tunActionsBody = "این actionها به Agentهای واقعی که host آن‌ها با endpointها match شود ارسال می‌شوند."
    override val tunDeleteBody = "رکورد محلی این tunnel حذف می‌شود و delete remote نیز برای Agentهای match‌شده ارسال خواهد شد."
    override val tunDeleted = "رکورد tunnel حذف شد."
    override val tunTokenEmpty = "token خالی است"
    override val tunTokenSet = "token وارد شده"
    override val tunDeleteTitle = "Delete tunnel؟"
    override val tunDeployTitle = "Deploy tunnel؟"
    override val tunDeployBody = "این عملیات روی Agentهای واقعی اجرا می‌شود و ممکن است سرویس‌های دو طرف را تغییر دهد."
    override val tunActionDone = "Remote action %1 انجام شد."
    override val tunActionFailed = "Remote action %1 ناموفق بود."
    override val deploy = "Deploy"
    override val vaultBadMaster = "رمز Master نادرست است."
    override val vaultUnlockFailed = "بازکردن Vault ناموفق بود."
    override val vaultSaveFailed = "ذخیرهٔ Vault ناموفق بود."
    override val lock = "قفل"
    override val vaultOpen = "Vault باز است"
    override val vaultLocked = "Vault قفل است"
    override val vaultOpenBody = "Secretها فقط در Session فعلی قابل مشاهده‌اند."
    override val vaultLockedBody = "هیچ Secretی در حالت قفل نمایش داده نمی‌شود."
    override val vaultLockNow = "قفل فوری"
    override val vaultUnlock = "بازکردن Vault"
    override val vaultSecrets = "Secretهای ذخیره‌شده"
    override val vaultAddSecret = "افزودن Secret"
    override val vaultEmpty = "Vault خالی است"
    override val vaultEmptyBody = "Secret واقعی خود را فقط پس از Unlock اضافه کنید."
    override val hide = "پنهان‌کردن"
    override val revealTemporarily = "نمایش موقت"
    override val copySecret = "کپی Secret"
    override val deleteSecret = "حذف Secret"
    override val secretTitle = "عنوان"
    override val secretContent = "محتوای حساس"
    override val backupCreate = "ساخت Backup"
    override val backupBody = "Backup بدون Password قابل خواندن است؛ برای دادهٔ واقعی از رمز استفاده کنید."
    override val backupPasswordOptional = "Password اختیاری"
    override val backupCreateEncrypted = "ساخت Backup رمزنگاری‌شده"
    override val backupCreated = "Backup ساخته شد. مقدار آن را خارج از دستگاه امن نگه دارید."
    override val backupOutput = "خروجی Backup"
    override val backupVerifyRestore = "بررسی و Restore"
    override val backupText = "متن Backup"
    override val backupValid = "ساختار معتبر"
    override val backupInvalid = "ساختار نامعتبر"
    override val alertsChannelsBody = "کانال‌ها و Triggerهای واقعی"
    override val alertsEnableTelegram = "فعال‌سازی Telegram"
    override val alertsEnableDiscord = "فعال‌سازی Discord"
    override val alertsTriggers = "Triggerها"
    override val alertsTriggerServerDown = "قطع شدن Server"
    override val alertsTriggerCpuSpike = "Spike CPU یا Memory"
    override val alertsTriggerTunnelDown = "افتادن Tunnel"
    override val backupSummary = "%1 سرور · %2 تونل · %3 Monitor · %4"
    override val netIndexBody = "هر ابزار با سؤال تشخیصی خودش شروع می‌شود."
    override val netQReachable = "آیا مقصد از این Server قابل دسترسی است؟"
    override val netQReachableTools = "TCP، HTTP، SSL و Check-Host"
    override val netQNetworkLayer = "آیا مشکل در لایهٔ شبکه است؟"
    override val netQNetworkLayerTools = "DPI، Port Scanner و TCP Ping"
    override val netQTls = "گواهی و هویت TLS درست است؟"
    override val netQTlsTools = "Subject، Chain، SAN و Fingerprint"
    override val netQDns = "DNS چه پاسخی می‌دهد؟"
    override val netQDnsTools = "Recordها و Cloudflare"
    override val netQQuality = "کیفیت اتصال چقدر است؟"
    override val netQQualityTools = "Latency، Loss، Jitter و Bandwidth"
    override val dnsIndexBody = "تشخیص پاسخ DNS از مدیریت Record جداست."
    override val dnsZoneRecords = "مدیریت Zone و Record"
    override val dnsZoneRecordsBody = "ساخت، ویرایش و حذف Recordهای واقعی Cloudflare"
    override val dnsInspect = "بررسی پاسخ DNS"
    override val dnsInspectBody = "Resolve، Reverse DNS و Recordهای عمومی"
    override val copyAction = "کپی"
    override val selected = "انتخاب‌شده"
    override val notSelected = "انتخاب‌نشده"
    override val batchSharedPassword = "SSH Password مشترک"
    override val batchSummary = "%1 موفق · %2 ناموفق"
    override val succeeded = "موفق"
    override val failed = "ناموفق"
    override val noOutput = "بدون خروجی"
    override val hostKeyChanged = "کلید SSH تغییر کرده است"
    override val hostKeyNew = "اعتماد به کلید SSH جدید"
    override val hostKeyChangedBody = "کلید فعلی با Trust Store یکسان نیست. فقط در صورت تأیید مستقل، اعتماد را بازنشانی کنید."
    override val hostKeyFirstBody = "این اولین اتصال است. Fingerprint زیر ذخیره خواهد شد."
    override val resetTrust = "بازنشانی اعتماد"
    override val trustAndConnect = "اعتماد و اتصال"
    override val srvNameRequired = "نام اتصال اجباری است."
    override val srvHostInvalid = "Host باید hostname یا IPv4 معتبر باشد."
    override val srvTokenRequired = "Agent token اجباری است."
    override val srvFingerprintInvalid = "Fingerprint TLS معتبر نیست."
    override val srvSavedPolled = "اتصال ذخیره شد؛ Polling واقعی برای آن درخواست شد."
    override val srvConnecting = "در حال اتصال به Agent واقعی..."
    override val srvFingerprintSuggested = "Agent پاسخ داد؛ fingerprint فقط به‌صورت پیشنهادی در فرم قرار گرفت و تا Save pin نمی‌شود."
    override val srvConnectFailed = "اتصال به Agent ناموفق بود."
    override val srvDossierHint = "از اینجا پروندهٔ واقعی سرور باز می‌شود؛ حذف فقط local connection را حذف می‌کند."
    override val srvDeleteTitle = "Delete connection؟"
    override val srvDeleteBody = "اتصال %1 از Prefs حذف می‌شود؛ چیزی روی خود سرور حذف نخواهد شد."
    override val srvLocalDeleted = "اتصال local حذف شد."
    override val delete = "حذف"
    override val upTargetRequired = "Target نمی‌تواند خالی باشد."
    override val upKeywordRequired = "برای KEYWORD باید keyword واقعی وارد شود."
    override val upSaved = "Monitor ذخیره شد؛ check بعدی توسط UptimeEngine انجام می‌شود."
    override val upTargetFirst = "ابتدا target را وارد کنید."
    override val upCheckDone = "Check واقعی انجام شد: %1 · %2 ms"
    override val upCheckFailed = "Check ناموفق بود."
    override val upNoMonitorYet = "هنوز Monitor واقعی ذخیره نشده است."
    override val upEditorBody = "URL/HTTP و SSL از engine واقعی استفاده می‌کنند؛ target نمونه یا synthetic result ساخته نمی‌شود."
    override val upDeleteTitle = "Delete monitor؟"
    override val upDeleteBody = "%1 و heartbeat/incidentهای محلی آن حذف می‌شوند."
    override val upDeleted = "Monitor حذف شد."
    override val and = "و"
    override val serversSearchPlaceholder = "جستجوی نام یا Host"
    override val tunnelsBody = "ساخت و ویرایش تنظیمات از همین مسیر انجام می‌شود؛ Save محلی است و Deploy جداگانه تأیید می‌خواهد."
    override val devLabBody = "این ابزار هیچ input ساختگی مصرف نمی‌کند؛ خروجی با SecureRandom و UUID واقعی تولید می‌شود."
    override val vaultConfigUnparseable = "Config قابل parse نیست"
    override val hostKeysStatus = "%d entries · Trust Store فعال است"
    override val sftpBody = "SFTP از Trust Store محلی استفاده می‌کند؛ اولین کلید سرور به‌صورت TOFU ثبت می‌شود و تغییر بعدی اتصال را رد می‌کند."
    override val netNoHost = "Host یا domain وارد نشده است."
    override val netNoOpenPorts = "هیچ پورت باز از مجموعهٔ common portها پاسخ نداد."
    override val netOnlyAnsweredPorts = "فقط پورت‌هایی که واقعاً پاسخ دادند نمایش داده می‌شوند."
    override val netProbeBody = "Probe از دستگاه فعلی اجرا می‌شود؛ نتیجه فقط پس از پاسخ واقعی شبکه نمایش داده می‌شود."
    override val svcNoPassword = "رمز SSH وارد نشده است."
    override val svcFetching = "در حال دریافت سرویس‌ها"
    override val svcReadingJournal = "در حال خواندن Journal"
    override val svcRunningAction = "در حال اجرای %s"
    override val svcConfirmTitle = "تأیید %1 روی %2"
    override val svcConfirmBody = "این عملیات مستقیماً روی سرویس واقعی سرور اجرا می‌شود. خروجی خام SSH بعد از اجرا نمایش داده خواهد شد."
    override val cancel = "لغو"
    override val hubObserveBody = "از سؤال عملیاتی شروع کنید؛ ابزار فقط در Context لازم باز می‌شود."
    override val hubFileTransfer = "انتقال فایل"
    override val hubInspectGenerate = "بررسی و تولید"
    override val hubDevTools = "ابزارهای توسعه"
    override val hubProtectBody = "اطلاعات حساس، اعلان‌ها و بازیابی داده در یک فضای جدا از عملیات عادی."
    override val hubSecretTrust = "Secret و Trust"
    override val hubAlertDelivery = "تحویل هشدار"
    override val hubDataRecovery = "داده و بازیابی"
    override val hubAppBehaviour = "رفتار برنامه"
    override val bandwidth = "سنجش پهنای باند"
    override val bandwidthBody = "دانلود و آپلود واقعی ۱۰۰ مگابایت از/به ایجنت، به‌همراه تأخیر، جیتر و افت بسته."
    override val bandwidthRunning = "در حال سنجش…"
    override val bandwidthResult = "نتیجهٔ سنجش"
    override val bandwidthUnsupported = "ایجنت این سرور از سنجش پهنای باند پشتیبانی نمی‌کند؛ ایجنت را به‌روزرسانی کنید."
    override val download = "دانلود"
    override val upload = "آپلود"
    override val jitter = "جیتر"
    override val packetLoss = "افت بسته"
    override val networkError = "خطای شبکه"
    override val security = "امنیت سرور"
    override val securityFirewall = "دیوار آتش"
    override val securityFirewallBody = "وضعیت ufw یا iptables و باز کردن پورت"
    override val securityInspect = "بررسی دیوار آتش"
    override val securityPortToAllow = "پورت"
    override val securityAllow = "باز کن"
    override val securityPortOpened = "پورت %d باز شد"
    override val securityBadPort = "شمارهٔ پورت معتبر نیست (۱ تا ۶۵۵۳۵)"
    override val securitySshPassword = "رمز SSH"
    override val securityNeedPassword = "رمز SSH را وارد کنید"
    override val securityBans = "مسدودی‌های fail2ban"
    override val securityBansBody = "فهرست زندان‌ها و آدرس‌های مسدودشده"
    override val securityNoBans = "هیچ آدرسی مسدود نیست"
    override val securityNoBansBody = "fail2ban در حال اجراست ولی زندان‌ها خالی‌اند."
    override val securityNoFail2ban = "fail2ban روی این سرور نصب یا فعال نیست."
    override val securityUnban = "آزاد کن"
    override val securityUnbanned = "آدرس %s آزاد شد"
    override val securityBadValue = "آدرس IP یا نام زندان نامعتبر است"
    override val radarSyncAll = "همگام‌سازی پایش‌ها"
    override val radarSyncAllBody = "همهٔ پایش‌های آپ‌تایم را به این ایجنت می‌فرستد تا از دید سرور هم بررسی شوند."
    override val radarSynced = "%d پایش همگام شد"
    override val radarNoTargets = "پایشی برای همگام‌سازی نیست"
    override val radarNoTargetsBody = "ابتدا در «پایش دسترس‌پذیری» یک پایش بسازید."
    override val vantagePhone = "گوشی"
}

/** English. Every key is stated explicitly — nothing is inherited. */
internal object CommandCopyEn : CommandCopy {
    override val observe = "Observe"
    override val fleet = "Fleet"
    override val operate = "Operate"
    override val diagnose = "Diagnose"
    override val workbench = "Workbench"
    override val protect = "Protect"
    override val overview = "Overview"
    override val incidents = "Incidents"
    override val servers = "Servers"
    override val serverDossier = "Server dossier"
    override val manageServers = "Manage connections"
    override val tunnels = "Tunnels"
    override val docker = "Docker"
    override val processes = "Processes"
    override val services = "Services"
    override val radar = "Server Radar"
    override val uptime = "Uptime"
    override val networkTools = "Network tools"
    override val dns = "DNS and Cloudflare"
    override val ssh = "SSH"
    override val batch = "Batch execution"
    override val sftp = "SFTP"
    override val singlePort = "Single-Port"
    override val proxy = "Proxy Inspector"
    override val developerLab = "Developer Lab"
    override val vault = "Vault"
    override val alerts = "Alert delivery"
    override val backup = "Backup and Restore"
    override val settings = "Settings"
    override val allSystems = "All systems"
    override val sync = "Sync"
    override val refreshing = "Refreshing"
    override val refresh = "Refresh"
    override val addServer = "Add server"
    override val addTunnel = "Add tunnel"
    override val addMonitor = "Add monitor"
    override val attention = "Needs attention"
    override val healthy = "Healthy"
    override val offline = "Offline"
    override val waitingForData = "Waiting for real data"
    override val noServersTitle = "No server is connected yet"
    override val noServersBody = "Connect a real Agent to begin. Sample data is never shown."
    override val openServer = "Open dossier"
    override val activeAttention = "Operations queue"
    override val recentActivity = "Recent state"
    override val affectedServers = "Affected servers"
    override val noAttention = "Nothing needs attention"
    override val noAttentionBody = "No abnormal state has been reported by the current Agents."
    override val unknownState = "Unknown"
    override val updated = "Updated"
    override val lastSeen = "Last seen"
    override val latency = "Latency"
    override val cpu = "CPU"
    override val memory = "Memory"
    override val uptimeValue = "Uptime"
    override val load = "Load"
    override val incidentsFromLiveState = "This list is derived from live state and current thresholds; full incident history will be added in the Incident History stage."
    override val noIncidentsTitle = "No active incident"
    override val noIncidentsBody = "Real abnormal state from an Agent or Uptime monitor will appear here."
    override val selectServer = "Select server"
    override val back = "Back"
    override val close = "Close"
    override val openExistingTool = "Open existing capability"
    override val stagedWorkspace = "This workspace will be migrated in the next stage."
    override val language = "Language"
    override val theme = "Theme"
    override val light = "Light"
    override val dark = "Dark"
    override val automatic = "Automatic"
    override val pollInterval = "Poll interval, seconds"
    override val save = "Save"
    override val saved = "Saved"
    override val english = "English"
    override val persian = "فارسی"
    override val currentServer = "Selected server"
    override val noServerSelected = "No server scope"
    override val capabilities = "Capabilities"
    override val notAvailable = "Not available from this Agent"
    override val retry = "Retry"
    override val dataIsStale = "Data is stale"
    override val start = "Start"
    override val stop = "Stop"
    override val restart = "Restart"
    override val test = "Test"
    override val edit = "Edit"
    override val run = "Run"
    override val operationDone = "Operation completed"
    override val operationFailed = "Operation failed"
    override val target = "Target"
    override val host = "Host"
    override val port = "Port"
    override val mode = "Mode"
    override val addTarget = "Add target"
    override val sources = "Probe sources"
    override val runProbe = "Run probe"
    override val online = "Online"
    override val averageCpu = "Average CPU"
    override val averageMemory = "Average memory"
    override val telemetry = "Telemetry"
    override val coverage = "Data coverage"
    override val scoreOutOf = "OUT OF 100"
    override val nodes = "nodes"
    override val pressAgainToExit = "Press back again to exit"
    override val dnsNoToken = "No Cloudflare API token entered."
    override val dnsZonesLoadFailed = "Zones failed to load."
    override val dnsRecordsLoadFailed = "Records failed to load."
    override val dnsRecordFieldsRequired = "Zone, name and content are required."
    override val dnsRecordCreated = "Record created."
    override val dnsRecordSaveFailed = "Saving the record failed."
    override val dnsLookupFailed = "DNS lookup failed."
    override val dnsTokenSaved = "Token stored in SecureStorage; press refresh to load the zones."
    override val dnsTokenBody = "The token is never shown in the UI, and no fake zone or record is created."
    override val dnsNoZonesYet = "Real zones appear here once a token is entered."
    override val dnsZoneEmpty = "This zone has no real records, or they have not loaded yet."
    override val dnsNoPublicAnswer = "No public DNS answer was received for this domain."
    override val dnsDeleteTitle = "Delete DNS record?"
    override val dnsRecordDeleted = "Record deleted."
    override val dnsRecordDeleteFailed = "Deleting the record failed."
    override val dnsRecordUpdated = "Record updated."
    override val dnsRecordsLoaded = "%1 real records received."
    override val dnsDeleteBody = "%1 %2 will really be deleted from Cloudflare."
    override val setPollRange = "Poll interval must be between 5 and 3600 seconds."
    override val setPollSaved = "Poll interval saved; it applies from the next poll."
    override val setBody = "Controls behaviour, observability and this device's trust level."
    override val setAppearanceBody = "Changes in this section apply to the shell immediately."
    override val setPollBody = "The real agent request interval and its limits."
    override val setDangerBody = "These actions touch encrypted data or SSH trust anchors."
    override val setPurge = "Purge"
    override val setTrustEmpty = "The trust store is empty."
    override val setRuntimeBody = "Runtime information; no synthetic status is produced in this section."
    override val setConnectivityBody = "For a full connectivity check use the SSH, SFTP and Probe tools in the workbench."
    override val setResetVaultBody = "This deletes the master password, the canary and every encrypted vault note. It cannot be undone."
    override val setPurgeTrustBody = "Every stored SSH host key is deleted; the next connection to each server needs trust again."
    override val setVaultReady = "Secrets cannot be read without the master password."
    override val setVaultLockedHint = "Unlock the vault before storing a secret."
    override val setResetVaultTitle = "Reset vault?"
    override val setPurgeTrustTitle = "Purge trust store?"
    override val setResetVaultAction = "Delete vault"
    override val setPurgeTrustAction = "Delete trust entries"
    override val setTrustCount = "%1 host keys recorded; fingerprints are not secrets."
    override val tunSaved = "Tunnel configuration saved; no deploy or remote change has happened yet."
    override val tunCodeGenerated = "Code generated from the real configuration, and a stable token stored."
    override val tunCodeFailed = "Generating the code failed."
    override val tunDeploying = "Deploying through the real agent…"
    override val tunDeployFailed = "Deploy failed."
    override val tunProbeFailed = "Probe failed."
    override val tunActionFailedGeneric = "The remote action failed."
    override val tunEmptyBody = "No tunnel has been saved yet. The form below is ready to create the first configuration."
    override val tunDiscoveryBody = "This tunnel came from discovery; deploy stays blocked without a real token."
    override val tunActionsBody = "These actions are sent to the real agents whose host matches the endpoints."
    override val tunDeleteBody = "The local record for this tunnel is deleted, and a remote delete is also sent to the matched agents."
    override val tunDeleted = "Tunnel record deleted."
    override val tunTokenEmpty = "token is empty"
    override val tunTokenSet = "token entered"
    override val tunDeleteTitle = "Delete tunnel?"
    override val tunDeployTitle = "Deploy tunnel?"
    override val tunDeployBody = "This runs on real agents and may change the services on both ends."
    override val tunActionDone = "Remote action %1 completed."
    override val tunActionFailed = "Remote action %1 failed."
    override val deploy = "Deploy"
    override val vaultBadMaster = "The master password is incorrect."
    override val vaultUnlockFailed = "Unlocking the vault failed."
    override val vaultSaveFailed = "Saving the vault failed."
    override val lock = "Lock"
    override val vaultOpen = "Vault is unlocked"
    override val vaultLocked = "Vault is locked"
    override val vaultOpenBody = "Secrets are visible only in the current session."
    override val vaultLockedBody = "No secret is shown while the vault is locked."
    override val vaultLockNow = "Lock now"
    override val vaultUnlock = "Unlock vault"
    override val vaultSecrets = "Stored secrets"
    override val vaultAddSecret = "Add secret"
    override val vaultEmpty = "The vault is empty"
    override val vaultEmptyBody = "Add a real secret only after unlocking."
    override val hide = "Hide"
    override val revealTemporarily = "Reveal temporarily"
    override val copySecret = "Copy secret"
    override val deleteSecret = "Delete secret"
    override val secretTitle = "Title"
    override val secretContent = "Sensitive content"
    override val backupCreate = "Create backup"
    override val backupBody = "A backup is readable without a password; use one for real data."
    override val backupPasswordOptional = "Password (optional)"
    override val backupCreateEncrypted = "Create encrypted backup"
    override val backupCreated = "Backup created. Keep its value somewhere safe off this device."
    override val backupOutput = "Backup output"
    override val backupVerifyRestore = "Verify & restore"
    override val backupText = "Backup text"
    override val backupValid = "Valid structure"
    override val backupInvalid = "Invalid structure"
    override val alertsChannelsBody = "Real channels and triggers"
    override val alertsEnableTelegram = "Enable Telegram"
    override val alertsEnableDiscord = "Enable Discord"
    override val alertsTriggers = "Triggers"
    override val alertsTriggerServerDown = "Server went down"
    override val alertsTriggerCpuSpike = "CPU or memory spike"
    override val alertsTriggerTunnelDown = "Tunnel went down"
    override val backupSummary = "%1 servers · %2 tunnels · %3 monitors · %4"
    override val netIndexBody = "Start with the diagnostic question, not a tool list."
    override val netQReachable = "Is the target reachable from this server?"
    override val netQReachableTools = "TCP, HTTP, SSL and Check-Host"
    override val netQNetworkLayer = "Is the problem in the network layer?"
    override val netQNetworkLayerTools = "DPI, port scanner and TCP ping"
    override val netQTls = "Is the TLS identity valid?"
    override val netQTlsTools = "Subject, chain, SAN and fingerprint"
    override val netQDns = "What does DNS return?"
    override val netQDnsTools = "Records and Cloudflare"
    override val netQQuality = "What is the connection quality?"
    override val netQQualityTools = "Latency, loss, jitter and bandwidth"
    override val dnsIndexBody = "DNS diagnosis is separate from record management."
    override val dnsZoneRecords = "Manage zones and records"
    override val dnsZoneRecordsBody = "Create, edit and delete real Cloudflare records"
    override val dnsInspect = "Inspect DNS resolution"
    override val dnsInspectBody = "Resolve, reverse DNS and public records"
    override val copyAction = "Copy"
    override val selected = "Selected"
    override val notSelected = "Not selected"
    override val batchSharedPassword = "Shared SSH password"
    override val batchSummary = "%1 succeeded · %2 failed"
    override val succeeded = "Succeeded"
    override val failed = "Failed"
    override val noOutput = "No output"
    override val hostKeyChanged = "The SSH host key has changed"
    override val hostKeyNew = "Trust the new SSH host key"
    override val hostKeyChangedBody = "The presented key does not match the trust store. Only reset trust after verifying it independently."
    override val hostKeyFirstBody = "This is the first connection. The fingerprint below will be stored."
    override val resetTrust = "Reset trust"
    override val trustAndConnect = "Trust and connect"
    override val srvNameRequired = "Connection name is required."
    override val srvHostInvalid = "Host must be a valid hostname or IPv4 address."
    override val srvTokenRequired = "Agent token is required."
    override val srvFingerprintInvalid = "TLS fingerprint is not valid."
    override val srvSavedPolled = "Connection saved; a real poll was requested for it."
    override val srvConnecting = "Connecting to the real agent…"
    override val srvFingerprintSuggested = "The agent replied; the fingerprint is only suggested in the form and is not pinned until you save."
    override val srvConnectFailed = "Connecting to the agent failed."
    override val srvDossierHint = "The server's real dossier opens from here; delete only removes the local connection."
    override val srvDeleteTitle = "Delete connection?"
    override val srvDeleteBody = "The connection to %1 is removed from local preferences; nothing is deleted on the server itself."
    override val srvLocalDeleted = "Local connection deleted."
    override val delete = "Delete"
    override val upTargetRequired = "Target cannot be empty."
    override val upKeywordRequired = "A KEYWORD monitor needs a real keyword."
    override val upSaved = "Monitor saved; the next check runs in UptimeEngine."
    override val upTargetFirst = "Enter a target first."
    override val upCheckDone = "Real check completed: %1 · %2 ms"
    override val upCheckFailed = "The check failed."
    override val upNoMonitorYet = "No real monitor has been saved yet."
    override val upEditorBody = "URL/HTTP and SSL use the real engine; no sample target or synthetic result is produced."
    override val upDeleteTitle = "Delete monitor?"
    override val upDeleteBody = "%1 and its local heartbeats and incidents are deleted."
    override val upDeleted = "Monitor deleted."
    override val and = "and"
    override val serversSearchPlaceholder = "Search name or host"
    override val tunnelsBody = "Create and edit tunnel configuration from here. Save is local; deploy asks for separate confirmation."
    override val devLabBody = "This tool consumes no synthetic input: output comes from SecureRandom and real UUIDs."
    override val vaultConfigUnparseable = "Config cannot be parsed"
    override val hostKeysStatus = "%d entries · trust store active"
    override val sftpBody = "SFTP uses the local trust store: the first server key is recorded as TOFU and a later change rejects the connection."
    override val netNoHost = "No host or domain entered."
    override val netNoOpenPorts = "No port in the common set responded."
    override val netOnlyAnsweredPorts = "Only ports that actually responded are shown."
    override val netProbeBody = "The probe runs from this device; results appear only after a real network response."
    override val svcNoPassword = "No SSH password entered."
    override val svcFetching = "Fetching services"
    override val svcReadingJournal = "Reading journal"
    override val svcRunningAction = "Running %s"
    override val svcConfirmTitle = "Confirm %1 on %2"
    override val svcConfirmBody = "This runs directly on the server's real service. The raw SSH output is shown after it completes."
    override val cancel = "Cancel"
    override val hubObserveBody = "Start from an operational question; tools open only in the context that needs them."
    override val hubFileTransfer = "File transfer"
    override val hubInspectGenerate = "Inspect & generate"
    override val hubDevTools = "Developer tools"
    override val hubProtectBody = "Secrets, alerts and data recovery live in a space separate from routine operations."
    override val hubSecretTrust = "Secrets & trust"
    override val hubAlertDelivery = "Alert delivery"
    override val hubDataRecovery = "Data & recovery"
    override val hubAppBehaviour = "App behaviour"
    override val bandwidth = "Bandwidth benchmark"
    override val bandwidthBody = "A real 100 MiB download from and upload to the agent, plus latency, jitter and packet loss."
    override val bandwidthRunning = "Measuring…"
    override val bandwidthResult = "Benchmark result"
    override val bandwidthUnsupported = "This server's agent does not support the bandwidth benchmark; update the agent."
    override val download = "Download"
    override val upload = "Upload"
    override val jitter = "Jitter"
    override val packetLoss = "Packet loss"
    override val networkError = "Network error"
    override val security = "Server security"
    override val securityFirewall = "Firewall"
    override val securityFirewallBody = "ufw or iptables state, and opening a port"
    override val securityInspect = "Inspect firewall"
    override val securityPortToAllow = "Port"
    override val securityAllow = "Allow"
    override val securityPortOpened = "Port %d opened"
    override val securityBadPort = "Invalid port number (1-65535)"
    override val securitySshPassword = "SSH password"
    override val securityNeedPassword = "Enter the SSH password"
    override val securityBans = "fail2ban bans"
    override val securityBansBody = "The jails and the addresses they have banned"
    override val securityNoBans = "No banned addresses"
    override val securityNoBansBody = "fail2ban is running but its jails are empty."
    override val securityNoFail2ban = "fail2ban is not installed or running on this server."
    override val securityUnban = "Unban"
    override val securityUnbanned = "Unbanned %s"
    override val securityBadValue = "Invalid IP address or jail name"
    override val radarSyncAll = "Sync monitors"
    override val radarSyncAllBody = "Pushes every uptime monitor to this agent so each one is also checked from the server's vantage point."
    override val radarSynced = "Synced %d monitors"
    override val radarNoTargets = "No monitors to sync"
    override val radarNoTargetsBody = "Create a monitor under Uptime first."
    override val vantagePhone = "Phone"
}
