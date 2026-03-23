package com.langosta.mission.data.api

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

class WebSocketManager(private val baseUrl: String) {

    private val client = HttpClient {
        install(WebSockets)
    }

    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    private val _connectionState = MutableSharedFlow<ConnectionState>()
    val connectionState = _connectionState.asSharedFlow()

    private var deviceId: String = "langosta-probe-${UUID.randomUUID().toString().take(8)}"
    private var wsDisabled: Boolean = true // Deshabilitado por defecto sin device auth
    
    private val devicePublicKey: String = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAlivFI8qB4D0y2jy0CfEqFyy46R0o7S8TKpsx5xbHKoU1VWg6QkQm+ntyIv1p4kE1sPEQO73+HY8+Bzs75XwRTHLokBm9LCqJ3xrYSO9hT2j2r+5w4UQx1xpJ2Mc8pmB1xS2i3hVdP8bQv3N1w4TQT1S5k6fqQ6FQ2xwptF1Jc3FxJ4qM3a4V2U+89lF3a6C0bI+YDpGiFCYPr+9hAQ=="

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    private fun generateSignature(nonce: String, timestamp: Long): String {
        val payload = "v3:$deviceId:$nonce:$timestamp"
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(payload.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    suspend fun connectWithRetry(path: String = "") {
        if (wsDisabled) {
            AppLogger.w("WebSocketManager", "WebSocket disabled, skipping connection")
            _connectionState.emit(ConnectionState.Disconnected)
            return
        }
        
        _connectionState.emit(ConnectionState.Disconnected)
        
        val token = ConfigManager.getAuthToken()
        val host = baseUrl.substringBefore(":")
        val port = baseUrl.substringAfter(":").toIntOrNull() ?: 18789

        AppLogger.i("WebSocketManager", "Connecting to $host:$port")

        try {
            _connectionState.emit(ConnectionState.Connecting)
            client.webSocket(host = host, port = port, path = path) {
                AppLogger.i("WebSocketManager", "Waiting for challenge...")

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
                            put("id", "1")
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

                        AppLogger.i("WebSocketManager", "Sending connect: $connectMessage")
                        send(Frame.Text(connectMessage))
                    }
                }

                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        AppLogger.i("WebSocketManager", "Received: $text")

                        val json = Json.parseToJsonElement(text).jsonObject
                        val type = json["type"]?.jsonPrimitive?.content

                        when (type) {
                            "res" -> {
                                val ok = json["ok"]?.jsonPrimitive?.boolean == true
                                if (ok) {
                                    _connectionState.emit(ConnectionState.Connected)
                                    AppLogger.i("WebSocketManager", "Connected successfully!")
                                } else {
                                    val error = json["error"]?.jsonObject
                                    val message = error?.get("message")?.jsonPrimitive?.content ?: "Unknown error"
                                    val details = error?.get("details")?.jsonObject
                                    val detailCode = details?.get("code")?.jsonPrimitive?.content
                                    
                                    if (detailCode == "DEVICE_AUTH_DEVICE_ID_MISMATCH") {
                                        AppLogger.w("WebSocketManager", "Device auth required - falling back to REST polling only")
                                        wsDisabled = true
                                        _connectionState.emit(ConnectionState.Disconnected)
                                        return@webSocket
                                    }
                                    
                                    _connectionState.emit(ConnectionState.Error(message))
                                    AppLogger.e("WebSocketManager", "Connection failed: $message")
                                    return@webSocket
                                }
                            }
                            "event" -> {
                                _events.emit(text)
                            }
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w("WebSocketManager", "WebSocket unavailable - using REST polling only: ${e.message}")
            wsDisabled = true
            _connectionState.emit(ConnectionState.Disconnected)
        }
    }

    fun close() = client.close()
}
