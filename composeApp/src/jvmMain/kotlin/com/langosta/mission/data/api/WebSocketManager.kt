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
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.UUID

/**
 * Gestiona la conexion WebSocket al gateway OpenClaw.
 *
 * Device identity (issue #4):
 *  - Prioritario: reutilizar el device ya pareado en ~/.openclaw/devices/paired.json
 *  - Fallback: generar keypair ECDSA P-256 nuevo (requiere aprobacion en el gateway)
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

    /**
     * Firma el payload con la private key del device pareado (PKCS8 Base64)
     * o con un keypair generado en memoria si no hay device pareado.
     */
    private fun buildDeviceIdentity(nonce: String, timestamp: Long): Triple<String, String, String> {
        // Intentar usar el device pareado
        if (ConfigManager.hasPairedDevice()) {
            val deviceId = ConfigManager.getPairedDeviceId()
            val privKeyB64 = ConfigManager.getPairedPrivateKey()
            val pubKeyB64 = ConfigManager.getPairedPublicKey()

            return try {
                val privKeyBytes = Base64.getDecoder().decode(privKeyB64)
                val privKey = KeyFactory.getInstance("EC")
                    .generatePrivate(PKCS8EncodedKeySpec(privKeyBytes))

                val payload = "v3:$deviceId:$nonce:$timestamp"
                val sig = Signature.getInstance("SHA256withECDSA")
                sig.initSign(privKey)
                sig.update(payload.toByteArray(Charsets.UTF_8))
                val signature = Base64.getEncoder().encodeToString(sig.sign())

                AppLogger.i("WebSocketManager", "Usando device pareado: id=$deviceId")
                Triple(deviceId, pubKeyB64, signature)
            } catch (e: Exception) {
                AppLogger.w("WebSocketManager", "Error usando paired key: ${e.message} — generando keypair nuevo")
                generateFreshIdentity(nonce, timestamp)
            }
        }

        AppLogger.w("WebSocketManager", "No hay device pareado, generando keypair nuevo (requiere aprobacion)")
        return generateFreshIdentity(nonce, timestamp)
    }

    private fun generateFreshIdentity(nonce: String, timestamp: Long): Triple<String, String, String> {
        val kp = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()

        val pubBytes = kp.public.encoded
        val deviceId = MessageDigest.getInstance("SHA-256").digest(pubBytes)
            .joinToString("") { "%02x".format(it) }.take(16)
        val pubKeyB64 = Base64.getEncoder().encodeToString(pubBytes)

        val payload = "v3:$deviceId:$nonce:$timestamp"
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(kp.private)
        sig.update(payload.toByteArray(Charsets.UTF_8))
        val signature = Base64.getEncoder().encodeToString(sig.sign())

        return Triple(deviceId, pubKeyB64, signature)
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

        AppLogger.i("WebSocketManager", "Connecting to $host:$port")

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

                // 2. Construir device identity y firmar
                val (deviceId, publicKey, signature) = buildDeviceIdentity(nonce, timestamp)

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
                        putJsonObject("auth") { put("token", token) }
                        put("locale", "es-419")
                        put("userAgent", "langosta-mission-control/1.0.0")
                        putJsonObject("device") {
                            put("id", deviceId)
                            put("publicKey", publicKey)
                            put("signature", signature)
                            put("signedAt", timestamp)
                            put("nonce", nonce)
                        }
                    }
                }.toString()

                AppLogger.i("WebSocketManager", "Sending connect | deviceId=$deviceId")
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
