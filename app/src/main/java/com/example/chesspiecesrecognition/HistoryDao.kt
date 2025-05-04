// HistoryDao.kt
package com.example.chesspiecesrecognition

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(item: HistoryItem): Long

    @Query("SELECT * FROM history_items ORDER BY timestamp DESC")
    fun getAllHistoryItems(): LiveData<List<HistoryItem>>

    @Query("DELETE FROM history_items WHERE id = :id")
    suspend fun deleteHistoryItem(id: Long): Int
}