package com.langosta.mission.data.api

import com.langosta.mission.domain.model.SessionInfo
import com.langosta.mission.util.AppLogger
import com.langosta.mission.util.ConfigManager
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.util.UUID
import kotlin.text.toByteArray

class OpenClawRpcClient(private val baseUrl: String) {

    private val client = HttpClient {
        install(WebSockets)
    }

    private val _sessions = MutableSharedFlow<List<SessionInfo>>()
    val sessions = _sessions.asSharedFlow()

    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    private var deviceId: String = "langosta-probe-${UUID.randomUUID().toString().take(8)}"
    private var wsDisabled: Boolean = false
    private var requestId: Int = 0
    
    private val devicePublicKey: String = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAlivFI8qB4D0y2jy0CfEqFyy46R0o7S8TKpsx5xbHKoU1VWg6QkQm+ntyIv1p4kE1sPEQO73+HY8+Bzs75XwRTHLokBm9LCqJ3xrYSO9hT2j2r+5w4UQx1xpJ2Mc8pmB1xS2i3hVdP8bQv3N1w4TQT1S5k6fqQ6FQ2xwptF1Jc3FxJ4qM3a4V2U+89lF3a6C0bI+YDpGiFCYPr+9hAQ=="

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    private val _connectionState = MutableSharedFlow<ConnectionState>()
    val connectionState = _connectionState.asSharedFlow()

