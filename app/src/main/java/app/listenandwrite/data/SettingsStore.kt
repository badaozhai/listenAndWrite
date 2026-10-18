package app.listenandwrite.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import app.listenandwrite.domain.EnglishCue
import app.listenandwrite.domain.LastPlay
import app.listenandwrite.domain.PlayMode
import app.listenandwrite.domain.decodeUnits
import app.listenandwrite.domain.encodeUnits
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val writingSeconds: Int = 5,
    val idiomSeconds: Int = 10,
    val repeats: Int = 2,
    val speechRate: Float = 0.85f,
    val shuffle: Boolean = false,
    val spellEnglish: Boolean = false,
    val explainChinese: Boolean = true,
    val englishCue: EnglishCue = EnglishCue.BOTH,
    val secondsPerLetter: Int = 1,
    val englishMinSeconds: Int = 5,
    val lastChinese: LastPlay = LastPlay(),
    val lastRead: LastPlay = LastPlay(),
    val lastDrill: LastPlay = LastPlay(cue = EnglishCue.CHINESE),
)

class SettingsStore(private val context: Context) {
    private val writingKey = intPreferencesKey("writing_seconds")
    private val idiomKey = intPreferencesKey("idiom_seconds")
    private val repeatsKey = intPreferencesKey("repeats")
    private val rateKey = floatPreferencesKey("speech_rate")
    private val shuffleKey = booleanPreferencesKey("shuffle")
    private val spellKey = booleanPreferencesKey("spell_english")
    private val explainChineseKey = booleanPreferencesKey("explain_chinese")
    private val explainEnglishKey = booleanPreferencesKey("explain_english")
    private val englishCueKey = stringPreferencesKey("english_cue")
    private val perLetterKey = intPreferencesKey("seconds_per_letter")
    private val englishMinKey = intPreferencesKey("english_min_seconds")
    private val chineseUnitsKey = stringPreferencesKey("last_chinese_units")
    private val chineseRandomKey = booleanPreferencesKey("last_chinese_random")
    private val chineseModeKey = stringPreferencesKey("last_chinese_mode")
    private val readUnitsKey = stringPreferencesKey("last_read_units")
    private val readRandomKey = booleanPreferencesKey("last_read_random")
    private val readModeKey = stringPreferencesKey("last_read_mode")
    private val readCueKey = stringPreferencesKey("last_read_cue")
    private val drillUnitsKey = stringPreferencesKey("last_drill_units")
    private val drillRandomKey = booleanPreferencesKey("last_drill_random")
    private val drillModeKey = stringPreferencesKey("last_drill_mode")
    private val drillCueKey = stringPreferencesKey("last_drill_cue")

    val flow: Flow<AppSettings> = context.dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs -> prefs.toSettings() }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[writingKey] = next.writingSeconds.coerceIn(3, 20)
            prefs[idiomKey] = next.idiomSeconds.coerceIn(next.writingSeconds.coerceIn(3, 20), 40)
            prefs[repeatsKey] = next.repeats.coerceIn(1, 3)
            prefs[rateKey] = next.speechRate.coerceIn(0.6f, 1.2f)
            prefs[shuffleKey] = next.shuffle
            prefs[spellKey] = next.spellEnglish
            prefs[explainChineseKey] = next.explainChinese
            prefs[explainEnglishKey] = next.englishCue != EnglishCue.ENGLISH
            prefs[englishCueKey] = next.englishCue.name
            prefs[perLetterKey] = next.secondsPerLetter.coerceIn(1, 5)
            prefs[englishMinKey] = next.englishMinSeconds.coerceIn(3, 20)
            prefs.putPlay(next.lastChinese, chineseUnitsKey, chineseRandomKey, chineseModeKey, cueKey = null)
            prefs.putPlay(next.lastRead, readUnitsKey, readRandomKey, readModeKey, readCueKey)
            prefs.putPlay(next.lastDrill, drillUnitsKey, drillRandomKey, drillModeKey, drillCueKey)
        }
    }

    private fun Preferences.toSettings(): AppSettings {
        val writing = this[writingKey] ?: 5
        val idiom = this[idiomKey] ?: 10
        return AppSettings(
            writingSeconds = writing.coerceIn(3, 20),
            idiomSeconds = idiom.coerceAtLeast(writing).coerceIn(3, 40),
            repeats = (this[repeatsKey] ?: 2).coerceIn(1, 3),
            speechRate = this[rateKey] ?: 0.85f,
            shuffle = this[shuffleKey] ?: false,
            spellEnglish = this[spellKey] ?: false,
            explainChinese = this[explainChineseKey] ?: true,
            englishCue = this[englishCueKey]?.let { runCatching { EnglishCue.valueOf(it) }.getOrNull() }
                ?: if (this[explainEnglishKey] == false) EnglishCue.ENGLISH else EnglishCue.BOTH,
            secondsPerLetter = (this[perLetterKey] ?: 1).coerceIn(1, 5),
            englishMinSeconds = (this[englishMinKey] ?: 5).coerceIn(3, 20),
            lastChinese = play(chineseUnitsKey, chineseRandomKey, chineseModeKey, cueKey = null, EnglishCue.BOTH),
            lastRead = play(readUnitsKey, readRandomKey, readModeKey, readCueKey, EnglishCue.BOTH),
            lastDrill = play(drillUnitsKey, drillRandomKey, drillModeKey, drillCueKey, EnglishCue.CHINESE),
        )
    }

    private fun Preferences.play(
        unitsKey: Preferences.Key<String>,
        randomKey: Preferences.Key<Boolean>,
        modeKey: Preferences.Key<String>,
        cueKey: Preferences.Key<String>?,
        defaultCue: EnglishCue,
    ): LastPlay {
        return LastPlay(
            units = decodeUnits(this[unitsKey]),
            random = this[randomKey] ?: false,
            mode = this[modeKey]?.let { runCatching { PlayMode.valueOf(it) }.getOrNull() } ?: PlayMode.TIMER,
            cue = cueKey?.let { key -> this[key] }?.let { runCatching { EnglishCue.valueOf(it) }.getOrNull() }
                ?: defaultCue,
        )
    }
}

private fun MutablePreferences.putPlay(
    play: LastPlay,
    unitsKey: Preferences.Key<String>,
    randomKey: Preferences.Key<Boolean>,
    modeKey: Preferences.Key<String>,
    cueKey: Preferences.Key<String>?,
) {
    this[unitsKey] = encodeUnits(play.units)
    this[randomKey] = play.random
    this[modeKey] = play.mode.name
    if (cueKey != null) this[cueKey] = play.cue.name
}
