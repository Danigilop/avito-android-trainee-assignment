package com.obpiub.myapplication.domain.repository

interface VoiceRepository {
    suspend fun createTaskTitle(text: String): String
}

interface SpeechInput {

    suspend fun recognize(onProcessing: () -> Unit): String
    fun stop()
}

class VoiceException(message: String) : Exception(message)
