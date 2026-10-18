package app.listenandwrite.domain

enum class Lang {
    ZH,
    EN,
}

enum class Subject {
    CHINESE,
    ENGLISH,
    MIXED,
}

/** 英语默写练习。和英语单词表分开存，避免和单元词混在一次播放里。 */
const val DRILL_SUBJECT = "EN_DRILL"

enum class PlayMode {
    TIMER,
    VOICE,
}

enum class EnglishCue {
    ENGLISH,
    CHINESE,
    BOTH,
}

fun englishCueLabel(cue: EnglishCue): String = when (cue) {
    EnglishCue.ENGLISH -> "报英文"
    EnglishCue.CHINESE -> "报中文"
    EnglishCue.BOTH -> "中英都报"
}

data class ParsedWord(
    val text: String,
    val lang: Lang,
)

fun subjectLabel(subject: String): String = when (subject) {
    Subject.CHINESE.name -> "语文"
    Subject.ENGLISH.name -> "英语"
    else -> "混合"
}

fun langLabel(lang: String): String = if (lang == Lang.EN.name) "英" else "中"

fun fallbackLang(subject: String): Lang = when (subject) {
    Subject.ENGLISH.name, DRILL_SUBJECT -> Lang.EN
    else -> Lang.ZH
}
