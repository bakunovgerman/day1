package com.example.day1.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.day1.BuildConfig
import com.example.day1.api.OpenRouterService
import com.example.day1.data.AIModel
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

    // System prompt для получения ответов в формате JSON (изменяемый)
    private val defaultSystemPrompt = ""

    private val _systemPrompt = MutableStateFlow(defaultSystemPrompt)
    val systemPrompt: StateFlow<String> = _systemPrompt.asStateFlow()

    private val _temperature = MutableStateFlow(1.0)
    val temperature: StateFlow<Double> = _temperature.asStateFlow()

    // Доступные модели
    private val _availableModels = MutableStateFlow<List<AIModel>>(emptyList())
    val availableModels: StateFlow<List<AIModel>> = _availableModels.asStateFlow()

    // Выбранные модели для отправки запросов (по умолчанию первые две)
    private val _selectedModels = MutableStateFlow<List<AIModel>>(emptyList())
    val selectedModels: StateFlow<List<AIModel>> = _selectedModels.asStateFlow()

    init {
        // Проверка наличия API ключа при инициализации
        if (apiKey.isEmpty()) {
            _error.value = "API ключ не настроен. Добавьте OPENROUTER_API_KEY в local.properties"
        }
        
        // Загружаем список доступных моделей
        _availableModels.value = openRouterService.getAvailableModels()
        
        // По умолчанию выбираем первые две модели
        _selectedModels.value = _availableModels.value.take(2)
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

        if (_selectedModels.value.isEmpty()) {
            _error.value = "Выберите хотя бы одну модель"
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
                content = _systemPrompt.value
            )

            val messageHistory = listOf(systemMessage) + _messages.value.map { msg ->
                MessageContent(
                    role = msg.role,
                    content = msg.content
                )
            }

            // Отправляем запросы ко всем выбранным моделям одновременно
            val responses = openRouterService.sendMessageToMultipleModels(
                messages = messageHistory,
                apiKey = apiKey,
                models = _selectedModels.value,
                temperature = _temperature.value
            )

            // Обрабатываем ответы от каждой модели
            responses.forEach { result ->
                result.onSuccess { modelResponse ->
                    try {
                        // Пытаемся распарсить JSON ответ
                        val parsedResponse = json.decodeFromString<AssistantResponse>(modelResponse.content)

                        val assistantMessage = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            role = "assistant",
                            content = modelResponse.content,
                            title = parsedResponse.title,
                            body = parsedResponse.body,
                            tags = parsedResponse.tags,
                            temperature = _temperature.value,
                            modelName = modelResponse.modelName,
                            responseTimeMs = modelResponse.responseTimeMs,
                            tokensUsed = modelResponse.totalTokens,
                            cost = modelResponse.cost
                        )
                        _messages.value = _messages.value + assistantMessage
                    } catch (e: Exception) {
                        // Если не удалось распарсить JSON, сохраняем как обычное сообщение
                        val assistantMessage = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            role = "assistant",
                            content = modelResponse.content,
                            temperature = _temperature.value,
                            modelName = modelResponse.modelName,
                            responseTimeMs = modelResponse.responseTimeMs,
                            tokensUsed = modelResponse.totalTokens,
                            cost = modelResponse.cost
                        )
                        _messages.value = _messages.value + assistantMessage
                    }
                }
                .onFailure { exception ->
                    _error.value = "Ошибка: ${exception.message}"
                }
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

    fun updateSystemPrompt(newPrompt: String) {
        _systemPrompt.value = newPrompt
    }

    fun resetSystemPromptToDefault() {
        _systemPrompt.value = defaultSystemPrompt
    }

    fun updateTemperature(newTemperature: Double) {
        _temperature.value = newTemperature.coerceIn(0.0, 2.0)
    }

    fun toggleModelSelection(model: AIModel) {
        val currentSelected = _selectedModels.value.toMutableList()
        if (currentSelected.contains(model)) {
            currentSelected.remove(model)
        } else {
            currentSelected.add(model)
        }
        _selectedModels.value = currentSelected
    }

    override fun onCleared() {
        super.onCleared()
        openRouterService.close()
    }
}