    private fun generateSignature(nonce: String, timestamp: Long): String {
        val payload = "v3:$deviceId:$nonce:$timestamp"
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(payload.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun nextId(): String = "${++requestId}"

    suspend fun connect() {
        if (wsDisabled) {
            AppLogger.w("OpenClawRpc", "WebSocket disabled")
            _connectionState.emit(ConnectionState.Disconnected)
            return
        }

        val token = ConfigManager.getAuthToken()
        val host = baseUrl.substringBefore(":")
        val port = baseUrl.substringAfter(":").toIntOrNull() ?: 18789

        AppLogger.i("OpenClawRpc", "Connecting to $host:$port")

        try {
            _connectionState.emit(ConnectionState.Connecting)
            client.webSocket(host = host, port = port, path = "") {
                AppLogger.i("OpenClawRpc", "Waiting for challenge...")

                val challengeFrame = incoming.receive()
                if (challengeFrame is Frame.Text) {
                    val challengeJson = Json.parseToJsonElement(challengeFrame.readText()).jsonObject
                    val eventType = challengeJson["event"]?.jsonPrimitive?.content

                    if (eventType == "connect.challenge") {
                        val payload = challengeJson["payload"]?.jsonObject
                        val nonce = payload?.get("nonce")?.jsonPrimitive?.content ?: ""
                        val timestamp = payload?.get("ts")?.jsonPrimitive?.content?.toLongOrNull() ?: System.currentTimeMillis()

                        val signature = generateSignature(nonce, timestamp)

                        val connectMessage = buildJsonObject {
                            put("type", "req")
                            put("id", nextId())
                            put("method", "connect")
                            putJsonObject("params") {
                                put("minProtocol", 3)
                                put("maxProtocol", 3)
                                putJsonObject("client") {
                                    put("id", "openclaw-probe")
                                    put("platform", "jvm")
                                    put("mode", "probe")
                                    put("version", "1.0.0")
                                }
                                put("role", "operator")
                                putJsonArray("scopes") {
                                    add("operator.read")
                                    add("operator.write")
                                }
                                putJsonArray("caps") { }
                                putJsonArray("commands") { }
                                putJsonObject("permissions") { }
                                putJsonObject("auth") {
                                    put("token", token)
                                }
                                put("locale", "en-US")
                                put("userAgent", "langosta-mission-control/1.0.0")
                                putJsonObject("device") {
                                    put("id", deviceId)
                                    put("publicKey", devicePublicKey)
                                    put("signature", signature)
                                    put("signedAt", timestamp)
                                    put("nonce", nonce)
                                }
                            }
                        }.toString()

                        AppLogger.i("OpenClawRpc", "Sending connect...")
                        send(Frame.Text(connectMessage))
                    }
                }

                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        AppLogger.i("OpenClawRpc", "Received: $text")

                        val json = Json.parseToJsonElement(text).jsonObject
                        val type = json["type"]?.jsonPrimitive?.content

                        when (type) {
                            "res" -> {
                                val ok = json["ok"]?.jsonPrimitive?.boolean == true
                                if (ok) {
                                    _connectionState.emit(ConnectionState.Connected)
                                    AppLogger.i("OpenClawRpc", "Connected!")
                                } else {
                                    val error = json["error"]?.jsonObject
                                    val message = error?.get("message")?.jsonPrimitive?.content ?: "Unknown error"
                                    val details = error?.get("details")?.jsonObject
                                    val detailCode = details?.get("code")?.jsonPrimitive?.content

                                    if (detailCode == "DEVICE_AUTH_DEVICE_ID_MISMATCH") {
                                        AppLogger.w("OpenClawRpc", "Device auth failed - disabling WS")
                                        wsDisabled = true
                                        _connectionState.emit(ConnectionState.Disconnected)
                                        return@webSocket
                                    }

                                    _connectionState.emit(ConnectionState.Error(message))
                                    return@webSocket
                                }
                            }
                            "event" -> {
                                _events.emit(text)
                                if (text.contains("session") || text.contains("agent")) {
                                    AppLogger.i("OpenClawRpc", "Event: $text")
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.w("OpenClawRpc", "Connection error: ${e.message}")
            wsDisabled = true
            _connectionState.emit(ConnectionState.Disconnected)
        }
    }

    suspend fun listSessions(): List<SessionInfo> {
        if (wsDisabled) return emptyList()

        val token = ConfigManager.getAuthToken()
        val host = baseUrl.substringBefore(":")
        val port = baseUrl.substringAfter(":").toIntOrNull() ?: 18789

        try {
            client.webSocket(host = host, port = port, path = "") {
                val challengeFrame = incoming.receive()
                if (challengeFrame is Frame.Text) {
                    val challengeJson = Json.parseToJsonElement(challengeFrame.readText()).jsonObject
                    val eventType = challengeJson["event"]?.jsonPrimitive?.content

                    if (eventType == "connect.challenge") {
                        val payload = challengeJson["payload"]?.jsonObject
                        val nonce = payload?.get("nonce")?.jsonPrimitive?.content ?: ""
                        val timestamp = payload?.get("ts")?.jsonPrimitive?.content?.toLongOrNull() ?: System.currentTimeMillis()
                        val signature = generateSignature(nonce, timestamp)

                        val connectMessage = buildJsonObject {
                            put("type", "req")
                            put("id", nextId())
                            put("method", "connect")
                            putJsonObject("params") {
                                put("minProtocol", 3)
                                put("maxProtocol", 3)
                                putJsonObject("client") {
                                    put("id", "openclaw-probe")
                                    put("platform", "jvm")
                                    put("mode", "probe")
                                    put("version", "1.0.0")
                                }
                                put("role", "operator")
                                putJsonArray("scopes") {
                                    add("operator.read")
                                }
                                putJsonArray("caps") { }
                                putJsonArray("commands") { }
                                putJsonObject("permissions") { }
                                putJsonObject("auth") { put("token", token) }
                                put("locale", "en-US")
                                put("userAgent", "langosta-mission-control/1.0.0")
                                putJsonObject("device") {
                                    put("id", deviceId)
                                    put("publicKey", devicePublicKey)
                                    put("signature", signature)
                                    put("signedAt", timestamp)
                                    put("nonce", nonce)
                                }
                            }
                        }.toString()

                        send(Frame.Text(connectMessage))

                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                val json = Json.parseToJsonElement(text).jsonObject
                                val type = json["type"]?.jsonPrimitive?.content

                                if (type == "res") {
                                    val ok = json["ok"]?.jsonPrimitive?.boolean == true
                                    if (ok) {
                                        val sessionsReq = buildJsonObject {
                                            put("type", "req")
                                            put("id", nextId())
                                            put("method", "sessions.list")
                                            putJsonObject("params") { }
                                        }.toString()
                                        send(Frame.Text(sessionsReq))
                                    }
                                } else if (type == "res" && json["id"]?.jsonPrimitive?.content?.endsWith("sessions.list") == false) {
                                    val result = json["result"]
                                    if (result != null) {
                                        val sessionsList = parseSessionsList(result)
                                        _sessions.emit(sessionsList)
                                        return@webSocket
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e("OpenClawRpc", "Error listing sessions: ${e.message}")
        }
        return emptyList()
    }

    private fun parseSessionsList(result: JsonElement): List<SessionInfo> {
        return try {
            if (result is JsonArray) {
                result.mapNotNull { item ->
                    val obj = item.jsonObject
                    SessionInfo(
                        sessionKey = obj["sessionKey"]?.jsonPrimitive?.content ?: "",
                        agentId = obj["agentId"]?.jsonPrimitive?.content ?: "",
                        model = obj["model"]?.jsonPrimitive?.content ?: "",
                        status = obj["status"]?.jsonPrimitive?.content ?: "unknown",
                        createdAt = obj["createdAt"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                        updatedAt = obj["updatedAt"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                        totalTokens = obj["totalTokens"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                        messagesCount = obj["messagesCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    )
                }
            } else emptyList()
        } catch (e: Exception) {
            AppLogger.e("OpenClawRpc", "Error parsing sessions: ${e.message}")
            emptyList()
        }
    }

    fun close() = client.close()
}
