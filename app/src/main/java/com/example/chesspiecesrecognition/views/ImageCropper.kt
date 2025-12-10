package com.example.chesspiecesrecognition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

sealed class DragMode {
    object NONE : DragMode()
    object MOVE : DragMode()
    object RESIZE_TOP_LEFT : DragMode()
    object RESIZE_TOP_RIGHT : DragMode()
    object RESIZE_BOTTOM_LEFT : DragMode()
    object RESIZE_BOTTOM_RIGHT : DragMode()
}

class ImageCropper(private val context: Context) {
    var cropRect by mutableStateOf(RectF(0.15f, 0.15f, 0.85f, 0.85f))
    var isDragging by mutableStateOf(false)
    var dragStart by mutableStateOf(Offset.Zero)
    var dragMode by mutableStateOf<DragMode>(DragMode.NONE)
    var showCropRect by mutableStateOf(true)
    var currentBitmap by mutableStateOf<Bitmap?>(null)
    var croppedBitmap by mutableStateOf<Bitmap?>(null)
    var containerSize by mutableStateOf(Size.Zero)
    var imageDisplaySize by mutableStateOf(Size.Zero)
    var imageOffset by mutableStateOf(Offset.Zero)

    // Переменные для масштабирования изображения
    private var _scale by mutableStateOf(1f)
    var translation by mutableStateOf(Offset.Zero)

    val scale: Float
        get() = _scale

    // Максимальное смещение для ограничения (50% от размера изображения) - только по X
    val maxTranslationX: Float
        get() = imageDisplaySize.width * 0.5f
    val maxTranslationY: Float
        get() = 0f // Убираем смещение по Y

    private val prefs = context.getSharedPreferences("CropPrefs", Context.MODE_PRIVATE)

    fun saveCropParameters() {
        prefs.edit().apply {
            putFloat("crop_left", cropRect.left)
            putFloat("crop_top", cropRect.top)
            putFloat("crop_right", cropRect.right)
            putFloat("crop_bottom", cropRect.bottom)
            apply()
        }
    }

    fun loadCropParameters() {
        cropRect = RectF(
            prefs.getFloat("crop_left", 0.15f),
            prefs.getFloat("crop_top", 0.15f),
            prefs.getFloat("crop_right", 0.85f),
            prefs.getFloat("crop_bottom", 0.85f)
        )
    }

    fun resetTransform() {
        _scale = 1f
        translation = Offset.Zero.copy(y = 0f) // Y всегда 0
    }

    fun autoCrop(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        val left = (cropRect.left * width).toInt()
        val top = (cropRect.top * height).toInt()
        val right = (cropRect.right * width).toInt()
        val bottom = (cropRect.bottom * height).toInt()

        return Bitmap.createBitmap(
            bitmap,
            left.coerceIn(0, width - 1),
            top.coerceIn(0, height - 1),
            (right - left).coerceIn(1, width - left),
            (bottom - top).coerceIn(1, height - top)
        )
    }

    fun updatePreview(bitmap: Bitmap) {
        croppedBitmap = autoCrop(bitmap)
    }

    // Преобразование координат экрана в координаты изображения (0-1)
    fun screenToImageCoordinates(screenPoint: Offset): Offset {
        if (imageDisplaySize.width <= 0 || imageDisplaySize.height <= 0) return Offset.Zero

        // Учитываем масштаб и смещение изображения
        val scaledWidth = imageDisplaySize.width * _scale
        val scaledHeight = imageDisplaySize.height * _scale

        val offsetX = imageOffset.x + translation.x + (imageDisplaySize.width - scaledWidth) / 2
        val offsetY = imageOffset.y + (imageDisplaySize.height - scaledHeight) / 2 // Убрали translation.y

        val xInImage = (screenPoint.x - offsetX) / scaledWidth
        val yInImage = (screenPoint.y - offsetY) / scaledHeight

        return Offset(
            xInImage.coerceIn(0f, 1f),
            yInImage.coerceIn(0f, 1f)
        )
    }

