package com.example.chesspiecesrecognition

import MainScreen
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
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
    private var currentFrame by mutableStateOf<Bitmap?>(null)
    private var videoProcessor: VideoToPGNProcessor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadModel()
        historyViewModel = HistoryViewModel(application)

        // Отключаем системные UI бары
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.navigationBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setContent {
            var showHistory by remember { mutableStateOf(false) }
            var showAbout by remember { mutableStateOf(false) }
            var showFenEditor by remember { mutableStateOf(false) }
            var showVideoTrimmer by remember { mutableStateOf(false) }
            var showVideoCropper by remember { mutableStateOf(false) }
            var videoToProcess by remember { mutableStateOf<Uri?>(null) }
            var videoStartTime by remember { mutableStateOf(0L) }
            var videoEndTime by remember { mutableStateOf(0L) }
            var isLoading by remember { mutableStateOf(false) }
            var isVideoProcessing by remember { mutableStateOf(false) }
            var processingProgress by remember { mutableStateOf(0) }
            var processingStatus by remember { mutableStateOf("") }
            val coroutineScope = rememberCoroutineScope()

            // Собираем поток текущих кадров
            LaunchedEffect(isVideoProcessing) {
                if (isVideoProcessing) {
                    videoProcessor?.currentFrame?.collectLatest { frame ->
                        currentFrame = frame
                    }
                }
            }

            when {
                isVideoProcessing -> {
                    VideoProcessingScreen(
                        progress = processingProgress,
                        status = processingStatus,
                        currentFrame = currentFrame,
                        onCancel = {
                            isVideoProcessing = false
                            videoProcessor?.cancel()
                            processingProgress = 0
                            processingStatus = ""
                            currentFrame = null
                        }
                    )
                }
                showHistory -> {
                    HistoryScreen(
                        onBack = { showHistory = false },
                        onItemClick = { url ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            startActivity(intent)
                        },
                        viewModel = historyViewModel
                    )
                }
                showAbout -> {
                    AboutScreen(
                        onBack = { showAbout = false }
                    )
                }
                showFenEditor -> {
                    FenEditorScreen(
                        onBack = { showFenEditor = false }
                    )
                }
                showVideoTrimmer -> {
                    videoToProcess?.let { uri ->
                        VideoTrimmerScreen(
                            videoUri = uri,
                            onTrimConfirmed = { start, end ->
                                videoStartTime = start
                                videoEndTime = end
                                showVideoTrimmer = false
                                extractFirstFrame(uri, start)?.let { frame ->
                                    imageCropper.currentBitmap = frame
                                    showVideoCropper = true
                                }
                            },
                            onCancel = {
                                showVideoTrimmer = false
                                videoToProcess = null
                            }
                        )
                    }
                }
                showVideoCropper -> {
                    imageCropper.currentBitmap?.let {
                        ImageCropperScreen(
                            imageCropper = imageCropper,
                            onCropConfirmed = {
                                showVideoCropper = false
                                videoToProcess?.let { uri ->
                                    isVideoProcessing = true
                                    processVideoWithProgress(
                                        uri = uri,
                                        startTime = videoStartTime,
                                        endTime = videoEndTime,
                                        coroutineScope = coroutineScope,
                                        onProgressUpdate = { progress, status ->
                                            processingProgress = progress
                                            processingStatus = status
                                        },
                                        onComplete = {
                                            isVideoProcessing = false
                                            isLoading = false
                                            processingProgress = 0
                                            processingStatus = ""
                                            currentFrame = null
                                        }
                                    )
                                }
                            },
                            onCancel = {
                                showVideoCropper = false
                                videoToProcess = null
                            }
                        )
                    }
                }
                else -> {
                    MainScreen(
                        tfLiteInterpreter = tfLiteInterpreter,
                        croppedImageUri = croppedImageUri,
                        isLoading = isLoading,
                        onRecognizeImage = { uri ->
                            startCrop(uri)
                        },
                        onShowHistory = { showHistory = true },
                        onShowAbout = { showAbout = true },
                        onProcessVideo = { uri ->
                            videoToProcess = uri
                            showVideoTrimmer = true
                        },
                        onFenEditor = { showFenEditor = true },
                        viewModel = historyViewModel
                    )
                }
            }
        }
    }

    private fun processVideoWithProgress(
        uri: Uri,
        startTime: Long,
        endTime: Long,
        coroutineScope: CoroutineScope,
        onProgressUpdate: (Int, String) -> Unit,
        onComplete: () -> Unit
    ) {
        coroutineScope.launch {
            Log.d("VideoProcessing", "Starting video processing coroutine with time range: $startTime - $endTime")
            try {
                Log.d("VideoProcessing", "Entered try block")

                videoProcessor = VideoToPGNProcessor(
                    this@MainActivity,
                    tfLiteInterpreter,
                    imageCropper.cropRect,
                    onProgressUpdate = onProgressUpdate
                )
                Log.d("VideoProcessing", "Entered Processor")
                videoProcessor?.processVideoToPGN(uri, startTime, endTime) { pgn ->
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
                Log.e("VideoProcessing", "Error processing video", e)
                onComplete()
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

    private fun extractFirstFrame(videoUri: Uri, timeMs: Long = 0): Bitmap? {
        return try {
            val retriever = MediaMetadataRetriever().apply {
                setDataSource(this@MainActivity, videoUri)
            }
            val bitmap = retriever.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
            retriever.release()
            bitmap
        } catch (e: Exception) {
            Log.e("MainActivity", "Error extracting first frame", e)
            null
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == UCrop.REQUEST_CROP && resultCode == RESULT_OK) {
            val resultUri = UCrop.getOutput(data!!)
            if (resultUri != null) {
                croppedImageUri = resultUri
                croppedImageUri?.let {
                    recognizeFromImage(this, tfLiteInterpreter, it, historyViewModel)
                }
            }
        } else if (resultCode == UCrop.RESULT_ERROR) {
            val cropError = UCrop.getError(data!!)
            cropError?.printStackTrace()
        }
    }
}