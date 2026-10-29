package com.example.aikeyboard.core.trigger

import com.example.aikeyboard.core.action.AppSettings

data class TriggerMatch(
    val keyword: String,
    val arg: String?,
    /** Text preceding the trigger token, without the whitespace that separated them. */
    val body: String,
    /** Characters to delete before the cursor: separating whitespace + token + the typed space. */
    val tokenLength: Int,
)

object TriggerParser {
    // The token must start at the beginning of the text or after whitespace, so URLs
    // such as "example.com/fix " never fire.
    private fun build(prefix: String): Regex {
        val p = Regex.escape(prefix)
        return Regex("""(?s)^(.*?)\s*((?<!\S)$p([a-z][a-z0-9_-]{0,19})(?::([\w-]{1,20}))?) $""")
    }

    /** Prebuilt for the prefixes Settings offers; immutable, so safe from any thread. */
    private val prebuilt: Map<String, Regex> = AppSettings.ALLOWED_PREFIXES.associateWith(::build)

    private fun regexFor(prefix: String): Regex = prebuilt[prefix] ?: build(prefix)

    fun parse(beforeCursor: String, knownKeywords: Set<String>, prefix: String = "/"): TriggerMatch? {
        if (prefix.isEmpty() || !beforeCursor.endsWith(" ")) return null
        val m = regexFor(prefix).find(beforeCursor) ?: return null
        val keyword = m.groupValues[3]
        if (keyword !in knownKeywords) return null // "/etc " in ordinary text is ignored
        val body = m.groupValues[1]
        if (body.isBlank()) return null
        return TriggerMatch(
            keyword = keyword,
            arg = m.groupValues[4].ifEmpty { null },
            body = body,
            tokenLength = beforeCursor.length - body.length,
        )
    }
}
