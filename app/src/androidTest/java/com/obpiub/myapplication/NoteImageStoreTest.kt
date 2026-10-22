package com.obpiub.myapplication

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.obpiub.myapplication.data.local.LocalNoteImageStore
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class NoteImageStoreTest {
    @Test
    fun deletingImageCannotRemoveFileOutsideOwnedDirectory() = kotlinx.coroutines.runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val external = File.createTempFile("keep", ".txt", context.cacheDir)
        try {
            LocalNoteImageStore(context).deleteImage(external.path)
            assertTrue(external.exists())
        } finally { external.delete() }
        Unit
    }

    @Test
    fun savedImageIsDeletedOnlyAfterLastReferenceIsRemoved() = kotlinx.coroutines.runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.filesDir, "note_images").apply { mkdirs() }
        val image = File.createTempFile("owned", ".png", directory)
        val replacement = File.createTempFile("replacement", ".png", directory)
        val database = androidx.room.Room.inMemoryDatabaseBuilder(context,
            com.obpiub.myapplication.data.local.AppDatabase::class.java).build()
        val repository = com.obpiub.myapplication.data.repository.RoomNoteRepository(
            database.noteDao(), LocalNoteImageStore(context))
        try {
            val first = repository.saveNote(com.obpiub.myapplication.domain.model.Note(title = "Первая", imagePath = image.path))
            val second = repository.saveNote(com.obpiub.myapplication.domain.model.Note(title = "Вторая", imagePath = image.path))
            repository.saveNote(repository.getNote(first)!!.copy(imagePath = replacement.path))
            assertTrue(image.exists())
            assertTrue(replacement.exists())
            repository.deleteNote(second)
            assertFalse(image.exists())
            repository.deleteNote(first)
            assertFalse(replacement.exists())
        } finally { database.close(); image.delete(); replacement.delete() }
        Unit
    }

    @Test
    fun importedImageSurvivesSourceDeletionAndNewStore() = kotlinx.coroutines.runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File.createTempFile("image_test", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val path = LocalNoteImageStore(context).importImage(Uri.fromFile(source).toString())
        val imported = File(path)
        try {
            assertTrue(source.delete())
            assertTrue(imported.exists())
            val second = LocalNoteImageStore(context).importImage(Uri.fromFile(imported).toString())
            assertTrue(File(second).readBytes().contentEquals(imported.readBytes()))
            File(second).delete()
        } finally { source.delete(); imported.delete() }
        Unit
    }

    @Test
    fun invalidImageLeavesNoPartialFile() = kotlinx.coroutines.runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.filesDir, "note_images")
        val before = directory.list()?.toSet().orEmpty()
        val source = File.createTempFile("invalid_image", ".txt", context.cacheDir)
        try {
            source.writeText("Not an image")
            val result = runCatching { LocalNoteImageStore(context).importImage(Uri.fromFile(source).toString()) }
            assertTrue(result.isFailure)
            assertEquals(before, directory.list()?.toSet().orEmpty())
        } finally { source.delete() }
        Unit
    }
}
