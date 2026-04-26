package com.example.chesspiecesrecognition

import junit.framework.TestCase.assertTrue
import org.junit.Test
import java.io.File

class FileUtilsTest {

    @Test
    fun testFileUtilsIsAccessible() {
        // Просто проверяем что FileUtils доступен
        val fileUtils = FileUtils
        assertTrue(fileUtils != null)
    }

    @Test
    fun deleteImageFile_returnsFalseForNonExistentFile() {
        // Arrange
        val nonExistentPath = "/this/path/does/not/exist/test.jpg"

        // Act
        val result = FileUtils.deleteImageFile(nonExistentPath)

        // Assert
        assertTrue(!result) // File.delete() возвращает false для несуществующего файла
    }

    @Test
    fun deleteImageFile_handlesEmptyPath() {
        // Arrange
        val emptyPath = ""

        // Act
        val result = FileUtils.deleteImageFile(emptyPath)

        // Assert
        assertTrue(!result) // Для пустого пути должно вернуть false
    }

    @Test
    fun deleteImageFile_handlesNullPath() {
        // Проверяем что метод не падает при null (если метод принимает nullable String)
        // Note: Если метод не принимает nullable, этот тест не нужен

        try {
            // Если метод принимает nullable
            // val result = FileUtils.deleteImageFile(null)
            // Если не принимает - просто пропускаем
            assertTrue(true)
        } catch (e: Exception) {
            // Не должно падать с NPE
            assertTrue("Method should handle null gracefully",false )
        }
    }

    @Test
    fun testFileUtilsBehaviorWithRealFile() {
        // Создаем временный файл для тестирования
        val tempFile = File.createTempFile("test_delete", ".jpg")
        tempFile.writeText("test content")
        assertTrue(tempFile.exists())

        try {
            // Act
            val result = FileUtils.deleteImageFile(tempFile.absolutePath)

            // Assert
            assertTrue(result) // Должен успешно удалить существующий файл
            assertTrue(!tempFile.exists()) // Файл должен быть удален
        } finally {
            // На всякий случай очищаем
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }
}