package org.didban.monitor

import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w400dp-h900dp")
@LooperMode(LooperMode.Mode.PAUSED)
class CommandRedesignUiTest {
    @get:Rule val compose = createComposeRule()
    private val copy = CommandCopy.forLanguage("fa")
    @Before fun reset() {
        RuntimeEnvironment.getApplication().getSharedPreferences("didban", Context.MODE_PRIVATE).edit().clear().commit()
    }
    private fun app() {
        compose.setContent { CommandCenterApp(mutableStateOf(null)) {
            Prefs.ServerLoadResult(mutableListOf(ServerConfig(71, "UI Node", "node.example")))
        } }
    }
    @Test fun `default is light and existing explicit choices survive`() {
        val context = RuntimeEnvironment.getApplication()
        assertEquals("light", Prefs.getThemeMode(context))
        listOf("dark", "auto", "light").forEach { Prefs.setThemeMode(context, it); assertEquals(it, Prefs.getThemeMode(context)) }
    }
    @Test fun `four tabs expose independent tools and data settings without a workspace menu`() {
        app()
        compose.onNodeWithTag("primary-servers").assertIsSelected()
        compose.onNodeWithTag("primary-tools").performClick().assertIsSelected()
        compose.onAllNodesWithText(copy.uiTools).assertCountEquals(2) // header + tab, no duplicate content title
        compose.onNodeWithText(copy.cfScanner).assertIsDisplayed()
        compose.onNodeWithText(copy.realitySni).assertExists()
        compose.onNodeWithTag("primary-settings").performClick().assertIsSelected()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(copy.vault))
        compose.onNodeWithText(copy.vault).assertIsDisplayed()
    }
    @Test fun `details replace fleet rather than opening a sheet and retain scoped tools`() {
        app()
        compose.onNodeWithText("UI Node").performScrollTo().performClick()
        compose.onNodeWithText(copy.fleetDetails).assertIsDisplayed()
        compose.onNodeWithText(copy.serversSearchPlaceholder).assertDoesNotExist()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(copy.docker))
        compose.onNodeWithText(copy.docker).assertIsDisplayed()
        compose.onNodeWithContentDescription(copy.back).performClick()
        compose.onNodeWithText(copy.uiMyServers).assertExists()
    }
    @Test fun `embedded editor intercepts hardware Back and preserves the draft on cancel`() {
        lateinit var activity: ComponentActivity
        var closed = false
        compose.setContent {
            activity = LocalContext.current.findActivity() as ComponentActivity
            CommandTheme("light", "fa") { CommandServerEditor(copy, null, {}, { closed = true }, embedded = true) }
        }
        compose.onNodeWithText(copy.fleetName).performScrollTo().performTextInput("Keep me")
        compose.runOnIdle { activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText(copy.fleetDiscardTitle).assertIsDisplayed()
        compose.onNodeWithText(copy.cancel).performClick()
        compose.onNodeWithText(copy.fleetName).assertTextContains("Keep me")
        compose.runOnIdle { assertFalse(closed); activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText(copy.fleetDiscard).performClick()
        compose.runOnIdle { assertTrue(closed) }
    }
    @Test fun `glass surface supplies readable default content color in dark mode`() {
        var color = androidx.compose.ui.graphics.Color.Transparent
        compose.setContent { CommandTheme("dark", "fa") {
            CommandLayerSurface {
                color = androidx.compose.material3.LocalContentColor.current
                androidx.compose.material3.Text("Glass content")
            }
        } }
        compose.onNodeWithText("Glass content").assertIsDisplayed()
        compose.runOnIdle { assertEquals(CommandDarkPalette.textPrimary, color) }
    }
}
