package app.forgetit.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

class PhotoRepository(val dao: PhotoDao, val dir: File) {
    companion object { const val MAX_PHOTOS = 5 }

    fun observeAll() = dao.observeAll()

    suspend fun listFor(type: OwnerType, id: Long) = dao.listFor(type.name, id)

    fun file(fileName: String) = File(dir, fileName)

    /** Adds one photo; false when the record already has [MAX_PHOTOS] or the image cannot be read. */
    suspend fun add(context: Context, type: OwnerType, ownerId: Long, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val existing = dao.listFor(type.name, ownerId)
        if (existing.size >= MAX_PHOTOS) return@withContext false
        val name = PhotoStore.import(context, uri, dir) ?: return@withContext false
        dao.insert(PhotoEntity(ownerType = type.name, ownerId = ownerId, fileName = name,
            addedEpochDay = LocalDate.now().toEpochDay(), sortOrder = existing.size))
        true
    }

    /** Photos added before a new record was saved use owner id 0; move them to the real id. */
    suspend fun reassign(type: OwnerType, from: Long, to: Long) = dao.reassign(type.name, from, to)

    suspend fun delete(photoId: Long) = withContext(Dispatchers.IO) {
        dao.get(photoId)?.let { file(it.fileName).delete() }
        dao.delete(photoId)
    }

    /** Remove every photo of a record (rows and files); called when the record is deleted. */
    suspend fun deleteAll(type: OwnerType, id: Long) = withContext(Dispatchers.IO) {
        dao.listFor(type.name, id).forEach { file(it.fileName).delete() }
        dao.deleteFor(type.name, id)
    }

    /** Delete files in the photo directory that no row references, for example after a crash mid-save. */
    suspend fun sweepOrphans() = withContext(Dispatchers.IO) {
        val referenced = dao.allFileNames().toSet()
        dir.listFiles()?.filter { it.name !in referenced }?.forEach { it.delete() }
    }
}
