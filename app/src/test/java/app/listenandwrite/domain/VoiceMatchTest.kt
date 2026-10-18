package app.listenandwrite.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceMatchTest {
    @Test
    fun acceptsDictationCommands() {
        assertTrue(VoiceMatch.isAdvance("下一个"))
        assertTrue(VoiceMatch.isAdvance("好了"))
        assertTrue(VoiceMatch.isAdvance("嗯，好了"))
        assertTrue(VoiceMatch.isAdvance("写好了"))
        assertTrue(VoiceMatch.isAdvance("next"))
        assertTrue(VoiceMatch.isAdvance("下 一个"))
        assertFalse(VoiceMatch.isAdvance("好"))
    }

    @Test
    fun ignoresOrdinaryWords() {
        assertFalse(VoiceMatch.isAdvance("春天"))
        assertFalse(VoiceMatch.isAdvance("banana"))
        assertFalse(VoiceMatch.isAdvance("好好学习"))
        assertFalse(VoiceMatch.isAdvance(""))
    }
}
