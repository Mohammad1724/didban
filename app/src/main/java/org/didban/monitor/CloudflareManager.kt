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

    // H11: the v4 API caps pages at 50 (zones) / 100 (dns_records) items;
    // list endpoints must walk every page or large zones come back truncated.
    private const val DEFAULT_API_BASE = "https://api.cloudflare.com/client/v4"
    private const val ZONES_PER_PAGE = 50
    private const val RECORDS_PER_PAGE = 100
    private const val MAX_PAGES = 100 // safety valve against a misbehaving API

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val mediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * One API page: fetch + success-check + return the raw JSON envelope.
     * [endpoint] is the full URL (pagination query included).
     *
     * The v4 API returns its JSON error envelope for non-2xx responses too
     * (401/403/429...), so the human-readable message is extracted from
     * the body before falling back to the raw HTTP status.
     */
    private fun fetchJson(apiToken: String, endpoint: String): JSONObject {
        val req = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer ${apiToken.trim()}")
            .build()
        client.newCall(req).execute().use { resp ->
            val body = BoundedResponseReader.readUtf8(resp.body, BoundedResponseReader.STANDARD_BYTES)
            if (!resp.isSuccessful) {
                val msg = try {
                    val errs = JSONObject(body).optJSONArray("errors")
                    if (errs != null && errs.length() > 0) errs.getJSONObject(0).optString("message") else null
                } catch (_: Exception) {
                    null
                }
                // Never expose the raw provider body; it is untrusted and may
                // echo request/account metadata. Keep only Cloudflare's
                // structured message, redacted and bounded.
                throw Exception(
                    msg?.let { SecretRedactor.redact(it, listOf(apiToken)).take(300) }
                        ?: "Cloudflare HTTP ${resp.code}"
                )
            }
            val j = JSONObject(body)
            if (!j.optBoolean("success", false)) {
                val errs = j.optJSONArray("errors")
                val msg = if (errs != null && errs.length() > 0) errs.getJSONObject(0).optString("message") else "Cloudflare API error"
                throw Exception(SecretRedactor.redact(msg, listOf(apiToken)).take(300))
            }
            return j
        }
    }

    /** total_count from `result_info`, or -1 when the API omitted it. */
    private fun totalCountOf(j: JSONObject): Long =
        j.optJSONObject("result_info")?.optLong("total_count", -1L) ?: -1L

    /**
     * All zones of the account (H11: every page, not just the first 50).
     * [apiBase] exists so the JVM tests can point the walker at a local
     * stub; production always uses the default.
     */
    suspend fun listZones(apiToken: String, apiBase: String = DEFAULT_API_BASE): List<CfZone> =
        withContext(Dispatchers.IO) {
            val list = mutableListOf<CfZone>()
            var page = 1
            while (page <= MAX_PAGES) {
                val j = fetchJson(apiToken, "$apiBase/zones?${CfPagination.pageQuery(page, ZONES_PER_PAGE)}")
                val arr = j.optJSONArray("result") ?: JSONArray()
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
                val next = CfPagination.nextPage(page, ZONES_PER_PAGE, arr.length(), totalCountOf(j)) ?: break
                page = next
            }
            list
        }

    /**
     * All DNS records of a zone (H11: every page, not just the first 100).
     * [apiBase] exists so the JVM tests can point the walker at a local
     * stub; production always uses the default.
     */
    suspend fun listRecords(apiToken: String, zoneId: String, apiBase: String = DEFAULT_API_BASE): List<CfRecord> =
        withContext(Dispatchers.IO) {
            val list = mutableListOf<CfRecord>()
            var page = 1
            while (page <= MAX_PAGES) {
                val j = fetchJson(
                    apiToken,
                    "$apiBase/zones/$zoneId/dns_records?${CfPagination.pageQuery(page, RECORDS_PER_PAGE)}"
                )
                val arr = j.optJSONArray("result") ?: JSONArray()
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
                val next = CfPagination.nextPage(page, RECORDS_PER_PAGE, arr.length(), totalCountOf(j)) ?: break
                page = next
            }
            list
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
            val body = BoundedResponseReader.readUtf8(resp.body, BoundedResponseReader.STANDARD_BYTES)
            val j = JSONObject(body)
            if (!j.optBoolean("success", false)) {
                val errs = j.optJSONArray("errors")
                val msg = if (errs != null && errs.length() > 0) errs.getJSONObject(0).optString("message") else "Failed to save record"
                throw Exception(SecretRedactor.redact(msg, listOf(apiToken)).take(300))
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
            val body = BoundedResponseReader.readUtf8(resp.body, BoundedResponseReader.STANDARD_BYTES)
            val j = JSONObject(body)
            if (!j.optBoolean("success", false)) {
                val errs = j.optJSONArray("errors")
                val msg = if (errs != null && errs.length() > 0) errs.getJSONObject(0).optString("message") else "Failed to delete record"
                throw Exception(SecretRedactor.redact(msg, listOf(apiToken)).take(300))
            }
            true
        }
    }
}
