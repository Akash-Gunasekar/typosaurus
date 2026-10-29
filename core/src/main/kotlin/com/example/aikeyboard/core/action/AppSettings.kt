package com.example.aikeyboard.core.action

import com.example.aikeyboard.core.provider.ModelQuirks
import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val defaultProviderId: String = "openai",
    /** providerId -> model id */
    val defaultModels: Map<String, String> = emptyMap(),
    val fallbackProviderId: String? = null,
    val streaming: Boolean = true,

    val triggersEnabled: Boolean = true,
    val triggerPrefix: String = "/",
    val defaultTone: String = "friendly",
    val defaultLanguage: String = "English",

    val clipboardAccess: Boolean = false,
    /** Keep content-free latency / undo counters on this device (Settings › Diagnostics). */
    val usageStats: Boolean = true,
    val blockedPackages: Set<String> = emptySet(),

    /** providerId -> base URL override (Custom, Ollama, self-hosted gateways). */
    val baseUrls: Map<String, String> = emptyMap(),
    /** "providerId:model" -> parameter quirks. */
    val modelQuirks: Map<String, ModelQuirks> = emptyMap(),

    val theme: Theme = Theme.System,
    val accentColor: Long = 0xFF3F51B5,
    val haptics: Boolean = true,
    val keySound: Boolean = false,
    val numberRow: Boolean = true,
    val heightPercent: Int = 100,
    val longPressDelayMs: Int = 350,
    /** Toolbar buttons (Settings › Keyboard). */
    val toolbarEmoji: Boolean = true,
    val toolbarClipboard: Boolean = true,
    val toolbarVoice: Boolean = true,
    /** Keep text copied while this keyboard is active in an in-memory list (never saved, never sent). */
    val clipboardHistory: Boolean = true,
    val onboardingDone: Boolean = false,
    /** The user accepted the "text is sent to your chosen AI provider" disclosure. Nothing is sent before this. */
    val consentGiven: Boolean = false,
) {
    enum class Theme { System, Light, Dark }

    fun modelFor(providerId: String): String? = defaultModels[providerId]?.takeIf { it.isNotBlank() }
    fun quirksFor(providerId: String, model: String): ModelQuirks =
        modelQuirks["$providerId:$model"] ?: ModelQuirks.defaultsFor(providerId, model)

    companion object {
        val ALLOWED_PREFIXES = listOf("/", "//", "!", "\\")
    }
}
