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
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

data class Detection3D(
    val boundingBox: RectF,
    val confidence: Float,
    val classId: Int,
    val className: String,
    val fenSymbol: Char
)

data class RecognitionResult3D(
    val fen: String,
    val detections: List<Detection3D>,
    val success: Boolean,
    val error: String? = null
)

class Chess3DRecognizer(context: Context) {
    private val interpreter: Interpreter
    private val inputSize = 416
    private val confidenceThreshold = 0.6f
    private val iouThreshold = 0.45f

    private val classToFen = mapOf(
        0 to 'P',  // white-pawn
        1 to 'R',  // white-rook
        2 to 'Q',  // white-queen
        3 to 'k',  // black-king
        4 to 'B',  // white-bishop
        5 to 'N',  // white-knight
        6 to 'q',  // black-queen
        7 to 'K',  // white-king
        8 to 'p',  // black-pawn
        9 to 'r',  // black-rook
        10 to 'b', // black-bishop
        11 to 'n'  // black-knight
    )

    private val className = mapOf(
        0 to "white-pawn",
        1 to "white-rook",
        2 to "white-queen",
        3 to "black-king",
        4 to "white-bishop",
        5 to "white-knight",
        6 to "black-queen",
        7 to "white-king",
        8 to "black-pawn",
        9 to "black-rook",
        10 to "black-bishop",
        11 to "black-knight"
    )

    init {
        val modelFile = loadModelFile(context)
        val options = Interpreter.Options().apply {
            setNumThreads(4)
        }
        interpreter = Interpreter(modelFile, options)

        try {
            Log.d("Chess3DRecognizer", "Model loaded successfully")
            Log.d("Chess3DRecognizer", "Input tensor shape: ${interpreter.getInputTensor(0).shape().contentToString()}")
            Log.d("Chess3DRecognizer", "Output tensor count: ${interpreter.getOutputTensorCount()}")

            for (i in 0 until interpreter.getOutputTensorCount()) {
                val shape = interpreter.getOutputTensor(i).shape()
                Log.d("Chess3DRecognizer", "Output tensor $i shape: ${shape.contentToString()}")
            }
        } catch (e: Exception) {
            Log.e("Chess3DRecognizer", "Error getting tensor info", e)
        }
    }

    private fun loadModelFile(context: Context): ByteBuffer {
        return try {
            val assetFileDescriptor = context.assets.openFd("chess3d.tflite")
            val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        } catch (e: Exception) {
            throw RuntimeException("Не удалось загрузить модель chess3d.tflite", e)
        }
    }

    fun recognize3DChessboard(bitmap: Bitmap): RecognitionResult3D {
        return try {
            val origWidth = bitmap.width
            val origHeight = bitmap.height
            Log.d("Chess3DRecognizer", "Начинаем распознавание: ${origWidth}x${origHeight}")

            val (resizedBitmap, paddingX, paddingY) = resizeWithPadding(bitmap, inputSize, inputSize)
            val inputBuffer = convertBitmapToByteBuffer(resizedBitmap)

            val detections = processYOLOOutput(inputBuffer, origWidth, origHeight, paddingX, paddingY)

            Log.d("Chess3DRecognizer", "Найдено фигур: ${detections.size}")

            val filteredDetections = applyNMS(detections)
            Log.d("Chess3DRecognizer", "После NMS осталось: ${filteredDetections.size}")

            val fen = createFENFromDetections(filteredDetections, origWidth, origHeight)

            RecognitionResult3D(
                fen = fen,
                detections = filteredDetections,
                success = true
            )

        } catch (e: Exception) {
            Log.e("Chess3DRecognizer", "Ошибка при распознавании шахматной доски", e)
            RecognitionResult3D(
                fen = "",
                detections = emptyList(),
                success = false,
                error = e.message
            )
        }
    }

