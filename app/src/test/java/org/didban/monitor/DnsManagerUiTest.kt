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

/** DNS manager state primitives must render without a live Cloudflare account. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w400dp-h900dp")
@LooperMode(LooperMode.Mode.PAUSED)
class DnsManagerUiTest {
    @get:Rule val compose = createComposeRule()
    private val copy = CommandCopy.forLanguage("fa")

    @Before
    fun resetPreferences() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("didban", Context.MODE_PRIVATE).edit().clear().commit()
        Prefs.setLanguage(context, "fa")
    }

    private fun openScreen() {
        compose.setContent {
            CommandTheme(themeMode = "dark", language = "fa") {
                CommandDnsManagerScreen(copy, {})
            }
        }
    }

    @Test
    fun `DNS starts with a shared empty state when no zones are loaded`() {
        openScreen()
        compose.onNodeWithText(copy.dnsNoZonesYet).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `missing token is a local error with the existing retry action`() {
        openScreen()
        compose.onNodeWithText(copy.dnsLoadZones).performClick()
        compose.onNodeWithText(copy.dnsNoToken).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(copy.retry).assertIsDisplayed()
    }
}
