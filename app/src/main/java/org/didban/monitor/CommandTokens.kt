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
    canvas = Color(0xFFEDF2F5),
    surface = Color(0xFFFCFDFE),
    surfaceRaised = Color(0xFFFFFFFF),
    border = Color(0xFFD7E1E9),
    borderStrong = Color(0xFFA3B5C4),
    textPrimary = Color(0xFF1C2C3A),
    textSecondary = Color(0xFF506478),
    textTertiary = Color(0xFF506478),
    accent = Color(0xFF2364A6),
    onAccent = Color(0xFFFFFFFF),
    success = Color(0xFF176B50),
    successSurface = Color(0xFFE7F4ED),
    warning = Color(0xFF8C5B15),
    warningSurface = Color(0xFFFFF4DF),
    danger = Color(0xFFB33348),
    dangerSurface = Color(0xFFFCEEF0),
    info = Color(0xFF2364A6),
    infoSurface = Color(0xFFE3EFF8),
    violet = Color(0xFF2364A6),
    track = Color(0xFFE1E9F0),
    focus = Color(0xFF2364A6)
)

val CommandDarkPalette = CommandPalette(
    canvas = Color(0xFF10151C),
    surface = Color(0xFF19222C),
    surfaceRaised = Color(0xFF222F3A),
    border = Color(0xFF34434F),
    borderStrong = Color(0xFF6F8899),
    textPrimary = Color(0xFFECF2F6),
    textSecondary = Color(0xFFAFBECA),
    textTertiary = Color(0xFFAFBECA),
    accent = Color(0xFF91CEDF),
    onAccent = Color(0xFF102831),
    success = Color(0xFF92D5B9),
    successSurface = Color(0xFF1C392F),
    warning = Color(0xFFEDC787),
    warningSurface = Color(0xFF3C3222),
    danger = Color(0xFFF1A6B1),
    dangerSurface = Color(0xFF402A32),
    info = Color(0xFF91CEDF),
    infoSurface = Color(0xFF263D4A),
    violet = Color(0xFF91CEDF),
    track = Color(0xFF344550),
    focus = Color(0xFF91CEDF)
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
        primaryContainer = infoSurface,
        onPrimaryContainer = textPrimary,
        secondary = info,
        secondaryContainer = infoSurface,
        onSecondaryContainer = textPrimary,
        tertiary = info,
        onTertiary = onAccent,
        tertiaryContainer = infoSurface,
        onTertiaryContainer = textPrimary,
        error = danger,
        onError = if (dark) canvas else Color.White,
        errorContainer = dangerSurface,
        onErrorContainer = textPrimary,
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
        secondaryContainer = infoSurface,
        onSecondaryContainer = textPrimary,
        tertiary = info,
        onTertiary = onAccent,
        tertiaryContainer = infoSurface,
        onTertiaryContainer = textPrimary,
        error = danger,
        onError = if (dark) canvas else Color.White,
        errorContainer = dangerSurface,
        onErrorContainer = textPrimary,
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
    val uiVaultInitialized: String
    val uiVaultNotInitialized: String
    val uiServers: String
    val uiMyServers: String
    val uiMonitoringIntro: String
    val uiMonitoring: String
    val uiTools: String
    val uiServerIntro: String
    val uiToolsIntro: String
    val uiPathTools: String
    val uiNetworkTools: String
    val uiMoreTools: String
    val uiServerTools: String
    val uiScopedTools: String
    val uiResources: String
    val uiAdvanced: String
    val uiAppearance: String
    val uiPreferences: String
    val uiDataSafety: String
    val uiSecuritySettings: String
    val uiComparison: String
    val uiComparisonHint: String
    val uiServerToolsHint: String
    val uiUnknown: String
    val uiSourceSelection: String
    val uiReadyScan: String
    val uiName: String
    val uiUser: String
    val uiSshPassword: String
    val uiCommand: String
    val uiInput: String
    val uiManualFields: String
    val uiNoData: String

    val observe: String
    val fleet: String
    val operate: String
    val diagnose: String
    val workbench: String
    val protect: String
    val overview: String
    val incidents: String
    val servers: String
    val fleetAll: String
    val fleetSummary: String
    val fleetDetails: String
    val fleetNoMatches: String
    val fleetClearFilters: String
    val fleetDiscardTitle: String
    val fleetDiscardBody: String
    val fleetDiscard: String
    val fleetRecordChanged: String
    val fleetMissing: String
    val fleetPortInvalid: String
    val fleetThresholdInvalid: String
    val fleetName: String
    val fleetDraftPrivacy: String
    val fleetTestVerified: String
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
    val refresh: String
    val backTo: String
    val refreshing: String
    val refreshComplete: String
    val refreshFailed: String
    val refreshPartial: String
    val refreshTimedOut: String
    val refreshInterrupted: String
    val refreshEmpty: String
    val refreshMetrics: String
    val refreshPending: String
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
    val noAttention: String
    val noAttentionBody: String
    val unknownState: String
    val updated: String
    val lastSeen: String
    val latency: String
    val cpu: String
    val memory: String
    val load: String
    val incidentsFromLiveState: String
    val noIncidentsTitle: String
    val noIncidentsBody: String
    val selectServer: String
    val back: String
    val close: String
    val language: String
    val theme: String
    val light: String
    val dark: String
    val automatic: String
    val save: String
    val saved: String
    val english: String
    val persian: String
    val currentServer: String
    val noServerSelected: String
    val capabilities: String
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
    val netQCfEdge: String
    val netQCfEdgeTools: String
    val netQRealityDonor: String
    val netQRealityDonorTools: String
    val cfScanner: String
    val scannerReadyLists: String
    val scannerBuiltIn: String
    val scannerManual: String
    val scannerCfReadyHint: String
    val scannerSniReadyHint: String
    val scannerShowList: String
    val scannerHideList: String
    val scannerSelectAll: String
    val scannerClearSelection: String
    val scannerImportFile: String
    val scannerImportFailed: String
    val scannerCategory: String
    val scannerCategoryAll: String
    val scannerCategoryTechnology: String
    val scannerCategoryDevelopment: String
    val scannerCategoryKnowledge: String
    val scannerCategoryServices: String
    val scannerLimit: String
    val scannerPlanSummary: String
    val scannerRangeSummary: String
    val scannerSniListHint: String
    val scannerInputSummary: String
    val scannerChooseRange: String
    val scannerEditList: String
    val scannerSnapshot: String
    val cfScannerBody: String
    val cfRandom: String
    val cfCustomList: String
    val cfCustomListHint: String
    val cfSettings: String
    val cfSettingsHint: String
    val cfCount: String
    val cfTries: String
    val cfTimeout: String
    val cfConcurrency: String
    val cfSniOverride: String
    val cfStart: String
    val cfStop: String
    val cfStopped: String
    val cfProgress: String
    val cfResults: String
    val cfCopyBest: String
    val cfCopied: String
    val cfNoResults: String
    val cfNoResultsBody: String
    val cfNoAddresses: String
    val cfLoss: String
    val cfColo: String
    val cfTlsOk: String
    val realitySni: String
    val realityBody: String
    val realityDomain: String
    val realityCheck: String
    val realityCheckAll: String
    val realityDiscouraged: String
    val realityResults: String
    val realityProgress: String
    val realityScore: String
    val realityGood: String
    val realityUsable: String
    val realityRisky: String
    val realityReject: String
    val realityBlockers: String
    val realityWarnings: String
    val realityNotes: String
    val realityBadTarget: String
    val realityResolved: String
    val realityTiming: String
    val realityDns: String
    val realityTcp: String
    val realityTls: String
    val realityTotal: String
    val realityTlsDetails: String
    val realityTlsVersion: String
    val realityAlpn: String
    val realityCipher: String
    val realityNotNegotiated: String
    val realityCert: String
    val realityCertValid: String
    val realitySniInSan: String
    val realityCertIssuer: String
    val realityCertDays: String
    val realityCertKey: String
    val realityCertChain: String
    val realityHttp: String
    val realityHttpStatus: String
    val realityRedirectTo: String
    val realityCdn: String
    val realityCloudflare: String
    val realityServerHeader: String
    val realityYes: String
    val realityNo: String
    val metricUptime: String
    val metricLoad: String
    val metricDisks: String
    val metricInterfaces: String
    val metricConfigs: String
    val metricUsed: String
    val metricTotal: String
    val metricExpire: String
    val netRegionLabel: String
    val netDnsRecordsLabel: String
    val svcLoadHint: String
    val svcSelectedServer: String
    val backupRestoreAction: String
    val backupModeMerge: String
    val backupModeOverwrite: String
    val netDomainLabel: String
    val netReverseDnsLabel: String
    val netIspLabel: String
    val netAsnLabel: String
    val netDiagFailed: String
    val dnsNoPtr: String
    val tunEditingId: String
    val upMonitorConfiguration: String
    val tunConfiguration: String
    val dnsRecordsSubtitle: String
    val dnsConnection: String
    val dnsApiToken: String
    val dnsSaveToken: String
    val dnsLoadZones: String
    val dnsCreateRecord: String
    val dnsEditRecord: String
    val dnsSaveRecord: String
    val dnsDiagnosis: String
    val dnsDomainOrIp: String
    val netLocalProbe: String
    val netProbeInput: String
    val netHostDomain: String
    val netOpenPorts: String
    val vaultMasterPassword: String
    val backupPreviewRestore: String
    val backupPassword: String
    val alertsBotToken: String
    val alertsChatId: String
    val alertsTestTelegram: String
    val alertsDiscordWebhook: String
    val alertsWebhookUrl: String
    val alertsTestDiscord: String
    val svcFilter: String
    val svcLoad: String
    val svcNoLiveData: String
    val svcOnlySystemdUnits: String
    val svcRawSsh: String
    val setPollIntervalSec: String
    val setResetVault: String
    val monitorDetails: String
    val monitorTitle: String
    val monitorOff: String
    val monitorOn: String
    val monitorStarting: String
    val monitorStopping: String
    val monitorFailed: String
    val monitorStart: String
    val monitorStop: String
    val monitorBody: String
    val monitorTargets: String
    val monitorNoTargets: String
    val monitorLimits: String
    val monitorNotification: String
    val monitorPermission: String
    val monitorPermissionSettings: String
    val monitorPermissionPending: String
    val monitorStartError: String
    val monitorStopError: String
    val monitorEngineError: String
    val monitorSettingsError: String
    val upNewMonitor: String
    val upAllowPrivateTitle: String
    val upAllowPrivateBody: String
    val upEditing: String
    val upPause: String
    val upResume: String
    val upSavedMonitors: String
    val upMonitorContract: String
    val upProbeType: String
    val upTargetUrlHost: String
    val upIntervalSeconds: String
    val upKeywordHint: String
    val upTestNow: String
    val wbFleetScope: String
    val wbResultMatrix: String
    val netRealResponses: String
    val upIncidentCount: String
    val setHostKeyStoreInit: String
    val wtLocalTools: String
    val wtInputTransform: String
    val wtInvalidInput: String
    val wtArtifactGenerator: String
    val wtRoutingContract: String
    val wtBindPort: String
    val wtInspectSec: String
    val wtPanelSni: String
    val wtSubscriptionSni: String
    val wtRealitySni: String
    val wtFallbackPort: String
    val wtGeneratedArtifact: String
    val wtSingleConfig: String
    val wtParseProbe: String
    val wtCopyNormalized: String
    val wtSubscriptionUrl: String
    val wtFetchSubscription: String
    val wtRemotePath: String
    val wtRemoteEntries: String
    val wtTextEditorMax: String
    val wtSaveRemoteFile: String
    val tunNewConfiguration: String
    val tunSavedTunnels: String
    val tunIdentityEngine: String
    val tunName: String
    val tunIranHost: String
    val tunForeignHost: String
    val tunCorePort: String
    val tunRealToken: String
    val tunGenerateToken: String
    val tunAdvancedParams: String
    val tunKcpMode: String
    val tunWsPath: String
    val tunWsHost: String
    val tunMultiPortHint: String
    val tunSpoofSource: String
    val tunSpoofPeer: String
    val tunVirtualIranIp: String
    val tunVirtualForeignIp: String
    val tunRemoteOps: String
    val tunGeneratedEvidence: String
    val srvNewConnection: String
    val srvEditConnection: String
    val srvAgentHost: String
    val srvAgentToken: String
    val srvAdminToken: String
    val srvQuickConnectHint: String
    val srvQuickConnectLabel: String
    val srvQuickConnectImport: String
    val srvQuickConnectImported: String
    val srvQuickConnectInvalid: String
    val srvUseTls: String
    val srvFingerprintPinning: String
    val srvTlsFingerprint: String
    val srvCpuAlert: String
    val srvMemAlert: String
    val srvTestAgent: String
    val srvConnectionResult: String
    val srvAgentOnline: String
    val srvSavedActions: String
    val srvOpenDossier: String
    val srvSavedCount: String
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
    val setScreenshotProtection: String
    val setScreenshotProtectionHint: String
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
    val srvFetchFingerprint: String
    val srvAcceptFingerprint: String
    val srvFingerprintConfirmTitle: String
    val srvFingerprintConfirmBody: String
    val connFingerprintMismatch: String
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
    val dockerUnsupported: String
    val dockerUnavailable: String
    val agentVersion: String
    val agentOutdated: String
    val agentOutdatedBody: String
    val copyAgentUpdateCommand: String
    val agentUpdateCommandCopied: String
    val agentToolAuthFailed: String
    val agentToolPayloadRejected: String
    val agentToolTimeout: String
    val agentToolDnsFailed: String
    val agentToolTlsFailed: String
    val agentToolUnreachable: String
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
    val radarTargetBody: String
    val radarTargetNameHint: String
    val radarTargetHostHint: String
    val radarTargetIncomplete: String
    val radarNoPointsBody: String
    val radarDivergentBody: String
    val vantagePhone: String

    companion object {
        val fa: CommandCopy = CommandCopyFa
        val en: CommandCopy = CommandCopyEn

        fun forLanguage(language: String): CommandCopy = if (language == "fa") fa else en
    }
}

