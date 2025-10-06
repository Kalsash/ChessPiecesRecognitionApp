package com.example.chesspiecesrecognition

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun VideoProcessingScreen(
    progress: Int,
    status: String,
    currentFrame: Bitmap? = null,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            // Отображение текущего кадра
            currentFrame?.let { frame ->
                Box(
                    modifier = Modifier
                        .size(300.dp)
                        .background(Color.DarkGray)
                ) {
                    Image(
                        bitmap = frame.asImageBitmap(),
                        contentDescription = "Текущий кадр",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Индикатор прогресса
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Текст статуса
            Text(
                text = status,
                color = Color.White,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Прогресс в процентах
            Text(
                text = "$progress%",
                color = Color.White,
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Кнопка отмены
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red.copy(alpha = 0.7f),
                    contentColor = Color.White
                )
            ) {
                Text("Отменить обработку")
            }
        }
    }
}