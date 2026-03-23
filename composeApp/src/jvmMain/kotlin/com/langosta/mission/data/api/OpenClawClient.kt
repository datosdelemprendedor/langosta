package com.langosta.mission.data.api

import com.langosta.mission.domain.model.*
import com.langosta.mission.util.AppLogger
import com.langosta.mission.util.ConfigManager
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

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

    suspend fun getHealth(): String {
        val response = client.get("$baseUrl/health") { withAuth() }.bodyAsText()
        return response
    }

    fun dashboardFromConfig(config: OpenClawBootstrapConfig): DashboardState =
        DashboardState(
            gateway = GatewayStatus(
                status = "online",
                mode = "local",
                version = config.serverVersion ?: "unknown"
            ),
            sessions = SessionsInfo(active = 0, total = 0),
            agents = listOf(
                AgentNode(
                    id = config.assistantAgentId,
                    name = config.assistantName,
                    type = "assistant",
                    model = "openclaw",
                    tokensIn = 0,
                    tokensOut = 0,
                    utilization = 0,
                    lastSeen = "now"
                )
            ),
            system = SystemMetrics(
                memoryPercent = 0,
                diskPercent = 0,
                errors = 0,
                queueSize = 0
            ),
            auditEvents24h = 0,
            loginFailures24h = 0
        )

    suspend fun sendMessage(agentId: String, input: String): String {
        val requestBody = buildJsonObject {
            put("model", "openclaw:$agentId")
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    put("content", input)
                }
            }
        }.toString()

        return try {
            val response = client.post("$baseUrl/v1/chat/completions") {
                withAuth()
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }.bodyAsText()

            Json.parseToJsonElement(response)
                .jsonObject["choices"]!!
                .jsonArray[0]
                .jsonObject["message"]!!
                .jsonObject["content"]!!
                .jsonPrimitive.content
        } catch (e: Exception) {
            AppLogger.e("OpenClawClient", "sendMessage failed: ${e.message}")
            throw Exception("Failed to send message. Ensure /v1/chat/completions is enabled in OpenClaw config. Error: ${e.message}")
        }
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
