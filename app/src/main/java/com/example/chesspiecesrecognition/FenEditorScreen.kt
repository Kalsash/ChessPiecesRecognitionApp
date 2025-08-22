package com.example.chesspiecesrecognition

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times

// Модель для представления шахматной фигуры
data class ChessPiece(
    val type: Char, // 'K', 'Q', 'R', 'B', 'N', 'P' для белых, 'k', 'q', 'r', 'b', 'n', 'p' для черных
    val position: Pair<Int, Int> // Позиция на доске (ряд, колонка)
)

// Функция для парсинга FEN строки
fun parseFen(fen: String): List<ChessPiece> {
    val pieces = mutableListOf<ChessPiece>()
    val rows = fen.split('/')

    for (rowIndex in rows.indices) {
        var colIndex = 0
        for (char in rows[rowIndex]) {
            if (char.isDigit()) {
                colIndex += char.toString().toInt()
            } else {
                if (char in "KQRBNPkqrbnp") {
                    pieces.add(ChessPiece(char, Pair(rowIndex, colIndex)))
                }
                colIndex++
            }
        }
    }
    return pieces
}

// Функция для генерации FEN строки из текущей позиции
fun generateFen(pieces: List<ChessPiece>): String {
    val board = Array(8) { Array(8) { ' ' } }

    for (piece in pieces) {
        val (row, col) = piece.position
        if (row in 0..7 && col in 0..7) {
            board[row][col] = piece.type
        }
    }

    val fenBuilder = StringBuilder()
    for (row in 0..7) {
        var emptyCount = 0
        for (col in 0..7) {
            if (board[row][col] == ' ') {
                emptyCount++
            } else {
                if (emptyCount > 0) {
                    fenBuilder.append(emptyCount)
                    emptyCount = 0
                }
                fenBuilder.append(board[row][col])
            }
        }
        if (emptyCount > 0) {
            fenBuilder.append(emptyCount)
        }
        if (row < 7) {
            fenBuilder.append('/')
        }
    }

    return fenBuilder.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FenEditorScreen(onBack: () -> Unit) {
    var fenInput by remember { mutableStateOf("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR") }
    var pieces by remember { mutableStateOf(parseFen(fenInput)) }
    var selectedPiece by remember { mutableStateOf<Char?>(null) }

    // Получаем размеры экрана для адаптивного дизайна
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    // Адаптивные размеры
    val squareSize = (screenWidth * 0.9f / 8).coerceAtMost(screenHeight * 0.4f / 8)
    val boardSize = squareSize * 8
    val pieceIconSize = (squareSize * 0.6f).coerceAtMost(30.dp)
    val controlPieceSize = (screenWidth * 0.1f).coerceAtMost(40.dp)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FEN редактор") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Поле ввода FEN
            OutlinedTextField(
                value = fenInput,
                onValueChange = { fenInput = it },
                label = { Text("FEN строка") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        pieces = parseFen(fenInput)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Загрузить FEN")
                }

                Button(
                    onClick = {
                        fenInput = generateFen(pieces)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Сгенерировать FEN")
                }
            }

            // Панель выбора фигур
            Text("Выберите фигуру:", fontWeight = FontWeight.Bold)
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Белые фигуры
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Белые:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    listOf('K', 'Q', 'R', 'B', 'N', 'P').forEach { pieceType ->
                        val pieceSymbol = when (pieceType) {
                            'K' -> "♔"; 'Q' -> "♕"; 'R' -> "♖"; 'B' -> "♗"; 'N' -> "♘"; 'P' -> "♙"
                            else -> ""
                        }

                        Box(
                            modifier = Modifier
                                .size(controlPieceSize)
                                .background(
                                    if (selectedPiece == pieceType) Color.LightGray else Color.White,
                                    RoundedCornerShape(8.dp)
                                )
                                .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                                .clickable {
                                    selectedPiece = pieceType
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = pieceSymbol,
                                fontSize = (controlPieceSize * 0.5f).value.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                }

                // Черные фигуры
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Черные:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    listOf('k', 'q', 'r', 'b', 'n', 'p').forEach { pieceType ->
                        val pieceSymbol = when (pieceType) {
                            'k' -> "♚"; 'q' -> "♛"; 'r' -> "♜"; 'b' -> "♝"; 'n' -> "♞"; 'p' -> "♟"
                            else -> ""
                        }

                        Box(
                            modifier = Modifier
                                .size(controlPieceSize)
                                .background(
                                    if (selectedPiece == pieceType) Color.LightGray else Color.White,
                                    RoundedCornerShape(8.dp)
                                )
                                .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                                .clickable {
                                    selectedPiece = pieceType
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = pieceSymbol,
                                fontSize = (controlPieceSize * 0.5f).value.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                }

                // Кнопка удаления
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(controlPieceSize)
                            .background(
                                if (selectedPiece == ' ') Color.LightGray else Color.White,
                                RoundedCornerShape(8.dp)
                            )
                            .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                            .clickable {
                                selectedPiece = ' '
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "X",
                            fontSize = (controlPieceSize * 0.5f).value.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Red
                        )
                    }
                    Text(
                        text = "Выбрано: ${selectedPiece?.let {
                            when(it) {
                                'K' -> "Белый король ♔"
                                'Q' -> "Белая ферзь ♕"
                                'R' -> "Белая ладья ♖"
                                'B' -> "Белый слон ♗"
                                'N' -> "Белый конь ♘"
                                'P' -> "Белая пешка ♙"
                                'k' -> "Черный король ♚"
                                'q' -> "Черная ферзь ♛"
                                'r' -> "Черная ладья ♜"
                                'b' -> "Черный слон ♝"
                                'n' -> "Черный конь ♞"
                                'p' -> "Черная пешка ♟"
                                ' ' -> "Удалить фигуру"
                                else -> ""
                            }
                        } ?: "Ничего не выбрано"}",
                        fontSize = 12.sp
                    )
                }
            }

            // Шахматная доска
            Box(
                modifier = Modifier
                    .width(boardSize)
                    .height(boardSize)
                    .background(Color.LightGray)
                    .align(Alignment.CenterHorizontally)
            ) {
                // Рисуем доску и фигуры
                for (row in 0 until 8) {
                    for (col in 0 until 8) {
                        val color = if ((row + col) % 2 == 0) Color(0xFFF0D9B5) else Color(0xFFB58863)

                        // Находим фигуру на этой клетке
                        val pieceOnSquare = pieces.find { it.position == Pair(row, col) }
                        val pieceSymbol = pieceOnSquare?.let { piece ->
                            when (piece.type) {
                                'K' -> "♔"; 'Q' -> "♕"; 'R' -> "♖"; 'B' -> "♗"; 'N' -> "♘"; 'P' -> "♙"
                                'k' -> "♚"; 'q' -> "♛"; 'r' -> "♜"; 'b' -> "♝"; 'n' -> "♞"; 'p' -> "♟"
                                else -> ""
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(squareSize)
                                .offset(x = col * squareSize, y = row * squareSize)
                                .background(color)
                                .border(1.dp, Color.Black)
                                .clickable {
                                    if (selectedPiece != null) {
                                        // Удаляем существующую фигуру на этой клетке
                                        val newPieces = pieces.filterNot { it.position == Pair(row, col) }.toMutableList()

                                        // Если выбран не пробел (не удаление), добавляем новую фигуру
                                        if (selectedPiece != ' ') {
                                            newPieces.add(ChessPiece(selectedPiece!!, Pair(row, col)))
                                        }

                                        pieces = newPieces
                                        fenInput = generateFen(pieces)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (pieceOnSquare != null) {
                                Text(
                                    text = pieceSymbol ?: "",
                                    fontSize = pieceIconSize.value.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (pieceOnSquare.type.isUpperCase()) Color.Black else Color.Black
                                )
                            }
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        pieces = emptyList()
                        fenInput = "8/8/8/8/8/8/8/8"
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Очистить")
                }

                Button(
                    onClick = {
                        pieces = parseFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR")
                        fenInput = generateFen(pieces)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Стартовая")
                }
            }
        }
    }
}