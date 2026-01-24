package com.example.day1.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.day1.data.ChatMessage
import com.example.day1.data.AIModel
import com.example.day1.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = viewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val systemPrompt by viewModel.systemPrompt.collectAsState()
    val temperature by viewModel.temperature.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val selectedModels by viewModel.selectedModels.collectAsState()
    
    var messageText by remember { mutableStateOf("") }
    var showSystemPromptDialog by remember { mutableStateOf(false) }
    
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Чат с AI агентом") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = { 
                        showSystemPromptDialog = true
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = "Настроить System Prompt")
                    }
                    IconButton(onClick = { 
                        viewModel.clearChat()
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = "Очистить чат")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Error message
            error?.let { errorMessage ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearError() }) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Закрыть",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
            
            // Messages list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { message ->
                    MessageBubble(message)
                }
                
                if (isLoading) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("AI печатает...")
                        }
                    }
                }
            }
            
            // Input field
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Введите сообщение...") },
                        enabled = !isLoading,
                        maxLines = 4
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    IconButton(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                viewModel.sendMessage(messageText)
                                messageText = ""
                            }
                        },
                        enabled = !isLoading && messageText.isNotBlank()
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Отправить",
                            tint = if (messageText.isNotBlank() && !isLoading) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                }
            }
        }
    }
    
    // Диалоговое окно для редактирования System Prompt, Temperature и выбора моделей
    if (showSystemPromptDialog) {
        SystemPromptDialog(
            currentPrompt = systemPrompt,
            currentTemperature = temperature,
            availableModels = availableModels,
            selectedModels = selectedModels,
            onDismiss = { showSystemPromptDialog = false },
            onSave = { newPrompt ->
                viewModel.updateSystemPrompt(newPrompt)
                showSystemPromptDialog = false
            },
            onReset = {
                viewModel.resetSystemPromptToDefault()
            },
            onTemperatureChange = { newTemp ->
                viewModel.updateTemperature(newTemp)
            },
            onModelToggle = { model ->
                viewModel.toggleModelSelection(model)
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    val hasStructuredData = !isUser && message.title != null && message.body != null
    
    val clipboardManager = LocalClipboardManager.current
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current
    
    // Функция для получения текста для копирования
    fun getTextToCopy(): String {
        return if (hasStructuredData) {
            buildString {
                message.title?.let { append("$it\n\n") }
                message.body?.let { append(it) }
                message.tags?.let { 
                    if (it.isNotEmpty()) {
                        append("\n\nТеги: ${it.joinToString(", ")}")
                    }
                }
            }
        } else {
            message.content
        }
    }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier
                .widthIn(max = if (hasStructuredData) 320.dp else 280.dp)
                .then(
                    // Добавляем долгое нажатие только для сообщений AI
                    if (!isUser) {
                        Modifier.combinedClickable(
                            onClick = { },
                            onLongClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                clipboardManager.setText(AnnotatedString(getTextToCopy()))
                                Toast.makeText(
                                    context,
                                    "Текст скопирован",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    } else {
                        Modifier
                    }
                )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isUser) "Вы" else message.modelName ?: "AI Агент",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (isUser) 
                                MaterialTheme.colorScheme.onPrimaryContainer 
                            else 
                                MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        
                        // Показываем метрики для ответов ассистента
                        if (!isUser && (message.responseTimeMs != null || message.tokensUsed != null)) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                message.responseTimeMs?.let { time ->
                                    Text(
                                        text = "⏱ ${time}ms",
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                                message.tokensUsed?.let { tokens ->
                                    Text(
                                        text = "🔤 $tokens tok",
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                                message.cost?.let { cost ->
                                    Text(
                                        text = "💰 $${String.format("%.6f", cost)}",
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                    
                    // Показываем температуру для ответов ассистента
                    if (!isUser && message.temperature != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "T: ${String.format("%.1f", message.temperature)}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                
                if (hasStructuredData) {
                    // Структурированное отображение для ответа AI
                    StructuredMessageContent(message)
                } else {
                    // Обычное текстовое сообщение
                    Text(
                        text = message.content,
                        color = if (isUser) 
                            MaterialTheme.colorScheme.onPrimaryContainer 
                        else 
                            MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun StructuredMessageContent(message: ChatMessage) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Title - жирный шрифт, чуть больше
        message.title?.let { title ->
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        
        // Body - обычный шрифт
        message.body?.let { body ->
            Text(
                text = body,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        
        // Tags - салатовый блок с прозрачностью 70% и закругленными краями
        message.tags?.let { tags ->
            if (tags.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0x80, 0xFF, 0x80).copy(alpha = 0.7f), // Салатовый с прозрачностью 70%
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = tags.joinToString(", "),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        fontSize = 12.sp,
                        color = Color(0x00, 0x60, 0x00), // Темно-зеленый текст для читаемости
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun SystemPromptDialog(
    currentPrompt: String,
    currentTemperature: Double,
    availableModels: List<AIModel>,
    selectedModels: List<AIModel>,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onReset: () -> Unit,
    onTemperatureChange: (Double) -> Unit,
    onModelToggle: (AIModel) -> Unit
) {
    var editedPrompt by remember { mutableStateOf(currentPrompt) }
    
    // Обновляем editedPrompt когда currentPrompt изменяется
    LaunchedEffect(currentPrompt) {
        editedPrompt = currentPrompt
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Настройки",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Temperature control
                Text(
                    text = "Temperature: ${String.format("%.2f", currentTemperature)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "0.0\nТочный",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 12.sp
                    )
                    
                    Slider(
                        value = currentTemperature.toFloat(),
                        onValueChange = { onTemperatureChange(it.toDouble()) },
                        valueRange = 0f..2f,
                        steps = 19,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Text(
                        text = "2.0\nКреативный",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 12.sp
                    )
                }
                
                Text(
                    text = when {
                        currentTemperature < 0.5 -> "Детерминированные, предсказуемые ответы"
                        currentTemperature < 1.2 -> "Сбалансированные ответы (стандарт)"
                        else -> "Креативные, разнообразные ответы"
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                
                HorizontalDivider()
                
                // Выбор моделей
                Text(
                    text = "Модели для запроса:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    availableModels.forEach { model ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = model.displayName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Prompt: $${model.costPerMillionPromptTokens}/1M tok | Completion: $${model.costPerMillionCompletionTokens}/1M tok",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = selectedModels.contains(model),
                                onCheckedChange = { onModelToggle(model) }
                            )
                        }
                    }
                }
                
                if (selectedModels.isEmpty()) {
                    Text(
                        text = "⚠️ Выберите хотя бы одну модель",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                HorizontalDivider()
                
                // System Prompt
                Text(
                    text = "Системный промпт:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                OutlinedTextField(
                    value = editedPrompt,
                    onValueChange = { editedPrompt = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 150.dp, max = 300.dp),
                    placeholder = { Text("Введите system prompt...") },
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                
                TextButton(
                    onClick = {
                        onReset()
                        editedPrompt = currentPrompt
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Сбросить промпт к значению по умолчанию")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(editedPrompt)
                }
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
