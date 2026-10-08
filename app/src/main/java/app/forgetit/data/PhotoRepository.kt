package app.forgetit.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PhotoRepository(val dao: PhotoDao, val dir: File) {
    fun observeAll() = dao.observeAll()

    suspend fun listFor(type: OwnerType, id: Long) = dao.listFor(type.name, id)

    fun file(fileName: String) = File(dir, fileName)

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