/** Persian (default). */
internal object CommandCopyFa : CommandCopy {
    override val uiVaultInitialized = "راه‌اندازی شده"
    override val uiVaultNotInitialized = "راه‌اندازی نشده"
    override val uiServers = "سرورها"
    override val uiMyServers = "سرورهای من"
    override val uiMonitoringIntro = "وضعیت سایت‌ها و سرویس‌ها را بررسی کن و دسترسی را از گوشی و سرورها مقایسه کن."
    override val uiMonitoring = "پایش"
    override val uiTools = "ابزارها"
    override val uiServerIntro = "همهٔ سرورها، یک جای مشخص."
    override val uiToolsIntro = "ابزار مناسب را برای کاری که داری انتخاب کن."
    override val uiPathTools = "پیداکردن مسیر مناسب"
    override val uiNetworkTools = "عیب‌یابی شبکه"
    override val uiMoreTools = "ابزارهای تخصصی"
    override val uiServerTools = "مدیریت این سرور"
    override val uiScopedTools = "همهٔ ابزارهای زیر برای «%1» هستند."
    override val uiResources = "وضعیت منابع"
    override val uiAdvanced = "تنظیمات پیشرفته"
    override val uiAppearance = "ظاهر و زبان"
    override val uiPreferences = "تنظیمات برنامه"
    override val uiDataSafety = "هشدار و نگهداری اطلاعات"
    override val uiSecuritySettings = "امنیت برنامه"
    override val uiComparison = "مقایسهٔ دسترسی از سرورها"
    override val uiComparisonHint = "نتیجهٔ یک مقصد را از گوشی و سرورهای خودت مقایسه کن."
    override val uiServerToolsHint = "Docker، SSH و فایل‌ها را از جزئیات سرور موردنظر باز کن."
    override val uiUnknown = "بررسی نشده"
    override val uiSourceSelection = "چه آدرس‌هایی بررسی شوند؟"
    override val uiReadyScan = "فهرست را انتخاب کن و شروع را بزن؛ نتیجه‌ها همین‌جا نمایش داده می‌شوند."
    override val uiName = "نام"
    override val uiUser = "نام کاربری SSH"
    override val uiSshPassword = "رمز SSH"
    override val uiCommand = "فرمان"
    override val uiInput = "ورودی"
    override val uiManualFields = "ورود دستی اطلاعات اتصال"
    override val uiNoData = "هنوز داده‌ای نداریم"

