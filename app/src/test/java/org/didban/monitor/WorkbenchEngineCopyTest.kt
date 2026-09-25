package org.didban.monitor

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkbenchEngineCopyTest {

    @Test
    fun `proxy subscription failures localize without exposing provider text in the screen contract`() {
        assertEquals(
            "Subscription fetch failed with HTTP 403.",
            ProxyFailure(ProxyFailureKind.HTTP_STATUS, "403").localized(CommandCopyEn)
        )
        assertEquals(
            "دریافت اشتراک با HTTP 403 ناموفق بود.",
            ProxyFailure(ProxyFailureKind.HTTP_STATUS, "403").localized(CommandCopyFa)
        )
        assertEquals("The subscription URL is invalid.", ProxyFailure(ProxyFailureKind.INVALID_URL).localized(CommandCopyEn))
        assertEquals("نشانی اشتراک معتبر نیست.", ProxyFailure(ProxyFailureKind.INVALID_URL).localized(CommandCopyFa))
    }

    @Test
    fun `sftp failures and dynamic file values localize at the screen boundary`() {
        assertEquals("File read failed", SftpFailure(SftpFailureKind.READ).localized(CommandCopyEn))
        assertEquals("خواندن فایل ناموفق بود", SftpFailure(SftpFailureKind.READ).localized(CommandCopyFa))
        assertEquals(
            "The file is 8192 KB and too large for the text editor.",
            SftpFailure(SftpFailureKind.FILE_TOO_LARGE, "8192").localized(CommandCopyEn)
        )
        assertEquals(
            "فایل 8192 کیلوبایت است و برای ویرایشگر متن بزرگ است.",
            SftpFailure(SftpFailureKind.FILE_TOO_LARGE, "8192").localized(CommandCopyFa)
        )

        val directory = SftpFileItem("etc", "/etc", isDirectory = true)
        assertEquals("DIR", directory.formattedSize(CommandCopyEn))
        assertEquals("پوشه", directory.formattedSize(CommandCopyFa))
    }

    @Test
    fun `subscription quota fallbacks use the active locale`() {
        val info = SubscriptionInfo()
        assertEquals("Unlimited", info.totalFormatted(CommandCopyEn))
        assertEquals("نامحدود", info.totalFormatted(CommandCopyFa))
        assertEquals("Unlimited", info.expireDateFormatted(CommandCopyEn))
        assertEquals("نامحدود", info.expireDateFormatted(CommandCopyFa))
    }

    @Test
    fun `tunnel deploy summaries localize while retaining remote detail`() {
        val foreignFailure = AutoDeployServerResult(
            serverName = "edge",
            host = "203.0.113.10",
            role = "foreign",
            success = false,
            status = "unreachable",
            message = "agent timeout"
        )
        val result = AutoDeployResult(null, foreignFailure, false, "legacy")
        assertEquals("Foreign host: agent timeout", result.localizedSummary(CommandCopyEn))
        assertEquals("میزبان خارج: agent timeout", result.localizedSummary(CommandCopyFa))

        val validation = AutoDeployResult(null, null, false, "legacy", validationFailed = true)
        assertEquals("Tunnel field validation failed; nothing was deployed.", validation.localizedSummary(CommandCopyEn))
    }
}
