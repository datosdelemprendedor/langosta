package com.langosta.mission.data.api

import com.langosta.mission.domain.model.*
import com.langosta.mission.util.ConfigManager
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
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
        if (token.isNotEmpty()) header("Authorization", "Bearer $token")
    }

    suspend fun ping(): OpenClawBootstrapConfig =
        client.get("$baseUrl/__openclaw/control-ui-config.json") { withAuth() }.body()

    suspend fun getBootstrapConfig(): OpenClawBootstrapConfig =
        client.get("$baseUrl/__openclaw/control-ui-config.json") { withAuth() }.body()

    // Dashboard no expone JSON — retorna estado por defecto
    suspend fun getDashboard(): DashboardState = DashboardState(
        gateway = GatewayStatus(status = "online", mode = "local"),
        sessions = SessionsInfo(active = 0, total = 0),
        agents = emptyList(),
        system = SystemMetrics(memoryPercent = 0, diskPercent = 0, errors = 0, queueSize = 0),
        auditEvents24h = 0,
        loginFailures24h = 0
    )

    suspend fun sendMessage(agentId: String, input: String): String {
        return client.post("$baseUrl/v1/responses") {
            withAuth()
            contentType(ContentType.Application.Json)
            header("x-openclaw-agent-id", agentId)
            setBody(mapOf("model" to "openclaw", "input" to input))
        }.body()
    }

    suspend fun getAgents(): List<Agent> {
        val config = getBootstrapConfig()
        return listOf(
            Agent(
                id = config.assistantAgentId,
                name = config.assistantName,
                model = "openclaw",
                isOnline = true
            )
        )
    }

    suspend fun getTasks(): List<Task> = emptyList()

    fun close() = client.close()
}
