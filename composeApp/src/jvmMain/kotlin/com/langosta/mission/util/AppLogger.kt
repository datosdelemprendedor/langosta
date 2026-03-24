package com.langosta.mission.util

object AppLogger {

    private var isDebug = true

    fun d(tag: String, message: String) {
        if (isDebug) println("[DEBUG][$tag] $message")
    }

    fun i(tag: String, message: String) {
        println("[INFO][$tag] $message")
    }

    fun w(tag: String, message: String) {
        println("[WARN][$tag] $message")
    }

    fun e(tag: String, message: String, error: Throwable? = null) {
        println("[ERROR][$tag] $message")
        error?.printStackTrace()
    }

    fun setDebug(enabled: Boolean) {
        isDebug = enabled
    }
}
