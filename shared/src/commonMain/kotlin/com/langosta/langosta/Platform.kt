package com.langosta.langosta

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform