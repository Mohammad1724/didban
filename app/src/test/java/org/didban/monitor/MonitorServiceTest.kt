package org.didban.monitor

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.android.controller.ServiceController

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class MonitorServiceTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private var controller: ServiceController<MonitorService>? = null

    @Before fun reset() {
        UptimeEngine.stop(); UptimeEngine.liveTargets.value = emptyList()
        context.getSharedPreferences("didban", Context.MODE_PRIVATE).edit().clear().commit()
        MonitoringControl.setRequested(context, false)
        MonitoringControl.lifecycle.requestStop { false }
    }
    @After fun cleanUp() {
        controller?.destroy(); UptimeEngine.stop()
        MonitoringControl.setRequested(context, false)
        MonitoringControl.lifecycle.requestStop { false }
    }
    private fun create(): MonitorService {
        controller = Robolectric.buildService(MonitorService::class.java).create()
        return controller!!.get()
    }
    @Test fun `creating service does not falsely report running`() {
        create()
        assertFalse(MonitorService.isRunning)
        assertFalse(UptimeEngine.monitoring.value)
    }
    @Test fun `explicit service start enters foreground starts engine and destruction clears state`() {
        MonitoringControl.setRequested(context, true)
        val service = create()
        assertEquals(android.app.Service.START_STICKY, service.onStartCommand(Intent().setAction(MonitorService.ACTION_START), 0, 1))
        assertTrue(MonitorService.isRunning); assertTrue(UptimeEngine.monitoring.value)
        val notification = shadowOf(service).lastForegroundNotification
        assertNotNull(notification)
        assertTrue(notification.actions.any { it.title == CommandCopy.forLanguage(Prefs.getLanguage(context)).monitorStop })
        service.onStartCommand(Intent().setAction(MonitorService.ACTION_START), 0, 2)
        assertTrue(UptimeEngine.monitoring.value)
        controller!!.destroy(); controller = null
        assertFalse(MonitorService.isRunning); assertFalse(UptimeEngine.monitoring.value)
    }
    @Test fun `notification stop disables sticky restart without deleting targets`() {
        val target = UptimeTarget(19, "paused fixture", "TCP", "127.0.0.1", isPaused = true, allowPrivateNetwork = true)
        UptimeEngine.upsert(context, target)
        MonitoringControl.setRequested(context, true)
        val service = create(); service.onStartCommand(null, 0, 1)
        service.onStartCommand(Intent().setAction(MonitorService.ACTION_STOP), 0, 2)
        assertFalse(MonitorService.isRunning); assertFalse(UptimeEngine.monitoring.value)
        assertFalse(MonitoringControl.requested(context))
        assertEquals(listOf(19L), UptimeEngine.liveTargets.value.map { it.id })
        assertEquals(android.app.Service.START_NOT_STICKY, service.onStartCommand(null, 0, 3))
        assertFalse(UptimeEngine.monitoring.value)
    }
    @Test fun `stale start cannot override saved user stop`() {
        val service = create()
        assertEquals(android.app.Service.START_NOT_STICKY, service.onStartCommand(Intent().setAction(MonitorService.ACTION_START), 0, 1))
        assertFalse(MonitorService.isRunning); assertFalse(UptimeEngine.monitoring.value)
    }
    @Test fun `disabled notification channel is reported rather than equated with engine failure`() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("didban_uptime", "fixture", NotificationManager.IMPORTANCE_NONE))
        assertFalse(monitoringNotificationsEnabled(context))
        MonitoringControl.setRequested(context, true)
        val service = create(); service.onStartCommand(null, 0, 1)
        assertTrue(MonitorService.isRunning)
        assertFalse(monitoringNotificationsEnabled(context))
    }
}
