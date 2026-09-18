package org.didban.monitor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Structured, bilingual help shown from every command route. */
data class HelpCommand(val label: String, val value: String)

data class CommandHelpContent(
    val summary: String,
    val steps: List<String>,
    val tip: String? = null,
    val warning: String? = null,
    val commands: List<HelpCommand> = emptyList()
)

internal fun CommandRoute.helpContent(language: String): CommandHelpContent {
    val fa = language == "fa"
    fun h(
        faSummary: String, enSummary: String,
        faSteps: List<String>, enSteps: List<String>,
        faTip: String? = null, enTip: String? = null,
        faWarning: String? = null, enWarning: String? = null,
        faCommands: List<HelpCommand> = emptyList(), enCommands: List<HelpCommand> = emptyList()
    ) = CommandHelpContent(
        if (fa) faSummary else enSummary,
        if (fa) faSteps else enSteps,
        if (fa) faTip else enTip,
        if (fa) faWarning else enWarning,
        if (fa) faCommands else enCommands
    )

    val base = when (this) {
        CommandRoute.OVERVIEW -> h("نمای کلی سلامت، مصرف منابع و رخدادهای همه سرورها.", "A fleet-wide view of health, resource usage, and incidents.", listOf("سرورها را اضافه کنید.", "برای دریافت تازه‌ترین داده، نوسازی را بزنید.", "برای جزئیات روی هر سرور یا رخداد بزنید."), listOf("Add your servers.", "Refresh to fetch the latest data.", "Open a server or incident for details."), "نمودارها فقط بر اساس داده‌های ثبت‌شده Agent هستند.", "Charts use data recorded by the Agent only.")
        CommandRoute.INCIDENTS -> h("رخدادهای CPU، حافظه، کانتینر و تونل را بررسی کنید.", "Review CPU, memory, container, and tunnel incidents.", listOf("بازه و سرور را انتخاب کنید.", "رخداد را باز کنید و فرایندهای عامل را ببینید.", "پس از رفع علت، وضعیت زنده را دوباره بررسی کنید."), listOf("Choose a server and time range.", "Open an incident to inspect culprit processes.", "After remediation, verify live health again."))
        CommandRoute.FLEET -> h(
            "اینجا فهرست سرورهاست. برای اولین استفاده باید Agent را روی سرور نصب و سپس اطلاعاتش را در دیدبان ثبت کنید.",
            "This is your server list. First install the Agent on a server, then register its details in Didban.",
            listOf(
                "اگر هنوز سروری ندارید، با SSH یا کنسول وب وارد سرور شوید و اجرا کنید:\ncurl -fsSL https://raw.githubusercontent.com/Mohammad1724/didban/main/agent/install.sh -o didban-install.sh && sudo bash didban-install.sh",
                "در صورت فعال‌بودن UFW اجرا کنید: sudo ufw allow 8686/tcp",
                "خروجی نصب شامل URL، دو توکن Read و Admin و اثر انگشت SHA256 را نگه دارید.",
                "به «مدیریت سرورها» بروید، «افزودن» را بزنید، اطلاعات را وارد کنید و ابتدا «آزمایش Agent» و سپس «ذخیره» را بزنید.",
                "به این صفحه برگردید؛ Online بودن و تأخیر را بررسی کنید و برای جزئیات روی کارت سرور بزنید."
            ),
            listOf(
                "If no server exists, connect over SSH or web console and run:\ncurl -fsSL https://raw.githubusercontent.com/Mohammad1724/didban/main/agent/install.sh -o didban-install.sh && sudo bash didban-install.sh",
                "If UFW is enabled, run: sudo ufw allow 8686/tcp",
                "Save the installer output: URL, Read and Admin tokens, and SHA-256 fingerprint.",
                "Open Manage servers, tap Add, enter those values, then tap Test Agent before Save.",
                "Return here, verify Online state and latency, and tap the server card for details."
            ),
            faWarning = "توکن‌ها محرمانه‌اند؛ آن‌ها را در پیام‌رسان یا تصویر صفحه منتشر نکنید.",
            enWarning = "Tokens are secrets; never publish them in messages or screenshots.",
            faCommands = listOf(
                HelpCommand("نصب Agent", "curl -fsSL https://raw.githubusercontent.com/Mohammad1724/didban/main/agent/install.sh -o didban-install.sh && sudo bash didban-install.sh"),
                HelpCommand("بازکردن پورت UFW", "sudo ufw allow 8686/tcp")
            ),
            enCommands = listOf(
                HelpCommand("Install Agent", "curl -fsSL https://raw.githubusercontent.com/Mohammad1724/didban/main/agent/install.sh -o didban-install.sh && sudo bash didban-install.sh"),
                HelpCommand("Open UFW port", "sudo ufw allow 8686/tcp")
            )
        )
        CommandRoute.SERVER_DOSSIER -> h("اطلاعات و عملیات یک سرور انتخاب‌شده.", "Metrics and operations for the selected server.", listOf("از نوار بالا سرور را انتخاب کنید.", "متریک‌ها و اتصال Agent را بررسی کنید.", "بخش فرایند، Docker یا تونل را باز کنید."), listOf("Select a server from the scope bar.", "Review metrics and Agent connectivity.", "Open processes, Docker, or tunnels."))
        CommandRoute.MANAGE_SERVERS -> h(
            "برای اضافه‌کردن سرور، ابتدا Agent دیدبان را در ترمینال همان سرور نصب کنید؛ سپس اطلاعات چاپ‌شده را دقیقاً در فرم وارد کنید.",
            "To add a server, first install the Didban Agent in that server's terminal, then enter the printed values in the form.",
            listOf(
                "با SSH وارد سرور Ubuntu/Debian شوید. اگر SSH ندارید، کنسول وب شرکت میزبان را باز کنید.",
                "این دستور را کامل در ترمینال کپی و اجرا کنید:\ncurl -fsSL https://raw.githubusercontent.com/Mohammad1724/didban/main/agent/install.sh -o didban-install.sh && sudo bash didban-install.sh",
                "اگر فایروال UFW فعال است، این دستور را اجرا کنید:\nsudo ufw allow 8686/tcp",
                "در پایان نصب، چهار مقدار Server URL، Read token، Admin token و Cert SHA256 نمایش داده می‌شود؛ آن‌ها را در جای امن نگه دارید.",
                "در دیدبان روی «افزودن» بزنید؛ نام دلخواه، IP یا دامنه سرور، پورت 8686، Read token، Admin token و اثر انگشت SHA256 را وارد کنید.",
                "TLS را روشن نگه دارید. اثر انگشت را بدون فاصله و دقیقاً مطابق خروجی نصب وارد کنید.",
                "«آزمایش Agent» را بزنید. فقط اگر وضعیت اتصال موفق بود، «ذخیره» را بزنید.",
                "اگر اتصال نشد، روی سرور اجرا کنید: sudo systemctl status didban-agent --no-pager و بازبودن پورت 8686 در فایروال سرور و پنل میزبان را بررسی کنید."
            ),
            listOf(
                "Connect to the Ubuntu/Debian server over SSH, or open your provider's web console.",
                "Copy and run this complete command:\ncurl -fsSL https://raw.githubusercontent.com/Mohammad1724/didban/main/agent/install.sh -o didban-install.sh && sudo bash didban-install.sh",
                "If UFW is enabled, run:\nsudo ufw allow 8686/tcp",
                "At the end, save the Server URL, Read token, Admin token, and Cert SHA256 shown by the installer.",
                "In Didban tap Add, then enter a friendly name, server IP/domain, port 8686, both tokens, and the SHA-256 fingerprint.",
                "Keep TLS enabled and copy the fingerprint exactly, without spaces.",
                "Tap Test Agent. Save only after the connection succeeds.",
                "If it fails, run sudo systemctl status didban-agent --no-pager and verify port 8686 in both server and provider firewalls."
            ),
            faTip = "Read token فقط برای مشاهده است؛ Admin token برای توقف، راه‌اندازی مجدد و تغییرات مدیریتی استفاده می‌شود.",
            enTip = "The Read token is for monitoring; the Admin token authorizes stop, restart, and configuration actions.",
            faWarning = "توکن‌ها و اثر انگشت را برای کسی نفرستید و از منابع ناشناس دستور نصب نگیرید.",
            enWarning = "Never share tokens or fingerprints, and do not run installer commands from untrusted sources.",
            faCommands = listOf(
                HelpCommand("نصب Agent", "curl -fsSL https://raw.githubusercontent.com/Mohammad1724/didban/main/agent/install.sh -o didban-install.sh && sudo bash didban-install.sh"),
                HelpCommand("بازکردن پورت UFW", "sudo ufw allow 8686/tcp"),
                HelpCommand("بررسی وضعیت Agent", "sudo systemctl status didban-agent --no-pager")
            ),
            enCommands = listOf(
                HelpCommand("Install Agent", "curl -fsSL https://raw.githubusercontent.com/Mohammad1724/didban/main/agent/install.sh -o didban-install.sh && sudo bash didban-install.sh"),
                HelpCommand("Open UFW port", "sudo ufw allow 8686/tcp"),
                HelpCommand("Check Agent status", "sudo systemctl status didban-agent --no-pager")
            )
        )
        CommandRoute.TUNNELS -> h("تونل‌های سرور را مشاهده و مدیریت کنید.", "View and manage tunnels on a server.", listOf("سرور را انتخاب کنید.", "سلامت و تأخیر هر تونل را بررسی کنید.", "برای ساخت یا ویرایش، فرم تونل را باز کنید."), listOf("Select a server.", "Check each tunnel's health and latency.", "Open the tunnel editor to create or edit one."), faWarning = "توقف یا حذف تونل می‌تواند ترافیک فعال را قطع کند.", enWarning = "Stopping or deleting a tunnel can interrupt active traffic.")
        CommandRoute.TUNNELS_EDITOR -> h("موتور، نقش نود و نگاشت پورت‌های تونل را تنظیم کنید.", "Configure tunnel engine, node role, and port mappings.", listOf("موتور و نقش ایران/خارج را انتخاب کنید.", "آدرس طرف مقابل، توکن و پورت‌ها را وارد کنید.", "پیش‌نمایش را بررسی و سپس اعمال کنید."), listOf("Choose the engine and Iran/foreign node role.", "Enter peer address, token, and port mappings.", "Review the preview before applying."), "نگاشت نمونه: 443:8443, 80:8080", "Example mapping: 443:8443, 80:8080", "تنظیم اشتباه فایروال یا مسیر می‌تواند دسترسی SSH را مختل کند.", "Incorrect firewall or routing changes can disrupt SSH access.")
        CommandRoute.DOCKER -> h("کانتینرهای Docker را پایش، متوقف یا راه‌اندازی مجدد کنید.", "Monitor, stop, or restart Docker containers.", listOf("سرور را انتخاب کنید.", "State و Health کانتینر را بررسی کنید.", "فقط در صورت نیاز Stop یا Restart را اجرا کنید."), listOf("Select a server.", "Review container state and health.", "Use Stop or Restart only when needed."), faWarning = "توقف کانتینر ممکن است سرویس عمومی را از دسترس خارج کند.", enWarning = "Stopping a container may take a public service offline.")
        CommandRoute.PROCESSES -> h("فرایندهای پرمصرف را پیدا و مدیریت کنید.", "Find and manage resource-heavy processes.", listOf("سرور را انتخاب کنید.", "بر اساس CPU یا حافظه مرتب کنید.", "ابتدا SIGTERM و فقط در ضرورت SIGKILL بفرستید."), listOf("Select a server.", "Sort by CPU or memory.", "Try SIGTERM first; use SIGKILL only if necessary."), faWarning = "کشتن PIDهای سیستمی ممکن است سرور را ناپایدار کند.", enWarning = "Killing system PIDs can destabilize the server.")
        CommandRoute.SERVICES -> h("سرویس‌های systemd را مشاهده و کنترل کنید.", "Inspect and control systemd services.", listOf("سرور را انتخاب و سرویس‌ها را بارگذاری کنید.", "وضعیت و لاگ را بررسی کنید.", "Start، Stop یا Restart را آگاهانه اجرا کنید."), listOf("Select a server and load services.", "Review status and logs.", "Run Start, Stop, or Restart deliberately."))
        CommandRoute.RADAR -> h("دسترسی‌پذیری سرور را از چند نقطه بررسی کنید.", "Check server reachability from multiple vantage points.", listOf("سرور یا مقصد را انتخاب کنید.", "نوع Ping، HTTP یا TCP را انتخاب کنید.", "نتایج نقاط مختلف را مقایسه کنید."), listOf("Choose a server or target.", "Select Ping, HTTP, or TCP.", "Compare results across locations."), "شکست یک نقطه به‌تنهایی به معنی قطعی کامل نیست.", "A single failed probe does not prove a full outage.")
        CommandRoute.BANDWIDTH -> h("سرعت واقعی مسیر سرور را با آزمون محدود اندازه بگیرید.", "Measure server path throughput with a bounded test.", listOf("سرور را انتخاب کنید.", "اندازه و مدت تست را محافظه‌کارانه تعیین کنید.", "Download و Upload را جداگانه مقایسه کنید."), listOf("Select a server.", "Choose a conservative size and duration.", "Compare download and upload separately."), faWarning = "تست پهنای‌باند مصرف ترافیک و بار شبکه ایجاد می‌کند.", enWarning = "Bandwidth tests consume traffic and create network load.")
        CommandRoute.CF_SCANNER -> h("IPهای Cloudflare را از نظر تأخیر و دسترسی اسکن کنید.", "Scan Cloudflare IPs for reachability and latency.", listOf("فهرست پیش‌فرض یا CIDR سفارشی وارد کنید.", "محدودیت هم‌زمانی و timeout را تنظیم کنید.", "نتایج برتر را دوباره اعتبارسنجی و سپس کپی کنید."), listOf("Use the default list or enter custom CIDRs.", "Set concurrency and timeout limits.", "Revalidate top results before copying them."), faWarning = "فقط محدوده‌هایی را اسکن کنید که مجاز به بررسی آن‌ها هستید.", enWarning = "Only scan ranges you are authorized to test.")
        CommandRoute.REALITY_SNI -> h("دامنه‌های مناسب برای SNI/REALITY را اعتبارسنجی کنید.", "Validate candidate domains for SNI/REALITY use.", listOf("دامنه‌های کاندید را وارد کنید.", "TLS، گواهی و پاسخ مقصد را آزمایش کنید.", "نتیجه پایدار و سازگار با نیاز خود را انتخاب کنید."), listOf("Enter candidate domains.", "Test TLS, certificate, and destination response.", "Choose a stable result compatible with your setup."), faWarning = "مالکیت دامنه و قوانین شبکه محل استفاده را رعایت کنید.", enWarning = "Respect domain ownership and applicable network rules.")
        CommandRoute.UPTIME -> h("مانیتورها، heartbeat و رخدادهای قطعی را مدیریت کنید.", "Manage monitors, heartbeats, and downtime incidents.", listOf("یک مانیتور HTTP، TCP، Ping، Keyword یا SSL بسازید.", "فاصله بررسی و timeout را تعیین کنید.", "نوار heartbeat و رخدادها را بررسی کنید."), listOf("Create an HTTP, TCP, Ping, Keyword, or SSL monitor.", "Set interval and timeout.", "Review heartbeat bars and incidents."))
        CommandRoute.UPTIME_EDITOR -> h("شرایط بررسی و هشدار یک مانیتور را تعریف کنید.", "Define checks and alerts for a monitor.", listOf("نوع مانیتور و مقصد را وارد کنید.", "برای Keyword متن مورد انتظار را تعیین کنید.", "تنظیمات را ذخیره و اولین نتیجه را تأیید کنید."), listOf("Choose the monitor type and target.", "For Keyword checks, enter expected text.", "Save and verify the first result."))
        CommandRoute.NETWORK_TOOLS -> h("برای مسئله شبکه، ابزار تشخیصی مناسب را انتخاب کنید.", "Choose the right diagnostic tool for a network issue.", listOf("نوع مشکل: دسترسی، TLS، DNS یا کیفیت را مشخص کنید.", "ابزار پیشنهادی را باز کنید.", "نتیجه را با بیش از یک آزمون تأیید کنید."), listOf("Identify reachability, TLS, DNS, or quality issues.", "Open the suggested tool.", "Confirm findings with more than one test."))
        CommandRoute.NETWORK_TOOLS_EDITOR -> h("Ping، TCP، پورت، SSL و GeoIP را اجرا کنید.", "Run Ping, TCP, port, SSL, and GeoIP diagnostics.", listOf("ابزار را انتخاب کنید.", "فقط hostname/IP و پورت معتبر وارد کنید.", "خروجی و زمان پاسخ را تفسیر کنید."), listOf("Choose a tool.", "Enter a valid hostname/IP and port.", "Interpret output and response time."))
        CommandRoute.DNS -> h("بین مدیریت Cloudflare و عیب‌یابی DNS انتخاب کنید.", "Choose Cloudflare management or DNS diagnostics.", listOf("برای رکوردها، مدیریت Cloudflare را باز کنید.", "برای resolve و بررسی DNS، ابزار شبکه را باز کنید.", "پس از تغییر، انتشار DNS را تأیید کنید."), listOf("Open Cloudflare management for records.", "Open network tools for DNS resolution.", "After changes, verify DNS propagation."))
        CommandRoute.DNS_EDITOR -> h("Zoneها و رکوردهای Cloudflare را مدیریت کنید.", "Manage Cloudflare zones and DNS records.", listOf("API Token محدود به Zone وارد کنید.", "Zone و رکورد را انتخاب کنید.", "مقدار، TTL و Proxy را بررسی و ذخیره کنید."), listOf("Use a zone-scoped API token.", "Choose a zone and record.", "Review value, TTL, and proxy mode before saving."), faWarning = "توکن Global API Key استفاده نکنید؛ حداقل دسترسی لازم را بدهید.", enWarning = "Avoid Global API Keys; grant the minimum required scope.")
        CommandRoute.WORKBENCH_HOME -> h("ابزار عملیاتی مناسب برای کار روی سرور را انتخاب کنید.", "Choose an operational tool for server work.", listOf("برای فرمان تعاملی SSH را انتخاب کنید.", "برای چند سرور Batch و برای فایل SFTP را انتخاب کنید.", "ابزارهای Proxy و Port را فقط روی مقصد مجاز اجرا کنید."), listOf("Use SSH for interactive commands.", "Use Batch for many servers and SFTP for files.", "Run proxy and port tools only against authorized targets."))
        CommandRoute.SSH -> h("ترمینال SSH امن به سرور انتخاب‌شده باز کنید.", "Open a secure SSH terminal to the selected server.", listOf("سرور و روش احراز هویت را انتخاب کنید.", "کلید میزبان را با منبع معتبر تطبیق دهید.", "فرمان را اجرا و خروجی را قبل از ادامه بررسی کنید."), listOf("Choose a server and authentication method.", "Verify the host key using a trusted source.", "Run commands and review output before continuing."), faWarning = "فرمان‌های root و اسکریپت ناشناس می‌توانند کنترل سرور را از بین ببرند.", enWarning = "Root commands and unknown scripts can compromise the server.")
        CommandRoute.BATCH -> h("یک فرمان را روی چند سرور اجرا کنید.", "Run one command across multiple servers.", listOf("سرورهای مقصد را انتخاب کنید.", "فرمان کم‌خطر را ابتدا روی یک سرور آزمایش کنید.", "Batch را اجرا و خروجی هر سرور را جدا بررسی کنید."), listOf("Select target servers.", "Test a low-risk command on one server first.", "Run the batch and inspect each server's output."), faWarning = "خطای یک فرمان Batch می‌تواند هم‌زمان چند سرور را تحت تأثیر قرار دهد.", enWarning = "A bad batch command can affect multiple servers at once.")
        CommandRoute.SFTP -> h("فایل‌ها را از طریق SFTP منتقل و مدیریت کنید.", "Transfer and manage files over SFTP.", listOf("سرور را انتخاب و اتصال را باز کنید.", "مسیر محلی و راه‌دور را دوباره بررسی کنید.", "انتقال را شروع و کامل‌شدن فایل را تأیید کنید."), listOf("Select a server and connect.", "Double-check local and remote paths.", "Start the transfer and verify completion."), faWarning = "بازنویسی یا حذف فایل‌های سیستمی می‌تواند سرویس را خراب کند.", enWarning = "Overwriting or deleting system files can break services.")
        CommandRoute.SINGLE_PORT -> h("دسترسی و تأخیر یک پورت TCP را بررسی کنید.", "Check reachability and latency for one TCP port.", listOf("مقصد و پورت را وارد کنید.", "timeout و تعداد تلاش را تعیین کنید.", "نتیجه را با وضعیت فایروال و سرویس مقایسه کنید."), listOf("Enter a target and port.", "Set timeout and attempt count.", "Compare results with firewall and service state."))
        CommandRoute.PROXY -> h("اتصال یک Proxy را بدون تغییر تنظیمات کل دستگاه آزمایش کنید.", "Test a proxy without changing device-wide settings.", listOf("نوع Proxy، آدرس و پورت را وارد کنید.", "در صورت نیاز احراز هویت را اضافه کنید.", "یک مقصد آزمون معتبر انتخاب و نتیجه را بررسی کنید."), listOf("Enter proxy type, address, and port.", "Add authentication if required.", "Choose a valid test destination and review the result."), faWarning = "اطلاعات Proxy را در مقصد یا لاگ نامطمئن وارد نکنید.", enWarning = "Do not expose proxy credentials to untrusted targets or logs.")
        CommandRoute.DEVELOPER_LAB -> h("ابزارهای تبدیل و تحلیل متن و شبکه را به‌صورت محلی اجرا کنید.", "Run local text, encoding, and network utilities.", listOf("ابزار Base64، JSON، CIDR، JWT یا Generator را انتخاب کنید.", "ورودی را وارد و عملیات را اجرا کنید.", "خروجی را بررسی و در صورت نیاز کپی کنید."), listOf("Choose Base64, JSON, CIDR, JWT, or a generator.", "Enter input and run the operation.", "Review and copy the output if needed."), "JWT فقط decode می‌شود؛ معتبر بودن امضا را تضمین نمی‌کند.", "JWT decoding does not prove that its signature is valid.")
        CommandRoute.PROTECT_HOME -> h("ابزار حفاظت از Secrets، هشدار و پشتیبان را انتخاب کنید.", "Choose tools for secrets, alerts, and backups.", listOf("Secrets را در Vault نگه دارید.", "کانال هشدار را آزمایش کنید.", "به‌طور منظم خروجی پشتیبان رمزگذاری‌شده بگیرید."), listOf("Store secrets in the Vault.", "Test alert channels.", "Export encrypted backups regularly."))
        CommandRoute.VAULT -> h("اطلاعات حساس را در Vault رمزگذاری‌شده نگه دارید.", "Store sensitive data in the encrypted Vault.", listOf("Vault را با گذرواژه قوی باز یا ایجاد کنید.", "Secret را با عنوان قابل تشخیص ذخیره کنید.", "پس از استفاده Vault را قفل کنید."), listOf("Create or unlock the Vault with a strong password.", "Store a secret with a recognizable label.", "Lock the Vault after use."), faWarning = "فراموشی گذرواژه ممکن است بازیابی داده را ناممکن کند.", enWarning = "A forgotten password may make recovery impossible.")
        CommandRoute.SECURITY -> h("وضعیت امنیت، پورت‌ها و محدودیت‌های سرور را بررسی کنید.", "Review server security, ports, and restrictions.", listOf("سرور را انتخاب کنید.", "پورت‌های شنونده و رخدادهای مشکوک را بررسی کنید.", "قبل از اعمال Ban یا تغییر، IP و اثر آن را تأیید کنید."), listOf("Select a server.", "Review listening ports and suspicious events.", "Verify an IP and impact before applying a ban or change."), faWarning = "مسدودکردن IP اشتباه می‌تواند دسترسی مدیریتی شما را قطع کند.", enWarning = "Blocking the wrong IP can lock you out of administration.")
        CommandRoute.ALERTS -> h("Telegram، Discord یا Webhook هشدارها را پیکربندی کنید.", "Configure Telegram, Discord, or webhook alerts.", listOf("کانال و اطلاعات اتصال را وارد کنید.", "پیام آزمایشی ارسال کنید.", "پس از دریافت موفق، هشدارهای موردنظر را فعال کنید."), listOf("Enter channel connection details.", "Send a test message.", "Enable desired alerts after successful delivery."), faWarning = "توکن Bot و URL وب‌هوک محرمانه هستند.", enWarning = "Bot tokens and webhook URLs are secrets.")
        CommandRoute.BACKUP -> h("تنظیمات و داده‌های برنامه را خروجی یا بازیابی کنید.", "Export or restore app settings and data.", listOf("Vault را باز کنید.", "پشتیبان رمزگذاری‌شده بگیرید و امن نگه دارید.", "برای بازیابی، فایل را انتخاب و پیش‌نمایش را تأیید کنید."), listOf("Unlock the Vault.", "Create an encrypted backup and store it safely.", "For restore, select the file and confirm the preview."), faWarning = "بازیابی ممکن است داده‌های فعلی را جایگزین کند.", enWarning = "Restore may replace current data.")
        CommandRoute.SETTINGS -> h("زبان، تم، Vault و رفتار عمومی برنامه را تنظیم کنید.", "Configure language, theme, Vault, and app behavior.", listOf("زبان و تم را انتخاب کنید.", "وضعیت Vault و تنظیمات امنیتی را بررسی کنید.", "پس از تغییرات، صفحات اصلی را دوباره بررسی کنید."), listOf("Choose language and theme.", "Review Vault and security settings.", "Recheck key screens after changes."))
    }

    // A beginner must never land on an operational page without knowing the
    // shared prerequisite. Keep this centralized so new help pages cannot
    // silently omit the server/Agent setup contract.
    val serverRequired = this in setOf(
        CommandRoute.OVERVIEW, CommandRoute.INCIDENTS, CommandRoute.FLEET,
        CommandRoute.SERVER_DOSSIER, CommandRoute.TUNNELS, CommandRoute.TUNNELS_EDITOR,
        CommandRoute.DOCKER, CommandRoute.PROCESSES, CommandRoute.SERVICES,
        CommandRoute.RADAR, CommandRoute.BANDWIDTH, CommandRoute.SSH,
        CommandRoute.BATCH, CommandRoute.SFTP, CommandRoute.SECURITY,
        CommandRoute.ALERTS
    )
    if (!serverRequired || this == CommandRoute.MANAGE_SERVERS || this == CommandRoute.FLEET) return base

    val prerequisite = if (fa) {
        "پیش‌نیاز: باید حداقل یک سرورِ آزمایش‌شده داشته باشید. اگر ندارید، از منوی «سرورها ← مدیریت سرورها ← افزودن» وارد شوید و راهنمای نصب Agent همان صفحه را قدم‌به‌قدم انجام دهید."
    } else {
        "Prerequisite: you need at least one tested server. If none exists, open Servers → Manage servers → Add and follow that page's Agent installation guide step by step."
    }
    return base.copy(steps = listOf(prerequisite) + base.steps)
}

