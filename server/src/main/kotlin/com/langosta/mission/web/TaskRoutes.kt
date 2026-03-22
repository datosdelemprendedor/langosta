package com.langosta.mission.web

import com.langosta.mission.data.api.dto.TaskDto
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

private val tasks = mutableListOf<TaskDto>()

fun Routing.taskRoutes() {

    route("/tasks") {

        get {
            call.respond(tasks)
        }

        post {
            val task = call.receive<TaskDto>()
            val newTask = task.copy(
                id = UUID.randomUUID().toString(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            tasks.add(newTask)
            call.respond(HttpStatusCode.Created, newTask)
        }

        get("/{id}") {
            val id = call.parameters["id"]
            val task = tasks.find { it.id == id }
            if (task != null) call.respond(task)
            else call.respond(HttpStatusCode.NotFound, "Task not found")
        }

        patch("/{id}/status") {
            val id = call.parameters["id"]
            val body = call.receive<Map<String, String>>()
            val newStatus = body["status"] ?: return@patch call.respond(
                HttpStatusCode.BadRequest, "Missing status"
            )
            val index = tasks.indexOfFirst { it.id == id }
            if (index == -1) return@patch call.respond(HttpStatusCode.NotFound, "Task not found")
            tasks[index] = tasks[index].copy(
                status = newStatus,
                updatedAt = System.currentTimeMillis()
            )
            call.respond(tasks[index])
        }

        delete("/{id}") {
            val id = call.parameters["id"]
            val removed = tasks.removeIf { it.id == id }
            if (removed) call.respond(HttpStatusCode.NoContent)
            else call.respond(HttpStatusCode.NotFound, "Task not found")
        }
    }
}
