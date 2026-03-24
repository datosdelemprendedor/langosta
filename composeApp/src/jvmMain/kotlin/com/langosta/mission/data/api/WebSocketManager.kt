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
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.UUID

/**
 * Gestiona la conexión WebSocket al gateway OpenClaw.
 *
 * Protocolo (issue #4 — Opción B):
 *  1. Servidor envía: { event: "connect.challenge", payload: { nonce, ts } }
 *  2. Cliente responde: { type: "req", method: "connect", params: {
 *       auth: { token },
 *       client: { id: "openclaw-control-ui", ... },
 *       device: { id, publicKey, signature, signedAt, nonce }
 *     }}
 *  3. Servidor responde: { type: "res", ok: true }
 *
 * Device identity: keypair ECDSA P-256 generado una vez por instancia.
 * El deviceId se deriva del hash SHA-256 de la clave pública (igual que el browser).
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

    // Keypair ECDSA P-256 — generado una vez, persistente por instancia
    private val keyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    // deviceId = primeros 16 chars del SHA-256 hex de la clave pública DER
    private val deviceId: String by lazy {
        val pubKeyBytes = keyPair.public.encoded
        val hash = MessageDigest.getInstance("SHA-256").digest(pubKeyBytes)
        hash.joinToString("") { "%02x".format(it) }.take(16)
    }

    // Clave pública en formato Base64 (DER encoded)
    private val publicKeyBase64: String by lazy {
        Base64.getEncoder().encodeToString(keyPair.public.encoded)
    }

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    /**
     * Firma el payload con ECDSA SHA-256.
     * payload = "v3:<deviceId>:<nonce>:<timestamp>"
     * Devuelve la firma en Base64.
     */
    private fun signPayload(nonce: String, timestamp: Long): String {
        val payload = "v3:$deviceId:$nonce:$timestamp"
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(keyPair.private)
        sig.update(payload.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(sig.sign())
    }

    suspend fun connectWithRetry(path: String = "") {
        if (wsDisabled) {
            AppLogger.w("WebSocketManager", "WebSocket disabled")
            _connectionState.emit(ConnectionState.Disconnected)
            return
        }

        val token = ConfigManager.getAuthToken()
        val serverUrl = ConfigManager.getServerUrl()
        val host = serverUrl.removePrefix("http://").removePrefix("https://").substringBefore(":")
        val port = serverUrl.substringAfterLast(":").toIntOrNull() ?: 18789
        val origin = "http://$host:$port"

        AppLogger.i("WebSocketManager", "Connecting to $host:$port | deviceId=$deviceId")

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

                val payload = challengeJson["payload"]?.jsonObject
                val nonce = payload?.get("nonce")?.jsonPrimitive?.content ?: UUID.randomUUID().toString()
                val timestamp = payload?.get("ts")?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()

                // 2. Firmar y enviar connect con device identity
                val signature = signPayload(nonce, timestamp)

                val connectMessage = buildJsonObject {
                    put("type", "req")
                    put("id", UUID.randomUUID().toString())
                    put("method", "connect")
                    putJsonObject("params") {
                        put("minProtocol", 3)
                        put("maxProtocol", 3)
                        putJsonObject("client") {
                            put("id", "openclaw-control-ui")
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
                            add("operator.approvals")
                            add("operator.pairing")
                        }
                        putJsonArray("caps") { add("tool-events") }
                        putJsonArray("commands") { }
                        putJsonObject("permissions") { }
                        putJsonObject("auth") {
                            put("token", token)
                        }
                        put("locale", "es-419")
                        put("userAgent", "langosta-mission-control/1.0.0")
                        putJsonObject("device") {
                            put("id", deviceId)
                            put("publicKey", publicKeyBase64)
                            put("signature", signature)
                            put("signedAt", timestamp)
                            put("nonce", nonce)
                        }
                    }
                }.toString()

                AppLogger.i("WebSocketManager", "Sending connect with ECDSA device identity")
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
                                AppLogger.i("WebSocketManager", "WS Connected! (ECDSA device auth OK)")
                            } else {
                                val error = json["error"]?.jsonObject
                                val message = error?.get("message")?.jsonPrimitive?.content ?: "Unknown"
                                val code = error?.get("details")?.jsonObject
                                    ?.get("code")?.jsonPrimitive?.content
                                    ?: error?.get("code")?.jsonPrimitive?.content
                                AppLogger.e("WebSocketManager", "Connect failed — code=$code message=$message")
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
            AppLogger.w("WebSocketManager", "WS unavailable: ${e.message}")
            _connectionState.emit(ConnectionState.Disconnected)
        }
    }

    fun close() = client.close()
}
