package com.example.chesspiecesrecognition

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("О приложении") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                }
            }
        )

        Column(
            modifier = Modifier
                .weight(1f) // важно: чтобы контент не вытеснял кнопку вниз
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "♔ Chess Pieces Recognition ♔",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Версия 1.0.0",
                fontSize = 16.sp,
                color = Color.Gray
            )

            Text(
                text = "Приложение для распознавания шахматных фигур на доске с помощью искусственного интеллекта.",
                fontSize = 16.sp,
                textAlign = TextAlign.Justify
            )

            Text(
                text = "Возможности:",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "• Распознавание фигур на фотографиях\n" +
                        "• Обработка видео для создания PGN\n" +
                        "• История распознаваний\n" +
                        "• Экспорт в Lichess",
                fontSize = 16.sp,
                textAlign = TextAlign.Start
            )

            Text(
                text = "Технологии:",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "• TensorFlow Lite для распознавания\n" +
                        "• Jetpack Compose для интерфейса\n" +
                        "• UCrop для обрезки изображений",
                fontSize = 16.sp,
                textAlign = TextAlign.Start
            )

            Text(
                text = "Разработано с ♥ для шахматистов",
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 32.dp)
            )
        }

        // Кнопка возврата в главное меню (такая же как в HistoryScreen)
        Button(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Вернуться на главный экран")
        }
    }
}