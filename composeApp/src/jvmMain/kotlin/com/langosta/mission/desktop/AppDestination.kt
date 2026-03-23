package com.langosta.mission.desktop

enum class AppDestination(
    val icon: String,
    val label: String,
    val parent: AppDestination? = null
) {
    // Dashboard
    DASHBOARD("📊", "Dashboard"),

    // Agentes
    AGENTS("🤖", "Agentes"),
    AGENTS_LIST("", "Lista de agentes", AGENTS),
    AGENTS_REGISTER("", "Registrar agente", AGENTS),

    // Tareas
    TASKS("📋", "Tareas"),
    TASKS_BOARD("", "Board", TASKS),
    TASKS_HISTORY("", "Historial", TASKS),

    // Monitor
    MONITOR("📡", "Monitor"),
    MONITOR_LOG("", "Log en tiempo real", MONITOR),
    MONITOR_WEBSOCKET("", "WebSocket status", MONITOR),

    // Configuración
    SETTINGS("⚙️", "Configuración"),
    SETTINGS_SERVER("", "Servidor", SETTINGS),
    SETTINGS_PREFERENCES("", "Preferencias", SETTINGS);

    val isParent get() = parent == null
    val children get() = entries.filter { it.parent == this }
}
