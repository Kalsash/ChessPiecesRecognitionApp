package com.example.chesspiecesrecognition.views

import android.graphics.RectF
import android.content.res.Configuration
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.chesspiecesrecognition.ChessboardDetector
import com.example.chesspiecesrecognition.ImageCropper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


// Вынесите эти композиции за пределы основной функции
@Composable
fun AutoCropProgressDialog(
    isVisible: Boolean,
    progressText: String = "Автоматический поиск шахматной доски..."
) {
    if (isVisible) {
        Dialog(
            onDismissRequest = { }
        ) {
            Card(
                modifier = Modifier
                    .width(280.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .padding(24.dp)
                ) {
                    // Анимированный спиннер
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(48.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Поиск шахматной доски",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = progressText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    // Анимация точек для ожидания
                    AnimatedProgressDots()
                }
            }
        }
    }
}

@Composable
fun AnimatedProgressDots() {
    var dotState by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            dotState = (dotState + 1) % 4
        }
    }

    Row(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = ".".repeat(dotState),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun ImageCropperScreen(
    imageCropper: ImageCropper,
    chessboardDetector: ChessboardDetector? = null,
    onCropConfirmed: (Boolean) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme

    // Определяем ориентацию экрана
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    var showPreview by remember { mutableStateOf(false) }
    var isBlackPlayer by remember { mutableStateOf(false) }
    var isAutoDetecting by remember { mutableStateOf(false) }
    var autoDetectSuccess by remember { mutableStateOf(false) }
    var showAutoDetectError by remember { mutableStateOf(false) }
    var detectionMessage by remember { mutableStateOf("Анализируем изображение...") }

    // Адаптивные размеры в зависимости от ориентации
    val adaptivePadding = if (isPortrait) 16.dp else 8.dp
    val adaptiveSpacing = if (isPortrait) 8.dp else 4.dp
    val previewAspectRatio = if (isPortrait) 1f else 0.75f

    // Загружаем настройку автоопределения при инициализации
    LaunchedEffect(Unit) {
        imageCropper.loadCropParameters()
    }

    val cornerSizePx = with(density) { if (isPortrait) 20.dp.toPx() else 16.dp.toPx() }
    val dragCornerSizePx = with(density) { if (isPortrait) 40.dp.toPx() else 32.dp.toPx() }
    val rectStrokeWidthPx = with(density) { if (isPortrait) 2.dp.toPx() else 1.5.dp.toPx() }
    val cornerStrokeWidthPx = with(density) { if (isPortrait) 3.dp.toPx() else 2.dp.toPx() }

    // Отображаем индикатор загрузки при автоматической детекции
    if (isAutoDetecting) {
        AutoCropProgressDialog(
            isVisible = isAutoDetecting,
            progressText = detectionMessage
        )
    }

    // Запускаем автоматическую детекцию при каждом изменении изображения только если включено
    LaunchedEffect(imageCropper.currentBitmap) {
        if (chessboardDetector != null &&
            imageCropper.currentBitmap != null &&
            imageCropper.autoDetectionEnabled &&
            !autoDetectSuccess &&
            !showPreview &&
            !isAutoDetecting
        ) {
            isAutoDetecting = true
            detectionMessage = "Загружаем изображение..."

            // Небольшая задержка для плавного отображения индикатора
            delay(300)

            try {
                detectionMessage = "Анализируем изображение..."

                // Запускаем детекцию в IO dispatcher
                val detection = withContext(Dispatchers.IO) {
                    chessboardDetector.detectChessboard(imageCropper.currentBitmap!!)
                }

                if (detection != null) {
                    detectionMessage = "Настраиваем область обрезки..."

                    // Преобразуем абсолютные координаты в относительные (0-1)
                    val bitmap = imageCropper.currentBitmap!!
                    val width = bitmap.width.toFloat()
                    val height = bitmap.height.toFloat()

                    val relativeRect = RectF(
                        detection.boundingBox.left / width,
                        detection.boundingBox.top / height,
                        detection.boundingBox.right / width,
                        detection.boundingBox.bottom / height
                    )

                    // Устанавливаем прямоугольник обрезки
                    imageCropper.setCropRectFromDetection(relativeRect)
                    autoDetectSuccess = true
                    showAutoDetectError = false

                    // Задержка перед скрытием индикатора
                    delay(500)
                } else {
                    // Шахматная доска не найдена
                    autoDetectSuccess = false
                    showAutoDetectError = true
                    detectionMessage = "Доска не найдена"

                    // Показываем сообщение об ошибке
                    scope.launch {
                        delay(2000)
                        showAutoDetectError = false
                    }
                }
            } catch (e: Exception) {
                // Ошибка при детекции
                autoDetectSuccess = false
                showAutoDetectError = true
                detectionMessage = "Ошибка анализа"
                Log.e("ImageCropperScreen", "Ошибка авто-детекции", e)

                scope.launch {
                    delay(2000)
                    showAutoDetectError = false
                }
            } finally {
                isAutoDetecting = false
            }
        }
    }

    // Сбрасываем статус авто-детекции при переключении на предпросмотр и обратно
    LaunchedEffect(showPreview) {
        if (!showPreview) {
            autoDetectSuccess = false
            showAutoDetectError = false
        }
    }

    // Адаптивный лейаут в зависимости от ориентации
    if (isPortrait) {
        // Вертикальный лейаут для портретной ориентации
        PortraitLayout(
            imageCropper = imageCropper,
            showPreview = showPreview,
            isBlackPlayer = isBlackPlayer,
            autoDetectSuccess = autoDetectSuccess,
            showAutoDetectError = showAutoDetectError,
            isAutoDetecting = isAutoDetecting,
            adaptivePadding = adaptivePadding,
            adaptiveSpacing = adaptiveSpacing,
            previewAspectRatio = previewAspectRatio,
            cornerSizePx = cornerSizePx,
            dragCornerSizePx = dragCornerSizePx,
            rectStrokeWidthPx = rectStrokeWidthPx,
            cornerStrokeWidthPx = cornerStrokeWidthPx,
            onBlackPlayerChanged = { isBlackPlayer = it },
            onResetAll = {
                imageCropper.resetTransform()
                imageCropper.cropRect = RectF(0.15f, 0.15f, 0.85f, 0.85f)
                autoDetectSuccess = false
                showAutoDetectError = false
            },
            onPreviewClick = {
                imageCropper.currentBitmap?.let { bitmap ->
                    imageCropper.croppedBitmap = imageCropper.autoCrop(bitmap)
                    showPreview = true
                }
            },
            onBackToEdit = { showPreview = false },
            onConfirm = {
                imageCropper.saveCropParameters()
                onCropConfirmed(isBlackPlayer)
            },
            onCancel = onCancel
        )
    } else {
        // Горизонтальный лейаут для ландшафтной ориентации
        LandscapeLayout(
            imageCropper = imageCropper,
            showPreview = showPreview,
            isBlackPlayer = isBlackPlayer,
            autoDetectSuccess = autoDetectSuccess,
            showAutoDetectError = showAutoDetectError,
            isAutoDetecting = isAutoDetecting,
            adaptivePadding = adaptivePadding,
            adaptiveSpacing = adaptiveSpacing,
            previewAspectRatio = previewAspectRatio,
            cornerSizePx = cornerSizePx,
            dragCornerSizePx = dragCornerSizePx,
            rectStrokeWidthPx = rectStrokeWidthPx,
            cornerStrokeWidthPx = cornerStrokeWidthPx,
            onBlackPlayerChanged = { isBlackPlayer = it },
            onResetAll = {
                imageCropper.resetTransform()
                imageCropper.cropRect = RectF(0.15f, 0.15f, 0.85f, 0.85f)
                autoDetectSuccess = false
                showAutoDetectError = false
            },
            onPreviewClick = {
                imageCropper.currentBitmap?.let { bitmap ->
                    imageCropper.croppedBitmap = imageCropper.autoCrop(bitmap)
                    showPreview = true
                }
            },
            onBackToEdit = { showPreview = false },
            onConfirm = {
                imageCropper.saveCropParameters()
                onCropConfirmed(isBlackPlayer)
            },
            onCancel = onCancel
        )
    }
}

@Composable
private fun PortraitLayout(
    imageCropper: ImageCropper,
    showPreview: Boolean,
    isBlackPlayer: Boolean,
    autoDetectSuccess: Boolean,
    showAutoDetectError: Boolean,
    isAutoDetecting: Boolean,
    adaptivePadding: Dp,
    adaptiveSpacing: Dp,
    previewAspectRatio: Float,
    cornerSizePx: Float,
    dragCornerSizePx: Float,
    rectStrokeWidthPx: Float,
    cornerStrokeWidthPx: Float,
    onBlackPlayerChanged: (Boolean) -> Unit,
    onResetAll: () -> Unit,
    onPreviewClick: () -> Unit,
    onBackToEdit: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(adaptivePadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Показываем сообщение об ошибке обнаружения
        if (showAutoDetectError) {
            AdaptiveErrorCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = adaptivePadding)
            )
        }

        // Заголовок
        Text(
            text = if (showPreview) "Предпросмотр обрезки" else "Настройте область обрезки",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = adaptivePadding),
            textAlign = TextAlign.Center
        )

        // Область изображения
        ImageCropperArea(
            imageCropper = imageCropper,
            showPreview = showPreview,
            autoDetectSuccess = autoDetectSuccess,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(previewAspectRatio),
            cornerSizePx = cornerSizePx,
            dragCornerSizePx = dragCornerSizePx,
            rectStrokeWidthPx = rectStrokeWidthPx,
            cornerStrokeWidthPx = cornerStrokeWidthPx
        )

        // Управляющие элементы под изображением
        if (!showPreview) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = adaptivePadding),
                verticalArrangement = Arrangement.spacedBy(adaptiveSpacing)
            ) {
                // Контроли масштабирования
                ScaleControls(imageCropper = imageCropper)

                // Контроли смещения
                OffsetControls(imageCropper = imageCropper)

                // Чекбоксы
                Column(
                    verticalArrangement = Arrangement.spacedBy(adaptiveSpacing)
                ) {
                    PlayerColorCheckbox(
                        isBlackPlayer = isBlackPlayer,
                        onCheckedChange = onBlackPlayerChanged
                    )

                    AutoDetectionCheckbox(imageCropper = imageCropper)
                }

                // Кнопка сброса
                ResetButton(
                    onClick = onResetAll,
                    modifier = Modifier.padding(top = adaptivePadding)
                )
            }
        }

        // Нижние кнопки
        BottomButtons(
            showPreview = showPreview,
            isAutoDetecting = isAutoDetecting,
            onPreviewClick = onPreviewClick,
            onBackToEdit = onBackToEdit,
            onConfirm = onConfirm,
            onCancel = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = adaptivePadding)
        )
    }
}