    // Преобразование координат изображения (0-1) в координаты экрана
    fun imageToScreenCoordinates(imagePoint: Offset): Offset {
        val scaledWidth = imageDisplaySize.width * _scale
        val scaledHeight = imageDisplaySize.height * _scale

        val offsetX = imageOffset.x + translation.x + (imageDisplaySize.width - scaledWidth) / 2
        val offsetY = imageOffset.y + (imageDisplaySize.height - scaledHeight) / 2 // Убрали translation.y

        return Offset(
            imagePoint.x * scaledWidth + offsetX,
            imagePoint.y * scaledHeight + offsetY
        )
    }

    // Упрощенная функция без MaterialTheme
    fun getDrawCropRectLambda(
        cornerSizePx: Float,
        rectStrokeWidthPx: Float,
        cornerStrokeWidthPx: Float,
        rectColor: Color = Color.Red,
        cornerColor: Color = Color.Blue,
        gridColor: Color = Color.White.copy(alpha = 0.7f),
        darkCellColor: Color = Color.Black.copy(alpha = 0.2f)
    ): androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit {
        return {
            if (imageDisplaySize.width > 0 && imageDisplaySize.height > 0) {
                // Преобразуем относительные координаты в экранные
                val rectLeft = imageToScreenCoordinates(Offset(cropRect.left, cropRect.top)).x
                val rectTop = imageToScreenCoordinates(Offset(cropRect.left, cropRect.top)).y
                val rectRight = imageToScreenCoordinates(Offset(cropRect.right, cropRect.bottom)).x
                val rectBottom = imageToScreenCoordinates(Offset(cropRect.right, cropRect.bottom)).y
                val rectWidth = rectRight - rectLeft
                val rectHeight = rectBottom - rectTop

                // Рисуем внешную рамку
                drawRect(
                    color = rectColor,
                    topLeft = Offset(rectLeft, rectTop),
                    size = Size(rectWidth, rectHeight),
                    style = Stroke(width = rectStrokeWidthPx)
                )

                // Рисуем вертикальные линии шахматной сетки
                for (i in 1 until 8) {
                    val x = rectLeft + (rectWidth * i / 8)
                    drawLine(
                        color = gridColor,
                        start = Offset(x, rectTop),
                        end = Offset(x, rectBottom),
                        strokeWidth = rectStrokeWidthPx / 2
                    )
                }

                // Рисуем горизонтальные линии шахматной сетки
                for (i in 1 until 8) {
                    val y = rectTop + (rectHeight * i / 8)
                    drawLine(
                        color = gridColor,
                        start = Offset(rectLeft, y),
                        end = Offset(rectRight, y),
                        strokeWidth = rectStrokeWidthPx / 2
                    )
                }

                // Закрашиваем черные клетки шахматной доски
                for (row in 0 until 8) {
                    for (col in 0 until 8) {
                        if ((row + col) % 2 == 1) {
                            val cellLeft = rectLeft + (rectWidth * col / 8)
                            val cellTop = rectTop + (rectHeight * row / 8)
                            val cellWidth = rectWidth / 8
                            val cellHeight = rectHeight / 8

                            drawRect(
                                color = darkCellColor,
                                topLeft = Offset(cellLeft, cellTop),
                                size = Size(cellWidth, cellHeight)
                            )
                        }
                    }
                }

                // Рисуем углы для изменения размера
                drawRect(
                    color = cornerColor,
                    topLeft = Offset(rectLeft, rectTop),
                    size = Size(cornerSizePx, cornerSizePx),
                    style = Stroke(width = cornerStrokeWidthPx)
                )

                drawRect(
                    color = cornerColor,
                    topLeft = Offset(rectRight - cornerSizePx, rectTop),
                    size = Size(cornerSizePx, cornerSizePx),
                    style = Stroke(width = cornerStrokeWidthPx)
                )

                drawRect(
                    color = cornerColor,
                    topLeft = Offset(rectLeft, rectBottom - cornerSizePx),
                    size = Size(cornerSizePx, cornerSizePx),
                    style = Stroke(width = cornerStrokeWidthPx)
                )

                drawRect(
                    color = cornerColor,
                    topLeft = Offset(rectRight - cornerSizePx, rectBottom - cornerSizePx),
                    size = Size(cornerSizePx, cornerSizePx),
                    style = Stroke(width = cornerStrokeWidthPx)
                )
            }
        }
    }

