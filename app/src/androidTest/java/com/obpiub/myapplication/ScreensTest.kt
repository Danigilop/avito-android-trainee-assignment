package com.obpiub.myapplication

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.obpiub.myapplication.data.local.AppDatabase
import com.obpiub.myapplication.data.repository.RoomNoteRepository
import com.obpiub.myapplication.data.repository.RoomTaskRepository
import com.obpiub.myapplication.domain.model.Note
import com.obpiub.myapplication.presentation.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ScreensTest {
    @Test
    fun voiceResultAppendsToNoteAndSaveIsBlockedWhileRecording() {
        val repository = RoomNoteRepository(database.noteDao())
        val finished = kotlinx.coroutines.CompletableDeferred<Unit>()
        val recorder = object : com.obpiub.myapplication.domain.repository.SpeechInput {
            override suspend fun recognize(onProcessing: () -> Unit): String { finished.await(); onProcessing(); return "Добавлено голосом" }
            override fun stop() { finished.complete(Unit) }
        }
        val api = object : com.obpiub.myapplication.domain.repository.VoiceRepository {
            override suspend fun createTaskTitle(text: String): String = error("Не вызывается для заметки")
        }
        lateinit var editor: NoteEditorViewModel
        lateinit var voice: VoiceViewModel
        compose.runOnUiThread {
            editor = NoteEditorViewModel(repository, SavedStateHandle())
            voice = VoiceViewModel(recorder, api, false) { text ->
                editor.changeText(editor.state.value.text + "\n" + text)
            }
            store.put("editor", editor)
            store.put("voice", voice)
        }
        compose.setContent {
            val state by editor.state.collectAsState()
            val voiceState by voice.state.collectAsState()
            MaterialTheme { NoteEditorScreen(state, editor, voice, voiceState) {} }
        }
        compose.waitUntil(5_000) { editor.state.value.canEdit }
        compose.runOnIdle {
            editor.changeTitle("Голосовая заметка")
            editor.changeText("Исходный текст")
            voice.start()
        }
        compose.onNodeWithText("Сохранить").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Завершить запись").performScrollTo().performClick()
        compose.waitUntil(5_000) { !voice.state.value.busy }
        compose.onNodeWithText("Сохранить").performScrollTo().performClick()
        compose.waitUntil(5_000) { editor.state.value.saved }
        assertEquals("Исходный текст\nДобавлено голосом", runBlocking { repository.observeNotes().first().single().text })
    }

    @get:Rule val compose = createComposeRule()
    private lateinit var database: AppDatabase
    private val store = ViewModelStore()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        compose.runOnUiThread { store.clear() }
        database.close()
    }

    @Test
    fun deletionModeDisablesOpeningAndDeletesNote() {
        val repository = RoomNoteRepository(database.noteDao())
        runBlocking { repository.saveNote(Note(title = "Проверить удаление")) }
        lateinit var model: NotesViewModel
        compose.runOnUiThread {
            model = NotesViewModel(repository, SavedStateHandle())
            store.put("notes", model)
        }
        compose.setContent {
            val state by model.state.collectAsState()
            MaterialTheme { NotesScreen(state, model) { fail("Открытие в режиме удаления запрещено") } }
        }
        compose.waitUntil(5_000) { !model.state.value.isLoading }
        compose.onNodeWithText("Удаление").performClick()
        compose.onNodeWithText("Проверить удаление").assertIsNotEnabled()
        compose.onNodeWithText("Удалить").performClick()
        compose.waitUntil(5_000) { model.state.value.notes.isEmpty() }
        compose.onNodeWithText("Заметок пока нет").assertIsDisplayed()
    }

    @Test
    fun taskIsCreatedInlineAndCheckboxPersistsStatus() {
        val repository = RoomTaskRepository(database.taskDao())
        lateinit var model: TasksViewModel
        compose.runOnUiThread {
            model = TasksViewModel(repository, SavedStateHandle())
            store.put("tasks", model)
        }
        compose.setContent {
            val state by model.state.collectAsState()
            MaterialTheme { TasksScreen(state, model) }
        }
        compose.waitUntil(5_000) { !model.state.value.isLoading }
        compose.onNodeWithText("Создать").performClick()
        compose.onNodeWithText("Текстовый ввод").performClick()
        compose.onNodeWithText("Название задачи").performTextInput("Прочитать ТЗ")
        compose.onNodeWithText("Название задачи").performImeAction()
        compose.waitUntil(5_000) { model.state.value.tasks.size == 1 && !model.state.value.isSaving }
        compose.onNodeWithText("Прочитать ТЗ").assertIsDisplayed()
        compose.onNode(isToggleable()).performClick()
        compose.waitUntil(5_000) { model.state.value.tasks.single().isCompleted }
        compose.onNodeWithText("Выполнена").assertIsDisplayed()
        assertTrue(runBlocking { repository.observeTasks().first().single().isCompleted })
    }

    @Test
    fun editorRejectsBlankTitleThenSavesNoteWithEmptyText() {
        val repository = RoomNoteRepository(database.noteDao())
        lateinit var model: NoteEditorViewModel
        var returned = false
        compose.runOnUiThread {
            model = NoteEditorViewModel(repository, SavedStateHandle())
            store.put("editor", model)
        }
        compose.setContent {
            val state by model.state.collectAsState()
            MaterialTheme { NoteEditorScreen(state, model) { returned = true } }
        }
        compose.waitUntil(5_000) { model.state.value.canEdit }
        compose.onNodeWithText("Сохранить").performScrollTo().performClick()
        compose.onNodeWithText("Введите заголовок заметки").assertExists()
        compose.onNodeWithText("Заголовок").performScrollTo().performTextInput("Новая заметка")
        compose.onNodeWithText("Сохранить").performScrollTo().performClick()
        compose.waitUntil(5_000) { model.state.value.saved }
        compose.runOnIdle { assertTrue(returned) }
        val saved = runBlocking { repository.observeNotes().first().single() }
        assertEquals("Новая заметка", saved.title)
        assertEquals("", saved.text)
    }
}
