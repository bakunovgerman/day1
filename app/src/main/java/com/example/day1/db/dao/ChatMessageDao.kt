package com.example.day1.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.day1.db.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessageEntity>>
    
    @Query("SELECT * FROM chat_messages WHERE needSend = 1 ORDER BY timestamp ASC")
    suspend fun getMessagesForSending(): List<ChatMessageEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)
    
    @Update
    suspend fun updateMessage(message: ChatMessageEntity)
    
    @Query("UPDATE chat_messages SET needSend = 0")
    suspend fun markAllMessagesAsNotNeeded()
    
    @Query("DELETE FROM chat_messages")
    suspend fun deleteAll()
    
    @Query("SELECT COUNT(*) FROM chat_messages")
    suspend fun getMessagesCount(): Int
}
