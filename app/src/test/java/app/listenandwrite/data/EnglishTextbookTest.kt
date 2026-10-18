package app.listenandwrite.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishTextbookTest {
    @Test
    fun importsAllEightUnits() {
        assertEquals(164, englishTextbook.size)
        assertEquals((1..8).toList(), englishTextbook.map { it.unit }.distinct())
        assertEquals(listOf(1, 4), englishTextbook.filter { it.text == "listen" }.map { it.unit })
        assertEquals("五月", englishTextbook.first { it.text == "May" }.meaning)
        assertEquals("也许，可能", englishTextbook.first { it.text == "may" }.meaning)
    }
}

class ChineseTextbookTest {
    @Test
    fun keepsLessonNumbersAndGardens() {
        assertEquals(213, chineseTextbook.size)
        assertEquals(8, chineseTextbook.count { it.slot == "1" })
        assertEquals(22, chineseTextbook.count { it.slot == "7" })
        assertEquals(8, chineseTextbook.count { it.slot == "g18" })
        assertEquals(8, chineseTextbook.count { it.slot == "g22" })
        assertEquals(listOf("7"), chineseTextbook.filter { it.text == "势不可当" }.map { it.slot })
        val tunnel = chineseTextbook.first { it.text == "地道" }
        assertEquals("4", tunnel.slot)
        assertTrue(tunnel.meaning.contains("地下通道"))
    }
}
