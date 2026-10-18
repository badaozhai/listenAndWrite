package app.listenandwrite.ui

import android.app.Application
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.listenandwrite.ListenApp
import app.listenandwrite.data.AppSettings
import app.listenandwrite.data.WordEntity
import app.listenandwrite.domain.Lang
import app.listenandwrite.domain.LessonOptions
import app.listenandwrite.domain.LessonScript
import app.listenandwrite.domain.PlayMode
import app.listenandwrite.domain.VoiceMatch
import app.listenandwrite.domain.WordText
import app.listenandwrite.speech.Speaker
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

enum class Phase {
    Loading,
    Speaking,
    Writing,
    Listening,
    Paused,
    Empty,
}

data class SessionUi(
    val title: String = "",
    val index: Int = 0,
    val total: Int = 0,
    val phase: Phase = Phase.Loading,
    val mode: PlayMode = PlayMode.TIMER,
    val remainingSeconds: Int = 0,
    val totalSeconds: Int = 0,
    val listening: Boolean = false,
    val hint: String = "准备好了",
    val answer: String = "",
    val ttsWarning: String? = null,
    val voiceWarning: String? = null,
    val finished: Boolean = false,
    val hideAnswer: Boolean = false,
)

private enum class Cmd { NEXT, PREV, REPLAY, PAUSE, RESUME }

private enum class BreakReason { DONE, NEXT, PREV, PAUSE, REPLAY }

class SessionViewModel(app: Application) : AndroidViewModel(app) {
    private val listenApp = app as ListenApp
    private val practice = listenApp.practice
    private val settingsStore = listenApp.settings
    private val speaker = Speaker(app)
    private val audio = app.getSystemService(AudioManager::class.java)
    private val vibrator = app.getSystemService(Vibrator::class.java)
    private var tone: ToneGenerator? = null
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        .build()

    private val pending = ArrayDeque<Cmd>()
    private var settings = AppSettings()
    private var words: List<WordEntity> = emptyList()
    private var ended = false

    var ui by mutableStateOf(SessionUi())
        private set

    private val _finished = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val finished = _finished.asSharedFlow()

    init {
        viewModelScope.launch {
            settings = settingsStore.flow.first()
            words = practice.words
            ui = SessionUi(
                title = practice.title,
                total = words.size,
                mode = practice.mode,
                phase = if (words.isEmpty()) Phase.Empty else Phase.Loading,
                hint = if (words.isEmpty()) "这个词单是空的" else "准备好了",
                hideAnswer = practice.hideAnswer,
            )
            if (words.isEmpty()) return@launch
            audio?.requestAudioFocus(focusRequest)
            runSession()
        }
    }

    fun next() {
        if (ended || ui.phase == Phase.Empty) return
        ui = ui.copy(listening = false)
        speaker.stop()
        if (pending.lastOrNull() != Cmd.NEXT) pending.add(Cmd.NEXT)
    }

    fun previous() {
        if (ended || ui.phase == Phase.Empty) return
        ui = ui.copy(listening = false)
        speaker.stop()
        if (pending.lastOrNull() != Cmd.PREV) pending.add(Cmd.PREV)
    }

    fun replay() {
        if (ended || ui.phase == Phase.Empty || ui.phase == Phase.Paused) return
        speaker.stop()
        pending.add(Cmd.REPLAY)
    }

    fun pause() {
        if (ended || ui.phase == Phase.Paused || ui.phase == Phase.Empty) return
        ui = ui.copy(listening = false, phase = Phase.Paused, hint = "已暂停")
        speaker.stop()
        pending.add(Cmd.PAUSE)
    }

    fun resume() {
        if (ui.phase != Phase.Paused) return
        pending.add(Cmd.RESUME)
    }

    fun onHeard(text: String) {
        if (!ui.listening) return
        if (VoiceMatch.isAdvance(text)) next()
    }

