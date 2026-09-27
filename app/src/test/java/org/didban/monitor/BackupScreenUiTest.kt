package org.didban.monitor

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollToNode
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/**
 * صفحهٔ پشتیبان‌گیری باید صادق باشد: رمز الزامی است، حالت‌های ادغام/بازنویسی
 * توضیح روشن دارند و برچسب‌های گمراه‌کننده بازنمی‌گردند.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class BackupScreenUiTest {
    @get:Rule val compose = createComposeRule()
    private val copy = CommandCopy.forLanguage("fa")

    @Before fun resetPreferences() {
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("didban", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun open() {
        compose.setContent {
            CommandTheme(themeMode = "dark", language = "fa") {
                CommandBackupScreen(copy, {})
            }
        }
    }

    @Test fun `password is labelled required with an honest explanation`() {
        open()
        compose.onNodeWithText(copy.backupPasswordField).assertExists()
        compose.onNodeWithText(copy.backupPasswordWhy).assertExists()
    }

    @Test fun `paste button fills the restore field from the clipboard`() {
        val app = RuntimeEnvironment.getApplication()
        val clip = app.getSystemService(ClipboardManager::class.java)
        val code = "DIDBAN_BACKUP_V2:AbCdEf123456"
        clip.setPrimaryClip(ClipData.newPlainText("code", code))
        open()
        // The restore card sits below the fold; an off-screen click is a no-op.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(copy.pasteAction))
        compose.onNodeWithText(copy.pasteAction).performClick()
        compose.onNodeWithText(code).assertExists()
        // The created-backup output card must NOT appear from pasting.
        compose.onNodeWithText(copy.backupOutput).assertDoesNotExist()
    }

    @Test fun `paste button reports an empty clipboard instead of silence`() {
        open()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(copy.pasteAction))
        compose.onNodeWithText(copy.pasteAction).performClick()
        compose.onNodeWithText(copy.backupEmpty).assertExists()
    }

    @Test fun `merge and overwrite modes carry a plain-language hint`() {
        open()
        // The mode row sits below the fold; an off-screen click is a silent no-op.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(copy.backupModeOverwrite))
        compose.onNodeWithText(copy.backupModeOverwrite).performClick()
        compose.onNodeWithText(copy.backupOverwriteHint).assertExists()
        compose.onNodeWithText(copy.backupModeMerge).performClick()
        compose.onNodeWithText(copy.backupMergeHint).assertExists()
    }
}
