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
    val timestamp: Long = System.currentTimeMillis(),
    val modelName: String? = null,
    val responseTimeMs: Long? = null,
    val tokensUsed: Int? = null,
    val cost: Double? = null
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
    val error: ErrorDetail? = null,
    val usage: Usage? = null,
    val model: String? = null
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

@Serializable
data class Usage(
    val prompt_tokens: Int? = null,
    val completion_tokens: Int? = null,
    val total_tokens: Int? = null,
    val cost: Double? = null,
)

// Модель AI с настройками стоимости
data class AIModel(
    val id: String,
    val displayName: String,
    val costPerMillionPromptTokens: Double,
    val costPerMillionCompletionTokens: Double
)

// Результат запроса к модели с метриками
data class ModelResponse(
    val modelName: String,
    val content: String,
    val responseTimeMs: Long,
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val cost: Double
)
