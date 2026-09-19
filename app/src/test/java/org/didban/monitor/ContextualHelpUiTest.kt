package org.didban.monitor

import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelProvider
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
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h800dp")
@LooperMode(LooperMode.Mode.PAUSED)
class ContextualHelpUiTest {
    @get:Rule val compose = createComposeRule()
    private val copy = CommandCopy.forLanguage("fa")

    @Before fun clearPrefs() {
        val ctx = RuntimeEnvironment.getApplication()
        ctx.getSharedPreferences("didban", Context.MODE_PRIVATE).edit().clear().commit()
        Prefs.setLanguage(ctx, "fa")
    }

    @Test fun `visible help button opens useful sections and closes without running a tool`() {
        var opened by mutableStateOf(false)
        compose.setContent { CommandTheme("dark", "fa") {
            CommandHelpButton("fa") { opened = true }
            if (opened) CommandHelpDialog(CommandRoute.UPTIME, "fa", copy) { opened = false }
        } }
        compose.onNodeWithText("راهنما").assertHasClickAction().performClick()
        compose.onNodeWithText("این بخش برای چیست؟").assertIsDisplayed()
        compose.onNodeWithText(CommandRoute.UPTIME.helpContent("fa").summary).assertIsDisplayed()
        compose.onNodeWithTag("help-body").performScrollToNode(hasText("چطور شروع کنم؟"))
        compose.onNodeWithText("چطور شروع کنم؟").assertIsDisplayed()
        compose.onNodeWithTag("help-body").performScrollToNode(hasText("نکات و محدودیت‌ها"))
        compose.onNodeWithText("نکات و محدودیت‌ها").assertIsDisplayed()
        compose.onNodeWithContentDescription("بستن").performClick()
        compose.onNodeWithTag("page-guide").assertDoesNotExist()
        compose.runOnIdle { assertFalse(opened) }
    }

    @Test fun `all routes render their own bilingual guide and reset scroll on route change`() {
        var route by mutableStateOf(CommandRoute.FLEET)
        var language by mutableStateOf("fa")
        compose.setContent { CommandTheme("dark", language) {
            CommandHelpDialog(route, language, CommandCopy.forLanguage(language)) {}
        } }
        for (lang in listOf("fa", "en")) for (next in CommandRoute.values()) {
            compose.runOnIdle { language = lang; route = next }
            compose.onNodeWithText(next.helpContent(lang).summary).assertExists()
            compose.onNodeWithTag("help-body").performScrollToNode(hasText(next.helpContent(lang).warning!!))
            compose.onNodeWithText(next.helpContent(lang).warning!!).assertExists()
        }
    }

    @Test @Config(qualifiers = "w320dp-h640dp")
    fun `long guide scrolls at large font and leaves close accessible`() {
        compose.setContent { CommandTheme("light", "fa") {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                CommandHelpDialog(CommandRoute.MANAGE_SERVERS, "fa", copy) {}
            }
        } }
        compose.onNodeWithTag("help-body").performScrollToNode(hasText(CommandRoute.MANAGE_SERVERS.helpContent("fa").warning!!))
        compose.onNodeWithText("هشدار").assertExists()
        compose.onNodeWithContentDescription("بستن").assertIsDisplayed()
    }

    @Test fun `copy command copies text only and gives feedback`() {
        lateinit var clipboard: ClipboardManager
        compose.setContent { CommandTheme("dark", "en") {
            clipboard = LocalClipboardManager.current
            CommandHelpDialog(CommandRoute.MANAGE_SERVERS, "en", CommandCopy.forLanguage("en")) {}
        } }
        val command = CommandRoute.MANAGE_SERVERS.helpContent("en").commands.first()
        compose.onNodeWithTag("help-body").performScrollToNode(hasText(command.label))
        compose.onAllNodesWithText("Copy")[0].performClick()
        compose.onNodeWithText("Copied").assertExists()
        compose.runOnIdle { assertEquals(command.value, clipboard.getText()?.text) }
    }

    @Test fun `server editor guide opens above form and does not lose unsaved draft`() {
        lateinit var activity: ComponentActivity
        var closed = false
        var saved = false
        compose.setContent { CommandTheme("dark", "fa") {
            activity = LocalContext.current.findActivity() as ComponentActivity
            CommandServerEditor(copy, null, { saved = true }, { closed = true })
        } }
        compose.onNodeWithText(copy.fleetName).performScrollTo().performTextInput("سرور آزمایشی")
        compose.onNodeWithText("راهنما").performClick()
        compose.onNodeWithTag("page-guide").assertExists()
        compose.onNodeWithText(CommandRoute.MANAGE_SERVERS.helpContent("fa").summary).assertExists()
        compose.onNodeWithContentDescription("بستن").performClick()
        compose.onNodeWithTag("page-guide").assertDoesNotExist()
        compose.runOnIdle {
            assertFalse(saved); assertFalse(closed)
            assertEquals("سرور آزمایشی", ViewModelProvider(activity)[ServerEditorViewModel::class.java].draft!!.name)
        }
        compose.onNodeWithText("سرور آزمایشی").assertExists()
    }

    @Test fun `server detail sheet has reachable help without closing inspector`() {
        val server = ServerConfig(id = 9, name = "Example server", host = "node.example.com", token = "fixture")
        var help by mutableStateOf(false)
        var closed = false
        val destination = CommandDestination(CommandRoute.FLEET, 9, ServerPane.DETAILS)
        compose.setContent { CommandTheme("dark", "fa") {
            CommandServerHub(copy, listOf(server), false, emptyMap(), destination, {}, { help = true },
                {}, {}, { closed = true }, {}, {}, { _, _ -> })
            if (help) CommandHelpDialog(destination.helpRoute(), "fa", copy) { help = false }
        } }
        compose.onAllNodesWithText("راهنما").onLast().performClick()
        compose.onNodeWithText(CommandRoute.SERVER_DOSSIER.helpContent("fa").summary).assertExists()
        compose.onNodeWithContentDescription("بستن").performClick()
        compose.runOnIdle { assertFalse(closed) }
        compose.onNodeWithText(copy.fleetDetails).assertExists()
    }
}
