package com.langosta.mission.data.repository

import com.langosta.mission.domain.model.AgentMemoryFile
import com.langosta.mission.domain.model.MemoryJournalFile
import com.langosta.mission.util.AppLogger
import com.langosta.mission.util.ConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

class MemoryRepository {

    /** GET /api/memory -> agentMemoryFiles + daily journal */
    suspend fun getMemoryOverview(): Pair<List<AgentMemoryFile>, List<MemoryJournalFile>> =
        withContext(Dispatchers.IO) {
            try {
                val url = java.net.URL("${ConfigManager.getServerUrl()}/api/memory")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", "Bearer ${ConfigManager.getAuthToken()}")
                conn.connectTimeout = 8_000
                conn.readTimeout = 12_000
                if (conn.responseCode !in 200..299) return@withContext Pair(emptyList(), emptyList())

                val root = Json.parseToJsonElement(
                    conn.inputStream.bufferedReader().readText()
                ).jsonObject

                val agents = root["agentMemoryFiles"]?.jsonArray?.map { el ->
                    val o = el.jsonObject
                    AgentMemoryFile(
                        agentId     = o["agentId"]?.jsonPrimitive?.content ?: "",
                        agentName   = o["agentName"]?.jsonPrimitive?.content ?: "",
                        isDefault   = o["isDefault"]?.jsonPrimitive?.boolean ?: false,
                        exists      = o["exists"]?.jsonPrimitive?.boolean ?: false,
                        fileName    = o["fileName"]?.jsonPrimitive?.content ?: "MEMORY.md",
                        content     = o["content"]?.jsonPrimitive?.content ?: "",
                        words       = o["words"]?.jsonPrimitive?.int ?: 0,
                        size        = o["size"]?.jsonPrimitive?.long ?: 0L,
                        mtime       = o["mtime"]?.jsonPrimitive?.contentOrNull,
                        vectorState = o["vectorState"]?.jsonPrimitive?.content ?: "unknown",
                        dirty       = o["dirty"]?.jsonPrimitive?.boolean ?: false,
                        indexedFiles   = o["indexedFiles"]?.jsonPrimitive?.int ?: 0,
                        indexedChunks  = o["indexedChunks"]?.jsonPrimitive?.int ?: 0
                    )
                } ?: emptyList()

                val daily = root["daily"]?.jsonArray?.map { el ->
                    val o = el.jsonObject
                    MemoryJournalFile(
                        name        = o["name"]?.jsonPrimitive?.content ?: "",
                        date        = o["date"]?.jsonPrimitive?.content ?: "",
                        words       = o["words"]?.jsonPrimitive?.int ?: 0,
                        size        = o["size"]?.jsonPrimitive?.long ?: 0L,
                        mtime       = o["mtime"]?.jsonPrimitive?.contentOrNull,
                        vectorState = o["vectorState"]?.jsonPrimitive?.content ?: "unknown"
                    )
                } ?: emptyList()

                Pair(agents, daily)
            } catch (e: Exception) {
                AppLogger.e("MemoryRepository", "getMemoryOverview failed: ${e.message}")
                Pair(emptyList(), emptyList())
            }
        }

    /** POST /api/memory action=index-memory para re-indexar un agente */
    suspend fun reindex(agentId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = buildJsonObject {
                put("action", "index-memory")
                put("agentId", agentId)
                put("force", true)
            }.toString()
            val url = java.net.URL("${ConfigManager.getServerUrl()}/api/memory")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer ${ConfigManager.getAuthToken()}")
            conn.connectTimeout = 10_000
            conn.readTimeout = 60_000
            conn.outputStream.write(body.toByteArray())
            val response = conn.inputStream.bufferedReader().readText()
            val json = Json.parseToJsonElement(response).jsonObject
            if (json["ok"]?.jsonPrimitive?.boolean == true)
                Result.success("Re-indexado correctamente")
            else
                Result.failure(Exception(json["error"]?.jsonPrimitive?.content ?: "Error"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
