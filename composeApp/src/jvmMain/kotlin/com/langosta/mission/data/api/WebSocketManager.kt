package com.langosta.mission.data.api

import com.langosta.mission.util.ConfigManager
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class WebSocketManager(private val baseUrl: String) {

    private val client = HttpClient {
        install(WebSockets)
    }

    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    suspend fun connectWithRetry(path: String = "/ws/broadcast") {
        val token = ConfigManager.getAuthToken()
        val host = baseUrl.substringBefore(":")
        val port = baseUrl.substringAfter(":").toIntOrNull() ?: 18789
        var attempt = 0
        while (true) {
            try {
                client.webSocket(host = host, port = port, path = path) {
                    attempt = 0
                    // 1. Esperar connect.challenge
                    val challengeFrame = incoming.receive()
                    if (challengeFrame is Frame.Text) {
                        // 2. Responder handshake
                        send(Frame.Text(
                            """{"type":"req","id":"1","method":"connect","params":{"auth":{"token":"$token"},"client":{"id":"langosta-desktop","platform":"jvm","mode":"control-ui"},"minProtocol":1,"maxProtocol":1}}"""
                        ))
                    }
                    // 3. Escuchar eventos
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            _events.emit(frame.readText())
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                attempt++
                delay(minOf(1000L * attempt, 30_000L))
            }
        }
    }

    fun close() = client.close()
}