    fun handleDragStart(screenStart: Offset, cornerSizePx: Float) {
        val imageStart = screenToImageCoordinates(screenStart)

        val rectLeft = cropRect.left
        val rectTop = cropRect.top
        val rectRight = cropRect.right
        val rectBottom = cropRect.bottom

        // Преобразуем размер угла в относительные единицы
        val relativeCornerSize = cornerSizePx / (imageDisplaySize.width * _scale)

        dragMode = when {
            abs(imageStart.x - rectLeft) < relativeCornerSize &&
                    abs(imageStart.y - rectTop) < relativeCornerSize -> DragMode.RESIZE_TOP_LEFT
            abs(imageStart.x - rectRight) < relativeCornerSize &&
                    abs(imageStart.y - rectTop) < relativeCornerSize -> DragMode.RESIZE_TOP_RIGHT
            abs(imageStart.x - rectLeft) < relativeCornerSize &&
                    abs(imageStart.y - rectBottom) < relativeCornerSize -> DragMode.RESIZE_BOTTOM_LEFT
            abs(imageStart.x - rectRight) < relativeCornerSize &&
                    abs(imageStart.y - rectBottom) < relativeCornerSize -> DragMode.RESIZE_BOTTOM_RIGHT
            imageStart.x in rectLeft..rectRight &&
                    imageStart.y in rectTop..rectBottom -> DragMode.MOVE
            else -> DragMode.NONE
        }

        if (dragMode != DragMode.NONE) {
            isDragging = true
            dragStart = screenStart
        }
    }

    fun handleDrag(screenChange: Offset) {
        if (isDragging && imageDisplaySize.width > 0 && imageDisplaySize.height > 0) {
            // Преобразуем экранное смещение в относительное смещение изображения
            val dx = screenChange.x / (imageDisplaySize.width * _scale)
            val dy = screenChange.y / (imageDisplaySize.height * _scale)

            cropRect = when (dragMode) {
                DragMode.MOVE -> RectF(
                    (cropRect.left + dx).coerceIn(0f, 1f),
                    (cropRect.top + dy).coerceIn(0f, 1f),
                    (cropRect.right + dx).coerceIn(0f, 1f),
                    (cropRect.bottom + dy).coerceIn(0f, 1f)
                )
                DragMode.RESIZE_TOP_LEFT -> RectF(
                    (cropRect.left + dx).coerceIn(0f, cropRect.right - 0.05f),
                    (cropRect.top + dy).coerceIn(0f, cropRect.bottom - 0.05f),
                    cropRect.right,
                    cropRect.bottom
                )
                DragMode.RESIZE_TOP_RIGHT -> RectF(
                    cropRect.left,
                    (cropRect.top + dy).coerceIn(0f, cropRect.bottom - 0.05f),
                    (cropRect.right + dx).coerceIn(cropRect.left + 0.05f, 1f),
                    cropRect.bottom
                )
                DragMode.RESIZE_BOTTOM_LEFT -> RectF(
                    (cropRect.left + dx).coerceIn(0f, cropRect.right - 0.05f),
                    cropRect.top,
                    cropRect.right,
                    (cropRect.bottom + dy).coerceIn(cropRect.top + 0.05f, 1f)
                )
                DragMode.RESIZE_BOTTOM_RIGHT -> RectF(
                    cropRect.left,
                    cropRect.top,
                    (cropRect.right + dx).coerceIn(cropRect.left + 0.05f, 1f),
                    (cropRect.bottom + dy).coerceIn(cropRect.top + 0.05f, 1f)
                )
                else -> cropRect
            }

            currentBitmap?.let { updatePreview(it) }
        }
    }

    fun handleDragEnd() {
        isDragging = false
        dragMode = DragMode.NONE
    }

