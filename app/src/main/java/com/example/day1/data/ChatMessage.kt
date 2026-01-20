package com.example.day1.data

import kotlinx.serialization.Serializable

data class ChatMessage(
    val id: String,
    val role: String, // "user" or "assistant"
    val content: String,
    val title: String? = null,
    val body: String? = null,
    val tags: List<String>? = null,
    val temperature: Double? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class AssistantResponse(
    val title: String,
    val body: String,
    val tags: List<String>
)

@Serializable
data class OpenRouterRequest(
    val model: String,
    val messages: List<MessageContent>,
    val temperature: Double = 1.0
)

@Serializable
data class MessageContent(
    val role: String,
    val content: String
)

@Serializable
data class OpenRouterResponse(
    val id: String? = null,
    val choices: List<Choice>? = null,
    val error: ErrorDetail? = null
)

@Serializable
data class Choice(
    val message: MessageResponse
)

@Serializable
data class MessageResponse(
    val role: String,
    val content: String
)

@Serializable
data class ErrorDetail(
    val message: String,
    val code: String? = null
)
