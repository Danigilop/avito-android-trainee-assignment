package com.obpiub.myapplication

import android.Manifest
import android.content.ComponentName
import androidx.test.platform.app.InstrumentationRegistry
import com.obpiub.myapplication.data.local.AndroidSpeechInput
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import androidx.test.core.app.ActivityScenario

class AndroidSpeechInputTest {
    @Test fun androidServiceCallbacksReturnTextAndSessionCanBeReused() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
        val input = AndroidSpeechInput(context, ComponentName(instrumentation.context, TestRecognitionService::class.java))
        ActivityScenario.launch(MainActivity::class.java).use {
        repeat(2) {
            var processing = false
            val result = withTimeout(10_000) { input.recognize { processing = true } }
            assertEquals("Проверка системного распознавания", result)
            assertTrue(processing)
        }
        }
        Unit
    }
}