    private fun processYOLOOutput(
        inputBuffer: ByteBuffer,
        origWidth: Int,
        origHeight: Int,
        paddingX: Float,
        paddingY: Float
    ): List<Detection3D> {
        val detections = mutableListOf<Detection3D>()

        try {
            // Формат [1, 16, 3549] где 16 = 4 (bbox) + 12 (классы)
            val outputShape = interpreter.getOutputTensor(0).shape()
            Log.d("Chess3DRecognizer", "Output shape: ${outputShape.contentToString()}")

            val outputSize = outputShape[1] // 16
            val numDetections = outputShape[2] // 3549
            val numClasses = 12 // 12 классов шахматных фигур

            val outputArray = Array(1) { Array(outputSize) { FloatArray(numDetections) } }

            interpreter.runForMultipleInputsOutputs(
                arrayOf(inputBuffer),
                mapOf(0 to outputArray)
            )

            val scale = min(inputSize.toFloat() / origWidth, inputSize.toFloat() / origHeight)
            val newWidth = (origWidth * scale).toInt()
            val newHeight = (origHeight * scale).toInt()

            // Функция сигмоиды
            fun sigmoid(x: Float): Float = 1.0f / (1.0f + exp(-x))

            // Обрабатываем все детекции
            for (i in 0 until numDetections) {
                // Индексы 0-3: bbox (x_center, y_center, width, height)
                // Индексы 4-15: class probabilities (12 классов)

                val xCenterNormalized = outputArray[0][0][i]
                val yCenterNormalized = outputArray[0][1][i]
                val widthNormalized = outputArray[0][2][i]
                val heightNormalized = outputArray[0][3][i]

                // Находим класс с максимальной уверенностью
                var maxClassConfidence = 0f
                var bestClassId = -1

                for (classId in 0 until numClasses) {
                    val classLogit = outputArray[0][4 + classId][i] // Индексы 4-15
                    val classConfidence = sigmoid(classLogit)
                    if (classConfidence > maxClassConfidence) {
                        maxClassConfidence = classConfidence
                        bestClassId = classId
                    }
                }

                // Общая уверенность = максимальная уверенность класса
                val totalConfidence = maxClassConfidence

                if (totalConfidence > confidenceThreshold && bestClassId in classToFen.keys) {
                    // Конвертируем координаты
                    val xCenterAbs = xCenterNormalized * inputSize
                    val yCenterAbs = yCenterNormalized * inputSize
                    val widthAbs = widthNormalized * inputSize
                    val heightAbs = heightNormalized * inputSize

                    val xCenterNoPadding = xCenterAbs - paddingX
                    val yCenterNoPadding = yCenterAbs - paddingY

                    val x1 = xCenterNoPadding - widthAbs / 2
                    val y1 = yCenterNoPadding - heightAbs / 2
                    val x2 = xCenterNoPadding + widthAbs / 2
                    val y2 = yCenterNoPadding + heightAbs / 2

                    if (x1 >= 0 && y1 >= 0 && x2 <= newWidth && y2 <= newHeight && x1 < x2 && y1 < y2) {
                        val left = (x1 / scale).coerceIn(0f, origWidth.toFloat())
                        val top = (y1 / scale).coerceIn(0f, origHeight.toFloat())
                        val right = (x2 / scale).coerceIn(0f, origWidth.toFloat())
                        val bottom = (y2 / scale).coerceIn(0f, origHeight.toFloat())

                        if (left < right && top < bottom) {
                            detections.add(
                                Detection3D(
                                    boundingBox = RectF(left, top, right, bottom),
                                    confidence = totalConfidence,
                                    classId = bestClassId,
                                    className = className[bestClassId] ?: "unknown",
                                    fenSymbol = classToFen[bestClassId]!!
                                )
                            )

                            if (detections.size <= 10) { // Логируем только первые 10
                                Log.d("Chess3DRecognizer",
                                    "Детекция: ${className[bestClassId]} " +
                                            "conf=$totalConfidence " +
                                            "bbox=(${left.toInt()},${top.toInt()},${right.toInt()},${bottom.toInt()})")
                            }
                        }
                    }
                }
            }

            Log.d("Chess3DRecognizer", "Обработано ${detections.size} детекций")

        } catch (e: Exception) {
            Log.e("Chess3DRecognizer", "Ошибка в processYOLOOutput: ${e.message}", e)
        }

        return detections
    }

