// Создадим файл TimeUtils.kt
package com.example.chesspiecesrecognition

object TimeUtils {
    fun formatTime(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%02d:%02d", minutes, remainingSeconds)
    }

    fun parseTime(timeString: String): Long {
        val parts = timeString.split(":")
        return if (parts.size == 2) {
            val minutes = parts[0].toLong()
            val seconds = parts[1].toLong()
            (minutes * 60 + seconds) * 1000
        } else {
            0L
        }
    }
}