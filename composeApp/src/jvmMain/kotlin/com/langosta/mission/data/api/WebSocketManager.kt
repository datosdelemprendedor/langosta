package com.langosta.mission.data.api

import com.langosta.mission.util.AppLogger
import com.langosta.mission.util.ConfigManager
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.*
import java.util.UUID

/**
 * Gestiona la conexión WebSocket al gateway OpenClaw.
 *
 * Protocolo (issue #4):
 *  1. Servidor envía: { event: "connect.challenge", payload: { nonce, ts } }
 *  2. Cliente responde: { type: "req", method: "connect", params: { auth: { token }, client: { id: "openclaw-control-ui" } } }
 *  3. Servidor responde: { type: "res", ok: true }
 *
 * Validaciones del servidor:
 *  - client.id DEBE ser "openclaw-control-ui"
 *  - Header Origin DEBE ser http://<host>:<port> (el servidor valida allowedOrigins)
 */
class WebSocketManager(private val baseUrl: String) {

    private val client = HttpClient {
        install(WebSockets)
    }

    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    private val _connectionState = MutableSharedFlow<ConnectionState>()
    val connectionState = _connectionState.asSharedFlow()

    private var wsDisabled: Boolean = false

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    suspend fun connectWithRetry(path: String = "") {
        if (wsDisabled) {
            AppLogger.w("WebSocketManager", "WebSocket disabled")
            _connectionState.emit(ConnectionState.Disconnected)
            return
        }

        val token = ConfigManager.getAuthToken()
        val serverUrl = ConfigManager.getServerUrl() // ej: http://127.0.0.1:18789
        val host = serverUrl.removePrefix("http://").removePrefix("https://").substringBefore(":")
        val port = serverUrl.substringAfterLast(":").toIntOrNull() ?: 18789
        // El Origin debe coincidir con la URL del gateway (como lo haría el browser)
        val origin = "http://$host:$port"

        AppLogger.i("WebSocketManager", "Connecting to $host:$port (Origin: $origin)")

        try {
            _connectionState.emit(ConnectionState.Connecting)
            client.webSocket(
                host = host,
                port = port,
                path = path,
                request = {
                    header("Origin", origin)
                    header("Host", "$host:$port")
                }
            ) {
                // 1. Recibir challenge
                val challengeFrame = incoming.receive()
                if (challengeFrame !is Frame.Text) {
                    _connectionState.emit(ConnectionState.Error("Expected text frame"))
                    return@webSocket
                }

                val challengeJson = Json.parseToJsonElement(challengeFrame.readText()).jsonObject
                if (challengeJson["event"]?.jsonPrimitive?.content != "connect.challenge") {
                    _connectionState.emit(ConnectionState.Error("Expected connect.challenge"))
                    return@webSocket
                }

                // 2. Responder con connect
                val connectMessage = buildJsonObject {
                    put("type", "req")
                    put("id", UUID.randomUUID().toString())
                    put("method", "connect")
                    putJsonObject("params") {
                        put("minProtocol", 3)
                        put("maxProtocol", 3)
                        putJsonObject("client") {
                            put("id", "openclaw-control-ui")  // constante requerida
                            put("platform", "Win32")
                            put("mode", "webchat")
                            put("version", "control-ui")
                            put("instanceId", UUID.randomUUID().toString())
                        }
                        put("role", "operator")
                        putJsonArray("scopes") {
                            add("operator.read")
                            add("operator.write")
                            add("operator.admin")
                        }
                        putJsonArray("caps") { add("tool-events") }
                        putJsonArray("commands") { }
                        putJsonObject("permissions") { }
                        putJsonObject("auth") {
                            put("token", token)
                        }
                        put("locale", "es-419")
                        put("userAgent", "langosta-mission-control/1.0.0")
                        // device omitido — Opción A (issue #4)
                    }
                }.toString()

                AppLogger.i("WebSocketManager", "Sending connect")
                send(Frame.Text(connectMessage))

                // 3. Procesar respuestas y eventos
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    val text = frame.readText()
                    AppLogger.d("WebSocketManager", "Received: ${text.take(300)}")

                    val json = try {
                        Json.parseToJsonElement(text).jsonObject
                    } catch (e: Exception) { continue }

                    when (json["type"]?.jsonPrimitive?.content) {
                        "res" -> {
                            if (json["ok"]?.jsonPrimitive?.boolean == true) {
                                _connectionState.emit(ConnectionState.Connected)
                                AppLogger.i("WebSocketManager", "WS Connected!")
                            } else {
                                val error = json["error"]?.jsonObject
                                val message = error?.get("message")?.jsonPrimitive?.content ?: "Unknown"
                                val code = error?.get("details")?.jsonObject
                                    ?.get("code")?.jsonPrimitive?.content
                                    ?: error?.get("code")?.jsonPrimitive?.content

                                AppLogger.e("WebSocketManager", "Connect failed — code=$code message=$message")

                                when (code) {
                                    "DEVICE_AUTH_REQUIRED",
                                    "DEVICE_AUTH_DEVICE_ID_MISMATCH" -> {
                                        AppLogger.w("WebSocketManager", "Device auth required — implementar keypair ECDSA (issue #4 Opción B)")
                                        wsDisabled = true
                                    }
                                }
                                _connectionState.emit(ConnectionState.Error(message))
                                return@webSocket
                            }
                        }
                        "event" -> _events.emit(text)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w("WebSocketManager", "WS unavailable — REST polling only: ${e.message}")
            _connectionState.emit(ConnectionState.Disconnected)
        }
    }

    fun close() = client.close()
}
