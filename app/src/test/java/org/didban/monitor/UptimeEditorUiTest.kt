package org.didban.monitor

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Uptime editor guidance: no live checks run; the form must explain itself. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class UptimeEditorUiTest {
    @get:Rule val compose = createComposeRule()
    private val copy = CommandCopy.forLanguage("fa")

    @Before fun resetPreferences() {
        RuntimeEnvironment.getApplication().getSharedPreferences("didban", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun openEditor() {
        compose.setContent {
            CommandTheme(themeMode = "dark", language = "fa") {
                CommandUptimeEditorScreen(copy, {})
            }
        }
    }

    @Test fun `keyword field only appears for keyword checks`() {
        openEditor()
        // Default HTTP form: no keyword field.
        compose.onNodeWithText(copy.upKeywordHint).assertDoesNotExist()
        // All six check types are visible without scrolling sideways.
        listOf("HTTP", "HTTPS", "TCP", "PING", "KEYWORD", "SSL").forEach { kind ->
            compose.onNodeWithText(kind).assertExists()
        }
        // Switching to KEYWORD reveals the field; switching back hides it.
        // Match the clickable chip itself: clicking the bare text node is a
        // silent no-op (same lesson as RadarNavigationUiTest's chooseButton).
        compose.onNode(hasText("KEYWORD") and hasClickAction()).performClick()
        compose.onNodeWithText(copy.upKeywordHint).assertExists()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(copy.upKeywordHint))
        compose.onNodeWithText(copy.upKeywordHint).assertIsDisplayed()
        compose.onNode(hasText("HTTP") and hasClickAction()).performClick()
        compose.onNodeWithText(copy.upKeywordHint).assertDoesNotExist()
    }

    @Test fun `private-network toggle is localized, not hardcoded English`() {
        openEditor()
        compose.onNodeWithText(copy.upAllowPrivateTitle).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Allow private network targets").assertDoesNotExist()
    }
}
