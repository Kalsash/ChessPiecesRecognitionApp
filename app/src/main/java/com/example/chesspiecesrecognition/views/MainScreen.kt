package com.example.chesspiecesrecognition.views

import ActionButton
import ChessboardBackground
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.chesspiecesrecognition.HistoryViewModel
import org.tensorflow.lite.Interpreter

@Composable
fun MainScreen(
    tfLiteInterpreter: Interpreter,
    croppedImageUri: Uri?,
    isLoading: Boolean,
    loadingType: String,
    onRecognizeImage: (Uri) -> Unit,
    onRecognize3D: (Uri) -> Unit,
    onShowHistory: () -> Unit,
    onProcessVideo: (Uri) -> Unit,
    onShowAbout: () -> Unit,
    onFenEditor: () -> Unit,
    viewModel: HistoryViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Лаунчеры для выбора файлов
    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onRecognizeImage(it) }
    }

    val image3DLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onRecognize3D(it) }
    }

    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onProcessVideo(it) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ChessboardBackground(modifier = Modifier.fillMaxSize())

        if (isLoading) {
            // ПОЛНОЭКРАННЫЙ ИНДИКАТОР ЗАГРУЗКИ - КНОПКИ ПОЛНОСТЬЮ СКРЫТЫ
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = when (loadingType) {
                            "2D" -> "2D распознавание шахматных фигур..."
                            "3D" -> "3D распознавание шахматных фигур..."
                            "video" -> "Подготовка видео..."
                            else -> "Обработка..."
                        },
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Пожалуйста, подождите",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            // ОСНОВНОЙ ЭКРАН С КНОПКАМИ - ПОКАЗЫВАЕТСЯ ТОЛЬКО КОГДА НЕТ ЗАГРУЗКИ
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Заголовок
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "♔ Chess Pieces Recognition ♔",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(shadow = Shadow(color = Color.Black, blurRadius = 4f)),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Основной контент с кнопками
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Показать предыдущее изображение, если есть
                    if (croppedImageUri != null) {
                        Image(
                            painter = rememberAsyncImagePainter(croppedImageUri),
                            contentDescription = "Выбранное фото",
                            modifier = Modifier
                                .size(200.dp)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.3f), shape = RoundedCornerShape(12.dp))
                        )
                    }

                    // ВСЕ КНОПКИ - ПОЯВЛЯЮТСЯ ТОЛЬКО КОГДА НЕТ ЗАГРУЗКИ
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ActionButton(
                            text = "Распознать 2D фигуры по фото",
                            icon = Icons.Default.Search
                        ) {
                            imageLauncher.launch("image/*")
                        }

                        ActionButton(
                            text = "Распознать 3D фигуры по фото",
                            icon = Icons.Default.Search
                        ) {
                            image3DLauncher.launch("image/*")
                        }

                        ActionButton(
                            text = "Извлечь шахматную партию из видео",
                            icon = Icons.Default.PlayArrow
                        ) {
                            videoLauncher.launch("video/*")
                        }

                        ActionButton(
                            text = "FEN-редактор",
                            icon = Icons.Default.Edit
                        ) {
                            onFenEditor()
                        }

                        ActionButton(
                            text = "История распознаваний",
                            icon = Icons.Default.List
                        ) {
                            onShowHistory()
                        }

                        ActionButton(
                            text = "О приложении",
                            icon = Icons.Default.Info
                        ) {
                            onShowAbout()
                        }
                    }
                }

                // Футер
                Text(
                    text = "ChessReco",
                    color = Color.DarkGray.copy(alpha = 0.9f),
                    fontSize = 16.sp
                )
            }
        }
    }
}