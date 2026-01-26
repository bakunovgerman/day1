# Исправление: "Cannot access database on the main thread"

## 🔧 Что было исправлено

### Проблема
```
java.lang.IllegalStateException: Cannot access database on the main thread
```

Метод `getMessagesForSending()` в DAO пытался выполнить запрос к БД в главном потоке.

### Решение

#### 1. Обновлен ChatMessageDao.kt

**Строка 17:**
```kotlin
// БЫЛО:
@Query("SELECT * FROM chat_messages WHERE needSend = 1 ORDER BY timestamp ASC")
fun getMessagesForSending(): List<ChatMessageEntity>

// СТАЛО:
@Query("SELECT * FROM chat_messages WHERE needSend = 1 ORDER BY timestamp ASC")
suspend fun getMessagesForSending(): List<ChatMessageEntity>
```

#### 2. Обновлен ChatViewModel.kt

**Добавлены импорты:**
```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.day1.data.SummaryResponse  // был удален, возвращен
```

**Строка 178-182:**
```kotlin
// БЫЛО:
val messagesToSend = chatRepository.getMessagesForSending()

// СТАЛО:
val messagesToSend = withContext(Dispatchers.IO) {
    chatRepository.getMessagesForSending()
}
```

## ✅ Результат

- ✅ Запросы к БД выполняются в фоновом потоке (Dispatchers.IO)
- ✅ UI не блокируется
- ✅ Ошибка устранена

## 📝 Правило на будущее

**Все методы DAO, которые возвращают данные (не Flow), ДОЛЖНЫ быть suspend функциями:**

```kotlin
// ✅ Правильно
@Query("SELECT * FROM table")
suspend fun getData(): List<Entity>

// ✅ Правильно (Flow не требует suspend)
@Query("SELECT * FROM table")
fun getDataFlow(): Flow<List<Entity>>

// ❌ Неправильно - вызовет ошибку
@Query("SELECT * FROM table")
fun getData(): List<Entity>
```

## 🧪 Проверка

После исправления:
1. Пересобрать проект: `./gradlew clean build`
2. Запустить приложение
3. Отправить сообщения
4. Ошибка не должна повторяться

## 📚 Дополнительно

Для подробного описания проблемы и других решений см. `TROUBLESHOOTING.md`

---

**Исправлено:** 2026-01-26  
**Файлы изменены:** 2  
**Строк кода:** ~10
