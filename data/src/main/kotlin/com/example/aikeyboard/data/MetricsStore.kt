package com.example.aikeyboard.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.aikeyboard.core.runner.MetricEvent
import com.example.aikeyboard.core.runner.MetricsSink
import com.example.aikeyboard.core.runner.MetricsSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * Local-only usage counters (spec §16.5): latency per provider/model, replace success and undo rate
 * per action, keyboard cold start. Events carry ids and timings only; nothing leaves the device.
 */
class MetricsStore(
    context: Context,
    private val scope: CoroutineScope,
    private val enabled: () -> Boolean,
    private val now: () -> Long = System::currentTimeMillis,
) : MetricsSink {
    private val store = context.applicationContext.metricsStore
    private val json = Json { ignoreUnknownKeys = true }
    private val key = stringPreferencesKey("summary")

    val summary: Flow<MetricsSummary> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { decode(it[key]) }

    override fun record(event: MetricEvent) {
        if (!enabled()) return
        scope.launch {
            runCatching { // a stats write must never break the keyboard
                store.edit { it[key] = json.encodeToString(MetricsSummary.serializer(), decode(it[key]).record(event, now())) }
            }
        }
    }

    suspend fun wipe() { store.edit { it.clear() } }

    private fun decode(raw: String?): MetricsSummary =
        raw?.let { runCatching { json.decodeFromString(MetricsSummary.serializer(), it) }.getOrNull() } ?: MetricsSummary()
}
