package com.example.chesspiecesrecognition

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
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
import com.yalantis.ucrop.UCrop
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.IOException

class MainActivity : ComponentActivity() {
    private lateinit var tfLiteInterpreter: Interpreter
    private var croppedImageUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadModel()
        setContent {
            MainScreen(
                tfLiteInterpreter = tfLiteInterpreter,
                croppedImageUri = croppedImageUri,
                onCropImage = { uri -> startCrop(uri) }
            )
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
    onCropImage: (Uri) -> Unit
) {
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = uri
            onCropImage(uri)
        }
    }

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
            croppedImageUri?.let {
                recognizeFromImage(context, tfLiteInterpreter, it)
            } ?: run {
                Log.e("MainScreen", "Cropped image is null")
            }
        }) {
            Text("Распознать фигуры")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = {
            selectedImageUri?.let {
                onCropImage(it)
            }
        }) {
            Text("Обрезать Фото")
        }
    }
}
