package com.example.chesspiecesrecognition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
    var imageDisplaySize by mutableStateOf(Size.Zero)
    var imageOffset by mutableStateOf(Offset.Zero)

    // Переменные для масштабирования изображения
    private var _scale by mutableStateOf(1f)
    var translation by mutableStateOf(Offset.Zero)

    // Новое поле для автоопределения
    var autoDetectionEnabled by mutableStateOf(true)

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
            putBoolean("auto_detection_enabled", autoDetectionEnabled)
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
        autoDetectionEnabled = prefs.getBoolean("auto_detection_enabled", true)
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


