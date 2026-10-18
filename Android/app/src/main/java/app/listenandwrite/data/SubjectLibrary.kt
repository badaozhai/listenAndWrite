package app.listenandwrite.data

import app.listenandwrite.domain.DRILL_SUBJECT
import app.listenandwrite.domain.Lang
import app.listenandwrite.domain.StudyGuide
import app.listenandwrite.domain.Subject
import app.listenandwrite.domain.WordSet
import app.listenandwrite.domain.subjectLabel
import app.listenandwrite.domain.unitNumber

/**
 * 语文、英语各有一份词库。打开时把同一科的旧词单并进来，单元号收成 1 到 15。
 */
suspend fun DictationDao.prepareSubject(subject: Subject): Long {
    val now = System.currentTimeMillis()
    val existing = findSubjectList(subject.name)
    val canonicalId = existing?.id ?: insertList(
        WordListEntity(
            title = subjectLabel(subject.name),
            subject = subject.name,
            createdAt = now,
            updatedAt = now,
        ),
    )
    val have = words(canonicalId).map { wordKey(it) }.toMutableSet()
    allLists()
        .filter { it.id != canonicalId && (it.subject == subject.name || it.subject == Subject.MIXED.name) }
        .forEach { list ->
            words(list.id).forEach { word ->
                if (!belongs(list.subject, word, subject)) return@forEach
                val key = wordKey(word)
                if (key in have) {
                    deleteWord(word.id)
                } else {
                    moveWord(word.id, canonicalId, unitNumber(word.unitName)?.toString().orEmpty())
                    have += key
                }
            }
            if (words(list.id).isEmpty()) deleteList(list.id)
        }
    if (subject == Subject.ENGLISH) {
        importEnglishTextbook(canonicalId)
        prepareEnglishDrills()
    }
    if (subject == Subject.CHINESE) importChineseTextbook(canonicalId)
    return canonicalId
}

/** 五上 Unit 1–4 默写练习。单独一份词单，不并进英语单词表。已有的不覆盖，方便改第 3 单元的补译。 */
suspend fun DictationDao.prepareEnglishDrills(): Long {
    val now = System.currentTimeMillis()
    val existing = findSubjectList(DRILL_SUBJECT)
    val listId = existing?.id ?: insertList(
        WordListEntity(
            title = "默写练习",
            subject = DRILL_SUBJECT,
            createdAt = now,
            updatedAt = now,
        ),
    )
    val current = words(listId)
    val have = current.map { drillKey(it) }.toMutableSet()
    var nextPosition = (current.maxOfOrNull { it.position } ?: -1) + 1
    val fresh = mutableListOf<WordEntity>()
    englishDrills.forEach { entry ->
        val key = entry.unit to entry.text.lowercase()
        if (key in have) return@forEach
        fresh += WordEntity(
            listId = listId,
            text = entry.text,
            lang = Lang.EN.name,
            position = nextPosition,
            meaning = entry.meaning,
            contrast = entry.note,
            focus = "",
            unitName = entry.unit.toString(),
        )
        have += key
        nextPosition += 1
    }
    if (fresh.isNotEmpty()) insertWords(fresh)
    return listId
}

private fun drillKey(word: WordEntity): Pair<Int, String>? {
    val unit = unitNumber(word.unitName) ?: return null
    return unit to word.text.lowercase()
}

private suspend fun DictationDao.importChineseTextbook(listId: Long) {
    val demo = setOf("春天", "一心一意", "势不可挡", "气势", "优势")
    words(listId)
        .filter { word -> word.unitName.isBlank() && word.text in demo }
        .forEach { deleteWord(it.id) }
    val current = words(listId).toMutableList()
    var nextPosition = (current.maxOfOrNull { it.position } ?: -1) + 1
    chineseTextbook.forEach { entry ->
        val guide = StudyGuide.lookup(entry.text, Lang.ZH)
        val meaning = entry.meaning.ifBlank { guide?.meaning.orEmpty() }
        val existing = current.find { word ->
            word.lang == Lang.ZH.name && word.text == entry.text && word.unitName == entry.slot
        }
        if (existing != null) {
            if (entry.meaning.isNotBlank() && existing.meaning != entry.meaning) {
                updateWord(
                    id = existing.id,
                    text = existing.text,
                    lang = existing.lang,
                    meaning = entry.meaning,
                    contrast = existing.contrast,
                    focus = existing.focus,
                    unitName = existing.unitName,
                )
            }
            return@forEach
        }
        val inserted = WordEntity(
            listId = listId,
            text = entry.text,
            lang = Lang.ZH.name,
            position = nextPosition,
            meaning = meaning,
            contrast = guide?.contrast.orEmpty(),
            focus = guide?.focus.orEmpty(),
            unitName = entry.slot,
        )
        val id = insertWords(listOf(inserted)).first()
        current += inserted.copy(id = id)
        nextPosition += 1
    }
}

private suspend fun DictationDao.importEnglishTextbook(listId: Long) {
    val demo = setOf("apple", "school", "there", "their", "they're", "good morning")
    words(listId)
        .filter { word -> word.unitName.isBlank() && word.text.lowercase() in demo }
        .forEach { deleteWord(it.id) }
    val current = words(listId).toMutableList()
    var nextPosition = (current.maxOfOrNull { it.position } ?: -1) + 1
    englishTextbook.forEach { entry ->
        val already = current.any { word ->
            word.lang == Lang.EN.name &&
                word.text.equals(entry.text, ignoreCase = true) &&
                unitNumber(word.unitName) == entry.unit
        }
        if (already) return@forEach
        val guide = StudyGuide.lookup(entry.text, Lang.EN)
        val inserted = WordEntity(
            listId = listId,
            text = entry.text,
            lang = Lang.EN.name,
            position = nextPosition,
            meaning = entry.meaning,
            contrast = guide?.contrast.orEmpty(),
            focus = guide?.focus.orEmpty(),
            unitName = entry.unit.toString(),
        )
        val id = insertWords(listOf(inserted)).first()
        current += inserted.copy(id = id)
        nextPosition += 1
    }
}

private fun belongs(listSubject: String, word: WordEntity, subject: Subject): Boolean {
    if (listSubject == subject.name) return true
    if (listSubject != Subject.MIXED.name) return false
    val lang = runCatching { Lang.valueOf(word.lang) }.getOrDefault(Lang.ZH)
    return if (subject == Subject.ENGLISH) lang == Lang.EN else lang != Lang.EN
}

private fun wordKey(word: WordEntity): Pair<String, Lang> {
    val lang = runCatching { Lang.valueOf(word.lang) }.getOrDefault(Lang.ZH)
    return WordSet.key(word.text, lang) to lang
}
