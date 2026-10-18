package app.listenandwrite.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyGuideTest {
    @Test
    fun chineseGroupsConfusableCharacter() {
        val hit = StudyGuide.lookup("势不可挡", Lang.ZH)!!
        assertEquals("势", hit.focus)
        assertTrue(hit.meaning.contains("挡"))
        assertTrue(hit.contrast.contains("事"))
        assertTrue(hit.groupTip.contains("势"))
        assertTrue("气势" in hit.companions)
        assertFalse("势不可挡" in hit.companions)
        assertFalse("事情" in hit.companions)
    }

    @Test
    fun oppositeCharacterGetsItsOwnGroup() {
        val hit = StudyGuide.lookup("事情", Lang.ZH)!!
        assertEquals("事", hit.focus)
        assertTrue(hit.contrast.contains("势"))
        assertEquals(emptyList<String>(), StudyGuide.missingCompanions("事情", Lang.ZH, hit.companions.toSet() + "事情"))
    }

    @Test
    fun idiomWithoutConfusionStillHasMeaning() {
        val hit = StudyGuide.lookup("一心一意", Lang.ZH)!!
        assertTrue(hit.meaning.contains("一心") || hit.meaning.contains("心思"))
        assertEquals("", hit.focus)
    }

    @Test
    fun englishHomophonesStayTogether() {
        val there = StudyGuide.lookup("there", Lang.EN)!!
        assertEquals("那里", there.meaning)
        assertTrue(there.contrast.contains("their"))
        assertTrue("their" in there.companions)
        assertEquals(there.focus, StudyGuide.lookup("their", Lang.EN)!!.focus)
        assertEquals("苹果", StudyGuide.lookup("Apple", Lang.EN)!!.meaning)
        assertEquals("", StudyGuide.lookup("Apple", Lang.EN)!!.focus)
    }
}

class LessonScriptTest {
    @Test
    fun chineseAloneExplainsBeforeTheLastReading() {
        val hit = StudyGuide.lookup("势不可挡", Lang.ZH)!!
        val lines = LessonScript.lines(
            text = "势不可挡",
            lang = Lang.ZH,
            meaning = hit.meaning,
            contrast = hit.contrast,
            place = GroupPlace.ALONE,
            options = LessonOptions(repeats = 1),
        )
        assertEquals("势不可挡", lines.first().text)
        val explain = lines.first { it.hint.contains("先别写") }
        assertTrue(explain.text.contains("挡"))
        assertTrue(explain.text.contains("事"))
        assertEquals("势不可挡", lines.last().text)
        assertTrue(lines.indexOf(explain) < lines.lastIndex)
    }

    @Test
    fun groupIntroIsSpokenOnce() {
        val focuses = listOf("势", "势")
        assertEquals(GroupPlace.HEAD, LessonScript.placeOf(focuses, 0))
        assertEquals(GroupPlace.REST, LessonScript.placeOf(focuses, 1))
        val head = LessonScript.lines(
            text = "势不可挡",
            lang = Lang.ZH,
            meaning = "来势很猛，挡也挡不住。",
            contrast = "这里的势，是气势的势，不是事情的事。",
            place = GroupPlace.HEAD,
            options = LessonOptions(repeats = 1),
        )
        assertTrue(head.first().text.contains("一起记"))
        assertFalse(head.drop(1).any { it.text.contains("事情") })
        val rest = LessonScript.lines(
            text = "气势",
            lang = Lang.ZH,
            meaning = "表现出来的力量。",
            contrast = "这里的势，是气势的势，不是事情的事。",
            place = GroupPlace.REST,
            options = LessonOptions(repeats = 1),
        )
        assertTrue(rest.any { it.text.contains("力量") })
        assertFalse(rest.any { it.text.contains("事情") })
    }

    @Test
    fun englishCueChoosesWhatIsSpoken() {
        val both = LessonScript.lines(
            text = "there",
            lang = Lang.EN,
            meaning = "那里",
            contrast = "",
            place = GroupPlace.ALONE,
            options = LessonOptions(repeats = 1, englishCue = EnglishCue.BOTH, spellEnglish = true),
        )
        assertEquals("there", both.first().text)
        assertEquals(Lang.ZH, both[1].lang)
        assertTrue(both[1].text.contains("那里"))
        assertTrue(both.any { it.hint.contains("拼读") })

        val chinese = LessonScript.lines(
            text = "there",
            lang = Lang.EN,
            meaning = "那里",
            contrast = "",
            place = GroupPlace.ALONE,
            options = LessonOptions(repeats = 1, englishCue = EnglishCue.CHINESE),
        )
        assertEquals(listOf(Lang.ZH), chinese.map { it.lang })

        val english = LessonScript.lines(
            text = "there",
            lang = Lang.EN,
            meaning = "那里",
            contrast = "",
            place = GroupPlace.ALONE,
            options = LessonOptions(repeats = 2, englishCue = EnglishCue.ENGLISH),
        )
        assertTrue(english.all { it.lang == Lang.EN && it.text == "there" })
    }

    @Test
    fun shuffleKeepsAMemoryGroupTogether() {
        val input = listOf("势不可挡", "气势", "apple")
        val focus = mapOf("势不可挡" to "势", "气势" to "势", "apple" to "")
        repeat(15) {
            val ordered = practiceOrder(input, shuffle = true) { focus.getValue(it) }
            assertEquals(1, ordered.indexOf("气势") - ordered.indexOf("势不可挡"))
        }
        assertEquals(input, practiceOrder(input, shuffle = false) { focus.getValue(it) })
    }
}
