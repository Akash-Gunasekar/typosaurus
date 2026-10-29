package com.example.aikeyboard.ime.ui

sealed interface Key {
    data class Char(val value: String) : Key
    data object Backspace : Key
    data object Enter : Key
    data object Space : Key
}

enum class Layer { Letters, Symbols1, Symbols2 }

object Layouts {
    val numberRow: List<String> = "1234567890".map { it.toString() }

    val letters: List<List<String>> = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").map { r -> r.map { it.toString() } }

    val symbols1: List<List<String>> = listOf(
        "1234567890".map { it.toString() },
        listOf("@", "#", "$", "%", "&", "-", "+", "(", ")", "/"),
        listOf("*", "\"", "'", ":", ";", "!", "?"),
    )
    val symbols2: List<List<String>> = listOf(
        listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆"),
        listOf("£", "¢", "€", "¥", "^", "°", "=", "{", "}", "\\"),
        listOf("©", "®", "™", "✓", "[", "]", "<"),
    )

    /** Long-press alternatives. */
    val accents: Map<String, List<String>> = mapOf(
        "a" to listOf("à", "á", "â", "ä", "æ", "ã", "å", "ā"),
        "e" to listOf("è", "é", "ê", "ë", "ē", "ė", "ę"),
        "i" to listOf("ì", "í", "î", "ï", "ī", "į"),
        "o" to listOf("ò", "ó", "ô", "ö", "œ", "ø", "ō", "õ"),
        "u" to listOf("ù", "ú", "û", "ü", "ū"),
        "c" to listOf("ç", "ć", "č"), "n" to listOf("ñ", "ń"), "s" to listOf("ß", "ś", "š"),
        "y" to listOf("ý", "ÿ"), "z" to listOf("ž", "ź", "ż"), "l" to listOf("ł"),
        "." to listOf(",", "?", "!", "…", ";", ":"),
        "-" to listOf("–", "—", "_"),
        "$" to listOf("€", "£", "¥", "¢"),
        "0" to listOf("°"),
        "\"" to listOf("“", "”", "«", "»"), "'" to listOf("‘", "’"),
    )

    /** Tone / language choices for actions that take an argument. */
    val tones = listOf("formal", "friendly", "confident", "polite", "casual")
    val languages = listOf("en", "ta", "hi", "es", "fr", "de", "pt", "ar", "ja", "zh")
}