@Composable
fun CommandHelpDialog(route: CommandRoute, language: String, copy: CommandCopy, onDismiss: () -> Unit) {
    val content = route.helpContent(language)
    val fa = language == "fa"
    val clipboard = LocalClipboardManager.current
    var copiedCommand by remember(route) { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.82f),
            shape = RoundedCornerShape(20.dp),
            color = CommandColors.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, CommandColors.borderStrong)
        ) {
            Column(Modifier.padding(CommandSpacing.lg)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.HelpOutline, contentDescription = null, tint = CommandColors.accent, modifier = Modifier.size(28.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(if (fa) "راهنمای صفحه" else "Page guide", color = CommandColors.textTertiary, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                        Text(route.commandLabel(copy), color = CommandColors.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, if (fa) "بستن" else "Close", tint = CommandColors.textSecondary) }
                }
                CommandRule(Modifier.padding(vertical = CommandSpacing.md))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(CommandSpacing.md)) {
                    item { Text(content.summary, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge) }
                    if (content.commands.isNotEmpty()) {
                        item { Text(if (fa) "دستورهای آماده" else "Ready-to-copy commands", color = CommandColors.textPrimary, fontWeight = FontWeight.Bold) }
                        itemsIndexed(content.commands) { _, command ->
                            CommandSurface(Modifier.fillMaxWidth(), raised = true) {
                                Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(command.label, color = CommandColors.textPrimary, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                        CommandTextButton(
                                            if (copiedCommand == command.value) {
                                                if (fa) "کپی شد" else "Copied"
                                            } else {
                                                if (fa) "کپی" else "Copy"
                                            },
                                            {
                                                clipboard.setText(AnnotatedString(command.value))
                                                copiedCommand = command.value
                                            },
                                            Icons.Rounded.ContentCopy
                                        )
                                    }
                                    SelectionContainer {
                                        Text(command.value, color = CommandColors.info, style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontFamily = Telemetry))
                                    }
                                }
                            }
                        }
                    }
                    item { Text(if (fa) "مراحل استفاده" else "How to use", color = CommandColors.textPrimary, fontWeight = FontWeight.Bold) }
                    itemsIndexed(content.steps) { index, step ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text("${index + 1}", color = CommandColors.onAccent, modifier = Modifier.padding(end = CommandSpacing.sm), fontWeight = FontWeight.Bold)
                            SelectionContainer {
                                Text(step, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    content.tip?.let { tip -> item { HelpNote(Icons.Rounded.Lightbulb, if (fa) "نکته" else "Tip", tip, CommandColors.info) } }
                    content.warning?.let { warning -> item { HelpNote(Icons.Rounded.WarningAmber, if (fa) "هشدار" else "Warning", warning, CommandColors.warning) } }
                    item { HelpNote(Icons.Rounded.CheckCircle, if (fa) "پیشنهاد" else "Recommendation", if (fa) "بعد از هر عملیات، نتیجه و وضعیت زنده را دوباره بررسی کنید." else "After every operation, verify the result and live state again.", CommandColors.success) }
                    item { Spacer(Modifier.height(CommandSpacing.sm)) }
                }
            }
        }
    }
}

@Composable
private fun HelpNote(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, color: Color) {
    CommandSurface(Modifier.fillMaxWidth(), raised = true) {
        Row(Modifier.padding(CommandSpacing.md), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(21.dp))
            Spacer(Modifier.size(CommandSpacing.sm))
            Column {
                Text(title, color = color, fontWeight = FontWeight.Bold, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(CommandSpacing.xxs))
                Text(body, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
