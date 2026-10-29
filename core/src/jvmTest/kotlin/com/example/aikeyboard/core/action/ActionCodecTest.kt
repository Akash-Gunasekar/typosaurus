package com.example.aikeyboard.core.action

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActionCodecTest {
    private val custom = Action("c1", "Pirate", "🏴‍☠️", "arr", "Talk like a pirate. ${Action.DATA_NOTE}")

    @Test fun roundTripSkipsBuiltIns() {
        val json = ActionCodec.export(BuiltInActions.all + custom)
        val r = ActionCodec.import(json, BuiltInActions.all)
        assertEquals(listOf("arr"), r.actions.map { it.keyword })
        assertFalse(r.actions.single().builtIn)
    }

    @Test fun collidingKeywordSkippedAndIdRegenerated() {
        val dup = custom.copy(id = "fix", keyword = "fix")
        val ok = custom.copy(id = "ask", keyword = "pir")
        val r = ActionCodec.import(ActionCodec.export(listOf(dup, ok)), BuiltInActions.all)
        assertEquals(1, r.skipped.size)
        assertTrue(r.actions.single().id != "ask")
    }

    @Test fun invalidKeywordAndClampedValues() {
        val bad = custom.copy(keyword = "Bad Key!")
        val wild = custom.copy(id = "w", keyword = "wild", temperature = 9.0, maxTokens = 10_000_000)
        val r = ActionCodec.import(ActionCodec.export(listOf(bad, wild)), emptyList())
        assertEquals(1, r.skipped.size)
        assertEquals(2.0, r.actions.single().temperature); assertEquals(8192, r.actions.single().maxTokens)
    }

    @Test fun garbageRejected() { assertFailsWith<IllegalArgumentException> { ActionCodec.import("not json", emptyList()) } }
    @Test fun keywordValidation() { assertTrue(ActionCodec.isValidKeyword("re")); assertFalse(ActionCodec.isValidKeyword("1x")) }
}
