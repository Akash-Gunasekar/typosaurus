package com.example.aikeyboard.ime

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.aikeyboard.core.runner.ActionRunner
import com.example.aikeyboard.core.runner.MetricEvent
import com.example.aikeyboard.data.DeviceState
import com.example.aikeyboard.graph
import com.example.aikeyboard.ime.ui.AppTheme
import com.example.aikeyboard.ime.ui.Key
import com.example.aikeyboard.ime.ui.KeyboardHandlers
import com.example.aikeyboard.ime.ui.KeyboardScreen
import com.example.aikeyboard.settings.SettingsActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The keyboard. Compose needs a lifecycle, ViewModelStore and SavedStateRegistry on the view tree,
 * none of which an InputMethodService provides, so the service implements the owners itself.
 */
class AiKeyboardService : InputMethodService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var text: TextController
    private lateinit var runner: ActionRunner

    private data class Editor(val inputType: Int, val imeOptions: Int, val packageName: String?)

    private val editor = MutableStateFlow<Editor?>(null)
    private val settingsLoaded = MutableStateFlow(false)
    private val unlocked = MutableStateFlow(true)
    private val autoShift = MutableStateFlow(false)
    private lateinit var clips: ClipboardHistory
    private lateinit var voice: VoiceInput
    private lateinit var online: StateFlow<Boolean>
    private lateinit var fieldPolicy: StateFlow<FieldPolicy>
    /** Service creation time; cleared once the first keyboard frame has been measured. */
    private var createdAt: Long? = null

    override fun onCreate() {
        super.onCreate()
        createdAt = SystemClock.elapsedRealtime()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        val appGraph = this.graph
        text = TextController { currentInputConnection }
        runner = appGraph.newRunner(scope, text)
        clips = ClipboardHistory(this) { appGraph.settings.settings.value.clipboardHistory }.also { it.start() }
        voice = VoiceInput(this, ::commitDictation)
        online = DeviceState.onlineFlow(this).stateIn(scope, SharingStarted.Eagerly, true)

        // Derived, not snapshotted: a blocklist edit (or settings finishing loading) re-evaluates the field.
        fieldPolicy = combine(editor, appGraph.settings.settings, settingsLoaded, unlocked) { e, cfg, loaded, unl ->
            when {
                e == null || !loaded -> FieldPolicy.Unknown
                else -> FieldPolicy.evaluate(e.inputType, e.imeOptions, e.packageName, cfg.blockedPackages, unl)
            }
        }.stateIn(scope, SharingStarted.Eagerly, FieldPolicy.Unknown)

        scope.launch { appGraph.settings.awaitLoaded(); settingsLoaded.value = true }
    }

    // Landscape "extract" mode would hide the host app and our toolbar layout.
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreateInputView(): View {
        window.window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(this)
            decor.setViewTreeViewModelStoreOwner(this)
            decor.setViewTreeSavedStateRegistryOwner(this)
        }
        val appGraph = this.graph
        val handlers = KeyboardHandlers(
            onKey = ::onKey,
            onAction = { action, arg -> if (fieldPolicy.value.aiAllowed) runner.run(action, arg) },
            onClipAction = { action, clip ->
                if (fieldPolicy.value.aiAllowed && appGraph.settings.settings.value.clipboardAccess) runner.runOnText(action, clip)
            },
            onAccept = runner::accept,
            onRetry = runner::retry,
            onCopy = runner::copyResult,
            onCancel = runner::cancel,
            onUndo = runner::undo,
            onOpenSettings = { openSettings("providers") },
            onPaste = { text.commit(it) },
            onOpenClipboard = clips::refresh,
            onRemoveClip = clips::remove,
            onClearClips = clips::clear,
            onStartVoice = ::startVoice,
            onStopVoice = voice::stop,
            onCancelVoice = voice::cancel,
        )
        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@AiKeyboardService)
            setViewTreeViewModelStoreOwner(this@AiKeyboardService)
            setViewTreeSavedStateRegistryOwner(this@AiKeyboardService)
            doOnPreDraw {
                createdAt?.let { appGraph.metrics.record(MetricEvent.ColdStart(SystemClock.elapsedRealtime() - it)) }
                createdAt = null
            }
            setContent {
                val cfg by appGraph.settings.settings.collectAsState()
                val actions by appGraph.settings.actions.collectAsState()
                val field by fieldPolicy.collectAsState()
                val state by runner.state.collectAsState()
                val isOnline by online.collectAsState()
                val shift by autoShift.collectAsState()
                val clipItems by clips.items.collectAsState()
                val voiceState by voice.state.collectAsState()
                AppTheme(cfg) {
                    KeyboardScreen(
                        field = field, runner = state, inputTruncated = runner.inputTruncated, online = isOnline,
                        settings = cfg, actions = actions, autoShift = shift, clips = clipItems, voice = voiceState,
                        handlers = handlers,
                    )
                }
            }
        }
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!restarting) runner.cancel()
        unlocked.value = DeviceState.isUserUnlocked(this)
        editor.value = Editor(info.inputType, info.imeOptions, info.packageName)
        updateAutoShift(info)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        runner.cancel() // never keep streaming into a closed or different field
        voice.cancel()  // nor keep the microphone open
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onFinishInputView(finishingInput)
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        currentInputEditorInfo?.let(::updateAutoShift)
    }

    override fun onDestroy() {
        scope.cancel()
        clips.stop()
        voice.destroy()
        viewModelStore.clear()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    private fun updateAutoShift(info: EditorInfo) {
        autoShift.value = (currentInputConnection?.getCursorCapsMode(info.inputType) ?: 0) != 0
    }

    private fun onKey(key: Key) {
        when (key) {
            is Key.Char -> text.commit(key.value)
            Key.Space -> {
                text.commit(" ")
                runner.checkInlineTrigger(fieldPolicy.value.aiAllowed)
            }
            Key.Backspace -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            Key.Enter -> handleEnter()
        }
    }

    private fun handleEnter() {
        val info = currentInputEditorInfo ?: return text.commit("\n")
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        val noEnterAction = info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED && !noEnterAction) {
            currentInputConnection?.performEditorAction(action)
        } else {
            text.commit("\n")
        }
    }

    private fun openSettings(route: String, requestMic: Boolean = false) {
        startActivity(
            Intent(this, SettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(SettingsActivity.EXTRA_ROUTE, route)
                .putExtra(SettingsActivity.EXTRA_REQUEST_MIC, requestMic),
        )
        requestHideSelf(0)
    }

    /** A keyboard can't show the permission dialog itself, so the first tap sends the user to Settings. */
    private fun startVoice() {
        if (voice.hasPermission()) voice.start() else openSettings("keyboard", requestMic = true)
    }

    private fun commitDictation(spoken: String) {
        val before = text.beforeCursor(1)
        val sep = if (before.isNullOrEmpty() || before.last().isWhitespace()) "" else " "
        text.commit(sep + spoken)
    }
}
