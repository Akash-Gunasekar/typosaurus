package com.example.aikeyboard.core.runner

import com.example.aikeyboard.core.action.Action
import com.example.aikeyboard.core.action.LanguageNames
import com.example.aikeyboard.core.action.PromptRenderer
import com.example.aikeyboard.core.provider.ChatRequest
import com.example.aikeyboard.core.provider.LlmEvent
import com.example.aikeyboard.core.provider.LlmException
import com.example.aikeyboard.core.provider.ProviderCatalog
import com.example.aikeyboard.core.provider.ResilientStreamer
import com.example.aikeyboard.core.provider.needsSettings
import com.example.aikeyboard.core.provider.userMessage
import com.example.aikeyboard.core.trigger.TriggerParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * State machine: Idle → Streaming → Preview → Applied (undo window) → Idle, with Failed on errors.
 * Not thread-safe by design: call from one dispatcher (Main.immediate in the IME).
 */
class ActionRunner(
    private val scope: CoroutineScope,
    private val text: TextPort,
    private val streamer: ResilientStreamer,
    private val source: SettingsSource,
    private val clipboard: ClipboardPort,
    private val totalTimeoutMs: Long = 60_000,
    private val undoWindowMs: Long = 5_000,
    private val metrics: MetricsSink = MetricsSink.None,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val _state = MutableStateFlow<RunnerState>(RunnerState.Idle)
    val state: StateFlow<RunnerState> = _state

    private class Job1(val action: Action, val arg: String?, val captured: Captured)

    /** Timing for one run; only ids and durations, never text. */
    private class Attempt(val actionId: String, val startedAt: Long) {
        var providerId: String? = null
        var model: String = ""
        var firstTokenAt: Long? = null
    }

    private var job: Job? = null
    private var undoJob: Job? = null
    private var last: Job1? = null

    val isBusy: Boolean get() = _state.value is RunnerState.Streaming

    /** True when the field held more text than was sent (shown as "Text truncated"). */
    val inputTruncated: Boolean get() = last?.captured?.truncated == true

    /** Run [action] on the selection / field. */
    fun run(action: Action, arg: String? = null) {
        val cap = text.capture()
        if (cap == null) {
            _state.value = RunnerState.Failed("No text to process", canRetry = false)
            return
        }
        start(Job1(action, arg, cap))
    }

    /** Explicit "Use clipboard" tap; the result is inserted at the cursor. */
    fun runOnClipboard(action: Action, arg: String? = null) = runOnText(action, clipboard.read(), arg)

    /** Run on text the user picked outside the field (a clipboard item); the result is inserted at the cursor. */
    fun runOnText(action: Action, input: String?, arg: String? = null) {
        val clip = input?.takeIf { it.isNotBlank() }
        if (clip == null) {
            _state.value = RunnerState.Failed("Clipboard is empty", canRetry = false)
            return
        }
        start(Job1(action, arg, Captured(clip.take(MAX_INPUT_CHARS), Captured.Mode.Clipboard, truncated = clip.length > MAX_INPUT_CHARS)))
    }

    /** Called after the user types a space; fires when the text before the cursor ends in a trigger. */
    fun checkInlineTrigger(aiAllowed: Boolean) {
        if (!aiAllowed || _state.value !is RunnerState.Idle && _state.value !is RunnerState.Applied) return
        scope.launch {
            source.awaitLoaded()
            val cfg = source.settings.value
            if (!cfg.triggersEnabled) return@launch
            if (!cfg.consentGiven) { // fail before touching the field, so the typed trigger stays intact
                _state.value = RunnerState.Failed("Open the app and accept the privacy notice first", canRetry = false, openSettings = true)
                return@launch
            }
            val actions = source.actions.value.associateBy { it.keyword }
            val before = text.beforeCursor(MAX_INPUT_CHARS) ?: return@launch
            val match = TriggerParser.parse(before, actions.keys, cfg.triggerPrefix) ?: return@launch
            val action = actions[match.keyword] ?: return@launch
            text.deleteBefore(match.tokenLength)
            start(Job1(action, match.arg, Captured(match.body, Captured.Mode.BeforeCursor)))
        }
    }

    fun retry() {
        val l = last ?: return
        start(l)
    }

    fun accept() {
        val p = _state.value as? RunnerState.Preview ?: return
        val l = last ?: return
        apply(l.action, l.captured, p.result)
    }

    fun copyResult() {
        val s = _state.value
        val result = when (s) {
            is RunnerState.Preview -> s.result
            is RunnerState.Applied -> s.result
            is RunnerState.Streaming -> s.partial
            else -> return
        }
        clipboard.write(result)
    }

    fun undo() {
        val applied = _state.value as? RunnerState.Applied ?: return
        undoJob?.cancel()
        if (text.beforeCursor(applied.result.length) == applied.result) {
            text.deleteBefore(applied.result.length)
            text.commit(applied.original.text)
        } else {
            clipboard.write(applied.original.text)
            _state.value = RunnerState.Failed("Text changed — original copied to clipboard", canRetry = false)
            return
        }
        metrics.record(MetricEvent.Undo(applied.action.id))
        _state.value = RunnerState.Idle
    }

    fun cancel() {
        job?.cancel(); job = null
        undoJob?.cancel(); undoJob = null
        _state.value = RunnerState.Idle
    }

    /** Dismiss a Failed / Preview panel without touching the field. */
    fun dismiss() = cancel()

    private fun start(j: Job1) {
        job?.cancel(); undoJob?.cancel()
        last = j
        _state.value = RunnerState.Streaming(j.action, "")
        job = scope.launch {
            val attempt = Attempt(j.action.id, clock())
            try {
                source.awaitLoaded()
                execute(j, attempt)
            } catch (e: TimeoutCancellationException) {
                report(attempt, LlmException.Kind.Timeout)
                _state.value = RunnerState.Failed("The request timed out — try again")
            } catch (e: CancellationException) {
                throw e // user cancelled or the field closed: not a provider outcome
            } catch (e: LlmException) {
                report(attempt, e.kind, e.httpCode)
                _state.value = RunnerState.Failed(e.userMessage(), canRetry = !e.needsSettings, openSettings = e.needsSettings)
            } catch (e: Exception) {
                report(attempt, LlmException.Kind.Unknown)
                _state.value = RunnerState.Failed("Something went wrong") // never include text or exception detail
            }
        }
    }

    /** Records the run once a provider was chosen; [kind] null means success. */
    private fun report(a: Attempt, kind: LlmException.Kind? = null, httpCode: Int? = null) {
        val provider = a.providerId ?: return
        val now = clock()
        metrics.record(
            MetricEvent.Request(
                providerId = provider, model = a.model, actionId = a.actionId,
                firstTokenMs = a.firstTokenAt?.let { it - a.startedAt }, totalMs = now - a.startedAt,
                errorKind = kind, httpCode = httpCode,
            ),
        )
    }

    private suspend fun execute(j: Job1, attempt: Attempt) {
        val cfg = source.settings.value
        if (!cfg.consentGiven) {
            _state.value = RunnerState.Failed("Open the app and accept the privacy notice first", canRetry = false, openSettings = true)
            return
        }
        val action = j.action
        val primary = action.providerId ?: cfg.defaultProviderId
        val vars = mapOf(
            "text" to j.captured.text,
            "arg" to (j.arg ?: ""),
            "tone" to (j.arg ?: cfg.defaultTone),
            "language" to (LanguageNames.resolve(j.arg) ?: cfg.defaultLanguage),
        )
        val (system, user) = PromptRenderer.render(action, vars)

        var via = ""
        fun requestFor(providerId: String): ChatRequest {
            attempt.providerId = providerId; attempt.model = ""
            val model = (if (providerId == primary) action.model else null)
                ?: cfg.modelFor(providerId)
                ?: throw LlmException(LlmException.Kind.Config, "Choose a model for ${ProviderCatalog.find(providerId)?.displayName ?: providerId} in Settings")
            attempt.model = model
            if (providerId == primary) via = "$providerId · $model"
            return ChatRequest(
                model = model, system = system, user = user,
                temperature = action.temperature, maxTokens = action.maxTokens,
                stream = cfg.streaming, quirks = cfg.quirksFor(providerId, model),
            )
        }

        val sb = StringBuilder()
        var stop: String? = null
        withTimeout(totalTimeoutMs) {
            streamer.stream(primary, cfg.fallbackProviderId, ::requestFor).collect { e ->
                when (e) {
                    is LlmEvent.Delta -> {
                        if (attempt.firstTokenAt == null) attempt.firstTokenAt = clock()
                        sb.append(e.text)
                        if (cfg.streaming) _state.value = RunnerState.Streaming(action, sb.toString())
                    }
                    is LlmEvent.Done -> if (e.stopReason != null) stop = e.stopReason
                }
            }
        }

        report(attempt)

        val result = PromptRenderer.clean(sb.toString(), j.captured.text)
        if (result.isBlank()) {
            _state.value = RunnerState.Failed("No result")
            return
        }
        val truncated = stop == "length" || stop == "max_tokens"
        val suspiciouslyLong = result.length > j.captured.text.length * 2 + 40
        val canAuto = action.autoApply && !truncated && !suspiciouslyLong &&
            j.captured.mode != Captured.Mode.Clipboard
        if (canAuto) apply(action, j.captured, result)
        else _state.value = RunnerState.Preview(action, result, truncated, via)
    }

    private fun apply(action: Action, cap: Captured, result: String) {
        val ok = text.replace(cap, result)
        metrics.record(MetricEvent.Replace(action.id, ok))
        if (ok) {
            _state.value = RunnerState.Applied(action, cap, result)
            undoJob?.cancel()
            undoJob = scope.launch {
                delay(undoWindowMs)
                if (_state.value is RunnerState.Applied) _state.value = RunnerState.Idle
            }
        } else {
            clipboard.write(result)
            _state.value = RunnerState.Failed("Text changed — result copied to clipboard", canRetry = false)
        }
    }

    private companion object {
        const val MAX_INPUT_CHARS = 4000
    }
}
