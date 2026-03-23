package com.langosta.mission.data

import java.io.File
import java.sql.DriverManager

object TaskHistoryDatabase {

    private val dbPath: String by lazy {
        val dir = File(System.getProperty("user.home"), ".langosta")
        dir.mkdirs()
        File(dir, "history.db").absolutePath
    }

    private fun connect() = DriverManager.getConnection("jdbc:sqlite:$dbPath")

    fun init() {
        connect().use { conn ->
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS task_history (
                    id             INTEGER PRIMARY KEY AUTOINCREMENT,
                    task_id        TEXT NOT NULL,
                    title          TEXT NOT NULL,
                    agent_name     TEXT NOT NULL,
                    started_at     INTEGER NOT NULL,
                    completed_at   INTEGER NOT NULL,
                    duration_secs  INTEGER NOT NULL,
                    result         TEXT NOT NULL
                )
            """)
        }
    }

    fun insert(entry: TaskHistoryEntry) {
        connect().use { conn ->
            conn.prepareStatement("""
                INSERT INTO task_history (task_id, title, agent_name, started_at, completed_at, duration_secs, result)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """).apply {
                setString(1, entry.taskId)
                setString(2, entry.title)
                setString(3, entry.agentName)
                setLong(4, entry.startedAt)
                setLong(5, entry.completedAt)
                setLong(6, entry.durationSeconds)
                setString(7, entry.result)
                executeUpdate()
            }
        }
    }

    fun getAll(): List<TaskHistoryEntry> {
        val list = mutableListOf<TaskHistoryEntry>()
        connect().use { conn ->
            val rs = conn.createStatement()
                .executeQuery("SELECT * FROM task_history ORDER BY completed_at DESC")
            while (rs.next()) {
                list.add(TaskHistoryEntry(
                    id            = rs.getInt("id"),
                    taskId        = rs.getString("task_id"),
                    title         = rs.getString("title"),
                    agentName     = rs.getString("agent_name"),
                    startedAt     = rs.getLong("started_at"),
                    completedAt   = rs.getLong("completed_at"),
                    durationSeconds = rs.getLong("duration_secs"),
                    result        = rs.getString("result")
                ))
            }
        }
        return list
    }

    fun clearAll() {
        connect().use { it.createStatement().execute("DELETE FROM task_history") }
    }
}
