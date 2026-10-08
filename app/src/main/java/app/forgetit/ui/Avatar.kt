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
        val tint = AVATAR_COLORS[name.lowercase().hashCode().mod(AVATAR_COLORS.size)]
        Box(
            modifier.size(size).clip(CircleShape).background(tint),
            contentAlignment = Alignment.Center,
        ) {
            Text(name.trim().firstOrNull()?.uppercase() ?: "?", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

/** A small fixed palette in the app colours, so avatars look chosen rather than random. */
private val AVATAR_COLORS = listOf(0xFF0B8F88, 0xFF5560E0, 0xFFB3254F, 0xFFB26B00, 0xFF2E86C1, 0xFF4F8A2B, 0xFF8E5BD0, 0xFF5E7371).map { Color(it) }
