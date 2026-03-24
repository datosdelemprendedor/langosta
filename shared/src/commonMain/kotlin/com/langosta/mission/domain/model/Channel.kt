package com.langosta.mission.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Channel(
    val id: String,             // "telegram" | "discord"
    val label: String,
    val icon: String,
    val enabled: Boolean = false,
    val configured: Boolean = false,
    val connected: Boolean = false,
    val botUsername: String? = null,
    val error: String? = null,
    val dmPolicy: String = "pairing",
    val groupPolicy: String = "disabled",
    val accounts: List<String> = emptyList(),
    val tokenLabel: String = "Bot Token",
    val tokenPlaceholder: String = "",
    val hint: String = "",
    val setupCommand: String = ""
)

val CHANNEL_CATALOG = listOf(
    Channel(
        id = "telegram",
        label = "Telegram",
        icon = "✈️",
        tokenLabel = "Bot Token",
        tokenPlaceholder = "123456:ABC-DEF1234ghIkl...",
        hint = "Crea un bot con @BotFather en Telegram y pega el token aquí.",
        setupCommand = "openclaw channels add --channel telegram --token <TOKEN>"
    ),
    Channel(
        id = "discord",
        label = "Discord",
        icon = "💬",
        tokenLabel = "Bot Token",
        tokenPlaceholder = "MTIzNDU2Nzg5MDEyMzQ1...",
        hint = "Crea un bot en el Discord Developer Portal, activa Message Content Intent, y pega el token.",
        setupCommand = "openclaw channels add --channel discord --token <TOKEN>"
    )
)