    // Обработка жестов масштабирования изображения
    fun handleTransformGesture(center: Offset, pan: Offset, zoom: Float, rotation: Float) {
        if (imageDisplaySize.width > 0 && imageDisplaySize.height > 0) {
            // Масштабирование изображения
            val newScale = (_scale * zoom).coerceIn(0.5f, 5f)

            // Панорамирование только по X
            val newTranslationX = translation.x + pan.x

            // Ограничиваем смещение только по X
            val limitedTranslationX = newTranslationX.coerceIn(-maxTranslationX, maxTranslationX)

            _scale = newScale
            translation = Offset(limitedTranslationX, 0f) // Y всегда 0
        }
    }

    // Установка масштаба изображения через слайдер
    fun updateScale(newScale: Float) {
        _scale = newScale.coerceIn(0.5f, 5f)
    }

    // Установка смещения по X
    fun updateTranslationX(newX: Float) {
        translation = Offset(newX.coerceIn(-maxTranslationX, maxTranslationX), 0f) // Y всегда 0
    }

    // Установка смещения по Y - всегда 0
    fun updateTranslationY(newY: Float) {
        translation = Offset(translation.x, 0f) // Y всегда 0
    }

    // Вычисление размера и позиции изображения на экране
    fun calculateImageDisplaySize(containerSize: Size, bitmap: Bitmap?) {
        if (bitmap == null) return

        val containerAspect = containerSize.width / containerSize.height
        val imageAspect = bitmap.width.toFloat() / bitmap.height.toFloat()

        if (containerAspect > imageAspect) {
            imageDisplaySize = Size(
                containerSize.height * imageAspect,
                containerSize.height
            )
            imageOffset = Offset(
                (containerSize.width - imageDisplaySize.width) / 2,
                0f
            )
        } else {
            imageDisplaySize = Size(
                containerSize.width,
                containerSize.width / imageAspect
            )
            imageOffset = Offset(
                0f,
                (containerSize.height - imageDisplaySize.height) / 2
            )
        }
    }

    // Получение параметров отображения изображения для Canvas
    fun getImageDrawParams(): Pair<Offset, Size> {
        val scaledWidth = imageDisplaySize.width * _scale
        val scaledHeight = imageDisplaySize.height * _scale

        val offsetX = imageOffset.x + translation.x + (imageDisplaySize.width - scaledWidth) / 2
        val offsetY = imageOffset.y + (imageDisplaySize.height - scaledHeight) / 2 // Убрали translation.y

        return Pair(Offset(offsetX, offsetY), Size(scaledWidth, scaledHeight))
    }

    // Установка прямоугольника обрезки на основе детекции шахматной доски
    fun setCropRectFromDetection(detectionRect: RectF) {
        // Добавляем небольшой запас по краям (5%)
        val margin = 0.05f
        val width = detectionRect.width()
        val height = detectionRect.height()

        cropRect = RectF(
            (detectionRect.left - width * margin).coerceIn(0f, 1f),
            (detectionRect.top - height * margin).coerceIn(0f, 1f),
            (detectionRect.right + width * margin).coerceIn(0f, 1f),
            (detectionRect.bottom + height * margin).coerceIn(0f, 1f)
        )

        // Обновляем предпросмотр
        currentBitmap?.let { updatePreview(it) }
    }
}

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
fun InfiniteRotationSpinner(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Canvas(modifier = modifier.size(80.dp)) {
        drawCircle(
            color = Color.Blue.copy(alpha = 0.3f), // Заменили MaterialTheme на фиксированный цвет
            radius = size.minDimension / 2
        )

        drawArc(
            color = Color.Blue, // Заменили MaterialTheme на фиксированный цвет
            startAngle = rotation,
            sweepAngle = 90f,
            useCenter = false,
            style = Stroke(width = 8.dp.toPx())
        )
    }
}

