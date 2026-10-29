package com.example.aikeyboard.ime

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import com.example.aikeyboard.ime.ui.ClipItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Recent clipboard entries for the keyboard's clipboard panel. In memory only: nothing is written to
 * disk or sent anywhere, and the list is gone when the keyboard process stops. Clips the source app
 * marked sensitive (passwords, OTPs), and text that looks like an API key or token, are never kept;
 * the current one is only shown masked.
 */
class ClipboardHistory(private val context: Context, private val enabled: () -> Boolean) {
    private val cm get() = context.getSystemService(ClipboardManager::class.java)
    private val _items = MutableStateFlow<List<ClipItem>>(emptyList())
    val items: StateFlow<List<ClipItem>> = _items

    private val listener = ClipboardManager.OnPrimaryClipChangedListener { if (enabled()) capture(remember = true) }

    fun start() { cm?.addPrimaryClipChangedListener(listener) }
    fun stop() { cm?.removePrimaryClipChangedListener(listener) }

    /** Explicit panel open: make sure the current clip is at the top. */
    fun refresh() = capture(remember = enabled())

    fun clear() { _items.value = emptyList() }

    fun remove(item: ClipItem) { _items.value = _items.value - item }

    private fun capture(remember: Boolean) {
        val clip = runCatching { cm?.primaryClip }.getOrNull() ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).coerceToText(context)?.toString()?.take(MAX_CHARS)?.takeIf { it.isNotBlank() } ?: return
        val item = ClipItem(text, sensitive = clip.description.isSensitive() || looksSecret(text))
        // A sensitive clip sits on top (masked) only until the next capture drops it.
        val others = if (remember) _items.value.filterNot { it.text == text || it.sensitive } else emptyList()
        _items.value = (listOf(item) + others).take(MAX_ITEMS)
    }

    private fun ClipDescription.isSensitive(): Boolean {
        val extras: PersistableBundle = extras ?: return false
        val key = if (Build.VERSION.SDK_INT >= 33) ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE"
        return extras.getBoolean(key, false)
    }

    companion object {
        private const val MAX_ITEMS = 20
        private const val MAX_CHARS = 5_000

        private val keyPrefixes = Regex(
            """^(sk-|sk_|rk_|pk_live|gsk_|xai-|AIza|ghp_|gho_|github_pat_|glpat-|xox[abpr]-|ya29\.|eyJ|Bearer\s)""",
        )

        /** API keys, access tokens, JWTs: well-known prefixes, or one long unbroken mixed letter/digit token. */
        fun looksSecret(text: String): Boolean {
            val t = text.trim()
            if (keyPrefixes.containsMatchIn(t)) return true
            if (t.length < 24 || t.any { it.isWhitespace() }) return false
            return t.any { it.isDigit() } && t.any { it.isLetter() } && t.all { it.isLetterOrDigit() || it in "-_.=+/" }
        }
    }
}
