package com.example.aikeyboard.core.runner

import com.example.aikeyboard.core.action.Action
import com.example.aikeyboard.core.action.AppSettings
import kotlinx.coroutines.flow.StateFlow

data class Captured(val text: String, val mode: Mode, val truncated: Boolean = false) {
    enum class Mode { Selection, BeforeCursor, WholeField, Clipboard }
}

/** What the runner needs from the host text field (implemented over InputConnection on Android). */
interface TextPort {
    fun capture(maxChars: Int = 4000): Captured?
    /** Replace the captured region with [result]; false if the field changed underneath. */
    fun replace(captured: Captured, result: String): Boolean
    fun beforeCursor(n: Int): String?
    fun deleteBefore(count: Int)
    fun commit(text: String)
}

interface ClipboardPort {
    /** Only ever called after an explicit user tap. */
    fun read(): String?
    fun write(text: String)
}

interface SettingsSource {
    val settings: StateFlow<AppSettings>
    val actions: StateFlow<List<Action>>
    /** Completes once persisted settings have been read at least once. */
    suspend fun awaitLoaded()
}

sealed interface RunnerState {
    data object Idle : RunnerState
    data class Streaming(val action: Action, val partial: String) : RunnerState
    data class Preview(
        val action: Action,
        val result: String,
        /** The model stopped because it hit the token limit. */
        val truncated: Boolean = false,
        /** "provider · model" shown in the preview panel. */
        val via: String = "",
    ) : RunnerState
    data class Applied(val action: Action, val original: Captured, val result: String) : RunnerState
    data class Failed(
        val message: String,
        val canRetry: Boolean = true,
        /** Offer a shortcut to Settings (bad key, missing model or URL). */
        val openSettings: Boolean = false,
    ) : RunnerState
}
