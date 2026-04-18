package com.example.chesspiecesrecognition

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.example.chesspiecesrecognition.ui.theme.ChessPiecesRecognitionTheme
import com.example.chesspiecesrecognition.views.CropChoiceDialog
import com.example.chesspiecesrecognition.views.ImageCropperScreen
import com.example.chesspiecesrecognition.views.MainScreen
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel

class MainActivity : ComponentActivity() {
    private lateinit var tfLiteInterpreter: Interpreter
    private lateinit var chessboardDetector: ChessboardDetector
    private var chess3DRecognizer: Chess3DRecognizer? = null
    private var croppedImageUri by mutableStateOf<Uri?>(null)
    private lateinit var historyViewModel: HistoryViewModel
    private val imageCropper by lazy { ImageCropper(this) }
    private var currentFrame by mutableStateOf<Bitmap?>(null)
    private var videoProcessor: VideoToPGNProcessor? = null
    private var isLoading3D by mutableStateOf(false)  // ТОЛЬКО для 3D распознавания
    private var showCropChoiceDialog by mutableStateOf(false)
    private var pendingImageUri by mutableStateOf<Uri?>(null)
    private var is3DRecognitionPending by mutableStateOf(false)
    private var pending3DUri by mutableStateOf<Uri?>(null)

    // Общие состояния загрузки
    private var isLoading by mutableStateOf(false)
    private var loadingType by mutableStateOf("")
    private var backPressedTime = 0L

    // SharedPreferences для хранения настроек
    private val sharedPreferences by lazy {
        getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadModel()
        chessboardDetector = ChessboardDetector(this)
        historyViewModel = HistoryViewModel(application)

        // Отключаем системные UI бары
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.navigationBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setContent {
            ChessPiecesRecognitionTheme {
                var showHistory by remember { mutableStateOf(false) }
                var showAbout by remember { mutableStateOf(false) }
                var showFenEditor by remember { mutableStateOf(false) }
                var showVideoTrimmer by remember { mutableStateOf(false) }
                var showVideoCropper by remember { mutableStateOf(false) }
                var videoToProcess by remember { mutableStateOf<Uri?>(null) }
                var videoStartTime by remember { mutableStateOf(0L) }
                var videoEndTime by remember { mutableStateOf(0L) }
                var frameIntervalMs by remember { mutableStateOf(1000L) }
                var isVideoProcessing by remember { mutableStateOf(false) }
                var processingProgress by remember { mutableStateOf(0) }
                var processingStatus by remember { mutableStateOf("") }

                val coroutineScope = rememberCoroutineScope()
                val context = LocalContext.current

                // Диалог выбора типа обрезки
                if (showCropChoiceDialog) {
                    CropChoiceDialog(
                        sharedPreferences = sharedPreferences,
                        onAutoCropSelected = {
                            showCropChoiceDialog = false
                            pendingImageUri?.let { uri ->
                                if (is3DRecognitionPending) {
                                    startAutoCropThenManual3D(uri)
                                } else {
                                    startAutoCropThenManual(uri)
                                }
                            }
                            pendingImageUri = null
                        },
                        onManualCropSelected = {
                            showCropChoiceDialog = false
                            pendingImageUri?.let { uri ->
                                if (is3DRecognitionPending) {
                                    startManualCrop3D(uri, null)
                                } else {
                                    startManualCrop(uri, null)
                                }
                            }
                            pendingImageUri = null
                        },
                        onNoCropSelected = {
                            showCropChoiceDialog = false
                            pendingImageUri?.let { uri ->
                                if (is3DRecognitionPending) {
                                    process3DRecognition(uri, lifecycleScope)
                                } else {
                                    processNoCropRecognition(uri)
                                }
                            }
                            pendingImageUri = null
                        },
                        onCancel = {
                            showCropChoiceDialog = false
                            pendingImageUri = null
                            is3DRecognitionPending = false
                            setLoading(false, "")
                        }
                    )
                }

                // Собираем поток текущих кадров
                LaunchedEffect(isVideoProcessing) {
                    if (isVideoProcessing) {
                        videoProcessor?.currentFrame?.collectLatest { frame ->
                            currentFrame = frame
                        }
                    }
                }

                // Показываем индикатор загрузки ТОЛЬКО при 3D распознавании
//                if (isLoading3D) {
//                    Dialog(
//                        onDismissRequest = { /* Нельзя закрыть */ }
//                    ) {
//                        Box(
//                            modifier = Modifier
//                                .fillMaxSize()
//                                .background(Color.Black.copy(alpha = 0.5f)),
//                            contentAlignment = Alignment.Center
//                        ) {
//                            Column(
//                                horizontalAlignment = Alignment.CenterHorizontally,
//                                verticalArrangement = Arrangement.Center,
//                                modifier = Modifier
//                                    .padding(16.dp)
//                            ) {
//                                CircularProgressIndicator(color = Color.White)
//                                Spacer(modifier = Modifier.height(16.dp))
//                                Text(
//                                    text = "3D Распознавание шахматных фигур...",
//                                    color = Color.White
//                                )
//                            }
//                        }
//                    }
//                }

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
                                setLoading(false, "")
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
                                onTrimConfirmed = { start, end, interval ->
                                    videoStartTime = start
                                    videoEndTime = end
                                    frameIntervalMs = interval as Long
                                    showVideoTrimmer = false
                                    extractFirstFrame(uri, start)?.let { frame ->
                                        imageCropper.currentBitmap = frame
                                        showVideoCropper = true
                                    }
                                },
                                onCancel = {
                                    showVideoTrimmer = false
                                    videoToProcess = null
                                    setLoading(false, "")
                                }
                            )
                        }
                    }

