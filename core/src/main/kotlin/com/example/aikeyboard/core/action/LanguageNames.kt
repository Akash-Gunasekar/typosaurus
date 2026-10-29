package com.example.aikeyboard.core.action

object LanguageNames {
    private val names = mapOf(
        "en" to "English", "ta" to "Tamil", "hi" to "Hindi", "te" to "Telugu", "ml" to "Malayalam",
        "kn" to "Kannada", "mr" to "Marathi", "bn" to "Bengali", "gu" to "Gujarati", "pa" to "Punjabi",
        "ur" to "Urdu", "fr" to "French", "es" to "Spanish", "de" to "German", "it" to "Italian",
        "pt" to "Portuguese", "nl" to "Dutch", "ru" to "Russian", "ja" to "Japanese", "ko" to "Korean",
        "zh" to "Chinese", "ar" to "Arabic", "tr" to "Turkish", "id" to "Indonesian", "th" to "Thai",
        "vi" to "Vietnamese", "pl" to "Polish", "sv" to "Swedish", "el" to "Greek", "he" to "Hebrew",
    )

    /** "ta" -> "Tamil"; a full name such as "tamil" is capitalised; blank -> null. */
    fun resolve(arg: String?): String? {
        val a = arg?.trim()?.lowercase().orEmpty()
        if (a.isEmpty()) return null
        return names[a] ?: a.replaceFirstChar { it.uppercase() }
    }
}
