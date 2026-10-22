package com.obpiub.myapplication

import android.Manifest
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.obpiub.myapplication.data.local.AndroidSpeechInput
import com.obpiub.myapplication.domain.repository.VoiceException
import kotlinx.coroutines.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Before

class LiveSpeechTest {
    @Before fun requireLiveMode() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveSpeech") == "true")
    }
    @Test fun realRecognitionServiceFinishesOrReportsSilence() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
        ActivityScenario.launch(MainActivity::class.java).use {
            val input = AndroidSpeechInput(context)
            val result = async { runCatching { input.recognize {} } }
            delay(4_000)
            if (!result.isCompleted) withContext(Dispatchers.Main) { input.stop() }
            val outcome = withTimeout(65_000) { result.await() }
            val error = outcome.exceptionOrNull()

            if (error != null && !(error is VoiceException && error.message == "Речь не распознана. Повторите ввод")) throw error
        }
        Unit
    }
}
