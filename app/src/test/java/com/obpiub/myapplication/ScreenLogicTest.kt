package com.obpiub.myapplication

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.obpiub.myapplication.domain.model.Note
import com.obpiub.myapplication.domain.model.Task
import com.obpiub.myapplication.domain.repository.NoteRepository
import com.obpiub.myapplication.domain.repository.TaskRepository
import com.obpiub.myapplication.presentation.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScreenLogicTest {
    @Test
    fun replacingAndDiscardingDraftDeletesOnlyNewImages() = runTest {
        val repository = TestNotes()
        repository.notes.value = listOf(Note(id = 1, title = "Фото", imagePath = "saved"))
        val deleted = mutableListOf<String>()
        val images = object : com.obpiub.myapplication.domain.repository.NoteImageStore {
            override suspend fun importImage(uri: String) = uri
            override suspend fun deleteImage(path: String) { deleted += path }
        }
        val editor = keep(NoteEditorViewModel(repository, SavedStateHandle(mapOf("noteId" to 1L)), images))
        runCurrent()
        editor.importImage("draft1")
        advanceUntilIdle()
        assertTrue(deleted.isEmpty())
        editor.importImage("draft2")
        advanceUntilIdle()
        assertEquals(listOf("draft1"), deleted)
        var returned = false
        editor.discard { returned = true }
        advanceUntilIdle()
        assertTrue(returned)
        assertEquals(listOf("draft1", "draft2"), deleted)
        assertEquals("saved", repository.notes.value.single().imagePath)
    }

    @Test
    fun imageImportFailureKeepsPreviousImageAndRemovalSurvivesRestoration() = runTest {
        val repository = TestNotes()
        repository.notes.value = listOf(Note(id = 1, title = "Фото", imagePath = "original"))
        val handle = SavedStateHandle(mapOf("noteId" to 1L))
        val images = object : com.obpiub.myapplication.domain.repository.NoteImageStore {
            override suspend fun importImage(uri: String): String = error("Нет доступа")
        }
        val editor = keep(NoteEditorViewModel(repository, handle, images))
        runCurrent()
        editor.importImage("content://missing")
        runCurrent()
        assertEquals("original", editor.state.value.imagePath)
        assertNotNull(editor.state.value.imageError)
        editor.removeImage()
        val restored = keep(NoteEditorViewModel(repository, handle, images))
        runCurrent()
        assertNull(restored.state.value.imagePath)
        restored.save()
        runCurrent()
        assertNull(repository.notes.value.single().imagePath)
    }

    @Test
    fun savingWaitsForImageImportAndPersistsImportedPath() = runTest {
        val repository = TestNotes()
        val images = object : com.obpiub.myapplication.domain.repository.NoteImageStore {
            override suspend fun importImage(uri: String): String {
                kotlinx.coroutines.delay(100)
                return "internal/image.jpg"
            }
        }
        val editor = keep(NoteEditorViewModel(repository, SavedStateHandle(), images))
        runCurrent()
        editor.changeTitle("Фото")
        editor.importImage("content://image")
        editor.save()
        assertEquals(0, repository.saves)
        advanceUntilIdle()
        editor.save()
        runCurrent()
        assertEquals("internal/image.jpg", repository.notes.value.single().imagePath)
    }

    private val store = ViewModelStore()

    @Before
    fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun <T : ViewModel> keep(viewModel: T): T {
        store.put(viewModel.javaClass.name, viewModel)
        return viewModel
    }

    @Test
    fun searchWaitsForConfirmationAndEmptyConfirmedQueryRestoresList() = runTest {
        val repository = TestNotes()
        repository.notes.value = listOf(Note(id = 1, title = "Авито"), Note(id = 2, title = "Учёба"))
        val model = keep(NotesViewModel(repository, SavedStateHandle()))
        runCurrent()
        model.changeSearch("АВИ")
        assertEquals(2, model.state.value.notes.size)
        model.search()
        assertEquals(listOf(1L), model.state.value.notes.map { it.id })
        model.changeSearch("")
        assertEquals(1, model.state.value.notes.size)
        model.search()
        assertEquals(2, model.state.value.notes.size)
    }

    @Test
    fun completedTasksRemainBelowActiveInBothSortOrdersAndSearchResults() {
        val tasks = listOf(
            Task(id = 1, title = "Дом: старое", createdAt = 10),
            Task(id = 2, title = "Дом: новое", createdAt = 20),
            Task(id = 3, title = "Дом: готово", isCompleted = true, createdAt = 30),
            Task(id = 4, title = "Работа", createdAt = 40),
        )
        assertEquals(listOf(2L, 1L, 3L),
            selectTasks(tasks, "дом", SortOrder.NewestFirst, TaskFilter.All).map { it.id })
        assertEquals(listOf(1L, 2L, 3L),
            selectTasks(tasks, "дом", SortOrder.OldestFirst, TaskFilter.All).map { it.id })
        assertEquals(listOf(3L),
            selectTasks(tasks, "", SortOrder.NewestFirst, TaskFilter.Completed).map { it.id })
    }

    @Test
    fun deletingRequiresDeleteModeAndUpdatesVisibleResults() = runTest {
        val repository = TestNotes()
        repository.notes.value = listOf(Note(id = 1, title = "Заметка"))
        val model = keep(NotesViewModel(repository, SavedStateHandle()))
        runCurrent()
        model.delete(1)
        runCurrent()
        assertEquals(1, repository.notes.value.size)
        model.toggleDeleteMode()
        model.delete(1)
        runCurrent()
        assertTrue(model.state.value.notes.isEmpty())
    }

    @Test
    fun editingKeepsCreationDateAndAllowsEmptyText() = runTest {
        val repository = TestNotes()
        repository.notes.value = listOf(Note(id = 1, title = "До", text = "Текст", createdAt = 100))
        val model = keep(NoteEditorViewModel(repository, SavedStateHandle(mapOf("noteId" to 1L))))
        runCurrent()
        assertEquals("До", model.state.value.title)
        model.changeTitle("После")
        model.changeText("")
        model.save()
        model.save()
        runCurrent()
        assertEquals(1, repository.saves)
        assertEquals(Note(id = 1, title = "После", text = "", createdAt = 100), repository.notes.value.single())
        assertTrue(model.state.value.saved)
    }

    @Test
    fun noteSaveErrorKeepsDraftAndCanBeRetried() = runTest {
        val repository = TestNotes()
        repository.failSave = true
        val model = keep(NoteEditorViewModel(repository, SavedStateHandle()))
        runCurrent()
        model.changeTitle("Черновик")
        model.changeText("Не терять")
        model.save()
        runCurrent()
        assertFalse(model.state.value.saved)
        assertEquals("Не терять", model.state.value.text)
        assertNotNull(model.state.value.error)
        repository.failSave = false
        model.save()
        runCurrent()
        assertTrue(model.state.value.saved)
    }

    @Test
    fun missingNoteCannotBeSavedAsNew() = runTest {
        val repository = TestNotes()
        val model = keep(NoteEditorViewModel(repository, SavedStateHandle(mapOf("noteId" to 99L))))
        runCurrent()
        assertFalse(model.state.value.canEdit)
        assertNotNull(model.state.value.error)
        model.save()
        runCurrent()
        assertEquals(0, repository.saves)
    }

    @Test
    fun blankTitlesAreRejectedBeforeRepositoryCall() = runTest {
        val notes = TestNotes()
        val editor = keep(NoteEditorViewModel(notes, SavedStateHandle()))
        val tasks = TestTasks()
        val model = keep(TasksViewModel(tasks, SavedStateHandle()))
        runCurrent()
        editor.changeTitle("  ")
        editor.save()
        model.startCreating()
        model.changeDraft("  ")
        model.saveTask()
        runCurrent()
        assertNotNull(editor.state.value.titleError)
        assertNotNull(model.state.value.draftError)
        assertEquals(0, notes.saves)
        assertTrue(tasks.tasks.value.isEmpty())
    }

    @Test
    fun taskErrorKeepsDraftAndRetryCreatesOnlyOneTask() = runTest {
        val repository = TestTasks()
        repository.failSave = true
        val model = keep(TasksViewModel(repository, SavedStateHandle()))
        runCurrent()
        model.startCreating()
        model.changeDraft("Купить хлеб")
        model.saveTask()
        runCurrent()
        assertTrue(model.state.value.isCreating)
        assertEquals("Купить хлеб", model.state.value.draft)
        assertNotNull(model.state.value.error)
        repository.failSave = false
        model.saveTask()
        model.saveTask()
        runCurrent()
        assertEquals(1, model.state.value.tasks.size)
        assertFalse(model.state.value.isCreating)
    }

    @Test
    fun editorRestoresUnsavedFieldsInsteadOfOverwritingWithDatabase() = runTest {
        val repository = TestNotes()
        repository.notes.value = listOf(Note(id = 1, title = "В базе", text = "В базе"))
        val handle = SavedStateHandle(mapOf("noteId" to 1L, "title" to "Черновик", "text" to ""))
        val editor = keep(NoteEditorViewModel(repository, handle))
        runCurrent()
        assertEquals("Черновик", editor.state.value.title)
        assertEquals("", editor.state.value.text)
    }
}

private class TestNotes : NoteRepository {
    val notes = MutableStateFlow<List<Note>>(emptyList())
    var saves = 0
    var failSave = false
    override fun observeNotes() = notes
    override suspend fun getNote(id: Long) = notes.value.find { it.id == id }
    override suspend fun saveNote(note: Note): Long {
        if (failSave) error("Ошибка записи")
        saves++
        val id = note.id.takeIf { it != 0L } ?: (notes.value.size + 1L)
        notes.value = notes.value.filterNot { it.id == id } + note.copy(id = id)
        return id
    }
    override suspend fun deleteNote(id: Long) { notes.value = notes.value.filterNot { it.id == id } }
}

private class TestTasks : TaskRepository {
    val tasks = MutableStateFlow<List<Task>>(emptyList())
    var failSave = false
    override fun observeTasks() = tasks
    override suspend fun saveTask(task: Task): Long {
        if (failSave) error("Ошибка записи")
        val id = tasks.value.size + 1L
        tasks.value = tasks.value + task.copy(id = id)
        return id
    }
    override suspend fun setCompleted(id: Long, completed: Boolean) {
        tasks.value = tasks.value.map { if (it.id == id) it.copy(isCompleted = completed) else it }
    }
}