    fun onVoiceError(message: String) {
        if (ui.voiceWarning == message) return
        ui = ui.copy(voiceWarning = message)
    }

    override fun onCleared() {
        speaker.shutdown()
        runCatching { tone?.release() }
        tone = null
        audio?.abandonAudioFocusRequest(focusRequest)
        super.onCleared()
    }

    private suspend fun runSession() {
        var index = 0
        while (index < words.size) {
            when (playOne(index)) {
                BreakReason.PREV -> index = (index - 1).coerceAtLeast(0)
                else -> {
                    buzz(longArrayOf(0, 28, 50, 36))
                    index++
                }
            }
        }
        if (ended) return
        ended = true
        ui = ui.copy(
            finished = true,
            listening = false,
            index = words.size,
            hint = "全部读完了",
        )
        _finished.tryEmit(practice.backRoute.ifBlank { "subjects" })
    }

    private suspend fun playOne(index: Int): BreakReason {
        val word = words[index]
        ui = ui.copy(index = index, total = words.size, answer = word.text, finished = false)
        var reason = speakWord(word, index)
        while (true) {
            when (reason) {
                BreakReason.NEXT -> return BreakReason.NEXT
                BreakReason.PREV -> return BreakReason.PREV
                BreakReason.REPLAY -> reason = speakWord(word, index)
                BreakReason.PAUSE -> {
                    reason = when (val paused = waitResume()) {
                        BreakReason.NEXT -> return BreakReason.NEXT
                        BreakReason.PREV -> return BreakReason.PREV
                        else -> speakWord(word, index)
                    }
                }
                BreakReason.DONE -> {
                    reason = if (practice.mode == PlayMode.TIMER) waitWriting(word) else waitVoice()
                    when (reason) {
                        BreakReason.DONE -> return BreakReason.DONE
                        BreakReason.NEXT -> return BreakReason.NEXT
                        BreakReason.PREV -> return BreakReason.PREV
                        BreakReason.REPLAY -> reason = speakWord(word, index)
                        BreakReason.PAUSE -> {
                            reason = when (val paused = waitResume()) {
                                BreakReason.NEXT -> return BreakReason.NEXT
                                BreakReason.PREV -> return BreakReason.PREV
                                else -> speakWord(word, index)
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun speakWord(word: WordEntity, index: Int): BreakReason {
        val lang = runCatching { Lang.valueOf(word.lang) }.getOrDefault(Lang.ZH)
        val place = LessonScript.placeOf(words.map { it.focus }, index)
        val lines = LessonScript.lines(
            text = word.text,
            lang = lang,
            meaning = word.meaning,
            contrast = word.contrast,
            place = place,
            options = LessonOptions(
                repeats = settings.repeats,
                explainChinese = settings.explainChinese,
                englishCue = practice.englishCue ?: settings.englishCue,
                spellEnglish = settings.spellEnglish,
            ),
        )
        for ((turn, line) in lines.withIndex()) {
            pullInterrupt()?.let { return it }
            ui = ui.copy(
                phase = Phase.Speaking,
                listening = false,
                hint = line.hint,
                remainingSeconds = 0,
            )
            val result = try {
                withTimeout(18_000) { speaker.speakAndWait(line.text, line.lang, settings.speechRate) }
            } catch (_: TimeoutCancellationException) {
                null
            }
            if (result?.languageMissing == true && ui.ttsWarning == null) {
                ui = ui.copy(ttsWarning = "系统里还没有可用的朗读语音。请安装中文和英文语音后再听。")
            }
            pullInterrupt()?.let { return it }
            if (turn != lines.lastIndex) delay(350)
        }
        return BreakReason.DONE
    }

    private suspend fun waitWriting(word: WordEntity): BreakReason {
        beep()
        val total = WordText.writingSeconds(
            word.text,
            settings.writingSeconds,
            settings.idiomSeconds,
            settings.secondsPerLetter,
            settings.englishMinSeconds,
        )
        val deadline = SystemClock.elapsedRealtime() + total * 1000L
        var lastSecond = -1
        while (true) {
            val left = deadline - SystemClock.elapsedRealtime()
            if (left <= 0) return BreakReason.DONE
            val seconds = ((left + 999) / 1000L).toInt()
            if (seconds != lastSecond) {
                lastSecond = seconds
                if (seconds in 1..3) buzz(longArrayOf(0, 24))
            }
            ui = ui.copy(
                phase = Phase.Writing,
                listening = false,
                remainingSeconds = seconds,
                totalSeconds = total,
                hint = "请写下来",
            )
            when (val cmd = waitCmd(minOf(100L, left))) {
                null -> Unit
                Cmd.NEXT -> return BreakReason.NEXT
                Cmd.PREV -> return BreakReason.PREV
                Cmd.REPLAY -> return BreakReason.REPLAY
                Cmd.PAUSE -> return BreakReason.PAUSE
                Cmd.RESUME -> Unit
            }
        }
    }

    private suspend fun waitVoice(): BreakReason {
        beep()
        ui = ui.copy(
            phase = Phase.Listening,
            listening = true,
            hint = "写好了就说「下一个」或「好了」",
            remainingSeconds = 0,
        )
        while (true) {
            when (waitCmd(null)) {
                Cmd.NEXT -> {
                    ui = ui.copy(listening = false)
                    return BreakReason.NEXT
                }
                Cmd.PREV -> {
                    ui = ui.copy(listening = false)
                    return BreakReason.PREV
                }
                Cmd.REPLAY -> return BreakReason.REPLAY
                Cmd.PAUSE -> return BreakReason.PAUSE
                Cmd.RESUME, null -> Unit
            }
        }
    }

    private suspend fun waitResume(): BreakReason {
        ui = ui.copy(phase = Phase.Paused, listening = false, hint = "已暂停")
        while (true) {
            when (waitCmd(null)) {
                Cmd.RESUME, Cmd.REPLAY -> return BreakReason.DONE
                Cmd.NEXT -> return BreakReason.NEXT
                Cmd.PREV -> return BreakReason.PREV
                Cmd.PAUSE, null -> Unit
            }
        }
    }

    private fun pullInterrupt(): BreakReason? {
        while (pending.isNotEmpty()) {
            when (pending.removeFirst()) {
                Cmd.NEXT -> {
                    while (pending.firstOrNull() == Cmd.NEXT) pending.removeFirst()
                    return BreakReason.NEXT
                }
                Cmd.PREV -> return BreakReason.PREV
                Cmd.PAUSE -> return BreakReason.PAUSE
                Cmd.REPLAY -> {
                    while (pending.firstOrNull() == Cmd.REPLAY) pending.removeFirst()
                    return BreakReason.REPLAY
                }
                Cmd.RESUME -> Unit
            }
        }
        return null
    }

    private suspend fun waitCmd(timeoutMs: Long?): Cmd? {
        val start = SystemClock.elapsedRealtime()
        while (true) {
            if (pending.isNotEmpty()) return pending.removeFirst()
            if (timeoutMs != null && SystemClock.elapsedRealtime() - start >= timeoutMs) return null
            delay(20)
        }
    }

    /** 读完后的一声，告诉孩子可以落笔，不用看屏幕。 */
    private fun beep() {
        val player = tone ?: runCatching {
            ToneGenerator(AudioManager.STREAM_MUSIC, 80)
        }.getOrNull()?.also { tone = it }
        runCatching { player?.startTone(ToneGenerator.TONE_PROP_BEEP, 140) }
    }

    private fun buzz(pattern: LongArray) {
        val effect = if (pattern.size == 1) {
            VibrationEffect.createOneShot(pattern[0], VibrationEffect.DEFAULT_AMPLITUDE)
        } else {
            VibrationEffect.createWaveform(pattern, -1)
        }
        runCatching { vibrator?.vibrate(effect) }
    }
}
