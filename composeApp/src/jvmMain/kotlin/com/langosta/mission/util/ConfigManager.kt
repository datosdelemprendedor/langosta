package com.langosta.mission.util

import kotlinx.serialization.json.*
import java.io.File

object ConfigManager {

    private val config = mutableMapOf<String, String>()

    fun set(key: String, value: String) { config[key] = value }

    fun get(key: String, default: String = ""): String = config[key] ?: default

    fun getServerUrl(): String = get("server_url", "http://127.0.0.1:18789")

    fun getWebSocketUrl(): String = get("ws_url", "127.0.0.1:18789")

    fun getAuthToken(): String = get("api_token", "")

    fun isDebugMode(): Boolean = get("debug", "true").toBoolean()

    fun loadDefaults() {
        loadFromOpenClawJson()
        set("debug", "true")
        set("app_name", "OpenCLAW Mission Control")
    }

    /**
     * Lee ~/.openclaw/openclaw.json y extrae:
     * - gateway.auth.token -> api_token
     * - gateway.port       -> server_url / ws_url
     *
     * Patron basado en paths.ts de robsannaa/openclaw-mission-control
     */
    private fun loadFromOpenClawJson() {
        try {
            val home = System.getProperty("user.home")
            val configFile = File("$home/.openclaw/openclaw.json")
            if (!configFile.exists()) {
                AppLogger.w("ConfigManager", "openclaw.json not found at ${configFile.absolutePath}")
                set("server_url", "http://127.0.0.1:18789")
                set("ws_url", "127.0.0.1:18789")
                return
            }

            val json = Json.parseToJsonElement(configFile.readText()).jsonObject

            // Token: gateway.auth.token
            val token = json["gateway"]?.jsonObject
                ?.get("auth")?.jsonObject
                ?.get("token")?.jsonPrimitive?.content
            if (!token.isNullOrEmpty()) {
                set("api_token", token)
                AppLogger.i("ConfigManager", "Token loaded from openclaw.json")
            }

            // Puerto: gateway.port
            val port = json["gateway"]?.jsonObject
                ?.get("port")?.jsonPrimitive?.intOrNull
            if (port != null) {
                set("server_url", "http://127.0.0.1:$port")
                set("ws_url", "127.0.0.1:$port")
                AppLogger.i("ConfigManager", "Gateway URL -> http://127.0.0.1:$port")
            } else {
                set("server_url", "http://127.0.0.1:18789")
                set("ws_url", "127.0.0.1:18789")
            }

        } catch (e: Exception) {
            AppLogger.w("ConfigManager", "Could not read openclaw.json: ${e.message}")
            set("server_url", "http://127.0.0.1:18789")
            set("ws_url", "127.0.0.1:18789")
        }
    }
}
