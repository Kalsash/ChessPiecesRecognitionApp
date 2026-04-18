package com.example.chesspiecesrecognition

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.unit.times

// Модель и функции остаются без изменений
data class ChessPiece(val type: Char, val position: Pair<Int, Int>)

fun parseFen(fen: String): List<ChessPiece> {
    val pieces = mutableListOf<ChessPiece>()
    val rows = fen.split('/')
    for (rowIndex in rows.indices) {
        var colIndex = 0
        for (char in rows[rowIndex]) {
            if (char.isDigit()) {
                colIndex += char.toString().toInt()
            } else {
                if (char in "KQRBNPkqrbnp") pieces.add(ChessPiece(char, Pair(rowIndex, colIndex)))
                colIndex++
            }
        }
    }
    return pieces
}

fun generateFen(pieces: List<ChessPiece>): String {
    val board = Array(8) { Array(8) { ' ' } }
    pieces.forEach { piece ->
        val (row, col) = piece.position
        if (row in 0..7 && col in 0..7) board[row][col] = piece.type
    }

    return board.joinToString("/") { row ->
        var fenRow = ""
        var emptyCount = 0

        for (cell in row) {
            if (cell == ' ') {
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

        fenRow
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FenEditorScreen(onBack: () -> Unit) {
    var fenInput by remember { mutableStateOf("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR") }
    var pieces by remember { mutableStateOf(parseFen(fenInput)) }
    var selectedPiece by remember { mutableStateOf<Char?>(null) }
    val context = LocalContext.current
    val lazyListState = rememberLazyListState()
    val configuration = LocalConfiguration.current

    // Адаптивные размеры
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    val isPortrait = configuration.screenHeightDp > configuration.screenWidthDp

    // Размеры для адаптивного дизайна
    val horizontalPadding = if (isPortrait) 16.dp else 32.dp
    val verticalSpacing = if (isPortrait) 8.dp else 12.dp

    val boardMaxSize = if (isPortrait) screenWidth * 0.9f else screenHeight * 0.6f
    val squareSize = (boardMaxSize / 8).coerceAtMost(48.dp)
    val boardSize = squareSize * 8

    val controlPieceSize = if (isPortrait) (screenWidth * 0.11f) else (screenHeight * 0.08f)
    val controlPieceSizeDp = controlPieceSize.coerceIn(32.dp, 48.dp)
    val pieceIconSize = squareSize * 0.7f

    fun openLichess(fen: String) {
        // Убираем лишние параметры, Lichess сам определит начальную позицию
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://lichess.org/editor/$fen"))
        context.startActivity(intent)
    }

    fun swapPieceColors() {
        pieces = pieces.map { piece ->
            val newType = when (piece.type) {
                'K' -> 'k'; 'Q' -> 'q'; 'R' -> 'r'; 'B' -> 'b'; 'N' -> 'n'; 'P' -> 'p'
                'k' -> 'K'; 'q' -> 'Q'; 'r' -> 'R'; 'b' -> 'B'; 'n' -> 'N'; 'p' -> 'P'
                else -> piece.type
            }
            ChessPiece(newType, piece.position)
        }
        fenInput = generateFen(pieces)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FEN редактор", fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = horizontalPadding),
            state = lazyListState,
            verticalArrangement = Arrangement.spacedBy(verticalSpacing)
        ) {
            // Выбор фигур
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Выберите фигуру:", fontWeight = FontWeight.Medium, fontSize = 14.sp)

                    PieceSelectionRow(
                        label = "Белые:",
                        pieces = listOf('K', 'Q', 'R', 'B', 'N', 'P'),
                        selectedPiece = selectedPiece,
                        onPieceSelected = { selectedPiece = it },
                        pieceSize = controlPieceSizeDp
                    )

                    PieceSelectionRow(
                        label = "Черные:",
                        pieces = listOf('k', 'q', 'r', 'b', 'n', 'p'),
                        selectedPiece = selectedPiece,
                        onPieceSelected = { selectedPiece = it },
                        pieceSize = controlPieceSizeDp
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(controlPieceSizeDp)
                                .background(
                                    if (selectedPiece == ' ') Color.LightGray else Color.White,
                                    RoundedCornerShape(8.dp)
                                )
                                .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                                .clickable { selectedPiece = ' ' },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✕", fontSize = (controlPieceSizeDp * 0.4f).value.sp, color = Color.Red)
                        }
                        Text(
                            text = selectedPiece?.let {
                                when(it) {
                                    'K' -> "Белый король ♔"; 'Q' -> "Белая ферзь ♕"
                                    'R' -> "Белая ладья ♖"; 'B' -> "Белый слон ♗"
                                    'N' -> "Белый конь ♘"; 'P' -> "Белая пешка ♙"
                                    'k' -> "Черный король ♚"; 'q' -> "Черная ферзь ♛"
                                    'r' -> "Черная ладья ♜"; 'b' -> "Черный слон ♝"
                                    'n' -> "Черный конь ♞"; 'p' -> "Черная пешка ♟"
                                    ' ' -> "Удалить фигуру"
                                    else -> ""
                                }
                            } ?: "Выберите фигуру",
                            fontSize = 12.sp,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Шахматная доска
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ChessBoard(
                        pieces = pieces,
                        squareSize = squareSize,
                        boardSize = boardSize,
                        pieceIconSize = pieceIconSize,
                        selectedPiece = selectedPiece,
                        onSquareClick = { row, col ->
                            selectedPiece?.let {
                                val newPieces = pieces.filterNot { it.position == Pair(row, col) }.toMutableList()
                                if (it != ' ') newPieces.add(ChessPiece(it, Pair(row, col)))
                                pieces = newPieces
                                fenInput = generateFen(pieces)
                            }
                        }
                    )
                }
            }

            // Кнопки управления доской
            item {
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
                        Text("Очистить", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            pieces = parseFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR")
                            fenInput = generateFen(pieces)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Стартовая", fontSize = 12.sp)
                    }
                }
            }

            // FEN редактирование
            item {
                OutlinedTextField(
                    value = fenInput,
                    onValueChange = { fenInput = it },
                    label = { Text("FEN строка") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // Кнопки FEN операций
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { pieces = parseFen(fenInput) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Загрузить", fontSize = 12.sp)
                    }

                    Button(
                        onClick = { swapPieceColors() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Поменять цвета", fontSize = 12.sp)
                    }
                }
            }

            // Кнопка Lichess
            item {
                Button(
                    onClick = { openLichess(fenInput) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Открыть в Lichess", fontSize = 13.sp)
                }
            }

            // Кнопка возврата
            item {
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Вернуться на главный экран", fontSize = 13.sp)
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
fun PieceSelectionRow(
    label: String,
    pieces: List<Char>,
    selectedPiece: Char?,
    onPieceSelected: (Char) -> Unit,
    pieceSize: Dp
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(60.dp))
        pieces.forEach { pieceType ->
            val pieceSymbol = when (pieceType) {
                'K' -> "♔"; 'Q' -> "♕"; 'R' -> "♖"; 'B' -> "♗"; 'N' -> "♘"; 'P' -> "♙"
                'k' -> "♚"; 'q' -> "♛"; 'r' -> "♜"; 'b' -> "♝"; 'n' -> "♞"; 'p' -> "♟"
                else -> ""
            }

            Box(
                modifier = Modifier
                    .size(pieceSize)
                    .background(
                        if (selectedPiece == pieceType) Color.LightGray.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(8.dp)
                    )
                    .border(
                        1.dp,
                        if (selectedPiece == pieceType) MaterialTheme.colorScheme.primary
                        else Color.Gray.copy(alpha = 0.5f),
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onPieceSelected(pieceType) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = pieceSymbol,
                    fontSize = (pieceSize * 0.5f).value.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (pieceType.isUpperCase()) Color.Black else Color.DarkGray
                )
            }
        }
    }
}

@Composable
fun ChessBoard(
    pieces: List<ChessPiece>,
    squareSize: Dp,
    boardSize: Dp,
    pieceIconSize: Dp,
    selectedPiece: Char?,
    onSquareClick: (Int, Int) -> Unit
) {
    Box(
        modifier = Modifier
            .size(boardSize)
            .background(Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .border(2.dp, Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
    ) {
        for (row in 0 until 8) {
            for (col in 0 until 8) {
                val color = if ((row + col) % 2 == 0) Color(0xFFF0D9B5) else Color(0xFFB58863)
                val pieceOnSquare = pieces.find { it.position == Pair(row, col) }
                val pieceSymbol = pieceOnSquare?.type?.let { type ->
                    when (type) {
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
                        .clickable(enabled = selectedPiece != null) { onSquareClick(row, col) },
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
}