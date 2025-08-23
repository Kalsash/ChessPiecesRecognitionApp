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
import androidx.compose.ui.text.font.FontWeight
import android.media.MediaMetadataRetriever

@Composable
fun VideoTrimmerScreen(
    videoUri: Uri,
    onTrimConfirmed: (Long, Long) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var duration by remember { mutableStateOf(10000L) }
    var startTime by remember { mutableStateOf(0L) }
    var endTime by remember { mutableStateOf(10000L) }
    var isPlaying by remember { mutableStateOf(false) }
    var videoViewRef by remember { mutableStateOf<android.widget.VideoView?>(null) }

    LaunchedEffect(videoUri) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val videoDuration = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLong() ?: 10000L

            duration = videoDuration
            endTime = videoDuration
            retriever.release()
        } catch (e: Exception) {
            duration = 10000L
            endTime = 10000L
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
                        videoViewRef = this
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        )

        Spacer(modifier = Modifier.height(16.dp))

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
                        } else {
                            videoView.seekTo(startTime.toInt())
                            videoView.start()
                        }
                        isPlaying = !isPlaying
                    }
                },
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Text(if (isPlaying) "Пауза" else "Воспроизвести")
            }

            Button(
                onClick = {
                    videoViewRef?.seekTo(startTime.toInt())
                    isPlaying = false
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

        Text(
            text = "Начало: ${formatTime(startTime)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )

        Slider(
            value = startTime.toFloat() / duration.toFloat(),
            onValueChange = { value ->
                val newTime = (value * duration).toLong().coerceIn(0L, endTime - 1000L)
                startTime = newTime
                videoViewRef?.seekTo(startTime.toInt())
            },
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "Конец: ${formatTime(endTime)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )

        Slider(
            value = endTime.toFloat() / duration.toFloat(),
            onValueChange = { value ->
                val newTime = (value * duration).toLong().coerceIn(startTime + 1000L, duration)
                endTime = newTime
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Диапазон: ${formatTime(startTime)} - ${formatTime(endTime)}",
            style = MaterialTheme.typography.bodyMedium
        )

        Text(
            text = "Длительность: ${formatTime(endTime - startTime)}",
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
                onClick = { onTrimConfirmed(startTime, endTime) },
                enabled = endTime - startTime > 1000
            ) {
                Text("Продолжить к обрезке")
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}