                    showVideoCropper -> {
                        imageCropper.currentBitmap?.let { it ->
                            ImageCropperScreen(
                                imageCropper = imageCropper,
                                chessboardDetector = chessboardDetector,
                                onCropConfirmed = { isBlackPlayer ->
                                    showVideoCropper = false
                                    videoToProcess?.let { uri ->
                                        isVideoProcessing = true
                                        setLoading(false, "") // Сбрасываем загрузку перед обработкой видео
                                        processVideoWithProgress(
                                            uri = uri,
                                            startTime = videoStartTime,
                                            endTime = videoEndTime,
                                            frameIntervalMs = frameIntervalMs,
                                            isBlackPlayer = isBlackPlayer,
                                            coroutineScope = coroutineScope,
                                            onProgressUpdate = { progress, status ->
                                                processingProgress = progress
                                                processingStatus = status
                                            },
                                            onComplete = {
                                                isVideoProcessing = false
                                                processingProgress = 0
                                                processingStatus = ""
                                                currentFrame = null
                                                setLoading(false, "")
                                            }
                                        )
                                    }
                                },
                                onCancel = {
                                    showVideoCropper = false
                                    videoToProcess = null
                                    setLoading(false, "")
                                }
                            )
                        }
                    }

                    else -> {
                        MainScreen(
                            tfLiteInterpreter = tfLiteInterpreter,
                            croppedImageUri = croppedImageUri,
                            isLoading = isLoading,
                            loadingType = loadingType,
                            onRecognizeImage = { uri ->
                                if (isLoading) return@MainScreen  // Блокируем нажатия во время загрузки

                                // Проверяем сохраненные настройки
                                val rememberChoice =
                                    sharedPreferences.getBoolean("remember_crop_choice", false)
                                val userChoice = sharedPreferences.getString("user_crop_choice", "")

                                is3DRecognitionPending = false // Это 2D распознавание
                                setLoading(true, "2D")  // Устанавливаем загрузку

                                if (rememberChoice && userChoice?.isNotEmpty() == true) {
                                    when (userChoice) {
                                        "auto" -> startAutoCropThenManual(uri)
                                        "manual" -> startManualCrop(uri, null)
                                        "no_crop" -> processNoCropRecognition(uri)
                                    }
                                } else {
                                    pendingImageUri = uri
                                    showCropChoiceDialog = true
                                }
                            },
                            onRecognize3D = { uri ->
                                if (isLoading) return@MainScreen  // Блокируем нажатия во время загрузки

                                // Сначала показываем диалог обрезки для 3D
                                val rememberChoice =
                                    sharedPreferences.getBoolean("remember_crop_choice", false)
                                val userChoice = sharedPreferences.getString("user_crop_choice", "")

                                is3DRecognitionPending = true // Это 3D распознавание
                                setLoading(true, "3D")  // Устанавливаем загрузку

                                if (rememberChoice && userChoice?.isNotEmpty() == true) {
                                    when (userChoice) {
                                        "auto" -> startAutoCropThenManual3D(uri)
                                        "manual" -> startManualCrop3D(uri, null)
                                        "no_crop" -> process3DRecognition(uri, lifecycleScope)
                                    }
                                } else {
                                    pendingImageUri = uri
                                    showCropChoiceDialog = true
                                }
                            },
                            onShowHistory = {
                                if (!isLoading) showHistory = true
                            },
                            onShowAbout = {
                                if (!isLoading) showAbout = true
                            },
                            onProcessVideo = { uri ->
                                if (!isLoading) {
                                    videoToProcess = uri
                                    showVideoTrimmer = true
                                }
                            },
                            onFenEditor = {
                                if (!isLoading) showFenEditor = true
                            },
                            viewModel = historyViewModel
                        )
                    }
                }
            }
        }
    }

    override fun onBackPressed() {
        // Если идет загрузка - сбрасываем загрузку и выходим
        if (isLoading) {
            resetLoadingStates()
            return
        }

        // Если показывается диалог выбора обрезки - закрываем его
        if (showCropChoiceDialog) {
            showCropChoiceDialog = false
            pendingImageUri = null
            is3DRecognitionPending = false
            setLoading(false, "")
            return
        }

        // Стандартное поведение для двойного нажатия для выхода
        if (backPressedTime + 2000 > System.currentTimeMillis()) {
            super.onBackPressed()
        } else {
            Toast.makeText(this, "Нажмите ещё раз для выхода", Toast.LENGTH_SHORT).show()
            backPressedTime = System.currentTimeMillis()
        }
    }

    // Функция: Управление состоянием загрузки
    private fun setLoading(isLoading: Boolean, type: String = "") {
        this.isLoading = isLoading
        this.loadingType = type

        // Если сбрасываем загрузку, то сбрасываем и все связанные состояния
        if (!isLoading) {
            resetLoadingStates()
        }
    }

    private fun resetLoadingStates() {
        isLoading3D = false
        is3DRecognitionPending = false
        showCropChoiceDialog = false
        pendingImageUri = null
    }

    private fun process3DRecognition(
        uri: Uri,
        coroutineScope: CoroutineScope
    ) {
        isLoading3D = true
        setLoading(true, "3D")

        coroutineScope.launch(Dispatchers.IO) {
            try {
                if (chess3DRecognizer == null) {
                    chess3DRecognizer = Chess3DRecognizer(this@MainActivity)
                }

                val bitmap = loadBitmapFromUri(uri)
                if (bitmap != null) {
                    Log.d("3DRecognition", "Изображение загружено: ${bitmap.width}x${bitmap.height}")

                    val result = chess3DRecognizer!!.recognize3DChessboard(bitmap)

                    runOnUiThread {
                        isLoading3D = false
                        is3DRecognitionPending = false
                        setLoading(false, "")

                        if (result.success) {
                            val url = chess3DRecognizer!!.getLichessUrl(result.fen)

                            // ВАЖНО: Сохраняем изображение с помощью FileUtils
                            val imageUri = FileUtils.saveBitmapToInternalStorage(
                                this@MainActivity,
                                bitmap,
                                90
                            )

                            // Теперь сохраняем и изображение, и ссылку в историю
                            historyViewModel.addHistoryItem(imageUri, url)

                            Toast.makeText(
                                this@MainActivity,
                                "Распознано ${result.detections.size} фигур. Открываем Lichess...",
                                Toast.LENGTH_LONG
                            ).show()

                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            startActivity(intent)

                            Log.d("3DRecognition", "FEN: ${result.fen}")
                            Log.d("3DRecognition", "URL: $url")
                            Log.d("3DRecognition", "Изображение сохранено: $imageUri")
                            Log.d("3DRecognition", "Обнаружено фигур: ${result.detections.size}")

                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "Ошибка распознавания: ${result.error ?: "неизвестная ошибка"}",
                                Toast.LENGTH_LONG
                            ).show()
                            Log.e("3DRecognition", "Ошибка: ${result.error}")
                        }
                    }
                } else {
                    runOnUiThread {
                        isLoading3D = false
                        is3DRecognitionPending = false
                        setLoading(false, "")
                        Toast.makeText(
                            this@MainActivity,
                            "Не удалось загрузить изображение",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "3D Recognition error", e)
                runOnUiThread {
                    isLoading3D = false
                    is3DRecognitionPending = false
                    setLoading(false, "")
                    Toast.makeText(
                        this@MainActivity,
                        "Ошибка обработки: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun processNoCropRecognition(uri: Uri) {
        setLoading(true, "2D")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("NoCrop", "Начинаем распознавание без обрезки: $uri")

                val bitmap = loadBitmapFromUri(uri)
                if (bitmap != null) {
                    runOnUiThread {
                        croppedImageUri = uri
                        recognizeFromImage(this@MainActivity, tfLiteInterpreter, uri, historyViewModel)
                    }
                } else {
                    runOnUiThread {
                        showToast("Не удалось загрузить изображение")
                        setLoading(false, "")
                    }
                }
            } catch (e: Exception) {
                Log.e("NoCrop", "Ошибка при распознавании без обрезки", e)
                runOnUiThread {
                    showToast("Ошибка при обработке изображения")
                    setLoading(false, "")
                }
            }
        }
    }

    private fun startAutoCropThenManual(sourceUri: Uri) {
        setLoading(true, "2D")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("AutoCrop", "Начинаем авто-обрезку для URI: $sourceUri")

                val bitmap = loadBitmapFromUri(sourceUri) ?: run {
                    runOnUiThread {
                        Log.e("AutoCrop", "Не удалось загрузить изображение")
                        showToast("Не удалось загрузить изображение")
                        setLoading(false, "")
                        startManualCrop(sourceUri, null)
                    }
                    return@launch
                }

                Log.d("AutoCrop", "Изображение загружено: ${bitmap.width}x${bitmap.height}")

                val detection = chessboardDetector.detectChessboard(bitmap)

                runOnUiThread {
                    if (detection != null && detection.confidence > 0.5f) {
                        Log.d("AutoCrop", "Шахматная доска найдена: confidence=${detection.confidence}, rect=${detection.boundingBox}")

                        val croppedBitmap = chessboardDetector.cropChessboard(bitmap, detection)
                        val file = saveBitmapToCache(croppedBitmap, "auto_cropped_${System.currentTimeMillis()}.jpg")

                        if (file != null && file.exists()) {
                            val croppedUri = Uri.fromFile(file)
                            startManualCrop(croppedUri, croppedBitmap)
                        } else {
                            Log.e("AutoCrop", "Не удалось сохранить обрезанное изображение")
                            showToast("Ошибка при сохранении")
                            setLoading(false, "")
                            startManualCrop(sourceUri, null)
                        }
                    } else {
                        Log.d("AutoCrop", "Шахматная доска не найдена или уверенность слишком низкая")
                        showToast("Шахматная доска не найдена, используйте ручную обрезку")
                        setLoading(false, "")
                        startManualCrop(sourceUri, null)
                    }
                }
            } catch (e: Exception) {
                Log.e("AutoCrop", "Ошибка при автоматической обрезке", e)
                runOnUiThread {
                    showToast("Ошибка при обработке изображения")
                    setLoading(false, "")
                    startManualCrop(sourceUri, null)
                }
            }
        }
    }

    private fun startAutoCropThenManual3D(sourceUri: Uri) {
        setLoading(true, "3D")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("AutoCrop3D", "Начинаем авто-обрезку для 3D распознавания: $sourceUri")

                val bitmap = loadBitmapFromUri(sourceUri) ?: run {
                    runOnUiThread {
                        Log.e("AutoCrop3D", "Не удалось загрузить изображение")
                        showToast("Не удалось загрузить изображение")
                        setLoading(false, "")
                        startManualCrop3D(sourceUri, null)
                    }
                    return@launch
                }

                Log.d("AutoCrop3D", "Изображение загружено: ${bitmap.width}x${bitmap.height}")

                val detection = chessboardDetector.detectChessboard(bitmap)

                runOnUiThread {
                    if (detection != null && detection.confidence > 0.5f) {
                        Log.d("AutoCrop3D", "Шахматная доска найдена: confidence=${detection.confidence}, rect=${detection.boundingBox}")

                        val croppedBitmap = chessboardDetector.cropChessboard(bitmap, detection)
                        val file = saveBitmapToCache(croppedBitmap, "auto_cropped_3d_${System.currentTimeMillis()}.jpg")

                        if (file != null && file.exists()) {
                            val croppedUri = Uri.fromFile(file)
                            startManualCrop3D(croppedUri, croppedBitmap)
                        } else {
                            Log.e("AutoCrop3D", "Не удалось сохранить обрезанное изображение")
                            showToast("Ошибка при сохранении")
                            setLoading(false, "")
                            startManualCrop3D(sourceUri, null)
                        }
                    } else {
                        Log.d("AutoCrop3D", "Шахматная доска не найдена или уверенность слишком низкая")
                        showToast("Шахматная доска не найдена, используйте ручную обрезку")
                        setLoading(false, "")
                        startManualCrop3D(sourceUri, null)
                    }
                }
            } catch (e: Exception) {
                Log.e("AutoCrop3D", "Ошибка при автоматической обрезке", e)
                runOnUiThread {
                    showToast("Ошибка при обработке изображения")
                    setLoading(false, "")
                    startManualCrop3D(sourceUri, null)
                }
            }
        }
    }

    private fun startManualCrop(sourceUri: Uri, autoCroppedBitmap: Bitmap? = null) {
        if (sourceUri.scheme != null) {
            val destinationUri = Uri.fromFile(File(cacheDir, "final_cropped_${System.currentTimeMillis()}.jpg"))

            autoCroppedBitmap?.let {
                imageCropper.currentBitmap = it
            }

            UCrop.of(sourceUri, destinationUri)
                .withAspectRatio(1f, 1f)
                .withMaxResultSize(800, 800)
                .start(this)
        } else {
            Log.e("UCrop", "Source URI is invalid")
            showToast("Неверный URI изображения")
            setLoading(false, "")
        }
    }

    private fun startManualCrop3D(sourceUri: Uri, autoCroppedBitmap: Bitmap? = null) {
        if (sourceUri.scheme != null) {
            val destinationUri = Uri.fromFile(File(cacheDir, "final_cropped_3d_${System.currentTimeMillis()}.jpg"))

            autoCroppedBitmap?.let {
                imageCropper.currentBitmap = it
            }

            UCrop.of(sourceUri, destinationUri)
                .withAspectRatio(1f, 1f)
                .withMaxResultSize(800, 800)
                .start(this)
        } else {
            Log.e("UCrop", "Source URI is invalid")
            showToast("Неверный URI изображения")
            setLoading(false, "")
        }
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            Log.e("AutoCrop", "Ошибка при загрузке изображения", e)
            null
        }
    }

    private fun saveBitmapToCache(bitmap: Bitmap, filename: String): File? {
        return try {
            val file = File(cacheDir, filename)
            FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            }
            file
        } catch (e: Exception) {
            Log.e("AutoCrop", "Ошибка при сохранении изображения", e)
            null
        }
    }

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun processVideoWithProgress(
        uri: Uri,
        startTime: Long,
        endTime: Long,
        frameIntervalMs: Long,
        isBlackPlayer: Boolean,
        coroutineScope: CoroutineScope,
        onProgressUpdate: (Int, String) -> Unit,
        onComplete: () -> Unit
    ) {
        coroutineScope.launch {
            try {
                videoProcessor = VideoToPGNProcessor(
                    this@MainActivity,
                    tfLiteInterpreter,
                    imageCropper.cropRect,
                    frameIntervalMs,
                    isBlackPlayer,
                    onProgressUpdate = onProgressUpdate
                )

                videoProcessor?.processVideoToPGN(uri, startTime, endTime) { pgn ->
                    coroutineScope.launch {
                        if (pgn.startsWith("Error:")) {
                            Log.e("VideoProcessing", pgn)
                        } else {
                            val url = "https://lichess.org/paste?pgn=${Uri.encode(pgn)}"
                            historyViewModel.addHistoryItem("video_processing", url)
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            startActivity(intent)
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

        if (requestCode == UCrop.REQUEST_CROP) {
            when (resultCode) {
                RESULT_OK -> {
                    val resultUri = UCrop.getOutput(data!!)
                    if (resultUri != null) {
                        // Проверяем, для какого типа распознавания предназначена обрезка
                        if (is3DRecognitionPending) {
                            // Для 3D распознавания
                            process3DRecognition(resultUri, lifecycleScope)
                        } else {
                            // Для обычного 2D распознавания
                            croppedImageUri = resultUri
                            croppedImageUri?.let {
                                recognizeFromImage(this, tfLiteInterpreter, it, historyViewModel)
                            }
                        }
                    }
                }
                RESULT_CANCELED -> {
                    // Пользователь нажал "Назад" в UCrop
                    setLoading(false, "")
                    is3DRecognitionPending = false
                    showToast("Обрезка отменена")
                }
                UCrop.RESULT_ERROR -> {
                    val cropError = UCrop.getError(data!!)
                    cropError?.printStackTrace()
                    showToast("Ошибка при обрезке: ${cropError?.message}")
                    setLoading(false, "")
                    is3DRecognitionPending = false
                }
            }
        }
    }

    // Функция вызывается после завершения распознавания (используется в recognizeFromImage)
    fun onRecognitionComplete() {
        setLoading(false, "")
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            chessboardDetector.close()
            chess3DRecognizer?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}