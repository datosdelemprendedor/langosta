package com.langosta.mission.util

import java.io.File

object ConfigManager {

    private val config = mutableMapOf<String, String>()

    fun set(key: String, value: String) {
        config[key] = value
    }

    fun get(key: String, default: String = ""): String =
        config[key] ?: default

    fun getServerUrl(): String =
        get("server_url", "http://127.0.0.1:18789")

    fun getWebSocketUrl(): String =
        get("ws_url", "127.0.0.1:18789")

    fun getAuthToken(): String =
        get("api_token", "")

    fun isDebugMode(): Boolean =
        get("debug", "true").toBoolean()

    fun loadDefaults() {
        loadEnvFile()
        set("server_url", "http://${get("OPENCLAW_HOST", "127.0.0.1")}:${get("OPENCLAW_PORT", "18789")}")
        set("ws_url", "${get("OPENCLAW_HOST", "127.0.0.1")}:${get("OPENCLAW_PORT", "18789")}")
        set("debug", "true")
        set("app_name", "OpenCLAW Mission Control")
    }

    private fun loadEnvFile() {
        val envFile = File(".env")
        if (!envFile.exists()) return
        envFile.forEachLine { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachLine
            val (key, value) = trimmed.split("=", limit = 2).let {
                if (it.size == 2) it[0].trim() to it[1].trim() else return@forEachLine
            }
            when (key) {
                "OPENCLAW_TOKEN" -> set("api_token", value)
                "OPENCLAW_HOST"  -> set("OPENCLAW_HOST", value)
                "OPENCLAW_PORT"  -> set("OPENCLAW_PORT", value)
            }
        }
    }
}
