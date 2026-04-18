package com.example.chesspiecesrecognition

import ChessboardBackground
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val configuration = LocalConfiguration.current

    // Определяем размер экрана
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    // Определяем тип экрана на основе диагонали (примерные значения)
    val isSmallScreen = screenWidth < 360.dp || screenHeight < 600.dp
    val isLargeScreen = screenWidth > 600.dp  // Планшеты и ландшафтные режимы

    // Адаптивные значения
    val paddingSize = when {
        isSmallScreen -> 8.dp
        isLargeScreen -> 32.dp
        else -> 16.dp
    }

    val titleFontSize = when {
        isSmallScreen -> 18.sp
        isLargeScreen -> 28.sp
        else -> 24.sp
    }

    val bodyFontSize = when {
        isSmallScreen -> 14.sp
        isLargeScreen -> 18.sp
        else -> 16.sp
    }

    val subtitleFontSize = when {
        isSmallScreen -> 16.sp
        isLargeScreen -> 20.sp
        else -> 18.sp
    }

    val cardPadding = when {
        isSmallScreen -> 12.dp
        isLargeScreen -> 32.dp
        else -> 24.dp
    }

    val buttonPadding = when {
        isSmallScreen -> 8.dp
        isLargeScreen -> 24.dp
        else -> 16.dp
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ChessboardBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
        ) {
            // Адаптивный TopAppBar
            TopAppBar(
                title = {
                    Text(
                        "О приложении",
                        fontSize = if (isSmallScreen) 16.sp else 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .padding(paddingSize)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(paddingSize)
            ) {
                // Для больших экранов ограничиваем ширину контента
                val contentModifier = if (isLargeScreen) {
                    Modifier.fillMaxWidth(0.7f)
                } else {
                    Modifier.fillMaxWidth()
                }

                Card(
                    modifier = contentModifier,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = if (isSmallScreen) 4.dp else 8.dp
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(cardPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(paddingSize)
                    ) {
                        // Заголовок
                        Text(
                            text = "♔ Chess Pieces Recognition ♔",
                            fontSize = titleFontSize,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            lineHeight = titleFontSize.times(1.2f)
                        )

                        // Версия
                        Text(
                            text = "Версия 1.0.0",
                            fontSize = bodyFontSize,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )

                        // Описание
                        Text(
                            text = "Приложение для распознавания шахматных фигур",
                            fontSize = bodyFontSize,
                            textAlign = TextAlign.Justify,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = bodyFontSize.times(1.3f)
                        )

                        // Возможности
                        Text(
                            text = "Возможности:",
                            fontSize = subtitleFontSize,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                "• Получение FEN по фотографии шахматной позиции",
                                "• Обработка видео шахматной партии для создания PGN",
                                "• Сохранение истории распознаваний",
                                "• Получение ссылки на Lichess для дальнейшего использования"
                            ).forEach { item ->
                                Text(
                                    text = item,
                                    fontSize = bodyFontSize,
                                    textAlign = TextAlign.Start,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Технологии
                        Text(
                            text = "Технологии:",
                            fontSize = subtitleFontSize,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                "• TensorFlow Lite и Yolo для распознавания",
                                "• Jetpack Compose для интерфейса",
                                "• Yolo для обрезки изображений"
                            ).forEach { item ->
                                Text(
                                    text = item,
                                    fontSize = bodyFontSize,
                                    textAlign = TextAlign.Start,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Подпись
                        Text(
                            text = "Разработано для шахматистов",
                            fontSize = if (isSmallScreen) 12.sp else 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = paddingSize)
                        )
                    }
                }

                // Для больших экранов добавляем дополнительное пространство
                if (isLargeScreen) {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            // Адаптивная кнопка
            Button(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth(if (isLargeScreen) 0.7f else 1f)
                    .padding(buttonPadding),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Назад",
                    modifier = Modifier.size(if (isSmallScreen) 16.dp else 24.dp)
                )
                Spacer(modifier = Modifier.width(if (isSmallScreen) 4.dp else 8.dp))
                Text(
                    "Вернуться на главный экран",
                    fontSize = if (isSmallScreen) 14.sp else 16.sp
                )
            }
        }
    }
}
