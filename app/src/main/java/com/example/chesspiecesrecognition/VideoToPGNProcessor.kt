package com.example.chesspiecesrecognition

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.*

class VideoToPGNProcessor(private val context: Context, private val tfLiteInterpreter: Interpreter) {
    fun processVideoToPGN(videoUri: Uri, callback: (String) -> Unit) {
        try {
            val framesDir = extractFramesFromVideo(videoUri)
            val fens = recognizeFensFromFrames(framesDir)
            // Log.d("FEN_LIST", "FENs: ${fens.joinToString("\n")}")
            val correctedFens = correctFenSequence(fens)
            //Log.d("COR_FEN_LIST", "FENs: ${correctedFens.joinToString("\n")}")
            val pgn = generatePGN(correctedFens)

            callback(pgn)
            framesDir.deleteRecursively()
        } catch (e: Exception) {
            Log.e("VideoToPGN", "Error processing video", e)
            callback("Error: ${e.message}")
        }
    }

    private fun extractFramesFromVideo(videoUri: Uri): File {
        val framesDir = File(context.cacheDir, "chess_video_frames").apply { mkdirs() }
        val retriever = MediaMetadataRetriever().apply {
            setDataSource(context, videoUri)
        }

        val duration = retriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_DURATION
        )?.toLong() ?: 0L

        val frameInterval = 1_000_000 // 1 frame per second
        var currentTimeUs = 0L
        var frameCount = 0

        while (currentTimeUs < duration * 1000) {
            retriever.getFrameAtTime(currentTimeUs, MediaMetadataRetriever.OPTION_CLOSEST)?.let { bitmap ->
                File(framesDir, "frame_${frameCount.toString().padStart(4, '0')}.jpg").apply {
                    FileOutputStream(this).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                }
                frameCount++
            }
            currentTimeUs += frameInterval
        }

