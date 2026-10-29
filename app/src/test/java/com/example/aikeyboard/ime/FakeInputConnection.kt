package com.example.aikeyboard.ime

import android.view.inputmethod.ExtractedText
import android.view.inputmethod.InputConnection
import java.lang.reflect.Proxy

/** A minimal host text field behind a real [InputConnection] interface (via java.lang.reflect.Proxy). */
class FakeField(var text: String, var selStart: Int = text.length, var selEnd: Int = text.length) {
    var supportsExtraction = true
    var extractionOffset = 0
    var composingStart = -1
    var finishComposingCalls = 0

    val connection: InputConnection = Proxy.newProxyInstance(
        InputConnection::class.java.classLoader, arrayOf(InputConnection::class.java),
    ) { _, m, a ->
        when (m.name) {
            "getSelectedText" -> if (selEnd > selStart) text.substring(selStart, selEnd) else null
            "getTextBeforeCursor" -> text.substring(maxOf(0, selStart - (a[0] as Int)), selStart)
            "getExtractedText" -> if (!supportsExtraction) null else ExtractedText().also {
                it.text = text; it.startOffset = extractionOffset
            }
            "commitText" -> { text = text.replaceRange(selStart, selEnd, a[0].toString()); selStart += a[0].toString().length; selEnd = selStart; true }
            "deleteSurroundingText" -> {
                val n = minOf(a[0] as Int, selStart)
                text = text.removeRange(selStart - n, selStart); selStart -= n; selEnd = selStart; true
            }
            "setSelection" -> { selStart = a[0] as Int; selEnd = a[1] as Int; true }
            "finishComposingText" -> { finishComposingCalls++; true }
            "beginBatchEdit", "endBatchEdit" -> true
            else -> when (m.returnType) { Boolean::class.javaPrimitiveType -> false; Int::class.javaPrimitiveType -> 0; else -> null }
        }
    } as InputConnection
}
