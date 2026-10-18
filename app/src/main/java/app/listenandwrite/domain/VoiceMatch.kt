package app.listenandwrite.domain

object VoiceMatch {
    private val phrases = listOf(
        "下一个",
        "下一题",
        "好了",
        "好啦",
        "好的",
        "可以了",
        "写好了",
        "行了",
        "next",
        "done",
    )

    fun isAdvance(raw: String): Boolean {
        val normalized = raw.lowercase().replace(Regex("[\\s\\p{P}]+"), "")
        if (normalized.isEmpty()) return false
        if (phrases.any { normalized.contains(it) }) return true
        return normalized == "ok" || normalized == "okay"
    }
}
