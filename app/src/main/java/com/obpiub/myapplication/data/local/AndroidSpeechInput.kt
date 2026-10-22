package com.obpiub.myapplication.data.local

import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.obpiub.myapplication.domain.repository.SpeechInput
import com.obpiub.myapplication.domain.repository.VoiceException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AndroidSpeechInput(context: Context, private val service: ComponentName? = null) : SpeechInput {
    private val context = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var onProcessing: (() -> Unit)? = null

    override suspend fun recognize(onProcessing: () -> Unit): String = withContext(Dispatchers.Main.immediate) {
        if (service == null && !SpeechRecognizer.isRecognitionAvailable(context)) {
            throw VoiceException("На устройстве нет службы распознавания речи. Установите или включите её в настройках Android")
        }
        check(recognizer == null)
        val current = if (service == null) SpeechRecognizer.createSpeechRecognizer(context)
            else SpeechRecognizer.createSpeechRecognizer(context, service)
        recognizer = current
        this@AndroidSpeechInput.onProcessing = onProcessing
        try {
            withTimeoutOrNull(60_000) {
                suspendCancellableCoroutine { continuation ->
                    current.setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {}
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {
                            if (continuation.isActive) onProcessing()
                        }
                        override fun onError(error: Int) {
                            if (continuation.isActive) continuation.resumeWithException(VoiceException(speechError(error)))
                        }
                        override fun onResults(results: Bundle?) {
                            if (!continuation.isActive) return
                            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()?.trim()
                            if (text.isNullOrEmpty()) continuation.resumeWithException(VoiceException("Речь не распознана. Повторите ввод"))
                            else continuation.resume(text)
                        }
                        override fun onPartialResults(partialResults: Bundle?) {}
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                    current.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                    })
                }
            } ?: throw VoiceException("Служба распознавания не ответила вовремя. Повторите ввод")
        } finally {
            this@AndroidSpeechInput.onProcessing = null
            recognizer = null
            try { current.cancel() } finally { current.destroy() }
        }
    }

    override fun stop() {
        recognizer?.stopListening()
        onProcessing?.invoke()
    }
}

internal fun speechError(code: Int): String = when (code) {
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Нет доступа к микрофону. Разрешите его в настройках приложения"
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Ошибка сети при распознавании. Проверьте интернет"
    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Речь не распознана. Повторите ввод"
    SpeechRecognizer.ERROR_AUDIO -> "Микрофон недоступен. Закройте другие приложения записи и повторите ввод"
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Служба распознавания занята. Попробуйте ещё раз"
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
        "Русский язык недоступен. Проверьте языки службы распознавания в настройках Android"
    else -> "Ошибка службы распознавания ($code). Попробуйте ещё раз"
}
