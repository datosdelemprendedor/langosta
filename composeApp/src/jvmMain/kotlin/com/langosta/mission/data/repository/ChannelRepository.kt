package com.langosta.mission.data.repository

import com.langosta.mission.domain.model.CHANNEL_CATALOG
import com.langosta.mission.domain.model.Channel
import com.langosta.mission.util.AppLogger
import com.langosta.mission.util.ConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

class ChannelRepository {

    /**
     * Lee el estado de channels desde GET /api/channels del gateway HTTP.
     * Fallback: lee openclaw.json directamente via ConfigManager para saber
     * qué channels están habilitados aunque el endpoint no exista aún.
     */
    suspend fun getChannels(): List<Channel> = withContext(Dispatchers.IO) {
        try {
            fetchFromHttp()
        } catch (e: Exception) {
            AppLogger.w("ChannelRepository", "HTTP fetch failed, usando catalog: ${e.message}")
            CHANNEL_CATALOG
        }
    }

    private fun fetchFromHttp(): List<Channel> {
        val url = java.net.URL("${ConfigManager.getServerUrl()}/api/channels")
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer ${ConfigManager.getAuthToken()}")
        conn.connectTimeout = 5_000
        conn.readTimeout = 8_000

        if (conn.responseCode !in 200..299) {
            AppLogger.w("ChannelRepository", "HTTP ${conn.responseCode}")
            return CHANNEL_CATALOG
        }

        val body = conn.inputStream.bufferedReader().readText()
        val root = Json.parseToJsonElement(body).jsonObject
        val arr = root["channels"]?.jsonArray ?: return CHANNEL_CATALOG

        return arr.map { el ->
            val o = el.jsonObject
            Channel(
                id          = o["id"]?.jsonPrimitive?.content ?: "",
                label       = o["label"]?.jsonPrimitive?.content ?: "",
                icon        = o["icon"]?.jsonPrimitive?.content ?: "📡",
                enabled     = o["enabled"]?.jsonPrimitive?.boolean ?: false,
                configured  = o["configured"]?.jsonPrimitive?.boolean ?: false,
                connected   = o["connected"]?.jsonPrimitive?.boolean ?: false,
                botUsername = o["botUsername"]?.jsonPrimitive?.contentOrNull,
                error       = o["error"]?.jsonPrimitive?.contentOrNull,
                dmPolicy    = o["dmPolicy"]?.jsonPrimitive?.content ?: "pairing",
                groupPolicy = o["groupPolicy"]?.jsonPrimitive?.content ?: "disabled",
                accounts    = o["accounts"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                hint        = o["hint"]?.jsonPrimitive?.content ?: "",
                setupCommand = o["setupCommand"]?.jsonPrimitive?.content ?: ""
            )
        }
    }

    /**
     * Conecta un channel: POST /api/channels con action=connect.
     * Escribe directamente en openclaw.json via CLI si el endpoint falla.
     */
    suspend fun connectChannel(
        channelId: String,
        token: String,
        dmPolicy: String = "pairing",
        groupPolicy: String = "disabled"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = buildJsonObject {
                put("action", "connect")
                put("channel", channelId)
                put("token", token)
                put("dmPolicy", dmPolicy)
                put("groupPolicy", groupPolicy)
            }.toString()

            val url = java.net.URL("${ConfigManager.getServerUrl()}/api/channels")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer ${ConfigManager.getAuthToken()}")
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.outputStream.write(body.toByteArray())

            val response = conn.inputStream.bufferedReader().readText()
            val json = Json.parseToJsonElement(response).jsonObject
            val ok = json["ok"]?.jsonPrimitive?.boolean == true
            if (ok) Result.success(json["message"]?.jsonPrimitive?.content ?: "Conectado")
            else Result.failure(Exception(json["error"]?.jsonPrimitive?.content ?: "Error desconocido"))
        } catch (e: Exception) {
            AppLogger.e("ChannelRepository", "connectChannel failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun disconnectChannel(channelId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = buildJsonObject {
                put("action", "disconnect")
                put("channel", channelId)
            }.toString()
            postAction(body)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun postAction(body: String): Result<String> {
        val url = java.net.URL("${ConfigManager.getServerUrl()}/api/channels")
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer ${ConfigManager.getAuthToken()}")
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.outputStream.write(body.toByteArray())
        val response = conn.inputStream.bufferedReader().readText()
        val json = Json.parseToJsonElement(response).jsonObject
        val ok = json["ok"]?.jsonPrimitive?.boolean == true
        return if (ok) Result.success(json["message"]?.jsonPrimitive?.content ?: "OK")
        else Result.failure(Exception(json["error"]?.jsonPrimitive?.content ?: "Error"))
    }
}
