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

    // Device identity cargada desde paired.json
    fun getPairedDeviceId(): String = get("device_id", "")
    fun getPairedPrivateKey(): String = get("device_private_key", "")
    fun getPairedPublicKey(): String = get("device_public_key", "")
    fun hasPairedDevice(): Boolean = getPairedDeviceId().isNotEmpty() && getPairedPrivateKey().isNotEmpty()

    fun loadDefaults() {
        loadFromOpenClawJson()
        loadFromPairedDevices()
        set("debug", "true")
        set("app_name", "OpenCLAW Mission Control")
    }

    private fun loadFromOpenClawJson() {
        val raw = readFileDirect("~/.openclaw/openclaw.json")
            ?: readFileViaWsl("~/.openclaw/openclaw.json")
        if (raw == null) {
            AppLogger.w("ConfigManager", "openclaw.json no encontrado")
            setDefaults()
            return
        }
        parseOpenClawJson(raw)
    }

    /**
     * Lee ~/.openclaw/devices/paired.json y extrae el primer device pareado.
     * Estructura esperada: array de objetos con { id, privateKey, publicKey, ... }
     */
    private fun loadFromPairedDevices() {
        val raw = readFileDirect("~/.openclaw/devices/paired.json")
            ?: readFileViaWsl("~/.openclaw/devices/paired.json")
        if (raw == null) {
            AppLogger.w("ConfigManager", "paired.json no encontrado")
            return
        }
        try {
            val json = Json.parseToJsonElement(raw)
            // El archivo puede ser un array o un objeto con campo "devices"
            val devicesArray: JsonArray? = when {
                json is JsonArray -> json
                json is JsonObject -> json["devices"]?.jsonArray
                    ?: json["paired"]?.jsonArray
                else -> null
            }

            val first = devicesArray?.firstOrNull()?.jsonObject
                ?: (json as? JsonObject)  // si es un objeto unico

            if (first == null) {
                AppLogger.w("ConfigManager", "paired.json: no se encontro ningun device")
                return
            }

            val deviceId = first["id"]?.jsonPrimitive?.content
                ?: first["deviceId"]?.jsonPrimitive?.content
            val privateKey = first["privateKey"]?.jsonPrimitive?.content
                ?: first["private_key"]?.jsonPrimitive?.content
                ?: first["privKey"]?.jsonPrimitive?.content
            val publicKey = first["publicKey"]?.jsonPrimitive?.content
                ?: first["public_key"]?.jsonPrimitive?.content
                ?: first["pubKey"]?.jsonPrimitive?.content

            if (!deviceId.isNullOrEmpty()) set("device_id", deviceId)
            if (!privateKey.isNullOrEmpty()) set("device_private_key", privateKey)
            if (!publicKey.isNullOrEmpty()) set("device_public_key", publicKey)

            AppLogger.i("ConfigManager", "Device pareado cargado: id=$deviceId hasPrivKey=${!privateKey.isNullOrEmpty()}")
        } catch (e: Exception) {
            AppLogger.w("ConfigManager", "Error parseando paired.json: ${e.message}")
        }
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

    private fun readFileDirect(path: String): String? {
        return try {
            val expanded = path.replaceFirst("~", System.getProperty("user.home") ?: return null)
            val file = File(expanded)
            if (file.exists() && file.canRead()) file.readText().trim().ifBlank { null }
            else null
        } catch (e: Exception) { null }
    }

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
