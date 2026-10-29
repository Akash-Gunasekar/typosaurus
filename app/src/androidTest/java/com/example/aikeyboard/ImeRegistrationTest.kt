package com.example.aikeyboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Smoke test: the IME is registered, can be enabled and selected. Run with `./gradlew connectedDebugAndroidTest`. */
@RunWith(AndroidJUnit4::class)
class ImeRegistrationTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    // The debug build has an applicationId suffix, so build the id from the real package name.
    private val imeId = InstrumentationRegistry.getInstrumentation().targetContext.packageName +
        "/com.example.aikeyboard.ime.AiKeyboardService"

    @Test fun keyboardIsListedEnabledAndSelectable() {
        assertTrue("IME not registered:\n" + device.executeShellCommand("ime list -a -s"), device.executeShellCommand("ime list -a -s").contains(imeId))
        device.executeShellCommand("ime enable $imeId")
        assertTrue(device.executeShellCommand("ime list -s").contains(imeId))
        device.executeShellCommand("ime set $imeId")
        assertEquals(imeId, device.executeShellCommand("settings get secure default_input_method").trim())
    }
}
