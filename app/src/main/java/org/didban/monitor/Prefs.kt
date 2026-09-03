package org.didban.monitor

import android.content.Context
import org.json.JSONArray

/** Tiny persistence layer on top of SharedPreferences. */
object Prefs {
    private const val FILE = "didban"

    fun loadServers(ctx: Context): MutableList<ServerConfig> {
        val sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val raw = sp.getString("servers", null) ?: return mutableListOf()
        val list = mutableListOf<ServerConfig>()
        return try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                list.add(ServerConfig.fromJson(arr.getJSONObject(i)))
            }
            list
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun saveServers(ctx: Context, servers: List<ServerConfig>) {
        val arr = JSONArray()
        servers.forEach { arr.put(it.toJson()) }
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString("servers", arr.toString()).apply()
    }

    fun getLanguage(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("lang", "fa") ?: "fa"

    fun setLanguage(ctx: Context, lang: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString("lang", lang).apply()
    }

    fun getPollIntervalMs(ctx: Context): Long {
        val sec = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString("poll_sec", "30")?.toLongOrNull() ?: 30L
        return sec * 1000L
    }

    fun setPollIntervalSec(ctx: Context, seconds: Long) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString("poll_sec", seconds.toString()).apply()
    }
}
