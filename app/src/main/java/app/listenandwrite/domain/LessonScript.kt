package app.listenandwrite.domain

enum class GroupPlace {
    ALONE,
    HEAD,
    REST,
}

data class LessonOptions(
    val repeats: Int = 2,
    val explainChinese: Boolean = true,
    val englishCue: EnglishCue = EnglishCue.BOTH,
    val spellEnglish: Boolean = false,
)

data class SpokenLine(
    val text: String,
    val lang: Lang,
    val hint: String,
)

object LessonScript {
    fun lines(
        text: String,
        lang: Lang,
        meaning: String,
        contrast: String,
        place: GroupPlace,
        options: LessonOptions,
    ): List<SpokenLine> {
        val guide = StudyGuide.lookup(text, lang)
        val tip = guide?.groupTip?.takeIf { it.isNotBlank() } ?: contrast
        return if (lang == Lang.ZH) {
            chinese(text, meaning, contrast, tip, place, options)
        } else {
            english(text, meaning, contrast, tip, place, options)
        }
    }

    fun placeOf(focuses: List<String>, index: Int): GroupPlace {
        val focus = focuses.getOrNull(index).orEmpty()
        if (focus.isBlank()) return GroupPlace.ALONE
        val prev = index > 0 && focuses[index - 1] == focus
        val next = index + 1 < focuses.size && focuses[index + 1] == focus
        return when {
            !prev && next -> GroupPlace.HEAD
            prev -> GroupPlace.REST
            else -> GroupPlace.ALONE
        }
    }

    private fun chinese(
        text: String,
        meaning: String,
        contrast: String,
        tip: String,
        place: GroupPlace,
        options: LessonOptions,
    ): List<SpokenLine> {
        val out = mutableListOf<SpokenLine>()
        if (options.explainChinese && place == GroupPlace.HEAD && tip.isNotBlank()) {
            out += SpokenLine("下面几个词一起记。$tip", Lang.ZH, "先听这一组")
        }
        repeat(options.repeats.coerceIn(1, 3)) { turn ->
            out += SpokenLine(text, Lang.ZH, if (turn == 0) "请听" else "再听一次")
        }
        if (!options.explainChinese) return out
        val contrastPart = if (place == GroupPlace.ALONE) contrast else ""
        val explain = joinParts(meaning, contrastPart)
        if (explain.isNotBlank()) {
            out += SpokenLine(explain, Lang.ZH, "听讲解，先别写")
            out += SpokenLine(text, Lang.ZH, "再听一次，准备写")
        }
        return out
    }

    private fun english(
        text: String,
        meaning: String,
        contrast: String,
        tip: String,
        place: GroupPlace,
        options: LessonOptions,
    ): List<SpokenLine> {
        val out = mutableListOf<SpokenLine>()
        val times = options.repeats.coerceIn(1, 3)
        val gloss = meaning.trim().ifBlank { contrast.trim() }
        val cue = options.englishCue
        if (cue != EnglishCue.CHINESE && place == GroupPlace.HEAD && tip.isNotBlank()) {
            out += SpokenLine(tip, Lang.ZH, "先听这一组")
        }
        repeat(times) { turn ->
            val again = turn > 0
            when (cue) {
                EnglishCue.ENGLISH -> out += SpokenLine(text, Lang.EN, if (again) "再听一次" else "请听英文")
                EnglishCue.CHINESE -> {
                    if (gloss.isBlank()) {
                        out += SpokenLine(text, Lang.EN, "没有中文，改为报英文")
                    } else {
                        out += SpokenLine(gloss, Lang.ZH, if (again) "再听一次" else "请听中文")
                    }
                }
                EnglishCue.BOTH -> {
                    out += SpokenLine(text, Lang.EN, if (again) "再听英文" else "请听英文")
                    if (gloss.isNotBlank()) out += SpokenLine(gloss, Lang.ZH, "请听中文")
                }
            }
        }
        if (options.spellEnglish && cue != EnglishCue.CHINESE) {
            val spelled = WordText.spellLetters(text)
            if (spelled != null) out += SpokenLine(spelled, Lang.EN, "拼读字母")
        }
        return out
    }

    private fun joinParts(meaning: String, contrast: String): String {
        val left = meaning.trim()
        val right = contrast.trim()
        if (left.isEmpty()) return right
        if (right.isEmpty()) return left
        val gap = if (left.last() == '。' || left.last() == '！') "" else "。"
        return left + gap + right
    }
}

fun <T> practiceOrder(words: List<T>, shuffle: Boolean, focusOf: (T) -> String): List<T> {
    if (!shuffle) return words
    val blocks = mutableListOf<MutableList<T>>()
    words.forEach { word ->
        val focus = focusOf(word)
        val last = blocks.lastOrNull()
        if (last != null && focus.isNotBlank() && focusOf(last.last()) == focus) {
            last.add(word)
        } else {
            blocks.add(mutableListOf(word))
        }
    }
    return blocks.shuffled().flatten()
}
