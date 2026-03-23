package com.langosta.mission.web

import com.langosta.mission.domain.model.Task
import com.langosta.mission.domain.model.TaskStatus
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

internal val tasks = mutableListOf<Task>()

fun Routing.taskRoutes() {

    route("/tasks") {

        get {
            call.respond(tasks)
        }

        post {
            val task = call.receive<Task>()
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
            val status = runCatching { TaskStatus.valueOf(newStatus) }.getOrElse {
                return@patch call.respond(HttpStatusCode.BadRequest, "Invalid status: $newStatus")
            }
            val index = tasks.indexOfFirst { it.id == id }
            if (index == -1) return@patch call.respond(HttpStatusCode.NotFound, "Task not found")
            tasks[index] = tasks[index].copy(
                status = status,
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
