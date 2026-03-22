package com.langosta.mission.data.api

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class WebSocketManager(private val baseUrl: String) {

    private val client = HttpClient {
        install(WebSockets)
    }

    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()

    suspend fun connect() {
        client.webSocket("ws://$baseUrl/ws") {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    _messages.emit(frame.readText())
                }
            }
        }
    }

    suspend fun send(message: String) {
        // Se implementa desde la sesión activa
    }

    fun close() = client.close()
}
