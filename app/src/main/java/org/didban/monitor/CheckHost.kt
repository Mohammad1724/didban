package org.didban.monitor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class CheckHostResultKind {
    PENDING,
    NO_DATA,
    TIMEOUT,
    PING_SUMMARY,
    HTTP_SUMMARY,
    RAW,
    FAILED,
    ERROR,
    OPEN,
    NO_RECORDS,
    OK,
    INVALID_RESPONSE
}

data class CheckHostResult(
    val kind: CheckHostResultKind,
    val first: String = "",
    val second: String = "",
    val milliseconds: Long = 0
) {
    fun english(): String = when (kind) {
        CheckHostResultKind.PENDING -> "…"
        CheckHostResultKind.NO_DATA -> "no data"
        CheckHostResultKind.TIMEOUT -> "$first/$second · timeout"
        CheckHostResultKind.PING_SUMMARY -> "$first/$second · ${milliseconds}ms"
        CheckHostResultKind.HTTP_SUMMARY -> "$first · ${milliseconds}ms"
        CheckHostResultKind.RAW -> first
        CheckHostResultKind.FAILED -> "failed"
        CheckHostResultKind.ERROR -> "error: $first"
        CheckHostResultKind.OPEN -> "open · ${milliseconds}ms"
        CheckHostResultKind.NO_RECORDS -> "no records"
        CheckHostResultKind.OK -> "OK"
        CheckHostResultKind.INVALID_RESPONSE -> "invalid response"
    }
}

data class CheckHostNode(
    val nodeKey: String,
    val countryCode: String,
    val country: String,
    val city: String,
    val flag: String,
    var resultText: String = "…",
    var result: CheckHostResult = CheckHostResult(CheckHostResultKind.PENDING),
    var state: Int = 0 // 0 = pending, 1 = ok, 2 = fail
) {
    val location: String
        get() = listOf(city, country).filter { it.isNotBlank() }.joinToString(", ")
}

object CheckHostService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun flagForCountry(cc: String): String {
        val clean = cc.trim().uppercase(Locale.US)
        if (clean.length != 2) return "🌐"
        val c1 = clean[0]
        val c2 = clean[1]
        if (c1 !in 'A'..'Z' || c2 !in 'A'..'Z') return "🌐"
        val firstChar = Character.codePointAt(clean, 0) - 0x41 + 0x1F1E6
        val secondChar = Character.codePointAt(clean, 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
    }

    suspend fun startCheck(
        target: String,
        type: String = "ping",
        maxNodes: Int = 20
    ): Pair<String, List<CheckHostNode>> = withContext(Dispatchers.IO) {
        val cleanHost = target.trim()
        if (cleanHost.isEmpty()) throw IllegalArgumentException("Host cannot be empty")

        val encoded = URLEncoder.encode(cleanHost, "UTF-8")
        val cleanType = type.lowercase(Locale.US)
        val url = "https://check-host.net/check-$cleanType?host=$encoded&max_nodes=$maxNodes"

        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()

        client.newCall(req).execute().use { resp ->
            val body = BoundedResponseReader.readUtf8(resp.body, BoundedResponseReader.STANDARD_BYTES)
            if (!resp.isSuccessful) throw Exception("Check-Host HTTP ${resp.code}")

            val json = JSONObject(body)
            if (json.optInt("ok", 0) != 1) {
                val err = json.optString("error", "Failed to initialize check-host request")
                throw Exception(err)
            }

            val reqId = json.optString("request_id")
            val nodesObj = json.optJSONObject("nodes") ?: JSONObject()
            val nodesList = mutableListOf<CheckHostNode>()

            val keys = nodesObj.keys()
            while (keys.hasNext()) {
                val nodeKey = keys.next()
                val nodeArr = nodesObj.optJSONArray(nodeKey) ?: JSONArray()
                var cc = ""
                var country = ""
                var city = ""

                if (nodeArr.length() > 0) cc = nodeArr.optString(0)
                if (nodeArr.length() > 1) country = nodeArr.optString(1)
                if (nodeArr.length() > 2) city = nodeArr.optString(2)

                if (country.contains(",")) {
                    val parts = country.split(",")
                    country = parts[0].trim()
                    if (city.isEmpty() && parts.size > 1) {
                        city = parts[1].trim()
                    }
                }

                nodesList.add(
                    CheckHostNode(
                        nodeKey = nodeKey,
                        countryCode = cc,
                        country = country,
                        city = city,
                        flag = flagForCountry(cc)
                    )
                )
            }

            Pair(reqId, nodesList)
        }
    }

