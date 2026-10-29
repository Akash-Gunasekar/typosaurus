package com.example.aikeyboard.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.aikeyboard.core.action.Action
import com.example.aikeyboard.core.action.ActionCodec
import com.example.aikeyboard.core.action.AppSettings
import com.example.aikeyboard.core.action.BuiltInActions
import com.example.aikeyboard.core.runner.SettingsSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * Settings and actions live in DataStore as JSON; this class mirrors them into StateFlows so the
 * IME can read them synchronously after [awaitLoaded]. Writes go through DataStore and are
 * reflected back by its Flow, so the UI and the IME (same process) always agree.
 */
class SettingsRepository(context: Context, scope: CoroutineScope) : SettingsSource {
    private val store = context.applicationContext.settingsStore
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val settingsKey = stringPreferencesKey("app_settings")
    private val actionsKey = stringPreferencesKey("actions")
    private val writeLock = Mutex()

    private val _settings = MutableStateFlow(AppSettings())
    private val _actions = MutableStateFlow(BuiltInActions.all)
    private val loaded = MutableStateFlow(false)

    override val settings: StateFlow<AppSettings> = _settings
    override val actions: StateFlow<List<Action>> = _actions
    /** True once persisted settings have been read (UI waits for it before choosing a start screen). */
    val isLoaded: StateFlow<Boolean> = loaded

    init {
        scope.launch {
            store.data.catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }.map { prefs ->
                val s = prefs[settingsKey]?.let { runCatching { json.decodeFromString(AppSettings.serializer(), it).migrated() }.getOrNull() }
                    ?: AppSettings()
                val a = prefs[actionsKey]?.let { runCatching { json.decodeFromString(ACTIONS, it) }.getOrNull() }
                s to mergeBuiltIns(a)
            }.collect { (s, a) ->
                _settings.value = s; _actions.value = a; loaded.value = true
            }
        }
    }

    override suspend fun awaitLoaded() { if (!loaded.value) loaded.first { it } }

    suspend fun update(transform: (AppSettings) -> AppSettings) = writeLock.withLock {
        val next = transform(store.data.first()[settingsKey]?.let { runCatching { json.decodeFromString(AppSettings.serializer(), it).migrated() }.getOrNull() } ?: AppSettings())
        store.edit { it[settingsKey] = json.encodeToString(AppSettings.serializer(), next) }
    }

    private suspend fun updateActions(transform: (List<Action>) -> List<Action>) = writeLock.withLock {
        val current = mergeBuiltIns(store.data.first()[actionsKey]?.let { runCatching { json.decodeFromString(ACTIONS, it) }.getOrNull() })
        store.edit { it[actionsKey] = json.encodeToString(ACTIONS, transform(current)) }
    }

    /** Insert or replace by id, preserving position. Keyword uniqueness is enforced by the UI and checked here. */
    suspend fun saveAction(action: Action): Result<Unit> {
        if (!ActionCodec.isValidKeyword(action.keyword)) return Result.failure(IllegalArgumentException("Keyword must be a-z, 0-9, - or _ (max 20)"))
        var error: Throwable? = null
        updateActions { list ->
            if (list.any { it.id != action.id && it.keyword == action.keyword }) {
                error = IllegalArgumentException("Keyword \"${action.keyword}\" is already used")
                list
            } else if (list.any { it.id == action.id }) list.map { if (it.id == action.id) action else it }
            else list + action
        }
        return error?.let { Result.failure(it) } ?: Result.success(Unit)
    }

    suspend fun deleteAction(id: String) = updateActions { list -> list.filterNot { it.id == id && !it.builtIn } }

    suspend fun move(id: String, delta: Int) = updateActions { list ->
        val i = list.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in list.indices) list else list.toMutableList().apply { add(j, removeAt(i)) }
    }

    suspend fun resetBuiltIn(id: String) = updateActions { list ->
        val original = BuiltInActions.all.firstOrNull { it.id == id } ?: return@updateActions list
        list.map { if (it.id == id) original else it }
    }

    fun exportActions(): String = ActionCodec.export(_actions.value)

    suspend fun importActions(text: String): ActionCodec.ImportResult {
        val result = ActionCodec.import(text, _actions.value)
        if (result.actions.isNotEmpty()) updateActions { it + result.actions }
        return result
    }

    suspend fun wipe() = writeLock.withLock { store.edit { it.clear() } }

    /** Built-ins added in a newer app version appear for existing users; user edits are kept. */
    private fun mergeBuiltIns(saved: List<Action>?): List<Action> {
        if (saved == null) return BuiltInActions.all
        val have = saved.mapTo(hashSetOf()) { it.id }
        return saved + BuiltInActions.all.filter { it.id !in have }
    }

    private companion object {
        val ACTIONS = ListSerializer(Action.serializer())
    }
}
