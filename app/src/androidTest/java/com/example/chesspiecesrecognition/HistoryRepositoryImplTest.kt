package com.example.chesspiecesrecognition

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class HistoryRepositoryImplTest {
    private lateinit var database: AppDatabase
    private lateinit var historyDao: HistoryDao
    private lateinit var repository: HistoryRepository

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        historyDao = database.historyDao()
        repository = HistoryRepositoryImpl(historyDao)
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndRetrieveHistoryItem() = runTest {
        // Arrange
        val item = HistoryItem(
            imageUri = "content://media/image1.jpg",
            lichessUrl = "https://lichess.org/analysis"
        )

        // Act
        val insertedId = repository.insert(item)
        val retrievedItem = repository.getHistoryItemById(insertedId)

        // Assert
        assert(retrievedItem != null)
        assert(retrievedItem?.imageUri == item.imageUri)
        assert(retrievedItem?.lichessUrl == item.lichessUrl)
    }

    @Test
    fun getAllHistoryItems_returnsItemsInCorrectOrder() = runTest {
        // Arrange
        val item1 = HistoryItem(
            imageUri = "image1.jpg",
            lichessUrl = "url1",
            timestamp = 1000L
        )
        val item2 = HistoryItem(
            imageUri = "image2.jpg",
            lichessUrl = "url2",
            timestamp = 2000L
        )

        // Act
        repository.insert(item1)
        repository.insert(item2)

        // Получаем значения из LiveData с ожиданием
        val liveData = repository.getAllHistoryItems()
        val historyItems = liveData.getOrAwaitValue()

        // Assert
        assert(historyItems.size >= 2)

        // Находим наши элементы в списке (может быть больше элементов из других тестов)
        val foundItem1 = historyItems.find { it.imageUri == "image1.jpg" }
        val foundItem2 = historyItems.find { it.imageUri == "image2.jpg" }

        assert(foundItem1 != null)
        assert(foundItem2 != null)

        // Проверяем порядок (timestamp DESC)
        val index1 = historyItems.indexOf(foundItem1)
        val index2 = historyItems.indexOf(foundItem2)

        // Поскольку сортировка по timestamp DESC, item2 (timestamp 2000) должен быть перед item1 (timestamp 1000)
        assert(index2 < index1)
    }

    @Test
    fun deleteHistoryItem_removesItem() = runTest {
        // Arrange
        val item = HistoryItem(
            imageUri = "image.jpg",
            lichessUrl = "url"
        )
        val insertedId = repository.insert(item)

        // Act
        val deletedCount = repository.deleteHistoryItem(insertedId)

        // Assert
        assert(deletedCount == 1)
        val retrieved = repository.getHistoryItemById(insertedId)
        assert(retrieved == null)
    }

    @Test
    fun getAllHistoryItems_liveDataUpdates() = runTest {
        // Arrange
        val testItem = HistoryItem(
            imageUri = "test.jpg",
            lichessUrl = "test.url"
        )

        // Act
        repository.insert(testItem)

        // Получаем значения из LiveData
        val liveData = repository.getAllHistoryItems()
        val items = liveData.getOrAwaitValue()

        // Assert
        assert(liveData != null)
        assert(items.isNotEmpty())

        // Проверяем что наш элемент есть в списке
        assert(items.any { it.imageUri == "test.jpg" })
    }
}

// Вспомогательная функция для получения значений из LiveData в тестах
fun <T> LiveData<T>.getOrAwaitValue(
    time: Long = 2,
    timeUnit: TimeUnit = TimeUnit.SECONDS,
    afterObserve: () -> Unit = {}
): T {
    var data: T? = null
    val latch = CountDownLatch(1)
    val observer = object : Observer<T> {
        override fun onChanged(value: T) {
            data = value
            latch.countDown()
            this@getOrAwaitValue.removeObserver(this)
        }
    }

    this.observeForever(observer)

    try {
        afterObserve.invoke()

        if (!latch.await(time, timeUnit)) {
            throw TimeoutException("LiveData value was never set.")
        }
    } finally {
        this.removeObserver(observer)
    }

    @Suppress("UNCHECKED_CAST")
    return data as T
}