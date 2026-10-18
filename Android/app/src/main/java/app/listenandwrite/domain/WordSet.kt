package app.listenandwrite.domain

data class MergeResult(
    val fresh: List<ParsedWord>,
    val skipped: Int,
)

object WordSet {
    fun key(text: String, lang: Lang): String {
        val trimmed = text.trim()
        return if (lang == Lang.EN) trimmed.lowercase() else trimmed
    }

    fun same(left: String, leftLang: Lang, right: String, rightLang: Lang): Boolean {
        return leftLang == rightLang && key(left, leftLang) == key(right, rightLang)
    }

    /** 新词并入已有词。已经有的留下，重复的丢掉。 */
    fun merge(existing: List<Pair<String, Lang>>, incoming: List<ParsedWord>): MergeResult {
        val seen = existing.map { key(it.first, it.second) to it.second }.toMutableSet()
        val fresh = mutableListOf<ParsedWord>()
        var skipped = 0
        incoming.forEach { word ->
            val cleaned = word.text.trim()
            val token = key(cleaned, word.lang) to word.lang
            if (cleaned.isEmpty() || token in seen) {
                skipped++
            } else {
                seen += token
                fresh += word.copy(text = cleaned)
            }
        }
        return MergeResult(fresh, skipped)
    }
}

const val UNIT_COUNT = 15
const val CHINESE_UNIT_COUNT = 24
const val GARDEN_AFTER_18 = 101
const val GARDEN_AFTER_22 = 102

fun unitNumber(raw: String): Int? {
    val trimmed = raw.trim()
    val exact = trimmed.toIntOrNull()
    if (exact != null) return exact.takeIf { it in 1..CHINESE_UNIT_COUNT }
    val labeled = Regex("""^第\s*(\d+)\s*(?:课|单元)$""").find(trimmed)?.groupValues?.get(1)?.toIntOrNull()
    return labeled?.takeIf { it in 1..CHINESE_UNIT_COUNT }
}

fun unitSlot(unitName: String): Int = when (unitName) {
    "g18" -> GARDEN_AFTER_18
    "g22" -> GARDEN_AFTER_22
    else -> unitNumber(unitName) ?: 0
}

fun slotTitle(slot: Int, chinese: Boolean): String = when (slot) {
    GARDEN_AFTER_18 -> "语文园地（第18课后）"
    GARDEN_AFTER_22 -> "语文园地（第22课后）"
    0 -> "其他"
    else -> if (chinese) "第${slot}课" else "第${slot}单元"
}

fun unitLabel(unit: String): String {
    val number = unitNumber(unit)
    return if (number != null) "第${number}单元" else if (unit.isBlank()) "其他" else unit
}

fun sortedUnits(units: Collection<String>): List<String> {
    return units.distinct().sortedWith(
        compareBy<String>({ it.isBlank() }, { numberIn(it) ?: Int.MAX_VALUE }, { it }),
    )
}

private fun numberIn(unit: String): Int? = Regex("\\d+").find(unit)?.value?.toIntOrNull()