@Composable
private fun LandscapeLayout(
    imageCropper: ImageCropper,
    showPreview: Boolean,
    isBlackPlayer: Boolean,
    autoDetectSuccess: Boolean,
    showAutoDetectError: Boolean,
    isAutoDetecting: Boolean,
    adaptivePadding: Dp,
    adaptiveSpacing: Dp,
    previewAspectRatio: Float,
    cornerSizePx: Float,
    dragCornerSizePx: Float,
    rectStrokeWidthPx: Float,
    cornerStrokeWidthPx: Float,
    onBlackPlayerChanged: (Boolean) -> Unit,
    onResetAll: () -> Unit,
    onPreviewClick: () -> Unit,
    onBackToEdit: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(adaptivePadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Левая часть - изображение
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Показываем сообщение об ошибке обнаружения
            if (showAutoDetectError) {
                AdaptiveErrorCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = adaptivePadding)
                )
            }

            // Заголовок
            Text(
                text = if (showPreview) "Предпросмотр" else "Настройка обрезки",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = adaptivePadding),
                textAlign = TextAlign.Center
            )

            // Область изображения
            ImageCropperArea(
                imageCropper = imageCropper,
                showPreview = showPreview,
                autoDetectSuccess = autoDetectSuccess,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(previewAspectRatio),
                cornerSizePx = cornerSizePx,
                dragCornerSizePx = dragCornerSizePx,
                rectStrokeWidthPx = rectStrokeWidthPx,
                cornerStrokeWidthPx = cornerStrokeWidthPx
            )
        }

        Spacer(modifier = Modifier.width(adaptivePadding))

        // Правая часть - элементы управления
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            if (!showPreview) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(adaptiveSpacing)
                ) {
                    // Контроли масштабирования
                    ScaleControls(imageCropper = imageCropper)

                    // Контроли смещения
                    OffsetControls(imageCropper = imageCropper)

                    // Чекбоксы
                    Column(
                        verticalArrangement = Arrangement.spacedBy(adaptiveSpacing)
                    ) {
                        PlayerColorCheckbox(
                            isBlackPlayer = isBlackPlayer,
                            onCheckedChange = onBlackPlayerChanged
                        )

                        AutoDetectionCheckbox(imageCropper = imageCropper)
                    }

                    // Кнопка сброса
                    ResetButton(
                        onClick = onResetAll,
                        modifier = Modifier.padding(top = adaptivePadding)
                    )
                }
            } else {
                // В режиме предпросмотра оставляем пустое пространство
                Spacer(modifier = Modifier.weight(1f))
            }

            // Нижние кнопки
            BottomButtons(
                showPreview = showPreview,
                isAutoDetecting = isAutoDetecting,
                onPreviewClick = onPreviewClick,
                onBackToEdit = onBackToEdit,
                onConfirm = onConfirm,
                onCancel = onCancel,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ImageCropperArea(
    imageCropper: ImageCropper,
    showPreview: Boolean,
    autoDetectSuccess: Boolean,
    modifier: Modifier,
    cornerSizePx: Float,
    dragCornerSizePx: Float,
    rectStrokeWidthPx: Float,
    cornerStrokeWidthPx: Float
) {
    Box(
        modifier = modifier
            .onSizeChanged { size ->
                imageCropper.containerSize = Size(size.width.toFloat(), size.height.toFloat())
                imageCropper.calculateImageDisplaySize(
                    Size(size.width.toFloat(), size.height.toFloat()),
                    imageCropper.currentBitmap
                )
            }
            .pointerInput(showPreview) {
                if (imageCropper.showCropRect && !showPreview) {
                    // Обработка масштабирования изображения (двумя пальцами)
                    detectTransformGestures { centroid, pan, zoom, rotation ->
                        imageCropper.handleTransformGesture(centroid, pan, zoom, rotation)
                    }
                }
            }
            .pointerInput(showPreview) {
                if (imageCropper.showCropRect && !showPreview) {
                    // Обработка перетаскивания для изменения области обрезки (одним пальцем)
                    detectDragGestures(
                        onDragStart = { start ->
                            imageCropper.handleDragStart(start, dragCornerSizePx)
                        },
                        onDrag = { change, dragAmount ->
                            imageCropper.handleDrag(dragAmount)
                        },
                        onDragEnd = { imageCropper.handleDragEnd() }
                    )
                }
            }
    ) {
        if (showPreview) {
            imageCropper.croppedBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Предпросмотр обрезки",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        } else {
            imageCropper.currentBitmap?.let { bitmap ->
                // Отображаем изображение с учетом масштаба и смещения
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val (offset, size) = imageCropper.getImageDrawParams()

                    // Рисуем изображение с помощью nativeCanvas для лучшего контроля
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawBitmap(
                            bitmap,
                            null,
                            android.graphics.RectF(
                                offset.x,
                                offset.y,
                                offset.x + size.width,
                                offset.y + size.height
                            ),
                            null
                        )
                    }

                    // Рисуем рамку обрезки поверх изображения
                    if (imageCropper.showCropRect) {
                        imageCropper.getDrawCropRectLambda(
                            cornerSizePx,
                            rectStrokeWidthPx,
                            cornerStrokeWidthPx,
                            rectColor = if (autoDetectSuccess) Color.Green else Color.Red,
                            cornerColor = if (autoDetectSuccess) Color.Green else Color.Blue,
                            gridColor = Color.White.copy(alpha = 0.7f),
                            darkCellColor = Color.Black.copy(alpha = 0.15f)
                        )()
                    }
                }
            } ?: run {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Изображение не загружено", textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun AdaptiveErrorCard(modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.errorContainer
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = "Warning",
                tint = colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Шахматная доска не найдена\nНастройте область вручную",
                color = colorScheme.onErrorContainer,
                fontSize = 12.sp,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun ScaleControls(imageCropper: ImageCropper) {
    Column {
        Text(
            text = "Масштаб: ${String.format("%.2f", imageCropper.scale)}",
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Slider(
            value = imageCropper.scale,
            onValueChange = { imageCropper.updateScale(it) },
            valueRange = 0.5f..2.2f,
            steps = 4500,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun OffsetControls(imageCropper: ImageCropper) {
    Column {
        Text(
            text = "Смещение X: ${String.format("%.1f", imageCropper.translation.x)}",
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Slider(
            value = imageCropper.translation.x,
            onValueChange = { imageCropper.updateTranslationX(it) },
            valueRange = -imageCropper.maxTranslationX..imageCropper.maxTranslationX,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PlayerColorCheckbox(
    isBlackPlayer: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isBlackPlayer,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = "Игрок играет черными фигурами",
            fontSize = 14.sp,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
private fun AutoDetectionCheckbox(imageCropper: ImageCropper) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = imageCropper.autoDetectionEnabled,
            onCheckedChange = {
                imageCropper.autoDetectionEnabled = it
                // Сохраняем настройку при изменении
                imageCropper.saveCropParameters()
            },
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = "Автоопределение доски",
            fontSize = 14.sp,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
private fun ResetButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(32.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondary
        )
    ) {
        Text("Сбросить все", fontSize = 12.sp)
    }
}

@Composable
private fun BottomButtons(
    showPreview: Boolean,
    isAutoDetecting: Boolean,
    onPreviewClick: () -> Unit,
    onBackToEdit: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (showPreview) {
            Button(
                onClick = onBackToEdit,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text("Назад", fontSize = 14.sp)
            }
        } else {
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text("Отмена", fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (showPreview) {
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f)
            ) {
                Text("Подтвердить", fontSize = 14.sp)
            }
        } else {
            Button(
                onClick = onPreviewClick,
                enabled = !isAutoDetecting,
                modifier = Modifier.weight(1f)
            ) {
                Text("Предпросмотр", fontSize = 14.sp)
            }
        }
    }
}