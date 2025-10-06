package com.example.chesspiecesrecognition

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import org.tensorflow.lite.Interpreter
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder


fun getSquaresFromImage(image: Bitmap): List<Bitmap> {
    val squares = mutableListOf<Bitmap>()
    val width = image.width
    val height = image.height
    val cellSize = height / 8
    val numCells = 8

    for (i in 0 until numCells) {
        for (j in 0 until numCells) {
            val xStart = j * cellSize
            val yStart = i * cellSize

            val cell = Bitmap.createBitmap(image, xStart, yStart, cellSize, cellSize)
            squares.add(cell)
        }
    }
    return squares
}

fun preprocessImageFromBitmap(bitmap: Bitmap, size: Int): Array<FloatArray>? {
    return try {
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, size, size, true)
        val imgArray = Array(1) { FloatArray(size * size * 3) }
        var index = 0

        for (y in 0 until size) {
            for (x in 0 until size) {
                val pixel = scaledBitmap.getPixel(x, y)
                imgArray[0][index++] = (pixel shr 16 and 0xFF) / 255.0f  // Красный канал
                imgArray[0][index++] = (pixel shr 8 and 0xFF) / 255.0f   // Зеленый канал
                imgArray[0][index++] = (pixel and 0xFF) / 255.0f         // Синий канал
            }
        }

        imgArray
    } catch (e: IOException) {
        e.printStackTrace()
        null
    }
}

val chessPositions = mapOf(
    57 to "A1", 49 to "A2", 41 to "A3", 33 to "A4", 25 to "A5", 17 to "A6", 9 to "A7", 1 to "A8",
    58 to "B1", 50 to "B2", 42 to "B3", 34 to "B4", 26 to "B5", 18 to "B6", 10 to "B7", 2 to "B8",
    59 to "C1", 51 to "C2", 43 to "C3", 35 to "C4", 27 to "C5", 19 to "C6", 11 to "C7", 3 to "C8",
    60 to "D1", 52 to "D2", 44 to "D3", 36 to "D4", 28 to "D5", 20 to "D6", 12 to "D7", 4 to "D8",
    61 to "E1", 53 to "E2", 45 to "E3", 37 to "E4", 29 to "E5", 21 to "E6", 13 to "E7", 5 to "E8",
    62 to "F1", 54 to "F2", 46 to "F3", 38 to "F4", 30 to "F5", 22 to "F6", 14 to "F7", 6 to "F8",
    63 to "G1", 55 to "G2", 47 to "G3", 39 to "G4", 31 to "G5", 23 to "G6", 15 to "G7", 7 to "G8",
    64 to "H1", 56 to "H2", 48 to "H3", 40 to "H4", 32 to "H5", 24 to "H6", 16 to "H7", 8 to "H8"
)

fun recognizeFromImage(context: Context, tfLiteInterpreter: Interpreter, imageUri: Uri,
                       viewModel: HistoryViewModel) {
    try {
        val imageStream = context.contentResolver.openInputStream(imageUri)
        val originalBitmap = BitmapFactory.decodeStream(imageStream)

        val squares = getSquaresFromImage(originalBitmap)

        val labelMap = mapOf(
            0 to "bishop:Black", 1 to "king:Black", 2 to "night:Black", 3 to "pawn:Black", 4 to "queen:Black", 5 to "rook:Black",
            6 to "bishop:White", 7 to "king:White", 8 to "night:White", 9 to "pawn:White", 10 to "queen:White", 11 to "rook:White",
            12 to "empty:White"
        )

        val positions = mutableListOf<String>()
        squares.forEachIndexed { index, squareBitmap ->
            val processedImage = preprocessImageFromBitmap(squareBitmap, 80)

            if (processedImage != null) {
                val flatImage = FloatArray(processedImage.size * processedImage[0].size)
                var idx = 0
                for (row in processedImage) {
                    for (pixel in row) {
                        flatImage[idx++] = pixel
                    }
                }

                val input = ByteBuffer.allocateDirect(4 * flatImage.size)
                input.order(ByteOrder.nativeOrder())
                input.asFloatBuffer().put(flatImage)

                val output = Array(1) { FloatArray(labelMap.size) }
                tfLiteInterpreter.run(input, output)

                var predictedClass = 0
                var maxScore = output[0][0]
                for (i in 1 until output[0].size) {
                    if (output[0][i] > maxScore) {
                        maxScore = output[0][i]
                        predictedClass = i
                    }
                }

                val predictedLabel = labelMap[predictedClass] ?: "Unknown"
                positions.add("${chessPositions[index + 1]}: $predictedLabel")
            }
        }

        val board = MutableList(8) { MutableList(8) { '.' } }
        positions.forEach { position ->
            val cleanedPosition = position.replace(" ", "")
            val (square, pieceType, color) = cleanedPosition.split(":")
            val x = square[0] - 'A'
            val y = 8 - square[1].digitToInt()

            if (pieceType.contains("empty")) {
                board[y][x] = '.'
            } else {
                val piece = if (color == "White") pieceType[0].uppercaseChar() else pieceType[0].lowercaseChar()
                board[y][x] = piece
            }
        }

        val fenParts = mutableListOf<String>()
        board.forEach { row ->
            var emptyCount = 0
            var rowStr = ""
            row.forEach { square ->
                if (square == '.') {
                    emptyCount++
                } else {
                    if (emptyCount > 0) {
                        rowStr += emptyCount.toString()
                        emptyCount = 0
                    }
                    rowStr += square
                }
            }
            if (emptyCount > 0) {
                rowStr += emptyCount.toString()
            }
            fenParts.add(rowStr)
        }

        val fen = fenParts.joinToString("/") + " w - - 0 1"
        val url = "https://lichess.org/editor/$fen"
        Toast.makeText(context, "Open URL: $url", Toast.LENGTH_LONG).show()

        val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
        val savedImagePath = FileUtils.saveBitmapToInternalStorage(context, bitmap)

        viewModel.addHistoryItem(savedImagePath, url)

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)

    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Ошибка при распознавании изображения", Toast.LENGTH_SHORT).show()
    }
}