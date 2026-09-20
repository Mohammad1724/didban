package org.didban.monitor

import android.app.Application
import android.content.Context
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
import org.robolectric.shadows.ShadowDialog

/** Real Compose form/click regressions, not just string/source assertions. No live Agent is used. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ServerEditorUiTest {
    @get:Rule val compose = createComposeRule()
    private val copy = CommandCopy.forLanguage("fa")
    private lateinit var activity: ComponentActivity
    private val model get() = ViewModelProvider(activity)[ServerEditorViewModel::class.java]

    @Before fun resetPreferences() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("didban", Context.MODE_PRIVATE).edit().clear().commit()
        Prefs.setLanguage(context, "fa")
    }

    private fun openEditor() {
        compose.setContent {
            activity = LocalContext.current.findActivity() as ComponentActivity
            CommandTheme(themeMode = "dark", language = "fa") {
                CommandServerEditor(copy, null, {}, {})
            }
        }
    }

    private fun field(label: String): SemanticsNodeInteraction {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(label))
        return compose.onNodeWithText(label)
    }

    private fun assertError(text: String) {
        // Scroll to the actual message, not just its card heading: the pinned 48dp
        // help control and large fonts can leave the body below the viewport.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    // This exact interaction failed with NAME on 52534a9, despite the visible entered name.
    @Test fun `save reads the name visibly entered in the actual dialog`() {
        openEditor()
        compose.onNodeWithText(copy.fleetName).performScrollTo().performTextInput("Tehran node")
        compose.onNodeWithText("Tehran node").assertExists()
        compose.onNodeWithText(copy.save).performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(copy.operationFailed))
        compose.onNodeWithText(copy.srvNameRequired).assertDoesNotExist()
        compose.onNodeWithText(copy.srvHostInvalid).performScrollTo().assertIsDisplayed()
    }

    @Test fun `test action also validates the current name instead of the initial draft`() {
        openEditor()
        field(copy.fleetName).performTextInput("تهران")
        compose.onNodeWithText(copy.srvTestAgent).performClick()
        assertError(copy.srvHostInvalid)
        compose.onNodeWithText(copy.srvNameRequired).assertDoesNotExist()
    }

    @Test fun `editing several fields preserves all previous input and repeated actions see latest changes`() {
        openEditor()
        field(copy.fleetName).performTextInput("Tehran node")
        field(copy.srvAgentHost).performTextInput("node.example.com")
        field(copy.port).performTextReplacement("9443")
        field(copy.srvAgentToken).performTextInput("a".repeat(48))
        field(copy.srvAdminToken).performTextInput("b".repeat(48))
        field(copy.srvTlsFingerprint).performTextInput("01".repeat(32))
        field(copy.srvCpuAlert).performTextReplacement("77")
        field(copy.srvMemAlert).performTextReplacement("101")
        compose.onNodeWithText(copy.save).performClick()
        assertError(copy.fleetThresholdInvalid)
        field(copy.srvMemAlert).performTextReplacement("66")
        field(copy.fleetName).performTextReplacement("نام جدید")
        compose.runOnIdle {
            val server = model.serverForAction()!!
            assertEquals("نام جدید", server.name)
            assertEquals("node.example.com", server.host)
            assertEquals(9443, server.port)
            assertEquals("a".repeat(48), server.token)
            assertEquals("b".repeat(48), server.adminToken)
            assertEquals("01".repeat(32), server.fingerprint)
            assertEquals(77, server.cpuAlert)
            assertEquals(66, server.memAlert)
            assertNull(connectionValidation(server))
        }
        // Force a DIFFERENT validation failure to prove a second click reads fresh data too.
        field(copy.port).performTextClearance()
        compose.onNodeWithText(copy.srvTestAgent).performClick()
        assertError(copy.fleetPortInvalid)
        compose.onNodeWithText(copy.fleetThresholdInvalid).assertDoesNotExist()
        field(copy.fleetName).assertTextContains("نام جدید")
    }

    @Test fun `import is a real button enabled by input and fills the current draft`() {
        openEditor()
        compose.onNodeWithText(copy.srvQuickConnectImport)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHasClickAction().assertIsNotEnabled()
        field(copy.srvCpuAlert).performTextReplacement("72")
        val code = "didban://203.0.113.8:9443?token=${"a".repeat(48)}&admin_token=${"b".repeat(48)}&fp=${"01".repeat(32)}&name=Imported%20Node"
        field(copy.srvQuickConnectLabel).performTextInput(code)
        compose.onNodeWithText(copy.srvQuickConnectImport).assertIsEnabled().performClick()
        compose.runOnIdle {
            val draft = model.draft!!
            assertEquals("Imported Node", draft.name)
            assertEquals("203.0.113.8", draft.host)
            assertEquals("9443", draft.port)
            assertEquals("72", draft.cpuAlert)
            assertEquals("", draft.quickConnect)
            assertNull(connectionValidation(model.serverForAction()!!))
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(copy.srvQuickConnectImported))
        compose.onNodeWithText(copy.srvQuickConnectImported).assertIsDisplayed()
        field(copy.fleetName).assertTextContains("Imported Node")
        field(copy.srvQuickConnectLabel)
        compose.onNodeWithText(copy.srvQuickConnectImport).assertIsNotEnabled()
    }

    @Test fun `invalid import leaves manually entered data intact`() {
        openEditor()
        field(copy.fleetName).performTextInput("Keep this name")
        field(copy.srvQuickConnectLabel).performTextInput("not a connection code")
        compose.onNodeWithText(copy.srvQuickConnectImport).performClick()
        assertError(copy.srvQuickConnectInvalid)
        field(copy.fleetName).assertTextContains("Keep this name")
    }

    @Test fun `editor and discard dialog allow screenshots by default and react to protection changes`() {
        openEditor()
        lateinit var editorWindow: android.view.Window
        compose.runOnIdle {
            editorWindow = ShadowDialog.getLatestDialog().window!!
            assertEquals(0, activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
            assertEquals(0, ShadowDialog.getLatestDialog().window!!.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
            Prefs.setScreenshotProtectionEnabled(activity, true)
        }
        compose.runOnIdle {
            assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            assertTrue(ShadowDialog.getLatestDialog().window!!.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        }
        field(copy.fleetName).performTextInput("Dirty form")
        compose.onNodeWithText(copy.close).performClick()
        compose.onNodeWithText(copy.fleetDiscardTitle).assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(ShadowDialog.getLatestDialog().window!!.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            Prefs.setScreenshotProtectionEnabled(activity, false)
        }
        compose.runOnIdle {
            assertEquals(0, activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
            assertEquals(0, ShadowDialog.getLatestDialog().window!!.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
        }
        compose.onNodeWithText(copy.cancel).performClick()
        compose.runOnIdle {
            assertEquals(0, activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
            assertEquals(0, ShadowDialog.getLatestDialog().window!!.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
        }
        compose.runOnIdle { assertEquals(0, editorWindow.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) }
        field(copy.fleetName).assertTextContains("Dirty form")
    }

    @Test fun `quick-connect import is offered when editing an existing server`() {
        val context = RuntimeEnvironment.getApplication<Application>()
        Prefs.saveServers(
            context,
            listOf(
                ServerConfig(
                    id = 42, name = "old", host = "old.example", port = 8686,
                    token = "tok", adminToken = "adm", useTls = true,
                    fingerprint = "ab".repeat(32), cpuAlert = 90, memAlert = 90
                )
            )
        )
        compose.setContent {
            activity = LocalContext.current.findActivity() as ComponentActivity
            CommandTheme(themeMode = "dark", language = "fa") {
                CommandServerEditor(copy, 42, {}, {})
            }
        }
        compose.waitForIdle()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(copy.srvQuickConnectImport))
        compose.onNodeWithText(copy.srvQuickConnectImport).assertIsDisplayed()
    }
}
