package org.didban.monitor

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkbenchEngineCopyTest {

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
