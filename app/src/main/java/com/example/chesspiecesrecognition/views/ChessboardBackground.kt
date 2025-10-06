import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

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