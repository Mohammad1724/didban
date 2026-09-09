package org.didban.monitor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class CfZone(
    val id: String,
    val name: String,
    val status: String,
    val paused: Boolean
)

data class CfRecord(
    val id: String,
    val zoneId: String,
    val type: String,
    val name: String,
    val content: String,
    val proxiable: Boolean,
    val proxied: Boolean,
    val ttl: Int
)

object CloudflareService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val mediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun listZones(apiToken: String): List<CfZone> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.cloudflare.com/client/v4/zones?per_page=50")
            .header("Authorization", "Bearer ${apiToken.trim()}")
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}: $body")
            val j = JSONObject(body)
            if (!j.optBoolean("success", false)) {
                val errs = j.optJSONArray("errors")
                val msg = if (errs != null && errs.length() > 0) errs.getJSONObject(0).optString("message") else "Failed to list zones"
                throw Exception(msg)
            }
            val arr = j.optJSONArray("result") ?: JSONArray()
            val list = mutableListOf<CfZone>()
            for (i in 0 until arr.length()) {
                val z = arr.getJSONObject(i)
                list.add(
                    CfZone(
                        id = z.optString("id"),
                        name = z.optString("name"),
                        status = z.optString("status"),
                        paused = z.optBoolean("paused")
                    )
                )
            }
            list
        }
    }

    suspend fun listRecords(apiToken: String, zoneId: String): List<CfRecord> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.cloudflare.com/client/v4/zones/$zoneId/dns_records?per_page=100")
            .header("Authorization", "Bearer ${apiToken.trim()}")
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}: $body")
            val j = JSONObject(body)
            if (!j.optBoolean("success", false)) {
                val errs = j.optJSONArray("errors")
                val msg = if (errs != null && errs.length() > 0) errs.getJSONObject(0).optString("message") else "Failed to list records"
                throw Exception(msg)
            }
            val arr = j.optJSONArray("result") ?: JSONArray()
            val list = mutableListOf<CfRecord>()
            for (i in 0 until arr.length()) {
                val r = arr.getJSONObject(i)
                list.add(
                    CfRecord(
                        id = r.optString("id"),
                        zoneId = zoneId,
                        type = r.optString("type"),
                        name = r.optString("name"),
                        content = r.optString("content"),
                        proxiable = r.optBoolean("proxiable", false),
                        proxied = r.optBoolean("proxied", false),
                        ttl = r.optInt("ttl", 1)
                    )
                )
            }
            list
        }
    }

    suspend fun saveRecord(
        apiToken: String,
        zoneId: String,
        recordId: String?,
        type: String,
        name: String,
        content: String,
        proxied: Boolean,
        ttl: Int = 1
    ): Boolean = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("type", type.uppercase())
            put("name", name.trim())
            put("content", content.trim())
            put("ttl", ttl)
            if (type.uppercase() in listOf("A", "AAAA", "CNAME")) {
                put("proxied", proxied)
            }
        }

        val url = if (recordId.isNullOrEmpty()) {
            "https://api.cloudflare.com/client/v4/zones/$zoneId/dns_records"
        } else {
            "https://api.cloudflare.com/client/v4/zones/$zoneId/dns_records/$recordId"
        }

        val reqBuilder = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${apiToken.trim()}")

        val req = if (recordId.isNullOrEmpty()) {
            reqBuilder.post(payload.toString().toRequestBody(mediaType)).build()
        } else {
            reqBuilder.put(payload.toString().toRequestBody(mediaType)).build()
        }

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            val j = JSONObject(body)
            if (!j.optBoolean("success", false)) {
                val errs = j.optJSONArray("errors")
                val msg = if (errs != null && errs.length() > 0) errs.getJSONObject(0).optString("message") else "Failed to save record"
                throw Exception(msg)
            }
            true
        }
    }

    suspend fun deleteRecord(apiToken: String, zoneId: String, recordId: String): Boolean = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.cloudflare.com/client/v4/zones/$zoneId/dns_records/$recordId")
            .header("Authorization", "Bearer ${apiToken.trim()}")
            .delete()
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            val j = JSONObject(body)
            if (!j.optBoolean("success", false)) {
                val errs = j.optJSONArray("errors")
                val msg = if (errs != null && errs.length() > 0) errs.getJSONObject(0).optString("message") else "Failed to delete record"
                throw Exception(msg)
            }
            true
        }
    }
}
