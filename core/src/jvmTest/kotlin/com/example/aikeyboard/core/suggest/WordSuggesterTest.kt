package com.example.aikeyboard.core.suggest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WordSuggesterTest {
    private val s = WordSuggester.parse(
        """
        # most common first
        the
        that
        this
        there
        they
        I
        I'm
        thanks
        report
        really
        important
        """.trimIndent(),
    )

    @Test fun completesByFrequency() {
        assertEquals(listOf("the", "that", "this"), s.suggest("th"))
        assertEquals(listOf("there", "they"), s.suggest("the"), "the typed word itself is not repeated")
        assertEquals(listOf("report"), s.suggest("rep"))
    }

    @Test fun followsTypedCapitalisation() {
        assertEquals(listOf("The", "That", "This"), s.suggest("Th"))
        assertEquals(listOf("THE", "THAT", "THIS"), s.suggest("TH"))
        assertEquals(listOf("I'm", "important"), s.suggest("i"), "the pronoun keeps its capital")
    }

    @Test fun nothingForUnknownOrEmpty() {
        assertTrue(s.suggest("xyz").isEmpty())
        assertTrue(s.suggest("").isEmpty())
        assertEquals(listOf("the"), s.suggest("th", limit = 1))
    }

    @Test fun currentWordIsTheTrailingWord() {
        assertEquals("tmr", WordSuggester.currentWord("pls send report tmr"))
        assertEquals("I'm", WordSuggester.currentWord("yes I'm"))
        assertEquals(null, WordSuggester.currentWord("hello "))
        assertEquals(null, WordSuggester.currentWord("done."))
        assertEquals(null, WordSuggester.currentWord(""))
    }

    @Test fun parsesTheBundledFormatQuickly() {
        val big = WordSuggester((0 until 30_000).map { "w$it" })
        assertEquals(30_000, big.size)
        assertEquals(listOf("w10", "w11", "w12"), big.suggest("w1"), "w1 itself is already typed")
    }
}
