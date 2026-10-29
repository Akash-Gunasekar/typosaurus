package com.example.aikeyboard.core.trigger

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TriggerParserTest {
    private val kw = setOf("fix", "tone", "tr", "re")

    @Test fun simpleTrigger() {
        val m = TriggerParser.parse("pls send report tmrw /fix ", kw)!!
        assertEquals("fix", m.keyword); assertEquals("pls send report tmrw", m.body)
        assertEquals(" /fix ".length, m.tokenLength); assertNull(m.arg)
    }

    @Test fun argument() {
        val m = TriggerParser.parse("thanks for the update /tone:formal ", kw)!!
        assertEquals("tone", m.keyword); assertEquals("formal", m.arg)
        assertEquals("good morning", TriggerParser.parse("good morning /tr:ta ", kw)!!.body)
    }

    @Test fun unknownKeywordIgnored() = assertNull(TriggerParser.parse("see /etc ", kw))
    @Test fun needsTrailingSpace() = assertNull(TriggerParser.parse("hello /fix", kw))
    @Test fun emptyBodyIgnored() = assertNull(TriggerParser.parse("/fix ", kw))
    @Test fun urlsNeverFire() = assertNull(TriggerParser.parse("visit example.com/fix ", kw))

    @Test fun multilineBodyAndDoubleSpace() {
        val m = TriggerParser.parse("line one\nline two  /fix ", kw)!!
        assertEquals("line one\nline two", m.body)
        assertEquals("line one\nline two  /fix ".length - m.body.length, m.tokenLength)
    }

    @Test fun customPrefixes() {
        assertEquals("fix", TriggerParser.parse("hi there //fix ", kw, "//")!!.keyword)
        assertEquals("fix", TriggerParser.parse("hi there !fix ", kw, "!")!!.keyword)
        assertNull(TriggerParser.parse("hi there /fix ", kw, "!"))
    }
}
