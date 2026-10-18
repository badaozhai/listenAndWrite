package app.listenandwrite.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishDrillTest {
    @Test
    fun importsFourUnitsFromTheSheets() {
        assertEquals(120, englishDrills.size)
        assertEquals(mapOf(1 to 31, 2 to 30, 3 to 31, 4 to 28), englishDrills.groupingBy { it.unit }.eachCount())
        val keys = englishDrills.map { it.unit to it.text.lowercase() }
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(englishDrills.all { it.meaning.isNotBlank() })
    }

    @Test
    fun keepsPrintedAnswersAndMarksUnitThreeGuesses() {
        val very = englishDrills.first { it.unit == 1 && it.text == "very much" }
        assertEquals("非常", very.meaning)
        assertEquals(
            "play card games",
            englishDrills.first { it.text == "play a card game" }.note.substringAfter("写 ").trim(),
        )
        assertEquals("listen to teachers carefully", englishDrills.first { it.unit == 1 && it.meaning == "认真听老师讲课" }.text)
        assertEquals("can't see or hear", englishDrills.first { it.meaning == "看不见也听不见" }.text)
        assertEquals("by the swimming pool", englishDrills.first { it.meaning == "在泳池旁边" }.text)
        assertEquals("beside", englishDrills.first { it.unit == 4 && it.text == "by" }.note.substringAfter("写 ").substringBefore(" 或"))
        assertEquals(1, englishDrills.count { it.unit == 4 && it.text.equals("keep safe", ignoreCase = true) })
        assertEquals(1, englishDrills.count { it.unit == 4 && it.text.equals("right now", ignoreCase = true) })
        val guessed = englishDrills.filter { it.unit == 3 }
        assertTrue(guessed.all { it.note.contains("没有印答案") })
        assertEquals("I don't think so.", guessed.first { it.meaning == "我不这么认为。" }.text)
        assertEquals("It keeps me healthy.", guessed.first { it.meaning == "它让我保持健康。" }.text)
    }
}