    suspend fun pollResults(
        reqId: String,
        type: String,
        nodes: List<CheckHostNode>
    ): Boolean = withContext(Dispatchers.IO) {
        val url = "https://check-host.net/check-result/$reqId"
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()

        client.newCall(req).execute().use { resp ->
            val body = BoundedResponseReader.readUtf8(resp.body, BoundedResponseReader.STANDARD_BYTES)
            if (!resp.isSuccessful) return@withContext false

            val json = JSONObject(body)
            var allDone = true

            for (node in nodes) {
                if (node.state != 0) continue // already determined

                if (!json.has(node.nodeKey) || json.isNull(node.nodeKey)) {
                    allDone = false
                    continue
                }

                val resArr = json.optJSONArray(node.nodeKey)
                if (resArr == null || resArr.length() == 0 || resArr.isNull(0)) {
                    allDone = false
                    continue
                }

                val (result, state) = parseNodeResult(type, resArr)
                node.result = result
                node.resultText = result.english()
                node.state = state
            }

            allDone
        }
    }

    private fun parseNodeResult(type: String, resArr: JSONArray): Pair<CheckHostResult, Int> {
        try {
            when (type.lowercase(Locale.US)) {
                "ping" -> {
                    val first = resArr.optJSONArray(0)
                        ?: return CheckHostResult(CheckHostResultKind.NO_DATA) to 2
                    var total = 0
                    var ok = 0
                    var sum = 0.0
                    for (i in 0 until first.length()) {
                        val att = first.optJSONArray(i) ?: continue
                        total++
                        if (att.optString(0) == "OK") {
                            ok++
                            sum += att.optDouble(1, 0.0)
                        }
                    }
                    if (ok == 0) {
                        return CheckHostResult(
                            CheckHostResultKind.TIMEOUT,
                            first = "0",
                            second = total.toString()
                        ) to 2
                    }
                    val avgMs = (sum / ok * 1000).toLong()
                    return CheckHostResult(
                        CheckHostResultKind.PING_SUMMARY,
                        first = ok.toString(),
                        second = total.toString(),
                        milliseconds = avgMs
                    ) to 1
                }

                "http" -> {
                    val first = resArr.optJSONArray(0)
                        ?: return CheckHostResult(CheckHostResultKind.NO_DATA) to 2
                    val success = first.optInt(0, 0) == 1
                    val timeSec = first.optDouble(1, 0.0)
                    val timeMs = (timeSec * 1000).toLong()
                    val code = first.optString(3, "")
                    val status = first.optString(2, "")

                    return if (success) {
                        val head = if (code.isNotEmpty()) code else "OK"
                        CheckHostResult(CheckHostResultKind.HTTP_SUMMARY, first = head, milliseconds = timeMs) to 1
                    } else {
                        if (status.isNotEmpty()) {
                            CheckHostResult(CheckHostResultKind.RAW, first = status) to 2
                        } else if (code.isNotEmpty()) {
                            CheckHostResult(CheckHostResultKind.RAW, first = code) to 2
                        } else {
                            CheckHostResult(CheckHostResultKind.FAILED) to 2
                        }
                    }
                }

                "tcp", "udp" -> {
                    val first = resArr.optJSONObject(0)
                        ?: return CheckHostResult(CheckHostResultKind.NO_DATA) to 2
                    if (first.has("error")) {
                        return CheckHostResult(CheckHostResultKind.ERROR, first = first.optString("error")) to 2
                    }
                    if (first.has("time")) {
                        val timeMs = (first.optDouble("time", 0.0) * 1000).toLong()
                        return CheckHostResult(CheckHostResultKind.OPEN, milliseconds = timeMs) to 1
                    }
                    val status = first.optString("status", "")
                    if (status.isNotBlank()) return CheckHostResult(CheckHostResultKind.RAW, first = status) to 1
                    return CheckHostResult(CheckHostResultKind.FAILED) to 2
                }

                "dns" -> {
                    val first = resArr.optJSONObject(0)
                        ?: return CheckHostResult(CheckHostResultKind.NO_DATA) to 2
                    val aList = mutableListOf<String>()
                    val aArr = first.optJSONArray("A")
                    if (aArr != null) {
                        for (i in 0 until aArr.length()) aList.add(aArr.optString(i))
                    }
                    if (aList.isEmpty()) return CheckHostResult(CheckHostResultKind.NO_RECORDS) to 2
                    return CheckHostResult(CheckHostResultKind.RAW, first = aList.joinToString(", ")) to 1
                }

                else -> return CheckHostResult(CheckHostResultKind.OK) to 1
            }
        } catch (_: Exception) {
            return CheckHostResult(CheckHostResultKind.INVALID_RESPONSE) to 2
        }
    }
}
