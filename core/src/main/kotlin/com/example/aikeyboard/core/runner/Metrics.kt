package com.example.aikeyboard.core.runner

import com.example.aikeyboard.core.provider.LlmException
import kotlinx.serialization.Serializable

/**
 * Content-free usage events (spec §14, §16.5): ids, error kinds, HTTP codes and timings only.
 * Never add typed text, prompts, results or keys here.
 */
sealed interface MetricEvent {
    /** One action run that reached a provider. [errorKind] is null on success. */
    data class Request(
        val providerId: String,
        val model: String,
        val actionId: String,
        val firstTokenMs: Long?,
        val totalMs: Long,
        val errorKind: LlmException.Kind? = null,
        val httpCode: Int? = null,
    ) : MetricEvent

    /** A result was written into the field ([success]) or fell back to the clipboard. */
    data class Replace(val actionId: String, val success: Boolean) : MetricEvent

    data class Undo(val actionId: String) : MetricEvent

    /** Keyboard service created → first input view drawn. */
    data class ColdStart(val ms: Long) : MetricEvent
}

fun interface MetricsSink {
    fun record(event: MetricEvent)

    companion object {
        val None = MetricsSink { }
    }
}

@Serializable
data class Timing(val count: Int = 0, val sumMs: Long = 0, val maxMs: Long = 0) {
    val avgMs: Long get() = if (count == 0) 0 else sumMs / count
    operator fun plus(ms: Long) = Timing(count + 1, sumMs + ms, maxOf(maxMs, ms))
}

@Serializable
data class ModelStats(
    val ok: Int = 0,
    /** "RateLimit 429" / "Network" -> count. */
    val errors: Map<String, Int> = emptyMap(),
    val firstToken: Timing = Timing(),
    val total: Timing = Timing(),
) {
    val requests: Int get() = ok + errors.values.sum()
}

@Serializable
data class ActionStats(
    val runs: Int = 0,
    val applied: Int = 0,
    val replaceFailed: Int = 0,
    val undone: Int = 0,
) {
    /** Accepted results that landed in the field rather than the clipboard. */
    val replaceSuccessRate: Double? get() = (applied + replaceFailed).takeIf { it > 0 }?.let { applied.toDouble() / it }
    /** Proxy for bad outputs: how often an applied result was undone. */
    val undoRate: Double? get() = applied.takeIf { it > 0 }?.let { undone.toDouble() / it }
}

/** Running aggregate kept on the device; small enough to store as one JSON value. */
@Serializable
data class MetricsSummary(
    val sinceMs: Long = 0,
    /** "providerId · model" -> stats. */
    val models: Map<String, ModelStats> = emptyMap(),
    /** action id -> stats. */
    val actions: Map<String, ActionStats> = emptyMap(),
    val coldStart: Timing = Timing(),
) {
    fun record(e: MetricEvent, nowMs: Long): MetricsSummary {
        val base = if (sinceMs == 0L) copy(sinceMs = nowMs) else this
        return when (e) {
            is MetricEvent.Request -> {
                val key = "${e.providerId} · ${e.model.ifBlank { "?" }}"
                val m = base.models[key] ?: ModelStats()
                val nextModel = if (e.errorKind == null) {
                    m.copy(
                        ok = m.ok + 1,
                        firstToken = e.firstTokenMs?.let { m.firstToken + it } ?: m.firstToken,
                        total = m.total + e.totalMs,
                    )
                } else {
                    val label = e.errorKind.name + (e.httpCode?.let { " $it" } ?: "")
                    m.copy(errors = m.errors + (label to (m.errors[label] ?: 0) + 1))
                }
                base.copy(models = base.models + (key to nextModel)).bumpAction(e.actionId) { it.copy(runs = it.runs + 1) }
            }
            is MetricEvent.Replace -> base.bumpAction(e.actionId) {
                if (e.success) it.copy(applied = it.applied + 1) else it.copy(replaceFailed = it.replaceFailed + 1)
            }
            is MetricEvent.Undo -> base.bumpAction(e.actionId) { it.copy(undone = it.undone + 1) }
            is MetricEvent.ColdStart -> base.copy(coldStart = base.coldStart + e.ms)
        }
    }

    private fun bumpAction(id: String, f: (ActionStats) -> ActionStats) =
        copy(actions = actions + (id to f(actions[id] ?: ActionStats())))
}
