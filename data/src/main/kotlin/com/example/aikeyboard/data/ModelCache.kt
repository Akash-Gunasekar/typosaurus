package com.example.aikeyboard.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 24-hour cache of each provider's model list (names are never hardcoded). */
class ModelCache(context: Context, private val now: () -> Long = System::currentTimeMillis) {
    private val store = context.applicationContext.modelStore
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable private data class Entry(val at: Long, val models: List<String>)

    private fun key(id: String) = stringPreferencesKey("models_$id")

    suspend fun get(providerId: String, allowStale: Boolean = false): List<String>? {
        val raw = store.data.first()[key(providerId)] ?: return null
        val e = runCatching { json.decodeFromString(Entry.serializer(), raw) }.getOrNull() ?: return null
        return e.models.takeIf { allowStale || now() - e.at < TTL_MS }
    }

    suspend fun put(providerId: String, models: List<String>) {
        store.edit { it[key(providerId)] = json.encodeToString(Entry.serializer(), Entry(now(), models)) }
    }

    suspend fun wipe() { store.edit { it.clear() } }

    companion object { const val TTL_MS = 24L * 60 * 60 * 1000 }
}
