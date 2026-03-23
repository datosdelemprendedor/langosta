package com.langosta.mission.data.api

import com.langosta.mission.domain.model.Agent
import com.langosta.mission.domain.model.DashboardState
import com.langosta.mission.domain.model.OpenClawBootstrapConfig
import com.langosta.mission.domain.model.Task
import com.langosta.mission.util.ConfigManager
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

    private fun HttpRequestBuilder.withAuth() {
        val token = ConfigManager.getAuthToken()
        if (token.isNotEmpty()) {
            header("Authorization", "Bearer $token")
        }
    }

    suspend fun getAgents(): List<Agent> =
        client.get("$baseUrl/agents") { withAuth() }.body()

    suspend fun getTasks(): List<Task> =
        client.get("$baseUrl/tasks") { withAuth() }.body()

    suspend fun createTask(task: Task): Task =
        client.post("$baseUrl/tasks") {
            withAuth()
            setBody(task)
        }.body()

    suspend fun updateTaskStatus(taskId: String, status: String): Task =
        client.patch("$baseUrl/tasks/$taskId/status") {
            withAuth()
            setBody(mapOf("status" to status))
        }.body()

    suspend fun getBootstrapConfig(): OpenClawBootstrapConfig =
        client.get("$baseUrl/__openclaw/control-ui-config.json") { withAuth() }.body()

    suspend fun getDashboard(): DashboardState =
        client.get("$baseUrl/__openclaw/dashboard") { withAuth() }.body()

    fun close() = client.close()
}
