package org.didban.monitor

/** UI strings interface — safe against DEX 255-argument constructor limit. */
interface Str {
    val appName: String
    val appSubtitle: String
    val servers: String
    val noServers: String
    val noServersHint: String
    val addServer: String
    val monitoringOn: String
    val monitoringOff: String
    val langButton: String
    val manual: String
    val sshInstall: String
    val sshInstallHint: String
    val name: String
    val host: String
    val port: String
    val token: String
    val useTls: String
    val fingerprint: String
    val fingerprintOptional: String
    val pinCert: String
    val pinCertHint: String
    val sshUser: String
    val sshPassword: String
    val install: String
    val installing: String
    val cancel: String
    val save: String
    val close: String
    val delete: String
    val confirmDelete: String
    val back: String
    val overview: String
    val processes: String
    val events: String
    val cpu: String
    val memory: String
    val user: String
    val system: String
    val iowait: String
    val steal: String
    val swap: String
    val disks: String
    val network: String
    val load: String
    val uptime: String
    val cores: String
    val noData: String
    val connecting: String
    val error: String
    val lastUpdate: String
    val whatAteCpu: String
    val spikeCpu: String
    val spikeMem: String
    val topProcesses: String
    val offline: String
    val online: String
    val serverSaved: String
    val installDone: String
    val installFailed: String
    val requiresRoot: String
    val available: String
    val used: String
    val yes: String
    val no: String
    val eventProcessDown: String
    val eventProcessUp: String
    val eventDisk: String
    val eventSteal: String
    val eventAgentRestart: String
    val edit: String
    val share: String
    val latency: String
    val settings: String
    val pollInterval: String
    val every5: String
    val every10: String
    val every15: String
    val every30: String
    val every60: String
    val every120: String
    val every300: String
    val cpuAlertLbl: String
    val memAlertLbl: String
    val kill: String
    val killProcessTitle: String
    val killConfirm: String
    val sigtermDesc: String
    val sigkillDesc: String
    val killSuccess: String
    val killError: String
    val searchProcesses: String
    val sortByCpu: String
    val sortByMem: String
    val globalCheck: String
    val runProbe: String
    val stopProbe: String
    val probing: String
    val probeTarget: String
    val probeType: String
    val probeSuccess: String
    val nodes: String
    val portNumber: String
    val enterTarget: String
    val testTelegram: String
    val telegramSent: String
    val telegramFailed: String
    val navTunnels: String
    val tunnelsHub: String
    val guideTunnels: String
    val tunnelGuideHeader: String
    val tunnelAddStep1Title: String
    val tunnelAddStep1Desc: String
    val tunnelAddStep2Title: String
    val tunnelAddStep2Desc: String
    val tunnelAddStep3Title: String
    val tunnelAddStep3Desc: String
    val addTunnel: String
    val editTunnel: String
    val deleteTunnel: String
    val tunnelCore: String
    val iranNode: String
    val foreignNode: String
    val iranListenPort: String
    val foreignTargetPort: String
    val coreCommPort: String
    val tunnelTransport: String
    val secretToken: String
    val generateToken: String
    val copyIranCommand: String
    val copyForeignCommand: String
    val testTunnel: String
    val viewConfigs: String
    val autoDiscoverTunnels: String
    val discoveringTunnels: String
    val discoverResultTitle: String
    val noTunnelsFound: String
    val tunnelsDiscoveredTpl: String
    val networkHub: String
    val portsAndSockets: String
    val listeningPorts: String
    val activeConnections: String
    val cloudflareDns: String
    val saveToken: String
    val changeToken: String
    val addRecord: String
    val addDnsRecord: String
    val encryptedVault: String
    val vaultHint: String
    val masterPassword: String
    val unlockVault: String
    val notes: String
    val addNote: String
    val copy: String
    val copied: String
    val backup: String
    val backupCopyHint: String
    val devLab: String
    val localWebServer: String
    val startServer: String
    val stopServer: String
    val scan: String
    val enterHostToScan: String
    val inspect: String
    val lookup: String
    val start: String
    val stop: String
    val calculate: String
    val uptimeMonitoring: String
    val uptimeGuideHeader: String
    val uptimeStep1Title: String
    val uptimeStep1Desc: String
    val uptimeStep2Title: String
    val uptimeStep2Desc: String
    val uptimeStep3Title: String
    val uptimeStep3Desc: String
    val addMonitor: String
    val noMonitorsHint: String
    val navServers: String
    val navUptime: String
    val navNetwork: String
    val navCloudflare: String
    val navAlerts: String
    val tgBotTokenLabel: String
    val tgChatIdLabel: String
    val discordWebhookLabel: String
    val testTelegramBtn: String
    val testDiscordBtn: String
    val enableTgAlerts: String
    val enableDiscordAlerts: String
    val alertTriggersHeader: String
    val trigServerDown: String
    val trigSpikes: String
    val trigTunnel: String
    val alertsGuideHeader: String
    val alertsStep1Title: String
    val alertsStep1Desc: String
    val alertsStep2Title: String
    val alertsStep2Desc: String
    val alertsStep3Title: String
    val alertsStep3Desc: String
    val navVault: String
    val navTools: String
    val navBackup: String
    val createBackup: String
    val restoreBackup: String
    val backupPasswordHint: String
    val restoreModeMerge: String
    val restoreModeOverwrite: String
    val backupCreatedSuccess: String
    val inspectBackupBtn: String
    val restoreNowBtn: String
    val backupGuideHeader: String
    val backupStep1Title: String
    val backupStep1Desc: String
    val backupStep2Title: String
    val backupStep2Desc: String
    val backupStep3Title: String
    val backupStep3Desc: String
    val sshTerminal: String
    val devopsSnippets: String
    val batchExec: String
    val bandwidthBenchmark: String
    val systemdManager: String
    val firewallSecurity: String
    val tipHeader: String
    val guideCensorship: String
    val guideVault: String
    val guideUptime: String
    val guideDocker: String
    val guideSockets: String
    val guideSpikes: String
    val guideDevLab: String
    val guideCloudflare: String
    val cfGuideHeader: String
    val cfStep1Title: String
    val cfStep1Desc: String
    val cfStep2Title: String
    val cfStep2Desc: String
    val cfStep3Title: String
    val cfStep3Desc: String
    val vaultGuideHeader: String
    val vaultStep1Title: String
    val vaultStep1Desc: String
    val vaultStep2Title: String
    val vaultStep2Desc: String
    val vaultStep3Title: String
    val vaultStep3Desc: String
    val monitorHintOn: String
    val monitorHintOff: String
    val startShort: String
    val stopShort: String
    val quickConnectGuide: String
    val quickConnectBody: String
    val serverAddStep1Title: String
    val serverAddStep1Desc: String
    val serverAddStep2Title: String
    val serverAddStep2Desc: String
    val serverAddStep3Title: String
    val serverAddStep3Desc: String
    val showGuide: String
    val hideGuide: String
    val cmdCopied: String
    val smartPaste: String
    val clipboardParsed: String
    val clipboardNotFound: String
    val testConnection: String
    val testOk: String
    val testFail: String
    val lblCpuUse: String
    val lblRamUse: String
    val lblDiskFree: String
    val lblUptime: String
    val lblNetLive: String
    val lblLoad1m: String
    val lblLatency: String
    val lblUptimeServer: String
    val lblCharts24h: String
    val lblCollecting: String
    val liveProcessWatch: String
    val dockerContainersLbl: String
    val spikeDetective: String
    val processManager: String
    val noSpikes24h: String
    val dockerNotRunning: String
    val containerRestartedTpl: String
    val containerStoppedTpl: String
    val restartLbl: String
    val errorShort: String
    val monitorsTotalLbl: String
    val upLbl: String
    val withDowntimeLbl: String
    val noMonitorsTitle: String
    val noMonitorsBody: String
    val incidentHistory: String
    val noIncidents: String
    val incidentTpl: String
    val resumeWatch: String
    val pauseWatch: String
    val recheckNow: String
    val statusUpdated: String
    val deleteMonitorTitle: String
    val confirmDeleteMonitorTpl: String
    val monitorNameLbl: String
    val protocolTypeLbl: String
    val monitorPortLbl: String
    val monitorKeywordLbl: String
    val testBeforeSave: String
    val probeOkTpl: String
    val probeFail: String
    val editMonitorTitle: String
    val fleetAllOk: String
    val fleetDownTpl: String
    val statAvgCpu: String
    val statAvgRam: String
    val statWorstPing: String
    val fleetUptimeAvg: String
}

