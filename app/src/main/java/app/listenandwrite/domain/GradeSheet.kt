package app.listenandwrite.domain

/**
 * 拍照批改只对这次听写的词，按题号对齐后逐字看。
 * 认成另一个字是明确写错。字数对不上，或这个字认不稳，是笔画没到位。
 * 照片上看不到笔顺，所以不判笔顺。
 */
enum class GradeMark {
    RIGHT,
    WRONG,
    STROKE,
    MISSING,
}

data class SeenChar(val char: Char, val confidence: Float = 0f)

data class InkBox(val left: Int, val top: Int, val right: Int, val bottom: Int)

data class SheetLine(
    val text: String,
    val chars: List<SeenChar> = emptyList(),
    val number: Int? = null,
    val box: InkBox? = null,
)

data class WrongSpot(val index: Int, val expected: Char, val seen: Char)

data class GradeHit(
    val mark: GradeMark,
    val expected: String,
    val seen: String,
    val spots: List<WrongSpot> = emptyList(),
    val box: InkBox? = null,
)

private const val SURE = 0.5f
private val leadNumber = Regex("""^\s*[（(]?\s*(\d{1,3})\s*[）).、．:：\s]""")
private val gluedNumber = Regex("""^\s*(\d{1,3})(?=\p{IsHan}|[A-Za-z])""")
private val skipLine = setOf("姓名", "班级", "学号", "听写", "订正", "订正栏", "语文", "英语", "默写")

fun leadingNumber(text: String): Int? {
    val lead = leadNumber.find(text) ?: gluedNumber.find(text) ?: return null
    return lead.groupValues[1].toIntOrNull()
}

fun alignLines(count: Int, lines: List<SheetLine>): List<SheetLine?> {
    val slots = arrayOfNulls<SheetLine>(count)
    if (count == 0) return emptyList()
    val usable = lines.map { line ->
        val number = line.number ?: leadingNumber(line.text)
        line.copy(number = number)
    }.filter { line ->
        val body = line.text.filter { WordText.isHan(it) || it.isLetter() }
        body.isNotEmpty() && body !in skipLine
    }
    val numbered = usable.mapNotNull { line ->
        val number = line.number
        if (number != null && number in 1..count) number to line else null
    }
    if (numbered.isNotEmpty()) {
        numbered.forEach { (number, line) ->
            if (slots[number - 1] == null) slots[number - 1] = line
        }
        return slots.toList()
    }
    usable.take(count).forEachIndexed { index, line -> slots[index] = line }
    return slots.toList()
}

fun gradeSheet(words: List<Pair<String, Boolean>>, lines: List<SheetLine>): List<GradeHit> {
    val aligned = alignLines(words.size, lines)
    return words.mapIndexed { index, (text, chinese) ->
        val line = aligned.getOrNull(index)
        if (line == null) {
            GradeHit(GradeMark.MISSING, text, "")
        } else {
            val chars = line.chars.ifEmpty { line.text.map { SeenChar(it) } }
            gradeOne(text, chars, chinese).copy(box = line.box)
        }
    }
}

fun gradeOne(expected: String, seenChars: List<SeenChar>, chinese: Boolean): GradeHit {
    val expectChars = if (chinese) {
        expected.filter { WordText.isHan(it) }
    } else {
        expected.filter { it.isLetter() }.lowercase()
    }
    val seen = if (chinese) {
        seenChars.filter { WordText.isHan(it.char) }
    } else {
        seenChars.filter { it.char.isLetter() }.map { it.copy(char = it.char.lowercaseChar()) }
    }
    val seenText = seen.joinToString("") { it.char.toString() }
    if (expectChars.isEmpty()) {
        val mark = when {
            seenText.isEmpty() -> GradeMark.MISSING
            expected.trim().equals(seenText, ignoreCase = true) -> GradeMark.RIGHT
            else -> GradeMark.WRONG
        }
        return GradeHit(mark, expected, seenText)
    }
    if (seen.isEmpty()) return GradeHit(GradeMark.MISSING, expected, "")
    if (expectChars.length != seen.size) return GradeHit(GradeMark.STROKE, expected, seenText)
    val spots = mutableListOf<WrongSpot>()
    var unsure = false
    expectChars.forEachIndexed { index, raw ->
        val wanted = if (chinese) raw else raw.lowercaseChar()
        val got = seen[index]
        if (wanted == got.char) return@forEachIndexed
        if (isSure(got.confidence)) spots += WrongSpot(index, raw, got.char)
        else unsure = true
    }
    val mark = when {
        spots.isNotEmpty() -> GradeMark.WRONG
        unsure -> GradeMark.STROKE
        else -> GradeMark.RIGHT
    }
    return GradeHit(mark, expected, seenText, spots)
}

fun gradeDetail(hit: GradeHit, chinese: Boolean): String = when (hit.mark) {
    GradeMark.RIGHT -> "写对"
    GradeMark.MISSING -> "没找到这一行"
    GradeMark.STROKE -> if (chinese) "笔画没到位" else "没认清"
    GradeMark.WRONG -> {
        val unit = if (chinese) "字" else "字母"
        hit.spots.joinToString("，") { "第${it.index + 1}个${unit}写成了「${it.seen}」" }.ifBlank { "明确写错" }
    }
}

/** 置信度为 0 表示识别器没给把握，它仍写出了另一个字，按明确写错。很低的把握才留给人看。 */
private fun isSure(confidence: Float): Boolean = confidence <= 0.001f || confidence >= SURE