    override val observe = "پایش"
    override val fleet = "ناوگان"
    override val operate = "عملیات"
    override val diagnose = "تشخیص"
    override val workbench = "ابزارها"
    override val protect = "حفاظت"
    override val overview = "نمای کلی"
    override val incidents = "رخدادها"
    override val servers = "سرورها"
    override val fleetAll = "همه"
    override val fleetSummary = "وضعیت زنده و کنترل سرورها در یک‌جا"
    override val fleetDetails = "جزئیات سرور"
    override val fleetNoMatches = "سروری با این جست‌وجو یا فیلتر پیدا نشد."
    override val fleetClearFilters = "پاک کردن جست‌وجو و فیلتر"
    override val fleetDiscardTitle = "تغییرات ذخیره‌نشده بسته شوند؟"
    override val fleetDiscardBody = "تغییرات این فرم هنوز ذخیره نشده‌اند. برای نگه‌داشتن آن‌ها به فرم برگردید."
    override val fleetDiscard = "بستن بدون ذخیره"
    override val fleetRecordChanged = "این اتصال تغییر کرده یا حذف شده است. فرم را ببندید و نسخهٔ تازه را باز کنید؛ چیزی بازنویسی نشد."
    override val fleetMissing = "این سرور دیگر در فهرست وجود ندارد."
    override val fleetPortInvalid = "پورت باید عددی بین ۱ و ۶۵۵۳۵ باشد."
    override val fleetThresholdInvalid = "آستانه‌های CPU و RAM باید بین ۱ و ۱۰۰ باشند."
    override val fleetName = "نام سرور"
    override val fleetDraftPrivacy = "اطلاعات فرم تا زمان ذخیره فقط در حافظه می‌مانند؛ با چرخش صفحه حفظ می‌شوند، اما با بسته‌شدن فرایند اپ ممکن است از دست بروند."
    override val fleetTestVerified = "Agent با اثر انگشت ثبت‌شده پاسخ داد؛ برای نگه‌داشتن تغییرات، ذخیره را بزنید."
    override val serverDossier = "پروندهٔ سرور"
    override val manageServers = "مدیریت اتصال‌ها"
    override val tunnels = "تونل‌ها"
    override val docker = "Docker"
    override val processes = "پردازش‌ها"
    override val services = "سرویس‌ها"
    override val radar = "رادار و مقایسهٔ دسترسی"
    override val uptime = "پایش سایت و سرویس"
    override val networkTools = "ابزارهای شبکه"
    override val dns = "DNS و Cloudflare"
    override val ssh = "SSH"
    override val batch = "اجرای گروهی"
    override val sftp = "فایل‌ها (SFTP)"
    override val singlePort = "Single-Port"
    override val proxy = "Proxy Inspector"
    override val developerLab = "Developer Lab"
    override val vault = "خزانه (Vault)"
    override val alerts = "کانال‌های هشدار"
    override val backup = "پشتیبان‌گیری و بازیابی"
    override val settings = "تنظیمات"
    override val allSystems = "همهٔ سامانه‌ها"
    override val sync = "همگام‌سازی"
    override val refresh = "تازه‌سازی"
    override val backTo = "بازگشت به"
    override val refreshing = "در حال تازه‌سازی…"
    override val refreshComplete = "تازه‌سازی انجام شد"
    override val refreshFailed = "تازه‌سازی ناموفق بود"
    override val refreshPartial = "بخشی از داده‌ها تازه شد"
    override val refreshTimedOut = "مهلت تازه‌سازی تمام شد؛ دوباره تلاش کنید"
    override val refreshInterrupted = "تازه‌سازی متوقف شد؛ دوباره تلاش کنید"
    override val refreshEmpty = "موردی برای تازه‌سازی وجود ندارد"
    override val refreshMetrics = "تازه‌سازی وضعیت سرورها"
    override val refreshPending = "بدون پاسخ"
    override val addServer = "افزودن سرور"
    override val addTunnel = "افزودن تونل"
    override val addMonitor = "افزودن مقصد"
    override val attention = "نیازمند توجه"
    override val healthy = "سالم"
    override val offline = "آفلاین"
    override val waitingForData = "در انتظار دادهٔ واقعی"
    override val noServersTitle = "هنوز سروری متصل نیست"
    override val noServersBody = "برای شروع، یک Agent واقعی را به ناوگان اضافه کنید. دادهٔ نمونه نمایش داده نمی‌شود."
    override val openServer = "جزئیات و مدیریت"
    override val activeAttention = "صف انتظار عملیات"
    override val noAttention = "مورد فوری وجود ندارد"
    override val noAttentionBody = "در دادهٔ فعلی، وضعیت نیازمند توجهی از Agentها دریافت نشده است."
    override val unknownState = "نامشخص"
    override val updated = "به‌روزرسانی"
    override val lastSeen = "آخرین مشاهده"
    override val latency = "تأخیر"
    override val cpu = "CPU"
    override val memory = "حافظه"
    override val load = "Load"
    override val incidentsFromLiveState = "این فهرست از وضعیت زنده و Thresholdهای فعلی ساخته شده است؛ تاریخچهٔ کامل رخداد در مرحلهٔ Incident History اضافه می‌شود."
    override val noIncidentsTitle = "رخداد فعالی ثبت نشده است"
    override val noIncidentsBody = "وقتی Agent یا Uptime وضعیت غیرعادی واقعی گزارش کند، اینجا قابل پیگیری خواهد بود."
    override val selectServer = "انتخاب سرور"
    override val back = "بازگشت"
    override val close = "بستن"
    override val language = "زبان"
    override val theme = "تم"
    override val light = "روشن"
    override val dark = "تیره"
    override val automatic = "خودکار"
    override val save = "ذخیره"
    override val saved = "ذخیره شد"
    override val english = "English"
    override val persian = "فارسی"
    override val currentServer = "سرور انتخاب‌شده"
    override val noServerSelected = "بدون محدودهٔ سرور"
    override val capabilities = "قابلیت‌ها"
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
    override val netQCfEdge = "اسکنر IP تمیز کلودفلر"
    override val netQCfEdgeTools = "پیدا کردن تمیزترین لبه از این موقعیت"
    override val netQRealityDonor = "بررسی دونر REALITY"
    override val netQRealityDonorTools = "TLS 1.3، h2، SAN، redirect، CDN"
    override val cfScanner = "اسکنر IP تمیز کلودفلر"
    override val scannerReadyLists = "فهرست‌های آمادهٔ اسکن"
    override val scannerBuiltIn = "فهرست آماده"
    override val scannerManual = "فهرست شخصی"
    override val scannerCfReadyHint = "از رنج‌های رسمی IPv4 کلودفلر نمونه‌گیری می‌شود، نه تمام آدرس‌ها. تمیزبودن IP به شبکهٔ فعلی شما بستگی دارد."
    override val scannerSniReadyHint = "این دامنه‌ها فقط کاندید آزمایش‌اند؛ ممکن است به‌دلیل TLS، گواهی یا CDN رد شوند. نتیجهٔ گوشی تضمینِ کارکرد از سرور نیست. اسکن فقط با شروع شما انجام می‌شود؛ حداکثر ۴ آزمایش هم‌زمان."
    override val scannerShowList = "مشاهدهٔ فهرست"
    override val scannerHideList = "بستن فهرست"
    override val scannerSelectAll = "انتخاب همه"
    override val scannerClearSelection = "لغو انتخاب همه"
    override val scannerImportFile = "واردکردن فایل متنی"
    override val scannerImportFailed = "فایل خوانده نشد. یک فایل متنی UTF-8 با حجم حداکثر ۶۴ KiB انتخاب کنید."
    override val scannerCategory = "دسته‌بندی"
    override val scannerCategoryAll = "همهٔ دسته‌ها"
    override val scannerCategoryTechnology = "فناوری و سخت‌افزار"
    override val scannerCategoryDevelopment = "توسعه و متن‌باز"
    override val scannerCategoryKnowledge = "دانش و رسانه"
    override val scannerCategoryServices = "خدمات و فروشگاه‌ها"
    override val scannerLimit = "حداکثر تعداد اسکن"
    override val scannerPlanSummary = "در این اجرا: %1 از %2 کاندید"
    override val scannerRangeSummary = "%1 از %2 رنج انتخاب شده"
    override val scannerSniListHint = "هر خط یک دامنه یا دامنه:پورت؛ # برای توضیح. موارد تکراری حذف می‌شوند."
    override val scannerInputSummary = "%1 مقصد معتبر؛ %2 خط نامعتبر. سقف اجرا: %3"
    override val scannerChooseRange = "حداقل یک رنج انتخاب کنید."
    override val scannerEditList = "کپی در فهرست شخصی"
    override val scannerSnapshot = "فهرست داخلی: %1؛ بدون به‌روزرسانی خودکار"
    override val cfScannerBody = "IPهای کلودفلر را روی اینترنت فعلی‌ات آزمایش کن؛ نتیجه روی شبکه‌های دیگر ممکن است متفاوت باشد."
    override val cfRandom = "رنج‌های آمادهٔ کلودفلر"
    override val cfCustomList = "فهرست دلخواه"
    override val cfCustomListHint = "هر خط یک IP یا CIDR؛ # برای توضیح"
    override val cfSettings = "تنظیمات اسکن"
    override val cfSettingsHint = "مقادیر از پیش محدود شده‌اند تا اسکن به خود دستگاه یا شبکهٔ شما آسیب نزند."
    override val cfCount = "تعداد آدرس"
    override val cfTries = "تلاش هر آدرس"
    override val cfTimeout = "مهلت (ms)"
    override val cfConcurrency = "همزمانی"
    override val cfSniOverride = "SNI دلخواه"
    override val cfStart = "شروع اسکن"
    override val cfStop = "توقف اسکن"
    override val cfStopped = "اسکن متوقف شد"
    override val cfProgress = "%1 از %2 بررسی شد · %3 سالم"
    override val cfResults = "IPهای تمیز"
    override val cfCopyBest = "کپی ۲۰ مورد برتر"
    override val cfCopied = "%1 آدرس کپی شد"
    override val cfNoResults = "هیچ IP تمیزی پیدا نشد"
    override val cfNoResultsBody = "هیچ لبه‌ای از این شبکه handshake را کامل نکرد. تعداد آدرس یا مهلت را بیشتر کنید، یا یک فهرست دلخواه بدهید."
    override val cfNoAddresses = "هیچ آدرسی برای بررسی نیست"
    override val cfLoss = "افت"
    override val cfColo = "دیتاسنتر"
    override val cfTlsOk = "TLS کامل شد"
    override val realitySni = "اسکنر SNI برای REALITY"
    override val realityBody = "آیا این دامنه دونر معتبری برای dest/serverNames در REALITY هست یا نه، و چرا."
    override val realityDomain = "دامنهٔ دونر"
    override val realityCheck = "بررسی دونر"
    override val realityCheckAll = "شروع اسکن فهرست"
    override val realityDiscouraged = "دونرهای نامناسب"
    override val realityResults = "نتایج"
    override val realityProgress = "%1 از %2 بررسی شد · %3 قابل استفاده"
    override val realityScore = "امتیاز"
    override val realityGood = "مناسب"
    override val realityUsable = "قابل استفاده"
    override val realityRisky = "پرریسک"
    override val realityReject = "رد"
    override val realityBlockers = "مانع‌ها"
    override val realityWarnings = "هشدارها"
    override val realityNotes = "یادداشت‌ها"
    override val realityBadTarget = "دامنه معتبر نیست؛ یک IP نمی‌تواند SNI باشد"
    override val realityResolved = "آدرس مقصد"
    override val realityTiming = "زمان‌بندی"
    override val realityDns = "DNS"
    override val realityTcp = "TCP"
    override val realityTls = "TLS"
    override val realityTotal = "کل"
    override val realityTlsDetails = "جزئیات TLS"
    override val realityTlsVersion = "نسخه"
    override val realityAlpn = "ALPN"
    override val realityCipher = "مجموعهٔ رمزنگار"
    override val realityNotNegotiated = "مذاکره نشد"
    override val realityCert = "گواهی"
    override val realityCertValid = "اعتبارسنجی"
    override val realitySniInSan = "SNI در SAN"
    override val realityCertIssuer = "صادرکننده"
    override val realityCertDays = "روزهای باقی‌مانده"
    override val realityCertKey = "نوع کلید"
    override val realityCertChain = "طول زنجیره"
    override val realityHttp = "پاسخ HTTP"
    override val realityHttpStatus = "کد وضعیت"
    override val realityRedirectTo = "redirect به"
    override val realityCdn = "پشت CDN"
    override val realityCloudflare = "کلودفلر"
    override val realityServerHeader = "هدر Server"
    override val realityYes = "بله"
    override val realityNo = "خیر"
    override val metricUptime = "آپ‌تایم"
    override val metricLoad = "بار"
    override val metricDisks = "دیسک"
    override val metricInterfaces = "رابط شبکه"
    override val metricConfigs = "تنظیمات"
    override val metricUsed = "مصرف"
    override val metricTotal = "کل"
    override val metricExpire = "انقضا"
    override val netRegionLabel = "منطقه"
    override val netDnsRecordsLabel = "رکوردهای DNS"
    override val svcLoadHint = "سرویس‌ها را بارگذاری کنید تا systemd روی %1 پرس‌وجو شود. هیچ سرویسی به‌صورت محلی ساخته نمی‌شود."
    override val svcSelectedServer = "سرور انتخاب‌شده"
    override val backupRestoreAction = "بازیابی %1"
    override val backupModeMerge = "ادغام"
    override val backupModeOverwrite = "بازنویسی"
    override val netDomainLabel = "دامنه"
    override val netReverseDnsLabel = "Reverse DNS"
    override val netIspLabel = "ISP"
    override val netAsnLabel = "ASN"
    override val netDiagFailed = "تشخیص شبکه ناموفق بود"
    override val dnsNoPtr = "بدون PTR"
    override val tunEditingId = "ویرایش #%1"
    override val upMonitorConfiguration = "تنظیمات پایش"
    override val tunConfiguration = "تنظیمات تونل"
    override val dnsRecordsSubtitle = "رکوردهای Cloudflare · تشخیص DNS"
    override val dnsConnection = "اتصال Cloudflare"
    override val dnsApiToken = "توکن API"
    override val dnsSaveToken = "ذخیرهٔ توکن"
    override val dnsLoadZones = "بارگذاری Zoneها"
    override val dnsCreateRecord = "ساخت رکورد"
    override val dnsEditRecord = "ویرایش رکورد"
    override val dnsSaveRecord = "ذخیرهٔ رکورد Cloudflare"
    override val dnsDiagnosis = "تشخیص DNS"
    override val dnsDomainOrIp = "دامنه یا IP"
    override val netLocalProbe = "probe محلی"
    override val netProbeInput = "ورودی probe"
    override val netHostDomain = "میزبان / دامنه"
    override val netOpenPorts = "پورت‌های باز"
    override val vaultMasterPassword = "رمز Master"
    override val backupPreviewRestore = "پیش‌نمایش → بازیابی"
    override val backupPassword = "رمز پشتیبان"
    override val alertsBotToken = "توکن ربات"
    override val alertsChatId = "شناسهٔ چت"
    override val alertsTestTelegram = "آزمون Telegram"
    override val alertsDiscordWebhook = "وب‌هوک Discord"
    override val alertsWebhookUrl = "نشانی وب‌هوک"
    override val alertsTestDiscord = "آزمون Discord"
    override val svcFilter = "فیلتر سرویس‌ها"
    override val svcLoad = "بارگذاری سرویس‌ها"
    override val svcNoLiveData = "دادهٔ زنده‌ای از سرویس‌ها نیست"
    override val svcOnlySystemdUnits = "فقط unitهایی که systemd برمی‌گرداند نمایش داده می‌شوند."
    override val svcRawSsh = "نتیجهٔ خام SSH"
    override val setPollIntervalSec = "فاصلهٔ polling (ثانیه)"
    override val setResetVault = "بازنشانی Vault"
    override val monitorDetails = "نکات اجرا و توقف"
    override val monitorTitle = "پایش خودکار"
    override val monitorOff = "پایش خودکار خاموش است"
    override val monitorOn = "پایش خودکار روشن است"
    override val monitorStarting = "در حال شروع پایش…"
    override val monitorStopping = "در حال توقف پایش…"
    override val monitorFailed = "پایش شروع نشد یا متوقف شد"
    override val monitorStart = "شروع پایش"
    override val monitorStop = "توقف پایش"
    override val monitorBody = "در حالت روشن، سرورهای ذخیره‌شده و مقصدهای فعال به‌صورت دوره‌ای از اینترنت این گوشی بررسی می‌شوند؛ پایش به یک سرور انتخاب‌شده محدود نیست."
    override val monitorTargets = "%1 مقصد فعال برای پایش سایت و سرویس"
    override val monitorNoTargets = "مقصد فعالی ندارید؛ یک مقصد اضافه یا از حالت توقف خارج کنید. پایش سرورهای ذخیره‌شده مستقل از این فهرست است."
    override val monitorLimits = "توقف، بررسی‌های خودکار بعدی را متوقف می‌کند؛ بررسی دستی همچنان ممکن است. اتصالِ در حال اجرا ممکن است کمی دیرتر تمام شود. بستن اجباری برنامه، راه‌اندازی دوبارهٔ گوشی و محدودیت باتری می‌توانند پایش را قطع کنند."
    override val monitorNotification = "بررسی دوره‌ای سرورها و مقصدها از این گوشی فعال است."
    override val monitorPermission = "اعلان‌ها یا یکی از کانال‌های هشدار مجاز نیست. پایش می‌تواند کار کند، اما ممکن است هشدار گوشی را نبینید."
    override val monitorPermissionSettings = "تنظیمات اعلان گوشی"
    override val monitorPermissionPending = "در انتظار پاسخ مجوز اعلان…"
    override val monitorStartError = "اندروید شروع پایش را نپذیرفت یا راه‌اندازی کامل نشد. برنامه را در پیش‌زمینه نگه دارید و دوباره شروع کنید؛ محدودیت باتری و تنظیمات برنامه را هم بررسی کنید."
    override val monitorStopError = "توقف تأیید نشد؛ وضعیت فعلی حفظ شده است. دوباره توقف را امتحان کنید."
    override val monitorEngineError = "موتور پایش با خطا متوقف شد. مقصدها حذف نشده‌اند؛ دوباره شروع کنید و اگر خطا تکرار شد داده‌ها و تنظیمات برنامه را بررسی کنید."
    override val monitorSettingsError = "تنظیمات اعلان باز نشد؛ از تنظیمات اندروید، برنامهٔ دیدبان را باز کنید."
    override val upNewMonitor = "پایش جدید"
    override val upAllowPrivateTitle = "اجازه به مقصدهای شبکهٔ خصوصی"
    override val upAllowPrivateBody = "برای localhost، LAN و آدرس‌های خصوصی لازم است. فقط برای مقصدهای قابل اعتماد فعال کنید."
    override val upEditing = "ویرایش #%1"
    override val upPause = "توقف"
    override val upResume = "ادامه"
    override val upSavedMonitors = "پایش‌های ذخیره‌شده"
    override val upMonitorContract = "مشخصات پایش"
    override val upProbeType = "نوع بررسی"
    override val upTargetUrlHost = "نشانی"
    override val upIntervalSeconds = "فاصله (ثانیه)"
    override val upKeywordHint = "کلیدواژه"
    override val upTestNow = "آزمون فوری"
    override val wbFleetScope = "محدودهٔ ناوگان"
    override val wbResultMatrix = "ماتریس نتایج"
    override val netRealResponses = "%1 پاسخ واقعی"
    override val upIncidentCount = "%1 رکورد رخداد"
    override val setHostKeyStoreInit = "Trust Store کلید میزبان مقداردهی‌شده: %1"
    override val wtLocalTools = "ابزارهای محلی"
    override val wtInputTransform = "ورودی / تبدیل"
    override val wtInvalidInput = "ورودی نامعتبر"
    override val wtArtifactGenerator = "تولیدکنندهٔ artifact"
    override val wtRoutingContract = "قرارداد مسیریابی"
    override val wtBindPort = "پورت bind"
    override val wtInspectSec = "ثانیهٔ بازرسی"
    override val wtPanelSni = "SNI پنل"
    override val wtSubscriptionSni = "SNI اشتراک"
    override val wtRealitySni = "SNI REALITY"
    override val wtFallbackPort = "پورت محلی fallback"
    override val wtGeneratedArtifact = "artifact تولیدشده"
    override val wtSingleConfig = "تنظیمات تکی"
    override val wtParseProbe = "پارس و probe"
    override val wtCopyNormalized = "کپی منبع نرمال‌شده"
    override val wtSubscriptionUrl = "نشانی اشتراک"
    override val wtFetchSubscription = "دریافت اشتراک"
    override val wtRemotePath = "مسیر راه دور"
    override val wtRemoteEntries = "ورودی‌های راه دور"
    override val wtTextEditorMax = "ویرایشگر متن · حداکثر ۴ مگابایت"
    override val wtSaveRemoteFile = "ذخیرهٔ فایل راه دور"
    override val tunNewConfiguration = "تنظیمات جدید"
    override val tunSavedTunnels = "تونل‌های ذخیره‌شده"
    override val tunIdentityEngine = "هویت و موتور"
    override val tunName = "نام تونل"
    override val tunIranHost = "میزبان ایران"
    override val tunForeignHost = "میزبان خارج"
    override val tunCorePort = "پورت هسته"
    override val tunRealToken = "توکن واقعی"
    override val tunGenerateToken = "ساخت توکن امن"
    override val tunAdvancedParams = "پارامترهای پیشرفته"
    override val tunKcpMode = "حالت KCP"
    override val tunWsPath = "مسیر WS"
    override val tunWsHost = "میزبان WS"
    override val tunMultiPortHint = "نگاشت چندپورتی، مثلاً 443:8443, 80:8080"
    override val tunSpoofSource = "جعل IP مبدأ"
    override val tunSpoofPeer = "جعل IP طرف مقابل"
    override val tunVirtualIranIp = "IP مجازی ایران"
    override val tunVirtualForeignIp = "IP مجازی خارج"
    override val tunRemoteOps = "عملیات راه دور"
    override val tunGeneratedEvidence = "شواهد تولیدشده"
    override val srvNewConnection = "اتصال جدید"
    override val srvEditConnection = "ویرایش اتصال"
    override val srvAgentHost = "آدرس ایجنت"
    override val srvAgentToken = "توکن خواندن ایجنت"
    override val srvAdminToken = "توکن مدیریت ایجنت"
    override val srvQuickConnectHint = "اتصال فوری: کل کد didban:// نمایش‌داده‌شده در پایان نصب را اینجا بچسبانید."
    override val srvQuickConnectLabel = "کد اتصال فوری"
    override val srvQuickConnectImport = "واردکردن خودکار اطلاعات"
    override val srvQuickConnectImported = "کد اتصال وارد شد؛ اکنون «آزمایش ایجنت» و سپس «ذخیره» را بزنید."
    override val srvQuickConnectInvalid = "کد اتصال فوری معتبر نیست. کل خطی را که با didban:// شروع می‌شود کپی کنید."
    override val srvUseTls = "استفاده از TLS"
    override val srvFingerprintPinning = "پین‌کردن fingerprint"
    override val srvTlsFingerprint = "fingerprint SHA-256 از TLS (اختیاری، TOFU)"
    override val srvCpuAlert = "آستانهٔ هشدار CPU (%)"
    override val srvMemAlert = "آستانهٔ هشدار حافظه (%)"
    override val srvTestAgent = "آزمون ایجنت"
    override val srvConnectionResult = "نتیجهٔ اتصال"
    override val srvAgentOnline = "ایجنت آنلاین است"
    override val srvSavedActions = "عملیات‌های ذخیره‌شده"
    override val srvOpenDossier = "بازکردن پرونده"
    override val srvSavedCount = "%1 اتصال ذخیره‌شده"
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
    override val setBody = "زبان، ظاهر، پایش و امنیت برنامه را از اینجا تنظیم کن."
    override val setAppearanceBody = "تغییرات ظاهر و زبان بلافاصله اعمال می‌شوند."
    override val setPollBody = "فاصلهٔ درخواست‌های واقعی Agent و محدودیت‌های آن."
    override val setScreenshotProtection = "جلوگیری از اسکرین‌شات در صفحه‌های حساس"
    override val setScreenshotProtectionHint = "پیش‌فرض خاموش است؛ می‌توانید اسکرین‌شات بگیرید. با روشن‌کردن، ثبت تصویر و پیش‌نمایش برنامه‌های اخیر برای صفحه‌های حساس مسدود می‌شود. توکن‌ها و رمزها همچنان پوشیده‌اند؛ پیش از اشتراک‌گذاری تصویر، اطلاعات حساس را بررسی کنید."
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
    override val netIndexBody = "عیب‌یابی دسترسی، شبکه، TLS، DNS و کیفیت اتصال."
    override val netQReachable = "تست دسترسی به مقصد"
    override val netQReachableTools = "از دید این سرور: TCP، HTTP، SSL و Check-Host"
    override val netQNetworkLayer = "عیب‌یابی لایهٔ شبکه"
    override val netQNetworkLayerTools = "TCP Ping، تشخیص DPI و Port Scanner"
    override val netQTls = "بررسی گواهی TLS"
    override val netQTlsTools = "Fingerprint، Subject، Chain و SAN"
    override val netQDns = "DNS و Cloudflare"
    override val netQDnsTools = "بررسی پاسخ DNS و مدیریت Recordها"
    override val netQQuality = "سنجش کیفیت اتصال"
    override val netQQualityTools = "Bandwidth، Latency، Loss و Jitter"
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
    override val srvFetchFingerprint = "دریافت fingerprint فعلی سرور"
    override val srvAcceptFingerprint = "پذیرش و درج در فرم"
    override val srvFingerprintConfirmTitle = "تأیید fingerprint جدید"
    override val srvFingerprintConfirmBody = "سرور %1 fingerprint جدیدی ارائه می‌کند.\n\nقبلی: %2\nفعلی: %3\n\nفقط اگر خودتان ایجنت را دوباره نصب یا به‌روز کرده‌اید بپذیرید. برای اطمینان، این دستور را روی سرور اجرا کنید و fingerprint را مقایسه کنید:\njournalctl -u didban-agent --no-pager | grep 'Cert SHA256'"
    override val connFingerprintMismatch = "fingerprint گواهی مطابقت ندارد (مورد انتظار %1…، دریافتی %2…). اگر ایجنت را دوباره نصب کرده‌اید، fingerprint را در ویرایش سرور به‌روز کنید."
    override val srvConnectFailed = "اتصال به Agent ناموفق بود."
    override val srvDossierHint = "از اینجا پروندهٔ واقعی سرور باز می‌شود؛ حذف فقط local connection را حذف می‌کند."
    override val srvDeleteTitle = "Delete connection؟"
    override val srvDeleteBody = "اتصال %1 از Prefs حذف می‌شود؛ چیزی روی خود سرور حذف نخواهد شد."
    override val srvLocalDeleted = "اتصال local حذف شد."
    override val delete = "حذف"
    override val upTargetRequired = "Target نمی‌تواند خالی باشد."
    override val upKeywordRequired = "برای KEYWORD باید keyword واقعی وارد شود."
    override val upSaved = "مقصد ذخیره شد. برای بررسی دوره‌ای، در صفحهٔ پایش «شروع پایش» را بزنید؛ اگر پایش روشن است، این مقصد طبق برنامه بررسی می‌شود."
    override val upTargetFirst = "ابتدا target را وارد کنید."
    override val upCheckDone = "Check واقعی انجام شد: %1 · %2 ms"
    override val upCheckFailed = "Check ناموفق بود."
    override val upNoMonitorYet = "هنوز پایشی ذخیره نشده است. با فرم زیر اولین پایش را بسازید."
    override val upEditorBody = "بررسی‌ها واقعاً روی همین مقصد اجرا و نتیجهٔ واقعی ثبت می‌شود."
    override val upDeleteTitle = "Delete monitor؟"
    override val upDeleteBody = "%1 و heartbeat/incidentهای محلی آن حذف می‌شوند."
    override val upDeleted = "Monitor حذف شد."
    override val serversSearchPlaceholder = "جست‌وجوی نام یا آدرس"
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
    override val hubProtectBody = "اطلاعات حساس، اعلان‌ها و بازیابی داده در یک فضای جدا از عملیات عادی."
    override val hubSecretTrust = "Secret و Trust"
    override val hubAlertDelivery = "تحویل هشدار"
    override val hubDataRecovery = "داده و بازیابی"
    override val hubAppBehaviour = "رفتار برنامه"
    override val bandwidth = "سنجش پهنای باند"
    override val bandwidthBody = "دانلود و آپلود واقعی، هرکدام ۱۰۰ مگابایت از/به ایجنت. تأخیر و جیتر با اتصال TCP سنجیده می‌شوند؛ درصد خطا مربوط به شکست اتصال TCP است، نه افت بستهٔ ICMP."
    override val bandwidthRunning = "در حال سنجش…"
    override val bandwidthResult = "نتیجهٔ سنجش"
    override val bandwidthUnsupported = "HTTP 404/405: مسیر سنجش پهنای باند در ایجنت یا پروکسی در دسترس نیست. ایجنت را با بیلد جدید به‌روز و سرویس را راه‌اندازی مجدد کنید؛ آدرس و پورت را هم بررسی کنید."
    override val download = "دانلود"
    override val upload = "آپلود"
    override val jitter = "جیتر"
    override val packetLoss = "افت بسته"
    override val dockerUnsupported = "HTTP 404/405: مسیر Docker در ایجنت یا پروکسی این سرور در دسترس نیست. ایجنت را با بیلد جدید به‌روز و سرویس را راه‌اندازی مجدد کنید؛ آدرس و پورت اتصال را هم بررسی کنید."
    override val dockerUnavailable = "ایجنت پاسخ داد، اما Docker در دسترس نیست. سرویس Docker و دسترسی ایجنت به سوکت Docker را بررسی کنید."
    override val agentVersion = "نسخه ایجنت"
    override val agentOutdated = "ایجنت این سرور قدیمی است"
    override val agentOutdatedBody = "این نسخه ایجنت، Docker و سنجش پهنای باند ندارد. دستور به‌روزرسانی را کپی کنید و روی سرور اجرا کنید؛ توکن‌ها حفظ می‌شوند."
    override val copyAgentUpdateCommand = "کپی دستور به‌روزرسانی ایجنت"
    override val agentUpdateCommandCopied = "دستور به‌روزرسانی ایجنت کپی شد؛ آن را روی سرور اجرا کنید."
    override val agentToolAuthFailed = "توکن یا مجوز ایجنت پذیرفته نشد؛ اطلاعات اتصال را بررسی کنید."
    override val agentToolPayloadRejected = "حجم درخواست رد شد؛ محدودیت آپلود پروکسی و نسخهٔ ایجنت را بررسی کنید."
    override val agentToolTimeout = "مهلت ارتباط تمام شد؛ دسترسی به ایجنت و محدودیت زمانی پروکسی را بررسی کنید."
    override val agentToolDnsFailed = "نام میزبان به آدرس IP تبدیل نشد؛ آدرس سرور و DNS را بررسی کنید."
    override val agentToolTlsFailed = "بررسی TLS یا گواهی سرور ناموفق بود. اثرانگشت گواهی را از منبع معتبر بررسی کنید؛ بررسی امنیتی را غیرفعال نکنید."
    override val agentToolUnreachable = "اتصال به پورت ایجنت برقرار نشد؛ آدرس، پورت، سرویس و فایروال را بررسی کنید."
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
    override val radarTargetBody = "یک مقصد برای پایش از دید این سرور ثبت کنید: یک نام دلخواه، آدرس میزبان، پورت و نوع بررسی."
    override val radarTargetNameHint = "مثلاً: سایت اصلی"
    override val radarTargetHostHint = "مثلاً: example.com"
    override val radarTargetIncomplete = "نام، آدرس میزبان و پورت هدف را کامل وارد کنید."
    override val radarNoPointsBody = "هنوز هدفی برای این سرور ثبت نشده است. با فرم بالا یک هدف اضافه کنید تا وضعیت دسترسی آن از دید این سرور دیده شود."
    override val radarDivergentBody = "وضعیت این هدف از دید گوشی با سرورها متفاوت است؛ مسیر دسترسی را بررسی کنید."
    override val vantagePhone = "گوشی"
}

