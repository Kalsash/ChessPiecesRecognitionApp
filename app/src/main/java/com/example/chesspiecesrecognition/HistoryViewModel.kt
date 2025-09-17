// HistoryViewModel.kt
package com.example.chesspiecesrecognition

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val historyDao = database.historyDao()
    private val repository: HistoryRepository = HistoryRepositoryImpl(historyDao)

    val historyItems: LiveData<List<HistoryItem>> = repository.getAllHistoryItems()

    fun addHistoryItem(imageUri: String, lichessUrl: String) {
        viewModelScope.launch {
            repository.insert(HistoryItem(imageUri = imageUri, lichessUrl = lichessUrl))
        }
    }

    fun deleteHistoryItem(id: Long, imagePath: String) {
        viewModelScope.launch {
            repository.deleteHistoryItem(id)
            FileUtils.deleteImageFile(imagePath)
        }
    }
}