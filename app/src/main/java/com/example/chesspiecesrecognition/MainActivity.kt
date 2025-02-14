package com.example.chesspiecesrecognition

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.chesspiecesrecognition.ui.theme.ChessPiecesRecognitionTheme
import java.io.IOException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChessPiecesRecognitionTheme {
                MainScreen(contentResolver = contentResolver)
            }
        }
    }
}

@Composable
fun MainScreen(contentResolver: android.content.ContentResolver) {
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var blackAndWhiteBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
        blackAndWhiteBitmap = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (blackAndWhiteBitmap != null) {
            Image(
                bitmap = blackAndWhiteBitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(300.dp)
            )
        } else {
            selectedImageUri?.let { uri ->
                Image(
                    painter = rememberAsyncImagePainter(uri),
                    contentDescription = null,
                    modifier = Modifier.size(300.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            launcher.launch("image/*")
        }) {
            Text("Выберите фото")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = {
            selectedImageUri?.let { uri ->
                blackAndWhiteBitmap = convertToBlackAndWhite(uri, contentResolver)
            }
        }) {
            Text("Преобразовать в черно-белое")
        }
    }
}

private fun convertToBlackAndWhite(uri: Uri, contentResolver: android.content.ContentResolver): Bitmap? {
    val inputStream = try {
        contentResolver.openInputStream(uri)
    } catch (e: IOException) {
        e.printStackTrace()
        return null
    }

    val bitmap = BitmapFactory.decodeStream(inputStream) ?: return null

    val width = bitmap.width
    val height = bitmap.height
    val bmpBlackAndWhite = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    for (x in 0 until width) {
        for (y in 0 until height) {
            val pixel = bitmap.getPixel(x, y)
            val avg = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
            bmpBlackAndWhite.setPixel(x, y, Color.rgb(avg, avg, avg))
        }
    }

    return bmpBlackAndWhite
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    ChessPiecesRecognitionTheme {
        Text("Превью MainScreen")
    }
}