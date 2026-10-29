package com.example.aikeyboard.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ApiKeyMaskTest {
    @Test fun masksMiddleOfLongKeys() {
        val m = ApiKeyRepository.mask("sk-abcdefghijklmnopqrstuvwxyz-a1b2")
        assertEquals("sk-…a1b2", m)
        assertFalse("abcdef" in m)
    }

    @Test fun shortKeysAreFullyHidden() = assertEquals("••••", ApiKeyRepository.mask("short"))
}
