package com.langosta.mission.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChatCompletionResponse(
    val choices: List<Choice>
) {
    @Serializable
    data class Choice(val message: Message)

    @Serializable
    data class Message(val content: String)
}
