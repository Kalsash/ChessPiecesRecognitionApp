package com.example.chesspiecesrecognition.views

import ActionButton
import ChessboardBackground
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.chesspiecesrecognition.HistoryViewModel
import org.tensorflow.lite.Interpreter

@Composable
fun MainScreen(
    tfLiteInterpreter: Interpreter,
    croppedImageUri: Uri?,
    isLoading: Boolean,
    onRecognizeImage: (Uri) -> Unit,
    onRecognize3D: (Uri) -> Unit,
    onShowHistory: () -> Unit,
    onProcessVideo: (Uri) -> Unit,
    onShowAbout: () -> Unit,
    onFenEditor: () -> Unit,
    viewModel: HistoryViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onRecognizeImage(uri)
        }
    }

    val image3DLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onRecognize3D(uri)
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
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "♔ Chess Pieces Recognition ♔",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(shadow = Shadow(color = Color.Black, blurRadius = 4f)),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
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
                    text = "Распознать фигуры по фото",
                    icon = Icons.Default.Search,

                ) {
                    imageLauncher.launch("image/*")
                }

                ActionButton(
                    text = "3D Распознавание шахмат",
                    icon = Icons.Default.Search,
                ) {
                    image3DLauncher.launch("image/*")
                }

                ActionButton(
                    text = "Извлечь шахматную партию из видео",
                    icon = Icons.Default.PlayArrow,
                ) {
                    videoLauncher.launch("video/*")
                }

                ActionButton(
                    text = "FEN-редактор",
                    icon = Icons.Default.Edit,
                ) {
                    onFenEditor()
                }

                ActionButton(
                    text = "История распознаваний",
                    icon = Icons.Default.List,
                ) {
                    onShowHistory()
                }

                ActionButton(
                    text = "О приложении",
                    icon = Icons.Default.Info,
                ) {
                    onShowAbout()
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