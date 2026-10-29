package com.example.aikeyboard.core

import com.example.aikeyboard.core.provider.DarwinHttpEngine
import com.example.aikeyboard.core.runner.Assistant
import kotlinx.coroutines.MainScope

/**
 * Swift entry point: `IosEntryKt.createAssistant(settingsJson:actionsJson:keyLookup:)`.
 * Callbacks arrive on the main queue, so the keyboard UI can update directly.
 */
fun createAssistant(settingsJson: String?, actionsJson: String?, keyLookup: (String) -> String?): Assistant =
    Assistant(settingsJson, actionsJson, keyLookup, MainScope(), DarwinHttpEngine())
