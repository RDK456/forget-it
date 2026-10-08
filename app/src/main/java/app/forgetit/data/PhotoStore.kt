package app.forgetit.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.util.UUID
import kotlin.math.max

/** Turns a picked or captured image into a small JPEG inside the app private photo directory. */
object PhotoStore {
    const val MAX_SIDE = 1280
    const val JPEG_QUALITY = 80

    /** Returns the new file name inside [dir], or null when the image cannot be read. */
    fun import(context: Context, uri: Uri, dir: File): String? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE * 2) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null

        val orientation = runCatching {
            resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }
        val scale = MAX_SIDE.toFloat() / max(decoded.width, decoded.height)
        if (scale < 1f) matrix.postScale(scale, scale)
        val out = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

        dir.mkdirs()
        val name = UUID.randomUUID().toString() + ".jpg"
        File(dir, name).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        if (out !== decoded) decoded.recycle()
        return name
    }
}
