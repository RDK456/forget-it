package app.forgetit.data

import android.content.Context
import android.content.Intent
import app.forgetit.AppContainer
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** One zip with the whole database and every photo. Restoring replaces everything on this phone, then restarts the app. */
object FullBackup {
    private const val DB = "forgetit.db"
    private const val PHOTOS = "photos/"
    private val SQLITE_MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.ISO_8859_1)

    /** Returns how many files were written. */
    fun write(c: AppContainer, out: OutputStream): Int {
        // Fold the write-ahead log into the main file so the copy is complete.
        c.db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        var n = 0
        ZipOutputStream(out.buffered()).use { zip ->
            fun add(name: String, f: File) {
                zip.putNextEntry(ZipEntry(name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                n++
            }
            add(DB, c.context.getDatabasePath(DB))
            File(c.context.filesDir, "photos").listFiles()?.filter { it.isFile }?.forEach { add(PHOTOS + it.name, it) }
        }
        return n
    }

    private fun safeName(n: String) = n == DB || (n.startsWith(PHOTOS) && n.length > PHOTOS.length && '/' !in n.removePrefix(PHOTOS) && ".." !in n && '\\' !in n)

    /** Returns an error message, or null after the files were replaced. The caller restarts the app. */
    fun restore(c: AppContainer, input: InputStream): String? {
        val tmp = File(c.context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        ZipInputStream(input.buffered()).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                if (!e.isDirectory && safeName(e.name)) File(tmp, e.name).also { it.parentFile?.mkdirs() }.outputStream().use { zip.copyTo(it) }
                e = zip.nextEntry
            }
        }
        val db = File(tmp, DB)
        if (!db.exists()) return "That file is not a Forget-it backup."
        val header = db.inputStream().use { s -> ByteArray(64).also { s.read(it) } }
        if (!header.copyOf(SQLITE_MAGIC.size).contentEquals(SQLITE_MAGIC)) return "That file is not a Forget-it backup."
        val version = ((header[60].toInt() and 0xFF) shl 24) or ((header[61].toInt() and 0xFF) shl 16) or ((header[62].toInt() and 0xFF) shl 8) or (header[63].toInt() and 0xFF)
        val current = c.db.openHelper.readableDatabase.version
        if (version > current) return "This backup is from a newer version of Forget-it. Update the app first."
        c.db.close()
        val target = c.context.getDatabasePath(DB)
        listOf(target, File(target.path + "-wal"), File(target.path + "-shm")).forEach { it.delete() }
        db.copyTo(target, overwrite = true)
        val photos = File(c.context.filesDir, "photos").apply { mkdirs() }
        photos.listFiles()?.forEach { it.delete() }
        File(tmp, "photos").listFiles()?.forEach { it.copyTo(File(photos, it.name), overwrite = true) }
        tmp.deleteRecursively()
        return null
    }

    fun restart(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (launch != null) context.startActivity(launch)
        Runtime.getRuntime().exit(0)
    }
}
