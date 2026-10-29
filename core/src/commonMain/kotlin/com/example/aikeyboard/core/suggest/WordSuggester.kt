package com.example.aikeyboard.core.suggest

/**
 * Word completion over a frequency-ordered word list (most common first). Lookups are a binary search for
 * the prefix plus a scan of its range, so they stay well under a millisecond for a 30k-word list.
 * Nothing is learned or stored: suggestions come only from the bundled list.
 */
class WordSuggester(wordsByFrequency: List<String>) {
    /** Lowercase keys in alphabetical order, with each word's original spelling and frequency rank. */
    private val keys: Array<String>
    private val words: Array<String>
    private val ranks: IntArray

    init {
        val order = wordsByFrequency.indices.sortedBy { wordsByFrequency[it].lowercase() }
        keys = Array(order.size) { wordsByFrequency[order[it]].lowercase() }
        words = Array(order.size) { wordsByFrequency[order[it]] }
        ranks = IntArray(order.size) { order[it] }
    }

    val size: Int get() = keys.size

    /**
     * Up to [limit] completions of [typed], most common first. The typed word itself is left out
     * (it is already on screen); capitalisation follows what was typed ("Th" → "The", "TH" → "THE").
     */
    fun suggest(typed: String, limit: Int = 3): List<String> {
        if (typed.isEmpty() || limit <= 0) return emptyList()
        val prefix = typed.lowercase()
        var i = lowerBound(prefix)
        val best = ArrayList<Int>(limit + 1) // indices into keys, kept sorted by rank
        while (i < keys.size && keys[i].startsWith(prefix)) {
            if (keys[i] != prefix) {
                val pos = best.indexOfFirst { ranks[it] > ranks[i] }.let { if (it < 0) best.size else it }
                if (pos < limit) {
                    best.add(pos, i)
                    if (best.size > limit) best.removeAt(best.lastIndex)
                }
            }
            i++
        }
        return best.map { matchCase(words[it], typed) }
    }

    private fun lowerBound(prefix: String): Int {
        var lo = 0
        var hi = keys.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (keys[mid] < prefix) lo = mid + 1 else hi = mid
        }
        return lo
    }

    companion object {
        private val trailingWord = Regex("""[A-Za-z]+(?:'[A-Za-z]*)?$""")

        /** One word per line, most common first; blank lines and `#` comments are skipped. */
        fun parse(text: String): WordSuggester = WordSuggester(
            text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toList(),
        )

        /** The word being typed at the end of [beforeCursor], or null after a space or punctuation. */
        fun currentWord(beforeCursor: String): String? = trailingWord.find(beforeCursor)?.value

        internal fun matchCase(word: String, typed: String): String = when {
            typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } -> word.uppercase()
            typed.first().isUpperCase() -> word.replaceFirstChar { it.uppercaseChar() }
            else -> word
        }
    }
}
