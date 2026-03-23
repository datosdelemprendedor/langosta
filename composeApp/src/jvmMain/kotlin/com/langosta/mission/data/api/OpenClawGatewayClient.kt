package com.langosta.mission.data.api

import com.langosta.mission.domain.model.*
import com.langosta.mission.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class OpenClawGatewayClient {

    private val openclawPath = "/home/curso/.npm-global/bin/openclaw"

    private fun runOpenClawCommand(vararg args: String): String? {
        try {
            val command = "$openclawPath ${args.joinToString(" ")}"
            val allArgs = listOf("cmd.exe", "/c", "wsl", "-d", "Ubuntu", "-e", "bash", "-lc", command)
            val processBuilder = ProcessBuilder(allArgs)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .start()

            val output = BufferedReader(InputStreamReader(processBuilder.inputStream)).readText()
            val exited = processBuilder.waitFor(60, TimeUnit.SECONDS)

            if (!exited || processBuilder.exitValue() != 0) {
                AppLogger.w("OpenClawGateway", "Command failed: exit ${processBuilder.exitValue()}")
                return null
            }
            return output
        } catch (e: Exception) {
            AppLogger.e("OpenClawGateway", "Command exception: ${e.message}")
            return null
        }
    }

    suspend fun health(): GatewayStatus {
        val output = runOpenClawCommand("gateway", "call", "health", "--timeout", "30000", "--json")
        if (output == null) {
            return GatewayStatus(status = "offline", mode = "unknown")
        }
        val result = parseJsonOutput(output)
        return if (result is JsonObject) {
            val ok = result["ok"]?.jsonPrimitive?.boolean == true
            GatewayStatus(
                status = if (ok) "online" else "offline",
                mode = "local",
                version = "2026.3.13",
                uptime = null
            )
        } else {
            GatewayStatus(status = "offline", mode = "unknown")
        }
    }

    suspend fun listSessions(): List<SessionInfo> {
        val output = runOpenClawCommand("gateway", "call", "health", "--timeout", "30000", "--json")
        if (output == null) {
            return emptyList()
        }
        val result = parseJsonOutput(output)
        return if (result is JsonObject) {
            val sessionsObj = result["sessions"]
            if (sessionsObj is JsonObject) {
                val sessionsArray = sessionsObj["recent"]
                if (sessionsArray is JsonArray) {
                    sessionsArray.mapNotNull { item ->
                        val obj = item.jsonObject
                        SessionInfo(
                            sessionKey = obj["key"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                            agentId = "main",
                            model = "qwen3.5-122b-a10b",
                            status = "active",
                            createdAt = 0L,
                            updatedAt = obj["updatedAt"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                            totalTokens = 0L,
                            messagesCount = 0
                        )
                    }
                } else emptyList()
            } else emptyList()
        } else {
            emptyList()
        }
    }

    suspend fun getBootstrapConfig(): OpenClawBootstrapConfig {
        val output = runOpenClawCommand("gateway", "call", "config.get", "--params", "{\"key\":\"controlUi\"}", "--json")
        if (output == null) {
            return OpenClawBootstrapConfig(basePath = "", assistantName = "OpenClaw", assistantAvatar = "", assistantAgentId = "main")
        }
        val result = parseJsonOutput(output)
        return if (result is JsonObject) {
            OpenClawBootstrapConfig(
                basePath = result["basePath"]?.jsonPrimitive?.content ?: "",
                assistantName = result["assistantName"]?.jsonPrimitive?.content ?: "OpenClaw",
                assistantAvatar = result["assistantAvatar"]?.jsonPrimitive?.content ?: "",
                assistantAgentId = result["assistantAgentId"]?.jsonPrimitive?.content ?: "main",
                serverVersion = result["serverVersion"]?.jsonPrimitive?.content
            )
        } else {
            OpenClawBootstrapConfig(basePath = "", assistantName = "OpenClaw", assistantAvatar = "", assistantAgentId = "main")
        }
    }

    suspend fun sendMessage(agentId: String, message: String): String {
        val params = "{\"message\":\"$message\",\"sessionKey\":\"agent:$agentId:main\"}"
        val output = runOpenClawCommand("gateway", "call", "chat.send", "--params", params, "--json")
        if (output == null) {
            return "Error: Command failed"
        }
        val result = parseJsonOutput(output)
        return result?.jsonObject?.get("content")?.jsonPrimitive?.content
            ?: result?.jsonObject?.get("text")?.jsonPrimitive?.content
            ?: "Message sent"
    }

    private fun parseJsonOutput(raw: String): JsonElement? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        val objectStart = trimmed.indexOf('{')
        val arrayStart = trimmed.indexOf('[')

        if (objectStart < 0 && arrayStart < 0) return null

        val start = if (objectStart >= 0 && arrayStart >= 0) {
            if (objectStart < arrayStart) objectStart else arrayStart
        } else objectStart.coerceAtLeast(arrayStart)

        val end = if (objectStart >= 0) {
            val objEnd = trimmed.lastIndexOf('}')
            if (arrayStart >= 0) {
                val arrEnd = trimmed.lastIndexOf(']')
                maxOf(objEnd, arrEnd)
            } else objEnd
        } else trimmed.lastIndexOf(']')

        if (start < 0 || end < start) return null

        return try {
            Json.parseToJsonElement(trimmed.slice(start..end))
        } catch (e: Exception) {
            null
        }
    }
}
