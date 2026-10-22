package com.obpiub.myapplication

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.obpiub.myapplication.data.local.AppDatabase
import com.obpiub.myapplication.data.repository.RoomNoteRepository
import com.obpiub.myapplication.data.repository.RoomTaskRepository
import com.obpiub.myapplication.domain.model.Note
import com.obpiub.myapplication.domain.model.Task
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalStorageTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private val databaseName = "local-storage-test.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(databaseName)
        database = openDatabase()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    private fun openDatabase() =
        Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()

    @Test
    fun notesAndTaskStatusSurviveReopeningDatabase() = runBlocking {
        val notes = RoomNoteRepository(database.noteDao())
        val tasks = RoomTaskRepository(database.taskDao())
        val noteId = notes.saveNote(Note(title = "Заметка", createdAt = 100))
        notes.saveNote(notes.getNote(noteId)!!.copy(text = "Новый текст"))
        val taskId = tasks.saveTask(Task(title = "Задача", createdAt = 200))
        tasks.setCompleted(taskId, true)

        database.close()
        database = openDatabase()

        val savedNote = RoomNoteRepository(database.noteDao()).getNote(noteId)!!
        assertEquals("Новый текст", savedNote.text)
        assertEquals(100L, savedNote.createdAt)
        val savedTask = RoomTaskRepository(database.taskDao()).observeTasks().first().single()
        assertEquals(taskId, savedTask.id)
        assertTrue(savedTask.isCompleted)
    }

    @Test
    fun noteFlowUpdatesAfterEditingAndDeleting() = runBlocking {
        val notes = RoomNoteRepository(database.noteDao())
        val id = notes.saveNote(Note(title = "До"))
        val changed = async {
            withTimeout(5_000) { notes.observeNotes().first { it.singleOrNull()?.title == "После" } }
        }
        notes.saveNote(notes.getNote(id)!!.copy(title = "После"))
        assertEquals("После", changed.await().single().title)
        val deleted = async {
            withTimeout(5_000) { notes.observeNotes().first { it.isEmpty() } }
        }
        notes.deleteNote(id)
        assertTrue(deleted.await().isEmpty())
    }

    @Test
    fun activeTasksComeFirstAndBlankTitlesAreRejected() = runBlocking {
        val tasks = RoomTaskRepository(database.taskDao())
        tasks.saveTask(Task(title = "Выполненная", isCompleted = true, createdAt = 300))
        tasks.saveTask(Task(title = "Старая", createdAt = 100))
        tasks.saveTask(Task(title = "Новая", createdAt = 200))
        assertEquals(
            listOf("Новая", "Старая", "Выполненная"),
            tasks.observeTasks().first().map { it.title },
        )
        try {
            tasks.saveTask(Task(title = "   "))
            fail("Пустая задача не должна сохраняться")
        } catch (_: IllegalArgumentException) {
            assertEquals(3, tasks.observeTasks().first().size)
        }
        val notes = RoomNoteRepository(database.noteDao())
        try {
            notes.saveNote(Note(title = " "))
            fail("Пустой заголовок не должен сохраняться")
        } catch (_: IllegalArgumentException) {
            assertTrue(notes.observeNotes().first().isEmpty())
        }
    }
}