    private fun applyNMS(detections: List<Detection3D>): List<Detection3D> {
        if (detections.isEmpty()) return detections

        val sorted = detections.sortedByDescending { it.confidence }.toMutableList()
        val selected = mutableListOf<Detection3D>()

        while (sorted.isNotEmpty()) {
            val current = sorted.first()
            selected.add(current)

            val iterator = sorted.listIterator(1)
            while (iterator.hasNext()) {
                val next = iterator.next()
                if (calculateIoU(current.boundingBox, next.boundingBox) > iouThreshold) {
                    iterator.remove()
                }
            }

            sorted.removeAt(0)
        }

        return selected
    }

    private fun calculateIoU(box1: RectF, box2: RectF): Float {
        val interLeft = maxOf(box1.left, box2.left)
        val interTop = maxOf(box1.top, box2.top)
        val interRight = minOf(box1.right, box2.right)
        val interBottom = minOf(box1.bottom, box2.bottom)

        if (interRight < interLeft || interBottom < interTop) return 0f

        val interArea = (interRight - interLeft) * (interBottom - interTop)
        val area1 = box1.width() * box1.height()
        val area2 = box2.width() * box2.height()

        return interArea / (area1 + area2 - interArea)
    }

    private fun createFENFromDetections(
        detections: List<Detection3D>,
        imageWidth: Int,
        imageHeight: Int
    ): String {
        val board = Array(8) { Array<Char?>(8) { null } }

        for (detection in detections) {
            val centerX = (detection.boundingBox.left + detection.boundingBox.right) / 2
            val centerY = (detection.boundingBox.top + detection.boundingBox.bottom) / 2

            val col = ((centerX / imageWidth) * 8).toInt().coerceIn(0, 7)
            val row = ((centerY / imageHeight) * 8).toInt().coerceIn(0, 7)

            val existing = board[row][col]
            if (existing == null || detection.confidence > detections
                    .firstOrNull { it.fenSymbol == existing }?.confidence ?: 0f) {
                board[row][col] = detection.fenSymbol
            }
        }

        return boardToFEN(board)
    }

    private fun boardToFEN(board: Array<Array<Char?>>): String {
        val fenRows = mutableListOf<String>()

        for (row in board) {
            var fenRow = ""
            var emptyCount = 0

            for (cell in row) {
                if (cell == null) {
                    emptyCount++
                } else {
                    if (emptyCount > 0) {
                        fenRow += emptyCount.toString()
                        emptyCount = 0
                    }
                    fenRow += cell
                }
            }

            if (emptyCount > 0) {
                fenRow += emptyCount.toString()
            }

            fenRows.add(fenRow)
        }

        return fenRows.joinToString("/") + " w - - 0 1"
    }

    private fun resizeWithPadding(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Triple<Bitmap, Float, Float> {
        val width = bitmap.width
        val height = bitmap.height

        val scale = min(targetWidth.toFloat() / width, targetHeight.toFloat() / height)
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        val paddedBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)

        val paddingX = (targetWidth - newWidth) / 2f
        val paddingY = (targetHeight - newHeight) / 2f

        val canvas = android.graphics.Canvas(paddedBitmap)
        canvas.drawBitmap(resizedBitmap, paddingX, paddingY, null)

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
                inputBuffer.putFloat(((color shr 16) and 0xFF) / 255.0f)
                inputBuffer.putFloat(((color shr 8) and 0xFF) / 255.0f)
                inputBuffer.putFloat((color and 0xFF) / 255.0f)
            }
        }

        return inputBuffer
    }

    fun getLichessUrl(fen: String): String {
        return "https://lichess.org/editor/${fen.replace(" ", "_")}"
    }

    fun close() {
        try {
            interpreter.close()
        } catch (e: Exception) {
            Log.e("Chess3DRecognizer", "Ошибка при закрытии интерпретатора", e)
        }
    }
}