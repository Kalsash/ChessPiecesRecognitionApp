package com.example.chesspiecesrecognition

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import android.widget.VideoView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun VideoTrimmerScreen(
    videoUri: Uri,
    onTrimConfirmed: (Long, Long, Any?) -> Unit, // Добавляем intervalMs как третий параметр
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var duration by remember { mutableStateOf(10000L) }
    var startTime by remember { mutableStateOf(0L) }
    var endTime by remember { mutableStateOf(10000L) }
    var isPlaying by remember { mutableStateOf(false) }
    var videoViewRef by remember { mutableStateOf<android.widget.VideoView?>(null) }
    var currentVideoPosition by remember { mutableStateOf(0L) }

    // Состояния для текстовых полей
    var startTimeText by remember { mutableStateOf("00:00:000") }
    var endTimeText by remember { mutableStateOf("00:00:000") }
    var startTimeError by remember { mutableStateOf(false) }
    var endTimeError by remember { mutableStateOf(false) }

    // Настройка интервала кадров
    var frameIntervalMs by remember { mutableStateOf(1000L) } // Интервал по умолчанию 1 секунда
    var frameIntervalText by remember { mutableStateOf("1000") }
    var frameIntervalError by remember { mutableStateOf(false) }

    // Таймер для отслеживания позиции видео
    LaunchedEffect(isPlaying) {
        while (true) {
            delay(100) // Проверяем позицию каждые 100мс
            videoViewRef?.let { videoView ->
                if (videoView.isPlaying) {
                    currentVideoPosition = videoView.currentPosition.toLong()
                    // Автоматически останавливаем воспроизведение при достижении конца
                    if (currentVideoPosition >= endTime) {
                        videoView.pause()
                        videoView.seekTo(startTime.toInt())
                        isPlaying = false
                        currentVideoPosition = startTime
                    }
                }
            }
        }
    }

    // Обновляем текстовые поля при изменении времени через слайдеры
    LaunchedEffect(startTime) {
        startTimeText = formatTimeWithMillis(startTime)
        startTimeError = false
        // Немедленно перематываем видео
        videoViewRef?.seekTo(startTime.toInt())
        currentVideoPosition = startTime
    }

    LaunchedEffect(endTime) {
        endTimeText = formatTimeWithMillis(endTime)
        endTimeError = false
    }

    LaunchedEffect(videoUri) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val videoDuration = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLong() ?: 10000L

            duration = videoDuration
            endTime = videoDuration
            endTimeText = formatTimeWithMillis(endTime)
            retriever.release()
        } catch (e: Exception) {
            duration = 10000L
            endTime = 10000L
        }
    }

    // Функции для обработки изменений времени
    fun updateStartTime(newTime: Long) {
        if (newTime in 0 until endTime) {
            startTime = newTime
            startTimeError = false
        } else {
            startTimeError = true
        }
    }

    fun updateEndTime(newTime: Long) {
        if (newTime in (startTime + 1000)..duration) {
            endTime = newTime
            endTimeError = false
        } else {
            endTimeError = true
        }
    }

    // Функция для обновления интервала кадров
    fun updateFrameInterval(newInterval: String) {
        frameIntervalText = newInterval
        try {
            val interval = newInterval.toLong()
            if (interval in 100..5000) { // Ограничиваем от 100мс до 5 секунд
                frameIntervalMs = interval
                frameIntervalError = false
            } else {
                frameIntervalError = true
            }
        } catch (e: Exception) {
            frameIntervalError = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Обрежьте видео по времени",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    setVideoURI(videoUri)
                    setOnPreparedListener { mp ->
                        val videoDuration = mp.duration.toLong()
                        duration = videoDuration
                        endTime = videoDuration
                        endTimeText = formatTimeWithMillis(endTime)
                        videoViewRef = this

                        // Устанавливаем начальную позицию
                        seekTo(startTime.toInt())
                    }

                    // Слушатель завершения воспроизведения
                    setOnCompletionListener {
                        isPlaying = false
                        seekTo(startTime.toInt())
                        currentVideoPosition = startTime
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        )

        // Индикатор текущей позиции
        Text(
            text = "Текущая позиция: ${formatTimeWithMillis(currentVideoPosition)}",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Кнопки управления воспроизведением
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    videoViewRef?.let { videoView ->
                        if (isPlaying) {
                            videoView.pause()
                            isPlaying = false
                        } else {
                            // КРИТИЧЕСКИ ВАЖНО: сначала перематываем, потом запускаем
                            videoView.seekTo(startTime.toInt())
                            currentVideoPosition = startTime
                            videoView.start()
                            isPlaying = true
                        }
                    }
                },
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Text(if (isPlaying) "Пауза" else "Воспроизвести")
            }

            Button(
                onClick = {
                    videoViewRef?.apply {
                        pause()
                        seekTo(startTime.toInt())
                    }
                    isPlaying = false
                    currentVideoPosition = startTime
                },
                modifier = Modifier.padding(horizontal = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("Сброс")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Настройка интервала кадров
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Text(
                text = "Интервал извлечения кадров",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            TimeInputField(
                value = frameIntervalText,
                onValueChange = { newText ->
                    updateFrameInterval(newText)
                },
                isError = frameIntervalError,
                placeholder = "мс (100-5000)",
                modifier = Modifier.width(120.dp)
            )
            if (frameIntervalError) {
                Text(
                    text = "Допустимый диапазон: 100-5000 мс",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
            } else {
                Text(
                    text = "≈ ${((endTime - startTime) / frameIntervalMs).toInt()} кадров",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Поля для точного ввода времени
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Начало времени",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                TimeInputField(
                    value = startTimeText,
                    onValueChange = { newText ->
                        startTimeText = newText
                        parseTimeToMillis(newText)?.let { millis ->
                            updateStartTime(millis)
                        } ?: run {
                            startTimeError = true
                        }
                    },
                    isError = startTimeError,
                    placeholder = "мм:сс:мсс"
                )
                if (startTimeError) {
                    Text(
                        text = "Некорректное время",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Конец времени",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                TimeInputField(
                    value = endTimeText,
                    onValueChange = { newText ->
                        endTimeText = newText
                        parseTimeToMillis(newText)?.let { millis ->
                            updateEndTime(millis)
                        } ?: run {
                            endTimeError = true
                        }
                    },
                    isError = endTimeError,
                    placeholder = "мм:сс:мсс"
                )
                if (endTimeError) {
                    Text(
                        text = "Некорректное время",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Слайдер для начала времени
        Text(
            text = "Начало: ${formatTimeWithMillis(startTime)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Slider(
            value = if (duration > 0) startTime.toFloat() / duration.toFloat() else 0f,
            onValueChange = { value ->
                val newTime = (value * duration).toLong().coerceIn(0L, endTime - 1000L)
                updateStartTime(newTime)
            },
            modifier = Modifier.fillMaxWidth()
        )

        // Слайдер для конца времени
        Text(
            text = "Конец: ${formatTimeWithMillis(endTime)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Slider(
            value = if (duration > 0) endTime.toFloat() / duration.toFloat() else 0f,
            onValueChange = { value ->
                val newTime = (value * duration).toLong().coerceIn(startTime + 1000L, duration)
                updateEndTime(newTime)
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Диапазон: ${formatTimeWithMillis(startTime)} - ${formatTimeWithMillis(endTime)}",
            style = MaterialTheme.typography.bodyMedium
        )

        Text(
            text = "Длительность: ${formatTimeWithMillis(endTime - startTime)}",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Blue
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Отмена")
            }

            Button(
                onClick = { onTrimConfirmed(startTime, endTime, frameIntervalMs) },
                enabled = endTime - startTime > 1000 && !startTimeError && !endTimeError && !frameIntervalError
            ) {
                Text("Продолжить к обрезке")
            }
        }
    }
}

@Composable
fun TimeInputField(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean = false,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.width(120.dp),
        textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center)
    )
}

// Форматирование времени с миллисекундами
private fun formatTimeWithMillis(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val milliseconds = millis % 1000
    return String.format("%02d:%02d:%03d", minutes, seconds, milliseconds)
}

// Парсинг строки времени в миллисекунды
private fun parseTimeToMillis(timeString: String): Long? {
    if (timeString.isEmpty()) return null

    return try {
        val parts = timeString.split(":")
        when {
            parts.size == 3 -> { // мм:сс:мсс
                val minutes = parts[0].toLong()
                val seconds = parts[1].toLong()
                val millis = parts[2].toLong()
                (minutes * 60 * 1000) + (seconds * 1000) + millis
            }
            parts.size == 2 -> { // мм:сс
                val minutes = parts[0].toLong()
                val seconds = parts[1].toLong()
                (minutes * 60 * 1000) + (seconds * 1000)
            }
            parts.size == 1 -> { // только секунды
                parts[0].toLong() * 1000
            }
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}