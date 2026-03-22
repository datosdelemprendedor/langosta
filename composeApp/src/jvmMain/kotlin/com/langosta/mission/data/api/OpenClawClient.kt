package com.langosta.mission.data.api

import com.langosta.mission.data.api.dto.AgentDto
import com.langosta.mission.data.api.dto.TaskDto
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class OpenClawClient(private val baseUrl: String) {

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun getAgents(): List<AgentDto> =
        client.get("$baseUrl/agents").body()

    suspend fun getTasks(): List<TaskDto> =
        client.get("$baseUrl/tasks").body()

    suspend fun createTask(task: TaskDto): TaskDto =
        client.post("$baseUrl/tasks") {
            setBody(task)
        }.body()

    suspend fun updateTaskStatus(taskId: String, status: String): TaskDto =
        client.patch("$baseUrl/tasks/$taskId/status") {
            setBody(mapOf("status" to status))
        }.body()

    fun close() = client.close()
}