        retriever.release()
        return framesDir
    }

    private fun recognizeFensFromFrames(framesDir: File): List<String> {
        return framesDir.listFiles()
            ?.sortedBy { it.name }
            ?.filter { it.name.endsWith(".jpg") }
            ?.mapNotNull { file ->
                try {
                    BitmapFactory.decodeFile(file.absolutePath)?.let { recognizeFromBitmap(it) }
                } catch (e: Exception) {
                    Log.e("VideoToPGN", "Error processing frame ${file.name}", e)
                    null
                }
            } ?: listOf("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
    }

    fun recognizeFromBitmap(bitmap: Bitmap): String {
        var fen = ""
        try {
            val squares = getSquaresFromImage(bitmap)

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

            fen = fenParts.joinToString("/") + " w - - 0 1"


        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Ошибка при распознавании изображения", Toast.LENGTH_SHORT).show()
        }
        return fen
    }


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



    private fun correctFenSequence(fens: List<String>): List<String> {

        if (fens.isEmpty()) return emptyList()

        val board = Board()
        board.loadFromFen(fens[0])
        val corrected = mutableListOf(normalizeFen(board.fen))
        val recognitionErrors = mutableSetOf<Triple<Square, Piece?, Piece?>>()

        for (i in 1 until fens.size) {
            val tempBoard = Board()
            tempBoard.loadFromFen(fens[i])

            // Находим изменения
            val changes = mutableListOf<Triple<Square, Piece?, Piece?>>()
            for (square in Square.entries) {
                val prevPiece = board.getPiece(square)
                val currPiece = tempBoard.getPiece(square)
                if (prevPiece != currPiece) {
                    if (prevPiece.toString() != currPiece.toString().lowercase()) {
                        if (!recognitionErrors.contains(Triple(square, prevPiece, currPiece)))
                            changes.add(Triple(square, prevPiece, currPiece))
                    }
                }
            }

            if (changes.count() == 0) continue

            var bestMove: Move? = null
            var bestDiffCount = Int.MAX_VALUE

            for (move in board.legalMoves()) {
                val testBoard = Board()
                testBoard.loadFromFen(board.fen)
                testBoard.doMove(move)

                var diffCount = 0
                for (square in Square.entries) {
                    val targetPiece = tempBoard.getPiece(square)
                    val testPiece = testBoard.getPiece(square)
                    if (targetPiece != testPiece) {
                        diffCount++
                    }
                }

                if (diffCount <= 2) {
                    bestDiffCount = diffCount
                    bestMove = move
                    if (diffCount == 0) break
                }
            }
            if (bestMove != null && bestDiffCount <= 2) {
                board.doMove(bestMove)
                corrected.add(normalizeFen(board.fen))
            }
            else {
                for (j in 0 until changes.size) {
                    recognitionErrors.add(changes[j])
                }
                corrected.add(normalizeFen(board.fen))
            }
        }

        return corrected.distinct()
    }

    private fun normalizeFen(fen: String): String {
        // Удаляем информацию о количестве ходов и полуходов, оставляя только позицию и очередь хода
        val parts = fen.split(" ")
        return "${parts[0]} ${parts[1]} - - 0 1"
    }

    private fun generatePGN(fens: List<String>): String {
        if (fens.size < 2) return ""

        val pgn = StringBuilder()
        val date = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date())

        // Добавляем заголовки PGN
        pgn.append("[Event \"Auto-generated game\"]\n")
        pgn.append("[Site \"Chess Recognition System\"]\n")
        pgn.append("[Date \"$date\"]\n")
        pgn.append("[Round \"1\"]\n")
        pgn.append("[White \"AI\"]\n")
        pgn.append("[Black \"AI\"]\n")
        pgn.append("[Result \"*\"]\n\n")

        val board = Board()
        board.loadFromFen(fens[0])
        var moveNumber = 1
        val moves = mutableListOf<String>()

        for (i in 1 until fens.size) {
            val tempBoard = Board()
            tempBoard.loadFromFen(fens[i])
            val move = board.legalMoves().find { legalMove ->
                Board().apply {
                    loadFromFen(board.fen)
                    doMove(legalMove)
                }.fen.split(" ")[0] == tempBoard.fen.split(" ")[0]
            }

            move?.let {
                val sanMove = moveToSan(board, it)
                if (board.sideToMove == com.github.bhlangonijr.chesslib.Side.WHITE) {
                    moves.add("$moveNumber.$sanMove")
                } else {
                    moves.add(sanMove)
                    moveNumber++
                }
                board.doMove(it)
            }
        }

        pgn.append(moves.joinToString(" ") + " *")
        return pgn.toString()
    }

    private fun moveToSan(board: Board, move: Move): String {
        // Простая реализация преобразования Move в SAN нотацию
        val piece = board.getPiece(move.from)
        val pieceChar = when (piece) {
            com.github.bhlangonijr.chesslib.Piece.WHITE_KING -> "K"
            com.github.bhlangonijr.chesslib.Piece.WHITE_QUEEN -> "Q"
            com.github.bhlangonijr.chesslib.Piece.WHITE_ROOK -> "R"
            com.github.bhlangonijr.chesslib.Piece.WHITE_BISHOP -> "B"
            com.github.bhlangonijr.chesslib.Piece.WHITE_KNIGHT -> "N"
            com.github.bhlangonijr.chesslib.Piece.WHITE_PAWN -> ""
            com.github.bhlangonijr.chesslib.Piece.BLACK_KING -> "K"
            com.github.bhlangonijr.chesslib.Piece.BLACK_QUEEN -> "Q"
            com.github.bhlangonijr.chesslib.Piece.BLACK_ROOK -> "R"
            com.github.bhlangonijr.chesslib.Piece.BLACK_BISHOP -> "B"
            com.github.bhlangonijr.chesslib.Piece.BLACK_KNIGHT -> "N"
            com.github.bhlangonijr.chesslib.Piece.BLACK_PAWN -> ""
            else -> ""
        }

        val fromSquare = move.from.name.toLowerCase()
        val toSquare = move.to.name.toLowerCase()

        // Для взятия добавляем "x"
        val capture = if (board.getPiece(move.to) != com.github.bhlangonijr.chesslib.Piece.NONE) "x" else ""

        return "$pieceChar$capture$toSquare"
    }
}