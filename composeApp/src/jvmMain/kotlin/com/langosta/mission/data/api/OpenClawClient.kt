package com.langosta.mission.data.api

import com.langosta.mission.domain.model.Agent
import com.langosta.mission.domain.model.DashboardState
import com.langosta.mission.domain.model.OpenClawBootstrapConfig
import com.langosta.mission.domain.model.Task
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

    suspend fun getAgents(): List<Agent> =
        client.get("$baseUrl/agents").body()

    suspend fun getTasks(): List<Task> =
        client.get("$baseUrl/tasks").body()

    suspend fun createTask(task: Task): Task =
        client.post("$baseUrl/tasks") {
            setBody(task)
        }.body()

    suspend fun updateTaskStatus(taskId: String, status: String): Task =
        client.patch("$baseUrl/tasks/$taskId/status") {
            setBody(mapOf("status" to status))
        }.body()

    suspend fun getBootstrapConfig(): OpenClawBootstrapConfig =
        client.get("$baseUrl/__openclaw/control-ui-config.json").body()

    suspend fun getDashboard(): DashboardState =
        client.get("$baseUrl/__openclaw/dashboard").body()

    fun close() = client.close()
}
