package com.example.chesspiecesrecognition

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.launch
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel

class MainActivity : ComponentActivity() {
    private lateinit var tfLiteInterpreter: Interpreter
    private var croppedImageUri by mutableStateOf<Uri?>(null)
    private lateinit var historyViewModel: HistoryViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadModel()
        historyViewModel = HistoryViewModel(application)

        setContent {
            var showHistory by remember { mutableStateOf(false) }

            if (showHistory) {
                HistoryScreen(
                    onBack = { showHistory = false },
                    onItemClick = { url ->
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                    },
                    viewModel = historyViewModel
                )
            } else {
                MainScreen(
                    tfLiteInterpreter = tfLiteInterpreter,
                    croppedImageUri = croppedImageUri,
                    onCropImage = { uri -> startCrop(uri) },
                    onShowHistory = { showHistory = true },
                    viewModel = historyViewModel
                )
            }
        }
    }

    private fun loadModel() {
        try {
            val assetFileDescriptor = assets.openFd("chess_piece_recognition_model.tflite")
            val inputStream = assetFileDescriptor.createInputStream()
            val byteBuffer = inputStream.channel.map(
                FileChannel.MapMode.READ_ONLY,
                assetFileDescriptor.startOffset,
                assetFileDescriptor.declaredLength
            )
            tfLiteInterpreter = Interpreter(byteBuffer, Interpreter.Options())
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun startCrop(sourceUri: Uri) {
        if (sourceUri.scheme != null) {
            val destinationUri = Uri.fromFile(File(cacheDir, "croppedImage.jpg"))
            UCrop.of(sourceUri, destinationUri)
                .withAspectRatio(1f, 1f)
                .withMaxResultSize(800, 800)
                .start(this)
        } else {
            Log.e("UCrop", "Source URI is invalid")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == UCrop.REQUEST_CROP && resultCode == RESULT_OK) {
            val resultUri = UCrop.getOutput(data!!)
            if (resultUri != null) {
                croppedImageUri = resultUri
            } else {
                Log.e("UCrop", "Result Uri is null")
            }
        } else if (resultCode == UCrop.RESULT_ERROR) {
            val cropError = UCrop.getError(data!!)
            cropError?.printStackTrace()
        }
    }
}

@Composable
fun MainScreen(
    tfLiteInterpreter: Interpreter,
    croppedImageUri: Uri?,
    onCropImage: (Uri) -> Unit,
    onShowHistory: () -> Unit,
    viewModel: HistoryViewModel
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            onCropImage(uri)
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Фото выбрано успешно")
            }
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Не удалось выбрать фото")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Фон на весь экран
            ChessboardBackground(modifier = Modifier.fillMaxSize())

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "♔ Chess Pieces Recognition ♔",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(shadow = Shadow(color = Color.Black, blurRadius = 4f))
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (croppedImageUri != null) {
                        Image(
                            painter = rememberAsyncImagePainter(croppedImageUri),
                            contentDescription = "Выбранное фото",
                            modifier = Modifier
                                .size(200.dp)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.3f), shape = RoundedCornerShape(12.dp))
                        )
                    }

                    ActionButton(
                        text = "Выбрать фото",
                        icon = Icons.Default.Add
                    ) {
                        launcher.launch("image/*")
                    }

                    ActionButton(
                        text = "Обрезать фото",
                        icon = Icons.Default.Edit
                    ) {
                        if (selectedImageUri != null) {
                            onCropImage(selectedImageUri!!)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Обрезка запущена")
                            }
                        } else {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Сначала выберите фото")
                            }
                        }
                    }

                    ActionButton(
                        text = "Распознать фигуры",
                        icon = Icons.Default.Search
                    ) {
                        if (croppedImageUri != null) {
                            recognizeFromImage(context, tfLiteInterpreter, croppedImageUri, viewModel)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Распознавание запущено")
                            }
                        } else {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Сначала выберите и обрежьте фото")
                            }
                        }
                    }



                    ActionButton(
                        text = "История распознаваний",
                        icon = Icons.Default.List
                    ) {
                        onShowHistory()
                    }
                }

                Text(
                    text = "ChessReco",
                    color = Color.DarkGray.copy(alpha = 0.9f),
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
fun ActionButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp)),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(icon, contentDescription = text, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, color = Color.White)
    }
}

@Composable
fun ChessboardBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val squareSize = size.minDimension*3 / 8f
        for (row in 0..7) {
            for (col in 0..7) {
                val isDark = (row + col) % 2 == 1
                drawRect(
                    color = if (isDark) Color(0xFF2C5364) else Color(0xFFECECEC),
                    topLeft = androidx.compose.ui.geometry.Offset(col * squareSize, row * squareSize),
                    size = Size(squareSize, squareSize)
                )
            }
        }
    }
}
