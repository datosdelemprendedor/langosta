package com.langosta.mission.data.repository

import com.langosta.mission.domain.model.Session
import com.langosta.mission.util.AppLogger
import com.langosta.mission.util.ConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

class SessionRepository {

    suspend fun getSessions(): List<Session> = withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL("${ConfigManager.getServerUrl()}/api/sessions")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer ${ConfigManager.getAuthToken()}")
            conn.connectTimeout = 5_000
            conn.readTimeout = 8_000

            if (conn.responseCode !in 200..299) return@withContext emptyList()

            val body = conn.inputStream.bufferedReader().readText()
            val root = Json.parseToJsonElement(body).jsonObject
            val arr = root["sessions"]?.jsonArray ?: return@withContext emptyList()

            arr.map { el ->
                val o = el.jsonObject
                Session(
                    key           = o["key"]?.jsonPrimitive?.content ?: "",
                    kind          = o["kind"]?.jsonPrimitive?.content ?: "",
                    sessionId     = o["sessionId"]?.jsonPrimitive?.content ?: "",
                    model         = o["model"]?.jsonPrimitive?.content ?: "unknown",
                    updatedAt     = o["updatedAt"]?.jsonPrimitive?.long ?: 0L,
                    ageMs         = o["ageMs"]?.jsonPrimitive?.long ?: 0L,
                    inputTokens   = o["inputTokens"]?.jsonPrimitive?.long ?: 0L,
                    outputTokens  = o["outputTokens"]?.jsonPrimitive?.long ?: 0L,
                    totalTokens   = o["totalTokens"]?.jsonPrimitive?.long ?: 0L,
                    contextTokens = o["contextTokens"]?.jsonPrimitive?.long ?: 0L
                )
            }.sortedByDescending { it.updatedAt }
        } catch (e: Exception) {
            AppLogger.e("SessionRepository", "getSessions failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun deleteSession(key: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL("${ConfigManager.getServerUrl()}/api/sessions?key=${java.net.URLEncoder.encode(key, "UTF-8")}")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "DELETE"
            conn.setRequestProperty("Authorization", "Bearer ${ConfigManager.getAuthToken()}")
            conn.connectTimeout = 8_000
            conn.readTimeout = 10_000
            if (conn.responseCode in 200..299) Result.success(Unit)
            else Result.failure(Exception("HTTP ${conn.responseCode}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
