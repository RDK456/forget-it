package app.forgetit.grocery

import android.content.Context
import android.net.Uri
import app.forgetit.domain.GroceryScan
import app.forgetit.domain.ScannedItem
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ScanRequest(val uri: Uri? = null, val preloaded: List<app.forgetit.domain.Detected>? = null, val note: String? = null, val autoAdd: Boolean = false)

data class ScanRead(val text: String, val labels: List<ScannedItem>)

/** Reads a photo of a receipt or of groceries on this phone (ML Kit, bundled models, nothing uploaded). */
object GroceryScanner {
    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
        addOnSuccessListener { c.resume(it) }
        addOnFailureListener { c.resumeWithException(it) }
    }

    /** OCR returns a receipt's name column and price column as separate blocks; rejoin pieces that sit on the same row. */
    internal fun rows(t: Text): String {
        val lines = t.textBlocks.flatMap { it.lines }.filter { it.boundingBox != null }
        val sorted = lines.sortedBy { it.boundingBox!!.centerY() }
        val rows = mutableListOf<MutableList<Text.Line>>()
        for (l in sorted) {
            val last = rows.lastOrNull()
            val box = l.boundingBox!!
            if (last != null && kotlin.math.abs(last.first().boundingBox!!.centerY() - box.centerY()) < box.height() * 0.6) last += l else rows += mutableListOf(l)
        }
        return rows.joinToString("\n") { r -> r.sortedBy { it.boundingBox!!.left }.joinToString("  ") { it.text } }
    }

    /** Text found in the photo (rows rejoined) plus confident grocery labels, for the classifier to sort. */
    suspend fun read(context: Context, uri: Uri): ScanRead {
        val image = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
        try {
            val text = rows(recognizer.process(image).await())
            val labels = GroceryScan.fromLabels(labeler.process(image).await().map { it.text to it.confidence })
            return ScanRead(text, labels)
        } finally {
            recognizer.close()
            labeler.close()
        }
    }

    suspend fun scan(context: Context, uri: Uri, currency: String): List<ScannedItem> {
        val image = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
        try {
            val receipt = GroceryScan.fromReceipt(rows(recognizer.process(image).await()), currency)
            val labels = GroceryScan.fromLabels(labeler.process(image).await().map { it.text to it.confidence })
            return GroceryScan.combine(receipt, labels)
        } finally {
            recognizer.close()
            labeler.close()
        }
    }
}
