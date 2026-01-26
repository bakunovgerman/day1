# Устранение неполадок Room Database

## ❌ Ошибка: "Cannot access database on the main thread"

### Симптомы
```
java.lang.IllegalStateException: Cannot access database on the main thread 
since it may potentially lock the UI for a long period of time.
```

### Причина
Метод DAO пытается выполнить запрос к БД в главном потоке (UI thread), что может заблокировать интерфейс.

### Решение

#### 1. Сделать метод DAO suspend функцией

**Было:**
```kotlin
@Query("SELECT * FROM chat_messages WHERE needSend = 1 ORDER BY timestamp ASC")
fun getMessagesForSending(): List<ChatMessageEntity>
```

**Стало:**
```kotlin
@Query("SELECT * FROM chat_messages WHERE needSend = 1 ORDER BY timestamp ASC")
suspend fun getMessagesForSending(): List<ChatMessageEntity>
```

#### 2. Добавить suspend в Repository

**Уже правильно:**
```kotlin
suspend fun getMessagesForSending(): List<ChatMessage> {
    return chatMessageDao.getMessagesForSending().map { it.toChatMessage() }
}
```

#### 3. Вызывать с использованием Dispatchers.IO (опционально, но рекомендуется)

**В ViewModel:**
```kotlin
val messagesToSend = withContext(Dispatchers.IO) {
    chatRepository.getMessagesForSending()
}
```

**Не забудьте импорты:**
```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
```

### Правило
✅ **Все методы DAO, возвращающие данные (не Flow), должны быть suspend функциями:**

```kotlin
// ✅ Правильно - Flow может быть не suspend
@Query("SELECT * FROM chat_messages")
fun getAllMessages(): Flow<List<ChatMessageEntity>>

// ✅ Правильно - suspend для обычного возврата
@Query("SELECT * FROM chat_messages WHERE needSend = 1")
suspend fun getMessagesForSending(): List<ChatMessageEntity>

// ✅ Правильно - suspend для модификации
@Query("DELETE FROM chat_messages")
suspend fun deleteAll()

// ❌ Неправильно - может вызвать ошибку в главном потоке
@Query("SELECT COUNT(*) FROM chat_messages")
fun getCount(): Int  // должно быть suspend fun
```

## 🔧 Другие распространенные проблемы

### Проблема: Данные не сохраняются в БД

**Проверьте:**
1. Метод вызывается внутри корутины (`viewModelScope.launch { }`)
2. Метод DAO помечен как `suspend`
3. Транзакция не откатывается из-за исключения

**Решение:**
```kotlin
viewModelScope.launch {
    try {
        chatRepository.saveMessage(message)
    } catch (e: Exception) {
        Log.e("ChatViewModel", "Error saving message", e)
        _error.value = "Ошибка сохранения: ${e.message}"
    }
}
```

### Проблема: UI не обновляется при изменении БД

**Причина:** Не используется Flow или не подписались на изменения

**Решение:**
```kotlin
// В Repository - используем Flow
fun getAllMessages(): Flow<List<ChatMessage>> {
    return chatMessageDao.getAllMessages().map { entities ->
        entities.map { it.toChatMessage() }
    }
}

// В ViewModel - подписываемся на Flow
init {
    viewModelScope.launch {
        chatRepository.getAllMessages().collect { messages ->
            _messages.value = messages
        }
    }
}
```

### Проблема: Дублирование сообщений в UI

**Причина:** Сообщения добавляются и в БД, и в StateFlow

**Решение:** Добавляйте только в БД, Flow автоматически обновит UI
```kotlin
// ❌ Неправильно
_messages.value = _messages.value + newMessage
chatRepository.saveMessage(newMessage)

// ✅ Правильно - только в БД
chatRepository.saveMessage(newMessage)
// Flow автоматически обновит _messages
```

### Проблема: База данных повреждена или пуста после обновления схемы

**Причина:** Изменена схема БД без миграции

**Решение для разработки:**
```kotlin
Room.databaseBuilder(context, AppDatabase::class.java, "chat_database")
    .fallbackToDestructiveMigration()  // ТОЛЬКО для разработки!
    .build()
```

