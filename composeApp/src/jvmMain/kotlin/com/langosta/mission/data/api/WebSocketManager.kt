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
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.UUID

/**
 * WebSocket al gateway OpenClaw con device identity ECDSA P-256.
 *
 * La private key del browser es raw Base64url (32 bytes = scalar D).
 * Se envuelve en PKCS8 manualmente (igual que hace el browser internamente).
 * La firma se produce en IEEE P1363 (r||s, 64 bytes) via SHA256withECDSAinP1363Format.
 */
class WebSocketManager(private val baseUrl: String) {

    private val client = HttpClient { install(WebSockets) }

    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    private val _connectionState = MutableSharedFlow<ConnectionState>()
    val connectionState = _connectionState.asSharedFlow()

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting   : ConnectionState()
        object Connected    : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    /**
     * Envuelve 32 bytes raw (scalar D de P-256) en un PKCS8 DER mínimo.
     * Estructura idéntica a la que usa WebCrypto internamente.
     *
     * SEQUENCE {
     *   INTEGER 0  (version)
     *   SEQUENCE { OID ecPublicKey, OID P-256 }
     *   OCTET STRING {
     *     SEQUENCE {
     *       INTEGER 1  (ECPrivateKey version)
     *       OCTET STRING <32 bytes d>
     *     }
     *   }
     * }
     */
    private fun wrapRawKeyAsPkcs8(rawD: ByteArray): ByteArray {
        require(rawD.size == 32) { "EC P-256 private key debe ser 32 bytes, got ${rawD.size}" }
        return byteArrayOf(
            0x30, 0x41,                                     // SEQUENCE (65 bytes)
            0x02, 0x01, 0x00,                               // INTEGER version=0
            0x30, 0x13,                                     // SEQUENCE AlgorithmIdentifier
              0x06, 0x07, 0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 0x02, 0x01, // OID ecPublicKey
              0x06, 0x08, 0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 0x03, 0x01, 0x07, // OID P-256
            0x04, 0x27,                                     // OCTET STRING (39 bytes)
              0x30, 0x25,                                   // SEQUENCE ECPrivateKey
                0x02, 0x01, 0x01,                           // INTEGER version=1
                0x04, 0x20                                  // OCTET STRING (32 bytes)
        ) + rawD
    }

    /**
     * Firma el payload con la raw private key P-256 del browser.
     * Usa SHA256withECDSAinP1363Format para producir r||s (64 bytes) directamente,
     * igual que WebCrypto.sign({ name:'ECDSA', hash:'SHA-256' }).
     * Devuelve Base64url sin padding.
     */
    private fun signWithRawKey(privateKeyB64url: String, deviceId: String, nonce: String, timestamp: Long): String {
        val padded = privateKeyB64url
            .replace('-', '+').replace('_', '/')
            .let { it + "=".repeat((4 - it.length % 4) % 4) }
        val rawBytes = Base64.getDecoder().decode(padded)

        val pkcs8 = wrapRawKeyAsPkcs8(rawBytes)
        val privKey = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(pkcs8))

        val payload = "v3:$deviceId:$nonce:$timestamp"
        // P1363Format produce r||s directamente (sin conversion DER)
        val sig = Signature.getInstance("SHA256withECDSAinP1363Format")
        sig.initSign(privKey)
        sig.update(payload.toByteArray(Charsets.UTF_8))
        val sigBytes = sig.sign()

        return Base64.getUrlEncoder().withoutPadding().encodeToString(sigBytes)
    }

    suspend fun connectWithRetry(path: String = "") {
        val token     = ConfigManager.getAuthToken()
        val serverUrl = ConfigManager.getServerUrl()
        val host      = serverUrl.removePrefix("http://").removePrefix("https://").substringBefore(":")
        val port      = serverUrl.substringAfterLast(":").toIntOrNull() ?: 18789
        val origin    = "http://$host:$port"

        if (!ConfigManager.hasPairedDevice()) {
            AppLogger.e("WebSocketManager", "No hay device identity. Crea ~/.openclaw/device-identity.json")
            _connectionState.emit(ConnectionState.Error("device-identity.json no encontrado"))
            return
        }

        val deviceId         = ConfigManager.getPairedDeviceId()
        val publicKey        = ConfigManager.getPairedPublicKey()
        val privateKeyB64url = ConfigManager.getPairedPrivateKey()

        AppLogger.i("WebSocketManager", "Connecting | deviceId=${deviceId.take(16)}...")

        try {
            _connectionState.emit(ConnectionState.Connecting)
            client.webSocket(
                host = host, port = port, path = path,
                request = {
                    header("Origin", origin)
                    header("Host", "$host:$port")
                }
            ) {
                // 1. Challenge
                val cf = incoming.receive()
                if (cf !is Frame.Text) { _connectionState.emit(ConnectionState.Error("Expected text frame")); return@webSocket }
                val challengeJson = Json.parseToJsonElement(cf.readText()).jsonObject
                if (challengeJson["event"]?.jsonPrimitive?.content != "connect.challenge") {
                    _connectionState.emit(ConnectionState.Error("Expected connect.challenge")); return@webSocket
                }

                val pl        = challengeJson["payload"]?.jsonObject
                val nonce     = pl?.get("nonce")?.jsonPrimitive?.content ?: UUID.randomUUID().toString()
                val timestamp = pl?.get("ts")?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()

                // 2. Firmar -> P1363 -> Base64url
                val signature = signWithRawKey(privateKeyB64url, deviceId, nonce, timestamp)

                val msg = buildJsonObject {
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
                            add("operator.read"); add("operator.write"); add("operator.admin")
                            add("operator.approvals"); add("operator.pairing")
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

                AppLogger.i("WebSocketManager", "Sending connect (PKCS8+P1363 signature)")
                send(Frame.Text(msg))

                // 3. Eventos
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    val text = frame.readText()
                    AppLogger.d("WebSocketManager", "Received: ${text.take(300)}")
                    val json = try { Json.parseToJsonElement(text).jsonObject } catch (e: Exception) { continue }
                    when (json["type"]?.jsonPrimitive?.content) {
                        "res" -> {
                            if (json["ok"]?.jsonPrimitive?.boolean == true) {
                                _connectionState.emit(ConnectionState.Connected)
                                AppLogger.i("WebSocketManager", "WS Connected!")
                            } else {
                                val error  = json["error"]?.jsonObject
                                val errMsg = error?.get("message")?.jsonPrimitive?.content ?: "Unknown"
                                val code   = error?.get("details")?.jsonObject?.get("code")?.jsonPrimitive?.content
                                    ?: error?.get("code")?.jsonPrimitive?.content
                                AppLogger.e("WebSocketManager", "Connect failed — code=$code msg=$errMsg")
                                _connectionState.emit(ConnectionState.Error(errMsg))
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
