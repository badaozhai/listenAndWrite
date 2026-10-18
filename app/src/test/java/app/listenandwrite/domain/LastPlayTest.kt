package app.listenandwrite.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class LastPlayTest {
    @Test
    fun roundTripsUnitSets() {
        assertEquals("1,4,101", encodeUnits(setOf(101, 4, 1, 0)))
        assertEquals(setOf(1, 4, 101), decodeUnits("101,1,4"))
        assertEquals(emptySet<Int>(), decodeUnits(""))
        assertEquals(setOf(2), decodeUnits("x,2"))
    }

    @Test
    fun captionNamesLessonsAndStopsAfterThree() {
        assertEquals("第1课、第4课", unitsCaption(setOf(4, 1), chinese = true))
        assertEquals("第1单元、第2单元 等4个", unitsCaption(setOf(1, 2, 3, 4), chinese = false))
        val play = LastPlay(setOf(1), mode = PlayMode.TIMER, cue = EnglishCue.CHINESE)
        assertEquals("第1单元 · 定时 · 顺序 · 报中文", playCaption(play, chinese = false, withCue = true))
    }
}
