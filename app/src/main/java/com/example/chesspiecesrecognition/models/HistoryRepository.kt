// HistoryRepository.kt
package com.example.chesspiecesrecognition

import androidx.lifecycle.LiveData

interface HistoryRepository {
    suspend fun insert(item: HistoryItem): Long
    fun getAllHistoryItems(): LiveData<List<HistoryItem>>
    suspend fun getHistoryItemById(id: Long): HistoryItem?
    suspend fun deleteHistoryItem(id: Long): Int
}