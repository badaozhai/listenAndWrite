package app.listenandwrite.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Listens in short turns and restarts after each result.
 * "下一个" / "好了" are decided by [app.listenandwrite.domain.VoiceMatch], not here.
 */
class VoiceCommandListener(
    context: Context,
    private val onText: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var running = false

    fun start() {
        if (running) return
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            onError("这台手机不能语音识别。点「下一个」按钮就行。")
            return
        }
        running = true
        val created = SpeechRecognizer.createSpeechRecognizer(appContext)
        created.setRecognitionListener(listener)
        recognizer = created
        listen()
    }

    fun destroy() {
        running = false
        main.removeCallbacksAndMessages(null)
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }

    private fun listen() {
        if (!running) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1600)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200)
        }
        try {
            recognizer?.startListening(intent)
        } catch (_: Exception) {
            running = false
            onError("麦克风打不开。点「下一个」按钮就行。")
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onError(error: Int) {
            if (!running) return
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                running = false
                onError("没有麦克风权限。点「下一个」按钮就行。")
                return
            }
            val delayMs = if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 450L else 250L
            main.postDelayed({ listen() }, delayMs)
        }

        override fun onResults(results: Bundle?) {
            dispatch(results)
            if (running) main.postDelayed({ listen() }, 180)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            dispatch(partialResults)
        }

        private fun dispatch(bundle: Bundle?) {
            val text = bundle
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?: return
            onText(text)
        }
    }
}
