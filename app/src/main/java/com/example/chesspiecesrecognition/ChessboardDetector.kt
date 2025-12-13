package com.example.chesspiecesrecognition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

class ChessboardDetector(context: Context) {
    private val interpreter: Interpreter
    private val inputSize = 640 // Размер для YOLO модели
    private val confidenceThreshold = 0.5f
    private var modelType: ModelType = ModelType.YOLOV8_CENTER_NORMALIZED

    enum class ModelType {
        YOLOV8_CENTER_NORMALIZED, // [1, 5, 8400] где 5: x_center, y_center, width, height, confidence (все normalized 0-1)
        YOLOV8_XYXY_NORMALIZED    // [1, 6, 8400] где 6: x1, y1, x2, y2, confidence, class
    }

    init {
        val modelFile = loadModelFile(context)
        val options = Interpreter.Options()
        interpreter = Interpreter(modelFile, options)

        // Определяем тип модели
        determineModelType()
    }

    private fun loadModelFile(context: Context): ByteBuffer {
        return try {
            val assetFileDescriptor = context.assets.openFd("chessboard_detector.tflite")
            val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        } catch (e: Exception) {
            throw RuntimeException("Не удалось загрузить модель chessboard_detector.tflite", e)
        }
    }

    private fun determineModelType() {
        try {
            // Получаем информацию о выходных тензорах
            val outputTensorCount = interpreter.getOutputTensorCount()
            Log.d("ChessboardDetector", "Количество выходных тензоров: $outputTensorCount")

            for (i in 0 until outputTensorCount) {
                val tensor = interpreter.getOutputTensor(i)
                val shape = tensor.shape()
                Log.d("ChessboardDetector", "Тензор $i: форма = ${shape.joinToString(", ")}")

                if (shape.size == 3 && shape[1] == 5 && shape[2] == 8400) {
                    modelType = ModelType.YOLOV8_CENTER_NORMALIZED
                    Log.d("ChessboardDetector", "Определен тип: YOLOv8 Center Normalized [1, 5, 8400]")
                }
            }
        } catch (e: Exception) {
            Log.e("ChessboardDetector", "Ошибка при определении типа модели", e)
        }
    }

    data class Detection(
        val boundingBox: RectF,
        val confidence: Float
    )

    fun detectChessboard(bitmap: Bitmap): Detection? {
        return try {
            val origWidth = bitmap.width
            val origHeight = bitmap.height
            Log.d("ChessboardDetector", "Начинаем детекцию: ${origWidth}x${origHeight}")

            // Сохраняем оригинальное соотношение сторон при ресайзе
            val (resizedBitmap, paddingX, paddingY) = resizeWithPadding(bitmap, inputSize, inputSize)

            Log.d("ChessboardDetector", "Padding: x=$paddingX, y=$paddingY")

            // Конвертируем в ByteBuffer
            val inputBuffer = convertBitmapToByteBuffer(resizedBitmap)

            // Запускаем инференс
            val detections = detectYoloV8Center(inputBuffer, origWidth, origHeight, paddingX, paddingY)

            Log.d("ChessboardDetector", "Найдено детекций: ${detections.size}")

            // Возвращаем самое уверенное обнаружение
            val bestDetection = detections.maxByOrNull { it.confidence }
            bestDetection?.let { detection ->
                Log.d("ChessboardDetector", "Лучшая детекция: confidence=${detection.confidence}, box=${detection.boundingBox}")
            }

            bestDetection

        } catch (e: Exception) {
            Log.e("ChessboardDetector", "Ошибка при детекции шахматной доски", e)
            null
        }
    }

