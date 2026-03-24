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
 * WebSocket al gateway OpenClaw con device identity ECDSA P-256.
 *
 * La private key del browser es raw Base64url (32 bytes = scalar D).
 * Java genera firmas ECDSA en DER (ASN.1), pero WebCrypto espera IEEE P1363 (r||s, 64 bytes).
 * -> Se convierte DER -> P1363 antes de enviar.
 */
class WebSocketManager(private val baseUrl: String) {

    init {
        if (Security.getProvider("BC") == null) Security.addProvider(BouncyCastleProvider())
    }

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
     * Convierte firma ECDSA de DER (ASN.1) a IEEE P1363 (r||s concatenados, 64 bytes).
     * WebCrypto.verify() espera P1363, Java produce DER.
     *
     * DER layout: 0x30 <len> 0x02 <rLen> <r> 0x02 <sLen> <s>
     */
    private fun derToP1363(der: ByteArray): ByteArray {
        var offset = 2                          // saltar 0x30 <totalLen>
        if (der[1].toInt() and 0xff == 0x81) offset = 3  // len > 127
        offset++                                // saltar 0x02
        val rLen = der[offset++].toInt() and 0xff
        val r = der.copyOfRange(offset, offset + rLen)
        offset += rLen
        offset++                                // saltar 0x02
        val sLen = der[offset++].toInt() and 0xff
        val s = der.copyOfRange(offset, offset + sLen)

        // Normalizar r y s a 32 bytes (quitar byte 0x00 de padding o rellenar con 0)
        fun normalize(bytes: ByteArray): ByteArray {
            val trimmed = bytes.dropWhile { it == 0.toByte() }.toByteArray()
            return if (trimmed.size == 32) trimmed
            else ByteArray(32 - trimmed.size) + trimmed
        }
        return normalize(r) + normalize(s)
    }

    /**
     * Firma el payload con la raw private key P-256 del browser.
     * Devuelve la firma en Base64url (formato que usa WebCrypto).
     */
    private fun signWithRawKey(privateKeyB64url: String, deviceId: String, nonce: String, timestamp: Long): String {
        val padded = privateKeyB64url
            .replace('-', '+').replace('_', '/')
            .let { it + "=".repeat((4 - it.length % 4) % 4) }
        val rawBytes = Base64.getDecoder().decode(padded)

        val curveParams = ECNamedCurveTable.getParameterSpec("secp256r1")
        val curveSpec   = ECNamedCurveSpec("secp256r1", curveParams.curve, curveParams.g, curveParams.n)
        val privKey     = KeyFactory.getInstance("EC", "BC")
            .generatePrivate(ECPrivateKeySpec(BigInteger(1, rawBytes), curveSpec))

        val payload = "v3:$deviceId:$nonce:$timestamp"
        val sig = Signature.getInstance("SHA256withECDSA", "BC")
        sig.initSign(privKey)
        sig.update(payload.toByteArray(Charsets.UTF_8))
        val derBytes = sig.sign()

        // Convertir DER -> P1363 (r||s) que es lo que valida el servidor (WebCrypto)
        val p1363 = derToP1363(derBytes)
        // Devolver en Base64url sin padding (igual que WebCrypto)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(p1363)
    }

    suspend fun connectWithRetry(path: String = "") {
        val token    = ConfigManager.getAuthToken()
        val serverUrl = ConfigManager.getServerUrl()
        val host     = serverUrl.removePrefix("http://").removePrefix("https://").substringBefore(":")
        val port     = serverUrl.substringAfterLast(":").toIntOrNull() ?: 18789
        val origin   = "http://$host:$port"

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

                AppLogger.i("WebSocketManager", "Sending connect (P1363 signature)")
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
                                val error = json["error"]?.jsonObject
                                val errMsg  = error?.get("message")?.jsonPrimitive?.content ?: "Unknown"
                                val code  = error?.get("details")?.jsonObject?.get("code")?.jsonPrimitive?.content
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
