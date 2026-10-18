package app.listenandwrite.domain

/**
 * 听写时间从「读完」之后才开始倒计时。
 * 朗读本身要一两秒，如果把这几秒算进写字时间，孩子来不及落笔。
 * 四字成语用更长的一档。英语按字母数留写字时间，短词也不少于最少秒数。
 */
object WordText {
    private val headers = setOf(
        "听写", "生字", "生词", "词语", "成语", "单词", "英语", "语文", "英文", "中文", "练习", "作业",
    )
    private val leadNumber = Regex(
        """^\s*(?:[\(（]?\d{1,3}[\)）]?|[一二三四五六七八九十]{1,3})\s*[.、．:：)）、\-]\s*""",
    )

    fun isHan(ch: Char): Boolean =
        Character.UnicodeScript.of(ch.code) == Character.UnicodeScript.HAN

    fun isLatinLetter(ch: Char): Boolean = ch in 'A'..'Z' || ch in 'a'..'z'

    fun isFourCharIdiom(text: String): Boolean {
        val significant = text.filter { it.isLetterOrDigit() }
        if (significant.length != 4) return false
        if (significant.any { isLatinLetter(it) || it.isDigit() }) return false
        return significant.all { isHan(it) }
    }

    fun letterCount(text: String): Int = text.count { isLatinLetter(it) }

    /** 报完之后的写字时间。字母数乘以每字母秒数，不够最少秒数就用最少秒数。 */
    fun englishGapSeconds(text: String, secondsPerLetter: Int, minimum: Int): Int {
        val perLetter = secondsPerLetter.coerceIn(1, 5)
        val floor = minimum.coerceIn(3, 30)
        val letters = letterCount(text).coerceAtLeast(1)
        return (letters * perLetter).coerceAtLeast(floor).coerceAtMost(90)
    }

    fun writingSeconds(
        text: String,
        base: Int,
        idiom: Int,
        secondsPerLetter: Int = 1,
        englishMinimum: Int = base,
    ): Int {
        if (text.any { isLatinLetter(it) } && text.none { isHan(it) }) {
            return englishGapSeconds(text, secondsPerLetter, englishMinimum)
        }
        val safeBase = base.coerceIn(3, 30)
        val safeIdiom = idiom.coerceIn(safeBase, 60)
        if (isFourCharIdiom(text)) return safeIdiom
        val han = text.count { isHan(it) }
        if (han >= 6) return safeIdiom
        return safeBase
    }

    fun detectLang(text: String, fallback: Lang = Lang.ZH): Lang {
        val han = text.count { isHan(it) }
        val latin = text.count { isLatinLetter(it) }
        return when {
            han > 0 && latin == 0 -> Lang.ZH
            latin > 0 && han == 0 -> Lang.EN
            han == 0 && latin == 0 -> fallback
            han >= latin -> Lang.ZH
            else -> Lang.EN
        }
    }

    fun parseBatch(raw: String, fallback: Lang = Lang.ZH): List<ParsedWord> {
        val items = mutableListOf<ParsedWord>()
        raw.lines().forEach { original ->
            var line = original.trim()
            if (line.isEmpty()) return@forEach
            line = leadNumber.replace(line, "").trim()
            if (line.isEmpty() || line in headers) return@forEach
            splitLine(line).forEach { token ->
                val cleaned = cleanToken(token)
                if (!accept(cleaned)) return@forEach
                items += ParsedWord(cleaned, detectLang(cleaned, fallback))
            }
        }
        return items
    }

    fun regroupHan(items: List<ParsedWord>, size: Int): List<ParsedWord> {
        if (size !in 2..4) return items
        val out = mutableListOf<ParsedWord>()
        items.forEach { item ->
            val significant = item.text.filter { it.isLetterOrDigit() }
            val han = significant.filter { isHan(it) }
            val canChunk = han.length == significant.length &&
                han.length > size &&
                han.length % size == 0 &&
                han.length <= 40
            if (canChunk) {
                han.chunked(size).forEach { piece ->
                    out += ParsedWord(piece, Lang.ZH)
                }
            } else {
                out += item
            }
        }
        return out
    }

    fun splitEnglishSpaces(items: List<ParsedWord>): List<ParsedWord> {
        return items.flatMap { item ->
            if (item.lang == Lang.EN && item.text.any { it.isWhitespace() }) {
                item.text.split(Regex("\\s+"))
                    .map { cleanToken(it) }
                    .filter { accept(it) }
                    .map { ParsedWord(it, Lang.EN) }
            } else {
                listOf(item)
            }
        }
    }

    fun spokenForm(text: String, lang: Lang, spellEnglish: Boolean): String {
        if (lang != Lang.EN || !spellEnglish) return text
        val spelled = spellLetters(text) ?: return text
        return "$text. $spelled"
    }

    fun spellLetters(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.any { it.isWhitespace() }) return null
        val letters = trimmed.filter { isLatinLetter(it) }
        if (letters.length < 2) return null
        return letters.map { it.uppercaseChar().toString() }.joinToString(", ")
    }

    private fun splitLine(line: String): List<String> {
        val parts = line.split(Regex("[,，、;；|/｜]+"))
        val out = mutableListOf<String>()
        parts.forEach { part ->
            val trimmed = part.trim()
            if (trimmed.isEmpty()) return@forEach
            if (trimmed.any { isHan(it) }) {
                out += trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
            } else {
                out += trimmed
            }
        }
        return out
    }

    private fun cleanToken(raw: String): String =
        raw.trim().trim { !it.isLetterOrDigit() }.trim()

    private fun accept(text: String): Boolean {
        if (text.isEmpty() || text.length > 80) return false
        if (text in headers) return false
        return text.any { it.isLetter() }
    }
}
