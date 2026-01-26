# Быстрый старт: Интеграция Room Database

## Что было добавлено

### ✅ Функционал
1. **Хранение истории чата в БД** - все сообщения сохраняются в Room Database
2. **Восстановление истории при перезапуске** - автоматическая загрузка при запуске приложения
3. **Суммаризация в отдельной таблице** - хранение истории суммаризаций
4. **Оптимизация отправки в LLM** - флаг `needSend` предотвращает повторную отправку старых сообщений
5. **Очистка БД при очистке чата** - полное удаление всех данных

## Структура изменений

```
Day1/
├── gradle/
│   └── libs.versions.toml                    [ИЗМЕНЕН] - добавлены зависимости Room
├── app/
│   ├── build.gradle.kts                      [ИЗМЕНЕН] - подключены Room и KSP
│   └── src/main/java/com/example/day1/
│       ├── viewmodel/
│       │   └── ChatViewModel.kt              [ИЗМЕНЕН] - интеграция с БД
│       ├── db/                               [НОВАЯ ПАПКА]
│       │   ├── entity/
│       │   │   ├── ChatMessageEntity.kt      [НОВЫЙ]
│       │   │   └── SummaryEntity.kt          [НОВЫЙ]
│       │   ├── dao/
│       │   │   ├── ChatMessageDao.kt         [НОВЫЙ]
│       │   │   └── SummaryDao.kt             [НОВЫЙ]
│       │   ├── AppDatabase.kt                [НОВЫЙ]
│       │   └── Converters.kt                 [НОВЫЙ]
│       └── repository/                       [НОВАЯ ПАПКА]
│           └── ChatRepository.kt             [НОВЫЙ]
└── [ДОКУМЕНТАЦИЯ]
    ├── DATABASE_IMPLEMENTATION.md            [НОВЫЙ] - подробное описание
    ├── TESTING_DATABASE.md                   [НОВЫЙ] - инструкции по тестированию
    ├── DATABASE_CHANGES_SUMMARY.md           [НОВЫЙ] - сводка изменений
    └── QUICK_START.md                        [НОВЫЙ] - этот файл
```

## Как использовать

### 1. Синхронизация Gradle

После клонирования или обновления проекта:

```bash
# В терминале Android Studio или командной строке
./gradlew build
```

Или в Android Studio:
- File → Sync Project with Gradle Files

### 2. Запуск приложения

1. Подключить устройство или запустить эмулятор
2. Нажать Run (зеленая кнопка Play)
3. Приложение автоматически создаст БД при первом запуске

### 3. Проверка работы

**Тест 1: Сохранение истории**
1. Отправить несколько сообщений
2. Закрыть приложение
3. Снова открыть приложение
4. ✅ История сообщений должна восстановиться

**Тест 2: Очистка данных**
1. Нажать "Очистить чат"
2. Закрыть и открыть приложение
3. ✅ История должна быть пустой

**Тест 3: Суммаризация**
1. Включить "Context Compression"
2. Отправить 11 сообщений
3. ✅ Должна появиться суммаризация
4. Следующие сообщения будут отправляться только с новыми данными

## Технические детали

### База данных: `chat_database`

**Таблица: chat_messages**
| Поле | Тип | Описание |
|------|-----|----------|
| id | String | Уникальный идентификатор (PK) |
| role | String | "user" или "assistant" |
| content | String | Текст сообщения |
| needSend | Boolean | Нужно ли отправлять в LLM |
| timestamp | Long | Время создания |
| tokensUsed | Int? | Количество использованных токенов |
| cost | Double? | Стоимость запроса |
| ... | ... | Другие метаданные |

**Таблица: summaries**
| Поле | Тип | Описание |
|------|-----|----------|
| id | Long | Автоинкремент (PK) |
| summary | String | Текст суммаризации |
| isCurrent | Boolean | Активная суммаризация |
| totalTokens | Int | Количество токенов |
| cost | Double | Стоимость генерации |
| timestamp | Long | Время создания |

### Ключевые компоненты

```kotlin
// Инициализация БД в ChatViewModel
private val database = AppDatabase.getDatabase(application)
private val chatRepository = ChatRepository(
    chatMessageDao = database.chatMessageDao(),
    summaryDao = database.summaryDao()
)

// Автоматическая загрузка истории
chatRepository.getAllMessages().collect { messages ->
    _messages.value = messages
}

// Сохранение сообщения
chatRepository.saveMessage(message)

// Сохранение суммаризации (автоматически устанавливает needSend=false)
chatRepository.saveSummary(summaryResponse)

// Очистка всех данных
chatRepository.clearAllData()
```

## Зависимости

```kotlin
// Room Database
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
ksp("androidx.room:room-compiler:2.6.1")

// KSP для генерации кода Room
id("com.google.devtools.ksp") version "2.0.21-1.0.28"
```

## Отладка

### Database Inspector (Android Studio)

1. Запустить приложение
2. View → Tool Windows → App Inspection
3. Вкладка "Database Inspector"
4. Выбрать процесс приложения
5. Открыть "chat_database"
6. Просмотреть таблицы `chat_messages` и `summaries`

### Логирование

Можно добавить логи в `ChatRepository.kt`:

```kotlin
import android.util.Log

suspend fun saveMessage(message: ChatMessage) {
    Log.d("ChatRepository", "Saving message: ${message.id}")
    chatMessageDao.insertMessage(message.toEntity())
}
```

## Распространенные проблемы

### ❌ Ошибка: "Cannot access database on the main thread"

**Решение:** Все операции с БД выполняются через `suspend` функции в корутинах. Убедитесь, что вызовы обернуты в `viewModelScope.launch { }`

### ❌ Ошибка: "No Room compiler dependency"

**Решение:** Убедитесь, что:
1. Применен плагин KSP: `alias(libs.plugins.ksp)`
2. Добавлена зависимость: `ksp(libs.androidx.room.compiler)`
3. Выполнена синхронизация Gradle

### ❌ История не восстанавливается

**Решение:** 
1. Проверьте логи на наличие ошибок Room
2. Убедитесь, что БД создана (Database Inspector)
3. Проверьте, что `loadChatHistory()` вызывается в init блоке

## Следующие шаги

1. ✅ Прочитать `DATABASE_IMPLEMENTATION.md` для понимания архитектуры
2. ✅ Выполнить тесты из `TESTING_DATABASE.md`
3. ✅ Проверить работу в Database Inspector
4. ✅ При необходимости добавить логирование для отладки

## Поддержка

Если возникли проблемы:
1. Проверьте логи Android Studio (Logcat)
2. Используйте Database Inspector для проверки данных
3. Убедитесь, что все зависимости корректно установлены
4. Проверьте версии библиотек в `libs.versions.toml`

---

**Версия:** 1.0  
**Дата:** 2026-01-26  
**Room Version:** 2.6.1  
**Target SDK:** 36  
**Min SDK:** 24
