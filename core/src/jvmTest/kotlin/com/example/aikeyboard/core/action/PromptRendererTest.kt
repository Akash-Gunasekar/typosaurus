package com.example.aikeyboard.core.action

import kotlin.test.Test
import kotlin.test.assertEquals

class PromptRendererTest {
    @Test fun singlePassSubstitution() {
        val out = PromptRenderer.fill("{text} / {tone}", mapOf("text" to "I like {tone} and \$1", "tone" to "formal"))
        assertEquals("I like {tone} and \$1 / formal", out)
    }

    @Test fun unknownPlaceholdersKept() = assertEquals("a {nope}", PromptRenderer.fill("{x} {nope}", mapOf("x" to "a")))

    @Test fun stripsPreamble() = assertEquals("Hello there", PromptRenderer.clean("Here's the corrected text:\nHello there"))
    @Test fun keepsUserOwnPreamble() =
        assertEquals("Here is the plan: go", PromptRenderer.clean("Here is the plan: go", input = "here is the plan: goo"))
    @Test fun doesNotEatRealResultWord() = assertEquals("Result of the audit: fine", PromptRenderer.clean("Result of the audit: fine"))
    @Test fun stripsWrappingQuotes() = assertEquals("Hi", PromptRenderer.clean("\"Hi\""))
    @Test fun keepsQuotesUserTyped() = assertEquals("\"Hi\"", PromptRenderer.clean("\"Hi\"", input = "\"hi\""))
    @Test fun keepsInnerQuotes() = assertEquals("\"a\" and \"b\"", PromptRenderer.clean("\"a\" and \"b\""))
    @Test fun stripsOutputTags() = assertEquals("done", PromptRenderer.clean("<output>\ndone\n</output>"))
}
