package com.langosta.mission.util

import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.File
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
     * Lee ~/.openclaw/openclaw.json.
     * Estrategia:
     *   1. Acceso directo al filesystem (Linux/Debian o cualquier POSIX)
     *   2. Fallback: via WSL (cuando la app corre en Windows/JVM)
     */
    private fun loadFromOpenClawJson() {
        val raw = readFileDirect() ?: readFileViaWsl("~/.openclaw/openclaw.json")
        if (raw == null) {
            AppLogger.w("ConfigManager", "openclaw.json no encontrado (directo ni WSL)")
            setDefaults()
            return
        }
        parseOpenClawJson(raw)
    }

    private fun parseOpenClawJson(raw: String) {
        try {
            val json = Json.parseToJsonElement(raw).jsonObject

            val token = json["gateway"]?.jsonObject
                ?.get("auth")?.jsonObject
                ?.get("token")?.jsonPrimitive?.content
            if (!token.isNullOrEmpty()) {
                set("api_token", token)
                AppLogger.i("ConfigManager", "Token cargado desde openclaw.json")
            }

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
            AppLogger.w("ConfigManager", "Error parseando openclaw.json: ${e.message}")
            setDefaults()
        }
    }

    /** Lectura directa (Linux/Debian/macOS) */
    private fun readFileDirect(): String? {
        return try {
            val home = System.getProperty("user.home") ?: return null
            val file = File(home, ".openclaw/openclaw.json")
            if (file.exists() && file.canRead()) {
                AppLogger.i("ConfigManager", "Leyendo openclaw.json directamente: ${file.absolutePath}")
                file.readText().trim().ifBlank { null }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /** Lectura via WSL (Windows/JVM con Ubuntu WSL) */
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
            AppLogger.w("ConfigManager", "readFileViaWsl fallido: ${e.message}")
            null
        }
    }

    private fun setDefaults() {
        set("server_url", "http://127.0.0.1:18789")
        set("ws_url", "127.0.0.1:18789")
    }
}
