package com.langosta.mission.domain.model

import kotlinx.serialization.Serializable

// Memoria de un agente: viene de /api/memory?agentMemory=<agentId>
@Serializable
data class AgentMemoryFile(
    val agentId: String,
    val agentName: String,
    val isDefault: Boolean = false,
    val exists: Boolean = false,
    val fileName: String = "MEMORY.md",
    val content: String = "",
    val words: Int = 0,
    val size: Long = 0L,
    val mtime: String? = null,
    val vectorState: String = "unknown", // indexed | stale | not_indexed | unknown
    val dirty: Boolean = false,
    val indexedFiles: Int = 0,
    val indexedChunks: Int = 0
)

// Archivo de diario diario (memory/*.md)
@Serializable
data class MemoryJournalFile(
    val name: String,
    val date: String,
    val words: Int = 0,
    val size: Long = 0L,
    val mtime: String? = null,
    val vectorState: String = "unknown"
)
