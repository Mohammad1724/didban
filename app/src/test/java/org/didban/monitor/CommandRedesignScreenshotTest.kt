package org.didban.monitor

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Before
import org.junit.After
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File

/** Actual Compose renders, with explicitly synthetic hosts and no live Agent. Not phone evidence. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w400dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class CommandRedesignScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var contentView: View
    private val context get() = RuntimeEnvironment.getApplication()
    @Before fun reset() {
        context.getSharedPreferences("didban", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @After fun clearFixture() { Repo.remove(970) }
    private fun open(language: String, theme: String) {
        Prefs.setLanguage(context, language)
        Prefs.setThemeMode(context, theme)
        compose.setContent {
            contentView = LocalView.current
            CommandCenterApp(mutableStateOf(null)) {
            Prefs.ServerLoadResult(mutableListOf(ServerConfig(970, "Demo node", "demo.example")))
        } }
    }
    private fun shot(name: String) {
        // PixelCopy waits for a real display compositor under Robolectric. Draw the
        // actual laid-out Compose view into a native bitmap instead.
        lateinit var image: Bitmap
        compose.runOnIdle {
            image = Bitmap.createBitmap(contentView.width, contentView.height, Bitmap.Config.ARGB_8888)
            contentView.draw(Canvas(image))
        }
        val file = File("build/reports/redesign/screenshots/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        check(image.width > 300 && image.height > 500)
    }
    @Test fun `Persian light phone renders all four sections and full details`() {
        val copy = CommandCopy.forLanguage("fa")
        open("fa", "light")
        shot("fa-light-servers")
        compose.onNodeWithText("Demo node").performScrollTo().performClick()
        shot("fa-light-details")
        compose.onNodeWithTag("primary-monitoring").performClick()
        shot("fa-light-monitoring")
        compose.onNodeWithTag("primary-tools").performClick()
        shot("fa-light-tools")
        compose.onNodeWithText(copy.cfScanner).performClick()
        shot("fa-light-scanner")
        compose.onNodeWithTag("primary-settings").performClick()
        shot("fa-light-settings")
    }
    @Test fun `Persian dark glass phone renders measured fixture and operational pages`() {
        // Explicit synthetic measurements for render coverage, never live Agent evidence.
        Repo.set(970, Metrics.fromJson(JSONObject()
            .put("cpu", JSONObject().put("usage", 34))
            .put("uptime_sec", 172800)
            .put("memory", JSONObject().put("usage_pct", 61)
                .put("total", 8589934592L).put("used", 5240000000L))), latencyMs = 48f)
        val copy = CommandCopy.forLanguage("fa")
        open("fa", "dark")
        shot("fa-dark-servers")
        compose.onNodeWithText("Demo node").performScrollTo().performClick()
        shot("fa-dark-details")
        compose.onNodeWithTag("primary-monitoring").performClick()
        shot("fa-dark-monitoring")
        compose.onNodeWithTag("primary-tools").performClick()
        compose.onNodeWithText(copy.cfScanner).performClick()
        shot("fa-dark-scanner")
        compose.onNodeWithTag("primary-settings").performClick()
        shot("fa-dark-settings")
    }
    @Test @Config(qualifiers = "w1000dp-h900dp-mdpi")
    fun `English dark tablet uses a rail`() {
        open("en", "dark")
        compose.onNodeWithTag("primary-tools").performClick()
        shot("en-dark-tablet-tools")
    }
    @Test @Config(qualifiers = "w320dp-h740dp-mdpi")
    fun `small phone with large text keeps navigation and Help accessible`() {
        RuntimeEnvironment.setFontScale(1.5f)
        try {
            open("fa", "light")
            compose.onNodeWithTag("primary-monitoring").assertIsDisplayed().performClick()
            compose.onNodeWithTag("primary-tools").assertIsDisplayed().performClick()
            compose.onNodeWithTag("page-help").assertIsDisplayed()
            shot("fa-light-large-text-tools")
        } finally { RuntimeEnvironment.setFontScale(1f) }
    }
}
