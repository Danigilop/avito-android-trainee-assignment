package com.obpiub.myapplication.domain.repository

interface NoteImageStore {
    suspend fun importImage(uri: String): String
    suspend fun deleteImage(path: String) {}
}
