package app.listenandwrite.data

import app.listenandwrite.domain.Lang
import app.listenandwrite.domain.WordSet

suspend fun DictationDao.rememberWrong(word: WordEntity) {
    val lang = runCatching { Lang.valueOf(word.lang) }.getOrDefault(Lang.ZH)
    val key = WordSet.key(word.text, lang)
    val now = System.currentTimeMillis()
    val found = mistake(lang.name, key)
    if (found == null) {
        insertMistake(
            MistakeEntity(
                text = if (lang == Lang.EN) key else word.text.trim(),
                lang = lang.name,
                meaning = word.meaning,
                contrast = word.contrast,
                focus = word.focus,
                wrongCount = 1,
                updatedAt = now,
            ),
        )
    } else {
        bumpMistake(found.id, now)
    }
}

suspend fun DictationDao.undoWrong(word: WordEntity) {
    val lang = runCatching { Lang.valueOf(word.lang) }.getOrDefault(Lang.ZH)
    val found = mistake(lang.name, WordSet.key(word.text, lang)) ?: return
    if (found.wrongCount <= 1) {
        deleteMistake(found.id)
    } else {
        setMistakeCount(found.id, found.wrongCount - 1, System.currentTimeMillis())
    }
}