/** English. Every key is stated explicitly — nothing is inherited. */
internal object CommandCopyEn : CommandCopy {
    override val uiVaultInitialized = "Initialized"
    override val uiVaultNotInitialized = "Not initialized"
    override val uiServers = "Servers"
    override val uiMyServers = "My servers"
    override val uiMonitoringIntro = "Monitor your sites and services, and compare access from your phone and servers."
    override val uiMonitoring = "Monitoring"
    override val uiTools = "Tools"
    override val uiServerIntro = "All your servers in one place."
    override val uiToolsIntro = "Choose the right tool for your task."
    override val uiPathTools = "Find a connection"
    override val uiNetworkTools = "Troubleshoot the network"
    override val uiMoreTools = "Advanced utilities"
    override val uiServerTools = "Manage this server"
    override val uiScopedTools = "All tools below act on “%1”."
    override val uiResources = "Resource usage"
    override val uiAdvanced = "Advanced settings"
    override val uiAppearance = "Appearance and language"
    override val uiPreferences = "App preferences"
    override val uiDataSafety = "Alerts and data"
    override val uiSecuritySettings = "App security"
    override val uiComparison = "Compare access from servers"
    override val uiComparisonHint = "Compare a target from your phone and your own servers."
    override val uiServerToolsHint = "Open Docker, SSH and files from the relevant server’s details."
    override val uiUnknown = "Not checked"
    override val uiSourceSelection = "Which addresses should be checked?"
    override val uiReadyScan = "Choose a list and start. Results will appear here."
    override val uiName = "Name"
    override val uiUser = "SSH username"
    override val uiSshPassword = "SSH password"
    override val uiCommand = "Command"
    override val uiInput = "Input"
    override val uiManualFields = "Enter connection details manually"
    override val uiNoData = "No measurements yet"

