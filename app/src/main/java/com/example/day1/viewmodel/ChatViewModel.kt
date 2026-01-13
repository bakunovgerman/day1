package com.example.day1.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.day1.BuildConfig
import com.example.day1.api.OpenRouterService
import com.example.day1.data.ChatMessage
import com.example.day1.data.MessageContent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel : ViewModel() {
    private val openRouterService = OpenRouterService()
    
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    // API ключ автоматически загружается из BuildConfig
    private val apiKey = BuildConfig.OPENROUTER_API_KEY
    
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
            val messageHistory = _messages.value.map { msg ->
                MessageContent(
                    role = msg.role,
                    content = msg.content
                )
            }
            
            openRouterService.sendMessage(messageHistory, apiKey)
                .onSuccess { responseText ->
                    val assistantMessage = ChatMessage(
                        id = UUID.randomUUID().toString(),
                        role = "assistant",
                        content = responseText
                    )
                    _messages.value = _messages.value + assistantMessage
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
