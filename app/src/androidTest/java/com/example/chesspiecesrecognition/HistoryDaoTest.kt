// HistoryDaoTest.kt
package com.example.chesspiecesrecognition

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.runner.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class HistoryDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var historyDao: HistoryDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        historyDao = database.historyDao()
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndGetItem() = runBlocking {
        // Arrange
        val item = HistoryItem(
            imageUri = "test.jpg",
            lichessUrl = "test.url"
        )

        // Act
        val id = historyDao.insert(item)
        val retrieved = historyDao.getHistoryItemById(id)

        // Assert
        assert(retrieved != null)
        assert(retrieved?.imageUri == "test.jpg")
    }

    @Test
    fun deleteItem() = runBlocking {
        // Arrange
        val item = HistoryItem(
            imageUri = "test.jpg",
            lichessUrl = "test.url"
        )
        val id = historyDao.insert(item)

        // Act
        val deletedCount = historyDao.deleteHistoryItem(id)
        val retrieved = historyDao.getHistoryItemById(id)

        // Assert
        assert(deletedCount == 1)
        assert(retrieved == null)
    }
}