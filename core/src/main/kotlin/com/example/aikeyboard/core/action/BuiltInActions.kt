package com.example.aikeyboard.core.action

object BuiltInActions {
    private const val ONLY = "Return ONLY the result, with no quotes, notes or explanations."
    private val note = Action.DATA_NOTE

    val fix = Action(
        id = "fix", name = "Fix", icon = "✨", keyword = "fix", builtIn = true, temperature = 0.1,
        systemPrompt = "You are a proofreader. Correct grammar, spelling and punctuation in the user's text. " +
            "Keep the original meaning, language, tone and formatting. Do not add content. $note $ONLY",
    )
    val rephrase = Action(
        id = "rephrase", name = "Rephrase", icon = "↔", keyword = "re", builtIn = true, temperature = 0.7,
        systemPrompt = "Rewrite the user's text with different wording but the same meaning and similar length. " +
            "Keep the language. $note $ONLY",
    )
    val detail = Action(
        id = "detail", name = "Detail", icon = "📝", keyword = "detail", builtIn = true, temperature = 0.6,
        maxTokens = 2048,
        systemPrompt = "Expand the user's text into a clearer, more detailed version. Keep facts unchanged; " +
            "do not invent names, numbers, dates or commitments. Keep the language. $note $ONLY",
    )
    val shorten = Action(
        id = "shorten", name = "Shorten", icon = "✂", keyword = "short", builtIn = true,
        systemPrompt = "Make the user's text concise while keeping all key information and the language. $note $ONLY",
    )
    val tone = Action(
        id = "tone", name = "Tone", icon = "🎭", keyword = "tone", builtIn = true, temperature = 0.5,
        systemPrompt = "Rewrite the user's text in a {tone} tone. Keep the meaning and the language. $note $ONLY",
    )
    val translate = Action(
        id = "translate", name = "Translate", icon = "🌐", keyword = "tr", builtIn = true,
        systemPrompt = "Translate the user's text into {language}. Preserve meaning and formatting. $note " +
            "Return ONLY the translation.",
    )
    val reply = Action(
        id = "reply", name = "Reply", icon = "💬", keyword = "reply", builtIn = true, temperature = 0.6,
        systemPrompt = "Write a short, natural reply to the message in the user's text. " +
            "Match its language and formality. $note Return ONLY the reply.",
    )
    val ask = Action(
        id = "ask", name = "Ask", icon = "🤖", keyword = "ask", builtIn = true, temperature = 0.5,
        maxTokens = 2048, userTemplate = "{text}",
        systemPrompt = "You answer the user's request directly and concisely, in the language of the request. " +
            "Return ONLY the answer, ready to paste into a text field.",
    )

    val all: List<Action> = listOf(fix, rephrase, detail, shorten, tone, translate, reply, ask)
    val ids: Set<String> = all.mapTo(linkedSetOf()) { it.id }
}
