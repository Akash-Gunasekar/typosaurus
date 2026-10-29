package com.example.aikeyboard.core.runner

import com.example.aikeyboard.core.action.Action
import com.example.aikeyboard.core.action.AppSettings
import com.example.aikeyboard.core.action.BuiltInActions
import com.example.aikeyboard.core.provider.ApiKeys
import com.example.aikeyboard.core.provider.HttpEngine
import com.example.aikeyboard.core.provider.ProviderRegistry
import com.example.aikeyboard.core.provider.ResilientStreamer
import com.example.aikeyboard.core.trigger.TriggerMatch
import com.example.aikeyboard.core.trigger.TriggerParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** What an [Assistant] run reports back; every callback arrives on the scope's dispatcher. */
interface AssistantListener {
    fun onPartial(text: String)
    fun onResult(text: String, truncated: Boolean)
    fun onError(message: String, openSettings: Boolean)
}

/**
 * Callback-style front end to [ActionRunner] for hosts without coroutines or Flow, such as the
 * iOS keyboard extension. The host captures text itself, shows the result, and replaces the text on
 * Accept; this class only runs the prompt with the same routing, retries, cleaning and limits as Android.
 *
 * Settings and actions are the same JSON the Android app stores, so both platforms share one format.
 */
class Assistant(
    settingsJson: String?,
    actionsJson: String?,
    keyLookup: (providerId: String) -> String?,
    private val scope: CoroutineScope,
    http: HttpEngine,
) {
    private val source = Source()
    private val runner: ActionRunner
    private var listener: AssistantListener? = null

    init {
        update(settingsJson, actionsJson)
        val registry = ProviderRegistry(ApiKeys { keyLookup(it) }, { source.settings.value }, http)
        runner = ActionRunner(scope, NoText, ResilientStreamer(registry), source, NoClipboard)
        scope.launch {
            runner.state.collect { s ->
                val l = listener ?: return@collect
                when (s) {
                    is RunnerState.Streaming -> l.onPartial(s.partial)
                    is RunnerState.Preview -> l.onResult(s.result, s.truncated)
                    is RunnerState.Failed -> l.onError(s.message, s.openSettings)
                    else -> Unit
                }
            }
        }
    }

    val settings: AppSettings get() = source.settings.value

    /** Every action, in the user's order. */
    val actions: List<Action> get() = source.actions.value

    val toolbarActions: List<Action> get() = actions.filter { it.inToolbar }

    /** Reload after the containing app changed settings or shortcuts. */
    fun update(settingsJson: String?, actionsJson: String?) {
        source.settings.value = settingsJson?.let { runCatching { json.decodeFromString(AppSettings.serializer(), it).migrated() }.getOrNull() }
            ?: AppSettings()
        source.actions.value = actionsJson?.let { runCatching { json.decodeFromString(ACTIONS, it) }.getOrNull() }
            ?.takeIf { it.isNotEmpty() } ?: BuiltInActions.all
    }

    /** Inline trigger ("text /fix ") at the end of [beforeCursor], or null. */
    fun matchTrigger(beforeCursor: String): TriggerMatch? {
        val cfg = settings
        if (!cfg.triggersEnabled) return null
        return TriggerParser.parse(beforeCursor, actions.map { it.keyword }.toSet(), cfg.triggerPrefix)
    }

    fun actionForKeyword(keyword: String): Action? = actions.firstOrNull { it.keyword == keyword }

    /** Run [action] on [text]; results arrive on [listener]. A new run replaces the previous one. */
    fun run(action: Action, text: String, arg: String?, listener: AssistantListener) {
        this.listener = listener
        runner.runOnText(action, text, arg)
    }

    fun retry() = runner.retry()

    fun cancel() {
        listener = null
        runner.cancel()
    }

    private class Source : SettingsSource {
        override val settings = MutableStateFlow(AppSettings())
        override val actions = MutableStateFlow(BuiltInActions.all)
        override suspend fun awaitLoaded() = Unit
    }

    /** The host owns the text field; the runner never touches it. */
    private object NoText : TextPort {
        override fun capture(maxChars: Int): Captured? = null
        override fun replace(captured: Captured, result: String) = false
        override fun beforeCursor(n: Int): String? = null
        override fun deleteBefore(count: Int) = Unit
        override fun commit(text: String) = Unit
    }

    private object NoClipboard : ClipboardPort {
        override fun read(): String? = null
        override fun write(text: String) = Unit
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        private val ACTIONS = ListSerializer(Action.serializer())

        /** Same encoding the Android app uses, for the iOS app's settings screen. */
        fun encodeSettings(settings: AppSettings): String = json.encodeToString(AppSettings.serializer(), settings)
        fun encodeActions(actions: List<Action>): String = json.encodeToString(ACTIONS, actions)
    }
}
