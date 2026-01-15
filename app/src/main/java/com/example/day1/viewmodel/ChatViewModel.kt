package com.example.day1.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.day1.BuildConfig
import com.example.day1.api.OpenRouterService
import com.example.day1.data.AssistantResponse
import com.example.day1.data.ChatMessage
import com.example.day1.data.MessageContent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.UUID

class ChatViewModel : ViewModel() {
    private val openRouterService = OpenRouterService()
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    // API ключ автоматически загружается из BuildConfig
    private val apiKey = BuildConfig.OPENROUTER_API_KEY
    
    // System prompt для получения ответов в формате JSON
    private val systemPrompt = """
        You are a helpful assistant that asks clarifying questions before providing the final answer.
        
        WORKFLOW:
        1. When the user asks a question, FIRST ask ONE clarifying question (if needed)
        2. After receiving the user's answer, you may ask ANOTHER clarifying question (max 3 total)
        3. Once you have enough information (or after 3 questions), provide the final detailed answer
        
        IMPORTANT: Ask questions ONE AT A TIME, not multiple questions in one message.
        
        You ALWAYS respond in valid JSON format with this structure:
        {
          "title": "Brief title",
          "body": "Your question OR detailed answer",
          "tags": ["tag1", "tag2"]
        }
        
        FOR CLARIFYING QUESTIONS:
        - title: should be "Уточняющий вопрос" (or "Clarifying question" in user's language)
        - body: should contain ONE specific question
        - tags: should include "question" as the first tag
        
        FOR FINAL ANSWER:
        - title: should be a brief summary of the answer (max 60 characters)
        - body: should contain the detailed, comprehensive answer
        - tags: should be 2-5 relevant topic tags (NOT including "question")
        
        Rules:
        - Always return ONLY valid JSON, no additional text
        - Do not use markdown code blocks or any formatting
        - Ask a maximum of 3 clarifying questions total
        - All text must be in the same language as the user's question
        - ONE question per message, then wait for the user's response
    """.trimIndent()
    
    init {
        // Проверка наличия API ключа при инициализации
        if (apiKey.isEmpty()) {
            _error.value = "API ключ не настроен. Добавьте OPENROUTER_API_KEY в local.properties"
        }
    }
    
    fun sendMessage(text: String) {
        if (text.isBlank()) {
            _error.value = "Введите сообщение"
            return
        }
        
        if (apiKey.isEmpty()) {
            _error.value = "API ключ не настроен. Добавьте OPENROUTER_API_KEY в local.properties"
            return
        }
        
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = "user",
            content = text
        )
        
        _messages.value = _messages.value + userMessage
        _isLoading.value = true
        _error.value = null
        
        viewModelScope.launch {
            // Добавляем system prompt в начало истории сообщений
            val systemMessage = MessageContent(
                role = "system",
                content = systemPrompt
            )
            
            val messageHistory = listOf(systemMessage) + _messages.value.map { msg ->
                MessageContent(
                    role = msg.role,
                    content = msg.content
                )
            }
            
            openRouterService.sendMessage(messageHistory, apiKey)
                .onSuccess { responseText ->
                    try {
                        // Парсим JSON ответ
                        val parsedResponse = json.decodeFromString<AssistantResponse>(responseText)
                        
                        val assistantMessage = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            role = "assistant",
                            content = responseText,
                            title = parsedResponse.title,
                            body = parsedResponse.body,
                            tags = parsedResponse.tags
                        )
                        _messages.value = _messages.value + assistantMessage
                    } catch (e: Exception) {
                        // Если не удалось распарсить JSON, сохраняем как обычное сообщение
                        val assistantMessage = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            role = "assistant",
                            content = responseText
                        )
                        _messages.value = _messages.value + assistantMessage
                        _error.value = "Предупреждение: ответ не в ожидаемом формате JSON"
                    }
                }
                .onFailure { exception ->
                    _error.value = "Ошибка: ${exception.message}"
                }
            
            _isLoading.value = false
        }
    }
    
    fun clearError() {
        _error.value = null
    }
    
    fun clearChat() {
        _messages.value = emptyList()
        _error.value = null
    }
    
    override fun onCleared() {
        super.onCleared()
        openRouterService.close()
    }
}
