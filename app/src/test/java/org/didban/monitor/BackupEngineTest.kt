package org.didban.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupEngineTest {
    @Test
    fun `inspect rejects oversized input before parsing with a typed message`() {
        val raw = "x".repeat(8 * 1024 * 1024 + 1)
        val preview = BackupEngine.inspectBackup(raw)
        assertFalse(preview.isValid)
        assertEquals(BackupMessageKind.TOO_LARGE, preview.error?.kind)
    }

    @Test
    fun `inspect rejects unsupported backup version`() {
        val preview = BackupEngine.inspectBackup(
            """{"version":999,"servers":[],"tunnels":[],"uptime_targets":[]}"""
        )
        assertFalse(preview.isValid)
    }

    @Test
    fun `encrypted backup without password is not restorable preview`() {
        val preview = BackupEngine.inspectBackup("DIDBAN_BACKUP_V2:not-a-payload")
        assertFalse(preview.isValid)
    }

    private fun encryptedBlob(json: String, password: String = "test-pw-123") =
        "DIDBAN_BACKUP_V2:" + EncryptedVault.encrypt(json, password)

    @Test
    fun `backup code hard-wrapped by a messenger still inspects with the right password`() {
        val blob = encryptedBlob("""{"version":2,"servers":[],"tunnels":[],"uptime_targets":[]}""")
        // Messaging apps and editors hard-wrap long codes; whitespace inside
        // the pasted base64 payload must not break decryption.
        val wrapped = blob.chunked(40).joinToString("\n")
        val preview = BackupEngine.inspectBackup(wrapped, "test-pw-123")
        assertTrue(preview.isValid)
        assertTrue(preview.isEncrypted)
    }

    @Test
    fun `wrapped backup code with CRLF and indentation still inspects`() {
        val blob = encryptedBlob("""{"version":2,"servers":[],"tunnels":[],"uptime_targets":[]}""")
        // Quoted emails and forwards indent every wrapped line.
        val mangled = blob.chunked(64).joinToString("\r\n  ")
        val preview = BackupEngine.inspectBackup(mangled, "test-pw-123")
        assertTrue(preview.isValid)
    }

    @Test
    fun `wrapped backup code with the wrong password is still rejected`() {
        val blob = encryptedBlob("""{"version":2,"servers":[],"tunnels":[],"uptime_targets":[]}""")
        val wrapped = blob.chunked(40).joinToString("\n")
        val preview = BackupEngine.inspectBackup(wrapped, "wrong-password")
        assertFalse(preview.isValid)
        assertEquals(BackupMessageKind.PASSWORD_INVALID, preview.error?.kind)
    }
}
