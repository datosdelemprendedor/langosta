package com.langosta.mission.data.api

import com.langosta.mission.util.AppLogger
import com.langosta.mission.util.ConfigManager
import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente HTTP directo al gateway OpenClaw.
 * Llama a http://127.0.0.1:8080/... con Authorization: Bearer <token>
 */
class OpenClawHttpClient {

    private fun get(path: String): JsonElement? {
        return try {
            val url = URL("${ConfigManager.getServerUrl()}$path")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer ${ConfigManager.getAuthToken()}")
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 5_000
            conn.readTimeout = 10_000

            if (conn.responseCode !in 200..299) {
                AppLogger.w("OpenClawHttpClient", "HTTP ${conn.responseCode} for $path")
                return null
            }

            val body = BufferedReader(InputStreamReader(conn.inputStream)).readText()
            Json.parseToJsonElement(body)
        } catch (e: Exception) {
            AppLogger.w("OpenClawHttpClient", "Request failed for $path: ${e.message}")
            null
        }
    }

    /** GET /api/status -> { gateway, latencyMs, transport } */
    fun getStatus(): GatewayHttpStatus? {
        val json = get("/api/status")?.jsonObject ?: return null
        return GatewayHttpStatus(
            gateway = json["gateway"]?.jsonPrimitive?.content ?: "offline",
            latencyMs = json["latencyMs"]?.jsonPrimitive?.intOrNull,
            transport = json["transport"]?.jsonPrimitive?.content
        )
    }

    /** GET /api/gateway -> health completa del gateway */
    fun getHealth(): JsonObject? {
        return get("/api/gateway")?.jsonObject
    }

    /** GET /api/sessions -> lista de sesiones */
    fun getSessions(): List<HttpSession> {
        val json = get("/api/sessions") ?: return emptyList()
        val arr = when {
            json is JsonArray -> json
            json is JsonObject -> json["sessions"]?.jsonArray
                ?: json["data"]?.jsonArray
                ?: json["items"]?.jsonArray
                ?: return emptyList()
            else -> return emptyList()
        }
        return arr.mapNotNull { item ->
            val obj = item.jsonObject
            HttpSession(
                id = obj["id"]?.jsonPrimitive?.content
                    ?: obj["key"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                agentId = obj["agentId"]?.jsonPrimitive?.content ?: "main",
                model = obj["model"]?.jsonPrimitive?.content ?: "unknown",
                status = obj["status"]?.jsonPrimitive?.content ?: "active",
                messagesCount = obj["messagesCount"]?.jsonPrimitive?.intOrNull
                    ?: obj["messages"]?.jsonPrimitive?.intOrNull ?: 0,
                tokensIn = obj["tokensIn"]?.jsonPrimitive?.longOrNull ?: 0L,
                tokensOut = obj["tokensOut"]?.jsonPrimitive?.longOrNull ?: 0L,
                createdAt = obj["createdAt"]?.jsonPrimitive?.longOrNull ?: 0L,
                updatedAt = obj["updatedAt"]?.jsonPrimitive?.longOrNull ?: 0L
            )
        }
    }

    /** GET /api/agents -> lista de agentes */
    fun getAgents(): List<HttpAgent> {
        val json = get("/api/agents") ?: return emptyList()
        val arr = when {
            json is JsonArray -> json
            json is JsonObject -> json["agents"]?.jsonArray
                ?: json["data"]?.jsonArray ?: return emptyList()
            else -> return emptyList()
        }
        return arr.mapNotNull { item ->
            val obj = item.jsonObject
            HttpAgent(
                id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                name = obj["name"]?.jsonPrimitive?.content ?: obj["id"]?.jsonPrimitive?.content ?: "unknown",
                model = obj["model"]?.jsonPrimitive?.content ?: "unknown",
                type = obj["type"]?.jsonPrimitive?.content ?: "assistant",
                status = obj["status"]?.jsonPrimitive?.content ?: "idle",
                activeSessions = obj["activeSessions"]?.jsonPrimitive?.intOrNull ?: 0,
                totalSessions = obj["totalSessions"]?.jsonPrimitive?.intOrNull ?: 0,
                tokensIn = obj["tokensIn"]?.jsonPrimitive?.longOrNull ?: 0L,
                tokensOut = obj["tokensOut"]?.jsonPrimitive?.longOrNull ?: 0L
            )
        }
    }

    /** GET /api/cron -> cron jobs */
    fun getCronJobs(): List<HttpCronJob> {
        val json = get("/api/cron") ?: return emptyList()
        val arr = when {
            json is JsonArray -> json
            json is JsonObject -> json["jobs"]?.jsonArray
                ?: json["crons"]?.jsonArray
                ?: json["data"]?.jsonArray ?: return emptyList()
            else -> return emptyList()
        }
        return arr.mapNotNull { item ->
            val obj = item.jsonObject
            HttpCronJob(
                id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                name = obj["name"]?.jsonPrimitive?.content ?: obj["id"]?.jsonPrimitive?.content ?: "unknown",
                schedule = obj["schedule"]?.jsonPrimitive?.content ?: obj["cron"]?.jsonPrimitive?.content ?: "",
                enabled = obj["enabled"]?.jsonPrimitive?.boolean ?: true,
                lastRun = obj["lastRun"]?.jsonPrimitive?.content,
                nextRun = obj["nextRun"]?.jsonPrimitive?.content,
                status = obj["status"]?.jsonPrimitive?.content ?: "scheduled"
            )
        }
    }

    /** GET /api/usage -> resumen de tokens y costo */
    fun getUsage(): HttpUsage? {
        val json = get("/api/usage")?.jsonObject ?: return null
        return HttpUsage(
            tokensIn = json["tokensIn"]?.jsonPrimitive?.longOrNull
                ?: json["inputTokens"]?.jsonPrimitive?.longOrNull ?: 0L,
            tokensOut = json["tokensOut"]?.jsonPrimitive?.longOrNull
                ?: json["outputTokens"]?.jsonPrimitive?.longOrNull ?: 0L,
            totalCost = json["totalCost"]?.jsonPrimitive?.doubleOrNull
                ?: json["cost"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            currency = json["currency"]?.jsonPrimitive?.content ?: "USD",
            period = json["period"]?.jsonPrimitive?.content ?: "today"
        )
    }
}

data class GatewayHttpStatus(
    val gateway: String,
    val latencyMs: Int?,
    val transport: String?
)

data class HttpSession(
    val id: String,
    val agentId: String,
    val model: String,
    val status: String,
    val messagesCount: Int,
    val tokensIn: Long,
    val tokensOut: Long,
    val createdAt: Long,
    val updatedAt: Long
) {
    val isActive: Boolean get() = status in listOf("active", "running", "streaming")
}

data class HttpAgent(
    val id: String,
    val name: String,
    val model: String,
    val type: String,
    val status: String,
    val activeSessions: Int,
    val totalSessions: Int,
    val tokensIn: Long,
    val tokensOut: Long
)

data class HttpCronJob(
    val id: String,
    val name: String,
    val schedule: String,
    val enabled: Boolean,
    val lastRun: String?,
    val nextRun: String?,
    val status: String
)

data class HttpUsage(
    val tokensIn: Long,
    val tokensOut: Long,
    val totalCost: Double,
    val currency: String,
    val period: String
)
