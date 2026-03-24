package com.langosta.mission.util

import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

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
     * Lee ~/.openclaw/openclaw.json via WSL y extrae:
     * - gateway.auth.token -> api_token
     * - gateway.port       -> server_url / ws_url
     *
     * Se lee via WSL porque la app corre en Windows/JVM pero
     * openclaw vive en el filesystem de WSL.
     */
    private fun loadFromOpenClawJson() {
        try {
            val raw = readFileViaWsl("~/.openclaw/openclaw.json")
            if (raw == null) {
                AppLogger.w("ConfigManager", "openclaw.json not found in WSL")
                setDefaults()
                return
            }

            val json = Json.parseToJsonElement(raw).jsonObject

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
                setDefaults()
            }

        } catch (e: Exception) {
            AppLogger.w("ConfigManager", "Could not read openclaw.json: ${e.message}")
            setDefaults()
        }
    }

    private fun setDefaults() {
        set("server_url", "http://127.0.0.1:18789")
        set("ws_url", "127.0.0.1:18789")
    }

    /** Lee un archivo del filesystem de WSL y retorna su contenido como String */
    private fun readFileViaWsl(path: String): String? {
        return try {
            val process = ProcessBuilder(
                "cmd.exe", "/c", "wsl", "-d", "Ubuntu", "-e", "bash", "-lc", "cat $path"
            )
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .start()
            val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
            process.waitFor(5, TimeUnit.SECONDS)
            output.trim().ifBlank { null }
        } catch (e: Exception) {
            AppLogger.w("ConfigManager", "readFileViaWsl failed: ${e.message}")
            null
        }
    }
}
