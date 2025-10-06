// HistoryRepositoryImpl.kt
package com.example.chesspiecesrecognition

import androidx.lifecycle.LiveData

class HistoryRepositoryImpl(private val historyDao: HistoryDao) : HistoryRepository {

    override suspend fun insert(item: HistoryItem): Long {
        return historyDao.insert(item)
    }

    override fun getAllHistoryItems(): LiveData<List<HistoryItem>> {
        return historyDao.getAllHistoryItems()
    }

    override suspend fun getHistoryItemById(id: Long): HistoryItem? {
        return historyDao.getHistoryItemById(id)
    }

    override suspend fun deleteHistoryItem(id: Long): Int {
        return historyDao.deleteHistoryItem(id)
    }
}