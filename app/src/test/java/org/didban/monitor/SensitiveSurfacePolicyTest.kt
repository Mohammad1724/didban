package org.didban.monitor

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class SensitiveSurfacePolicyTest {
    private fun projectFile(path: String): File? = sequenceOf(File(path), File("../$path"), File("../../$path"))
        .firstOrNull { it.isFile }

    @Test
    fun `manifest disables backup and declares extraction exclusions`() {
        val manifest = projectFile("app/src/main/AndroidManifest.xml")
        assumeTrue("source tree unavailable", manifest != null)
        val text = manifest!!.readText()
        assertTrue(text.contains("android:allowBackup=\"false\""))
        assertTrue(text.contains("android:fullBackupContent=\"false\""))
        assertTrue(text.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))

        val rules = projectFile("app/src/main/res/xml/data_extraction_rules.xml")
        assumeTrue("rules unavailable", rules != null)
        val rulesText = rules!!.readText()
        assertTrue(rulesText.contains("<cloud-backup"))
        assertTrue(rulesText.contains("<device-transfer>"))
        assertTrue(rulesText.contains("domain=\"sharedpref\" path=\".\""))
    }

    @Test
    fun `credential entry screens enable secure window policy`() {
        val files = listOf(
            "CommandProtectScreens.kt",
            "CommandTunnelEditorScreen.kt",
            "CommandWorkbenchTools.kt",
            "CommandManageServersScreen.kt",
            "CommandServerEditor.kt",
            "CommandServicesScreen.kt",
            "CommandSecurityScreen.kt"
        )
        files.forEach { name ->
            val file = projectFile("app/src/main/java/org/didban/monitor/$name")
            assumeTrue("source tree unavailable", file != null)
            assertTrue("$name lacks SecureWindowEffect", file!!.readText().contains("SecureWindowEffect()"))
        }
    }
}
