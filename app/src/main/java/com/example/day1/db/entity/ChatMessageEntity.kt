package com.example.day1.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey
    val id: String,
    val role: String, // "user" or "assistant"
    val content: String,
    val title: String? = null,
    val body: String? = null,
    val tags: String? = null, // JSON строка списка тегов
    val temperature: Double? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val modelName: String? = null,
    val responseTimeMs: Long? = null,
    val tokensUsed: Int? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val cost: Double? = null,
    val needSend: Boolean = true // Если false, сообщение не отправляется в LLM вместе с суммаризацией
)
