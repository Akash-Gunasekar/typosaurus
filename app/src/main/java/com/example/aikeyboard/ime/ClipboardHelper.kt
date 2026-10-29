package com.example.aikeyboard.ime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.example.aikeyboard.core.runner.ClipboardPort

class ClipboardHelper(private val context: Context) : ClipboardPort {
    private val cm get() = context.getSystemService(ClipboardManager::class.java)

    /** Called only after an explicit "Use clipboard" tap, and only if the setting is enabled. */
    override fun read(): String? =
        cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()

    override fun write(text: String) {
        cm?.setPrimaryClip(ClipData.newPlainText("AI result", text))
    }
}
