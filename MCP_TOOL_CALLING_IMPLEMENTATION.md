# MCP Tool Calling Implementation

## Обзор

Реализована интеграция MCP (Model Context Protocol) с LLM для поддержки вызова внешних tools.

## Что было сделано

### 1. Расширены модели данных (ChatMessage.kt)

#### Добавлены типы для tool calling:
- `ToolDefinition` - определение tool для передачи в LLM
- `FunctionDefinition` - описание функции (name, description, parameters)
- `ToolCall` - запрос на вызов tool от LLM
- `FunctionCall` - конкретный вызов функции с аргументами

#### Обновлены существующие модели:
- `MessageContent` - добавлены поля `tool_calls`, `tool_call_id`, `name` для поддержки tool calling
- `OpenRouterRequest` - добавлено поле `tools` для передачи списка доступных tools
- `MessageResponse` - добавлено поле `tool_calls` для получения запросов на вызов tools
- `ModelResponse` - добавлено поле `toolCalls` для передачи tool calls из ответа LLM

### 2. Обновлен OpenRouterService

Добавлена поддержка передачи tools в запросы:
- `sendMessageToMultipleModels()` - принимает параметр `tools`
- `sendMessageToModel()` - передает tools в OpenRouterRequest
- Возвращает `tool_calls` из ответа LLM в `ModelResponse`

### 3. Обновлен ChatViewModel

#### Инициализация MCP:
```kotlin
val mcpClient = McpClient(
    config = McpConfig(url = "https://fittable-deeanna-noneditorially.ngrok-free.dev/mcp")
)
```

#### Загрузка tools при старте:
- `loadMcpTools()` - инициализирует MCP и получает список tools
- `convertMcpToolToOpenAI()` - конвертирует формат MCP tools в формат OpenAI

#### Tool calling цикл:
- `sendMessageWithToolCalling()` - основной цикл обработки tool calls
  - Отправляет запрос к LLM с tools
  - Если LLM вернул tool_calls:
    - Выполняет каждый tool через MCP
    - Добавляет результаты в историю сообщений
    - Отправляет обратно в LLM
  - Повторяет до 5 итераций или пока LLM не вернет финальный ответ

#### Выполнение tools:
- `executeToolCall()` - вызывает tool через MCP клиент
  - Парсит аргументы из JSON
  - Вызывает `mcpClient.callTool()`
  - Возвращает результат как строку

#### Сохранение результатов:
- `saveFinalResponse()` - сохраняет финальный ответ LLM в БД

## Архитектура flow

```
User Input
    ↓
ChatViewModel.sendMessage()
    ↓
sendMessageWithToolCalling()
    ↓
    ┌─────────────────────────────────────┐
    │  Tool Calling Loop (max 5 iter)    │
    │                                     │
    │  1. Send to LLM with tools         │
    │     ↓                               │
    │  2. LLM responds                    │
    │     ↓                               │
    │  3. Has tool_calls?                 │
    │     ├─ No → Save & Exit             │
    │     └─ Yes →                        │
    │        ↓                             │
    │  4. Execute each tool via MCP      │
    │        ↓                             │
    │  5. Add results to messages        │
    │        ↓                             │
    │  6. Loop back to step 1            │
    └─────────────────────────────────────┘
         ↓
    Save to Database
         ↓
    Update UI
```

## Конфигурация

### MCP Server URL
```kotlin
val mcpClient = McpClient(
    config = McpConfig(
        url = "https://fittable-deeanna-noneditorially.ngrok-free.dev/mcp"
    )
)
```

### Максимальное число итераций
```kotlin
private val maxToolCallIterations = 5
```
Защита от бесконечных циклов tool calling.

## Безопасность

1. **Максимум итераций** - предотвращает бесконечные циклы
2. **Error handling** - все tool calls оборачиваются в try-catch
3. **Logging** - детальное логирование всех tool calls для отладки

## Логирование

