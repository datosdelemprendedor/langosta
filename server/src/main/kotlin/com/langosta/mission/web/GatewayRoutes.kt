package com.langosta.mission.web

import com.langosta.mission.domain.model.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

val incidentBroadcast = MutableSharedFlow<String>(extraBufferCapacity = 64)

fun Routing.gatewayRoutes() {

    route("/__openclaw") {

        get("/control-ui-config.json") {
            call.respond(
                OpenClawBootstrapConfig(
                    basePath = "/__openclaw",
                    assistantName = "OpenCLAW",
                    assistantAvatar = "",
                    assistantAgentId = "main",
                    serverVersion = "1.0.0"
                )
            )
        }

        get("/dashboard") {
            val activeAgents = agents.filter { it.isOnline }
            val queueSize = tasks.count { it.status.name == "PENDING" }
            val errors = tasks.count { it.status.name == "FAILED" }

            call.respond(
                DashboardState(
                    gateway = GatewayStatus(
                        status = "online",
                        mode = "Gateway",
                        version = "1.0.0",
                        uptime = System.currentTimeMillis()
                    ),
                    sessions = SessionsInfo(
                        active = activeAgents.size,
                        total = agents.size
                    ),
                    agents = activeAgents.map { agent ->
                        AgentNode(
                            id = agent.id,
                            name = agent.name,
                            type = "direct",
                            model = agent.model,
                            tokensIn = 0L,
                            tokensOut = 0L,
                            utilization = if (agent.currentTaskId != null) 100 else 0,
                            lastSeen = agent.lastSeen.toString()
                        )
                    },
                    system = SystemMetrics(
                        memoryPercent = (Runtime.getRuntime().let {
                            ((it.totalMemory() - it.freeMemory()) * 100 / it.maxMemory()).toInt()
                        }),
                        diskPercent = 0,
                        errors = errors,
                        queueSize = queueSize
                    ),
                    auditEvents24h = tasks.size,
                    loginFailures24h = 0
                )
            )
        }
    }

    webSocket("/ws/broadcast") {
        val job = launch {
            incidentBroadcast.collect { event ->
                send(Frame.Text(event))
            }
        }
        try {
            for (frame in incoming) { /* escucha sin procesar */ }
        } catch (e: ClosedReceiveChannelException) {
            // cliente desconectado
        } finally {
            job.cancel()
        }
    }
}
