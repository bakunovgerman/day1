package com.example.day1.repository

import com.example.day1.data.ChatMessage
import com.example.day1.data.SummaryResponse
import com.example.day1.db.dao.ChatMessageDao
import com.example.day1.db.dao.SummaryDao
import com.example.day1.db.entity.ChatMessageEntity
import com.example.day1.db.entity.SummaryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ChatRepository(
    private val chatMessageDao: ChatMessageDao,
    private val summaryDao: SummaryDao
) {
    private val json = Json { ignoreUnknownKeys = true }
    
    // Получение всех сообщений как Flow для автоматического обновления UI
    fun getAllMessages(): Flow<List<ChatMessage>> {
        return chatMessageDao.getAllMessages().map { entities ->
            entities.map { it.toChatMessage() }
        }
    }
    
    // Получение сообщений, которые нужно отправлять в LLM
    suspend fun getMessagesForSending(): List<ChatMessage> {
        return chatMessageDao.getMessagesForSending().map { it.toChatMessage() }
    }
    
    // Сохранение сообщения
    suspend fun saveMessage(message: ChatMessage) {
        chatMessageDao.insertMessage(message.toEntity())
    }
    
    // Сохранение нескольких сообщений
    suspend fun saveMessages(messages: List<ChatMessage>) {
        chatMessageDao.insertMessages(messages.map { it.toEntity() })
    }
    
    // Установка needSend = false для всех сообщений
    suspend fun markAllMessagesAsNotNeeded() {
        chatMessageDao.markAllMessagesAsNotNeeded()
    }
    
    // Очистка всех сообщений
    suspend fun clearMessages() {
        chatMessageDao.deleteAll()
    }
    
    // Получение количества сообщений
    suspend fun getMessagesCount(): Int {
        return chatMessageDao.getMessagesCount()
    }
    
    // Сохранение суммаризации
    suspend fun saveSummary(summaryResponse: SummaryResponse) {
        // Помечаем все предыдущие суммаризации как неактивные
        summaryDao.markAllSummariesAsNotCurrent()
        
        // Сохраняем новую суммаризацию как текущую
        val summaryEntity = SummaryEntity(
            summary = summaryResponse.summary,
            promptTokens = summaryResponse.promptTokens,
            completionTokens = summaryResponse.completionTokens,
            totalTokens = summaryResponse.totalTokens,
            cost = summaryResponse.cost,
            isCurrent = true
        )
        summaryDao.insertSummary(summaryEntity)
        
        // Помечаем все сообщения как не требующие отправки
        markAllMessagesAsNotNeeded()
    }
    
    // Получение текущей суммаризации
    suspend fun getCurrentSummary(): String? {
        return summaryDao.getCurrentSummary()?.summary
    }
    
    // Очистка всех суммаризаций
    suspend fun clearSummaries() {
        summaryDao.deleteAll()
    }
    
    // Очистка всех данных (сообщения + суммаризации)
    suspend fun clearAllData() {
        clearMessages()
        clearSummaries()
    }
    
    // Конвертация ChatMessage в ChatMessageEntity
    private fun ChatMessage.toEntity(): ChatMessageEntity {
        return ChatMessageEntity(
            id = this.id,
            role = this.role,
            content = this.content,
            title = this.title,
            body = this.body,
            tags = this.tags?.let { json.encodeToString(it) },
            temperature = this.temperature,
            timestamp = this.timestamp,
            modelName = this.modelName,
            responseTimeMs = this.responseTimeMs,
            tokensUsed = this.tokensUsed,
            promptTokens = this.promptTokens,
            completionTokens = this.completionTokens,
            cost = this.cost,
            needSend = true // По умолчанию новые сообщения нужно отправлять
        )
    }
    
    // Конвертация ChatMessageEntity в ChatMessage
    private fun ChatMessageEntity.toChatMessage(): ChatMessage {
        return ChatMessage(
            id = this.id,
            role = this.role,
            content = this.content,
            title = this.title,
            body = this.body,
            tags = this.tags?.let { 
                try {
                    json.decodeFromString<List<String>>(it)
                } catch (e: Exception) {
                    null
                }
            },
            temperature = this.temperature,
            timestamp = this.timestamp,
            modelName = this.modelName,
            responseTimeMs = this.responseTimeMs,
            tokensUsed = this.tokensUsed,
            promptTokens = this.promptTokens,
            completionTokens = this.completionTokens,
            cost = this.cost
        )
    }
}