**Решение для продакшена:**
```kotlin
@Database(
    entities = [ChatMessageEntity::class, SummaryEntity::class],
    version = 2,  // Увеличиваем версию
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    // ...
    
    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // SQL для миграции
                database.execSQL("ALTER TABLE chat_messages ADD COLUMN newField TEXT")
            }
        }
        
        fun getDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(context, AppDatabase::class.java, "chat_database")
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}
```

## 🔍 Отладка

### Проверка выполнения в правильном потоке

Добавьте логи для отладки:
```kotlin
suspend fun getMessagesForSending(): List<ChatMessage> {
    Log.d("ChatRepository", "Thread: ${Thread.currentThread().name}")
    return chatMessageDao.getMessagesForSending().map { it.toChatMessage() }
}
```

Должно быть:
- ✅ `DefaultDispatcher-worker-1` или `RoomDatabase-1` - правильный поток
- ❌ `main` - главный поток, будет ошибка

### Проверка транзакций БД

Включите подробное логирование Room:
```kotlin
Room.databaseBuilder(context, AppDatabase::class.java, "chat_database")
    .setQueryCallback({ sqlQuery, bindArgs ->
        Log.d("RoomQuery", "SQL: $sqlQuery")
    }, Executors.newSingleThreadExecutor())
    .build()
```

## 📋 Чеклист для проверки

### Все методы DAO корректны
- [ ] Методы, возвращающие Flow, не помечены suspend
- [ ] Методы, возвращающие данные напрямую, помечены suspend
- [ ] Методы модификации данных (@Insert, @Update, @Delete) помечены suspend
- [ ] Методы с @Query, возвращающие данные, помечены suspend

### Все вызовы БД в корутинах
- [ ] Вызовы Repository в ViewModel обернуты в `viewModelScope.launch { }`
- [ ] Для синхронных операций используется `withContext(Dispatchers.IO)`
- [ ] Нет прямых вызовов DAO вне корутин

### Flow настроен правильно
- [ ] Repository возвращает Flow для автообновления
- [ ] ViewModel подписывается на Flow через collect
- [ ] Только БД является источником данных, StateFlow обновляется из БД

### Обработка ошибок
- [ ] Вызовы БД обернуты в try-catch
- [ ] Ошибки логируются
- [ ] Пользователь получает понятные сообщения об ошибках

## 🎯 Лучшие практики

### 1. Используйте Dispatchers правильно
```kotlin
// ✅ Правильно - явно указываем IO для БД
viewModelScope.launch {
    val data = withContext(Dispatchers.IO) {
        chatRepository.getMessagesForSending()
    }
    // Обработка в Main потоке
    _messages.value = data
}
```

### 2. Flow для автообновления UI
```kotlin
// ✅ Правильно - Flow для реактивного обновления
fun getAllMessages(): Flow<List<ChatMessage>>

// ❌ Неправильно - требует ручного обновления
suspend fun getAllMessages(): List<ChatMessage>
```

### 3. Одиночный источник истины
```kotlin
// ✅ Правильно - БД как единственный источник
chatRepository.saveMessage(message)  // БД обновится
// Flow автоматически обновит UI

// ❌ Неправильно - двойной источник
_messages.value = _messages.value + message  // StateFlow
chatRepository.saveMessage(message)  // БД
// Возможны рассинхронизации!
```

### 4. Транзакции для связанных операций
```kotlin
@Transaction
suspend fun saveSummaryAndMarkMessages(summary: SummaryEntity) {
    summaryDao.insertSummary(summary)
    chatMessageDao.markAllMessagesAsNotNeeded()
}
```

## 📚 Дополнительные ресурсы

- [Room Documentation](https://developer.android.com/training/data-storage/room)
- [Kotlin Coroutines Guide](https://kotlinlang.org/docs/coroutines-guide.html)
- [Flow Documentation](https://kotlinlang.org/docs/flow.html)

---

**Версия:** 1.0  
**Обновлено:** 2026-01-26
