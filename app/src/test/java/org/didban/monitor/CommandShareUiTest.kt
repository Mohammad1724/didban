package org.didban.monitor

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/**
 * صفحهٔ «اشتراک اینترنت با VPN»: همان چیزی که کاربر می‌بیند.
 *
 * تمرکز تست روی سه چیز است که واقعاً مهم‌اند: ابزار بدون VPN هشدار می‌دهد،
 * رمز پیش‌فرض از همان اول وجود دارد و پورت نامعتبر اجازهٔ شروع نمی‌دهد.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w400dp-h900dp")
@LooperMode(LooperMode.Mode.PAUSED)
class CommandShareUiTest {

    @get:Rule val compose = createComposeRule()
    private val context get() = RuntimeEnvironment.getApplication()
    private val copy = CommandCopy.forLanguage("fa")

    @Before fun reset() {
        context.getSharedPreferences("didban", Context.MODE_PRIVATE).edit().clear().commit()
        ShareRuntime.reset(1080)
    }

    private fun screen() {
        compose.setContent { CommandTheme("light", "fa") { CommandShareScreen(copy, {}, {}) } }
    }

    @Test fun `screen explains the tool before anything is running`() {
        screen()
        compose.onNodeWithText(copy.shareStopped).assertIsDisplayed()
        compose.onNodeWithText(copy.shareVpnOff).assertIsDisplayed()
        compose.onNodeWithText(copy.shareStart).assertIsDisplayed()
    }

    @Test fun `a missing vpn is called out instead of silently sharing`() {
        screen()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(copy.shareVpnMissing))
        compose.onNodeWithText(copy.shareVpnMissing).assertIsDisplayed()
    }

    @Test fun `the honest tv limitation is part of the screen`() {
        screen()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(copy.shareTvTitle))
        compose.onNodeWithText(copy.shareTvTitle).assertIsDisplayed()
        compose.onNodeWithText(copy.shareTvBody).assertIsDisplayed()
    }

    @Test fun `default config is usable and already locked with a generated password`() {
        val saved = Prefs.getShareConfig(context)
        assertTrue(saved.valid)
        assertTrue(saved.requireAuth)
        assertTrue(saved.password.length >= 4)
        assertTrue(saved.port >= 1024)
        // خواندن دوباره باید همان رمز را بدهد، نه رمز تازه
        assertTrue(Prefs.getShareConfig(context).password == saved.password)
    }

    @Test fun `an invalid port is refused with a message`() {
        screen()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(copy.sharePort))
        // فیلد پورت با برچسبش هدف گرفته می‌شود؛ صفحه سه فیلد متنی دارد.
        compose.onNode(hasSetTextAction() and hasText(copy.sharePort)).performTextClearance()
        compose.onNode(hasSetTextAction() and hasText(copy.sharePort)).performTextInput("80")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(copy.shareErrorPort))
        compose.onNodeWithText(copy.shareErrorPort).assertIsDisplayed()
    }

    @Test fun `tools tab lists the new tool next to the other independent tools`() {
        compose.setContent {
            CommandCenterApp(androidx.compose.runtime.mutableStateOf(null)) {
                Prefs.ServerLoadResult(mutableListOf())
            }
        }
        compose.onNodeWithTag("primary-tools").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(copy.shareTitle))
        compose.onNodeWithText(copy.shareTitle).assertIsDisplayed()
    }
}
