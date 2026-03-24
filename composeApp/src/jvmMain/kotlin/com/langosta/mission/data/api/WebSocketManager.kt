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
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.ECNamedCurveSpec
import java.math.BigInteger
import java.security.KeyFactory
import java.security.Security
import java.security.Signature
import java.security.spec.ECPrivateKeySpec
import java.util.Base64
import java.util.UUID

/**
 * Gestiona la conexion WebSocket al gateway OpenClaw.
 *
 * Device identity: reutiliza el keypair del browser (raw Base64url P-256)
 * leido desde ~/.openclaw/device-identity.json.
 *
 * La private key del browser es un scalar raw de 32 bytes (no PKCS8).
 * Se reconstruye via BouncyCastle -> ECPrivateKeySpec.
 */
class WebSocketManager(private val baseUrl: String) {

    init {
        // Registrar BouncyCastle si no esta registrado
        if (Security.getProvider("BC") == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    private val client = HttpClient {
        install(WebSockets)
    }

    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    private val _connectionState = MutableSharedFlow<ConnectionState>()
    val connectionState = _connectionState.asSharedFlow()

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    /**
     * Construye la firma ECDSA usando la raw private key del browser.
     * La key es Base64url sin padding (32 bytes = scalar D de la curva P-256).
     */
    private fun signWithRawKey(privateKeyB64url: String, deviceId: String, nonce: String, timestamp: Long): String {
        // Base64url -> bytes (agregar padding si falta)
        val padded = privateKeyB64url
            .replace('-', '+').replace('_', '/')
            .let { it + "=".repeat((4 - it.length % 4) % 4) }
        val rawBytes = Base64.getDecoder().decode(padded)

        // Reconstruir ECPrivateKey desde scalar raw via BouncyCastle
        val curveParams = ECNamedCurveTable.getParameterSpec("secp256r1")
        val curveSpec = ECNamedCurveSpec("secp256r1", curveParams.curve, curveParams.g, curveParams.n)
        val privKeySpec = ECPrivateKeySpec(BigInteger(1, rawBytes), curveSpec)
        val privKey = KeyFactory.getInstance("EC", "BC").generatePrivate(privKeySpec)

        // Firmar payload
        val payload = "v3:$deviceId:$nonce:$timestamp"
        val sig = Signature.getInstance("SHA256withECDSA", "BC")
        sig.initSign(privKey)
        sig.update(payload.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(sig.sign())
    }

    suspend fun connectWithRetry(path: String = "") {
        val token = ConfigManager.getAuthToken()
        val serverUrl = ConfigManager.getServerUrl()
        val host = serverUrl.removePrefix("http://").removePrefix("https://").substringBefore(":")
        val port = serverUrl.substringAfterLast(":").toIntOrNull() ?: 18789
        val origin = "http://$host:$port"

        if (!ConfigManager.hasPairedDevice()) {
            AppLogger.e("WebSocketManager", "No hay device identity. Crea ~/.openclaw/device-identity.json")
            _connectionState.emit(ConnectionState.Error("device-identity.json no encontrado"))
            return
        }

        val deviceId  = ConfigManager.getPairedDeviceId()
        val publicKey = ConfigManager.getPairedPublicKey()
        val privateKeyB64url = ConfigManager.getPairedPrivateKey()

        AppLogger.i("WebSocketManager", "Connecting | deviceId=${deviceId.take(16)}...")

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
                // 1. Challenge
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

                val payload   = challengeJson["payload"]?.jsonObject
                val nonce     = payload?.get("nonce")?.jsonPrimitive?.content ?: UUID.randomUUID().toString()
                val timestamp = payload?.get("ts")?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()

                // 2. Firmar con raw private key P-256
                val signature = signWithRawKey(privateKeyB64url, deviceId, nonce, timestamp)

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

                AppLogger.i("WebSocketManager", "Sending connect | deviceId=${deviceId.take(16)}...")
                send(Frame.Text(connectMessage))

                // 3. Eventos
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
                                val msg = error?.get("message")?.jsonPrimitive?.content ?: "Unknown"
                                val code = error?.get("details")?.jsonObject
                                    ?.get("code")?.jsonPrimitive?.content
                                    ?: error?.get("code")?.jsonPrimitive?.content
                                AppLogger.e("WebSocketManager", "Connect failed — code=$code msg=$msg")
                                _connectionState.emit(ConnectionState.Error(msg))
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
