package app.listenandwrite.speech

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import app.listenandwrite.domain.Lang
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

data class SpeakResult(
    val spoken: Boolean,
    val languageMissing: Boolean,
)

class Speaker(context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val appContext = context.applicationContext
    private var ready = false
    private var failed = false
    private val waiting = ArrayDeque<Job>()
    private var callback: ((SpeakResult) -> Unit)? = null
    private var markMissing = false
    private var seq = 0

    private val tts: TextToSpeech = TextToSpeech(appContext) { status ->
        main.post { onEngineReady(status) }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                deliver(SpeakResult(spoken = true, languageMissing = markMissing))
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                deliver(SpeakResult(spoken = false, languageMissing = markMissing))
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                deliver(SpeakResult(spoken = false, languageMissing = markMissing))
            }
        })
    }

    suspend fun speakAndWait(text: String, lang: Lang, rate: Float): SpeakResult =
        suspendCancellableCoroutine { cont ->
            val once = AtomicBoolean(false)
            fun finish(result: SpeakResult) {
                if (once.compareAndSet(false, true) && cont.isActive) {
                    cont.resume(result)
                }
            }
            speak(text, lang, rate, ::finish)
            cont.invokeOnCancellation { stop() }
        }

    fun stop() {
        main.post {
            runCatching { tts.stop() }
            deliver(SpeakResult(spoken = false, languageMissing = false))
        }
    }

    fun shutdown() {
        callback = null
        waiting.clear()
        runCatching {
            tts.stop()
            tts.shutdown()
        }
    }

    private fun speak(text: String, lang: Lang, rate: Float, done: (SpeakResult) -> Unit) {
        main.post {
            val job = Job(text, lang, rate, done)
            if (failed) {
                done(SpeakResult(spoken = false, languageMissing = true))
                return@post
            }
            if (!ready) {
                waiting.add(job)
                return@post
            }
            start(job)
        }
    }

    private fun onEngineReady(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            failed = true
            val jobs = waiting.toList()
            waiting.clear()
            jobs.forEach { it.done(SpeakResult(spoken = false, languageMissing = true)) }
            return
        }
        ready = true
        val media = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        tts.setAudioAttributes(media)
        val jobs = waiting.toList()
        waiting.clear()
        jobs.forEach(::start)
    }

    private fun start(job: Job) {
        val locale = if (job.lang == Lang.ZH) Locale.SIMPLIFIED_CHINESE else Locale.US
        val langResult = tts.setLanguage(locale)
        markMissing = langResult == TextToSpeech.LANG_MISSING_DATA ||
            langResult == TextToSpeech.LANG_NOT_SUPPORTED
        tts.setSpeechRate(job.rate.coerceIn(0.5f, 1.3f))
        val id = "w${++seq}"
        callback = job.done
        val code = tts.speak(job.text, TextToSpeech.QUEUE_FLUSH, Bundle(), id)
        if (code == TextToSpeech.ERROR) {
            deliver(SpeakResult(spoken = false, languageMissing = markMissing))
        }
    }

    private fun deliver(result: SpeakResult) {
        main.post {
            val done = callback ?: return@post
            callback = null
            done(result)
        }
    }

    private data class Job(
        val text: String,
        val lang: Lang,
        val rate: Float,
        val done: (SpeakResult) -> Unit,
    )
}
