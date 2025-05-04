package com.example.chesspiecesrecognition

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.graphics.BitmapFactory
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onItemClick: (String) -> Unit,
    viewModel: HistoryViewModel = viewModel()
) {
    val historyItems by viewModel.historyItems.observeAsState(emptyList())

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("История распознаваний") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                }
            }
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f) // важно: чтобы список не вытеснял кнопку вниз
                .fillMaxWidth()
        ) {
            items(historyItems) { item ->
                HistoryItemCard(
                    item = item,
                    onItemClick = onItemClick,
                    onDelete = { viewModel.deleteHistoryItem(item.id, item.imageUri) }
                )
            }
        }

        // Кнопка возврата в главное меню
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


@Composable
fun HistoryItemCard(
    item: HistoryItem,
    onItemClick: (String) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val bitmap = remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    LaunchedEffect(item.imageUri) {
        val loadedBitmap = FileUtils.loadBitmapFromInternalStorage(context, item.imageUri)
        loadedBitmap?.let {
            bitmap.value = it.asImageBitmap()
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Отображение изображения
            bitmap.value?.let { imageBitmap ->
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "Распознанная шахматная доска",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Отображение ссылки
            Text(
                text = "Ссылка на партию:",
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = item.lichessUrl,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Кнопки
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = { onItemClick(item.lichessUrl) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Открыть в браузере")
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Удалить")
                }
            }
        }
    }
}

@Composable
fun rememberImageBitmap(imageUri: String): androidx.compose.ui.graphics.ImageBitmap? {
    val context = LocalContext.current
    return remember(imageUri) {
        try {
            val file = File(imageUri)
            if (file.exists()) {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                bitmap?.asImageBitmap()
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}