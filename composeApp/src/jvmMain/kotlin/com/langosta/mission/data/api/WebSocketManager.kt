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
        val url = "ws://$baseUrl$path${if (token.isNotEmpty()) "?token=$token" else ""}"
        var attempt = 0
        while (true) {
            try {
                client.webSocket(url) {
                    attempt = 0
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
                val delay = minOf(1000L * attempt, 30_000L)
                delay(delay)
            }
        }
    }

    fun close() = client.close()
}
