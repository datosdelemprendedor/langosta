package com.langosta

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
