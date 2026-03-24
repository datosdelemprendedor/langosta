package com.langosta.mission.desktop

/**
 * Secciones del menú lateral.
 * Inspirado en robsannaa/openclaw-mission-control:
 * agents, tasks, skills, channels, sessions, memory, cron, monitor, settings
 */
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

    CHANNELS("📡", "Channels"),
    CHANNELS_LIST("", "Configurar canales", CHANNELS),

    SESSIONS("💬", "Sesiones"),
    SESSIONS_LIST("", "Sesiones activas", SESSIONS),

    MEMORY("🧠", "Memoria"),
    MEMORY_LIST("", "Entradas de memoria", MEMORY),

    CRON("⏰", "Cron Jobs"),
    CRON_LIST("", "Tareas programadas", CRON),

    MONITOR("🔍", "Monitor"),
    MONITOR_LOG("", "Log de eventos", MONITOR),

    SETTINGS("⚙️", "Configuración");

    val isParent get() = parent == null
    val children get() = entries.filter { it.parent == this }
}
