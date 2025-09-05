package com.example.chesspiecesrecognition

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import java.net.URLDecoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onItemClick: (String) -> Unit,
    viewModel: HistoryViewModel = viewModel()
) {
    val historyItems by viewModel.historyItems.observeAsState(emptyList())
    val lazyListState = rememberLazyListState()
    val density = LocalDensity.current

    // Состояние для хранения высоты кнопки
    var buttonHeight by remember { mutableStateOf(0.dp) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("История распознаваний") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                }
            }
        )

        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = lazyListState
            ) {
                items(historyItems) { item ->
                    HistoryItemCard(
                        item = item,
                        onItemClick = onItemClick,
                        onDelete = { viewModel.deleteHistoryItem(item.id, item.imageUri) }
                    )
                }
            }

            // Простой и отзывчивый скроллбар
            SimpleScrollbar(
                lazyListState = lazyListState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp),
                bottomPadding = buttonHeight // Передаем высоту кнопки
            )
        }

        // Кнопка возврата в главное меню с измерением высоты
        Box(
            modifier = Modifier
                .onSizeChanged { size ->
                    buttonHeight = with(density) { size.height.toDp() }
                }
        ) {
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
}
@Composable
fun SimpleScrollbar(
    lazyListState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp
) {
    val layoutInfo = lazyListState.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    if (totalItems <= 0) {
        return
    }

    // Фиксируем количество видимых элементов при первом вычислении
    val initialVisibleItems = remember { layoutInfo.visibleItemsInfo.size }

    // Высота видимой области (вычисляется один раз)
    val availableHeightPx = remember(totalItems, bottomPadding) {
        with(density) {
            (configuration.screenHeightDp.dp - bottomPadding).toPx()
        }
    }

    // Вычисляем высоту ползунка (один раз, используя initialVisibleItems)
    val thumbHeightPx = remember(totalItems, initialVisibleItems, availableHeightPx) {
        val thumbHeightRatio = initialVisibleItems.toFloat() / totalItems.toFloat()
        val calculatedHeight = availableHeightPx * thumbHeightRatio
        val minThumbHeight = with(density) { 40.dp.toPx() }
        calculatedHeight.coerceAtLeast(minThumbHeight)
    }

    val thumbHeight = with(density) { thumbHeightPx.toDp() }

    // Только позиция реагирует на скролл
    val targetPosition by remember(lazyListState.firstVisibleItemIndex, totalItems, initialVisibleItems) {
        derivedStateOf {
            with(density) {
                val firstVisible = lazyListState.firstVisibleItemIndex
                val totalScrollRange = (totalItems - initialVisibleItems).coerceAtLeast(1)
                val scrollProgress = firstVisible.toFloat() / totalScrollRange
                val rawPosition = scrollProgress * (availableHeightPx - thumbHeightPx)
                rawPosition.coerceIn(0f, availableHeightPx - thumbHeightPx).toDp()
            }
        }
    }

    // Анимированная позиция ползунка
    val thumbPosition by animateDpAsState(
        targetValue = targetPosition,
        animationSpec = tween(durationMillis = 150),
        label = "scrollbar_animation"
    )

    Box(
        modifier = modifier
            .width(8.dp)
            .fillMaxHeight()
    ) {
        Box(
            modifier = Modifier
                .width(8.dp)
                .height(thumbHeight)
                .offset(y = thumbPosition)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
        )
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
    var showCopySuccess by remember { mutableStateOf(false) }

    LaunchedEffect(item.imageUri) {
        val loadedBitmap = FileUtils.loadBitmapFromInternalStorage(context, item.imageUri)
        loadedBitmap?.let {
            bitmap.value = it.asImageBitmap()
        }
    }

    // Извлекаем FEN или PGN из ссылки
    val extractedData = remember(item.lichessUrl) {
        extractFenFromLichessUrl(item.lichessUrl) ?: extractPgnFromLichessUrl(item.lichessUrl)
    }
    val dataType = remember(item.lichessUrl) {
        when {
            extractFenFromLichessUrl(item.lichessUrl) != null -> "FEN"
            extractPgnFromLichessUrl(item.lichessUrl) != null -> "PGN"
            else -> "Ссылка"
        }
    }

    // Эффект для скрытия успешного сообщения о копировании
    LaunchedEffect(showCopySuccess) {
        if (showCopySuccess) {
            delay(2000)
            showCopySuccess = false
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

            // Заголовок с кнопкой копирования
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "$dataType:",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = {
                        copyToClipboard(
                            context = context,
                            text = extractedData ?: item.lichessUrl,
                            label = dataType
                        )
                        showCopySuccess = true
                    }
                ) {
                    if (showCopySuccess) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Скопировано",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Копировать $dataType"
                        )
                    }
                }
            }

            // Отображение извлеченных данных или ссылки с возможностью выделения
            SelectionContainer {
                Text(
                    text = extractedData ?: item.lichessUrl,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable {
                            copyToClipboard(
                                context = context,
                                text = extractedData ?: item.lichessUrl,
                                label = dataType
                            )
                            showCopySuccess = true
                        }
                )
            }

            // Сообщение об успешном копировании
            if (showCopySuccess) {
                Text(
                    text = "$dataType скопирован в буфер обмена!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

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

// Функция для копирования текста в буфер обмена
fun copyToClipboard(context: android.content.Context, text: String, label: String) {
    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
}

// Функция для извлечения FEN из ссылки lichess
fun extractFenFromLichessUrl(url: String): String? {
    return try {
        // Для ссылок вида: https://lichess.org/editor/rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBqKBNR_w_KQkq_-_0_1?color=white
        val pattern = Regex("lichess\\.org/editor/([^?]+)")
        val match = pattern.find(url)
        match?.groups?.get(1)?.value?.replace('_', ' ')
    } catch (e: Exception) {
        null
    }
}

// Функция для извлечения PGN из ссылки lichess
fun extractPgnFromLichessUrl(url: String): String? {
    return try {
        if (url.contains("lichess.org/paste") && url.contains("pgn=")) {
            val pgnParam = url.substringAfter("pgn=").substringBefore("&")
            URLDecoder.decode(pgnParam, "UTF-8")
        } else if (url.contains("lichess.org/analysis/pgn/")) {
            // Старый формат для обратной совместимости
            val pattern = Regex("lichess\\.org/analysis/pgn/(.+)")
            val match = pattern.find(url)
            match?.groups?.get(1)?.value?.let { encodedPgn ->
                URLDecoder.decode(encodedPgn, "UTF-8")
                    .replace("_", " ")
                    .replace("+", " ")
            }
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}