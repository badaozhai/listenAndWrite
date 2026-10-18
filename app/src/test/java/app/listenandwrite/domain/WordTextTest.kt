package app.listenandwrite.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WordTextTest {
    @Test
    fun parsesWorksheetWithoutSplittingEnglishPhrases() {
        val raw = """
            听写
            1. 春天
            2、秋天
            一心一意、春暖花开
            3. good morning
            apple, banana
            春天 花朵
        """.trimIndent()

        val words = WordText.parseBatch(raw).map { it.text }
        assertEquals(
            listOf("春天", "秋天", "一心一意", "春暖花开", "good morning", "apple", "banana", "春天", "花朵"),
            words,
        )
        assertEquals(Lang.EN, WordText.parseBatch(raw).first { it.text == "good morning" }.lang)
        assertEquals(Lang.ZH, WordText.parseBatch(raw).first { it.text == "一心一意" }.lang)
    }

    @Test
    fun writingTimeFollowsIdiomAndPhraseRules() {
        assertEquals(5, WordText.writingSeconds("春天", 5, 10))
        assertEquals(10, WordText.writingSeconds("一心一意", 5, 10))
        assertEquals(10, WordText.writingSeconds("「春暖花开」", 5, 10))
        assertEquals(5, WordText.writingSeconds("apple", 5, 10))
        assertEquals(11, WordText.writingSeconds("good morning", 5, 10))
        assertEquals(5, WordText.englishGapSeconds("or", 1, 5))
        assertEquals(9, WordText.englishGapSeconds("carefully", 1, 5))
        assertEquals(18, WordText.englishGapSeconds("carefully", 2, 5))
        assertTrue(WordText.isFourCharIdiom("一心一意"))
        assertFalse(WordText.isFourCharIdiom("春天"))
        assertFalse(WordText.isFourCharIdiom("good"))
    }

    @Test
    fun regroupsOnlyEvenHanRuns() {
        val items = listOf(
            ParsedWord("春暖花开一心一意", Lang.ZH),
            ParsedWord("春天", Lang.ZH),
            ParsedWord("good morning", Lang.EN),
        )
        assertEquals(
            listOf("春暖", "花开", "一心", "一意", "春天", "good morning"),
            WordText.regroupHan(items, 2).map { it.text },
        )
        assertEquals(
            listOf("春暖花开", "一心一意", "春天", "good morning"),
            WordText.regroupHan(items, 4).map { it.text },
        )
    }

    @Test
    fun spellsSingleEnglishWordsOnly() {
        assertEquals("cat. C, A, T", WordText.spokenForm("cat", Lang.EN, spellEnglish = true))
        assertEquals("good morning", WordText.spokenForm("good morning", Lang.EN, spellEnglish = true))
        assertEquals("春天", WordText.spokenForm("春天", Lang.ZH, spellEnglish = true))
        assertNull(WordText.spellLetters("a"))
    }
}
