package com.example.aikeyboard.core.action

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Export / import of custom actions as JSON. */
object ActionCodec {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    private val keywordRegex = Regex("[a-z][a-z0-9_-]{0,19}")
    const val SCHEMA_VERSION = 1

    @Serializable
    private data class Bundle(val schemaVersion: Int = SCHEMA_VERSION, val actions: List<Action>)

    class ImportResult(val actions: List<Action>, val skipped: List<String>)

    fun isValidKeyword(k: String) = keywordRegex.matches(k)

    fun export(actions: List<Action>): String =
        json.encodeToString(Bundle.serializer(), Bundle(actions = actions.filter { !it.builtIn }))

    /**
     * Imported actions are always custom (builtIn=false). Ids and keywords that
     * collide with [existing] are skipped (keyword) or regenerated (id).
     */
    fun import(text: String, existing: List<Action>): ImportResult {
        val bundle = try {
            json.decodeFromString(Bundle.serializer(), text)
        } catch (e: Exception) {
            throw IllegalArgumentException("Not a valid shortcut file", e)
        }
        require(bundle.schemaVersion <= SCHEMA_VERSION) { "File was made by a newer app version" }
        val ids = existing.mapTo(hashSetOf()) { it.id }
        val keywords = existing.mapTo(hashSetOf()) { it.keyword }
        val accepted = mutableListOf<Action>()
        val skipped = mutableListOf<String>()
        for (a in bundle.actions) {
            val kw = a.keyword.lowercase()
            when {
                a.name.isBlank() || a.systemPrompt.isBlank() -> skipped += "${a.name.ifBlank { "(unnamed)" }}: empty name or prompt"
                !isValidKeyword(kw) -> skipped += "${a.name}: invalid keyword \"${a.keyword}\""
                kw in keywords -> skipped += "${a.name}: keyword \"$kw\" already used"
                else -> {
                    var id = a.id
                    while (id in ids || id in BuiltInActions.ids) id = "c_" + java.util.UUID.randomUUID().toString().take(8)
                    ids += id; keywords += kw
                    accepted += a.copy(
                        id = id, keyword = kw, builtIn = false,
                        temperature = a.temperature.coerceIn(0.0, 2.0),
                        maxTokens = a.maxTokens.coerceIn(16, 8192),
                    )
                }
            }
        }
        return ImportResult(accepted, skipped)
    }
}
