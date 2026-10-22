package com.obpiub.myapplication

import androidx.lifecycle.ViewModelStore
import com.obpiub.myapplication.domain.repository.*
import com.obpiub.myapplication.presentation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceViewModelTest {
    private val store = ViewModelStore()
    private val recorder = TestRecorder()
    private val repository = TestVoiceRepository()
    private val results = mutableListOf<String>()

    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }
    private fun model(task: Boolean = false) = VoiceViewModel(recorder, repository, task) { results += it }
        .also { store.put("voice", it) }

    @Test fun noteReceivesRecognizedTextWithoutCallingGigaChat() = runTest {
        val model = model()
        model.start()
        model.start()
        runCurrent()
        assertEquals(VoicePhase.Recording, model.state.value.phase)
        model.finish()
        advanceUntilIdle()
        assertEquals(listOf("Купить хлеб"), results)
        assertEquals(1, recorder.starts)
        assertEquals(0, repository.formats)
        assertFalse(model.state.value.busy)
    }

    @Test fun taskIsFormattedAndSavedOnce() = runTest {
        val model = model(true)
        model.start()
        runCurrent()
        model.finish()
        model.finish()
        advanceUntilIdle()
        assertEquals(listOf("Купить хлеб завтра"), results)
        assertEquals(1, repository.formats)
    }

    @Test fun cancelReleasesRecordingWithoutSendingAudio() = runTest {
        val model = model()
        model.start()
        runCurrent()
        model.cancelRecording()
        advanceUntilIdle()
        assertTrue(recorder.released)
        assertEquals(0, repository.formats)
        assertTrue(results.isEmpty())
        assertFalse(model.state.value.busy)
    }

    @Test fun emptySpeechDoesNotCreateTaskAndAllowsAnotherAttempt() = runTest {
        recorder.text = " "
        val model = model(true)
        model.start()
        runCurrent()
        model.finish()
        advanceUntilIdle()
        assertNotNull(model.state.value.error)
        assertEquals(0, repository.formats)
        assertTrue(results.isEmpty())
        recorder.text = "Купить хлеб"
        model.start()
        runCurrent()
        model.finish()
        advanceUntilIdle()
        assertEquals(1, results.size)
    }

    @Test fun permissionRefusalDoesNotStartRecording() {
        val model = model()
        model.permissionDenied()
        assertEquals(0, recorder.starts)
        assertNotNull(model.state.value.error)
        assertFalse(model.state.value.busy)
    }

    @Test fun leavingDuringRecognitionCancelsSessionAndDoesNotCreateTask() = runTest {
        recorder.waitForNetwork = true
        val model = model(true)
        model.start()
        model.finish()
        runCurrent()
        assertEquals(VoicePhase.Recognizing, model.state.value.phase)
        model.cancelRecording()
        advanceUntilIdle()
        assertTrue(recorder.released)
        assertFalse(model.state.value.busy)
        assertTrue(results.isEmpty())
        assertEquals(0, repository.formats)
    }

    @Test fun serviceCanCompleteAutomaticallyWithoutFinishButton() = runTest {
        val model = model()
        model.start()
        recorder.stop() 
        advanceUntilIdle()
        assertEquals(listOf("Купить хлеб"), results)
        assertFalse(model.state.value.busy)
    }

    @Test fun clearingViewModelCancelsRecognitionWithoutDeliveringResult() = runTest {
        recorder.waitForNetwork = true
        val model = model()
        model.start()
        runCurrent()
        model.finish()
        runCurrent()
        assertEquals(VoicePhase.Recognizing, model.state.value.phase)
        store.clear()
        advanceUntilIdle()
        assertTrue(results.isEmpty())
    }
}

private class TestRecorder : SpeechInput {
    var starts = 0
    var released = false
    private var finished = CompletableDeferred<Unit>()
    var text = "Купить хлеб"
    var waitForNetwork = false
    override suspend fun recognize(onProcessing: () -> Unit): String {
        starts++
        finished = CompletableDeferred()
        try { finished.await(); onProcessing(); if (waitForNetwork) awaitCancellation(); return text } finally { released = true }
    }
    override fun stop() { finished.complete(Unit) }
}

private class TestVoiceRepository : VoiceRepository {
    var formats = 0

    override suspend fun createTaskTitle(text: String): String { formats++; return "Купить хлеб завтра" }
}
