package com.obpiub.myapplication.data.local

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.obpiub.myapplication.domain.repository.NoteImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class LocalNoteImageStore(private val context: Context) : NoteImageStore {
    override suspend fun deleteImage(path: String) = withContext(Dispatchers.IO) {
        val file = File(path).canonicalFile
        if (file.parentFile == File(context.filesDir, "note_images").canonicalFile) file.delete()
        Unit
    }

    override suspend fun importImage(uri: String): String = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, "note_images").apply { mkdirs() }
        val target = File(directory, UUID.randomUUID().toString())
        try {
            context.contentResolver.openInputStream(Uri.parse(uri)).use { input ->
                requireNotNull(input) { "Файл недоступен" }
                target.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 30L * 1024 * 1024) { "Изображение больше 30 МБ" }
                        output.write(buffer, 0, count)
                    }
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(target.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Файл не является поддерживаемым изображением" }
            target.absolutePath
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }
}
