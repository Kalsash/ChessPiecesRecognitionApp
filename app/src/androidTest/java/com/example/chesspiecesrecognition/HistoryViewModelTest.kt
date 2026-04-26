package com.example.chesspiecesrecognition

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var testDispatcher: TestDispatcher
    private lateinit var database: AppDatabase
    private lateinit var historyDao: HistoryDao
    private lateinit var repository: HistoryRepository
    private lateinit var viewModel: HistoryViewModel

    @Before
    fun setUp() {
        testDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)

        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        historyDao = database.historyDao()
        repository = HistoryRepositoryImpl(historyDao)

        // Создаем ViewModel с реальным Application
        val application = context.applicationContext as android.app.Application
        viewModel = HistoryViewModel(application)
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    @Test
    fun addHistoryItem_insertsItemIntoDatabase() = runTest {
        // Arrange
        val imageUri = "content://media/image.jpg"
        val lichessUrl = "https://lichess.org/analysis"

        // Act
        viewModel.addHistoryItem(imageUri, lichessUrl)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert - проверяем что элемент добавлен в базу
        // Мы не можем напрямую проверить базу, так как у ViewModel своя внутренняя база
        // Вместо этого проверяем что LiveData обновился
        val items = viewModel.historyItems.getOrAwaitValue()
        assert(items.any { it.imageUri == imageUri && it.lichessUrl == lichessUrl })
    }

    @Test
    fun deleteHistoryItem_removesItemFromDatabase() = runTest {
        // Arrange - сначала добавляем элемент через ViewModel
        val imageUri = "test_delete.jpg"
        val lichessUrl = "test.url"

        viewModel.addHistoryItem(imageUri, lichessUrl)
        testDispatcher.scheduler.advanceUntilIdle()

        // Получаем добавленный элемент
        val allItems = viewModel.historyItems.getOrAwaitValue()
        val addedItem = allItems.find { it.imageUri == imageUri && it.lichessUrl == lichessUrl }
        assert(addedItem != null)

        // Act
        viewModel.deleteHistoryItem(addedItem!!.id, "/fake/path/to/image.jpg")
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert - проверяем что элемент удален из LiveData
        val finalItems = viewModel.historyItems.getOrAwaitValue()
        assert(finalItems.none { it.id == addedItem.id })
    }

    @Test
    fun historyItems_liveDataUpdatesWhenItemAdded() = runTest {
        // Arrange
        var observedItems: List<HistoryItem>? = null
        val observer = Observer<List<HistoryItem>> { items ->
            observedItems = items
        }

        viewModel.historyItems.observeForever(observer)

        // Act - добавляем элемент
        val imageUri = "test_live_data.jpg"
        val lichessUrl = "live.data.url"
        viewModel.addHistoryItem(imageUri, lichessUrl)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        assert(observedItems != null)
        assert(observedItems!!.any { it.imageUri == imageUri && it.lichessUrl == lichessUrl })

        // Cleanup
        viewModel.historyItems.removeObserver(observer)
    }
}

// Helper function for LiveData testing
fun <T> androidx.lifecycle.LiveData<T>.getOrAwaitValue(
    time: Long = 2,
    timeUnit: java.util.concurrent.TimeUnit = java.util.concurrent.TimeUnit.SECONDS
): T {
    var data: T? = null
    val latch = java.util.concurrent.CountDownLatch(1)
    val observer = object : Observer<T> {
        override fun onChanged(value: T) {
            data = value
            latch.countDown()
            this@getOrAwaitValue.removeObserver(this)
        }
    }

    this.observeForever(observer)

    try {
        if (!latch.await(time, timeUnit)) {
            throw java.util.concurrent.TimeoutException("LiveData value was never set.")
        }
    } finally {
        this.removeObserver(observer)
    }

    @Suppress("UNCHECKED_CAST")
    return data as T
}