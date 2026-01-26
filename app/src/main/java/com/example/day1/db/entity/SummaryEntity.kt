package com.example.day1.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "summaries")
data class SummaryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val summary: String,
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val cost: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val isCurrent: Boolean = true // Флаг, указывающий на текущую активную суммаризацию
)
