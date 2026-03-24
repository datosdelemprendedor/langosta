package com.langosta.mission.data.repository

import com.langosta.mission.domain.model.CronJob
import com.langosta.mission.util.AppLogger
import com.langosta.mission.util.ConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

class CronRepository {

    suspend fun getCronJobs(): List<CronJob> = withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL("${ConfigManager.getServerUrl()}/api/cron")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer ${ConfigManager.getAuthToken()}")
            conn.connectTimeout = 5_000
            conn.readTimeout = 8_000
            if (conn.responseCode !in 200..299) return@withContext emptyList()

            val body = conn.inputStream.bufferedReader().readText()
            val root = Json.parseToJsonElement(body).jsonObject
            val arr = root["jobs"]?.jsonArray ?: return@withContext emptyList()

            arr.map { el ->
                val o = el.jsonObject
                val schedule = o["schedule"]?.jsonObject
                val payload  = o["payload"]?.jsonObject
                val delivery = o["delivery"]?.jsonObject
                val state    = o["state"]?.jsonObject

                val expr = schedule?.get("expr")?.jsonPrimitive?.content
                    ?: schedule?.get("everyMs")?.let { ms ->
                        val v = ms.jsonPrimitive.long
                        when {
                            v >= 86_400_000L -> "cada ${v / 86_400_000}d"
                            v >= 3_600_000L  -> "cada ${v / 3_600_000}h"
                            v >= 60_000L     -> "cada ${v / 60_000}m"
                            else             -> "cada ${v / 1000}s"
                        }
                    } ?: ""

                CronJob(
                    id                = o["id"]?.jsonPrimitive?.content ?: "",
                    name              = o["name"]?.jsonPrimitive?.content ?: "",
                    enabled           = o["enabled"]?.jsonPrimitive?.boolean ?: true,
                    description       = o["description"]?.jsonPrimitive?.content ?: "",
                    agentId           = o["agentId"]?.jsonPrimitive?.contentOrNull,
                    scheduleKind      = schedule?.get("kind")?.jsonPrimitive?.content ?: "every",
                    scheduleExpr      = expr,
                    payloadMessage    = payload?.get("message")?.jsonPrimitive?.content
                                     ?: payload?.get("text")?.jsonPrimitive?.content ?: "",
                    model             = payload?.get("model")?.jsonPrimitive?.contentOrNull,
                    deliveryMode      = delivery?.get("mode")?.jsonPrimitive?.content ?: "none",
                    deliveryChannel   = delivery?.get("channel")?.jsonPrimitive?.contentOrNull,
                    deliveryTo        = delivery?.get("to")?.jsonPrimitive?.contentOrNull,
                    nextRunAtMs       = state?.get("nextRunAtMs")?.jsonPrimitive?.longOrNull,
                    lastRunAtMs       = state?.get("lastRunAtMs")?.jsonPrimitive?.longOrNull,
                    lastStatus        = state?.get("lastRunStatus")?.jsonPrimitive?.contentOrNull
                                     ?: state?.get("lastStatus")?.jsonPrimitive?.contentOrNull,
                    lastError         = state?.get("lastError")?.jsonPrimitive?.contentOrNull,
                    consecutiveErrors = state?.get("consecutiveErrors")?.jsonPrimitive?.int ?: 0
                )
            }
        } catch (e: Exception) {
            AppLogger.e("CronRepository", "getCronJobs failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun doAction(action: String, id: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = buildJsonObject {
                put("action", action)
                put("id", id)
            }.toString()
            val url = java.net.URL("${ConfigManager.getServerUrl()}/api/cron")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer ${ConfigManager.getAuthToken()}")
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.outputStream.write(body.toByteArray())
            val response = conn.inputStream.bufferedReader().readText()
            val json = Json.parseToJsonElement(response).jsonObject
            if (json["ok"]?.jsonPrimitive?.boolean == true)
                Result.success(json["action"]?.jsonPrimitive?.content ?: "ok")
            else
                Result.failure(Exception(json["error"]?.jsonPrimitive?.content ?: "Error"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
