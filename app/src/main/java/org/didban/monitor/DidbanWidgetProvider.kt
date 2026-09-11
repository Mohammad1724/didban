package org.didban.monitor

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * Cyber Bento Home Screen Widget for Didban.
 */
class DidbanWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, DidbanWidgetProvider::class.java)
            val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            for (widgetId in allWidgetIds) {
                updateAppWidget(context, appWidgetManager, widgetId)
            }
        }

        private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_didban_dashboard)

            // 1. Live Data Counts
            val servers = Prefs.loadServers(context)
            val tunnels = Prefs.loadTunnels(context)
            val uptime = Prefs.loadUptimeTargets(context)

            views.setTextViewText(R.id.tv_widget_servers, "${servers.size} Node")
            views.setTextViewText(R.id.tv_widget_tunnels, "${tunnels.size} Link")

            val activeUptimeCount = uptime.count { it.lastStatus == 1 }
            val uptimePct = if (uptime.isNotEmpty()) {
                "${((activeUptimeCount.toFloat() / uptime.size) * 100).toInt()}%"
            } else {
                "100%"
            }
            views.setTextViewText(R.id.tv_widget_uptime, uptimePct)

            // 2. Click Intent to open App
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
