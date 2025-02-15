package com.example.chesspiecesrecognition

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.tensorflow.lite.Interpreter
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MainActivity : ComponentActivity() {
    private lateinit var tfLiteInterpreter: Interpreter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadModel()
        setContent {
            MainScreen(contentResolver = contentResolver, tfLiteInterpreter = tfLiteInterpreter)
        }
    }

    private fun loadModel() {
        try {
            val assetFileDescriptor = assets.openFd("chess_piece_recognition_model.tflite")
            val inputStream = assetFileDescriptor.createInputStream()
            val byteBuffer = inputStream.channel.map(
                java.nio.channels.FileChannel.MapMode.READ_ONLY,
                assetFileDescriptor.startOffset,
                assetFileDescriptor.declaredLength
            )
            tfLiteInterpreter = Interpreter(byteBuffer, Interpreter.Options())
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}

@Composable
fun MainScreen(contentResolver: android.content.ContentResolver, tfLiteInterpreter: Interpreter) {
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> selectedImageUri = uri }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = { launcher.launch("image/*") }) {
            Text("Выберите фото")
        }

        Spacer(modifier = Modifier.height(8.dp))

        val context = LocalContext.current
        Button(onClick = {
            recognize(context, tfLiteInterpreter)
        }) {
            Text("Распознать фигуры")
        }
    }
}

fun preprocessImageFromAssets(context: Context, imageName: String, size: Int): Array<FloatArray>? {
    return try {
        val assetInputStream = context.assets.open(imageName)
        val bitmap = BitmapFactory.decodeStream(assetInputStream)?.let {
            Bitmap.createScaledBitmap(it, size, size, true)
        }
        assetInputStream.close()

        bitmap?.let {
            val imgArray = Array(1) { FloatArray(size * size * 3) }
            var index = 0

            for (y in 0 until size) {
                for (x in 0 until size) {
                    val pixel = it.getPixel(x, y)
                    imgArray[0][index++] = (pixel shr 16 and 0xFF) / 255.0f  // Красный канал
                    imgArray[0][index++] = (pixel shr 8 and 0xFF) / 255.0f   // Зеленый канал
                    imgArray[0][index++] = (pixel and 0xFF) / 255.0f         // Синий канал
                }
            }

            imgArray
        }
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

fun recognize(context: Context, tfLiteInterpreter: Interpreter) {
    val folderPath = "squares"

    val labelMap = mapOf(
        0 to "bishop:Black", 1 to "king:Black", 2 to "night:Black", 3 to "pawn:Black", 4 to "queen:Black", 5 to "rook:Black",
        6 to "bishop:White", 7 to "king:White", 8 to "night:White", 9 to "pawn:White", 10 to "queen:White", 11 to "rook:White",
        12 to "empty:White"
    )

    val arr = mutableListOf<String>()
    for (i in 1..64) {
        val imageName = "squares/cell_$i.jpg"

        val processedImage = preprocessImageFromAssets(context, imageName, 80)

        if (processedImage != null) {
            val flatImage = FloatArray(processedImage.size * processedImage[0].size)
            var index = 0
            for (row in processedImage) {
                for (pixel in row) {
                    flatImage[index++] = pixel
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
            arr.add("${chessPositions[i]}: $predictedLabel")
        } else {
            Toast.makeText(context, "Файл $imageName не найден в assets", Toast.LENGTH_SHORT).show()
        }
    }
    val positions = arr

// Используем списки вместо массивов
    val board = MutableList(8) { MutableList(8) { '.' } }

    positions.forEach { position ->
        // Убираем пробелы из строки перед обработкой
        val cleanedPosition = position.replace(" ", "")

        // Разделяем строку на части
        val (square, pieceType, color) = cleanedPosition.split(":")
        val x = square[0] - 'A' // Это X координата (столбец)
        val y = 8 - square[1].digitToInt() // Это Y координата (строка)

        // Проверяем, если фигура пустая, ставим точку
        if (pieceType.contains("empty")) {
            board[y][x] = '.'
        } else {
            // Если фигура не пустая, то определяем, какого цвета фигура и заполняем доску
            val piece = if (color == "White") pieceType[0].uppercaseChar() else pieceType[0].lowercaseChar()
            board[y][x] = piece
        }
    }


// Преобразуем доску в строковое представление для логирования
    //val boardString = board.joinToString("\n") { row -> row.joinToString(" ") }
    //Log.d("ChessRecognition", "Распознанные позиции:\n$boardString")

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

// Формируем правильную FEN строку
    val fen = fenParts.joinToString("/") + " w - - 0 1"


// Формируем URL с чистой FEN строкой
    val url = "https://lichess.org/editor/$fen"
    Toast.makeText(context, "Open URL: $url", Toast.LENGTH_LONG).show()
    Log.d("URL", url)

    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)



}
