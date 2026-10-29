package com.example.aikeyboard.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class FieldPolicyTest {
    private val text = InputType.TYPE_CLASS_TEXT
    private fun eval(type: Int, ime: Int = 0, pkg: String? = "com.some.app", blocked: Set<String> = emptySet(), unlocked: Boolean = true) =
        FieldPolicy.evaluate(type, ime, pkg, blocked, unlocked)
    private fun blocked(r: FieldPolicy.Reason) = FieldPolicy.AiBlocked(r)

    @Test fun plainTextAllowed() {
        assertEquals(FieldPolicy.AiAllowed, eval(text))
        assertEquals(FieldPolicy.AiAllowed, eval(text or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT))
        assertEquals(FieldPolicy.AiAllowed, eval(text or InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE))
    }

    @Test fun allPasswordVariationsBlocked() {
        for (v in listOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
            assertEquals(blocked(FieldPolicy.Reason.Password), eval(text or v))
        assertEquals(blocked(FieldPolicy.Reason.Password), eval(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD))
    }

    @Test fun incognitoBlocked() =
        assertEquals(blocked(FieldPolicy.Reason.Incognito), eval(text, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING))

    @Test fun nonTextBlocked() {
        for (c in listOf(InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME, InputType.TYPE_NULL))
            assertEquals(blocked(FieldPolicy.Reason.NonText), eval(c))
    }

    @Test fun blocklistsAndLock() {
        assertEquals(blocked(FieldPolicy.Reason.BlockedApp), eval(text, blocked = setOf("com.bank.app"), pkg = "com.bank.app"))
        assertEquals(blocked(FieldPolicy.Reason.BlockedApp), eval(text, pkg = "com.x8bit.bitwarden"))
        assertEquals(blocked(FieldPolicy.Reason.Locked), eval(text, unlocked = false))
        assertEquals(FieldPolicy.AiAllowed, eval(text, pkg = null))
    }
}
