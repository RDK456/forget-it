package app.forgetit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File

/** Cover photo when there is one, else a colored circle with the initial. */
@Composable
fun Avatar(name: String, photo: File?, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    if (photo != null) {
        AsyncImage(
            model = photo, contentDescription = "Photo of $name", contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape),
        )
    } else {
        val hue = name.lowercase().hashCode().mod(360).toFloat()
        Box(
            modifier.size(size).clip(CircleShape).background(Color.hsv(hue, 0.45f, 0.72f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(name.trim().firstOrNull()?.uppercase() ?: "?", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}
