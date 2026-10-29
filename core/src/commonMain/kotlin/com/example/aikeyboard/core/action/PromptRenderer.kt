package com.example.aikeyboard.core.action

object PromptRenderer {
    private val placeholder = Regex("""\{(\w+)\}""")

    /**
     * Single-pass substitution: values are never re-scanned, so user text that
     * itself contains "{arg}" or "{tone}" cannot be rewritten.
     */
    fun fill(template: String, vars: Map<String, String>): String =
        placeholder.replace(template) { m -> vars[m.groupValues[1]] ?: m.value }

    fun render(action: Action, vars: Map<String, String>): Pair<String, String> =
        fill(action.systemPrompt, vars) to fill(action.userTemplate, vars)

    private val preamble = Regex(
        """^(?:sure|certainly|of course|okay|ok)?[,!. ]*(?:here(?:'s| is| are)|below is)\b[^:\n]{0,60}:[ \t]*\n?""",
        RegexOption.IGNORE_CASE,
    )
    private val wrapperTags = Regex("""^\s*<(output|text|result)>\s*([\s\S]*?)\s*</\1>\s*$""", RegexOption.IGNORE_CASE)
    private val quotePairs = listOf('"' to '"', '“' to '”', '\'' to '\'', '«' to '»')

    /**
     * Safety net for chatty models. [input] is the text that was sent, so legitimate
     * wrapping quotes or a leading "Here is" that the user wrote are preserved.
     */
    fun clean(output: String, input: String = ""): String {
        var s = output.trim()
        wrapperTags.find(s)?.let { s = it.groupValues[2] }
        if (!preamble.containsMatchIn(input.trim())) s = s.replace(preamble, "").trim()
        val inputTrim = input.trim()
        for ((open, close) in quotePairs) {
            val inputQuoted = inputTrim.length > 1 && inputTrim.first() == open && inputTrim.last() == close
            if (!inputQuoted && s.length > 1 && s.first() == open && s.last() == close &&
                s.substring(1, s.length - 1).none { it == open || it == close }
            ) {
                s = s.substring(1, s.length - 1).trim()
                break
            }
        }
        return s
    }
}
