package com.langosta.mission.desktop

enum class AppDestination(
    val icon: String,
    val label: String,
    val parent: AppDestination? = null
) {
    DASHBOARD("📊", "Dashboard"),

    AGENTS("🤖", "Agentes"),
    AGENTS_LIST("", "Lista de agentes", AGENTS),

    TASKS("📋", "Tareas"),
    TASKS_BOARD("", "Board", TASKS),

    MONITOR("📡", "Monitor"),
    MONITOR_LOG("", "Log de eventos", MONITOR),

    SETTINGS("⚙️", "Configuración");

    val isParent get() = parent == null
    val children get() = entries.filter { it.parent == this }
}
