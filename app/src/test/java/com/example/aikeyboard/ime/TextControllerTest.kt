package com.example.aikeyboard.ime

import com.example.aikeyboard.core.runner.Captured
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TextControllerTest {
    private fun ctl(f: FakeField) = TextController { f.connection }

    @Test fun selectionWinsAndIsReplaced() {
        val f = FakeField("hello teh world", 6, 9)
        val c = ctl(f).capture()!!
        assertEquals(Captured("teh", Captured.Mode.Selection), c)
        assertTrue(ctl(f).replace(c, "the"))
        assertEquals("hello the world", f.text)
    }

    @Test fun selectionReplaceRefusedIfSelectionChanged() {
        val f = FakeField("hello teh world", 6, 9)
        val t = ctl(f); val c = t.capture()!!
        f.selStart = 0; f.selEnd = 5
        assertFalse(t.replace(c, "the"))
        assertEquals("hello teh world", f.text)
    }

    @Test fun wholeFieldUsedWhenNoSelection() {
        val f = FakeField("teh cat sat", selStart = 3, selEnd = 3)
        val t = ctl(f); val c = t.capture()!!
        assertEquals(Captured("teh cat sat", Captured.Mode.WholeField), c)
        assertTrue(t.replace(c, "The cat sat."))
        assertEquals("The cat sat.", f.text)
    }

    @Test fun wholeFieldRefusedWhenFieldChanged() {
        val f = FakeField("teh cat")
        val t = ctl(f); val c = t.capture()!!
        f.text = "teh cat!!"; f.selStart = f.text.length; f.selEnd = f.selStart
        assertFalse(t.replace(c, "x"))
    }

    @Test fun fallsBackToBeforeCursorWithoutExtraction() {
        val f = FakeField("some webview text").apply { supportsExtraction = false }
        assertEquals(Captured("some webview text", Captured.Mode.BeforeCursor), ctl(f).capture())
    }

    @Test fun partialExtractionIsNotTreatedAsWholeField() {
        val f = FakeField("tail of a long note").apply { extractionOffset = 500 }
        assertEquals(Captured.Mode.BeforeCursor, ctl(f).capture()!!.mode)
    }

    @Test fun longFieldIsCappedAndFlaggedTruncated() {
        val f = FakeField("x".repeat(5000))
        val c = ctl(f).capture(maxChars = 4000)!!
        assertEquals(Captured.Mode.BeforeCursor, c.mode)
        assertEquals(4000, c.text.length); assertTrue(c.truncated)
    }

    @Test fun beforeCursorReplaceDetectsUserTyping() {
        val f = FakeField("pls send report")
        val t = ctl(f); val c = Captured("pls send report", Captured.Mode.BeforeCursor)
        f.text += " now"; f.selStart = f.text.length; f.selEnd = f.selStart
        assertFalse(t.replace(c, "Please send the report."))
        assertEquals("pls send report now", f.text)
    }

    @Test fun beforeCursorReplaceWorksAndKeepsTextAfterCursor() {
        val f = FakeField("pls send report TAIL", selStart = 15, selEnd = 15)
        assertTrue(ctl(f).replace(Captured("pls send report", Captured.Mode.BeforeCursor), "Please send the report."))
        assertEquals("Please send the report. TAIL", f.text)
    }

    @Test fun finishesComposingBeforeReplacing() {
        val f = FakeField("teh", 0, 3)
        ctl(f).replace(Captured("teh", Captured.Mode.Selection), "the")
        assertEquals(1, f.finishComposingCalls)
    }

    @Test fun clipboardModeInsertsAtCursor() {
        val f = FakeField("")
        assertTrue(ctl(f).replace(Captured("q?", Captured.Mode.Clipboard), "Sure."))
        assertEquals("Sure.", f.text)
    }

    @Test fun emptyOrNullConnection() {
        assertNull(TextController { null }.capture())
        assertNull(ctl(FakeField("   ")).capture())
        assertFalse(TextController { null }.replace(Captured("a", Captured.Mode.Selection), "b"))
    }
}