@Composable
fun FullScreenLoading(
    isVisible: Boolean,
    message: String = "Поиск шахматной доски"
) {
    if (isVisible) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.95f)), // Заменили MaterialTheme на фиксированный цвет
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Кастомный спиннер с анимацией
                InfiniteRotationSpinner()

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = message,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.Black // Заменили MaterialTheme на фиксированный цвет
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Прогресс бар с неопределенным прогрессом
                LinearProgressIndicator(
                    modifier = Modifier.width(200.dp),
                    color = Color.Blue // Заменили MaterialTheme на фиксированный цвет
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Это может занять несколько секунд",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray, // Заменили MaterialTheme на фиксированный цвет
                    fontSize = 14.sp
                )
            }
        }
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

    var showPreview by remember { mutableStateOf(false) }
    var isBlackPlayer by remember { mutableStateOf(false) }
    var isAutoDetecting by remember { mutableStateOf(false) }
    var autoDetectSuccess by remember { mutableStateOf(false) }
    var showAutoDetectError by remember { mutableStateOf(false) }
    var detectionMessage by remember { mutableStateOf("Анализируем изображение...") }

    val cornerSizePx = with(density) { 20.dp.toPx() }
    val dragCornerSizePx = with(density) { 40.dp.toPx() }
    val rectStrokeWidthPx = with(density) { 2.dp.toPx() }
    val cornerStrokeWidthPx = with(density) { 3.dp.toPx() }

    // Отображаем индикатор загрузки при автоматической детекции
    if (isAutoDetecting) {
        AutoCropProgressDialog(
            isVisible = isAutoDetecting,
            progressText = detectionMessage
        )
    }

    // Запускаем автоматическую детекцию при каждом изменении изображения
    LaunchedEffect(imageCropper.currentBitmap) {
        if (chessboardDetector != null &&
            imageCropper.currentBitmap != null &&
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Показываем сообщение об ошибке обнаружения
        if (showAutoDetectError) {
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = colorScheme.errorContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = "Warning",
                        tint = colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Шахматная доска не найдена\nНастройте область вручную",
                        color = colorScheme.onErrorContainer,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Text(
            text = if (showPreview) "Предпросмотр обрезки" else "Настройте область обрезки для видео",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
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
                        Text("Изображение не загружено")
                    }
                }
            }
        }

        if (!showPreview) {
            // Секция управления масштабом и смещением
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Масштаб
                Column {
                    Text(
                        text = "Масштаб: ${String.format("%.2f", imageCropper.scale)}",
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Slider(
                        value = imageCropper.scale,
                        onValueChange = { imageCropper.updateScale(it) },
                        valueRange = 0.5f..5f,
                        steps = 4500,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Диапазон: 0.50 - 5.00",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // Смещение по X (влево/вправо)
                Column {
                    Text(
                        text = "Смещение X: ${String.format("%.1f", imageCropper.translation.x)}",
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Slider(
                        value = imageCropper.translation.x,
                        onValueChange = { imageCropper.updateTranslationX(it) },
                        valueRange = -imageCropper.maxTranslationX..imageCropper.maxTranslationX,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Кнопка "Игрок играет черными фигурами"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isBlackPlayer,
                    onCheckedChange = { isBlackPlayer = it }
                )
                Text(
                    text = "Игрок играет черными фигурами",
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            // Кнопка "Сбросить все"
            Button(
                onClick = {
                    imageCropper.resetTransform()
                    imageCropper.cropRect = RectF(0.15f, 0.15f, 0.85f, 0.85f)
                    autoDetectSuccess = false
                    showAutoDetectError = false
                },
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .height(36.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.secondary
                )
            ) {
                Text("Сбросить все", fontSize = 14.sp)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (showPreview) {
                Button(
                    onClick = { showPreview = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.secondary
                    )
                ) {
                    Text("Назад к настройке")
                }
            } else {
                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.error
                    )
                ) {
                    Text("Отмена")
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            if (showPreview) {
                Button(
                    onClick = {
                        imageCropper.saveCropParameters()
                        onCropConfirmed(isBlackPlayer)
                    }
                ) {
                    Text("Подтвердить и обработать")
                }
            } else {
                Button(
                    onClick = {
                        imageCropper.currentBitmap?.let { bitmap ->
                            imageCropper.croppedBitmap = imageCropper.autoCrop(bitmap)
                            showPreview = true
                        }
                    },
                    enabled = !isAutoDetecting
                ) {
                    Text("Предпросмотр")
                }
            }
        }
    }
}