package com.example.aikeyboard.ime

import com.example.aikeyboard.core.runner.Captured
import com.example.aikeyboard.core.runner.ClipboardPort
import com.example.aikeyboard.core.runner.TextPort
import kotlinx.coroutines.flow.MutableStateFlow

/** A text "field" held in memory: used by PROCESS_TEXT and the in-app "Try it" box. */
class InMemoryTextPort(initial: String) : TextPort {
    val text = MutableStateFlow(initial)

    override fun capture(maxChars: Int): Captured? = text.value.takeIf { it.isNotBlank() }
        ?.let { Captured(it.take(maxChars), Captured.Mode.Selection, truncated = it.length > maxChars) }

    override fun replace(captured: Captured, result: String): Boolean { text.value = result; return true }
    override fun beforeCursor(n: Int): String = text.value.takeLast(n)
    override fun deleteBefore(count: Int) { text.value = text.value.dropLast(count) }
    override fun commit(text: String) { this.text.value += text }
}

/** Clipboard stand-in that never touches the system clipboard. */
class NoClipboard : ClipboardPort {
    var last: String? = null
    override fun read(): String? = null
    override fun write(text: String) { last = text }
}
