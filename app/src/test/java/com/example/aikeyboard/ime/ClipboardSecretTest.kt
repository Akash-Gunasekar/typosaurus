package com.example.aikeyboard.ime

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClipboardSecretTest {
    @Test fun apiKeysAndTokensAreSecret() {
        listOf(
            "sk-ant-api03-AbCdEfGh1234567890",
            "sk-proj-abc123",
            "gsk_0123456789abcdefABCDEF",
            "AIzaSyD-0123456789abcdefghij",
            "ghp_abcdefghijklmnopqrstuvwxyz0123",
            "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.sig",
            "  Bearer abc.def  ",
            "a8F3kd92LmQp0zXv7Bn4Ty6Wc1Re5Uh", // long unprefixed token
        ).forEach { assertTrue(ClipboardHistory.looksSecret(it), it) }
    }

    @Test fun ordinaryTextIsNot() {
        listOf(
            "See you at 5pm tomorrow",
            "https://example.com/some/page path",
            "teh cat sat",
            "1234567890",
            "supercalifragilisticexpialidocious", // long but no digits
            "Order #A12B34 is on the way, track it in the app",
        ).forEach { assertFalse(ClipboardHistory.looksSecret(it), it) }
    }
}
