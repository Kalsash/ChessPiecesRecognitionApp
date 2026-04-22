package com.example.chesspiecesrecognition

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalConfiguration

@Composable
fun VideoTrimmerScreen(
    videoUri: Uri,
    onTrimConfirmed: (Long, Long, Any?) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

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
    var frameIntervalMs by remember { mutableStateOf(1000L) }
    var frameIntervalText by remember { mutableStateOf("1000") }
    var frameIntervalError by remember { mutableStateOf(false) }

    // Таймер для отслеживания позиции видео
    LaunchedEffect(isPlaying) {
        while (true) {
            delay(100)
            videoViewRef?.let { videoView ->
                if (videoView.isPlaying) {
                    currentVideoPosition = videoView.currentPosition.toLong()
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
            if (interval in 100..5000) {
                frameIntervalMs = interval
                frameIntervalError = false
            } else {
                frameIntervalError = true
            }
        } catch (e: Exception) {
            frameIntervalError = true
        }
    }

    // Адаптивный padding
    val adaptivePadding = when {
        screenWidth < 360.dp -> 8.dp
        screenWidth < 600.dp -> 12.dp
        else -> 16.dp
    }

    // Адаптивная ширина элементов
    val adaptiveElementWidth = when {
        screenWidth < 360.dp -> 100.dp
        screenWidth < 600.dp -> 110.dp
        else -> 120.dp
    }

    // Адаптивный размер шрифта
    val adaptiveTitleSize = when {
        screenWidth < 360.dp -> 16.sp
        screenWidth < 600.dp -> 18.sp
        else -> 20.sp
    }

    val adaptiveBodySize = when {
        screenWidth < 360.dp -> 12.sp
        screenWidth < 600.dp -> 14.sp
        else -> 16.sp
    }

    val adaptiveSmallSize = when {
        screenWidth < 360.dp -> 10.sp
        screenWidth < 600.dp -> 11.sp
        else -> 12.sp
    }

    // Ландшафтный режим
    if (isLandscape) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(adaptivePadding)
        ) {
            // Левая колонка - видео и управление
            Column(
                modifier = Modifier.weight(0.6f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Обрежьте видео по времени",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = adaptiveTitleSize),
                    modifier = Modifier.padding(bottom = adaptivePadding)
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
                                seekTo(startTime.toInt())
                            }
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
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = adaptiveSmallSize),
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(adaptivePadding))

                // Кнопки управления
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = {
                            videoViewRef?.let { videoView ->
                                if (isPlaying) {
                                    videoView.pause()
                                    isPlaying = false
                                } else {
                                    videoView.seekTo(startTime.toInt())
                                    currentVideoPosition = startTime
                                    videoView.start()
                                    isPlaying = true
                                }
                            }
                        },
                        modifier = Modifier.padding(horizontal = adaptivePadding / 2)
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
                        modifier = Modifier.padding(horizontal = adaptivePadding / 2),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("Сброс")
                    }
                }
            }

            // Правая колонка - настройки
            Column(
                modifier = Modifier
                    .weight(0.4f)
                    .padding(start = adaptivePadding)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Интервал кадров
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = adaptivePadding / 2)
                ) {
                    Text(
                        text = "Интервал кадров",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = adaptiveBodySize,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    TimeInputField(
                        value = frameIntervalText,
                        onValueChange = { updateFrameInterval(it) },
                        isError = frameIntervalError,
                        placeholder = "мс (100-5000)",
                        modifier = Modifier.width(adaptiveElementWidth),
                        textSize = adaptiveBodySize
                    )
                    if (frameIntervalError) {
                        Text(
                            text = "Диапазон: 100-5000 мс",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = adaptiveSmallSize
                        )
                    } else {
                        Text(
                            text = "≈ ${((endTime - startTime) / frameIntervalMs).toInt()} кадров",
                            color = Color.Gray,
                            fontSize = adaptiveSmallSize
                        )
                    }
                }

                Spacer(modifier = Modifier.height(adaptivePadding))

                // Время начала и окончания
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Начало",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = adaptiveBodySize,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        TimeInputField(
                            value = startTimeText,
                            onValueChange = { newText ->
                                startTimeText = newText
                                parseTimeToMillis(newText)?.let { millis ->
                                    updateStartTime(millis)
                                } ?: run { startTimeError = true }
                            },
                            isError = startTimeError,
                            placeholder = "мм:сс:мсс",
                            modifier = Modifier.width(adaptiveElementWidth),
                            textSize = adaptiveBodySize
                        )
                        if (startTimeError) {
                            Text(
                                text = "Некорректно",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = adaptiveSmallSize
                            )
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Конец",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = adaptiveBodySize,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        TimeInputField(
                            value = endTimeText,
                            onValueChange = { newText ->
                                endTimeText = newText
                                parseTimeToMillis(newText)?.let { millis ->
                                    updateEndTime(millis)
                                } ?: run { endTimeError = true }
                            },
                            isError = endTimeError,
                            placeholder = "мм:сс:мсс",
                            modifier = Modifier.width(adaptiveElementWidth),
                            textSize = adaptiveBodySize
                        )
                        if (endTimeError) {
                            Text(
                                text = "Некорректно",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = adaptiveSmallSize
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(adaptivePadding))

                // Слайдеры
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Начало: ${formatTimeWithMillis(startTime)}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = adaptiveBodySize,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Slider(
                        value = if (duration > 0) startTime.toFloat() / duration.toFloat() else 0f,
                        onValueChange = { value ->
                            val newTime = (value * duration).toLong().coerceIn(0L, endTime - 1000L)
                            updateStartTime(newTime)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Конец: ${formatTimeWithMillis(endTime)}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = adaptiveBodySize,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Slider(
                        value = if (duration > 0) endTime.toFloat() / duration.toFloat() else 0f,
                        onValueChange = { value ->
                            val newTime = (value * duration).toLong().coerceIn(startTime + 1000L, duration)
                            updateEndTime(newTime)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(adaptivePadding))

                // Информация о диапазоне
                Text(
                    text = "Диапазон: ${formatTimeWithMillis(startTime)} - ${formatTimeWithMillis(endTime)}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = adaptiveBodySize),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Длительность: ${formatTimeWithMillis(endTime - startTime)}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = adaptiveBodySize),
                    color = Color.Blue,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(adaptivePadding * 1.5f))

                // Кнопки действий
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = onCancel,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.weight(0.48f)
                    ) {
                        Text("Отмена")
                    }

                    Spacer(modifier = Modifier.width(adaptivePadding / 2))

                    Button(
                        onClick = { onTrimConfirmed(startTime, endTime, frameIntervalMs) },
                        enabled = endTime - startTime > 1000 && !startTimeError && !endTimeError && !frameIntervalError,
                        modifier = Modifier.weight(0.48f)
                    ) {
                        Text(
                            text = if (screenWidth < 400.dp) "Далее" else "Продолжить",
                            fontSize = adaptiveBodySize
                        )
                    }
                }
            }
        }
    } else {
        // Портретный режим (оригинальный, но адаптированный)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(adaptivePadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Обрежьте видео по времени",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = adaptiveTitleSize),
                modifier = Modifier.padding(bottom = adaptivePadding)
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
                            seekTo(startTime.toInt())
                        }
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
                style = MaterialTheme.typography.bodySmall.copy(fontSize = adaptiveSmallSize),
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(adaptivePadding))

            // Кнопки управления
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = {
                        videoViewRef?.let { videoView ->
                            if (isPlaying) {
                                videoView.pause()
                                isPlaying = false
                            } else {
                                videoView.seekTo(startTime.toInt())
                                currentVideoPosition = startTime
                                videoView.start()
                                isPlaying = true
                            }
                        }
                    },
                    modifier = Modifier.padding(horizontal = adaptivePadding / 2)
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
                    modifier = Modifier.padding(horizontal = adaptivePadding / 2),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("Сброс")
                }
            }

            Spacer(modifier = Modifier.height(adaptivePadding))

            // Интервал кадров
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = adaptivePadding / 2)
            ) {
                Text(
                    text = "Интервал извлечения кадров",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = adaptiveBodySize,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                TimeInputField(
                    value = frameIntervalText,
                    onValueChange = { updateFrameInterval(it) },
                    isError = frameIntervalError,
                    placeholder = "мс (100-5000)",
                    modifier = Modifier.width(adaptiveElementWidth),
                    textSize = adaptiveBodySize
                )
                if (frameIntervalError) {
                    Text(
                        text = "Допустимый диапазон: 100-5000 мс",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = adaptiveSmallSize
                    )
                } else {
                    Text(
                        text = "≈ ${((endTime - startTime) / frameIntervalMs).toInt()} кадров",
                        color = Color.Gray,
                        fontSize = adaptiveSmallSize
                    )
                }
            }

            Spacer(modifier = Modifier.height(adaptivePadding))

            // Поля для точного ввода времени
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Начало",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = adaptiveBodySize,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    TimeInputField(
                        value = startTimeText,
                        onValueChange = { newText ->
                            startTimeText = newText
                            parseTimeToMillis(newText)?.let { millis ->
                                updateStartTime(millis)
                            } ?: run { startTimeError = true }
                        },
                        isError = startTimeError,
                        placeholder = "мм:сс:мсс",
                        modifier = Modifier.width(adaptiveElementWidth),
                        textSize = adaptiveBodySize
                    )
                    if (startTimeError) {
                        Text(
                            text = "Некорректно",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = adaptiveSmallSize
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Конец",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = adaptiveBodySize,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    TimeInputField(
                        value = endTimeText,
                        onValueChange = { newText ->
                            endTimeText = newText
                            parseTimeToMillis(newText)?.let { millis ->
                                updateEndTime(millis)
                            } ?: run { endTimeError = true }
                        },
                        isError = endTimeError,
                        placeholder = "мм:сс:мсс",
                        modifier = Modifier.width(adaptiveElementWidth),
                        textSize = adaptiveBodySize
                    )
                    if (endTimeError) {
                        Text(
                            text = "Некорректно",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = adaptiveSmallSize
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(adaptivePadding))

            // Слайдер для начала времени
            Text(
                text = "Начало: ${formatTimeWithMillis(startTime)}",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = adaptiveBodySize,
                    fontWeight = FontWeight.Bold
                )
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
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = adaptiveBodySize,
                    fontWeight = FontWeight.Bold
                )
            )
            Slider(
                value = if (duration > 0) endTime.toFloat() / duration.toFloat() else 0f,
                onValueChange = { value ->
                    val newTime = (value * duration).toLong().coerceIn(startTime + 1000L, duration)
                    updateEndTime(newTime)
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(adaptivePadding / 2))

            // Информация о диапазоне
            Text(
                text = "Диапазон: ${formatTimeWithMillis(startTime)} - ${formatTimeWithMillis(endTime)}",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = adaptiveBodySize)
            )

            Text(
                text = "Длительность: ${formatTimeWithMillis(endTime - startTime)}",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = adaptiveBodySize),
                color = Color.Blue
            )

            Spacer(modifier = Modifier.height(adaptivePadding * 1.5f))

            // Кнопки действий
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.weight(0.48f)
                ) {
                    Text("Отмена")
                }

                Spacer(modifier = Modifier.width(adaptivePadding / 2))

                Button(
                    onClick = { onTrimConfirmed(startTime, endTime, frameIntervalMs) },
                    enabled = endTime - startTime > 1000 && !startTimeError && !endTimeError && !frameIntervalError,
                    modifier = Modifier.weight(0.48f)
                ) {
                    Text(
                        text = if (screenWidth < 400.dp) "Далее" else "Продолжить",
                        fontSize = adaptiveBodySize
                    )
                }
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
    modifier: Modifier = Modifier,
    textSize: androidx.compose.ui.unit.TextUnit = 16.sp
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            textAlign = TextAlign.Center,
            fontSize = textSize
        )
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
            parts.size == 3 -> {
                val minutes = parts[0].toLong()
                val seconds = parts[1].toLong()
                val millis = parts[2].toLong()
                (minutes * 60 * 1000) + (seconds * 1000) + millis
            }
            parts.size == 2 -> {
                val minutes = parts[0].toLong()
                val seconds = parts[1].toLong()
                (minutes * 60 * 1000) + (seconds * 1000)
            }
            parts.size == 1 -> {
                parts[0].toLong() * 1000
            }
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}