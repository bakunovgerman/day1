package com.example.day1.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.day1.db.entity.SummaryEntity

@Dao
interface SummaryDao {
    @Query("SELECT * FROM summaries WHERE isCurrent = 1 ORDER BY timestamp DESC LIMIT 1")
    suspend fun getCurrentSummary(): SummaryEntity?
    
    @Query("SELECT * FROM summaries ORDER BY timestamp DESC")
    suspend fun getAllSummaries(): List<SummaryEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummary(summary: SummaryEntity)
    
    @Query("UPDATE summaries SET isCurrent = 0")
    suspend fun markAllSummariesAsNotCurrent()
    
    @Query("DELETE FROM summaries")
    suspend fun deleteAll()
}