Все операции логируются с тегом "ChatViewModel":
- Загрузка tools при инициализации
- Каждая итерация tool calling
- Выполнение каждого tool call
- Результаты tool calls
- Ошибки выполнения

## Примеры использования

### 1. LLM запрашивает вызов tool

**User:** "What's the weather in Moscow?"

**LLM Response:**
```json
{
  "tool_calls": [
    {
      "id": "call_123",
      "type": "function",
      "function": {
        "name": "get_weather",
        "arguments": "{\"city\":\"Moscow\"}"
      }
    }
  ]
}
```

**System:** Executes `get_weather` via MCP

**MCP Response:** "Temperature: 5°C, Cloudy"

**System:** Sends result back to LLM

**LLM Final Response:** "The weather in Moscow is 5°C and cloudy."

### 2. Множественные tool calls

LLM может запросить несколько tools за один раз:
```json
{
  "tool_calls": [
    {"id": "1", "function": {"name": "search", "arguments": "..."}},
    {"id": "2", "function": {"name": "calculate", "arguments": "..."}}
  ]
}
```

Все tools выполняются последовательно, результаты отправляются обратно в LLM.

### 3. Цепочка tool calls

LLM может запросить tool, получить результат, и запросить другой tool на основе первого результата (до 5 итераций).

## Зависимости

### Уже включены:
- `com.github.bakunovgerman:McpClient:1.4.0` - MCP клиент
- Ktor для HTTP запросов
- Kotlinx Serialization для JSON

### Не требуется дополнительных зависимостей

## Тестирование

### Проверка загрузки tools:
1. Запустить приложение
2. Проверить логи:
```
ChatViewModel: Loaded X tools from MCP
ChatViewModel:   - tool_name: description
```

### Проверка tool calling:
1. Отправить сообщение, требующее вызова tool
2. Проверить логи:
```
ChatViewModel: Tool calling iteration 1
ChatViewModel: Model GPT-4o-mini requested 1 tool calls
ChatViewModel: Executing tool: tool_name
ChatViewModel: Arguments: {...}
ChatViewModel: Tool result: ...
```

## Возможные проблемы и решения

### 1. MCP Server недоступен
**Симптом:** Ошибка при загрузке tools
**Решение:** Проверить доступность URL, проверить ngrok

### 2. Tool не найден
**Симптом:** Ошибка "Tool not found"
**Решение:** Проверить, что tool существует на MCP сервере

### 3. Неверный формат аргументов
**Симптом:** JSON parsing error
**Решение:** Проверить схему параметров tool в MCP

### 4. Бесконечный цикл
**Симптом:** "Достигнут максимум итераций tool calling"
**Решение:** LLM постоянно вызывает tools - проверить промпты, увеличить maxToolCallIterations если нужно

## Будущие улучшения

1. **Кэширование tools** - не загружать каждый раз при инициализации
2. **Parallel tool execution** - выполнять независимые tools параллельно
3. **Tool calling history** - сохранять историю tool calls в БД
4. **User confirmation** - запрашивать подтверждение перед выполнением tools
5. **Tool costs tracking** - отслеживать стоимость вызовов tools
6. **Retry политика** - повторять неудачные tool calls
7. **Streaming tool results** - показывать результаты tool calls в реальном времени

## API Reference

### ChatViewModel

#### Properties
- `availableTools: StateFlow<List<ToolDefinition>>` - список доступных tools от MCP

#### Methods
- `loadMcpTools()` - загружает tools от MCP сервера
- `sendMessageWithToolCalling(messages)` - отправляет сообщение с поддержкой tool calling
- `executeToolCall(toolCall)` - выполняет один tool call через MCP
- `saveFinalResponse(response)` - сохраняет финальный ответ в БД

### OpenRouterService

#### Methods
- `sendMessageToMultipleModels(..., tools)` - отправляет запрос с tools к моделям

### Data Models

См. `ChatMessage.kt` для полного списка моделей данных.
