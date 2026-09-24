package org.didban.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
