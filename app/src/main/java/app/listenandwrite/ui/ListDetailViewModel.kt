package app.listenandwrite.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.listenandwrite.ListenApp
import app.listenandwrite.data.AppSettings
import app.listenandwrite.data.WordEntity
import app.listenandwrite.data.WordListEntity
import app.listenandwrite.data.studiedWord
import app.listenandwrite.domain.CHINESE_UNIT_COUNT
import app.listenandwrite.domain.DRILL_SUBJECT
import app.listenandwrite.domain.EnglishCue
import app.listenandwrite.domain.GARDEN_AFTER_18
import app.listenandwrite.domain.GARDEN_AFTER_22
import app.listenandwrite.domain.Lang
import app.listenandwrite.domain.Subject
import app.listenandwrite.domain.slotTitle
import app.listenandwrite.domain.LessonOptions
import app.listenandwrite.domain.LessonScript
import app.listenandwrite.domain.PlayMode
import app.listenandwrite.domain.StudyGuide
import app.listenandwrite.domain.WordSet
import app.listenandwrite.domain.WordText
import app.listenandwrite.domain.fallbackLang
import app.listenandwrite.domain.unitNumber
import app.listenandwrite.domain.unitSlot
import app.listenandwrite.ocr.OcrEngine
import app.listenandwrite.domain.ParsedWord
import app.listenandwrite.speech.Speaker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class ListDetailViewModel(
    app: Application,
    private val listId: Long,
    val fixedUnit: Int?,
) : AndroidViewModel(app) {
    private val listenApp = app as ListenApp
    private val dao = listenApp.database.dao()

    val list: StateFlow<WordListEntity?> = dao.observeList(listId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val words: StateFlow<List<WordEntity>> = dao.observeWords(listId)
        .map { items ->
            if (fixedUnit == null) items else items.filter { matchesUnit(it.unitName, fixedUnit) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<AppSettings> = listenApp.settings.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val message = _message.asSharedFlow()

    private val _openReview = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openReview = _openReview.asSharedFlow()

    private val _openSession = MutableSharedFlow<PlayMode>(extraBufferCapacity = 1)
    val openSession = _openSession.asSharedFlow()

    private val _groupOffer = MutableStateFlow<String?>(null)
    val groupOffer: StateFlow<String?> = _groupOffer.asStateFlow()
    private var pendingGroups = emptyList<PendingGroup>()

    val entryUnit = MutableStateFlow("")
    private val chosenUnits = MutableStateFlow<Set<String>?>(null)
    val playUnits: StateFlow<Set<String>> = combine(words, chosenUnits) { items, chosen ->
        val all = items.map { it.unitName }.toSet()
        chosen?.intersect(all) ?: all
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val randomOrder = MutableStateFlow(false)
    val drillCue = MutableStateFlow(EnglishCue.CHINESE)

    fun setDrillCue(cue: EnglishCue) {
        drillCue.value = cue
    }

    init {
        if (fixedUnit != null && fixedUnit in 1..CHINESE_UNIT_COUNT) {
            entryUnit.value = fixedUnit.toString()
        }
        viewModelScope.launch {
            randomOrder.value = listenApp.settings.flow.first().shuffle
            backfillEmptyNotes()
        }
    }

    fun setEntryUnit(unit: String) {
        entryUnit.value = unit.trim()
    }

    fun togglePlayUnit(unit: String) {
        val all = words.value.map { it.unitName }.toSet()
        val current = chosenUnits.value ?: all
        chosenUnits.value = if (unit in current) current - unit else current + unit
    }

    fun setRandom(random: Boolean) {
        randomOrder.value = random
    }

    private val _deleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val deleted = _deleted.asSharedFlow()

    private var previewJob: Job? = null

    fun rename(title: String) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch { dao.rename(listId, clean, System.currentTimeMillis()) }
    }

    fun addManual(raw: String) {
        val subject = list.value?.subject
        val fallback = if (subject == null) Lang.ZH else fallbackLang(subject)
        val parsed = WordText.parseBatch(raw, fallback)
        if (parsed.isEmpty()) {
            _message.tryEmit("没有读到词语")
            return
        }
        viewModelScope.launch { insertParsed(parsed) }
    }

    fun updateWord(word: WordEntity, text: String, lang: Lang, meaning: String, contrast: String, unitName: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val clash = dao.words(listId).any { other ->
                other.id != word.id && WordSet.same(other.text, runCatching { Lang.valueOf(other.lang) }.getOrDefault(Lang.ZH), clean, lang)
            }
            if (clash) {
                _message.emit("词单里已经有这个词，没有再存一份")
                return@launch
            }
            val number = unitNumber(unitName)
            if (unitName.isNotBlank() && number == null) {
                _message.emit("单元请填 1 到 15")
                return@launch
            }
            val hit = StudyGuide.lookup(clean, lang)
            dao.updateWord(
                id = word.id,
                text = clean,
                lang = lang.name,
                meaning = meaning.trim(),
                contrast = contrast.trim(),
                focus = hit?.focus.orEmpty(),
                unitName = number?.toString().orEmpty(),
            )
            dao.touch(listId, System.currentTimeMillis())
        }
    }

    fun requestGroup(word: WordEntity) {
        val lang = runCatching { Lang.valueOf(word.lang) }.getOrDefault(Lang.ZH)
        viewModelScope.launch {
            val have = dao.words(listId).map { it.text }.toSet()
            val missing = StudyGuide.missingCompanions(word.text, lang, have)
            if (missing.isEmpty()) {
                _message.emit("这一组已经在词单里了")
                return@launch
            }
            pendingGroups = listOf(PendingGroup(word.id, missing, lang))
            _groupOffer.value = offerText(pendingGroups)
        }
    }

    fun acceptGroupOffer() {
        val groups = pendingGroups
        pendingGroups = emptyList()
        _groupOffer.value = null
        if (groups.isEmpty()) return
        viewModelScope.launch { insertGroups(groups) }
    }

    fun dismissGroupOffer() {
        pendingGroups = emptyList()
        _groupOffer.value = null
    }

    fun deleteWord(word: WordEntity) {
        viewModelScope.launch {
            dao.deleteWord(word.id)
            dao.touch(listId, System.currentTimeMillis())
        }
    }

    fun move(word: WordEntity, delta: Int) {
        viewModelScope.launch {
            val all = dao.words(listId).toMutableList()
            val index = all.indexOfFirst { it.id == word.id }
            val target = index + delta
            if (index < 0 || target !in all.indices) return@launch
            val item = all.removeAt(index)
            all.add(target, item)
            dao.reorder(listId, all.map { it.id })
        }
    }

    fun deleteList() {
        viewModelScope.launch {
            if (fixedUnit != null) {
                dao.words(listId).filter { matchesUnit(it.unitName, fixedUnit) }.forEach { dao.deleteWord(it.id) }
                dao.touch(listId, System.currentTimeMillis())
            } else {
                dao.deleteList(listId)
                _deleted.emit(Unit)
            }
        }
    }

    fun recognize(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            try {
                val fallback = fallbackLang(list.value?.subject ?: "CHINESE")
                val parsed = withTimeout(20_000) {
                    OcrEngine.recognize(getApplication(), uri, fallback)
                }
                if (parsed.isEmpty()) {
                    _message.emit("没有认出词语。请拍清楚，尽量横着排、字大一点。")
                } else {
                    listenApp.draft.set(listId, parsed, entryUnit.value)
                    _openReview.emit(Unit)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _message.emit("识别失败了，请换一张照片再试。")
            } finally {
                _busy.value = false
            }
        }
    }

    fun start(mode: PlayMode) {
        previewJob?.cancel()
        viewModelScope.launch {
            val items = dao.words(listId)
            if (items.isEmpty()) {
                _message.emit("先加上要听写的词语")
                return@launch
            }
            val filtered = if (fixedUnit != null) {
                items.filter { matchesUnit(it.unitName, fixedUnit) }
            } else {
                items.filter { it.unitName in playUnits.value }
            }
            if (filtered.isEmpty()) {
                _message.emit(if (fixedUnit != null) "这个单元还没有词" else "先勾选这次要听写的单元")
                return@launch
            }
            val ordered = if (randomOrder.value) filtered.shuffled() else filtered
            val drill = list.value?.subject == DRILL_SUBJECT
            val title = if (fixedUnit != null) {
                val slot = slotTitle(fixedUnit, list.value?.subject == Subject.CHINESE.name)
                if (drill) "默写练习 $slot" else slot
            } else {
                list.value?.title ?: "听写"
            }
            val back = if (fixedUnit != null) "unit/$listId/$fixedUnit" else "subject/${list.value?.subject ?: "CHINESE"}"
            listenApp.practice.set(
                listId,
                title,
                mode,
                ordered,
                back,
                englishCue = if (drill) drillCue.value else null,
            )
            _openSession.emit(mode)
        }
    }

    fun preview() {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            val items = dao.words(listId)
            if (items.isEmpty()) {
                _message.emit("还没有词语")
                return@launch
            }
            val current = listenApp.settings.flow.first()
            val speaker = Speaker(getApplication())
            val options = LessonOptions(
                repeats = 1,
                explainChinese = current.explainChinese,
                englishCue = if (list.value?.subject == DRILL_SUBJECT) drillCue.value else current.englishCue,
                spellEnglish = false,
            )
            try {
                val focuses = items.map { it.focus }
                items.forEachIndexed { index, word ->
                    val lang = runCatching { Lang.valueOf(word.lang) }.getOrDefault(Lang.ZH)
                    val lines = LessonScript.lines(
                        text = word.text,
                        lang = lang,
                        meaning = word.meaning,
                        contrast = word.contrast,
                        place = LessonScript.placeOf(focuses, index),
                        options = options,
                    )
                    for (line in lines) {
                        withTimeout(18_000) { speaker.speakAndWait(line.text, line.lang, current.speechRate) }
                        delay(250)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _message.emit("试听中断了")
            } finally {
                speaker.shutdown()
            }
        }
    }

    fun stopPreview() {
        previewJob?.cancel()
    }

    private suspend fun insertParsed(parsed: List<ParsedWord>) {
        val existing = dao.words(listId)
        val merged = WordSet.merge(
            existing.map { it.text to (runCatching { Lang.valueOf(it.lang) }.getOrDefault(Lang.ZH)) },
            parsed,
        )
        if (merged.fresh.isEmpty()) {
            _message.emit(if (merged.skipped > 0) "这些词都已经在词单里，没有重复保存" else "没有读到词语")
            return
        }
        val already = existing.map { it.text }.toSet() + merged.fresh.map { it.text }
        val start = dao.maxPosition(listId) + 1
        val unit = when (fixedUnit) {
            GARDEN_AFTER_18 -> "g18"
            GARDEN_AFTER_22 -> "g22"
            in 1..CHINESE_UNIT_COUNT -> fixedUnit.toString()
            else -> unitNumber(entryUnit.value)?.toString().orEmpty()
        }
        val entities = merged.fresh.mapIndexed { index, word ->
            studiedWord(listId, word.text, word.lang, start + index, unit)
        }
        val ids = dao.insertWords(entities)
        if (merged.skipped > 0) {
            _message.emit("新加了 ${merged.fresh.size} 个，已有的 ${merged.skipped} 个没有重复保存")
        }
        dao.touch(listId, System.currentTimeMillis())
        val saved = entities.zip(ids) { word, id -> word.copy(id = id) }
        val known = already + parsed.map { it.text }
        val groups = saved.mapNotNull { word ->
            val lang = runCatching { Lang.valueOf(word.lang) }.getOrDefault(Lang.ZH)
            val missing = StudyGuide.missingCompanions(word.text, lang, known)
            if (missing.isEmpty()) null else PendingGroup(word.id, missing, lang)
        }
        if (groups.isNotEmpty()) {
            pendingGroups = groups
            _groupOffer.value = offerText(groups)
        }
    }

    private suspend fun insertGroups(groups: List<PendingGroup>) {
        val all = dao.words(listId).toMutableList()
        val have = all.map { it.text }.toMutableSet()
        groups.forEach { group ->
            val index = all.indexOfFirst { it.id == group.anchorId }
            if (index < 0) return@forEach
            val anchor = all[index]
            val fresh = group.texts.filter { text -> have.none { WordSet.same(it, group.lang, text, group.lang) } }
            if (fresh.isEmpty()) return@forEach
            val entities = fresh.map { text -> studiedWord(listId, text, group.lang, 0, anchor.unitName) }
            val ids = dao.insertWords(entities)
            val withIds = entities.zip(ids) { word, id -> word.copy(id = id) }
            all.addAll(index + 1, withIds)
            have += fresh
        }
        dao.reorder(listId, all.map { it.id })
    }

    private suspend fun backfillEmptyNotes() {
        val all = dao.words(listId)
        all.filter { it.meaning.isBlank() && it.contrast.isBlank() && it.focus.isBlank() }.forEach { word ->
            val lang = runCatching { Lang.valueOf(word.lang) }.getOrDefault(Lang.ZH)
            val filled = studiedWord(word.listId, word.text, lang, word.position)
            if (filled.meaning.isBlank() && filled.contrast.isBlank() && filled.focus.isBlank()) return@forEach
            dao.updateWord(word.id, word.text, word.lang, filled.meaning, filled.contrast, filled.focus, word.unitName)
        }
    }

    private fun offerText(groups: List<PendingGroup>): String {
        return groups.joinToString("\n") { group ->
            val label = StudyGuide.focusLabel(group.lang.let { StudyGuide.lookup(group.texts.first(), group.lang)?.focus.orEmpty() })
                .ifBlank { if (group.lang == Lang.EN) "同音" else "这一组" }
            "$label：${group.texts.joinToString("、")}"
        }
    }
}

private data class PendingGroup(val anchorId: Long, val texts: List<String>, val lang: Lang)

class ListDetailViewModelFactory(
    private val app: Application,
    private val listId: Long,
    private val fixedUnit: Int?,
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return ListDetailViewModel(app, listId, fixedUnit) as T
    }
}

private fun matchesUnit(unitName: String, fixedUnit: Int): Boolean {
    return unitSlot(unitName) == fixedUnit
}
