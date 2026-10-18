package app.listenandwrite.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordSetTest {
    @Test
    fun mergeKeepsUnionAndDropsDuplicates() {
        val existing = listOf("势不可挡" to Lang.ZH, "Apple" to Lang.EN)
        val incoming = listOf(
            ParsedWord("势不可挡", Lang.ZH),
            ParsedWord("气势", Lang.ZH),
            ParsedWord("apple", Lang.EN),
            ParsedWord("there", Lang.EN),
        )
        val merged = WordSet.merge(existing, incoming)
        assertEquals(listOf("气势", "there"), merged.fresh.map { it.text })
        assertEquals(2, merged.skipped)
    }

    @Test
    fun unitsSortByNumberAndLeaveBlankLast() {
        assertEquals(listOf("第2单元", "第10单元", ""), sortedUnits(listOf("", "第10单元", "第2单元")))
        assertEquals("其他", unitLabel(""))
        assertEquals("第2单元", unitLabel("第2单元"))
        assertEquals(3, unitNumber("第3单元"))
        assertEquals(16, unitNumber("第16课"))
        assertEquals(null, unitNumber("第25单元"))
    }
}