    private fun detectYoloV8Center(
        inputBuffer: ByteBuffer,
        origWidth: Int,
        origHeight: Int,
        paddingX: Float,
        paddingY: Float
    ): List<Detection> {
        val detections = mutableListOf<Detection>()

        try {
            // Для YOLOv8 с форматом [1, 6, 8400]
            val outputArray = Array(1) { Array(6) { FloatArray(8400) } }

            interpreter.runForMultipleInputsOutputs(
                arrayOf(inputBuffer),
                mapOf(0 to outputArray)
            )

            // Для отладки: логируем первые несколько значений
            Log.d("ChessboardDetector", "Первые значения вывода:")
            for (i in 0 until min(6, 8400)) {
                Log.d("ChessboardDetector",
                    "Box $i: x=${outputArray[0][0][i]}, y=${outputArray[0][1][i]}, " +
                            "w=${outputArray[0][2][i]}, h=${outputArray[0][3][i]}, conf=${outputArray[0][4][i]}")
            }

            // Вычисляем масштаб
            val scale = min(
                inputSize.toFloat() / origWidth,
                inputSize.toFloat() / origHeight
            )

            // Размеры после ресайза с сохранением aspect ratio
            val newWidth = (origWidth * scale).toInt()
            val newHeight = (origHeight * scale).toInt()

            Log.d("ChessboardDetector", "Scale: $scale, New size: ${newWidth}x${newHeight}")

            // Парсим вывод
            for (i in 0 until 8400) {
                val xCenterNormalized = outputArray[0][0][i] // x_center normalized (0-1) от ВСЕГО изображения 640x640
                val yCenterNormalized = outputArray[0][1][i] // y_center normalized (0-1) от ВСЕГО изображения 640x640
                val widthNormalized = outputArray[0][2][i]   // width normalized (0-1) от ВСЕГО изображения 640x640
                val heightNormalized = outputArray[0][3][i]  // height normalized (0-1) от ВСЕГО изображения 640x640
                val confidence = outputArray[0][4][i]

                if (confidence > confidenceThreshold) {
                    // Конвертируем normalized координаты в абсолютные для изображения 640x640
                    val xCenterAbs = xCenterNormalized * inputSize
                    val yCenterAbs = yCenterNormalized * inputSize
                    val widthAbs = widthNormalized * inputSize
                    val heightAbs = heightNormalized * inputSize

                    // Убираем padding
                    val xCenterNoPadding = xCenterAbs - paddingX
                    val yCenterNoPadding = yCenterAbs - paddingY

                    // Конвертируем center+size в xyxy
                    val x1 = xCenterNoPadding - widthAbs / 2
                    val y1 = yCenterNoPadding - heightAbs / 2
                    val x2 = xCenterNoPadding + widthAbs / 2
                    val y2 = yCenterNoPadding + heightAbs / 2

                    // Проверяем, что bounding box внутри unpadded области
                    if (x1 >= 0 && y1 >= 0 && x2 <= newWidth && y2 <= newHeight && x1 < x2 && y1 < y2) {
                        // Масштабируем обратно к исходному размеру
                        val left = (x1 / scale).coerceIn(0f, origWidth.toFloat())
                        val top = (y1 / scale).coerceIn(0f, origHeight.toFloat())
                        val right = (x2 / scale).coerceIn(0f, origWidth.toFloat())
                        val bottom = (y2 / scale).coerceIn(0f, origHeight.toFloat())

                        if (left < right && top < bottom) {
                            detections.add(Detection(RectF(left, top, right, bottom), confidence))
                        }
                    }
                }
            }

            Log.d("ChessboardDetector", "YOLOv8 Center: обработано ${detections.size} детекций")
        } catch (e: Exception) {
            Log.e("ChessboardDetector", "Ошибка в detectYoloV8Center", e)
        }

        return detections
    }

    private fun resizeWithPadding(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Triple<Bitmap, Float, Float> {
        val width = bitmap.width
        val height = bitmap.height

        // Вычисляем scale с сохранением aspect ratio
        val scale = min(
            targetWidth.toFloat() / width,
            targetHeight.toFloat() / height
        )

        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        // Создаем Bitmap с padding
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        val paddedBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)

        // Вычисляем padding
        val paddingX = (targetWidth - newWidth) / 2f
        val paddingY = (targetHeight - newHeight) / 2f

        // Рисуем resized изображение по центру
        val canvas = android.graphics.Canvas(paddedBitmap)
        canvas.drawBitmap(resizedBitmap, paddingX, paddingY, null)

        Log.d("ChessboardDetector", "Resize: $width x $height -> $newWidth x $newHeight (scale: $scale)")
        Log.d("ChessboardDetector", "Padding: $paddingX, $paddingY")

        return Triple(paddedBitmap, paddingX, paddingY)
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val inputBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        inputBuffer.order(ByteOrder.nativeOrder())
        inputBuffer.rewind()

        val intValues = IntArray(inputSize * inputSize)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        var pixel = 0
        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                val color = intValues[pixel++]

                // Нормализация значений пикселей (0-1)
                inputBuffer.putFloat(((color shr 16) and 0xFF) / 255.0f)
                inputBuffer.putFloat(((color shr 8) and 0xFF) / 255.0f)
                inputBuffer.putFloat((color and 0xFF) / 255.0f)
            }
        }

        return inputBuffer
    }

    fun cropChessboard(originalBitmap: Bitmap, detection: Detection): Bitmap {
        val rect = detection.boundingBox

        Log.d("ChessboardDetector", "Обрезка: box=$rect, image=${originalBitmap.width}x${originalBitmap.height}")

        return try {
            // Добавляем небольшой margin (2%) для уверенности
            val margin = 0.02f
            val width = rect.width()
            val height = rect.height()

            val left = (rect.left - width * margin).coerceIn(0f, originalBitmap.width.toFloat())
            val top = (rect.top - height * margin).coerceIn(0f, originalBitmap.height.toFloat())
            val right = (rect.right + width * margin).coerceIn(0f, originalBitmap.width.toFloat())
            val bottom = (rect.bottom + height * margin).coerceIn(0f, originalBitmap.height.toFloat())

            val finalLeft = left.toInt()
            val finalTop = top.toInt()
            val finalWidth = (right - left).toInt()
            val finalHeight = (bottom - top).toInt()

            Log.d("ChessboardDetector", "Обрезка с margin: left=$finalLeft, top=$finalTop, width=$finalWidth, height=$finalHeight")

            if (finalWidth > 0 && finalHeight > 0 &&
                finalLeft >= 0 && finalTop >= 0 &&
                finalLeft + finalWidth <= originalBitmap.width &&
                finalTop + finalHeight <= originalBitmap.height) {

                Bitmap.createBitmap(
                    originalBitmap,
                    finalLeft,
                    finalTop,
                    finalWidth,
                    finalHeight
                )
            } else {
                Log.e("ChessboardDetector", "Некорректные размеры для обрезки")
                originalBitmap
            }
        } catch (e: Exception) {
            Log.e("ChessboardDetector", "Ошибка при обрезке изображения", e)
            originalBitmap
        }
    }

    fun close() {
        try {
            interpreter.close()
        } catch (e: Exception) {
            Log.e("ChessboardDetector", "Ошибка при закрытии интерпретатора", e)
        }
    }
}