package com.obpiub.myapplication

import android.content.Context
import androidx.room.Room
import com.obpiub.myapplication.data.local.AppDatabase
import com.obpiub.myapplication.data.repository.RoomNoteRepository
import com.obpiub.myapplication.data.repository.RoomTaskRepository
import com.obpiub.myapplication.domain.repository.NoteRepository
import com.obpiub.myapplication.domain.repository.TaskRepository
import com.obpiub.myapplication.data.repository.PreferencesSettingsRepository
import com.obpiub.myapplication.data.remote.GigaChatClient
import com.obpiub.myapplication.domain.repository.SettingsRepository
import com.obpiub.myapplication.domain.repository.BalanceRepository

class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext
    fun createSpeechInput() = com.obpiub.myapplication.data.local.AndroidSpeechInput(applicationContext)
    val voiceRepository by lazy { com.obpiub.myapplication.data.remote.VoiceClient.create() }
    val noteImageStore = com.obpiub.myapplication.data.local.LocalNoteImageStore(context.applicationContext)
    private val database = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "notes_and_tasks.db",
    ).build()

    val noteRepository: NoteRepository = RoomNoteRepository(database.noteDao(), noteImageStore)
    val taskRepository: TaskRepository = RoomTaskRepository(database.taskDao())
    val settingsRepository: SettingsRepository = PreferencesSettingsRepository(
        context.applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE),
    )
    val balanceRepository: BalanceRepository by lazy { GigaChatClient.createBalanceRepository() }
}
