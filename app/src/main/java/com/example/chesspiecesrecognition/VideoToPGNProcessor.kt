package com.example.chesspiecesrecognition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.*

class VideoToPGNProcessor(private val context: Context, private val tfLiteInterpreter: Interpreter) {

    companion object {
        private val CHESS_POSITIONS = mapOf(
            0 to "A8", 1 to "B8", 2 to "C8", 3 to "D8", 4 to "E8", 5 to "F8", 6 to "G8", 7 to "H8",
            8 to "A7", 9 to "B7", 10 to "C7", 11 to "D7", 12 to "E7", 13 to "F7", 14 to "G7", 15 to "H7",
            16 to "A6", 17 to "B6", 18 to "C6", 19 to "D6", 20 to "E6", 21 to "F6", 22 to "G6", 23 to "H6",
            24 to "A5", 25 to "B5", 26 to "C5", 27 to "D5", 28 to "E5", 29 to "F5", 30 to "G5", 31 to "H5",
            32 to "A4", 33 to "B4", 34 to "C4", 35 to "D4", 36 to "E4", 37 to "F4", 38 to "G4", 39 to "H4",
            40 to "A3", 41 to "B3", 42 to "C3", 43 to "D3", 44 to "E3", 45 to "F3", 46 to "G3", 47 to "H3",
            48 to "A2", 49 to "B2", 50 to "C2", 51 to "D2", 52 to "E2", 53 to "F2", 54 to "G2", 55 to "H2",
            56 to "A1", 57 to "B1", 58 to "C1", 59 to "D1", 60 to "E1", 61 to "F1", 62 to "G1", 63 to "H1"
        )

        private val LABEL_MAP = mapOf(
            0 to "bishop:Black", 1 to "king:Black", 2 to "knight:Black", 3 to "pawn:Black", 4 to "queen:Black", 5 to "rook:Black",
            6 to "bishop:White", 7 to "king:White", 8 to "knight:White", 9 to "pawn:White", 10 to "queen:White", 11 to "rook:White",
            12 to "empty:White"
        )
    }

    fun processVideoToPGN(videoUri: Uri, callback: (String) -> Unit) {
        try {
            val framesDir = extractFramesFromVideo(videoUri)
            val fens = recognizeFensFromFrames(framesDir)
            val correctedFens = correctFenSequence(fens)
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

    private fun recognizeFromBitmap(bitmap: Bitmap): String {
        val squares = getSquaresFromImage(bitmap)
        val positions = mutableListOf<String>()

        squares.forEachIndexed { index, squareBitmap ->
            preprocessImageFromBitmap(squareBitmap, 80)?.let { processedImage ->
                val flatImage = FloatArray(processedImage.size * processedImage[0].size)
                var idx = 0
                for (row in processedImage) {
                    for (pixel in row) {
                        flatImage[idx++] = pixel
                    }
                }

                val input = ByteBuffer.allocateDirect(4 * flatImage.size).apply {
                    order(ByteOrder.nativeOrder())
                    asFloatBuffer().put(flatImage)
                }

                val output = Array(1) { FloatArray(LABEL_MAP.size) }
                tfLiteInterpreter.run(input, output)

                val predictedClass = output[0].indices.maxByOrNull { output[0][it] } ?: 0
                val predictedLabel = LABEL_MAP[predictedClass] ?: "Unknown"
                positions.add("${CHESS_POSITIONS[index]}: $predictedLabel")
            }
        }

        return convertPositionsToFen(positions)
    }

    private fun getSquaresFromImage(image: Bitmap): List<Bitmap> {
        val squares = mutableListOf<Bitmap>()
        val cellSize = image.height / 8

        for (i in 0 until 8) {
            for (j in 0 until 8) {
                squares.add(Bitmap.createBitmap(
                    image,
                    j * cellSize,
                    i * cellSize,
                    cellSize,
                    cellSize
                ))
            }
        }
        return squares
    }

    private fun preprocessImageFromBitmap(bitmap: Bitmap, size: Int): Array<FloatArray>? {
        return try {
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, size, size, true)
            val imgArray = Array(1) { FloatArray(size * size * 3) }
            var index = 0

            for (y in 0 until size) {
                for (x in 0 until size) {
                    val pixel = scaledBitmap.getPixel(x, y)
                    imgArray[0][index++] = (pixel shr 16 and 0xFF) / 255.0f
                    imgArray[0][index++] = (pixel shr 8 and 0xFF) / 255.0f
                    imgArray[0][index++] = (pixel and 0xFF) / 255.0f
                }
            }
            imgArray
        } catch (e: Exception) {
            Log.e("VideoToPGN", "Error preprocessing image", e)
            null
        }
    }

    private fun convertPositionsToFen(positions: List<String>): String {
        val board = MutableList(8) { MutableList(8) { '.' } }

        positions.forEach { position ->
            val cleaned = position.replace(" ", "")
            val parts = cleaned.split(":")
            if (parts.size >= 3) {
                val square = parts[0]
                val pieceType = parts[1]
                val color = parts[2]

                if (!pieceType.contains("empty")) {
                    val x = square[0] - 'A'
                    val y = '8' - square[1]
                    if (x in 0..7 && y in 0..7) {
                        board[y][x] = if (color == "White") pieceType[0].uppercaseChar()
                        else pieceType[0].lowercaseChar()
                    }
                }
            }
        }

        return board.joinToString("/") { row ->
            row.joinToString("") { cell ->
                if (cell == '.') "1" else cell.toString()
            }.replace("11111111", "8")
                .replace("1111111", "7")
                .replace("111111", "6")
                .replace("11111", "5")
                .replace("1111", "4")
                .replace("111", "3")
                .replace("11", "2")
        } + " w KQkq - 0 1"
    }

    private fun correctFenSequence(fens: List<String>): List<String> {
        if (fens.isEmpty()) return emptyList()

        val board = Board()
        board.loadFromFen(fens[0])
        val corrected = mutableListOf(board.fen)

        for (i in 1 until fens.size) {
            val tempBoard = Board()
            tempBoard.loadFromFen(fens[i])
            var bestMove: Move? = null
            var bestDiffCount = Int.MAX_VALUE

            for (move in board.legalMoves()) {
                val testBoard = Board()
                testBoard.loadFromFen(board.fen)
                testBoard.doMove(move)

                val diffCount = Square.values().count { square ->
                    testBoard.getPiece(square) != tempBoard.getPiece(square)
                }

                if (diffCount < bestDiffCount) {
                    bestDiffCount = diffCount
                    bestMove = move
                    if (diffCount == 0) break
                }
            }

            if (bestMove != null && bestDiffCount <= 2) {
                board.doMove(bestMove)
                corrected.add(board.fen)
            } else {
                corrected.add(board.fen)
            }
        }

        return corrected.distinct()
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