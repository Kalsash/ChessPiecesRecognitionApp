package com.example.chesspiecesrecognition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

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
    var imageDisplaySize by mutableStateOf(Size.Zero) // Фактический размер изображения на экране
    var imageOffset by mutableStateOf(Offset.Zero) // Смещение изображения относительно контейнера

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

    fun autoCrop(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        // Преобразуем относительные координаты в пиксельные координаты изображения
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

        val xInImage = (screenPoint.x - imageOffset.x) / imageDisplaySize.width
        val yInImage = (screenPoint.y - imageOffset.y) / imageDisplaySize.height

        return Offset(
            xInImage.coerceIn(0f, 1f),
            yInImage.coerceIn(0f, 1f)
        )
    }

    // Преобразование координат изображения (0-1) в координаты экрана
    fun imageToScreenCoordinates(imagePoint: Offset): Offset {
        return Offset(
            imagePoint.x * imageDisplaySize.width + imageOffset.x,
            imagePoint.y * imageDisplaySize.height + imageOffset.y
        )
    }

    fun getDrawCropRectLambda(
        cornerSizePx: Float,
        rectStrokeWidthPx: Float,
        cornerStrokeWidthPx: Float,
        rectColor: Color = Color.Red,
        cornerColor: Color = Color.Blue
    ): androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit {
        return {
            if (imageDisplaySize.width > 0 && imageDisplaySize.height > 0) {
                // Преобразуем относительные координаты в экранные
                val rectLeft = cropRect.left * imageDisplaySize.width + imageOffset.x
                val rectTop = cropRect.top * imageDisplaySize.height + imageOffset.y
                val rectRight = cropRect.right * imageDisplaySize.width + imageOffset.x
                val rectBottom = cropRect.bottom * imageDisplaySize.height + imageOffset.y
                val rectWidth = rectRight - rectLeft
                val rectHeight = rectBottom - rectTop

                // Рисуем прямоугольник обрезки
                drawRect(
                    color = rectColor,
                    topLeft = Offset(rectLeft, rectTop),
                    size = Size(rectWidth, rectHeight),
                    style = Stroke(width = rectStrokeWidthPx)
                )

                // Рисуем углы
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
        // Преобразуем экранные координаты в координаты изображения
        val imageStart = screenToImageCoordinates(screenStart)

        val rectLeft = cropRect.left
        val rectTop = cropRect.top
        val rectRight = cropRect.right
        val rectBottom = cropRect.bottom

        // Преобразуем размер угла в относительные единицы
        val relativeCornerSize = cornerSizePx / imageDisplaySize.width

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
            val dx = screenChange.x / imageDisplaySize.width
            val dy = screenChange.y / imageDisplaySize.height

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

            // Автоматически обновляем предпросмотр при изменении области
            currentBitmap?.let { updatePreview(it) }
        }
    }

    fun handleDragEnd() {
        isDragging = false
        dragMode = DragMode.NONE
    }

    // Вычисление размера и позиции изображения на экране
    fun calculateImageDisplaySize(containerSize: Size, bitmap: Bitmap?) {
        if (bitmap == null) return

        val containerAspect = containerSize.width / containerSize.height
        val imageAspect = bitmap.width.toFloat() / bitmap.height.toFloat()

        if (containerAspect > imageAspect) {
            // Контейнер шире изображения - изображение занимает всю высоту
            imageDisplaySize = Size(
                containerSize.height * imageAspect,
                containerSize.height
            )
            imageOffset = Offset(
                (containerSize.width - imageDisplaySize.width) / 2,
                0f
            )
        } else {
            // Контейнер уже изображения - изображение занимает всю ширину
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
}

@Composable
fun ImageCropperScreen(
    imageCropper: ImageCropper,
    onCropConfirmed: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var showPreview by remember { mutableStateOf(false) }

    val cornerSizePx = with(density) { 20.dp.toPx() }
    val dragCornerSizePx = with(density) { 40.dp.toPx() }
    val rectStrokeWidthPx = with(density) { 2.dp.toPx() }
    val cornerStrokeWidthPx = with(density) { 3.dp.toPx() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
                        detectDragGestures(
                            onDragStart = { start ->
                                imageCropper.handleDragStart(
                                    start,
                                    dragCornerSizePx
                                )
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
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )

                    if (imageCropper.showCropRect) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            imageCropper.getDrawCropRectLambda(
                                cornerSizePx,
                                rectStrokeWidthPx,
                                cornerStrokeWidthPx
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
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("Назад к настройке")
                }
            } else {
                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
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
                        onCropConfirmed()
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
                    }
                ) {
                    Text("Предпросмотр")
                }
            }
        }
    }
}