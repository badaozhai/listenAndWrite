package app.listenandwrite.domain

/** 上次从首页开始的一次播放。用来「继续」，也用来把勾选恢复出来。 */
data class LastPlay(
    val units: Set<Int> = emptySet(),
    val random: Boolean = false,
    val mode: PlayMode = PlayMode.TIMER,
    val cue: EnglishCue = EnglishCue.BOTH,
)

fun encodeUnits(units: Set<Int>): String =
    units.filter { it > 0 }.sorted().joinToString(",")

fun decodeUnits(raw: String?): Set<Int> =
    raw.orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }.toSet()

fun unitsCaption(units: Set<Int>, chinese: Boolean): String {
    val titles = units.filter { it > 0 }.sorted().map { slotTitle(it, chinese) }
    if (titles.isEmpty()) return ""
    if (titles.size <= 3) return titles.joinToString("、")
    return titles.take(2).joinToString("、") + " 等${titles.size}个"
}

fun playCaption(play: LastPlay, chinese: Boolean, withCue: Boolean): String {
    val units = unitsCaption(play.units, chinese)
    val mode = if (play.mode == PlayMode.TIMER) "定时" else "说完再继续"
    val order = if (play.random) "随机" else "顺序"
    val cue = if (withCue) " · ${englishCueLabel(play.cue)}" else ""
    return listOf(units, mode, order).filter { it.isNotEmpty() }.joinToString(" · ") + cue
}