    override val observe = "Observe"
    override val fleet = "Fleet"
    override val operate = "Operate"
    override val diagnose = "Diagnose"
    override val workbench = "Tools"
    override val protect = "Protect"
    override val overview = "Overview"
    override val incidents = "Incidents"
    override val servers = "Servers"
    override val fleetAll = "All"
    override val fleetSummary = "Live health and server controls in one place"
    override val fleetDetails = "Server details"
    override val fleetNoMatches = "No servers match this search or filter."
    override val fleetClearFilters = "Clear search and filter"
    override val fleetDiscardTitle = "Discard unsaved changes?"
    override val fleetDiscardBody = "Changes in this form have not been saved. Return to the form to keep editing."
    override val fleetDiscard = "Discard and close"
    override val fleetRecordChanged = "This connection changed or was deleted. Close and reopen the editor; nothing was overwritten."
    override val fleetMissing = "This server is no longer in your list."
    override val fleetPortInvalid = "Port must be a number from 1 to 65535."
    override val fleetThresholdInvalid = "CPU and RAM thresholds must be from 1 to 100."
    override val fleetName = "Server name"
    override val fleetDraftPrivacy = "Drafts stay in memory until saved. They survive rotation, but may be lost if Android ends the app process."
    override val fleetTestVerified = "The Agent responded using the supplied certificate pin. Save to keep your changes."
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
    override val refresh = "Refresh"
    override val backTo = "Back to"
    override val refreshing = "Refreshing…"
    override val refreshComplete = "Refresh complete"
    override val refreshFailed = "Refresh failed"
    override val refreshPartial = "Partially refreshed"
    override val refreshTimedOut = "Refresh timed out; try again"
    override val refreshInterrupted = "Refresh interrupted; try again"
    override val refreshEmpty = "Nothing to refresh"
    override val refreshMetrics = "Refresh server metrics"
    override val refreshPending = "Not completed"
    override val addServer = "Add server"
    override val addTunnel = "Add tunnel"
    override val addMonitor = "Add monitor"
    override val attention = "Needs attention"
    override val healthy = "Healthy"
    override val offline = "Offline"
    override val waitingForData = "Waiting for real data"
    override val noServersTitle = "No server is connected yet"
    override val noServersBody = "Connect a real Agent to begin. Sample data is never shown."
    override val openServer = "Details and tools"
    override val activeAttention = "Operations queue"
    override val noAttention = "Nothing needs attention"
    override val noAttentionBody = "No abnormal state has been reported by the current Agents."
    override val unknownState = "Unknown"
    override val updated = "Updated"
    override val lastSeen = "Last seen"
    override val latency = "Latency"
    override val cpu = "CPU"
    override val memory = "Memory"
    override val load = "Load"
    override val incidentsFromLiveState = "This list is derived from live state and current thresholds; full incident history will be added in the Incident History stage."
    override val noIncidentsTitle = "No active incident"
    override val noIncidentsBody = "Real abnormal state from an Agent or Uptime monitor will appear here."
    override val selectServer = "Select server"
    override val back = "Back"
    override val close = "Close"
    override val language = "Language"
    override val theme = "Theme"
    override val light = "Light"
    override val dark = "Dark"
    override val automatic = "Automatic"
    override val save = "Save"
    override val saved = "Saved"
    override val english = "English"
    override val persian = "فارسی"
    override val currentServer = "Selected server"
    override val noServerSelected = "No server scope"
    override val capabilities = "Capabilities"
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
    override val netQCfEdge = "Cloudflare clean-IP scanner"
    override val netQCfEdgeTools = "Find the cleanest edge from this location"
    override val netQRealityDonor = "Check REALITY donor"
    override val netQRealityDonorTools = "TLS 1.3, h2, SANs, redirect, CDN"
    override val cfScanner = "Cloudflare clean-IP scanner"
    override val scannerReadyLists = "Ready-to-scan lists"
    override val scannerBuiltIn = "Built-in list"
    override val scannerManual = "Custom list"
    override val scannerCfReadyHint = "Samples the official Cloudflare IPv4 ranges, not every address. Clean-IP results depend on your current network."
    override val scannerSniReadyHint = "These domains are test candidates, not verified donors. TLS, certificates or CDN checks may reject them. Phone results do not guarantee server-side suitability. Scanning starts only when requested, with at most 4 probes at once."
    override val scannerShowList = "View list"
    override val scannerHideList = "Hide list"
    override val scannerSelectAll = "Select all"
    override val scannerClearSelection = "Clear selection"
    override val scannerImportFile = "Import text file"
    override val scannerImportFailed = "Could not read the file. Choose a UTF-8 text file up to 64 KiB."
    override val scannerCategory = "Category"
    override val scannerCategoryAll = "All categories"
    override val scannerCategoryTechnology = "Technology & hardware"
    override val scannerCategoryDevelopment = "Development & open source"
    override val scannerCategoryKnowledge = "Knowledge & media"
    override val scannerCategoryServices = "Services & shopping"
    override val scannerLimit = "Scan limit"
    override val scannerPlanSummary = "This run: %1 of %2 candidates"
    override val scannerRangeSummary = "%1 of %2 ranges selected"
    override val scannerSniListHint = "One domain or domain:port per line; # starts a comment. Duplicates are removed."
    override val scannerInputSummary = "%1 valid targets; %2 invalid lines. Run limit: %3"
    override val scannerChooseRange = "Select at least one range."
    override val scannerEditList = "Copy to custom list"
    override val scannerSnapshot = "Bundled list: %1; no automatic updates"
    override val cfScannerBody = "Test Cloudflare IPs on your current connection. Results may differ on other networks."
    override val cfRandom = "Cloudflare range presets"
    override val cfCustomList = "Custom list"
    override val cfCustomListHint = "One IP or CIDR per line; # for a comment"
    override val cfSettings = "Scan settings"
    override val cfSettingsHint = "Values are clamped up front so a scan cannot harm your own device or network."
    override val cfCount = "Addresses"
    override val cfTries = "Tries"
    override val cfTimeout = "Timeout (ms)"
    override val cfConcurrency = "Concurrency"
    override val cfSniOverride = "SNI override"
    override val cfStart = "Start scan"
    override val cfStop = "Stop scan"
    override val cfStopped = "Scan stopped"
    override val cfProgress = "%1 of %2 probed · %3 clean"
    override val cfResults = "Clean IPs"
    override val cfCopyBest = "Copy top 20"
    override val cfCopied = "%1 addresses copied"
    override val cfNoResults = "No clean IP found"
    override val cfNoResultsBody = "No edge completed a handshake from this network. Try more addresses, a longer timeout, or a custom list."
    override val cfNoAddresses = "No addresses to probe"
    override val cfLoss = "Loss"
    override val cfColo = "Colo"
    override val cfTlsOk = "TLS completed"
    override val realitySni = "REALITY SNI scanner"
    override val realityBody = "Whether a domain is a valid REALITY dest/serverNames donor, and why."
    override val realityDomain = "Donor domain"
    override val realityCheck = "Check donor"
    override val realityCheckAll = "Scan selected list"
    override val realityDiscouraged = "Donors to avoid"
    override val realityResults = "Results"
    override val realityProgress = "%1 of %2 checked · %3 usable"
    override val realityScore = "Score"
    override val realityGood = "Good"
    override val realityUsable = "Usable"
    override val realityRisky = "Risky"
    override val realityReject = "Reject"
    override val realityBlockers = "Blockers"
    override val realityWarnings = "Warnings"
    override val realityNotes = "Notes"
    override val realityBadTarget = "Not a valid donor domain; an IP cannot be an SNI"
    override val realityResolved = "Resolved to"
    override val realityTiming = "Timing"
    override val realityDns = "DNS"
    override val realityTcp = "TCP"
    override val realityTls = "TLS"
    override val realityTotal = "Total"
    override val realityTlsDetails = "TLS details"
    override val realityTlsVersion = "Version"
    override val realityAlpn = "ALPN"
    override val realityCipher = "Cipher suite"
    override val realityNotNegotiated = "Not negotiated"
    override val realityCert = "Certificate"
    override val realityCertValid = "Validates"
    override val realitySniInSan = "SNI in SANs"
    override val realityCertIssuer = "Issuer"
    override val realityCertDays = "Days remaining"
    override val realityCertKey = "Key type"
    override val realityCertChain = "Chain length"
    override val realityHttp = "HTTP response"
    override val realityHttpStatus = "Status"
    override val realityRedirectTo = "Redirects to"
    override val realityCdn = "Behind a CDN"
    override val realityCloudflare = "Cloudflare"
    override val realityServerHeader = "Server header"
    override val realityYes = "Yes"
    override val realityNo = "No"
    override val metricUptime = "Uptime"
    override val metricLoad = "Load"
    override val metricDisks = "disks"
    override val metricInterfaces = "interfaces"
    override val metricConfigs = "configs"
    override val metricUsed = "Used"
    override val metricTotal = "Total"
    override val metricExpire = "Expire"
    override val netRegionLabel = "Region"
    override val netDnsRecordsLabel = "DNS records"
    override val svcLoadHint = "Load services to query systemd on %1. No service is fabricated locally."
    override val svcSelectedServer = "the selected server"
    override val backupRestoreAction = "Restore %1"
    override val backupModeMerge = "Merge"
    override val backupModeOverwrite = "Overwrite"
    override val netDomainLabel = "Domain"
    override val netReverseDnsLabel = "Reverse DNS"
    override val netIspLabel = "ISP"
    override val netAsnLabel = "ASN"
    override val netDiagFailed = "Network diagnostic failed"
    override val dnsNoPtr = "No PTR"
    override val tunEditingId = "editing #%1"
    override val upMonitorConfiguration = "Monitor configuration"
    override val tunConfiguration = "Tunnel configuration"
    override val dnsRecordsSubtitle = "Cloudflare records · DNS diagnosis"
    override val dnsConnection = "Cloudflare connection"
    override val dnsApiToken = "API token"
    override val dnsSaveToken = "Save token"
    override val dnsLoadZones = "Load zones"
    override val dnsCreateRecord = "Create record"
    override val dnsEditRecord = "Edit record"
    override val dnsSaveRecord = "Save Cloudflare record"
    override val dnsDiagnosis = "DNS diagnosis"
    override val dnsDomainOrIp = "Domain or IP"
    override val netLocalProbe = "local probe"
    override val netProbeInput = "Probe input"
    override val netHostDomain = "Host / domain"
    override val netOpenPorts = "Open ports"
    override val vaultMasterPassword = "Master Password"
    override val backupPreviewRestore = "Preview → Restore"
    override val backupPassword = "Password Backup"
    override val alertsBotToken = "Bot Token"
    override val alertsChatId = "Chat ID"
    override val alertsTestTelegram = "Test Telegram"
    override val alertsDiscordWebhook = "Discord Webhook"
    override val alertsWebhookUrl = "Webhook URL"
    override val alertsTestDiscord = "Test Discord"
    override val svcFilter = "Filter services"
    override val svcLoad = "Load services"
    override val svcNoLiveData = "No live service data"
    override val svcOnlySystemdUnits = "Only units returned by systemd are shown."
    override val svcRawSsh = "raw SSH result"
    override val setPollIntervalSec = "Poll interval (seconds)"
    override val setResetVault = "Reset Vault"
    override val monitorDetails = "Running and stopping tips"
    override val monitorTitle = "Automatic monitoring"
    override val monitorOff = "Automatic monitoring is off"
    override val monitorOn = "Automatic monitoring is on"
    override val monitorStarting = "Starting monitoring…"
    override val monitorStopping = "Stopping monitoring…"
    override val monitorFailed = "Monitoring could not start or has stopped"
    override val monitorStart = "Start monitoring"
    override val monitorStop = "Stop monitoring"
    override val monitorBody = "When on, saved servers and active targets are checked periodically from this phone. Monitoring is not limited to one selected server."
    override val monitorTargets = "%1 active website/service targets"
    override val monitorNoTargets = "No active targets. Add or resume a target. Saved-server monitoring is independent of this list."
    override val monitorLimits = "Stop prevents future automatic checks; manual tests remain available. In-flight connections may take longer to finish. Force-stop, reboot and battery restrictions can interrupt monitoring."
    override val monitorNotification = "Periodic server and target checks from this phone are active."
    override val monitorPermission = "Notifications or an alert channel are disabled. Checks can run, but phone alerts may not be visible."
    override val monitorPermissionSettings = "Phone notification settings"
    override val monitorPermissionPending = "Waiting for notification permission…"
    override val monitorStartError = "Android rejected the start or setup did not finish. Keep the app in the foreground and try again; check battery restrictions and app settings."
    override val monitorStopError = "Stop was not confirmed; the current state is retained. Try stopping again."
    override val monitorEngineError = "The monitoring engine stopped after an error. Targets were not deleted. Retry and review app data/settings if it recurs."
    override val monitorSettingsError = "Could not open notification settings. Open Didban in Android app settings."
    override val upNewMonitor = "New monitor"
    override val upAllowPrivateTitle = "Allow private network targets"
    override val upAllowPrivateBody = "Required for localhost, LAN, link-local or private IPv6 targets. Enable only for trusted destinations."
    override val upEditing = "editing #%1"
    override val upPause = "Pause"
    override val upResume = "Resume"
    override val upSavedMonitors = "Saved monitors"
    override val upMonitorContract = "Monitor details"
    override val upProbeType = "Probe type"
    override val upTargetUrlHost = "Address"
    override val upIntervalSeconds = "Interval seconds"
    override val upKeywordHint = "Keyword"
    override val upTestNow = "Test now"
    override val wbFleetScope = "Fleet Scope"
    override val wbResultMatrix = "Result Matrix"
    override val netRealResponses = "%1 real responses"
    override val upIncidentCount = "%1 incident record(s)"
    override val setHostKeyStoreInit = "Host Key Store initialized: %1"
    override val wtLocalTools = "local tools"
    override val wtInputTransform = "Input / transform"
    override val wtInvalidInput = "Invalid input"
    override val wtArtifactGenerator = "artifact generator"
    override val wtRoutingContract = "Routing contract"
    override val wtBindPort = "Bind port"
    override val wtInspectSec = "Inspect sec"
    override val wtPanelSni = "Panel SNI"
    override val wtSubscriptionSni = "Subscription SNI"
    override val wtRealitySni = "REALITY SNI"
    override val wtFallbackPort = "Fallback local port"
    override val wtGeneratedArtifact = "Generated artifact"
    override val wtSingleConfig = "Single config"
    override val wtParseProbe = "Parse and probe"
    override val wtCopyNormalized = "Copy normalized source"
    override val wtSubscriptionUrl = "Subscription URL"
    override val wtFetchSubscription = "Fetch subscription"
    override val wtRemotePath = "Remote path"
    override val wtRemoteEntries = "Remote entries"
    override val wtTextEditorMax = "Text editor · max 4 MB"
    override val wtSaveRemoteFile = "Save remote file"
    override val tunNewConfiguration = "new configuration"
    override val tunSavedTunnels = "Saved tunnels"
    override val tunIdentityEngine = "Identity and engine"
    override val tunName = "Tunnel name"
    override val tunIranHost = "Iran host"
    override val tunForeignHost = "Foreign host"
    override val tunCorePort = "Core port"
    override val tunRealToken = "Real token"
    override val tunGenerateToken = "Generate secure token"
    override val tunAdvancedParams = "Advanced parameters"
    override val tunKcpMode = "KCP mode"
    override val tunWsPath = "WS path"
    override val tunWsHost = "WS host"
    override val tunMultiPortHint = "Multi-port mappings, e.g. 443:8443, 80:8080"
    override val tunSpoofSource = "Spoof source IP"
    override val tunSpoofPeer = "Spoof peer IP"
    override val tunVirtualIranIp = "Virtual Iran IP"
    override val tunVirtualForeignIp = "Virtual foreign IP"
    override val tunRemoteOps = "Remote operations"
    override val tunGeneratedEvidence = "Generated evidence"
    override val srvNewConnection = "New connection"
    override val srvEditConnection = "Edit connection"
    override val srvAgentHost = "Agent host"
    override val srvAgentToken = "Agent read token"
    override val srvAdminToken = "Agent admin token"
    override val srvQuickConnectHint = "Quick connect: paste the complete didban:// code printed after installation."
    override val srvQuickConnectLabel = "Quick-connect code"
    override val srvQuickConnectImport = "Import connection details"
    override val srvQuickConnectImported = "Quick-connect code imported. Tap Test Agent, then Save."
    override val srvQuickConnectInvalid = "Invalid quick-connect code. Copy the complete line beginning with didban://."
    override val srvUseTls = "Use TLS"
    override val srvFingerprintPinning = "Fingerprint pinning"
    override val srvTlsFingerprint = "TLS SHA-256 fingerprint (optional TOFU)"
    override val srvCpuAlert = "CPU alert %"
    override val srvMemAlert = "Memory alert %"
    override val srvTestAgent = "Test Agent"
    override val srvConnectionResult = "Connection result"
    override val srvAgentOnline = "Agent online"
    override val srvSavedActions = "Saved actions"
    override val srvOpenDossier = "Open dossier"
    override val srvSavedCount = "%1 saved connections"
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
    override val setAppearanceBody = "Appearance and language changes apply immediately."
    override val setPollBody = "The real agent request interval and its limits."
    override val setScreenshotProtection = "Block screenshots on sensitive screens"
    override val setScreenshotProtectionHint = "Off by default: screenshots are allowed. Turn on to block capture and Recent Apps previews on sensitive screens. Tokens and passwords stay masked; check for sensitive content before sharing images."
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
    override val netIndexBody = "Diagnose reachability, network, TLS, DNS and connection quality."
    override val netQReachable = "Test destination reachability"
    override val netQReachableTools = "From this server: TCP, HTTP, SSL and Check-Host"
    override val netQNetworkLayer = "Troubleshoot the network layer"
    override val netQNetworkLayerTools = "TCP ping, DPI detection and port scanner"
    override val netQTls = "Inspect the TLS certificate"
    override val netQTlsTools = "Fingerprint, subject, chain and SAN"
    override val netQDns = "DNS and Cloudflare"
    override val netQDnsTools = "Inspect DNS answers and manage records"
    override val netQQuality = "Measure connection quality"
    override val netQQualityTools = "Bandwidth, latency, loss and jitter"
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
    override val srvFetchFingerprint = "Fetch server's current fingerprint"
    override val srvAcceptFingerprint = "Accept into form"
    override val srvFingerprintConfirmTitle = "Confirm new fingerprint"
    override val srvFingerprintConfirmBody = "Server %1 presents a new fingerprint.\n\nOld: %2\nNew: %3\n\nAccept only if you reinstalled or updated the agent yourself. To verify, run this on the server and compare fingerprints:\njournalctl -u didban-agent --no-pager | grep 'Cert SHA256'"
    override val connFingerprintMismatch = "Certificate fingerprint mismatch (expected %1…, got %2…). If you reinstalled the agent, refresh the fingerprint in the server editor."
    override val srvConnectFailed = "Connecting to the agent failed."
    override val srvDossierHint = "The server's real dossier opens from here; delete only removes the local connection."
    override val srvDeleteTitle = "Delete connection?"
    override val srvDeleteBody = "The connection to %1 is removed from local preferences; nothing is deleted on the server itself."
    override val srvLocalDeleted = "Local connection deleted."
    override val delete = "Delete"
    override val upTargetRequired = "Target cannot be empty."
    override val upKeywordRequired = "A KEYWORD monitor needs a real keyword."
    override val upSaved = "Target saved. Start monitoring on the Uptime page for scheduled checks; if already running, this target follows its schedule."
    override val upTargetFirst = "Enter a target first."
    override val upCheckDone = "Real check completed: %1 · %2 ms"
    override val upCheckFailed = "The check failed."
    override val upNoMonitorYet = "No monitor saved yet. Build the first one with the form below."
    override val upEditorBody = "Checks really run against this target and record the real result."
    override val upDeleteTitle = "Delete monitor?"
    override val upDeleteBody = "%1 and its local heartbeats and incidents are deleted."
    override val upDeleted = "Monitor deleted."
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
    override val hubProtectBody = "Secrets, alerts and data recovery live in a space separate from routine operations."
    override val hubSecretTrust = "Secrets & trust"
    override val hubAlertDelivery = "Alert delivery"
    override val hubDataRecovery = "Data & recovery"
    override val hubAppBehaviour = "App behaviour"
    override val bandwidth = "Bandwidth benchmark"
    override val bandwidthBody = "A real 100 MiB download and 100 MiB upload to the agent. Latency and jitter use TCP handshakes; loss means failed TCP connections, not measured ICMP packet loss."
    override val bandwidthRunning = "Measuring…"
    override val bandwidthResult = "Benchmark result"
    override val bandwidthUnsupported = "HTTP 404/405: the bandwidth endpoint is unavailable on this agent or proxy. Update the agent to the current build and restart its service; also check its address and port."
    override val download = "Download"
    override val upload = "Upload"
    override val jitter = "Jitter"
    override val packetLoss = "Packet loss"
    override val dockerUnsupported = "HTTP 404/405: the Docker endpoint is unavailable on this agent or proxy. Update the agent to the current build and restart its service; also check the connection address and port."
    override val dockerUnavailable = "The agent responded, but Docker is unavailable. Check the Docker service and the agent’s access to its socket."
    override val agentVersion = "Agent version"
    override val agentOutdated = "This server's agent is outdated"
    override val agentOutdatedBody = "This agent version has no Docker or bandwidth endpoints. Copy the update command and run it on the server; tokens are preserved."
    override val copyAgentUpdateCommand = "Copy agent update command"
    override val agentUpdateCommandCopied = "Agent update command copied; run it on the server."
    override val agentToolAuthFailed = "The agent rejected the token or permissions. Check the connection credentials."
    override val agentToolPayloadRejected = "The request payload was rejected. Check the proxy upload limit and agent version."
    override val agentToolTimeout = "The connection timed out. Check agent reachability and proxy timeouts."
    override val agentToolDnsFailed = "The hostname could not be resolved. Check the server address and DNS."
    override val agentToolTlsFailed = "TLS or certificate verification failed. Verify the certificate fingerprint through a trusted source; do not disable verification."
    override val agentToolUnreachable = "Could not connect to the agent port. Check its address, port, service and firewall."
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
    override val radarTargetBody = "Register a destination to watch from this server: any name, a host address, a port and a check type."
    override val radarTargetNameHint = "e.g. Main site"
    override val radarTargetHostHint = "e.g. example.com"
    override val radarTargetIncomplete = "Enter the target name, host address and port completely."
    override val radarNoPointsBody = "No targets registered for this server yet. Add one with the form above to watch its reachability from this server."
    override val radarDivergentBody = "This target looks different from the phone than from the servers; check the access path."
    override val vantagePhone = "Phone"
}
