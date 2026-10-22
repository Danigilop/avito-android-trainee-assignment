package com.obpiub.myapplication.data.repository

import com.obpiub.myapplication.data.local.NoteDao
import com.obpiub.myapplication.data.local.NoteEntity
import com.obpiub.myapplication.domain.model.Note
import com.obpiub.myapplication.domain.repository.NoteRepository
import kotlinx.coroutines.flow.map
import com.obpiub.myapplication.domain.repository.NoteImageStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class RoomNoteRepository(private val dao: NoteDao, private val images: NoteImageStore? = null) : NoteRepository {
    override fun observeNotes() = dao.observeAll().map { notes -> notes.map { it.toNote() } }

    override suspend fun getNote(id: Long) = dao.getById(id)?.toNote()

    override suspend fun saveNote(note: Note): Long {
        require(note.title.isNotBlank()) { "Заголовок заметки не должен быть пустым" }
        val entity = NoteEntity(note.id, note.title.trim(), note.text, note.imagePath, note.createdAt)
        if (note.id == 0L) return dao.insert(entity)
        val previousPath = dao.getById(note.id)?.imagePath
        dao.update(entity)
        if (previousPath != note.imagePath) deleteUnusedImage(previousPath)
        return note.id
    }

    override suspend fun deleteNote(id: Long) {
        val path = dao.getById(id)?.imagePath
        dao.delete(id)
        deleteUnusedImage(path)
    }

    private suspend fun deleteUnusedImage(path: String?) {
        if (path == null || images == null) return
        withContext(NonCancellable) {
            runCatching { if (!dao.isImageUsed(path)) images.deleteImage(path) }
        }
    }

    private fun NoteEntity.toNote() = Note(id, title, text, imagePath, createdAt)
}
