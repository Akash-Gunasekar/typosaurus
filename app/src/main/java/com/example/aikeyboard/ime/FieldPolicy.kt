package com.example.aikeyboard.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo

/** Whether AI actions may run in the focused field. The keyboard always types normally. */
sealed interface FieldPolicy {
    data object Unknown : FieldPolicy
    data object AiAllowed : FieldPolicy
    data class AiBlocked(val reason: Reason) : FieldPolicy

    enum class Reason(val label: String) {
        Password("Password field"),
        Incognito("Private / incognito field"),
        NonText("Not a text field"),
        BlockedApp("App is on your blocklist"),
        Locked("Unlock your phone to use AI"),
    }

    companion object {
        /** Starter blocklist (password managers, authenticators); users extend it in Settings > Privacy. */
        val DEFAULT_BLOCKED_PACKAGES = setOf(
            "com.x8bit.bitwarden", "com.bitwarden.authenticator", "com.agilebits.onepassword",
            "com.lastpass.lpandroid", "com.google.android.apps.authenticator2",
            "com.authy.authy", "com.keepersecurity.passwordmanager",
        )

        fun from(info: EditorInfo, userBlocked: Set<String>, unlocked: Boolean = true): FieldPolicy =
            evaluate(info.inputType, info.imeOptions, info.packageName, userBlocked, unlocked)

        /** Pure function so it can be unit-tested without an EditorInfo. */
        fun evaluate(
            inputType: Int,
            imeOptions: Int,
            packageName: String?,
            userBlocked: Set<String>,
            unlocked: Boolean = true,
        ): FieldPolicy {
            if (!unlocked) return AiBlocked(Reason.Locked)
            val cls = inputType and InputType.TYPE_MASK_CLASS
            val variation = inputType and InputType.TYPE_MASK_VARIATION
            val isPassword = when (cls) {
                InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
                else -> false
            }
            return when {
                isPassword -> AiBlocked(Reason.Password)
                imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0 -> AiBlocked(Reason.Incognito)
                cls != InputType.TYPE_CLASS_TEXT -> AiBlocked(Reason.NonText)
                packageName != null && (packageName in userBlocked || packageName in DEFAULT_BLOCKED_PACKAGES) ->
                    AiBlocked(Reason.BlockedApp)
                else -> AiAllowed
            }
        }
    }
}

val FieldPolicy.aiAllowed: Boolean get() = this is FieldPolicy.AiAllowed
