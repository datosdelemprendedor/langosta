package com.langosta.mission.web

import com.langosta.mission.data.api.dto.AgentDto
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

private val agents = mutableListOf<AgentDto>()

fun Routing.agentRoutes() {

    route("/agents") {

        get {
            call.respond(agents)
        }

        post {
            val agent = call.receive<AgentDto>()
            val newAgent = agent.copy(
                id = UUID.randomUUID().toString(),
                lastSeen = System.currentTimeMillis()
            )
            agents.add(newAgent)
            call.respond(HttpStatusCode.Created, newAgent)
        }

        get("/{id}") {
            val id = call.parameters["id"]
            val agent = agents.find { it.id == id }
            if (agent != null) call.respond(agent)
            else call.respond(HttpStatusCode.NotFound, "Agent not found")
        }

        patch("/{id}/status") {
            val id = call.parameters["id"]
            val body = call.receive<Map<String, String>>()
            val isOnline = body["isOnline"]?.toBoolean() ?: return@patch call.respond(
                HttpStatusCode.BadRequest, "Missing isOnline"
            )
            val index = agents.indexOfFirst { it.id == id }
            if (index == -1) return@patch call.respond(HttpStatusCode.NotFound, "Agent not found")
            agents[index] = agents[index].copy(
                isOnline = isOnline,
                lastSeen = System.currentTimeMillis()
            )
            call.respond(agents[index])
        }

        delete("/{id}") {
            val id = call.parameters["id"]
            val removed = agents.removeIf { it.id == id }
            if (removed) call.respond(HttpStatusCode.NoContent)
            else call.respond(HttpStatusCode.NotFound, "Agent not found")
        }
    }
}
