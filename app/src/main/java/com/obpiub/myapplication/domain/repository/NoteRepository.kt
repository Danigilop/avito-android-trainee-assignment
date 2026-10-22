package com.obpiub.myapplication.domain.repository

import com.obpiub.myapplication.domain.model.Note
import kotlinx.coroutines.flow.Flow

interface NoteRepository {
    fun observeNotes(): Flow<List<Note>>
    suspend fun getNote(id: Long): Note?
    suspend fun saveNote(note: Note): Long
    suspend fun deleteNote(id: Long)
}
