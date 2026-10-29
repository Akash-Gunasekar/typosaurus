package com.example.aikeyboard.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.aikeyboard.core.provider.ApiKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class ApiKeyRepository(context: Context) : ApiKeys {
    private val store = context.applicationContext.keyStore
    private fun k(id: String) = stringPreferencesKey("key_$id")

    /** Decrypted only for the duration of a request; never cached, logged or exported. */
    override suspend fun get(providerId: String): String? =
        store.data.first()[k(providerId)]?.let(KeyCipher::decrypt)

    suspend fun set(providerId: String, key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return delete(providerId)
        val enc = KeyCipher.encrypt(trimmed)
        store.edit { it[k(providerId)] = enc }
    }

    suspend fun delete(providerId: String) { store.edit { it.remove(k(providerId)) } }

    /** Provider ids that have a stored key (does not decrypt). */
    val configured: Flow<Set<String>> = store.data.map { prefs ->
        prefs.asMap().keys.map { it.name }.filter { it.startsWith("key_") }.map { it.removePrefix("key_") }.toSet()
    }

    /** "sk-…a1b2" style hint for the UI; the full key never reaches the UI layer. */
    suspend fun masked(providerId: String): String? = get(providerId)?.let { mask(it) }

    suspend fun wipe() {
        store.edit { it.clear() }
        KeyCipher.deleteKey()
    }

    companion object {
        fun mask(key: String): String = when {
            key.length <= 8 -> "••••"
            else -> key.take(3) + "…" + key.takeLast(4)
        }
    }
}
