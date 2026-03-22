package com.langosta.mission.util

object ConfigManager {

    private val config = mutableMapOf<String, String>()

    fun set(key: String, value: String) {
        config[key] = value
    }

    fun get(key: String, default: String = ""): String =
        config[key] ?: default

    fun getServerUrl(): String =
        get("server_url", "http://localhost:8000")

    fun getWebSocketUrl(): String =
        get("ws_url", "localhost:8000")

    fun isDebugMode(): Boolean =
        get("debug", "true").toBoolean()

    fun loadDefaults() {
        set("server_url", "http://localhost:8000")
        set("ws_url", "localhost:8000")
        set("debug", "true")
        set("app_name", "OpenCLAW Mission Control")
    }
}
