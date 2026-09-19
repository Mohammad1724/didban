package org.didban.monitor

/** Offline, route-specific beginner guides. No network, credentials or actions here. */
internal fun CommandRoute.helpContent(language: String): CommandHelpContent {
    val fa = language == "fa"
    fun h(faSummary: String, enSummary: String, faSteps: List<String>, enSteps: List<String>,
          faTip: String, enTip: String, faWarning: String, enWarning: String) = CommandHelpContent(
        summary = if (fa) faSummary else enSummary,
        steps = if (fa) faSteps else enSteps,
        tip = if (fa) faTip else enTip,
        warning = if (fa) faWarning else enWarning
    )
    val content = when (this) {
        CommandRoute.OVERVIEW -> h(
            "برای اینکه یک‌جا ببینید کدام سرور سالم است و کدام سرور نیاز به رسیدگی دارد؛ مصرف پردازنده، رم و وضعیت اتصال را کنار هم می‌بینید.",
            "See which servers are healthy and which need attention, with processor use, memory use and connection status together.",
            listOf(
                "از «سرورها» سرور خود را اضافه و اتصالش را آزمایش کنید.",
                "نوسازی را بزنید و منتظر نتیجه بمانید.",
                "روی سرور موردنظر بزنید تا جزئیات و ابزارهای همان سرور باز شود.",
            ),
            listOf(
                "Add and test your server under Servers.",
                "Refresh and wait for a result.",
                "Open a server to see its details and tools.",
            ),
            "مثال: مصرف رم بالا با قطع‌بودن سایت یکی نیست؛ برای پاسخ‌دادن سایت از پایش دسترس‌پذیری استفاده کنید.",
            "High memory use is not the same as a website outage. Use Uptime to check whether a site responds.",
            "دادهٔ قدیمی یا دریافت‌نشدن داده به معنی سالم‌بودن سرور نیست. در ناوبری فعلی نمای کلی در صفحهٔ سرورها ادغام شده است.",
            "Missing or old data does not mean healthy. In current navigation, the overview is part of Servers."
        )
        CommandRoute.INCIDENTS -> h(
            "برای پیدا کردن وضعیت‌هایی که نیاز به رسیدگی دارند، مثل قطع ارتباط یا فشار روی منابع؛ این بخش خودش مشکل را تعمیر نمی‌کند.",
            "Find conditions that need attention, such as lost connections or resource pressure. This does not repair the problem for you.",
            listOf(
                "صفحهٔ سرورها را باز و اطلاعات را نوسازی کنید.",
                "سروری را که هشدار دارد باز کنید.",
                "مصرف منابع و وضعیت ارتباط را ببینید؛ پس از رفع علت دوباره نوسازی کنید.",
            ),
            listOf(
                "Open Servers and refresh.",
                "Open a server showing an alert.",
                "Check resources and connectivity, then refresh after addressing the cause.",
            ),
            "مثال: اگر پردازنده شلوغ است، از جزئیات همان سرور وارد فرایندها شوید.",
            "For high processor use, open Processes from that server’s details.",
            "هشدارها در صفحهٔ یکپارچهٔ سرورها نمایش داده می‌شوند؛ آن‌ها را گزارش کامل همهٔ اتفاقات گذشته فرض نکنید.",
            "Alerts appear in the unified server page; they are not a complete historical audit log."
        )
        CommandRoute.FLEET -> h(
            "خانهٔ سرورهای شماست: سرور اضافه می‌کنید، سلامت آن را می‌بینید و ابزارهای همان سرور را باز می‌کنید. Agent برنامهٔ کوچکی است که روی سرور نصب می‌شود تا اطلاعاتش را به دیدبان بدهد.",
            "The home for your servers: add a server, see its health and open its tools. The Agent is a small program installed on the server to provide data to Didban.",
            listOf(
                "«افزودن سرور» را بزنید؛ اگر اولین بار است راهنمای همان فرم را باز کنید و Agent را روی سرور نصب کنید.",
                "خط didban:// خروجی نصب را در «کد اتصال فوری» بچسبانید و دکمهٔ واردکردن را بزنید؛ یا اطلاعات را دستی وارد کنید.",
                "نام دلخواه بگذارید، «آزمایش Agent» را بزنید و نتیجه را بررسی کنید؛ سپس ذخیره کنید.",
                "روی کارت سرور بزنید؛ از جزئیاتش Docker، SSH یا ابزار موردنظر را باز کنید. رادار در بخش پایش است.",
            ),
            listOf(
                "Tap Add server. On first use, open the form’s guide and install the Agent on your server.",
                "Paste the installer’s didban:// line into Quick-connect code and press the import button, or fill in the fields manually.",
                "Choose a name, use Test Agent and review the result, then save.",
                "Open the server card, then choose Docker, SSH or another tool from its details. Find Radar in Monitoring.",
            ),
            "نوسازی، داده‌ها را دوباره می‌گیرد. ویرایش و حذف از جزئیات همان سرور انجام می‌شود؛ لازم نیست برای هر ابزار سرور را دوباره بسازید.",
            "Refresh fetches data again. Edit or delete from server details; do not re-add a server for each tool.",
            "کد اتصال و توکن‌ها محرمانه‌اند. حذف از دیدبان فقط رکورد گوشی را پاک می‌کند و Agent سرور را حذف نمی‌کند.",
            "Connection codes and tokens are secrets. Deleting a server in Didban only removes its phone record, not its Agent."
        )
        CommandRoute.SERVER_DOSSIER -> h(
            "پروندهٔ یک سرور است: وضعیت اتصال، مصرف منابع و ابزارهای همان سرور را نشان می‌دهد تا اشتباهی روی سرور دیگری کار نکنید.",
            "The details of one server: connectivity, resource use and tools scoped to it, helping you avoid acting on the wrong server.",
            listOf(
                "از فهرست سرورها، کارت سرور موردنظر را باز کنید.",
                "نام و آدرس را بررسی و نوسازی را بزنید.",
                "برای تغییر اطلاعات، ویرایش را بزنید؛ برای کار عملیاتی، ابزار همان سرور را انتخاب کنید.",
            ),
            listOf(
                "Open the desired server card.",
                "Verify its name and address, then refresh.",
                "Use Edit for connection details or select a tool for operations on this server.",
            ),
            "مثال: Docker را از اینجا باز کنید تا همان سرور انتخاب شود.",
            "Open Docker here to carry this server selection into the tool.",
            "پیش از توقف سرویس یا حذف رکورد، نام سرور را دوباره بررسی کنید. حذف رکورد، خاموش‌کردن سرور نیست.",
            "Double-check the server before stopping services or deleting its record. Removing a record does not shut down the server."
        )
        CommandRoute.MANAGE_SERVERS -> h(
            "برای وصل‌کردن دیدبان به سرور یا اصلاح اطلاعات اتصال است. آدرس می‌گوید کجا وصل شویم، توکن اجازهٔ دسترسی می‌دهد و اثر انگشت گواهی کمک می‌کند هویت سرور درست بررسی شود.",
            "Connect Didban to a server or edit its connection. The address identifies the destination, the token grants access and the certificate fingerprint helps verify server identity.",
            listOf(
                "اگر قبلاً Agent نصب است، همان اطلاعات اتصال را استفاده کنید؛ برای ویرایش لازم نیست دوباره نصب کنید.",
                "برای نصب اولیه، با SSH یا کنسول شرکت میزبان وارد سرور شوید؛ دستور نصب پایین را بخوانید و اجرا کنید.",
                "خروجی نصب شامل URL، توکن Read، توکن Admin، اثر انگشت SHA256 و کد didban:// را در جای امن نگه دارید.",
                "کد کامل را در «کد اتصال فوری» قرار دهید و دکمهٔ واردکردن را بزنید؛ یا نام، آدرس، پورت و توکن‌ها را دستی وارد کنید.",
                "TLS را روشن نگه دارید و اثر انگشت را از خروجی معتبر سرور بردارید. پورت Agent را مطابق خروجی نصب در فایروال سرور و میزبان بررسی کنید.",
                "«آزمایش Agent» را بزنید؛ پس از موفقیت ذخیره کنید. اگر وصل نشد، آدرس، پورت، توکن و وضعیت سرویس Agent را بررسی کنید.",
            ),
            listOf(
                "If the Agent is installed, reuse its connection details; editing does not require reinstalling.",
                "For first installation, connect using SSH or your provider’s console. Review and run the installer command below.",
                "Securely keep the printed URL, Read token, Admin token, SHA256 fingerprint and didban:// code.",
                "Paste the whole code into Quick-connect code and press import, or manually enter the name, address, port and tokens.",
                "Keep TLS enabled and obtain the fingerprint from a trusted server source. Check the Agent port shown by the installer in both server and provider firewalls.",
                "Use Test Agent, then save after success. On failure, check address, port, tokens and the Agent service status.",
            ),
            "Read برای مشاهده است؛ Admin برای تغییرات مدیریتی. پورت معمول Agent برابر 8686 است و با پورت SSH یکی نیست.",
            "Read grants monitoring access; Admin permits management actions. The usual Agent port is 8686, which is different from SSH.",
            "دستور نصب را قبل از اجرا بررسی کنید. نصب پیش‌فرض نسخهٔ منتشرشده را می‌گیرد؛ قابلیت جدید اپ ممکن است Agent جدیدتر بخواهد. فقط دسترسی لازم را در فایروال باز کنید و توکن‌ها را منتشر نکنید.",
            "Review the installer before running it. Default installation uses a published release; new app features may need a newer Agent. Restrict firewall access and never publish tokens."
        )
        CommandRoute.TUNNELS -> h(
            "برای دیدن و مدیریت اتصال بین دو سرور است؛ تونل ترافیک را از یک سمت به سمت دیگر می‌برد، مثلاً از سرور ایران به سرور خارج.",
            "View and manage links between two servers. A tunnel carries traffic from one side to the other, for example between an Iran node and a foreign node.",
            listOf(
                "سرورهای دو سمت را در دیدبان ثبت کنید.",
                "بخش پیکربندی تونل را باز و یک تونل بسازید؛ راهنمای همان فرم جزئیات ورودی‌ها را می‌گوید.",
                "پس از ذخیره، وضعیت و آزمایش اتصال را بررسی کنید.",
                "فقط برای تونل موردنظر، عملیات توقف یا راه‌اندازی مجدد را انجام دهید.",
            ),
            listOf(
                "Register the servers for both ends.",
                "Open tunnel configuration and create a tunnel; its guide explains the fields.",
                "After saving, check status and run a connection test.",
                "Stop or restart only the intended tunnel.",
            ),
            "مثال: پورت ورودی یک سرور را به پورت سرویس در سمت دیگر وصل کنید؛ خود سرویس مقصد باید قبلاً فعال باشد.",
            "For example, map an incoming port to a service at the other end. That service must already be running.",
            "ذخیرهٔ تنظیمات به‌تنهایی تضمین نصب و فعال‌شدن هر دو سمت نیست. تغییر تونل زنده ممکن است کاربران را قطع کند.",
            "Saving settings alone does not prove both ends are deployed and working. Changes to a live tunnel may disconnect users."
        )
        CommandRoute.TUNNELS_EDITOR -> h(
            "برای ساخت یا ویرایش مسیر تونل است: انتخاب می‌کنید چه نرم‌افزاری استفاده شود، هر سرور کدام سمت باشد و ترافیک به کدام پورت برسد.",
            "Create or edit a tunnel: choose its software, each node’s role and the ports carrying traffic.",
            listOf(
                "موتور تونل و نقش هر سمت را انتخاب و سرورهای مرتبط را مشخص کنید.",
                "آدرس طرف مقابل، پورت و توکن مشترک لازم را وارد کنید؛ تنظیمات دو سمت باید سازگار باشند.",
                "نگاشت پورت را مطابق فرم وارد کنید؛ مثلاً 443:8443 برای نگاشت بین این دو پورت.",
                "ابتدا کد تولیدشده را بررسی کنید؛ «ذخیره» فقط تنظیمات را نگه می‌دارد و استقرار عملیات جداگانه است.",
                "اگر استقرار را می‌خواهید، دسترسی مدیریتی لازم را آماده کنید؛ پس از اجرای آن اتصال و وضعیت هر دو سمت را آزمایش کنید.",
            ),
            listOf(
                "Choose the tunnel engine, each side’s role and its servers.",
                "Enter the peer address, port and required shared token; both ends must agree.",
                "Enter port mappings as requested, for example 443:8443 between those ports.",
                "Review generated code first. Save stores configuration; deployment is a separate action.",
                "For deployment, provide the required management access, then test the connection and status at both ends.",
            ),
            "توکن تونل، توکن Agent و رمز SSH یک چیز نیستند؛ هرکدام را فقط در فیلد مربوط به خودش وارد کنید.",
            "The tunnel token, Agent token and SSH password are different credentials; use each in its own field.",
            "پیش از استقرار، نسخهٔ پشتیبان و دسترسی کنسول میزبان داشته باشید؛ تغییر پورت یا مسیر می‌تواند ارتباط مدیریتی را قطع کند.",
            "Keep a backup and provider-console access before deployment; changing ports or routes may cut off administration."
        )
        CommandRoute.DOCKER -> h(
            "کانتینر یعنی یک محیط جدا برای اجرای برنامه. اینجا می‌بینید برنامه‌های Docker روی سرور اجرا هستند یا متوقف شده‌اند و می‌توانید آن‌ها را کنترل کنید.",
            "A container is an isolated environment for running an app. See whether your server’s Docker apps are running or stopped, and control them here.",
            listOf(
                "سروری را انتخاب کنید که Docker روی آن نصب و فعال است.",
                "مطمئن شوید Agent سرور از ابزار Docker پشتیبانی می‌کند؛ فقط آپدیت اپ کافی نیست.",
                "فهرست را نوسازی و نام و وضعیت کانتینر را بررسی کنید.",
                "اگر لازم بود توقف یا راه‌اندازی مجدد را بزنید و نتیجه را دوباره نوسازی کنید؛ عملیات مدیریتی به توکن Admin نیاز دارد.",
            ),
            listOf(
                "Select a server with Docker installed and running.",
                "Ensure its Agent supports Docker tools; updating the phone app alone is not enough.",
                "Refresh and check the container name and state.",
                "Stop or restart only if needed, then refresh to verify. Management actions need an Admin token.",
            ),
            "اگر HTTP 404 می‌بینید، نسخه و مسیرهای Agent را بررسی کنید؛ این خطا الزاماً به معنی نبودن کانتینر نیست.",
            "For HTTP 404, check the Agent version and routes; it does not necessarily mean there are no containers.",
            "توقف کانتینر ممکن است سایت یا پنل را قطع کند. این صفحه Docker را روی سرور نصب نمی‌کند.",
            "Stopping a container may take a site or panel offline. This screen does not install Docker on the server."
        )
        CommandRoute.PROCESSES -> h(
            "برای فهمیدن این است که کدام برنامهٔ در حال اجرا، پردازنده یا رم سرور را مصرف می‌کند. PID شمارهٔ همان فرایند است.",
            "Find which running program is using server CPU or memory. A PID is the identifying number of that process.",
            listOf(
                "سرور موردنظر را انتخاب و فهرست را بارگذاری کنید.",
                "مصرف CPU و حافظه را مقایسه و نام فرایند را بررسی کنید.",
                "اگر لازم است آن را متوقف کنید، ابتدا SIGTERM را انتخاب کنید؛ یعنی درخواست پایان عادی.",
                "فقط اگر راه عادی جواب نداد و اثرش را می‌دانید، SIGKILL را در نظر بگیرید؛ سپس وضعیت را دوباره بررسی کنید.",
            ),
            listOf(
                "Select a server and load its processes.",
                "Compare CPU and memory use and identify the program.",
                "If stopping is needed, try SIGTERM: a request to exit normally.",
                "Consider SIGKILL only if normal termination fails and you understand the impact, then verify state again.",
            ),
            "مثال: مصرف زیاد ممکن است از به‌روزرسانی یا پشتیبان‌گیری باشد؛ هر فرایند پرمصرفی خراب نیست.",
            "High usage may be an update or backup job, not a faulty program.",
            "پایان اجباری می‌تواند دادهٔ ذخیره‌نشده را از بین ببرد. فرایند سیستم یا Agent را بدون شناخت متوقف نکنید؛ عملیات نیاز به دسترسی مدیریتی دارد.",
            "Forced termination may lose unsaved data. Do not stop system processes or the Agent blindly; management access is required."
        )
        CommandRoute.SERVICES -> h(
            "برای مدیریت برنامه‌هایی است که لینوکس با systemd اجرا می‌کند؛ مثلاً وب‌سرور. می‌توانید وضعیت، گزارش اجرا و روشن یا خاموش‌بودن سرویس را ببینید.",
            "Manage programs started by Linux systemd, such as a web server. View status and logs, and start or stop a service.",
            listOf(
                "سرور را انتخاب کنید و نام کاربری، پورت و رمز SSH را وارد کنید؛ رمز SSH با توکن Agent فرق دارد.",
                "بارگذاری را بزنید و در اولین اتصال، اثر انگشت SSH را با منبع معتبر مقایسه کنید.",
                "سرویس موردنظر را پیدا و ابتدا Logs، یعنی گزارش اجرا، را ببینید.",
                "در صورت نیاز Start، Stop یا Restart را انتخاب و نام سرور و سرویس را در تأیید نهایی بررسی کنید.",
            ),
            listOf(
                "Select a server and enter the SSH user, port and password; this is not the Agent token.",
                "Load services and verify the SSH fingerprint against a trusted source on first connection.",
                "Find your service and inspect Logs first.",
                "If needed, choose Start, Stop or Restart, checking the server and service in the confirmation.",
            ),
            "این ابزار به SSH و سرور دارای systemd نیاز دارد؛ خطای رمز را با تغییر توکن Agent رفع نمی‌کنید.",
            "This tool needs SSH and a systemd server. Changing an Agent token does not fix an SSH password error.",
            "توقف SSH، شبکه یا سرویس اصلی ممکن است دسترسی شما و کاربران را قطع کند؛ دسترسی کنسول جایگزین داشته باشید.",
            "Stopping SSH, networking or a critical service can disconnect you and users. Keep alternate console access."
        )
        CommandRoute.RADAR -> h(
            "برای مقایسهٔ دسترسی از چند مبدأ است: یک مقصد ممکن است از گوشی شما باز شود ولی از یک سرور باز نشود. رادار از Agent سرورهای خودتان کمک می‌گیرد.",
            "Compare reachability from different locations. A target may work from your phone but fail from a server. Radar uses Agents on your own servers.",
            listOf(
                "از سرورهای ذخیره‌شده یکی را انتخاب کنید؛ اگر فقط یک سرور دارید همان انتخاب می‌شود.",
                "برای مقصد جدید، نام، میزبان، پورت و نوع بررسی را وارد و به Agent همگام کنید؛ سپس بررسی را اجرا و نوسازی کنید.",
                "برای جدول مقایسه، ابتدا در «پایش دسترس‌پذیری» مقصد بسازید و از گوشی یک بار آزمایش کنید.",
                "در رادار همگام‌سازی مانیتورها را اجرا کنید تا مقاصد به Agent سرورهای ذخیره‌شده فرستاده شوند؛ نتایج هر ستون را جدا بخوانید.",
            ),
            listOf(
                "Choose a saved server; a sole server is selected automatically.",
                "For a new target, enter its name, host, port and check type, sync it to the Agent, then run and refresh.",
                "For the comparison table, create targets under Uptime and test once from your phone.",
                "Sync monitors in Radar to send targets to your saved server Agents; inspect each column separately.",
            ),
            "اگر فقط یک مبدأ شکست خورد، ممکن است مسیر همان شبکه مشکل داشته باشد. دادهٔ گوشی در جدول، آخرین نتیجهٔ ثبت‌شدهٔ گوشی است.",
            "Failure at only one location may indicate a path problem. The phone column uses its last recorded check.",
            "این شبکهٔ عمومی نقاط تست نیست؛ Agent سازگار لازم است. همگام‌سازی مقاصد، تنظیمات بررسی روی Agentها را تغییر می‌دهد و نتیجه ممکن است با تأخیر برسد.",
            "This is not a public network of test locations; compatible Agents are required. Sync changes Agent probe targets and results may take time to arrive."
        )
        CommandRoute.BANDWIDTH -> h(
            "برای اندازه‌گیری سرعت انتقال داده بین گوشی و سرور انتخاب‌شده است؛ کمک می‌کند کندی مسیر را بررسی کنید، نه اینکه قدرت CPU سرور را بسنجید.",
            "Measure data transfer speed between your phone and the selected server, helping diagnose a slow path rather than server CPU performance.",
            listOf(
                "سرور موردنظر را انتخاب و اتصال Agent را بررسی کنید.",
                "Agent باید مسیرهای تست پهنای باند را پشتیبانی کند؛ اگر قدیمی است، اپ به‌تنهایی این قابلیت را فعال نمی‌کند.",
                "روی اجرا بزنید و تا پایان مراحل تأخیر، دانلود و آپلود صبر کنید؛ در صورت نیاز لغو کنید.",
                "نتیجه را در زمان یا اینترنت دیگری تکرار و مقایسه کنید.",
            ),
            listOf(
                "Select your server and check Agent connectivity.",
                "The Agent must support bandwidth-test routes; a phone update alone cannot add them.",
                "Run the test and wait for latency, download and upload stages, or cancel if needed.",
                "Repeat on another connection or at another time to compare.",
            ),
            "Mbps یعنی مگابیت بر ثانیه، نه مگابایت. نتیجه به اینترنت گوشی، مسیر و بار سرور بستگی دارد.",
            "Mbps means megabits per second, not megabytes. Results depend on the phone connection, path and server load.",
            "تست ترافیک مصرف می‌کند. نتیجه، حداکثر ظرفیت پورت دیتاسنتر یا سرعت سرور به کل اینترنت نیست.",
            "The test consumes traffic. It is not the datacenter port’s maximum capacity or the server’s speed to the whole internet."
        )
        CommandRoute.CF_SCANNER -> h(
            "برای پیدا کردن IPهای کلودفلر است که از اینترنت فعلی گوشی بهتر پاسخ می‌دهند. رنج یعنی یک گروه آدرس؛ اسکنر تعدادی از آن‌ها را آزمایش می‌کند.",
            "Find Cloudflare IPs that respond better on your current phone connection. A range is a group of addresses; the scanner tests a sample.",
            listOf(
                "حالت رنج‌های آماده را انتخاب کنید؛ ۱۵ رنج رسمی IPv4 آماده‌اند و می‌توانید بعضی را انتخاب یا همه را نگه دارید.",
                "تعداد آزمایش را ابتدا کم بگذارید؛ تنظیمات زمان انتظار و هم‌زمانی را در صورت نیاز تغییر دهید.",
                "شروع اسکن را بزنید و نتایج را ببینید؛ اگر لازم بود توقف کنید.",
                "برای فهرست خودتان به حالت دستی بروید یا فایل متنی UTF-8 وارد کنید؛ IP یا CIDR مثل 104.16.0.0/13 قابل استفاده است.",
                "نتایج مناسب را کپی و در کاربرد واقعی خودتان دوباره آزمایش کنید.",
            ),
            listOf(
                "Choose built-in ranges: 15 official IPv4 ranges are ready. Select some or keep all.",
                "Start with a small test count; adjust timeout and concurrency only if needed.",
                "Start scanning and inspect results; stop if necessary.",
                "Use custom input or import a UTF-8 text file for your own IPs or CIDRs, such as 104.16.0.0/13.",
                "Copy promising results and retest in your actual setup.",
            ),
            "به Agent نیاز ندارد. فایل واردشده قابل ویرایش است و واردکردن آن اسکن را خودکار شروع نمی‌کند؛ سقف فایل ۶۴ KiB است.",
            "No Agent is needed. Imported text stays editable and importing does not start a scan; the file limit is 64 KiB.",
            "این IPها کاندید هستند، نه IP تمیز تضمینی. نتیجهٔ گوشی در سرور یا شبکهٔ دیگر ممکن است فرق کند؛ موتور فعلی برای Cloudflare IPv4 است.",
            "These are candidates, not guaranteed clean IPs. Results may differ from a server or another network; this engine targets Cloudflare IPv4."
        )
        CommandRoute.REALITY_SNI -> h(
            "SNI نام دامنه‌ای است که در شروع اتصال امن معرفی می‌شود. اینجا دامنه‌های کاندید برای استفاده در تنظیمات SNI/REALITY را از نظر TLS و گواهی بررسی می‌کنید؛ خود VPN ساخته نمی‌شود.",
            "SNI is the domain name introduced when starting a secure connection. Check candidate SNI/REALITY domains for TLS and certificate properties; this does not create a VPN.",
            listOf(
                "فهرست آماده را انتخاب کنید: ۲۲۶ دامنه در چهار دسته دارید و می‌توانید دسته یا همه را انتخاب کنید.",
                "تعداد را تعیین کنید؛ پیش‌فرض ۵۰ مقصد است. پورت معمول 443 را نگه دارید مگر مقصد شما متفاوت باشد.",
                "در پیش‌نمایش فهرست را ببینید و شروع اسکن را بزنید؛ نتیجهٔ قبول یا رد هر مقصد را بخوانید.",
                "برای دامنه‌های شخصی، فهرست را به حالت دستی کپی و ویرایش کنید یا فایل UTF-8 تا ۶۴ KiB وارد کنید؛ بررسی تکی هم موجود است.",
                "دامنهٔ مناسب را با تنظیمات و از سرور واقعی خودتان دوباره آزمایش کنید.",
            ),
            listOf(
                "Choose the bundled list: 226 domains in four categories, or select all categories.",
                "Choose a count; the default is 50. Keep port 443 unless your target uses another port.",
                "Preview the list, start scanning and read each accepted or rejected result.",
                "For custom domains, copy the list to custom input and edit, or import UTF-8 text up to 64 KiB; single-domain checking remains available.",
                "Retest a promising domain with your actual configuration and from your server.",
            ),
            "روی گوشی اجرا می‌شود و Agent نمی‌خواهد؛ سقف هر اجرا ۲۵۶ مقصد و حداکثر چهار بررسی هم‌زمان است.",
            "Checks run on your phone without an Agent, with up to 256 targets and four concurrent checks per run.",
            "دامنه‌های آماده فقط کاندیدند؛ سازگاری REALITY یا اتصال VPN تضمین نمی‌شود. توقف جلوی کارهای بعدی را می‌گیرد ولی DNS یا اتصال در حال اجرا ممکن است دیرتر تمام شود.",
            "Bundled domains are candidates, not guaranteed REALITY donors or working VPN connections. Stop prevents queued work; active DNS or socket work may take longer to finish."
        )
        CommandRoute.UPTIME -> h(
            "یعنی برنامه مرتب بپرسد «سایت یا سرویس من جواب می‌دهد؟» تا قطع‌شدن و برگشتن آن را بفهمید. مثلاً هر ۳۰ ثانیه پنلتان بررسی شود؛ این با اندازه‌گیری CPU و رم فرق دارد.",
            "Keep asking “Does my site or service respond?” to notice outages and recovery. For example, check your panel every 30 seconds. This differs from CPU and memory monitoring.",
            listOf(
                "«افزودن مقصد» را بزنید؛ مانیتور یعنی یک مقصد همراه با برنامهٔ بررسی آن.",
                "برای سایت HTTP/HTTPS، برای اتصال به پورت TCP، برای متن داخل صفحه KEYWORD و برای گواهی HTTPS گزینهٔ SSL را انتخاب کنید.",
                "نام، آدرس و فاصلهٔ بررسی را وارد کنید؛ مثلاً نام «پنل من»، نشانی واقعی پنلتان و فاصلهٔ ۳۰ ثانیه.",
                "ذخیره کنید و «آزمایش اکنون» را بزنید. ذخیره به‌تنهایی بررسی دوره‌ای را شروع نمی‌کند؛ به صفحهٔ پایش برگردید و در کارت «پایش خودکار» دکمهٔ «شروع پایش» را بزنید. وضعیت روشن فقط پس از راه‌اندازی سرویس نمایش داده می‌شود.",
                "مجوز اعلان را بدهید تا تغییر وضعیت قطع و وصل را دریافت کنید؛ از همین فهرست وضعیت و زمان پاسخ را ببینید.",
            ),
            listOf(
                "Tap Add monitor: a monitor is a target plus its checking schedule.",
                "Use HTTP/HTTPS for sites, TCP for a port, KEYWORD for text on a page and SSL for a certificate.",
                "Enter a name, address and interval, such as “My panel”, your real panel URL and 30 seconds.",
                "Save and use Test now. Saving alone does not start scheduled checks. Return to Uptime and press Start monitoring in the Automatic monitoring card. The on state appears only after the service starts.",
                "Allow notifications to receive down/recovery transitions; inspect status and response time in the list.",
            ),
            "بررسی از اینترنت گوشی انجام می‌شود و Agent لازم ندارد. درصد وضعیت از ۳۰ بررسی اخیر است، نه گزارش آپ‌تایم ماهانه؛ قبل از اولین آزمایش به درصد اعتماد نکنید.",
            "Checks run from your phone connection without an Agent. The percentage uses the last 30 checks, not monthly uptime; do not rely on it before the first check.",
            "اعلان ردشده مانع بررسی نمی‌شود ولی ممکن است هشدار را نبینید. پایش با بستن عادی صفحه ادامه دارد، اما بستن اجباری، راه‌اندازی دوبارهٔ گوشی یا محدودیت باتری می‌تواند آن را قطع کند. پورت باز، سلامت کامل VPN را تضمین نمی‌کند. گزینهٔ PING فعلی در این بخش اتصال TCP است، نه پینگ ICMP.",
            "Denied notifications do not prevent checks but can hide alerts. Checks continue when leaving this page, but force-stop, reboot and battery restrictions can interrupt them. An open port does not prove a working VPN. PING in this section currently uses TCP, not ICMP ping."
        )
        CommandRoute.UPTIME_EDITOR -> h(
            "اینجا به دیدبان می‌گویید چه چیزی را و هر چند ثانیه بررسی کند؛ برای اینکه مثلاً زودتر بفهمید پنل یا پورت سرویس قطع شده است.",
            "Tell Didban what to check and how often, so you can notice a panel or service-port outage sooner.",
            listOf(
                "برای مقصد تازه New را بزنید؛ برای ویرایش روی نام مانیتور ذخیره‌شده بزنید و نامی قابل‌فهم انتخاب کنید.",
                "برای سایت، HTTPS و URL کامل مثل https://panel.example.com را با آدرس واقعی خودتان جایگزین کنید؛ پورت خاص وب را داخل URL بنویسید.",
                "برای TCP یا SSL فقط دامنه یا IP مناسب مقصد و پورت را وارد کنید؛ مثلاً پورت 443. برای KEYWORD متن مورد انتظار صفحه را هم بنویسید.",
                "فاصله را مثلاً ۳۰ ثانیه بگذارید؛ ذخیره و آزمایش اکنون را بزنید و نتیجه را بررسی کنید.",
                "به صفحهٔ پایش برگردید و «شروع پایش» را بزنید؛ Pause بررسی خودکار فقط این مقصد را متوقف و Resume دوباره فعال می‌کند. دکمهٔ «توقف پایش» موتور خودکار همهٔ مقصدها و پایش پس‌زمینهٔ سرورها را متوقف می‌کند.",
            ),
            listOf(
                "Use New for a new target, or select a saved monitor to edit; choose a recognizable name.",
                "For a website choose HTTPS and replace https://panel.example.com with your real URL. Put a custom web port inside the URL.",
                "For TCP or SSL enter the target host and port, such as 443. For KEYWORD also enter the expected page text.",
                "Choose an interval such as 30 seconds, save and use Test now to verify.",
                "Return to Uptime and press Start monitoring. Pause suspends only this target and Resume enables it again. Stop monitoring stops automatic target checks and background server monitoring.",
            ),
            "HTTP پاسخ موفق وب را بررسی می‌کند؛ KEYWORD علاوه بر آن دنبال متن شما می‌گردد؛ SSL وضعیت انقضای گواهی را بررسی می‌کند. برای اعلان‌ها مجوز اعلان لازم است.",
            "HTTP checks a successful web response; KEYWORD also looks for your text; SSL checks certificate expiry. Notifications need permission.",
            "فقط برای مقصد داخلی مورداعتماد، اجازهٔ شبکهٔ خصوصی را فعال کنید. نتایج از گوشی‌اند؛ درصد فقط ۳۰ بررسی اخیر است و PING این بخش فعلاً TCP است. توقف پایش یا محدودیت پس‌زمینه، بررسی دوره‌ای را مختل می‌کند.",
            "Only allow private-network targets when trusted. Results come from the phone; the percentage uses 30 recent checks and PING here currently means TCP. Stopped monitoring or background restrictions interrupt scheduling."
        )
        CommandRoute.NETWORK_TOOLS -> h(
            "مثل جعبه‌ابزار عیب‌یابی اینترنت است؛ کمک می‌کند بفهمید مشکل از دسترسی مقصد، پورت، نام دامنه یا اتصال امن است.",
            "A network troubleshooting toolbox to distinguish destination, port, domain-name and secure-connection problems.",
            listOf(
                "سؤال نزدیک به مشکلتان را انتخاب کنید؛ مثلاً «مقصد در دسترس است؟» یا مشکل TLS.",
                "برای آزمایش مستقیم از گوشی، مجموعهٔ ابزار شبکه را باز کنید و راهنمای همان صفحه را بخوانید.",
                "برای مقایسهٔ سرورها رادار، برای رکورد دامنه DNS و برای کاندیدهای کلودفلر یا SNI اسکنر مربوط را باز کنید.",
            ),
            listOf(
                "Choose the question closest to your problem, such as reachability or TLS.",
                "For a direct phone-side test, open the network suite and its guide.",
                "Use Radar for server comparisons, DNS for domain records and the respective scanners for Cloudflare or SNI candidates.",
            ),
            "DNS نام دامنه را به آدرس تبدیل می‌کند؛ TLS اتصال امن است؛ TCP بررسی اتصال به یک پورت است.",
            "DNS translates a domain to an address; TLS secures a connection; TCP testing checks a port connection.",
            "موفقیت یک تست به‌تنهایی سلامت کل سرویس را ثابت نمی‌کند؛ نتیجه را با چند بررسی مرتبط مقایسه کنید.",
            "One successful test does not prove the whole service works; compare relevant checks."
        )
        CommandRoute.NETWORK_TOOLS_EDITOR -> h(
            "برای آزمایش مستقیم یک مقصد از گوشی است: دسترسی، پورت‌های پاسخ‌گو، گواهی امن و اطلاعات دامنه یا IP را بررسی می‌کند.",
            "Run direct checks from your phone: reachability, responding ports, secure certificates and domain or IP information.",
            listOf(
                "ابزار موردنظر را از بالای صفحه انتخاب کنید؛ برای اتصال پورت TCP و برای گواهی SSL را انتخاب کنید.",
                "در مقصد نام دامنه یا IP را بدون مسیر صفحه بنویسید؛ اگر ابزار پورت می‌خواهد آن را هم وارد کنید.",
                "Run را بزنید و پاسخ یا خطا را بخوانید؛ برای مقایسه می‌توانید خروجی را کپی کنید.",
                "برای GeoIP یا اطلاعات دامنه، کشور و شبکهٔ گزارش‌شده را فقط به‌عنوان اطلاعات تقریبی استفاده کنید.",
            ),
            listOf(
                "Choose a tool at the top: TCP for port connectivity or SSL for certificates.",
                "Enter a domain or IP without a page path, and a port when needed.",
                "Press Run and inspect responses or errors; copy output for comparison if useful.",
                "Treat GeoIP country and network information as approximate.",
            ),
            "مثال: برای سرویس HTTPS خودتان دامنه و پورت 443 را با TCP امتحان کنید، سپس SSL را بررسی کنید؛ نصب Agent لازم نیست.",
            "For your HTTPS service, test its domain on TCP port 443, then inspect SSL. No Agent installation is needed.",
            "فقط مقاصد مجاز را اسکن کنید. اطلاعات DNS و GeoIP ممکن است از سرویس‌های بیرونی گرفته شود؛ مسدودبودن پینگ الزاماً قطع‌بودن سرویس نیست.",
            "Only scan authorized targets. DNS and GeoIP may query external services; blocked ping does not necessarily mean a service outage."
        )
        CommandRoute.DNS -> h(
            "DNS دفترچهٔ آدرس اینترنت است: مشخص می‌کند نام دامنه به کدام آدرس برسد. اینجا بین دیدن پاسخ DNS و تغییر رکوردهای Cloudflare انتخاب می‌کنید.",
            "DNS is the internet’s address book: it maps domain names to addresses. Choose between inspecting answers and editing Cloudflare records.",
            listOf(
                "اگر فقط می‌خواهید بدانید دامنه به چه IP می‌رسد، عیب‌یابی DNS را باز کنید.",
                "اگر دامنه در حساب Cloudflare خودتان است و می‌خواهید رکورد تغییر دهید، مدیریت رکوردها را باز کنید.",
                "راهنمای ابزار انتخاب‌شده را بخوانید؛ مدیریت Cloudflare توکن می‌خواهد، ولی مشاهدهٔ پاسخ DNS به توکن آن حساب نیاز ندارد.",
            ),
            listOf(
                "For finding which IP a domain resolves to, open DNS diagnostics.",
                "For changing records in your own Cloudflare account, open record management.",
                "Read that tool’s guide: Cloudflare editing needs a token, while public DNS lookup does not.",
            ),
            "مثال: رکورد A آدرس IPv4 و رکورد AAAA آدرس IPv6 را به نام دامنه وصل می‌کند.",
            "An A record maps a name to IPv4; an AAAA record maps it to IPv6.",
            "تغییر رکورد ممکن است سایت یا ایمیل را مختل کند و اثرش به دلیل کش DNS فوری دیده نشود.",
            "Record changes can disrupt a website or email, and DNS caches may delay visible changes."
        )
        CommandRoute.DNS_EDITOR -> h(
            "برای مدیریت آدرس‌های دامنه‌های خودتان در Cloudflare است؛ مثلاً عوض‌کردن IP سروری که یک زیر‌دامنه به آن اشاره می‌کند.",
            "Manage your Cloudflare domain records, for example changing the server IP used by a subdomain.",
            listOf(
                "در حساب Cloudflare یک API Token محدود به دامنهٔ موردنظر با مجوز Zone Read و DNS Edit بسازید؛ به‌جای کلید سراسری از توکن محدود استفاده کنید.",
                "توکن را وارد و ذخیره کنید؛ بارگذاری دامنه‌ها را بزنید و دامنهٔ موردنظر را انتخاب کنید.",
                "برای ساخت New یا برای تغییر Edit را بزنید؛ نوع، Name، Content و TTL را وارد کنید. TTL زمان کش پاسخ است.",
                "برای نمونهٔ A، نام زیر‌دامنه و IPv4 واقعی سرور خودتان را بنویسید؛ وضعیت Proxy را آگاهانه انتخاب و ذخیره کنید.",
                "برای بررسی نتیجه از Lookup استفاده کنید و به کش DNS زمان بدهید.",
            ),
            listOf(
                "Create a Cloudflare API Token restricted to your domain with Zone Read and DNS Edit permissions, rather than a global key.",
                "Enter and save the token, load zones and select your domain.",
                "Use New or Edit; fill in type, Name, Content and TTL, which controls cache duration.",
                "For an A record enter your subdomain and actual server IPv4; choose Proxy status deliberately and save.",
                "Use Lookup to inspect the result, allowing time for DNS caches.",
            ),
            "Proxy روشن یعنی ترافیکِ پشتیبانی‌شده از Cloudflare عبور کند؛ این گزینه برای هر پروتکل یا پورت مناسب نیست.",
            "Proxy enabled routes supported traffic through Cloudflare; it is not suitable for every protocol or port.",
            "توکن محرمانه است. قبل از حذف یا تغییر، دامنه و رکورد را دوباره بررسی و مقدار قبلی را نگه دارید؛ تغییر اشتباه ممکن است سایت یا ایمیل را قطع کند.",
            "The token is secret. Verify the domain and record and keep the old value before editing or deleting; mistakes may break a site or email."
        )
        CommandRoute.WORKBENCH_HOME -> h(
            "محل اسکنرها و ابزارهای مستقل شبکه است: یافتن IP و SNI، عیب‌یابی اتصال و ساخت تنظیمات. ابزارهای مدیریت یک سرور در جزئیات همان سرور هستند.",
            "Independent scanners and network utilities: find IP and SNI candidates, diagnose connectivity and generate configuration. Manage a specific server from its details.",
            listOf(
                "برای اسکن IP یا SNI، ابزار مربوط را باز کنید؛ فهرست آماده را انتخاب و شروع را بزنید.",
                "برای کار روی یک سرور، به سرورها ← جزئیات بروید. اجرای گروهی برای چند سرور در ابزارهاست.",
                "برای ساخت تنظیمات چند سرویس روی یک پورت، تک‌پورت را باز کنید؛ برای بررسی لینک اتصال، Proxy را انتخاب کنید.",
                "قبل از هر عملیات، راهنمای همان ابزار و پیش‌نیازهایش را بخوانید.",
            ),
            listOf(
                "For IP or SNI scanning, open the scanner, choose a ready list and start.",
                "For a single server, go to Servers → details. Batch operations across servers remain in Tools.",
                "Use Single port to generate shared-port routing configuration, or Proxy to inspect connection links.",
                "Read each tool’s guide and prerequisites before operating.",
            ),
            "SSH راه ورود فرمانی به سرور است و SFTP دسترسی فایل از همان بستر است؛ نام کاربری و رمز آن‌ها با توکن Agent فرق دارد.",
            "SSH provides command access and SFTP provides file access over it; their login differs from an Agent token.",
            "اجرای فرمان یا ذخیرهٔ فایل می‌تواند سرور را تغییر دهد؛ ابتدا با کار کم‌خطر شروع کنید.",
            "Commands and file saves can change the server; start with a low-risk task."
        )
        CommandRoute.SSH -> h(
            "برای فرستادن فرمان به سرور و دیدن خروجی آن است؛ مثل اجرای دستور در ترمینال سرور. این صفحه هر فرمان را اجرا می‌کند، نه یک نشست تعاملی کامل.",
            "Send a command to the server and see its output, like using a server terminal. This page runs commands rather than providing a full interactive session.",
            listOf(
                "سرور ذخیره‌شده را انتخاب کنید.",
                "User، پورت SSH، معمولاً 22، و رمز SSH را وارد کنید؛ توکن Agent جای رمز نیست.",
                "اول با فرمان کم‌خطر مثل uptime شروع کنید و اجرا را بزنید.",
                "در اولین اتصال اثر انگشت کلید میزبان را از منبع معتبر مقایسه و فقط در صورت تطابق تأیید کنید.",
                "خروجی یا خطا را بخوانید؛ فرمان بعدی را فقط بعد از فهمیدن نتیجه اجرا کنید.",
            ),
            listOf(
                "Select a saved server.",
                "Enter User, SSH port, usually 22, and SSH password; the Agent token is not a password.",
                "Start with a low-risk command such as uptime and run it.",
                "On first connection, compare the host-key fingerprint with a trusted source before approving.",
                "Read output or errors before running the next command.",
            ),
            "برای ویرایش تعاملی مثل nano روی این صفحه حساب نکنید؛ برای فایل متنی از SFTP استفاده کنید.",
            "Do not rely on this screen for interactive editors such as nano; use SFTP for text files.",
            "فرمان root می‌تواند سرور را خراب کند. اگر کلید میزبان عوض شده، بدون بررسی هویت سرور تأیید نکنید.",
            "Root commands can damage the server. Do not approve a changed host key without verifying server identity."
        )
        CommandRoute.BATCH -> h(
            "برای اجرای یک فرمان یکسان روی چند سرور است؛ مثلاً دیدن وضعیت چند سرور بدون تکرار دستی فرمان.",
            "Run the same command on several servers, such as checking their status without typing it repeatedly.",
            listOf(
                "سرورهای مقصد را از فهرست انتخاب کنید.",
                "رمز SSH مشترک قابل استفاده برای سرورهای انتخاب‌شده را وارد کنید؛ این فرم برای رمزهای متفاوت نیست.",
                "متن Command را بررسی و برای اولین آزمایش با uptime جایگزین کنید؛ ابتدا فقط یک سرور را انتخاب کنید.",
                "اثر انگشت هر سرور را در صورت درخواست بررسی کنید؛ پس از آزمایش موفق، اجرای چندسروری را انجام دهید.",
                "نتیجهٔ هر سرور را جدا بخوانید؛ موفقیت یکی به معنی موفقیت همه نیست.",
            ),
            listOf(
                "Select the target servers.",
                "Enter a shared SSH password valid for those servers; this form does not support different passwords per target.",
                "Review Command and replace it with uptime for a first test on one server.",
                "Verify each requested host fingerprint; after testing, run across the intended servers.",
                "Read each server result separately; one success does not mean all succeeded.",
            ),
            "اجرای فعلی Batch از کاربر root و پورت 22 استفاده می‌کند؛ اگر سرور شما این شرایط را ندارد از SSH تکی استفاده کنید.",
            "The current Batch path uses root and port 22; use individual SSH if your servers need different settings.",
            "فرمان پیش‌فرض را بی‌بررسی اجرا نکنید. یک اشتباه می‌تواند چند سرور را هم‌زمان تغییر دهد؛ پیش از عملیات مخرب پشتیبان بگیرید.",
            "Do not run the prefilled command blindly. A mistake can change several servers at once; back up before destructive operations."
        )
        CommandRoute.SFTP -> h(
            "برای دیدن پوشه‌ها و خواندن یا ویرایش فایل متنی روی سرور از طریق SSH است؛ مثلاً بررسی یک فایل تنظیمات بدون ورود به ترمینال.",
            "Browse folders and read or edit text files on a server over SSH, for example inspecting a configuration without a terminal.",
            listOf(
                "سرور را انتخاب و نام کاربری، پورت و رمز SSH را وارد کنید.",
                "مسیر پوشهٔ موردنظر را بنویسید و نوسازی کنید؛ در صورت درخواست، کلید میزبان را با منبع معتبر تطبیق دهید.",
                "روی پوشه برای ورود یا روی فایل برای خواندن متن بزنید.",
                "قبل از تغییر، یک نسخهٔ پشتیبان از فایل داشته باشید؛ متن را اصلاح و ذخیره کنید، سپس دوباره باز کنید تا نتیجه را بررسی کنید.",
            ),
            listOf(
                "Select a server and enter the SSH user, port and password.",
                "Enter a folder path and refresh, verifying a requested host key against a trusted source.",
                "Open a folder to browse or a file to read its text.",
                "Back up the file before editing, save changes, then reopen to verify.",
            ),
            "این صفحهٔ فعلی مرور و ویرایش متن است؛ آن را مدیر انتقال همهٔ انواع فایل یا جایگزین پشتیبان‌گیری کامل فرض نکنید.",
            "This screen browses and edits text; it is not a full file-transfer manager or a complete backup tool.",
            "ذخیره مستقیماً فایل راه‌دور را تغییر می‌دهد. فایل باینری یا تنظیمات حیاتی را بدون شناخت ویرایش نکنید.",
            "Save changes the remote file directly. Do not edit binary files or critical configuration without understanding them."
        )
        CommandRoute.SINGLE_PORT -> h(
            "برای ساخت تنظیمات هدایت چند سرویس از یک پورت مشترک است؛ مثلاً پنل و اشتراک و REALITY روی ورودی 443، با تشخیص نام دامنه در شروع اتصال TLS.",
            "Generate routing configuration for several services sharing one incoming port, such as a panel, subscription and REALITY on 443, using the TLS domain name.",
            listOf(
                "پورت ورودی را مشخص کنید؛ سرویس‌های پشت آن باید از قبل روی پورت‌های داخلی متفاوت آماده باشند.",
                "دامنهٔ پنل، دامنهٔ اشتراک، SNI مربوط به REALITY و پورت داخلی هرکدام را وارد کنید؛ مقدارهای نمونه را جایگزین کنید.",
                "پورت fallback، یعنی مسیر پیش‌فرض برای اتصال‌های نامنطبق، را مشخص کنید.",
                "نوع خروجی HAProxy، Bash deploy یا Docker Compose را انتخاب و Generate را بزنید.",
                "خروجی را بخوانید و کپی کنید؛ برای استقرار باید خودتان با آگاهی روی سرور اجرا یا نصبش کنید.",
            ),
            listOf(
                "Choose an incoming port; backend services must already be ready on different internal ports.",
                "Enter panel and subscription domains, the REALITY SNI and each internal port, replacing example values.",
                "Set the fallback port for unmatched connections.",
                "Choose HAProxy, Bash deploy or Docker Compose and press Generate.",
                "Review and copy the output; deploying it on your server is a separate manual action.",
            ),
            "این بخش تست یک پورت نیست و بازکردن صفحه چیزی روی سرور نصب نمی‌کند؛ فقط متن تنظیمات یا اسکریپت تولید می‌شود.",
            "This is not a single-port connectivity test. Opening it installs nothing; it generates configuration or script text.",
            "اجرای خروجی می‌تواند با سرویس فعلی روی 443 تداخل کند. قبل از استقرار، پشتیبان و دسترسی کنسول داشته باشید و دامنه‌ها و گواهی‌های لازم را آماده کنید.",
            "Deployment may conflict with an existing service on 443. Back up, keep console access and prepare required domains and certificates."
        )
        CommandRoute.PROXY -> h(
            "برای خواندن لینک کانفیگ و بررسی اولیهٔ مقصد آن است؛ همچنین می‌توانید اطلاعات یک لینک اشتراک را ببینید. این صفحه VPN گوشی را وصل نمی‌کند.",
            "Read a configuration link and perform an initial check of its destination, or inspect a subscription link. This screen does not connect a phone VPN.",
            listOf(
                "لینک کامل VLESS، VMess، Trojan یا Shadowsocks خودتان را در بخش کانفیگ تکی بچسبانید.",
                "دکمهٔ خواندن و آزمایش را بزنید و نام پروتکل، میزبان، پورت و نتیجهٔ دسترسی را ببینید.",
                "برای اشتراک، URL کامل اشتراک را در بخش Subscription وارد و بررسی را اجرا کنید.",
                "اگر نیاز به استفاده دارید، کانفیگ را در برنامهٔ کلاینت مناسب خودتان جداگانه امتحان کنید.",
            ),
            listOf(
                "Paste your full VLESS, VMess, Trojan or Shadowsocks link into the single-config field.",
                "Parse and probe it, then inspect protocol, host, port and reachability.",
                "For a subscription, enter its full URL under Subscription and inspect it.",
                "Test the configuration separately in a suitable client for actual use.",
            ),
            "این ابزار به Agent نیاز ندارد؛ از اتصال گوشی برای بررسی مقصد یا دریافت اشتراک استفاده می‌کند.",
            "No Agent is needed; the phone connection is used to probe the destination or fetch a subscription.",
            "دسترسی TCP/TLS ثابت نمی‌کند احراز هویت یا عبور ترافیک VPN کار می‌کند. لینک کانفیگ و اشتراک محرمانه‌اند؛ فقط لینک مورداعتماد را وارد کنید.",
            "TCP/TLS reachability does not prove VPN authentication or traffic forwarding. Configuration and subscription links are secrets; use trusted links only."
        )
        CommandRoute.DEVELOPER_LAB -> h(
            "ابزارهای کوچک برای کار با متن و تنظیمات است: مرتب‌کردن JSON، تبدیل Base64 و URL، محاسبهٔ هش و محدودهٔ شبکه یا دیدن محتوای JWT.",
            "Small utilities for text and configuration: format JSON, convert Base64 and URLs, calculate hashes and subnet ranges, or inspect JWT content.",
            listOf(
                "نوع ابزار را انتخاب کنید؛ مثلاً JSON برای خواناترشدن متن تنظیمات.",
                "متن مناسب همان ابزار را وارد کنید؛ برای Subnet یک CIDR مثل 192.168.1.0/24 بنویسید.",
                "اجرا را بزنید و نتیجه یا خطای ورودی را بخوانید.",
                "برای ساخت مقدار تازه از Generator و برای برداشتن خروجی از کپی استفاده کنید.",
            ),
            listOf(
                "Choose a tool, such as JSON to format configuration text.",
                "Enter suitable input; for Subnet use a CIDR such as 192.168.1.0/24.",
                "Run and inspect output or input errors.",
                "Use Generator for new values and Copy for output.",
            ),
            "پردازش این ابزارها محلی است و سرور نمی‌خواهد. Base64 تبدیل نمایش متن است، نه رمزگذاری امن.",
            "These utilities process locally without a server. Base64 is an encoding, not secure encryption.",
            "دیدن محتوای JWT به معنی تأیید امضای آن نیست. خروجی حاوی رمز یا توکن را در چت یا اسکرین‌شات منتشر نکنید.",
            "Decoding JWT content does not verify its signature. Never publish output containing passwords or tokens."
        )
        CommandRoute.PROTECT_HOME -> h(
            "محل نگهداری امن اطلاعات، تنظیم خبررسانی و تهیهٔ پشتیبان است؛ برای کم‌کردن خطر گم‌شدن تنظیمات یا افشای رمزها.",
            "Keep sensitive information, configure notifications and make backups, reducing the risk of lost settings or exposed passwords.",
            listOf(
                "برای یادداشت محرمانه مثل رمز یا کانفیگ، خزانه را باز کنید.",
                "برای خبر قطع سرویس، هشدارها را تنظیم و پیام آزمایشی ارسال کنید.",
                "برای انتقال یا نگهداری تنظیمات، پشتیبان رمزگذاری‌شده بسازید.",
                "برای بررسی فایروال و محدودیت‌های سرور، بخش امنیت را باز کنید و راهنمایش را بخوانید.",
            ),
            listOf(
                "Use Vault for sensitive notes such as passwords or configurations.",
                "Set up Alerts and send a test notification.",
                "Create an encrypted Backup to keep or move settings.",
                "For server firewall and ban checks, open Security and read its guide.",
            ),
            "این ابزارها مستقل‌اند؛ روشن‌کردن هشدار به‌تنهایی پشتیبان نمی‌سازد و خزانه هم جایگزین امنیت سرور نیست.",
            "These tools are separate: enabling alerts does not create backups, and Vault does not secure the server itself.",
            "رمز پشتیبان و خزانه را در جای امن نگه دارید؛ اطلاعات محرمانه را در خروجی عمومی قرار ندهید.",
            "Keep backup and Vault passwords safely and keep secrets out of public output."
        )
        CommandRoute.VAULT -> h(
            "خزانه یک دفترچهٔ رمزگذاری‌شده برای یادداشت‌های حساس مثل رمز، توکن و کانفیگ است؛ بدون رمز اصلی نباید بتوان متن یادداشت‌ها را خواند.",
            "Vault is an encrypted notebook for passwords, tokens and configurations, protected by a master password.",
            listOf(
                "در اولین استفاده یک رمز اصلی قوی بسازید و جای امن نگه دارید؛ دفعات بعد با همان رمز باز کنید.",
                "عنوان قابل‌شناسایی، متن محرمانه و در صورت نیاز برچسب وارد و ذخیره کنید.",
                "برای دیدن یا کپی یادداشت، همان مورد را باز کنید؛ آن را با افراد دیگر به اشتراک نگذارید.",
                "پس از استفاده قفل کنید و برای نگهداری بلندمدت، برنامهٔ پشتیبان‌گیری جدا داشته باشید.",
            ),
            listOf(
                "On first use create a strong master password and keep it safe; use it to unlock later.",
                "Enter a recognizable title, secret text and optional tags, then save.",
                "Reveal or copy only the required note and do not share it.",
                "Lock after use and maintain a separate backup plan.",
            ),
            "Agent لازم ندارد و روی گوشی نگهداری می‌شود. رمز اصلی خزانه با رمز SSH و توکن سرور متفاوت است.",
            "No Agent is needed; notes are kept on the phone. The Vault password differs from SSH passwords and server tokens.",
            "فراموشی رمز یا بازنشانی خزانه می‌تواند دسترسی به یادداشت‌ها را از بین ببرد. رمزگذاری جلوی افشای عمدی با کپی یا عکس را نمی‌گیرد.",
            "Forgetting the password or resetting Vault may lose access to notes. Encryption does not prevent disclosure through copying or screenshots."
        )
        CommandRoute.SECURITY -> h(
            "برای بررسی فایروال و IPهای مسدودشدهٔ سرور است؛ می‌توانید یک پورت TCP را در UFW مجاز یا یک IP را از مسدودی Fail2ban خارج کنید.",
            "Inspect server firewall rules and blocked IPs, allow a TCP port in UFW or unban an IP from Fail2ban.",
            listOf(
                "سرور را انتخاب و رمز و پورت SSH را وارد کنید؛ به دسترسی لازم برای فرمان‌های مدیریتی نیاز دارید.",
                "ابتدا بررسی فایروال را اجرا کنید؛ اگر UFW یا Fail2ban نصب نیست، نبودن ابزار را با سالم‌بودن همه‌چیز اشتباه نگیرید.",
                "فقط در صورت نیاز شمارهٔ پورت را وارد، «مجازکردن» را بزنید و سرور و پورت را در تأیید نهایی بررسی کنید.",
                "برای IP مسدودشده، فهرست را نوسازی کنید؛ فقط پس از بررسی دلیل مسدودی، Unban را تأیید کنید.",
            ),
            listOf(
                "Select the server and enter SSH password and port, with permission for management commands.",
                "Inspect the firewall first. If UFW or Fail2ban is missing, that is not an all-clear result.",
                "Only if needed, enter a port, choose Allow and verify the server and port in confirmation.",
                "Refresh blocked IPs and approve Unban only after checking why the IP was blocked.",
            ),
            "UFW ابزار فایروال و Fail2ban ابزار مسدودکردن تلاش‌های مشکوک است. بازکردن پورت سرور به‌تنهایی فایروال شرکت میزبان را تغییر نمی‌دهد.",
            "UFW manages firewall rules and Fail2ban blocks suspicious attempts. Opening a server port does not change the provider firewall.",
            "بازکردن پورت دسترسی شبکه را بیشتر می‌کند و رفع مسدودی ممکن است مهاجم را برگرداند. این صفحه بررسی امنیتی کامل یا تضمین امن‌بودن سرور نیست.",
            "Opening a port increases exposure and unbanning may let an attacker return. This is not a complete security audit or a safety guarantee."
        )
        CommandRoute.ALERTS -> h(
            "برای اینکه هنگام رخداد مهم لازم نباشد دائم برنامه را نگاه کنید؛ دیدبان می‌تواند از طریق بات تلگرام یا وب‌هوک Discord پیام بفرستد.",
            "Get notified of important events without constantly watching the app, using a Telegram bot or Discord webhook.",
            listOf(
                "برای تلگرام از BotFather بات بسازید، با بات گفتگو را شروع کنید و توکن و Chat ID مقصد را وارد کنید؛ Chat ID نام کاربری نیست.",
                "یا در کانال Discord موردنظر یک Webhook بسازید و URL آن را وارد کنید.",
                "پیام آزمایشی بفرستید و در مقصد دریافتش را بررسی کنید.",
                "کانال و رخدادهای موردنظر را فعال و ذخیره کنید؛ برای هشدار خودکار باید پایش مربوط هم فعال و گوشی به اینترنت وصل باشد.",
            ),
            listOf(
                "For Telegram, create a bot with BotFather, start a chat with it, then enter its token and destination Chat ID, not a username.",
                "Alternatively create a webhook in your Discord channel and enter its URL.",
                "Send a test message and verify delivery at the destination.",
                "Enable the channel and desired event triggers, then save. Automatic alerts also need active monitoring and phone connectivity.",
            ),
            "ارسال آزمایشی فقط مسیر پیام را امتحان می‌کند؛ پایش سایت یا سرور را خودش ایجاد نمی‌کند.",
            "A test message checks delivery only; it does not create a site or server monitor.",
            "توکن بات و URL وب‌هوک محرمانه‌اند. قطع اینترنت یا محدودیت پس‌زمینه ممکن است مانع پیام شود؛ نبودن هشدار نشانهٔ قطعیِ سالم‌بودن نیست.",
            "Bot tokens and webhook URLs are secrets. Connectivity or background restrictions may prevent messages; silence does not prove health."
        )
        CommandRoute.BACKUP -> h(
            "برای ساخت نسخهٔ رمزگذاری‌شده از داده‌های پشتیبانی‌شدهٔ دیدبان و برگرداندن آن‌هاست؛ مثلاً پیش از تغییرات مهم یا انتقال به گوشی دیگر.",
            "Create an encrypted copy of supported Didban data and restore it, for example before major changes or moving phones.",
            listOf(
                "برای ساخت پشتیبان، یک رمز قوی در بخش ایجاد وارد و ساخت پشتیبان رمزگذاری‌شده را بزنید.",
                "متن خروجی را کپی و در فایل یا محل امنی بیرون از برنامه نگه دارید؛ رمز را جدا نگه دارید.",
                "برای بازیابی، متن کامل پشتیبان را در کادر بازیابی بچسبانید و رمز همان پشتیبان را وارد کنید.",
                "Inspect را بزنید و تعداد رکوردهای پیش‌نمایش را بررسی کنید؛ Merge یعنی ادغام و Overwrite یعنی جایگزینی.",
                "فقط پس از بررسی حالت و پیش‌نمایش، بازیابی را تأیید و داده‌های برگشته را بررسی کنید.",
            ),
            listOf(
                "Enter a strong password in the create section and create an encrypted backup.",
                "Copy the output text to a secure file or location outside the app; keep the password separately.",
                "To restore, paste the full backup text and enter that backup’s password.",
                "Press Inspect and review record counts; Merge combines data while Overwrite replaces it.",
                "Confirm only after reviewing mode and preview, then verify restored data.",
            ),
            "ساخت خروجی به‌تنهایی فایل را در جای امن ذخیره نمی‌کند. این صفحه با متن پشتیبان کار می‌کند، نه انتخاب‌گر فایل.",
            "Generating output alone does not store it safely. This screen uses backup text, not a file picker.",
            "Overwrite می‌تواند دادهٔ فعلی را جایگزین کند؛ قبلش پشتیبان تازه بگیرید. پشتیبان دیدبان نسخهٔ کامل فایل‌ها یا برنامه‌های سرور نیست و بدون رمز قابل بازیابی نیست.",
            "Overwrite can replace existing data; make a fresh backup first. Didban backups are not full server-file or server-app backups, and require their password."
        )
        CommandRoute.SETTINGS -> h(
            "برای تنظیم زبان، ظاهر، فاصلهٔ دریافت اطلاعات و گزینه‌های امنیتی خود برنامه است؛ این تغییرها با تغییر تنظیمات سیستم‌عامل سرور فرق دارند.",
            "Adjust app language, appearance, polling interval and security options, rather than server operating-system settings.",
            listOf(
                "فارسی یا English و حالت روشن، تیره یا خودکار را انتخاب کنید.",
                "هشدارها، خزانه و پشتیبان‌گیری را از همین بخش باز کنید.",
                "فاصلهٔ دریافت اطلاعات سرورها را به ثانیه وارد و ذخیره کنید؛ برای شروع مقدار پیش‌فرض را نگه دارید.",
                "اگر می‌خواهید عکس و ضبط صفحه محدود شود، محافظت تصویری را آگاهانه فعال کنید؛ پیش‌فرض خاموش است.",
                "بازنشانی خزانه یا پاک‌کردن اعتماد SSH را فقط پس از خواندن هشدار و داشتن اطلاعات لازم تأیید کنید.",
            ),
            listOf(
                "Choose Persian or English and light, dark or automatic appearance.",
                "Open Alerts, Vault and Backup from this section.",
                "Enter and save the server polling interval in seconds; keep the default initially.",
                "Enable capture protection if you want to restrict screenshots and screen recording; it is off by default.",
                "Reset Vault or clear SSH trust only after reading the warning and keeping the information you need.",
            ),
            "فاصلهٔ دریافت اطلاعات سرور با فاصلهٔ هر مانیتور سایت متفاوت است؛ فاصلهٔ مانیتور را در فرم همان مقصد تغییر دهید.",
            "Server polling and each site monitor’s interval are different; edit a monitor’s interval in its own form.",
            "فاصلهٔ خیلی کوتاه مصرف باتری و ترافیک را بیشتر می‌کند. بازنشانی خزانه ممکن است یادداشت‌ها را از بین ببرد؛ پس از پاک‌کردن اعتماد SSH باید هویت میزبان را دوباره بررسی کنید.",
            "Very short intervals increase battery and data use. Vault reset may lose notes; clearing SSH trust requires verifying host identity again."
        )
    }
    val needsAgent = this in setOf(CommandRoute.OVERVIEW, CommandRoute.INCIDENTS,
        CommandRoute.SERVER_DOSSIER, CommandRoute.PROCESSES, CommandRoute.DOCKER,
        CommandRoute.RADAR, CommandRoute.BANDWIDTH)
    val needsSsh = this in setOf(CommandRoute.SSH, CommandRoute.SFTP, CommandRoute.BATCH,
        CommandRoute.SERVICES, CommandRoute.SECURITY)
    val prerequisite = when {
        needsAgent -> if (fa) "یک سرور ذخیره‌شده با Agent سازگار و اتصال آزمایش‌شده لازم است. اگر ندارید، از «سرورها ← افزودن سرور» و راهنمای همان فرم شروع کنید." else
            "Use a saved server with a compatible, tested Agent connection. If you have none, start at Servers → Add server and read that form’s guide."
        needsSsh -> if (fa) "سرور ذخیره‌شده و دسترسی SSH معتبر لازم است. این ابزار مستقیماً از SSH استفاده می‌کند؛ توکن Agent جای رمز SSH نیست." else
            "A saved server and valid SSH access are needed. This tool uses SSH directly; an Agent token is not an SSH password."
        else -> null
    }
    val commands = if (this == CommandRoute.MANAGE_SERVERS) listOf(
        HelpCommand(if (fa) "نصب اولیهٔ Agent؛ ابتدا دستور را بررسی کنید" else "First-time Agent install; review before running",
            "curl -fsSL https://raw.githubusercontent.com/Mohammad1724/didban/main/agent/install.sh -o didban-install.sh && sudo bash didban-install.sh"),
        HelpCommand(if (fa) "دیدن وضعیت سرویس Agent" else "Check Agent service status", "sudo systemctl status didban-agent --no-pager")
    ) else emptyList()
    return content.copy(prerequisite = prerequisite, commands = commands)
}

/** Server subpanels share the fleet route but need different instructions. */
internal fun CommandDestination.helpRoute(): CommandRoute = if (route == CommandRoute.FLEET) {
    when (serverPane) {
        ServerPane.ADD, ServerPane.EDIT -> CommandRoute.MANAGE_SERVERS
        ServerPane.DETAILS -> CommandRoute.SERVER_DOSSIER
        ServerPane.LIST -> CommandRoute.FLEET
    }
} else route