object FaStr : Str {
    override val appName: String = "دیدبان"
    override val appSubtitle: String = "مرکز فرماندهی و پایش سرور"
    override val servers: String = "سرورها"
    override val noServers: String = "هنوز سروری اضافه نشده"
    override val noServersHint: String = "با دکمه‌ی زیر اولین سرورتان را متصل کنید"
    override val addServer: String = "افزودن سرور جدید"
    override val monitoringOn: String = "پایش زنده فعال است"
    override val monitoringOff: String = "پایش پس‌زمینه خاموش است"
    override val langButton: String = "EN"
    override val manual: String = "اتصال دستی"
    override val sshInstall: String = "نصب خودکار با SSH"
    override val sshInstallHint: String = "آی‌پی و رمز root سرور را وارد کنید تا ایجنت Go به‌صورت خودکار و ایمن نصب شود"
    override val name: String = "نام دلخواه سرور"
    override val host: String = "آدرس (IP یا دامنه)"
    override val port: String = "پورت ایجنت"
    override val token: String = "توکن احراز هویت"
    override val useTls: String = "اتصال امن HTTPS (TLS)"
    override val fingerprint: String = "اثر انگشت سرتیفیکیت (Fingerprint)"
    override val fingerprintOptional: String = "اختیاری — خالی = اعتماد اولیه"
    override val pinCert: String = "قفل کردن این سرتیفیکیت"
    override val pinCertHint: String = "سرتیفیکیت هنوز قفل نشده — برای امنیت کامل آن را پین کنید"
    override val sshUser: String = "کاربر SSH (مثلا root)"
    override val sshPassword: String = "رمز عبور SSH"
    override val install: String = "نصب و اتصال"
    override val installing: String = "در حال نصب خودکار ایجنت…"
    override val cancel: String = "انصراف"
    override val save: String = "ذخیره تغییرات"
    override val close: String = "بستن"
    override val delete: String = "حذف سرور"
    override val confirmDelete: String = "آیا از حذف این سرور اطمینان دارید؟"
    override val back: String = "بازگشت"
    override val overview: String = "نمای کلی"
    override val processes: String = "پروسه‌ها"
    override val events: String = "رویدادها"
    override val cpu: String = "پردازنده (CPU)"
    override val memory: String = "حافظه (RAM)"
    override val user: String = "کاربر"
    override val system: String = "سیستم"
    override val iowait: String = "انتظار دیسک (I/O)"
    override val steal: String = "دزدی هسته (Steal)"
    override val swap: String = "حافظه مجازی (Swap)"
    override val disks: String = "فضای دیسک‌ها"
    override val network: String = "ترافیک شبکه"
    override val load: String = "میانگین لود"
    override val uptime: String = "مدت زمان روشن بودن"
    override val cores: String = "هسته"
    override val noData: String = "داده‌ای برای نمایش موجود نیست"
    override val connecting: String = "در حال اتصال به سرور…"
    override val error: String = "خطای ارتباط"
    override val lastUpdate: String = "آخرین به‌روزرسانی"
    override val whatAteCpu: String = "چه چیزی منابع سرور را خورد؟"
    override val spikeCpu: String = "اسپایک شدید پردازنده"
    override val spikeMem: String = "اسپایک شدید رم"
    override val topProcesses: String = "پروسه‌های مقصر مصرف بالا"
    override val offline: String = "غیرقابل دسترس"
    override val online: String = "فعال و آنلاین"
    override val serverSaved: String = "مشخصات سرور ذخیره شد"
    override val installDone: String = "ایجنت با موفقیت نصب شد و سرور متصل گردید"
    override val installFailed: String = "نصب ناموفق بود"
    override val requiresRoot: String = "کاربر باید دسترسی root داشته باشد"
    override val available: String = "فضای آزاد"
    override val used: String = "مصرف‌شده"
    override val yes: String = "بله"
    override val no: String = "خیر"
    override val eventProcessDown: String = "پروسه متوقف شد"
    override val eventProcessUp: String = "پروسه مجدداً بالا آمد"
    override val eventDisk: String = "دیسک نزدیک به پر شدن"
    override val eventSteal: String = "افزایش Steal پردازنده"
    override val eventAgentRestart: String = "ایجنت ری‌استارت شد"
    override val edit: String = "ویرایش"
    override val share: String = "اشتراک‌گذاری لاگ"
    override val latency: String = "زمان پاسخ (Latency)"
    override val settings: String = "تنظیمات پایش"
    override val pollInterval: String = "بازه بررسی پس‌زمینه"
    override val every5: String = "۵ ثانیه"
    override val every10: String = "۱۰ ثانیه"
    override val every15: String = "۱۵ ثانیه"
    override val every30: String = "۳۰ ثانیه"
    override val every60: String = "۱ دقیقه"
    override val every120: String = "۲ دقیقه"
    override val every300: String = "۵ دقیقه"
    override val cpuAlertLbl: String = "آستانه هشدار CPU بالاتر از (٪)"
    override val memAlertLbl: String = "آستانه هشدار RAM بالاتر از (٪)"
    override val kill: String = "متوقف‌سازی"
    override val killProcessTitle: String = "بستن و خاتمه دادن به پروسه"
    override val killConfirm: String = "آیا مطمئن هستید که می‌خواهید این پروسه را متوقف کنید؟"
    override val sigtermDesc: String = "توقف استاندارد (SIGTERM — اجازه ذخیره وضعیت)"
    override val sigkillDesc: String = "بستن فوری و اجباری (SIGKILL — قطع بدون درنگ)"
    override val killSuccess: String = "دستور بستن پروسه با موفقیت ارسال شد"
    override val killError: String = "خطا در متوقف کردن پروسه"
    override val searchProcesses: String = "جستجوی نام پروسه یا PID…"
    override val sortByCpu: String = "مرتب‌سازی بر اساس پردازنده"
    override val sortByMem: String = "مرتب‌سازی بر اساس رم"
    override val globalCheck: String = "تست دسترسی جهانی"
    override val runProbe: String = "شروع تست"
    override val stopProbe: String = "توقف تست"
    override val probing: String = "در حال دریافت وضعیت از سرورهای جهانی…"
    override val probeTarget: String = "آدرس سرور یا دامنه مقصد"
    override val probeType: String = "نوع تست اتصال"
    override val probeSuccess: String = "نود در دسترس است"
    override val nodes: String = "نود جهانی"
    override val portNumber: String = "شماره پورت (مثلا 22 یا 443)"
    override val enterTarget: String = "آدرس IP یا دامنه مورد نظر را وارد کنید"
    override val testTelegram: String = "تست هشدار تلگرام"
    override val telegramSent: String = "پیام تست با موفقیت به تلگرام ارسال شد"
    override val telegramFailed: String = "ارسال هشدار به تلگرام ناموفق بود"
    // Dual-Node Tunnels
    override val navTunnels: String = "تانل‌ها"
    override val tunnelsHub: String = "مرکز تانل بین دو سرور"
    override val guideTunnels: String = "مدیریت و برقراری تانل‌های معکوس، سوکت خام، پینگ ICMP و IP Spoofing (شامل BackPack 🎒، Paqet، Narnia، Spoof Tunnel، Backhaul، Rathole، GOST، Chisel، FRP و IPTables) بین سرور ایران و خارج همراه با استقرار خودکار ۱-کلیکه مانند پنل Smite."
    override val tunnelGuideHeader: String = "راهنمای ۳ مرحله‌ای راه‌اندازی تانل ایران-خارج"
    override val tunnelAddStep1Title: String = "۱. اتصال اولیه سرور ایران و خارج"
    override val tunnelAddStep1Desc: String = "ابتدا در صفحه «سرورها»، هر دو سرور ایران (Relay) و سرور خارج (Upstream) را ثبت و به ایجنت دیدبان متصل کنید."
    override val tunnelAddStep2Title: String = "۲. انتخاب پروتکل و نگاشت پورت‌ها"
    override val tunnelAddStep2Desc: String = "پروتکل تانل (Backhaul, Rathole, Gost, Paqet, BackPack) را انتخاب کرده و پورت‌های ورودی و مقصد را مشخص کنید."
    override val tunnelAddStep3Title: String = "۳. استقرار خودکار ۱-کلیکه (Zero-Touch)"
    override val tunnelAddStep3Desc: String = "با فشردن دکمه «استقرار خودکار»، دیدبان به طور همزمان تانل را روی هر دو سرور نصب و فعال می‌کند."
    override val addTunnel: String = "ساخت تانل جدید"
    override val editTunnel: String = "ویرایش مشخصات تانل"
    override val deleteTunnel: String = "حذف تانل"
    override val tunnelCore: String = "هسته تانل"
    override val iranNode: String = "سرور ایران (پل ارتباطی / Relay)"
    override val foreignNode: String = "سرور خارج (مقصد / Upstream)"
    override val iranListenPort: String = "پورت ورودی ایران (Listen)"
    override val foreignTargetPort: String = "پورت مقصد خارج (Target)"
    override val coreCommPort: String = "پورت ارتباطی تانل (Core Port)"
    override val tunnelTransport: String = "پروتکل انتقال ترافیک"
    override val secretToken: String = "توکن اختصاصی امنیتی"
    override val generateToken: String = "تولید توکن قوی"
    override val copyIranCommand: String = "کپی دستور سرور ایران"
    override val copyForeignCommand: String = "کپی دستور سرور خارج"
    override val testTunnel: String = "تست پایداری و پینگ"
    override val viewConfigs: String = "کانفیگ‌ها و داکر"
    override val autoDiscoverTunnels: String = "کشف خودکار تانل‌های سرور"
    override val discoveringTunnels: String = "در حال اسکن پردازه‌ها و کشف تانل‌ها…"
    override val discoverResultTitle: String = "نتیجه اسکن و کشف خودکار تانل‌ها"
    override val noTunnelsFound: String = "هیچ تانل فعالی روی پردازه‌ها یا داکر سرورهای ثبت‌شده یافت نشد."
    override val tunnelsDiscoveredTpl: String = "تعداد %d تانل فعال روی سرورها با موفقیت شناسایی و به سامانه اضافه شد!"
    // Echoes Tools Suite
    override val networkHub: String = "ابزارهای شبکه و عیب‌یابی"
    override val portsAndSockets: String = "پورت‌ها و سوکت‌ها"
    override val listeningPorts: String = "پورت‌های باز"
    override val activeConnections: String = "اتصالات فعال"
    override val cloudflareDns: String = "مدیریت DNS کلودفلر"
    override val saveToken: String = "ذخیره توکن API"
    override val changeToken: String = "تغییر توکن"
    override val addRecord: String = "رکورد جدید"
    override val addDnsRecord: String = "افزودن رکورد جدید DNS"
    override val encryptedVault: String = "گاوصندوق امن محرمانه"
    override val vaultHint: String = "رمز عبور اصلی برای گشایش گاوصندوق (رمزنگاری نظامی AES-256)"
    override val masterPassword: String = "رمز عبور اصلی گاوصندوق"
    override val unlockVault: String = "بازگشایی قفل گاوصندوق"
    override val notes: String = "یادداشت و کلید محرمانه"
    override val addNote: String = "یادداشت / کلید جدید"
    override val copy: String = "کپی در حافظه"
    override val copied: String = "با موفقیت کپی شد!"
    override val backup: String = "پشتیبان‌گیری رمزنگاری‌شده"
    override val backupCopyHint: String = "رشته رمزنگاری‌شده زیر شامل سرورها و کلیدهای شماست:"
    override val devLab: String = "جعبه‌ابزار کاربردی"
    override val localWebServer: String = "وب‌سرور و اشتراک فایل با QR"
    override val startServer: String = "روشن کردن وب‌سرور"
    override val stopServer: String = "خاموش کردن"
    override val scan: String = "شروع اسکن پورت‌ها"
    override val enterHostToScan: String = "یک آدرس برای اسکن پورت‌ها وارد کنید"
    override val inspect: String = "بررسی گواهی SSL"
    override val lookup: String = "استعلام موقعیت IP"
    override val start: String = "شروع پینگ"
    override val stop: String = "توقف"
    override val calculate: String = "محاسبه ساب‌نت"
    override val uptimeMonitoring: String = "پایش پایداری و سلامت سایت‌ها"
    override val uptimeGuideHeader: String = "راهنمای ۳ مرحله‌ای پایش پایداری (SLA)"
    override val uptimeStep1Title: String = "۱. انتخاب نوع پروتکل پایش"
    override val uptimeStep1Desc: String = "پروتکل مورد نظر (HTTP/HTTPS وب، پورت TCP دیتابیس، پینگ ICMP، کلمه کلیدی یا گواهی SSL) را تعیین کنید."
    override val uptimeStep2Title: String = "۲. تنظیم آدرس و معیارهای تایید"
    override val uptimeStep2Desc: String = "آدرس دامنه یا IP مقصد و در صورت نیاز پورت و کلمه کلیدی بررسی سلامت را وارد نمایید."
    override val uptimeStep3Title: String = "۳. پایش ۲۴ ساعته و دریافت هشدار"
    override val uptimeStep3Desc: String = "دیدبان وضعیت پایداری، تاخیر و قطعی‌ها را ثبت کرده و در صورت افت SLA بلافاصله هشدار می‌دهد."
    override val addMonitor: String = "افزودن مانیتور جدید"
    override val noMonitorsHint: String = "هنوز سایتی برای پایش پایداری و آپ‌تایم ثبت نشده است"
    override val navServers: String = "سرورها"
    override val navUptime: String = "آپ‌تایم"
    override val navNetwork: String = "شبکه"
    override val navCloudflare: String = "کلودفلر"
    override val navAlerts: String = "ربات‌ها و هشدارها"
    override val tgBotTokenLabel: String = "توکن ربات تلگرام (Bot Token):"
    override val tgChatIdLabel: String = "شناسه چت یا کانال تلگرام (Chat ID):"
    override val discordWebhookLabel: String = "آدرس وبهوک دیسکورد (Discord Webhook URL):"
    override val testTelegramBtn: String = "ارسال پیام تست به تلگرام"
    override val testDiscordBtn: String = "ارسال پیام تست به دیسکورد"
    override val enableTgAlerts: String = "فعال‌سازی هشدارهای هوشمند تلگرام"
    override val enableDiscordAlerts: String = "فعال‌سازی هشدارهای هوشمند دیسکورد"
    override val alertTriggersHeader: String = "رویدادهای نیازمند هشدار فوری:"
    override val trigServerDown: String = "قطع شدن ارتباط یا خاموش شدن سرور"
    override val trigSpikes: String = "افزایش ناگهانی مصرف رم و پردازنده (Spikes)"
    override val trigTunnel: String = "قطع شدن یا افت شدید کیفیت تانل ایران-خارج"
    override val alertsGuideHeader: String = "راهنمای اتصال به ربات تلگرام و دیسکورد"
    override val alertsStep1Title: String = "۱. ساخت ربات در BotFather@"
    override val alertsStep1Desc: String = "در تلگرام به BotFather@ پیام دهید و با دستور newbot/ یک ربات بسازید و توکن آن را کپی کنید."
    override val alertsStep2Title: String = "۲. دریافت Chat ID شخصی یا گروه"
    override val alertsStep2Desc: String = "ربات را استارت کنید و با کمک userinfobot@ شناسه عددی چت یا گروه خود را به دست آورید."
    override val alertsStep3Title: String = "۳. دریافت آنی هشدارهای قطعی ۲۴/۷"
    override val alertsStep3Desc: String = "دیدبان در صورت قطعی سرور یا تانل، سریعاً پیام هشدار با جزییات کامل ارسال می‌کند."
    override val navVault: String = "گاوصندوق"
    override val navTools: String = "ابزارها"
    override val navBackup: String = "بکاپ و بازیابی"
    override val createBackup: String = "تهیه نسخه پشتیبان"
    override val restoreBackup: String = "بازیابی اطلاعات (Restore)"
    override val backupPasswordHint: String = "رمز عبور دلخواه برای رمزنگاری بکاپ (اختیاری):"
    override val restoreModeMerge: String = "ادغام با داده‌های فعلی (بدون حذف)"
    override val restoreModeOverwrite: String = "جایگزینی کامل داده‌ها (Overwrite)"
    override val backupCreatedSuccess: String = "نسخه پشتیبان آماده شد. آن را کپی کرده و در جایی امن نگه دارید."
    override val inspectBackupBtn: String = "بررسی و پیش‌نمایش محتوا"
    override val restoreNowBtn: String = "تایید و شروع بازیابی"
    override val backupGuideHeader: String = "راهنمای پشتیبان‌گیری و بازیابی داده‌ها"
    override val backupStep1Title: String = "۱. رمزنگاری سرتاسری AES-256"
    override val backupStep1Desc: String = "تمامی مشخصات سرورها، تانل‌ها، مانیتورهای آپ‌تایم و کلیدها در یک رشته فشرده و ایمن ذخیره می‌شوند."
    override val backupStep2Title: String = "۲. انتقال بین گوشی‌ها و دستگاه‌ها"
    override val backupStep2Desc: String = "رشته بکاپ را به راحتی در تلگرام، نوت یا پیام ذخیره کنید و در دستگاه دیگر وارد نمایید."
    override val backupStep3Title: String = "۳. بازیابی هوشمند با ۱ تپ"
    override val backupStep3Desc: String = "می‌توانید اطلاعات را با داده‌های موجود ادغام کنید تا هیچ سرور یا تانلی از دست نرود."
    override val sshTerminal: String = "ترمینال زنده SSH"
    override val devopsSnippets: String = "دستورات آماده DevOps"
    override val batchExec: String = "اجرای گروهی ناوگان"
    override val bandwidthBenchmark: String = "بنچمارک پهنای باند و سرعت"
    override val systemdManager: String = "سرویس‌های Systemd و کران‌جاب"
    override val firewallSecurity: String = "فایروال و امنیت Fail2ban"
    override val tipHeader: String = "💡 نکته: اطلاعات پردازنده، رم و دیسک هر چند ثانیه به‌صورت زنده دریافت می‌شوند."
    override val guideCensorship: String = "این ابزار هوشمند، اختلالات اینترنت و فیلترینگ را تفکیک می‌کند: آیا سرور فیلتر شده، پکت‌های TCP RST توسط فیلترینگ تزریق می‌شوند، یا پروتکل TLS/SNI قطع شده است."
    override val guideVault: String = "گاوصندوق امن محلی برای ذخیره کلیدهای SSH، رمزها، توکن‌ها و کانفیگ‌ها با رمزنگاری نظامی AES-256 (داده‌ها فقط روی گوشی شما ذخیره می‌شوند و به هیچ سروری ارسال نمی‌گردند)."
    override val guideUptime: String = "پایش ۲۴ ساعته پایداری سایت‌ها، وب‌سرویس‌ها، پورت دیتابیس‌ها و گواهی SSL با نوارهای ضربان قلب ۳۰ تایی (مشابه Uptime Kuma) و هشدار قطعی آنی."
    override val guideDocker: String = "مشاهده و کنترل تمام کانتینرهای فعال و متوقف Docker روی سرور، بدون نیاز به خط فرمان با امکان ری‌استارت یا توقف با یک کلیک."
    override val guideSockets: String = "مشاهده زنده تمام پورت‌های در حال شنود (Listening) و ارتباطات فعال شبکه همراه با نام پروسه و شماره PID."
    override val guideSpikes: String = "کارآگاه اسپایک: هر زمان مصرف پردازنده یا رم سرور به‌طور ناگهانی بالا برود، دیدبان پروسه‌های مقصر را به همراه جزئیات کامل ذخیره می‌کند."
    override val guideDevLab: String = "مجموعه ابزارهای دم‌دستی برای برنامه‌نویسان و ادمین‌ها: فرمت JSON، دیکودر توکن‌های JWT، محاسبه‌گر ساب‌نت شبکه، و تولیدکننده پسورد و UUID."
    override val guideCloudflare: String = "مدیریت مستقیم رکوردهای دامنه (A, AAAA, CNAME) و تغییر وضعیت پروکسی ابری بدون نیاز به ورود به داشبورد سنگین کلودفلر."
    override val cfGuideHeader: String = "راهنمای ۳ مرحله‌ای مدیریت کلودفلر"
    override val cfStep1Title: String = "۱. دریافت توکن API کلودفلر"
    override val cfStep1Desc: String = "در پنل کلودفلر وارد منوی API Tokens شده و یک توکن با دسترسی Zone.DNS ایجاد کنید."
    override val cfStep2Title: String = "۲. انتخاب دامنه (Zone)"
    override val cfStep2Desc: String = "پس از وارد کردن توکن، دامنه‌های شما به صورت خودکار لیست و لود می‌شوند."
    override val cfStep3Title: String = "۳. مدیریت رکوردها و پروکسی ابری"
    override val cfStep3Desc: String = "رکوردهای A, AAAA, CNAME را اضافه، ویرایش یا پروکسی ابری (ابر نارنجی) را فعال/غیرفعال کنید."
    override val vaultGuideHeader: String = "راهنمای ۳ مرحله‌ای گاوصندوق رمزنگاری‌شده"
    override val vaultStep1Title: String = "۱. تعیین رمز عبور اصلی (Master Password)"
    override val vaultStep1Desc: String = "یک رمز عبور قوی انتخاب کنید؛ داده‌های شما با الگوریتم AES-256-GCM محلی رمزگذاری می‌شوند."
    override val vaultStep2Title: String = "۲. ثبت کلیدهای SSH، رمزها و یادداشت‌ها"
    override val vaultStep2Desc: String = "مشخصات سرورها، کلیدهای دسترسی خصوصی و کانفیگ‌های محرمانه را با خیال راحت ذخیره کنید."
    override val vaultStep3Title: String = "۳. پشتیبان‌گیری رمزنگاری‌شده ۱-کلیکه"
    override val vaultStep3Desc: String = "در هر لحظه می‌توانید خروجی رمزنگاری‌شده گاوصندوق را کپی یا به دستگاه دیگر منتقل نمایید."
    // Nightwatch UI
    override val monitorHintOn: String = "پایش خودکار پس‌زمینه فعال است"
    override val monitorHintOff: String = "پایش پس‌زمینه متوقف است"
    override val startShort: String = "شروع"
    override val stopShort: String = "توقف"
    override val quickConnectGuide: String = "راهنمای اتصال سریع سرور"
    override val quickConnectBody: String = "برای اتصال سرور لینوکس (Ubuntu, Debian, CentOS, AlmaLinux) دستور زیر را در ترمینال سرور اجرا کنید:"
    override val serverAddStep1Title: String = "۱. اجرای اسکریپت نصب در سرور"
    override val serverAddStep1Desc: String = "وارد ترمینال لینوکس خود شده و دستور نصب ۱-خطی دیدبان را کپی و اجرا کنید:"
    override val serverAddStep2Title: String = "۲. دریافت لینک اتصال اختصاصی"
    override val serverAddStep2Desc: String = "پس از اتمام نصب، اسکریپت یک لینک اتصال اختصاصی (didban://...) چاپ می‌کند؛ آن را کپی کنید."
    override val serverAddStep3Title: String = "۳. اتصال و رمزنگاری امن"
    override val serverAddStep3Desc: String = "دکمه «افزودن سرور» را لمس کرده و با «چسباندن هوشمند» مشخصات را در یک ثانیه ثبت کنید."
    override val showGuide: String = "راهنمای مرحله‌به‌مرحله اتصال سرور"
    override val hideGuide: String = "بستن راهنما"
    override val cmdCopied: String = "دستور نصب کپی شد!"
    override val smartPaste: String = "الصاق خودکار مشخصات از کلیپ‌بورد"
    override val clipboardParsed: String = "اطلاعات از کلیپ‌بورد شناسایی شد!"
    override val clipboardNotFound: String = "مشخصات معتبری در کلیپ‌بورد یافت نشد"
    override val testConnection: String = "تست اتصال"
    override val testOk: String = "اتصال موفق!"
    override val testFail: String = "خطا در اتصال"
    override val lblCpuUse: String = "مصرف پردازنده"
    override val lblRamUse: String = "حافظه رم"
    override val lblDiskFree: String = "فضای آزاد دیسک"
    override val lblUptime: String = "آپ‌تایم سیستم"
    override val lblNetLive: String = "ترافیک زنده شبکه"
    override val lblLoad1m: String = "لود پردازنده (Load 1m)"
    override val lblLatency: String = "زمان پاسخ"
    override val lblUptimeServer: String = "آپتایم سرور"
    override val lblCharts24h: String = "نمودار تغییرات ۲۴ ساعته پردازنده و رم"
    override val lblCollecting: String = "در حال جمع‌آوری تاریخچه…"
    override val liveProcessWatch: String = "پایش زنده و رصد سریع پروسه‌ها"
    override val dockerContainersLbl: String = "کانتینرهای داکر"
    override val spikeDetective: String = "کارآگاه اسپایک"
    override val processManager: String = "مدیریت پروسه‌ها"
    override val noSpikes24h: String = "هیچ اسپایکی در ۲۴ ساعت گذشته ثبت نشده است"
    override val dockerNotRunning: String = "سرویس Docker روی این سرور در حال اجرا نیست"
    override val containerRestartedTpl: String = "کانتینر %s ری‌استارت شد"
    override val containerStoppedTpl: String = "کانتینر %s متوقف شد"
    override val restartLbl: String = "ری‌استارت"
    override val errorShort: String = "خطا"
    override val monitorsTotalLbl: String = "کل مانیتورها"
    override val upLbl: String = "آنلاین"
    override val withDowntimeLbl: String = "دارای قطعی"
    override val noMonitorsTitle: String = "هنوز مانیتوری ثبت نشده است"
    override val noMonitorsBody: String = "با افزودن اولین مانیتور، وضعیت در دسترس بودن سرویس‌های شما به‌صورت خودکار و شبانه‌روزی بررسی می‌شود."
    override val incidentHistory: String = "تاریخچه حوادث و قطعی‌ها"
    override val noIncidents: String = "هیچ حادثه قطعی برای این سرویس ثبت نشده است"
    override val incidentTpl: String = "%1\$s — قطعی به مدت %2\$d ثانیه (%3\$s)"
    override val resumeWatch: String = "ادامه پایش"
    override val pauseWatch: String = "توقف موقت"
    override val recheckNow: String = "بررسی مجدد"
    override val statusUpdated: String = "وضعیت به‌روزرسانی شد"
    override val deleteMonitorTitle: String = "حذف مانیتور"
    override val confirmDeleteMonitorTpl: String = "آیا از حذف مانیتور «%s» اطمینان دارید؟"
    override val monitorNameLbl: String = "نام دلخواه مانیتور"
    override val protocolTypeLbl: String = "نوع پروتکل"
    override val monitorPortLbl: String = "شماره پورت (مثلاً 443, 80, 5432, 3306, 6379, 22)"
    override val monitorKeywordLbl: String = "کلمه کلیدی مورد انتظار (مثلاً ok یا status)"
    override val testBeforeSave: String = "تست اتصال قبل از ذخیره"
    override val probeOkTpl: String = "پاسخ دریافت شد! (زمان پاسخ: %d میلی‌ثانیه)"
    override val probeFail: String = "پاسخ دریافت نشد یا خطایی رخ داد"
    override val editMonitorTitle: String = "ویرایش مانیتور"
    override val fleetAllOk: String = "همه سیستم‌ها عملیاتی است"
    override val fleetDownTpl: String = "%d از %d سرور در دسترس نیست"
    override val statAvgCpu: String = "میانگین CPU"
    override val statAvgRam: String = "میانگین RAM"
    override val statWorstPing: String = "بدترین پینگ"
    override val fleetUptimeAvg: String = "میانگین آپ‌تایم ناوگان"
}

