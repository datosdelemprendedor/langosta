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
import java.util.UUID

/**
 * Gestiona la conexión WebSocket al gateway OpenClaw.
 *
 * Protocolo real descubierto (issue #4):
 *  1. Servidor envía: { event: "connect.challenge", payload: { nonce, ts } }
 *  2. Cliente responde: { type: "req", method: "connect", params: { auth: { token }, ... } }
 *  3. Servidor responde: { type: "res", ok: true } → conexión establecida
 *
 * FASE DE PRUEBA: se omite el campo "device" para verificar si el servidor
 * acepta solo auth.token sin device auth (Opción A del issue #4).
 * Si falla con DEVICE_AUTH_*, implementar keypair ECDSA P-256 (Opción B).
 */
class WebSocketManager(private val baseUrl: String) {

    private val client = HttpClient {
        install(WebSockets)
    }

    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    private val _connectionState = MutableSharedFlow<ConnectionState>()
    val connectionState = _connectionState.asSharedFlow()

    private var wsDisabled: Boolean = false // Habilitado — probando sin device auth

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
        val host = baseUrl.substringBefore(":")
        val port = baseUrl.substringAfter(":").toIntOrNull() ?: 18789

        AppLogger.i("WebSocketManager", "Connecting to $host:$port (token-only auth)")

        try {
            _connectionState.emit(ConnectionState.Connecting)
            client.webSocket(host = host, port = port, path = path) {
                AppLogger.i("WebSocketManager", "Waiting for challenge...")

                // 1. Recibir challenge
                val challengeFrame = incoming.receive()
                if (challengeFrame !is Frame.Text) {
                    _connectionState.emit(ConnectionState.Error("Expected text frame for challenge"))
                    return@webSocket
                }

                val challengeJson = Json.parseToJsonElement(challengeFrame.readText()).jsonObject
                val eventType = challengeJson["event"]?.jsonPrimitive?.content

                if (eventType != "connect.challenge") {
                    AppLogger.w("WebSocketManager", "Unexpected first frame: $eventType")
                    _connectionState.emit(ConnectionState.Error("Expected connect.challenge, got: $eventType"))
                    return@webSocket
                }

                // 2. Enviar connect SIN device auth
                val connectMessage = buildJsonObject {
                    put("type", "req")
                    put("id", UUID.randomUUID().toString())
                    put("method", "connect")
                    putJsonObject("params") {
                        put("minProtocol", 3)
                        put("maxProtocol", 3)
                        putJsonObject("client") {
                            put("id", "langosta-mission-control")
                            put("platform", "jvm")
                            put("mode", "probe")
                            put("version", "1.0.0")
                        }
                        put("role", "operator")
                        putJsonArray("scopes") {
                            add("operator.read")
                            add("operator.write")
                        }
                        putJsonArray("caps") { add("tool-events") }
                        putJsonArray("commands") { }
                        putJsonObject("permissions") { }
                        putJsonObject("auth") {
                            put("token", token)
                        }
                        put("locale", "es-419")
                        put("userAgent", "langosta-mission-control/1.0.0")
                        // device omitido intencionalmente — ver issue #4 Opción A
                    }
                }.toString()

                AppLogger.i("WebSocketManager", "Sending connect (no device auth)")
                send(Frame.Text(connectMessage))

                // 3. Procesar respuestas y eventos
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    val text = frame.readText()
                    AppLogger.d("WebSocketManager", "Received: ${text.take(200)}")

                    val json = try {
                        Json.parseToJsonElement(text).jsonObject
                    } catch (e: Exception) {
                        AppLogger.w("WebSocketManager", "Invalid JSON: ${e.message}")
                        continue
                    }

                    when (json["type"]?.jsonPrimitive?.content) {
                        "res" -> {
                            val ok = json["ok"]?.jsonPrimitive?.boolean == true
                            if (ok) {
                                _connectionState.emit(ConnectionState.Connected)
                                AppLogger.i("WebSocketManager", "Connected! (token-only auth succeeded)")
                            } else {
                                val error = json["error"]?.jsonObject
                                val message = error?.get("message")?.jsonPrimitive?.content ?: "Unknown error"
                                val code = error?.get("details")?.jsonObject
                                    ?.get("code")?.jsonPrimitive?.content

                                AppLogger.e("WebSocketManager", "Connect failed — code=$code message=$message")

                                when (code) {
                                    "DEVICE_AUTH_REQUIRED",
                                    "DEVICE_AUTH_DEVICE_ID_MISMATCH" -> {
                                        // Opción A fallida → necesita Opción B (keypair ECDSA)
                                        // Ver issue #4
                                        AppLogger.w("WebSocketManager", "Device auth required — disabling WS (implement keypair in issue #4)")
                                        wsDisabled = true
                                        _connectionState.emit(ConnectionState.Error("Device auth required (ver issue #4)"))
                                    }
                                    else -> _connectionState.emit(ConnectionState.Error(message))
                                }
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
            AppLogger.w("WebSocketManager", "WS error — falling back to REST polling: ${e.message}")
            _connectionState.emit(ConnectionState.Disconnected)
        }
    }

    fun close() = client.close()
}
