package com.langosta.mission.data.repository

import com.langosta.mission.data.api.OpenClawHttpClient
import com.langosta.mission.domain.model.Agent
import com.langosta.mission.domain.model.AgentSkill
import com.langosta.mission.domain.model.AgentStatus
import com.langosta.mission.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class AgentRepository(
    private val httpClient: OpenClawHttpClient = OpenClawHttpClient()
) {

    /** Flow con polling cada 5s — emite lista de agentes enriquecidos */
    fun agentsStream(): Flow<List<Agent>> = flow {
        while (true) {
            try {
                val agents = fetchAgents()
                emit(agents)
            } catch (e: Exception) {
                AppLogger.e("AgentRepository", "Error fetching agents: ${e.message}")
                emit(emptyList())
            }
            delay(5_000)
        }
    }

    suspend fun fetchAgents(): List<Agent> = withContext(Dispatchers.IO) {
        val httpAgents = try { httpClient.getAgents() } catch (e: Exception) { emptyList() }
        httpAgents.map { a ->
            Agent(
                id = a.id,
                name = a.name,
                emoji = emojiForType(a.type),
                model = a.model,
                status = statusFromString(a.status),
                isOnline = a.status in listOf("active", "busy", "idle"),
                skills = emptyList() // TODO: cargar desde /api/agents/{id}/skills cuando esté disponible
            )
        }.also {
            AppLogger.i("AgentRepository", "Loaded ${it.size} agents")
        }
    }

    /** Activa o desactiva un skill en un agente (optimista local por ahora) */
    suspend fun toggleSkill(agentId: String, skillId: String, enabled: Boolean) {
        AppLogger.i("AgentRepository", "toggleSkill $agentId / $skillId -> $enabled")
        // TODO: PATCH /api/agents/{agentId}/skills/{skillId} cuando exista el endpoint
    }

    private fun emojiForType(type: String): String = when (type.lowercase()) {
        "assistant"  -> "🤖"
        "researcher" -> "🔍"
        "coder"      -> "💻"
        "analyst"    -> "📊"
        "writer"     -> "✍️"
        "manager"    -> "🧭"
        else         -> "🦞"
    }

    private fun statusFromString(s: String): AgentStatus = when (s.lowercase()) {
        "active", "running"  -> AgentStatus.ACTIVE
        "busy", "processing" -> AgentStatus.BUSY
        "error", "failed"    -> AgentStatus.ERROR
        else                 -> AgentStatus.IDLE
    }
}