object EnStr : Str {
    override val appName: String = "Didban"
    override val appSubtitle: String = "DevOps Command Center"
    override val servers: String = "Servers"
    override val noServers: String = "No servers added yet"
    override val noServersHint: String = "Add your first server with the button below"
    override val addServer: String = "Add Server"
    override val monitoringOn: String = "Live monitoring is active"
    override val monitoringOff: String = "Background polling is paused"
    override val langButton: String = "فا"
    override val manual: String = "Manual Setup"
    override val sshInstall: String = "1-Click SSH Install"
    override val sshInstallHint: String = "Enter server IP and root password to automatically install the lightweight Go agent"
    override val name: String = "Server Label"
    override val host: String = "Host (IP or Domain)"
    override val port: String = "Agent Port"
    override val token: String = "Auth Token"
    override val useTls: String = "HTTPS (TLS Encryption)"
    override val fingerprint: String = "Certificate Fingerprint"
    override val fingerprintOptional: String = "optional — empty = trust on first use"
    override val pinCert: String = "Pin this certificate"
    override val pinCertHint: String = "Certificate not pinned yet — pin it for full security"
    override val sshUser: String = "SSH User (e.g. root)"
    override val sshPassword: String = "SSH Password"
    override val install: String = "Install & Connect"
    override val installing: String = "Installing agent…"
    override val cancel: String = "Cancel"
    override val save: String = "Save Changes"
    override val close: String = "Close"
    override val delete: String = "Delete Server"
    override val confirmDelete: String = "Are you sure you want to delete this server?"
    override val back: String = "Back"
    override val overview: String = "Overview"
    override val processes: String = "Processes"
    override val events: String = "Spike Events"
    override val cpu: String = "Processor (CPU)"
    override val memory: String = "Memory (RAM)"
    override val user: String = "User"
    override val system: String = "System"
    override val iowait: String = "I/O Wait"
    override val steal: String = "CPU Steal"
    override val swap: String = "Virtual Swap"
    override val disks: String = "Disk Volumes"
    override val network: String = "Network Traffic"
    override val load: String = "Load Average"
    override val uptime: String = "System Uptime"
    override val cores: String = "cores"
    override val noData: String = "No data available"
    override val connecting: String = "Connecting to server…"
    override val error: String = "Connection Error"
    override val lastUpdate: String = "Last update"
    override val whatAteCpu: String = "What ate your server resources?"
    override val spikeCpu: String = "CPU Spike"
    override val spikeMem: String = "Memory Spike"
    override val topProcesses: String = "Culprit Processes"
    override val offline: String = "Unreachable"
    override val online: String = "Online & Healthy"
    override val serverSaved: String = "Server saved successfully"
    override val installDone: String = "Agent installed and server connected!"
    override val installFailed: String = "Installation failed"
    override val requiresRoot: String = "User must be root"
    override val available: String = "available"
    override val used: String = "used"
    override val yes: String = "Yes"
    override val no: String = "No"
    override val eventProcessDown: String = "Process died"
    override val eventProcessUp: String = "Process is back"
    override val eventDisk: String = "Disk almost full"
    override val eventSteal: String = "CPU steal alert"
    override val eventAgentRestart: String = "Agent restarted"
    override val edit: String = "Edit"
    override val share: String = "Share Event Log"
    override val latency: String = "Latency"
    override val settings: String = "Monitoring Settings"
    override val pollInterval: String = "Background poll interval"
    override val every5: String = "5 seconds"
    override val every10: String = "10 seconds"
    override val every15: String = "15 seconds"
    override val every30: String = "30 seconds"
    override val every60: String = "1 minute"
    override val every120: String = "2 minutes"
    override val every300: String = "5 minutes"
    override val cpuAlertLbl: String = "Alert threshold: CPU above (%)"
    override val memAlertLbl: String = "Alert threshold: RAM above (%)"
    override val kill: String = "Kill Process"
    override val killProcessTitle: String = "Terminate Process"
    override val killConfirm: String = "Are you sure you want to terminate this process?"
    override val sigtermDesc: String = "Graceful (SIGTERM — allows clean state saving)"
    override val sigkillDesc: String = "Force Kill (SIGKILL — terminates immediately)"
    override val killSuccess: String = "Kill signal sent successfully"
    override val killError: String = "Failed to send signal to process"
    override val searchProcesses: String = "Search by process name or PID…"
    override val sortByCpu: String = "Sort by CPU Usage"
    override val sortByMem: String = "Sort by RAM Usage"
    override val globalCheck: String = "Global Reachability"
    override val runProbe: String = "Run Check"
    override val stopProbe: String = "Stop Check"
    override val probing: String = "Probing nodes worldwide…"
    override val probeTarget: String = "Target Host or Domain"
    override val probeType: String = "Check Type"
    override val probeSuccess: String = "nodes reachable"
    override val nodes: String = "nodes"
    override val portNumber: String = "Port (e.g. 22 or 443)"
    override val enterTarget: String = "Enter target host or IP"
    override val testTelegram: String = "Test Telegram Alert"
    override val telegramSent: String = "Test alert sent to Telegram"
    override val telegramFailed: String = "Failed to send Telegram test"
    // Dual-Node Tunnels
    override val navTunnels: String = "Tunnels"
    override val tunnelsHub: String = "Dual-Node Tunnel Hub"
    override val guideTunnels: String = "Manage high-performance reverse, raw-socket, ICMP ping, and IP spoofing tunnels (BackPack 🎒, Paqet, Narnia, Spoof Tunnel, Backhaul, Rathole, GOST, Chisel, FRP, IPTables) with Smite-style zero-touch auto-sync."
    override val tunnelGuideHeader: String = "3-Step Iran-to-Foreign Tunnel Setup"
    override val tunnelAddStep1Title: String = "1. Connect Iran & Foreign Servers"
    override val tunnelAddStep1Desc: String = "First, ensure both your Iran Relay server and Foreign Upstream server are connected in the Servers tab."
    override val tunnelAddStep2Title: String = "2. Configure Core Protocol & Ports"
    override val tunnelAddStep2Desc: String = "Select your desired tunnel core (Backhaul, Rathole, Gost, Paqet, BackPack) and map input/output ports."
    override val tunnelAddStep3Title: String = "3. Zero-Touch 1-Click Auto-Deploy"
    override val tunnelAddStep3Desc: String = "Tap 'Auto-Deploy' to let Didban automatically install, configure, and start the systemd daemon on both nodes."
    override val addTunnel: String = "New Tunnel"
    override val editTunnel: String = "Edit Tunnel"
    override val deleteTunnel: String = "Delete Tunnel"
    override val tunnelCore: String = "Tunnel Core"
    override val iranNode: String = "Iran Node (Relay / Bridge)"
    override val foreignNode: String = "Foreign Server (Upstream / Target)"
    override val iranListenPort: String = "Iran Listen Port"
    override val foreignTargetPort: String = "Foreign Target Port"
    override val coreCommPort: String = "Core Tunnel Port"
    override val tunnelTransport: String = "Transport Protocol"
    override val secretToken: String = "Security Token"
    override val generateToken: String = "Generate Token"
    override val copyIranCommand: String = "Copy Iran Command"
    override val copyForeignCommand: String = "Copy Foreign Command"
    override val testTunnel: String = "Test Tunnel Latency"
    override val viewConfigs: String = "Configs & Docker"
    override val autoDiscoverTunnels: String = "Auto-Discover Tunnels"
    override val discoveringTunnels: String = "Scanning servers for active tunnels…"
    override val discoverResultTitle: String = "Tunnel Discovery Results"
    override val noTunnelsFound: String = "No active tunnel processes or Docker containers found on your connected servers."
    override val tunnelsDiscoveredTpl: String = "Successfully discovered and added %d active tunnel(s) from your servers!"
    // Echoes Tools Suite
    override val networkHub: String = "Network & Diagnostic Hub"
    override val portsAndSockets: String = "Ports & Sockets"
    override val listeningPorts: String = "Listening Ports"
    override val activeConnections: String = "Active Connections"
    override val cloudflareDns: String = "Cloudflare DNS Manager"
    override val saveToken: String = "Save API Token"
    override val changeToken: String = "Change Token"
    override val addRecord: String = "New Record"
    override val addDnsRecord: String = "Add DNS Record"
    override val encryptedVault: String = "Encrypted Secrets Vault"
    override val vaultHint: String = "Master password for AES-256 vault encryption"
    override val masterPassword: String = "Vault Master Password"
    override val unlockVault: String = "Unlock Vault"
    override val notes: String = "Confidential Secrets"
    override val addNote: String = "New Secret / Note"
    override val copy: String = "Copy"
    override val copied: String = "Copied to clipboard!"
    override val backup: String = "Encrypted Backup"
    override val backupCopyHint: String = "Copy the encrypted backup string below:"
    override val devLab: String = "Developer Lab"
    override val localWebServer: String = "Local Web Server & QR Share"
    override val startServer: String = "Start Web Server"
    override val stopServer: String = "Stop Server"
    override val scan: String = "Scan Common Ports"
    override val enterHostToScan: String = "Enter host or IP to scan"
    override val inspect: String = "Inspect SSL Certificate"
    override val lookup: String = "Lookup IP Geo Data"
    override val start: String = "Start Ping"
    override val stop: String = "Stop"
    override val calculate: String = "Calculate Subnet"
    override val uptimeMonitoring: String = "Uptime & Service Health"
    override val uptimeGuideHeader: String = "3-Step Uptime & SLA Monitor Setup"
    override val uptimeStep1Title: String = "1. Choose Probe Protocol"
    override val uptimeStep1Desc: String = "Select HTTP/HTTPS web, TCP socket (e.g. database), ICMP ping, keyword match, or SSL certificate expiry watch."
    override val uptimeStep2Title: String = "2. Set Host & Verification Rules"
    override val uptimeStep2Desc: String = "Specify target URL or IP, custom port, and expected keyword or response code for health validation."
    override val uptimeStep3Title: String = "3. 24/7 Monitoring & Smart Alerts"
    override val uptimeStep3Desc: String = "Didban records continuous latency telemetry, logs incidents, and sends push or Telegram alerts if SLA drops."
    override val addMonitor: String = "Add New Monitor"
    override val noMonitorsHint: String = "No uptime monitors configured yet"
    override val navServers: String = "Servers"
    override val navUptime: String = "Uptime"
    override val navNetwork: String = "Network"
    override val navCloudflare: String = "Cloudflare"
    override val navAlerts: String = "Alerts & Bots"
    override val tgBotTokenLabel: String = "Telegram Bot Token:"
    override val tgChatIdLabel: String = "Telegram Chat ID / Group ID:"
    override val discordWebhookLabel: String = "Discord Webhook URL:"
    override val testTelegramBtn: String = "Send Test Alert to Telegram"
    override val testDiscordBtn: String = "Send Test Alert to Discord"
    override val enableTgAlerts: String = "Enable Telegram Smart Alerts"
    override val enableDiscordAlerts: String = "Enable Discord Webhook Alerts"
    override val alertTriggersHeader: String = "Active Alert Triggers:"
    override val trigServerDown: String = "Server Unreachable / Down"
    override val trigSpikes: String = "High CPU & RAM Usage Spikes (>90%)"
    override val trigTunnel: String = "Dual-Node Tunnel Interruption"
    override val alertsGuideHeader: String = "3-Step Telegram & Discord Setup Guide"
    override val alertsStep1Title: String = "1. Create Bot via @BotFather"
    override val alertsStep1Desc: String = "Message @BotFather on Telegram, use /newbot to create an alert bot and copy the API token."
    override val alertsStep2Title: String = "2. Get Your Personal/Group Chat ID"
    override val alertsStep2Desc: String = "Start your bot, then use @userinfobot to get your numeric Chat ID and paste it here."
    override val alertsStep3Title: String = "3. 24/7 Real-Time Incident Dispatch"
    override val alertsStep3Desc: String = "Didban will automatically dispatch instant alert payloads on server failures and spikes."
    override val navVault: String = "Vault"
    override val navTools: String = "Tools"
    override val navBackup: String = "Backup"
    override val createBackup: String = "Create Backup"
    override val restoreBackup: String = "Restore Backup"
    override val backupPasswordHint: String = "Optional password for AES-256 backup encryption:"
    override val restoreModeMerge: String = "Merge with existing data (safe)"
    override val restoreModeOverwrite: String = "Overwrite all existing data"
    override val backupCreatedSuccess: String = "Backup generated! Copy and store it in a secure location."
    override val inspectBackupBtn: String = "Inspect & Preview Content"
    override val restoreNowBtn: String = "Confirm & Restore"
    override val backupGuideHeader: String = "3-Step Backup & Restore Guide"
    override val backupStep1Title: String = "1. Full AES-256 Encryption"
    override val backupStep1Desc: String = "All your servers, tunnels, uptime monitors, and vault keys are packed into a secure compressed string."
    override val backupStep2Title: String = "2. Easy Cross-Device Migration"
    override val backupStep2Desc: String = "Export backup string to notes, messenger, or file and import on your other phone or tablet."
    override val backupStep3Title: String = "3. Smart Merging"
    override val backupStep3Desc: String = "Restore with smart merge to combine data without losing any newly added servers."
    override val sshTerminal: String = "SSH Terminal"
    override val devopsSnippets: String = "DevOps Snippets"
    override val batchExec: String = "Fleet Batch Exec"
    override val bandwidthBenchmark: String = "Speed & Bandwidth Test"
    override val systemdManager: String = "Systemd & Cron Manager"
    override val firewallSecurity: String = "Firewall & Fail2ban"
    override val tipHeader: String = "💡 Tip: CPU, RAM, and Disk metrics are refreshed in real-time from the Go agent."
    override val guideCensorship: String = "Diagnoses network interference: distinguishes between routing blackholes, DPI TCP RST packet injections, and TLS/SNI handshake filtering."
    override val guideVault: String = "A zero-knowledge AES-256 encrypted local vault to keep your SSH private keys, API credentials, and secrets safe right on your phone."
    override val guideUptime: String = "24/7 service availability monitoring (Uptime Kuma style) with 30-bar heartbeats and instant multi-channel downtime alerts."
    override val guideDocker: String = "Live monitoring and remote 1-click restart/stop for all Docker containers directly through the native unix socket."
    override val guideSockets: String = "Live inspection of listening ports and active socket connections matched to PIDs and process names."
    override val guideSpikes: String = "Spike Forensics: Automatically records the culprit processes behind any CPU/memory spike even while your phone is off."
    override val guideDevLab: String = "Everyday developer utilities: JSON formatting, JWT token decoder, CIDR subnet calculator, and secure password/UUID generators."
    override val guideCloudflare: String = "Quickly manage Cloudflare DNS records (A, AAAA, CNAME) and toggle proxy status without the bulky web console."
    override val cfGuideHeader: String = "3-Step Cloudflare DNS & Proxy Setup"
    override val cfStep1Title: String = "1. Create Cloudflare API Token"
    override val cfStep1Desc: String = "In your Cloudflare dashboard, go to My Profile > API Tokens and create a token with Zone.DNS edit permissions."
    override val cfStep2Title: String = "2. Select Zone Domain"
    override val cfStep2Desc: String = "Enter the token above; your active Cloudflare domains/zones will automatically populate."
    override val cfStep3Title: String = "3. Manage DNS Records & Proxying"
    override val cfStep3Desc: String = "Quickly create, edit, or delete A/AAAA/CNAME records and toggle Cloudflare orange-cloud proxy on/off."
    override val vaultGuideHeader: String = "3-Step Zero-Knowledge Vault Setup"
    override val vaultStep1Title: String = "1. Set Master Password"
    override val vaultStep1Desc: String = "Choose a strong master password. All secrets are encrypted client-side using AES-256-GCM and PBKDF2."
    override val vaultStep2Title: String = "2. Store SSH Keys, Root Passwords & Configs"
    override val vaultStep2Desc: String = "Safely store confidential server credentials, private keys, and WireGuard configurations."
    override val vaultStep3Title: String = "3. 1-Click Encrypted Backup"
    override val vaultStep3Desc: String = "Export or restore encrypted backup blobs anytime across your personal devices."
    // Nightwatch UI
    override val monitorHintOn: String = "Background auto-polling is active"
    override val monitorHintOff: String = "Background polling is stopped"
    override val startShort: String = "Start"
    override val stopShort: String = "Stop"
    override val quickConnectGuide: String = "Quick server onboarding"
    override val quickConnectBody: String = "Run this one-liner on any Linux server (Ubuntu, Debian, CentOS, AlmaLinux) to connect it:"
    override val serverAddStep1Title: String = "1. Run Agent Installer on Server"
    override val serverAddStep1Desc: String = "Run this 1-line command on your Linux server terminal via SSH to install and start the agent:"
    override val serverAddStep2Title: String = "2. Copy Connection Link"
    override val serverAddStep2Desc: String = "Once finished, the script prints a secure connection link (didban://...). Copy it."
    override val serverAddStep3Title: String = "3. Connect with 1 Tap"
    override val serverAddStep3Desc: String = "Tap 'Add Server' and hit 'Smart Paste' to auto-populate credentials with TLS encryption."
    override val showGuide: String = "Step-by-step Setup Guide"
    override val hideGuide: String = "Hide Guide"
    override val cmdCopied: String = "Install command copied!"
    override val smartPaste: String = "Smart-paste credentials from clipboard"
    override val clipboardParsed: String = "Credentials detected in clipboard!"
    override val clipboardNotFound: String = "No valid credentials found in clipboard"
    override val testConnection: String = "Test Connection"
    override val testOk: String = "Connection successful!"
    override val testFail: String = "Connection failed"
    override val lblCpuUse: String = "CPU usage"
    override val lblRamUse: String = "Memory"
    override val lblDiskFree: String = "Disk free"
    override val lblUptime: String = "System uptime"
    override val lblNetLive: String = "Live network traffic"
    override val lblLoad1m: String = "CPU load (1m)"
    override val lblLatency: String = "Latency"
    override val lblUptimeServer: String = "Server uptime"
    override val lblCharts24h: String = "24-hour CPU & memory telemetry"
    override val lblCollecting: String = "Collecting history…"
    override val liveProcessWatch: String = "Live process watch"
    override val dockerContainersLbl: String = "Docker containers"
    override val spikeDetective: String = "Spike detective"
    override val processManager: String = "Process manager"
    override val noSpikes24h: String = "No spikes recorded in the last 24 hours"
    override val dockerNotRunning: String = "Docker is not running on this server"
    override val containerRestartedTpl: String = "Container %s restarted"
    override val containerStoppedTpl: String = "Container %s stopped"
    override val restartLbl: String = "Restart"
    override val errorShort: String = "Error"
    override val monitorsTotalLbl: String = "Monitors"
    override val upLbl: String = "Up"
    override val withDowntimeLbl: String = "Down"
    override val noMonitorsTitle: String = "No monitors yet"
    override val noMonitorsBody: String = "Add your first monitor and Didban will keep watch over your services around the clock."
    override val incidentHistory: String = "Incident history"
    override val noIncidents: String = "No incidents recorded for this service"
    override val incidentTpl: String = "%1\$s — down for %2\$d s (%3\$s)"
    override val resumeWatch: String = "Resume"
    override val pauseWatch: String = "Pause"
    override val recheckNow: String = "Recheck"
    override val statusUpdated: String = "Status updated"
    override val deleteMonitorTitle: String = "Delete monitor"
    override val confirmDeleteMonitorTpl: String = "Delete monitor “%s”?"
    override val monitorNameLbl: String = "Monitor label"
    override val protocolTypeLbl: String = "Protocol type"
    override val monitorPortLbl: String = "Port (e.g. 443, 80, 5432, 3306, 6379, 22)"
    override val monitorKeywordLbl: String = "Expected keyword (e.g. ok or status)"
    override val testBeforeSave: String = "Test before saving"
    override val probeOkTpl: String = "Response received! (latency: %d ms)"
    override val probeFail: String = "No response or an error occurred"
    override val editMonitorTitle: String = "Edit monitor"
    override val fleetAllOk: String = "All systems operational"
    override val fleetDownTpl: String = "%d of %d servers unreachable"
    override val statAvgCpu: String = "Avg CPU"
    override val statAvgRam: String = "Avg RAM"
    override val statWorstPing: String = "Worst ping"
    override val fleetUptimeAvg: String = "Fleet uptime average"
}

object Locales {
    val fa: Str = FaStr
    val en: Str = EnStr
}
