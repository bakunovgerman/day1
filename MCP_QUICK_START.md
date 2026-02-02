# MCP Tool Calling - Quick Start

## Что реализовано

✅ Автоматическое получение списка tools от MCP сервера  
✅ Передача tools в запросы к LLM  
✅ Автоматическое выполнение tool calls через MCP  
✅ Цикл обработки с защитой от бесконечных вызовов  
✅ Детальное логирование всех операций

## Быстрый старт

### 1. Убедитесь, что MCP сервер доступен

```bash
curl https://fittable-deeanna-noneditorially.ngrok-free.dev/mcp
```

### 2. Запустите приложение

При запуске автоматически произойдет:
- Подключение к MCP серверу
- Получение списка доступных tools
- Логирование списка tools

### 3. Отправьте сообщение

Просто используйте приложение как обычно. Если LLM решит, что нужно вызвать tool - это произойдет автоматически.

## Как это работает

### Сценарий 1: Обычное сообщение (без tools)

```
User: "Привет, как дела?"
  ↓
LLM: "Привет! У меня всё хорошо..."
  ↓
Сохраняется в БД и отображается в UI
```

### Сценарий 2: Сообщение с tool calling

```
User: "What's the current date?"
  ↓
LLM: [tool_call: get_current_date()]
  ↓
MCP: "2026-02-01"
  ↓
LLM: "The current date is February 1st, 2026"
  ↓
Сохраняется в БД и отображается в UI
```

### Сценарий 3: Цепочка tool calls

```
User: "Search for information about Kotlin and summarize it"
  ↓
LLM: [tool_call: web_search("Kotlin programming")]
  ↓
MCP: "Kotlin is a programming language..."
  ↓
LLM: [tool_call: summarize_text(...)]
  ↓
MCP: "Brief summary: Kotlin is modern JVM language..."
  ↓
LLM: "Here's a summary: Kotlin is a modern..."
  ↓
Сохраняется в БД и отображается в UI
```

## Логи для отладки

### При запуске приложения:
```
D/ChatViewModel: Loaded 3 tools from MCP
D/ChatViewModel:   - get_current_date: Returns the current date
D/ChatViewModel:   - web_search: Search the web for information
D/ChatViewModel:   - calculate: Perform mathematical calculations
```

### При tool calling:
```
D/ChatViewModel: Tool calling iteration 1
D/ChatViewModel: Model GPT-4o-mini requested 1 tool calls
D/ChatViewModel: Executing tool: get_current_date
D/ChatViewModel: Arguments: {}
D/ChatViewModel: Tool result: 2026-02-01

D/ChatViewModel: Tool calling iteration 2
D/ChatViewModel: Model GPT-4o-mini requested 0 tool calls
```

## Настройка

### Изменить MCP сервер URL

В `ChatViewModel.kt`:
```kotlin
val mcpClient = McpClient(
    config = McpConfig(url = "YOUR_MCP_SERVER_URL")
)
```

### Изменить максимум итераций

В `ChatViewModel.kt`:
```kotlin
private val maxToolCallIterations = 5  // Измените на нужное значение
```

### Отключить tool calling

Установите пустой URL или обработайте ошибку загрузки tools:
```kotlin
val mcpClient = McpClient(
    config = McpConfig(url = "")
)
```

## Проверка работы

### 1. Проверить загрузку tools

Откройте Logcat в Android Studio и отфильтруйте по "ChatViewModel".
Должны увидеть сообщения о загрузке tools.

### 2. Проверить tool calling

Отправьте сообщение, которое очевидно требует external data:
- "What's the weather?" (если есть weather tool)
- "What's the current time?" (если есть time tool)
- "Search for X" (если есть search tool)

### 3. Проверить цепочку calls

Отправьте сложный запрос:
- "Search for Kotlin and count how many times 'JVM' appears"
- "Get weather in Moscow and New York and compare"

## Примеры запросов для тестирования

В зависимости от доступных tools на вашем MCP сервере:

### Если есть get_current_date:
```
"What's today's date?"
"What day of the week is it?"
```

### Если есть web_search:
```
"Search for the latest news about AI"
"Find information about Kotlin Coroutines"
```

### Если есть calculate:
```
"Calculate 123 * 456"
"What's the square root of 144?"
```

### Если есть file tools:
```
"Read the content of config.json"
"List all files in the project"
```

## Troubleshooting

### Tools не загружаются

**Проблема:** В логах нет "Loaded X tools from MCP"

**Решения:**
1. Проверить доступность MCP сервера
2. Проверить ngrok tunnel
3. Проверить логи на наличие ошибок

### LLM не вызывает tools

**Проблема:** LLM отвечает без использования tools

**Причины:**
1. Tools не были загружены (проверить логи)
2. LLM решил, что tools не нужны
3. Промпт недостаточно явный

**Решения:**
1. Сделать запрос более явным: "Use the weather tool to get weather in Moscow"
2. Проверить, что tools корректно определены в MCP
3. Добавить system prompt: "You have access to tools, use them when needed"

### Ошибки выполнения tools

**Проблема:** "Error executing tool: ..."

**Решения:**
1. Проверить формат аргументов
2. Проверить, что tool существует на MCP сервере
3. Проверить логи MCP сервера

### Достигнут максимум итераций

**Проблема:** "Достигнут максимум итераций tool calling"

**Причины:**
1. LLM застрял в цикле вызова tools
2. Tools возвращают неполные результаты
3. Промпт создает бесконечный loop

**Решения:**
1. Увеличить `maxToolCallIterations` если нужно больше итераций
2. Изменить промпт
3. Проверить логику tools на MCP сервере

## Дополнительная информация

См. полную документацию в `MCP_TOOL_CALLING_IMPLEMENTATION.md`

## Что дальше?

После успешного тестирования можно:
1. Добавить UI индикатор выполнения tools
2. Добавить возможность отключения tool calling в настройках
3. Добавить подтверждение перед выполнением критичных tools
4. Добавить историю tool calls в UI
5. Оптимизировать parallel execution tools
