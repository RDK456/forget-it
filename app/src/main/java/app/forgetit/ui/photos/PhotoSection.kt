package app.forgetit.ui.photos

import app.forgetit.ui.AppIcons
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.forgetit.data.OwnerType
import app.forgetit.data.PhotoEntity
import app.forgetit.data.PhotoRepository
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/** Thumbnail strip with Camera and Gallery buttons for one record; tap a thumbnail to view or delete. */
@Composable
fun PhotoSection(repo: PhotoRepository, type: OwnerType, ownerId: Long, modifier: Modifier = Modifier) {
    val all by repo.observeAll().collectAsStateWithLifecycle(emptyList())
    val mine = all.filter { it.ownerType == type.name && it.ownerId == ownerId }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var viewer by remember { mutableStateOf<Int?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var cameraFile by remember { mutableStateOf<File?>(null) }

    fun add(uri: Uri) = scope.launch {
        message = if (repo.add(ctx, type, ownerId, uri)) null else "Could not add photo (limit is ${PhotoRepository.MAX_PHOTOS})"
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) add(uri) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = cameraFile
        if (ok && f != null) {
            scope.launch {
                add(Uri.fromFile(f)).join()
                f.delete()
            }
        } else f?.delete()
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Photos (${mine.size}/${PhotoRepository.MAX_PHOTOS})", style = MaterialTheme.typography.labelLarge)
        if (mine.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(mine, key = { _, p -> p.id }) { i, p ->
                    AsyncImage(
                        model = repo.file(p.fileName), contentDescription = "Photo ${i + 1}", contentScale = ContentScale.Crop,
                        modifier = Modifier.size(84.dp).clip(RoundedCornerShape(8.dp)).clickable { viewer = i },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val f = File(ctx.cacheDir, "camera/${UUID.randomUUID()}.jpg").also { it.parentFile?.mkdirs() }
                cameraFile = f
                camera.launch(FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f))
            }) { Text("Camera") }
            OutlinedButton(onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) { Text("Gallery") }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }

    viewer?.let { start ->
        if (mine.isEmpty()) viewer = null else PhotoViewer(mine, start.coerceIn(0, mine.lastIndex), repo, onClose = { viewer = null })
    }
}

@Composable
private fun PhotoViewer(photos: List<PhotoEntity>, start: Int, repo: PhotoRepository, onClose: () -> Unit) {
    val pager = rememberPagerState(initialPage = start) { photos.size }
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background0()) {
            HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                AsyncImage(
                    model = repo.file(photos[page].fileName), contentDescription = "Photo ${page + 1} of ${photos.size}",
                    contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(),
                )
            }
            Row(Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = { scope.launch { repo.delete(photos[pager.currentPage].id) } }) {
                    Icon(AppIcons.Delete, contentDescription = "Delete photo", tint = Color.White)
                }
                IconButton(onClick = onClose) { Icon(AppIcons.Close, contentDescription = "Close", tint = Color.White) }
            }
        }
    }
}

private fun Modifier.background0(): Modifier = this.then(Modifier.background(Color.Black))
