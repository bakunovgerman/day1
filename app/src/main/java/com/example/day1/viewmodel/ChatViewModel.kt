package com.example.day1.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.day1.BuildConfig
import com.example.day1.R
import com.example.day1.api.OpenRouterService
import com.example.day1.data.AIModel
import com.example.day1.data.AssistantResponse
import com.example.day1.data.ChatMessage
import com.example.day1.data.FunctionDefinition
import com.example.day1.data.MessageContent
import com.example.day1.data.ToolDefinition
import com.example.day1.db.AppDatabase
import com.example.day1.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.example.mcp.McpClient
import org.example.mcp.McpConfig
import org.example.mcp.models.Tool
import java.util.UUID

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val openRouterService = OpenRouterService()

    // Инициализация БД и репозитория
    private val database = AppDatabase.getDatabase(application)
    private val chatRepository = ChatRepository(
        chatMessageDao = database.chatMessageDao(),
        summaryDao = database.summaryDao()
    )

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

    // Промпт выше контекста
    private val _usePromptAboveContext = MutableStateFlow(false)
    val usePromptAboveContext: StateFlow<Boolean> = _usePromptAboveContext.asStateFlow()

    // Доступные модели
    private val _availableModels = MutableStateFlow<List<AIModel>>(emptyList())
    val availableModels: StateFlow<List<AIModel>> = _availableModels.asStateFlow()

    // Выбранные модели для отправки запросов (по умолчанию первые две)
    private val _selectedModels = MutableStateFlow<List<AIModel>>(emptyList())
    val selectedModels: StateFlow<List<AIModel>> = _selectedModels.asStateFlow()

    // Сжатие контекста
    private val _useContextCompression = MutableStateFlow(false)
    val useContextCompression: StateFlow<Boolean> = _useContextCompression.asStateFlow()

    // Состояние генерации summary
    private val _isGeneratingSummary = MutableStateFlow(false)
    val isGeneratingSummary: StateFlow<Boolean> = _isGeneratingSummary.asStateFlow()

    // Сохраненный summary контекста
    private var contextSummary: String? = null

    // Общее число токенов за диалог
    private val _totalTokens = MutableStateFlow(0)
    val totalTokens: StateFlow<Int> = _totalTokens.asStateFlow()
    
    // MCP Client для работы с tools
    val mcpClient =
        McpClient(config = McpConfig(url = "https://fittable-deeanna-noneditorially.ngrok-free.dev/mcp"))
    
    // Список доступных tools от MCP
    private val _availableTools = MutableStateFlow<List<ToolDefinition>>(emptyList())
    val availableTools: StateFlow<List<ToolDefinition>> = _availableTools.asStateFlow()
    
    // Максимальное число итераций tool calling (защита от бесконечных циклов)
    private val maxToolCallIterations = 5

    init {
        // Проверка наличия API ключа при инициализации
        if (apiKey.isEmpty()) {
            _error.value = "API ключ не настроен. Добавьте OPENROUTER_API_KEY в local.properties"
        }

        // Загружаем список доступных моделей
        _availableModels.value = openRouterService.getAvailableModels()

        // По умолчанию выбираем первые две модели
        _selectedModels.value = _availableModels.value.take(2)

        // Загружаем историю чата из БД
        loadChatHistory()
        
        // Загружаем список tools от MCP сервера
        loadMcpTools()
    }
    
    private fun loadMcpTools() {
        viewModelScope.launch {
            try {
                // Инициализируем MCP соединение
                mcpClient.initialize()
                
                // Получаем список tools
                val toolsResult = mcpClient.listTools()
                
                // Конвертируем MCP tools в формат OpenAI
                val toolDefinitions = toolsResult.tools.map { mcpTool ->
                    convertMcpToolToOpenAI(mcpTool)
                }
                
                _availableTools.value = toolDefinitions
                
                android.util.Log.d("ChatViewModel", "Loaded ${toolDefinitions.size} tools from MCP")
                toolDefinitions.forEach { tool ->
                    android.util.Log.d("ChatViewModel", "  - ${tool.function.name}: ${tool.function.description}")
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed to load MCP tools", e)
                _error.value = "Не удалось загрузить tools от MCP: ${e.message}"
            }
        }
    }
    
    private fun convertMcpToolToOpenAI(mcpTool: Tool): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = mcpTool.name,
                description = mcpTool.description,
                parameters = mcpTool.inputSchema
            )
        )
    }
    
    private suspend fun sendMessageWithToolCalling(initialMessages: List<MessageContent>) {
        var conversationMessages = initialMessages.toMutableList()
        var iteration = 0
        
        while (iteration < maxToolCallIterations) {
            iteration++
            
            android.util.Log.d("ChatViewModel", "Tool calling iteration $iteration")
            
            // Отправляем запросы ко всем выбранным моделям одновременно
            val responses = openRouterService.sendMessageToMultipleModels(
                messages = conversationMessages,
                apiKey = apiKey,
                models = _selectedModels.value,
                temperature = _temperature.value,
                tools = if (_availableTools.value.isNotEmpty()) _availableTools.value else null
            )
            
            var hasToolCalls = false
            
            // Обрабатываем ответы от каждой модели
            responses.forEach { result ->
                result.onSuccess { modelResponse ->
                    // Проверяем, есть ли tool calls
                    if (!modelResponse.toolCalls.isNullOrEmpty()) {
                        hasToolCalls = true
                        
                        android.util.Log.d("ChatViewModel", "Model ${modelResponse.modelName} requested ${modelResponse.toolCalls.size} tool calls")
                        
                        // Добавляем сообщение ассистента с tool calls в историю
                        conversationMessages.add(
                            MessageContent(
                                role = "assistant",
                                content = modelResponse.content,
                                tool_calls = modelResponse.toolCalls
                            )
                        )
                        
                        // Выполняем каждый tool call
                        modelResponse.toolCalls.forEach { toolCall ->
                            val toolResult = executeToolCall(toolCall)
                            
                            // Добавляем результат tool call в историю
                            conversationMessages.add(
                                MessageContent(
                                    role = "tool",
                                    content = toolResult,
                                    tool_call_id = toolCall.id,
                                    name = toolCall.function.name
                                )
                            )
                        }
                        
                        // Обновляем токены
                        _totalTokens.value += modelResponse.totalTokens
                    } else {
                        // Нет tool calls - это финальный ответ
                        saveFinalResponse(modelResponse)
                    }
                }
                .onFailure { exception ->
                    _error.value = "Ошибка: ${exception.message}"
                }
            }
            
            // Если ни одна модель не запросила tool calls, выходим из цикла
            if (!hasToolCalls) {
                break
            }
        }
        
        if (iteration >= maxToolCallIterations) {
            android.util.Log.w("ChatViewModel", "Достигнут максимум итераций tool calling")
        }
    }
    
    private suspend fun executeToolCall(toolCall: com.example.day1.data.ToolCall): String {
        return try {
            android.util.Log.d("ChatViewModel", "Executing tool: ${toolCall.function.name}")
            android.util.Log.d("ChatViewModel", "Arguments: ${toolCall.function.arguments}")
            
            // Парсим аргументы
            val arguments = json.parseToJsonElement(toolCall.function.arguments).jsonObject
            
            // Вызываем tool через MCP
            val result = mcpClient.callTool(
                name = toolCall.function.name,
                arguments = arguments
            )
            
            // Конвертируем результат в строку
            val resultText = result.content.joinToString("\n") { content ->
                content.text ?: content.data ?: "No content"
            }
            
            android.util.Log.d("ChatViewModel", "Tool result: $resultText")
            
            resultText
        } catch (e: Exception) {
            android.util.Log.e("ChatViewModel", "Error executing tool ${toolCall.function.name}", e)
            "Error executing tool: ${e.message}"
        }
    }
    
    private suspend fun saveFinalResponse(modelResponse: com.example.day1.data.ModelResponse) {
        try {
            // Пытаемся распарсить JSON ответ
            val parsedResponse = modelResponse.content?.let { 
                json.decodeFromString<AssistantResponse>(it) 
            }

            val assistantMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                role = "assistant",
                content = modelResponse.content ?: "",
                title = parsedResponse?.title,
                body = parsedResponse?.body,
                tags = parsedResponse?.tags,
                temperature = _temperature.value,
                modelName = modelResponse.modelName,
                responseTimeMs = modelResponse.responseTimeMs,
                tokensUsed = modelResponse.totalTokens,
                promptTokens = modelResponse.promptTokens,
                completionTokens = modelResponse.completionTokens,
                cost = modelResponse.cost
            )
            // Сохраняем сообщение ассистента в БД
            chatRepository.saveMessage(assistantMessage)
            // Обновляем общее число токенов
            _totalTokens.value += modelResponse.totalTokens
        } catch (e: Exception) {
            // Если не удалось распарсить JSON, сохраняем как обычное сообщение
            val assistantMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                role = "assistant",
                content = modelResponse.content ?: "",
                temperature = _temperature.value,
                modelName = modelResponse.modelName,
                responseTimeMs = modelResponse.responseTimeMs,
                tokensUsed = modelResponse.totalTokens,
                promptTokens = modelResponse.promptTokens,
                completionTokens = modelResponse.completionTokens,
                cost = modelResponse.cost
            )
            // Сохраняем сообщение ассистента в БД
            chatRepository.saveMessage(assistantMessage)
            // Обновляем общее число токенов
            _totalTokens.value += modelResponse.totalTokens
        }
    }

    private fun loadChatHistory() {
        viewModelScope.launch {
            // Подписываемся на изменения в БД
            chatRepository.getAllMessages().collect { messages ->
                _messages.value = messages
                // Пересчитываем общее количество токенов
                _totalTokens.value = messages.sumOf { it.tokensUsed ?: 0 }
            }
        }

        // Загружаем текущую суммаризацию
        viewModelScope.launch {
            contextSummary = chatRepository.getCurrentSummary()
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

        if (_selectedModels.value.isEmpty()) {
            _error.value = "Выберите хотя бы одну модель"
            return
        }

        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = "user",
            content = text
        )

        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            // Сохраняем сообщение пользователя в БД
            chatRepository.saveMessage(userMessage)

            // Получаем сообщения, которые нужно отправлять (needSend = true)
            val messagesToSend = withContext(Dispatchers.IO) {
                chatRepository.getMessagesForSending()
            }

            // Формируем список сообщений для отправки
            val messageHistory = if (_useContextCompression.value && contextSummary != null) {
                // Используем summary как system prompt
                val systemMessage = MessageContent(
                    role = "system",
                    content = if (_systemPrompt.value.isNotEmpty()) {
                        "${_systemPrompt.value}\n\nКонтекст предыдущего диалога: $contextSummary"
                    } else {
                        "Контекст предыдущего диалога: $contextSummary"
                    }
                )

                // Отправляем только сообщения с needSend = true
                listOf(systemMessage) + messagesToSend.map { msg ->
                    MessageContent(
                        role = msg.role,
                        content = msg.content
                    )
                }
            } else {
                // Обычная логика без сжатия - отправляем всю историю
                val systemMessage = MessageContent(
                    role = "system",
                    content = _systemPrompt.value
                )

                listOf(systemMessage) + _messages.value.map { msg ->
                    MessageContent(
                        role = msg.role,
                        content = msg.content
                    )
                }
            }

            // Отправляем запросы с поддержкой tool calling
            sendMessageWithToolCalling(messageHistory)

            // ПОСЛЕ получения всех ответов от LLM проверяем, нужно ли генерировать summary
            val messageCount = chatRepository.getMessagesCount()

            // Каждые 11 сообщений (при 11, 22, 33, 44 и т.д.) генерируем новый summary
            if (_useContextCompression.value && messageCount % 11 == 0) {
                _isGeneratingSummary.value = true

                // Генерируем summary всех сообщений (включая только что добавленные ответы от LLM)
                val allMessages = _messages.value
                val dialogText =
                    allMessages.joinToString("\n\n") { msg ->
                        "${if (msg.role == "user") "Пользователь" else "Ассистент"}: ${msg.content}"
                    }

                val summaryResult = openRouterService.generateSummary(dialogText, apiKey)
                summaryResult.onSuccess { summaryResponse ->
                    contextSummary = summaryResponse.summary
                    // Сохраняем суммаризацию в БД и помечаем все сообщения как needSend = false
                    chatRepository.saveSummary(summaryResponse)
                    // Добавляем токены summary к общему счетчику
                    _totalTokens.value += summaryResponse.totalTokens
                    _isGeneratingSummary.value = false
                }
                    .onFailure { exception ->
                        _error.value = "Ошибка генерации summary: ${exception.message}"
                        _isGeneratingSummary.value = false
                    }
            }

            _isLoading.value = false
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun clearChat() {
        viewModelScope.launch {
            // Очищаем БД
            chatRepository.clearAllData()
        }
        _error.value = null
        contextSummary = null // Сбрасываем summary при очистке чата
        _totalTokens.value = 0 // Сбрасываем счетчик токенов
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

    fun togglePromptAboveContext(enabled: Boolean) {
        _usePromptAboveContext.value = enabled
    }

    fun toggleContextCompression(enabled: Boolean) {
        _useContextCompression.value = enabled
        if (!enabled) {
            contextSummary = null // Сбрасываем summary при выключении
        }
    }

    fun sendPromptFromFile() {
        try {
            val inputStream =
                getApplication<Application>().resources.openRawResource(R.raw.prompt_above_context)
            val promptText = inputStream.bufferedReader().use { it.readText() }
            sendMessage(promptText)
        } catch (e: Exception) {
            _error.value = "Ошибка при отправке промпта из файла: ${e.message}"
        }
    }

    override fun onCleared() {
        super.onCleared()
        openRouterService.close()
        mcpClient.close()
    }
}
