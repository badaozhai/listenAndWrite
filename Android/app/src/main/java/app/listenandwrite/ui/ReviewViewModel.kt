package app.listenandwrite.ui

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.listenandwrite.DraftWord
import app.listenandwrite.ListenApp
import app.listenandwrite.data.studiedWord
import app.listenandwrite.domain.Lang
import app.listenandwrite.domain.ParsedWord
import app.listenandwrite.domain.WordSet
import app.listenandwrite.domain.WordText
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ReviewViewModel(app: Application, private val listId: Long) : AndroidViewModel(app) {
    private val listenApp = app as ListenApp
    private val dao = listenApp.database.dao()
    private val original: List<DraftWord> = listenApp.draft.original

    val lines = mutableStateListOf<DraftWord>().apply {
        if (listenApp.draft.listId == listId) addAll(listenApp.draft.lines)
    }

    val ready: Boolean = listenApp.draft.listId == listId && lines.isNotEmpty()

    private val _saved = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val saved = _saved.asSharedFlow()
    private val _notice = MutableStateFlow("")
    val notice = _notice.asStateFlow()

    fun toggle(index: Int) {
        val item = lines.getOrNull(index) ?: return
        lines[index] = item.copy(checked = !item.checked)
    }

    fun edit(index: Int, text: String, lang: Lang) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val item = lines.getOrNull(index) ?: return
        lines[index] = item.copy(text = clean, lang = lang)
    }

    fun remove(index: Int) {
        if (index in lines.indices) lines.removeAt(index)
    }

    fun restore() {
        lines.clear()
        lines.addAll(original)
    }

    fun applyHanGroups(size: Int) {
        replace(WordText.regroupHan(lines.toParsed(), size))
    }

    fun splitEnglish() {
        replace(WordText.splitEnglishSpaces(lines.toParsed()))
    }

    fun save() {
        val chosen = lines.filter { it.checked && it.text.isNotBlank() }
        if (chosen.isEmpty()) return
        viewModelScope.launch {
            val existing = dao.words(listId)
            val merged = WordSet.merge(
                existing.map { it.text to runCatching { Lang.valueOf(it.lang) }.getOrDefault(it.lang.let { Lang.ZH }) },
                chosen.map { ParsedWord(it.text.trim(), it.lang) },
            )
            if (merged.fresh.isEmpty()) {
                _notice.value = "这些词都已经在词单里，没有重复保存"
                return@launch
            }
            val start = dao.maxPosition(listId) + 1
            val unit = listenApp.draft.unitName
            dao.insertWords(
                merged.fresh.mapIndexed { index, word ->
                    studiedWord(listId, word.text, word.lang, start + index, unit)
                },
            )
            dao.touch(listId, System.currentTimeMillis())
            listenApp.draft.lines = emptyList()
            _saved.emit(merged.fresh.size)
        }
    }

    private fun replace(parsed: List<ParsedWord>) {
        val previous = lines.toList()
        lines.clear()
        lines.addAll(
            parsed.map { word ->
                val wasChecked = previous.find { it.text == word.text }?.checked ?: true
                DraftWord(word.text, word.lang, wasChecked)
            },
        )
    }

    private fun List<DraftWord>.toParsed(): List<ParsedWord> =
        map { ParsedWord(it.text, it.lang) }
}

class ReviewViewModelFactory(
    private val app: Application,
    private val listId: Long,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ReviewViewModel(app, listId) as T
    }
}
