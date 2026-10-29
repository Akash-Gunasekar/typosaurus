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
    /** Accent in dark mode (light mode always uses slate blue). */
    val accentColor: Long = NEON_TEAL,
    /** The keyboard follows the phone's light/dark mode by default, in its own sky-blue palette. */
    val keyboardTheme: Theme = Theme.System,
    val keyboardAccent: Long = NEON_TEAL,
    val haptics: Boolean = true,
    val keySound: Boolean = false,
    /** Letter pop-up above the key while it is pressed. */
    val keyPopup: Boolean = true,
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
    /** Bumped when look-and-feel defaults change; see [migrated]. */
    val styleVersion: Int = 0,
) {
    enum class Theme { System, Light, Dark }

    fun modelFor(providerId: String): String? = defaultModels[providerId]?.takeIf { it.isNotBlank() }
    fun quirksFor(providerId: String, model: String): ModelQuirks =
        modelQuirks["$providerId:$model"] ?: ModelQuirks.defaultsFor(providerId, model)

    /** Move installs from before the Typosaurus rebrand onto the brand theme, once. */
    fun migrated(): AppSettings =
        if (styleVersion >= STYLE_VERSION) this
        else copy(theme = Theme.System, accentColor = NEON_TEAL, keyboardAccent = NEON_TEAL, styleVersion = STYLE_VERSION)

    companion object {
        const val NEON_TEAL = 0xFF00E676
        const val STYLE_VERSION = 3 // 3: minimalist light / stealth dark palette, follows the phone
        val ALLOWED_PREFIXES = listOf("/", "//", "!", "\\")
    }
}
