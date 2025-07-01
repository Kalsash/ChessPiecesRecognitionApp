package com.example.chesspiecesrecognition

import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel

class MainActivity : ComponentActivity() {
    private lateinit var tfLiteInterpreter: Interpreter
    private var croppedImageUri by mutableStateOf<Uri?>(null)
    private lateinit var historyViewModel: HistoryViewModel
    private val imageCropper by lazy { ImageCropper(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadModel()
        historyViewModel = HistoryViewModel(application)

        setContent {
            var showHistory by remember { mutableStateOf(false) }
            var showVideoCropper by remember { mutableStateOf(false) }
            var videoToProcess by remember { mutableStateOf<Uri?>(null) }
            var isLoading by remember { mutableStateOf(false) }
            val coroutineScope = rememberCoroutineScope()

            if (showHistory) {
                HistoryScreen(
                    onBack = { showHistory = false },
                    onItemClick = { url ->
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                    },
                    viewModel = historyViewModel
                )
            } else if (showVideoCropper) {
                imageCropper.currentBitmap?.let {
                    ImageCropperScreen(
                        imageCropper = imageCropper,
                        onCropConfirmed = {
                            showVideoCropper = false
                            videoToProcess?.let { uri ->
                                isLoading = true
                                processVideoWithProgress(uri, coroutineScope) {
                                    isLoading = false
                                }
                            }
                        },
                        onCancel = {
                            showVideoCropper = false
                            videoToProcess = null
                        }
                    )
                }
            } else {
                MainScreen(
                    tfLiteInterpreter = tfLiteInterpreter,
                    croppedImageUri = croppedImageUri,
                    isLoading = isLoading,
                    onCropImage = { uri -> startCrop(uri) },
                    onShowHistory = { showHistory = true },
                    onProcessVideo = { uri ->
                        extractFirstFrame(uri)?.let { frame ->
                            imageCropper.currentBitmap = frame
                            showVideoCropper = true
                            videoToProcess = uri
                        }
                    },
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

    private fun extractFirstFrame(videoUri: Uri): Bitmap? {
        return try {
            val retriever = MediaMetadataRetriever().apply {
                setDataSource(this@MainActivity, videoUri)
            }
            val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST)
            retriever.release()
            bitmap
        } catch (e: Exception) {
            Log.e("MainActivity", "Error extracting first frame", e)
            null
        }
    }

    private fun processVideoWithProgress(
        uri: Uri,
        coroutineScope: CoroutineScope,
        onComplete: () -> Unit
    ) {
        coroutineScope.launch {
            Log.d("VideoProcessing", "Starting video processing coroutine")
            try {
                Log.d("VideoProcessing", "Entered try block")

                val processor = VideoToPGNProcessor(
                    this@MainActivity,
                    tfLiteInterpreter,
                    imageCropper.cropRect
                )
                Log.d("VideoProcessing", "Entered Processor")
                processor.processVideoToPGN(uri) { pgn ->
                    coroutineScope.launch {
                        Log.d("VideoProcessing", "Video Ended")
                        if (pgn.startsWith("Error:")) {
                            Log.d("VideoProcessing", "Error with pgn")
                        } else {
                            val url = "https://lichess.org/paste?pgn=${Uri.encode(pgn)}"
                            historyViewModel.addHistoryItem("video_processing", url)
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            startActivity(intent)
                            Log.d("VideoProcessing", "PGN Ready")
                        }
                        onComplete()
                    }
                }
            } catch (e: Exception) {
                onComplete()
            }
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
    isLoading: Boolean,
    onCropImage: (Uri) -> Unit,
    onShowHistory: () -> Unit,
    onProcessVideo: (Uri) -> Unit,
    viewModel: HistoryViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            onCropImage(uri)
        }
    }

    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onProcessVideo(uri)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ChessboardBackground(modifier = Modifier.fillMaxSize())

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
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
                    imageLauncher.launch("image/*")
                }

                ActionButton(
                    text = "Обрезать фото",
                    icon = Icons.Default.Edit
                ) {
                    if (selectedImageUri != null) {
                        onCropImage(selectedImageUri!!)
                    }
                }

                ActionButton(
                    text = "Распознать фигуры",
                    icon = Icons.Default.Search
                ) {
                    if (croppedImageUri != null) {
                        recognizeFromImage(context, tfLiteInterpreter, croppedImageUri!!, viewModel)
                    }
                }

                ActionButton(
                    text = "Обработать видео",
                    icon = Icons.Default.Search
                ) {
                    videoLauncher.launch("video/*")
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