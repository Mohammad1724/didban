package org.didban.monitor

import android.app.Application
import android.content.Context
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.window.SecureFlagPolicy
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
@Config(sdk = [33], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ScreenshotProtectionUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var activity: ComponentActivity

    @Before fun resetPreferences() {
        RuntimeEnvironment.getApplication().getSharedPreferences("didban", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun secure(): Boolean = activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

    @Test fun `protection defaults off and dialog policy matches user preference`() {
        assertFalse(Prefs.isScreenshotProtectionEnabled(RuntimeEnvironment.getApplication()))
        assertEquals(SecureFlagPolicy.SecureOff, screenshotDialogPolicy(false))
        assertEquals(SecureFlagPolicy.SecureOn, screenshotDialogPolicy(true))
    }

    @Test fun `nested sensitive surfaces release flags only when last surface closes`() {
        val first = mutableStateOf(true)
        val second = mutableStateOf(true)
        Prefs.setScreenshotProtectionEnabled(RuntimeEnvironment.getApplication(), true)
        compose.setContent {
            activity = LocalContext.current.findActivity() as ComponentActivity
            if (first.value) SecureWindowEffect()
            if (second.value) SecureWindowEffect()
        }
        compose.runOnIdle { assertTrue(secure()); first.value = false }
        compose.runOnIdle { assertTrue(secure()); second.value = false }
        compose.runOnIdle { assertFalse(secure()) }
    }

    @Test fun `turning protection off releases all existing flags and later screens remain capturable`() {
        val show = mutableStateOf(true)
        compose.setContent {
            activity = LocalContext.current.findActivity() as ComponentActivity
            if (show.value) {
                SecureWindowEffect()
                SecureWindowEffect()
            }
        }
        compose.runOnIdle { assertFalse(secure()); Prefs.setScreenshotProtectionEnabled(activity, true) }
        compose.runOnIdle { assertTrue(secure()); Prefs.setScreenshotProtectionEnabled(activity, false) }
        compose.runOnIdle { assertFalse(secure()); show.value = false }
        compose.runOnIdle { assertFalse(secure()); show.value = true }
        compose.runOnIdle { assertFalse(secure()) }
    }

    @Test fun `explicitly disabled sensitive effect never protects ordinary screens`() {
        Prefs.setScreenshotProtectionEnabled(RuntimeEnvironment.getApplication(), true)
        compose.setContent {
            activity = LocalContext.current.findActivity() as ComponentActivity
            SecureWindowEffect(enabled = false)
        }
        compose.runOnIdle { assertFalse(secure()) }
    }

    @Test fun `settings switch is accessible and persists both choices without restarting`() {
        val copy = CommandCopy.forLanguage("fa")
        compose.setContent {
            activity = LocalContext.current.findActivity() as ComponentActivity
            CommandTheme(themeMode = "dark", language = "fa") {
                CommandSettingsScreen(copy, "dark", "fa", {}, {})
            }
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(copy.setScreenshotProtection))
        val toggle = compose.onNodeWithText(copy.setScreenshotProtection)
        toggle.assertIsOff().performClick().assertIsOn()
        compose.runOnIdle { assertTrue(Prefs.isScreenshotProtectionEnabled(activity)); assertFalse(secure()) }
        toggle.performClick().assertIsOff()
        compose.runOnIdle { assertFalse(Prefs.isScreenshotProtectionEnabled(activity)) }
    }
}
