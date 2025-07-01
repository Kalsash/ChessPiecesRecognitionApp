package com.example.chesspiecesrecognition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import java.io.InputStream
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

    fun getDrawCropRectLambda(
        imageSize: Size,
        cornerSizePx: Float,
        rectStrokeWidthPx: Float,
        cornerStrokeWidthPx: Float,
        rectColor: Color = Color.Red,
        cornerColor: Color = Color.Blue
    ): androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit {
        return {
            val rectLeft = cropRect.left * imageSize.width
            val rectTop = cropRect.top * imageSize.height
            val rectRight = cropRect.right * imageSize.width
            val rectBottom = cropRect.bottom * imageSize.height
            val rectWidth = rectRight - rectLeft
            val rectHeight = rectBottom - rectTop

            drawRect(
                color = rectColor,
                topLeft = Offset(rectLeft, rectTop),
                size = Size(rectWidth, rectHeight),
                style = Stroke(width = rectStrokeWidthPx)
            )

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

    fun handleDragStart(start: Offset, imageSize: Size, cornerSizePx: Float) {
        val rectLeft = cropRect.left * imageSize.width
        val rectTop = cropRect.top * imageSize.height
        val rectRight = cropRect.right * imageSize.width
        val rectBottom = cropRect.bottom * imageSize.height

        dragMode = when {
            abs(start.x - rectLeft) < cornerSizePx &&
                    abs(start.y - rectTop) < cornerSizePx -> DragMode.RESIZE_TOP_LEFT
            abs(start.x - rectRight) < cornerSizePx &&
                    abs(start.y - rectTop) < cornerSizePx -> DragMode.RESIZE_TOP_RIGHT
            abs(start.x - rectLeft) < cornerSizePx &&
                    abs(start.y - rectBottom) < cornerSizePx -> DragMode.RESIZE_BOTTOM_LEFT
            abs(start.x - rectRight) < cornerSizePx &&
                    abs(start.y - rectBottom) < cornerSizePx -> DragMode.RESIZE_BOTTOM_RIGHT
            start.x in rectLeft..rectRight &&
                    start.y in rectTop..rectBottom -> DragMode.MOVE
            else -> DragMode.NONE
        }

        if (dragMode != DragMode.NONE) {
            isDragging = true
            dragStart = start
        }
    }

    fun handleDrag(change: Offset, imageSize: Size) {
        if (isDragging) {
            val dx = change.x / imageSize.width
            val dy = change.y / imageSize.height

            cropRect = when (dragMode) {
                DragMode.MOVE -> RectF(
                    (cropRect.left + dx).coerceIn(0f, 1f),
                    (cropRect.top + dy).coerceIn(0f, 1f),
                    (cropRect.right + dx).coerceIn(0f, 1f),
                    (cropRect.bottom + dy).coerceIn(0f, 1f)
                )
                DragMode.RESIZE_TOP_LEFT -> RectF(
                    (cropRect.left + dx).coerceIn(0f, cropRect.right - 0.1f),
                    (cropRect.top + dy).coerceIn(0f, cropRect.bottom - 0.1f),
                    cropRect.right,
                    cropRect.bottom
                )
                DragMode.RESIZE_TOP_RIGHT -> RectF(
                    cropRect.left,
                    (cropRect.top + dy).coerceIn(0f, cropRect.bottom - 0.1f),
                    (cropRect.right + dx).coerceIn(cropRect.left + 0.1f, 1f),
                    cropRect.bottom
                )
                DragMode.RESIZE_BOTTOM_LEFT -> RectF(
                    (cropRect.left + dx).coerceIn(0f, cropRect.right - 0.1f),
                    cropRect.top,
                    cropRect.right,
                    (cropRect.bottom + dy).coerceIn(cropRect.top + 0.1f, 1f)
                )
                DragMode.RESIZE_BOTTOM_RIGHT -> RectF(
                    cropRect.left,
                    cropRect.top,
                    (cropRect.right + dx).coerceIn(cropRect.left + 0.1f, 1f),
                    (cropRect.bottom + dy).coerceIn(cropRect.top + 0.1f, 1f)
                )
                else -> cropRect
            }
        }
    }

    fun handleDragEnd() {
        isDragging = false
        dragMode = DragMode.NONE
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
            text = "Настройте область обрезки для видео",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .pointerInput(Unit) {
                    if (imageCropper.showCropRect) {
                        detectDragGestures(
                            onDragStart = { start ->
                                imageCropper.handleDragStart(
                                    start,
                                    Size(size.width.toFloat(), size.height.toFloat()),
                                    dragCornerSizePx
                                )
                            },
                            onDrag = { change, dragAmount ->
                                imageCropper.handleDrag(
                                    dragAmount,
                                    Size(size.width.toFloat(), size.height.toFloat())
                                )
                            },
                            onDragEnd = { imageCropper.handleDragEnd() }
                        )
                    }
                }
        ) {
            imageCropper.currentBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )

                if (imageCropper.showCropRect) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        imageCropper.getDrawCropRectLambda(
                            Size(size.width.toFloat(), size.height.toFloat()),
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Отмена")
            }

            Spacer(modifier = Modifier.width(16.dp))

            Button(
                onClick = {
                    imageCropper.currentBitmap?.let { bitmap ->
                        imageCropper.croppedBitmap = imageCropper.autoCrop(bitmap)
                        imageCropper.saveCropParameters()
                        onCropConfirmed()
                    }
                }
            ) {
                Text("Подтвердить и обработать")
            }
        }
    }
}