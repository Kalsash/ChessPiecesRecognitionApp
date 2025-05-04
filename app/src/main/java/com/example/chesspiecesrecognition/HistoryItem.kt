// HistoryItem.kt
package com.example.chesspiecesrecognition

import androidx.room.Entity
import androidx.room.PrimaryKey

// HistoryItem.kt
@Entity(tableName = "history_items")
data class HistoryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val imageUri: String,
    val lichessUrl: String,
    val timestamp: Long = System.currentTimeMillis()
)