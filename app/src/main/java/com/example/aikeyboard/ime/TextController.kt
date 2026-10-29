package com.example.aikeyboard.ime

import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import com.example.aikeyboard.core.runner.Captured
import com.example.aikeyboard.core.runner.TextPort

/**
 * [TextPort] over a host app's InputConnection. Every call tolerates null / partial data, which
 * host apps return freely (see the compatibility table in the docs).
 */
class TextController(private val ic: () -> InputConnection?) : TextPort {

    override fun commit(text: String) { ic()?.commitText(text, 1) }

    override fun beforeCursor(n: Int): String? = ic()?.getTextBeforeCursor(n, 0)?.toString()

    override fun deleteBefore(count: Int) { ic()?.deleteSurroundingText(count, 0) }

    override fun capture(maxChars: Int): Captured? {
        val conn = ic() ?: return null

        conn.getSelectedText(0)?.toString()?.takeIf { it.isNotBlank() }
            ?.let { return Captured(it.take(maxChars), Captured.Mode.Selection, truncated = it.length > maxChars) }

        val full = wholeField(conn)
        if (full != null && full.isNotBlank() && full.length <= maxChars) {
            return Captured(full, Captured.Mode.WholeField)
        }
        val before = conn.getTextBeforeCursor(maxChars, 0)?.toString()
        return before?.takeIf { it.isNotBlank() }
            ?.let { Captured(it, Captured.Mode.BeforeCursor, truncated = it.length >= maxChars || (full?.length ?: 0) > maxChars) }
    }

    /** The complete field text, or null when the host can't provide all of it (WebViews, partial extraction). */
    private fun wholeField(conn: InputConnection): String? {
        val ex = conn.getExtractedText(ExtractedTextRequest(), 0) ?: return null
        if (ex.startOffset != 0 || ex.partialStartOffset > 0) return null
        return ex.text?.toString()
    }

    override fun replace(captured: Captured, result: String): Boolean {
        val conn = ic() ?: return false
        conn.finishComposingText() // some apps re-apply composing text and would duplicate the result
        conn.beginBatchEdit()
        try {
            return when (captured.mode) {
                Captured.Mode.Selection -> {
                    if (conn.getSelectedText(0)?.toString() != captured.text) return false
                    conn.commitText(result, 1) // replaces the selection
                }
                Captured.Mode.BeforeCursor -> {
                    val now = conn.getTextBeforeCursor(captured.text.length, 0)?.toString()
                    if (now != captured.text) return false // user kept typing
                    conn.deleteSurroundingText(captured.text.length, 0)
                    conn.commitText(result, 1)
                }
                Captured.Mode.WholeField -> {
                    if (wholeField(conn) != captured.text) return false
                    conn.setSelection(0, captured.text.length)
                    conn.commitText(result, 1)
                }
                Captured.Mode.Clipboard -> conn.commitText(result, 1)
            }
        } finally {
            conn.endBatchEdit()
        }
    }
}
