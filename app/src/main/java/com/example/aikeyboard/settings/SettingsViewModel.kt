package com.example.aikeyboard.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aikeyboard.core.action.Action
import com.example.aikeyboard.core.action.AppSettings
import com.example.aikeyboard.core.provider.LlmException
import com.example.aikeyboard.core.provider.userMessage
import com.example.aikeyboard.core.runner.MetricsSummary
import com.example.aikeyboard.graph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val g = app.graph

    val settings: StateFlow<AppSettings> = g.settings.settings
    val actions: StateFlow<List<Action>> = g.settings.actions
    val configuredKeys: StateFlow<Set<String>> =
        g.keys.configured.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** providerId -> model ids (from the 24 h cache or a refresh). */
    val models = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val busy = MutableStateFlow(false)
    /** Set when the keyboard sent the user here to grant the microphone. */
    val requestMic = MutableStateFlow(false)
    /** One-shot status line (snackbar-style) for the current screen. */
    val message = MutableStateFlow<String?>(null)

    fun update(transform: (AppSettings) -> AppSettings) { viewModelScope.launch { g.settings.update(transform) } }

    // ---- providers -------------------------------------------------------------------------
    suspend fun maskedKey(id: String): String? = g.keys.masked(id)

    fun saveKey(id: String, key: String, done: () -> Unit = {}) {
        viewModelScope.launch { g.keys.set(id, key); done() }
    }

    fun deleteKey(id: String, done: () -> Unit = {}) {
        viewModelScope.launch { g.keys.delete(id); done() }
    }

    fun loadCachedModels(id: String) {
        viewModelScope.launch { g.modelCache.get(id, allowStale = true)?.let { models.value = models.value + (id to it) } }
    }

    /** "Test connection": lists models, which also refreshes the picker. */
    fun testConnection(id: String) {
        viewModelScope.launch {
            busy.value = true
            message.value = try {
                val list = g.registry.get(id).listModels()
                g.modelCache.put(id, list)
                models.value = models.value + (id to list)
                "Connected — ${list.size} models available"
            } catch (e: LlmException) {
                e.userMessage()
            } catch (e: Exception) {
                "Could not connect"
            } finally {
                busy.value = false
            }
        }
    }

    // ---- actions ---------------------------------------------------------------------------
    fun saveAction(a: Action, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch { onResult(g.settings.saveAction(a)) }
    }
    fun deleteAction(id: String) { viewModelScope.launch { g.settings.deleteAction(id) } }
    fun moveAction(id: String, delta: Int) { viewModelScope.launch { g.settings.move(id, delta) } }
    fun resetBuiltIn(id: String) { viewModelScope.launch { g.settings.resetBuiltIn(id) } }
    fun exportJson(): String = g.settings.exportActions()

    fun importJson(text: String) {
        viewModelScope.launch {
            message.value = try {
                val r = g.settings.importActions(text)
                buildString {
                    append("Imported ${r.actions.size} shortcut(s)")
                    if (r.skipped.isNotEmpty()) append(" · skipped ${r.skipped.size}: ${r.skipped.first()}")
                }
            } catch (e: IllegalArgumentException) {
                e.message ?: "Import failed"
            }
        }
    }

    fun newRunnerFor(text: com.example.aikeyboard.core.runner.TextPort, scope: kotlinx.coroutines.CoroutineScope) =
        g.newRunner(scope, text, com.example.aikeyboard.ime.NoClipboard())

    // ---- diagnostics -----------------------------------------------------------------------
    val metrics: StateFlow<MetricsSummary> = g.metrics.summary.stateIn(viewModelScope, SharingStarted.Eagerly, MetricsSummary())
    fun clearMetrics() { viewModelScope.launch { g.metrics.wipe() } }

    // ---- privacy ---------------------------------------------------------------------------
    /** Deletes every key, setting and cached list, and the Keystore alias. */
    fun resetEverything(done: () -> Unit) {
        viewModelScope.launch {
            g.keys.wipe(); g.settings.wipe(); g.modelCache.wipe(); g.metrics.wipe()
            done()
        }
    }
}
