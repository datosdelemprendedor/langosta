package com.langosta.mission.domain.model

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform