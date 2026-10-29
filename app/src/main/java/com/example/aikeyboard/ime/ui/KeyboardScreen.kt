package com.example.aikeyboard.ime.ui

import android.media.AudioManager
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.filled.KeyboardCapslock
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.style.TextOverflow
import com.example.aikeyboard.ime.VoiceInput
import com.example.aikeyboard.ime.aiAllowed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aikeyboard.core.action.Action
import com.example.aikeyboard.core.action.AppSettings
import com.example.aikeyboard.core.action.LanguageNames
import com.example.aikeyboard.core.runner.RunnerState
import com.example.aikeyboard.ime.FieldPolicy
import kotlinx.coroutines.withTimeoutOrNull

class KeyboardHandlers(
    val onKey: (Key) -> Unit,
    val onAction: (Action, String?) -> Unit,
    val onClipAction: (Action, String) -> Unit,
    val onAccept: () -> Unit,
    val onRetry: () -> Unit,
    val onCopy: () -> Unit,
    val onCancel: () -> Unit,
    val onUndo: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onPaste: (String) -> Unit,
    val onOpenClipboard: () -> Unit,
    val onRemoveClip: (ClipItem) -> Unit,
    val onClearClips: () -> Unit,
    /** Starts voice typing, or opens Settings when the microphone permission is still missing. */
    val onStartVoice: () -> Unit,
    val onStopVoice: () -> Unit,
    val onCancelVoice: () -> Unit,
)

private enum class Shift { Off, Once, Caps }
private enum class ArgKind { Tone, Language }
private enum class Panel { Keys, Emoji, Clipboard, AiMenu }

private fun Action.argKind(): ArgKind? = when {
    "{tone}" in systemPrompt -> ArgKind.Tone
    "{language}" in systemPrompt -> ArgKind.Language
    else -> null
}

@Composable
fun KeyboardScreen(
    field: FieldPolicy,
    runner: RunnerState,
    inputTruncated: Boolean,
    online: Boolean,
    settings: AppSettings,
    actions: List<Action>,
    autoShift: Boolean,
    clips: List<ClipItem>,
    voice: VoiceInput.State,
    handlers: KeyboardHandlers,
) {
    var layer by remember { mutableStateOf(Layer.Letters) }
    var shift by remember { mutableStateOf(Shift.Off) }
    var lastShiftTap by remember { mutableStateOf(0L) }
    var accent by remember { mutableStateOf<List<String>?>(null) }
    var pendingArg by remember { mutableStateOf<Action?>(null) }
    var panel by remember { mutableStateOf(Panel.Keys) }

    val upper = layer == Layer.Letters && (shift != Shift.Off || autoShift)
    val rowHeight = (46 * settings.heightPercent / 100f).dp
    val longPress = settings.longPressDelayMs.toLong()
    // Panels take exactly the space of the key rows so the keyboard never jumps in height.
    val panelHeight = (rowHeight + 4.dp) * (if (settings.numberRow) 5 else 4)
    val aiAllowed = field.aiAllowed
    // Voice goes to the phone's speech service: never offer it in password or incognito fields.
    val blockedReason = (field as? FieldPolicy.AiBlocked)?.reason
    val voiceAllowed = settings.toolbarVoice &&
        blockedReason != FieldPolicy.Reason.Password && blockedReason != FieldPolicy.Reason.Incognito
    val toolbarActions = actions.filter { it.inToolbar }

    fun typeChar(v: String) {
        accent = null
        handlers.onKey(Key.Char(if (upper) v.uppercase() else v))
        if (shift == Shift.Once) shift = Shift.Off
    }

    fun runAction(a: Action) {
        panel = Panel.Keys
        if (a.argKind() != null) pendingArg = a else handlers.onAction(a, null)
    }

    fun toggle(p: Panel) {
        panel = if (panel == p) Panel.Keys else p
        if (panel == Panel.Clipboard) handlers.onOpenClipboard()
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 3.dp, vertical = 2.dp)) {
            // ---- toolbar / status row ----------------------------------------------------
            Box(Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                val showPanel = runner is RunnerState.Streaming || runner is RunnerState.Preview || runner is RunnerState.Failed
                when {
                    accent != null -> AccentBar(accent!!, onPick = { typeChar(it) ; accent = null }, onClose = { accent = null })
                    showPanel -> PreviewPanel(
                        state = runner, inputTruncated = inputTruncated,
                        onAccept = handlers.onAccept, onRetry = handlers.onRetry, onCopy = handlers.onCopy,
                        onCancel = handlers.onCancel, onOpenSettings = handlers.onOpenSettings,
                    )
                    runner is RunnerState.Applied -> AppliedBar(handlers.onUndo, handlers.onCancel)
                    pendingArg != null -> ArgBar(
                        action = pendingArg!!, settings = settings,
                        onPick = { arg -> handlers.onAction(pendingArg!!, arg); pendingArg = null },
                        onBack = { pendingArg = null },
                    )
                    else -> Toolbar(
                        quickActions = if (aiAllowed) toolbarActions.take(3) else emptyList(),
                        blockedReason = blockedReason?.label,
                        online = online, settings = settings, voiceAllowed = voiceAllowed, active = panel,
                        onAction = ::runAction,
                        onPanel = ::toggle,
                        onMic = { panel = Panel.Keys; handlers.onStartVoice() },
                    )
                }
            }

            // ---- keys, or a panel in their place -------------------------------------------
            when {
                voice !is VoiceInput.State.Idle -> VoicePanel(
                    panelHeight, voice, onStop = handlers.onStopVoice, onRetry = handlers.onStartVoice, onClose = handlers.onCancelVoice,
                )
                panel == Panel.Emoji -> EmojiPanel(
                    panelHeight, onEmoji = { handlers.onKey(Key.Char(it)) },
                    onBackspace = { handlers.onKey(Key.Backspace) }, onAbc = { panel = Panel.Keys },
                )
                panel == Panel.Clipboard -> ClipboardPanel(
                    panelHeight, clips,
                    aiReply = if (aiAllowed && online && settings.clipboardAccess) actions.firstOrNull { it.id == "reply" } else null,
                    onPaste = { handlers.onPaste(it.text); panel = Panel.Keys },
                    onAiReply = { a, item -> panel = Panel.Keys; handlers.onClipAction(a, item.text) },
                    onRemove = handlers.onRemoveClip, onClear = handlers.onClearClips,
                    onBackspace = { handlers.onKey(Key.Backspace) }, onAbc = { panel = Panel.Keys },
                )
                panel == Panel.AiMenu -> AiMenuPanel(
                    panelHeight, toolbarActions, enabled = aiAllowed && online, onAction = ::runAction,
                    onOpenSettings = { panel = Panel.Keys; handlers.onOpenSettings() }, onAbc = { panel = Panel.Keys },
                )
                else -> KeyRows(
                    layer, upper, shift, autoShift, KeyArgs(rowHeight, longPress, settings.haptics, settings.keySound), settings.numberRow,
                    onChar = ::typeChar,
                    onAccents = { accent = it },
                    onShift = {
                        val now = System.currentTimeMillis()
                        shift = when (shift) {
                            Shift.Off -> Shift.Once
                            Shift.Once -> if (now - lastShiftTap < 400) Shift.Caps else Shift.Off
                            Shift.Caps -> Shift.Off
                        }
                        lastShiftTap = now
                    },
                    onLayer = { layer = it },
                    onKey = { accent = null; handlers.onKey(it) },
                )
            }
        }
    }
}

@Composable
private fun KeyRows(
    layer: Layer,
    upper: Boolean,
    shift: Shift,
    autoShift: Boolean,
    keyArgs: KeyArgs,
    numberRow: Boolean,
    onChar: (String) -> Unit,
    onAccents: (List<String>) -> Unit,
    onShift: () -> Unit,
    onLayer: (Layer) -> Unit,
    onKey: (Key) -> Unit,
) {
    Column {
        if (numberRow && layer == Layer.Letters) {
            KeyRow {
                Layouts.numberRow.forEach { n -> TextKey(n, keyArgs, onTap = { onChar(n) }, onLong = { Layouts.accents[n]?.let(onAccents) }) }
            }
        }
        val rows = when (layer) { Layer.Letters -> Layouts.letters; Layer.Symbols1 -> Layouts.symbols1; Layer.Symbols2 -> Layouts.symbols2 }
        KeyRow(sidePadding = if (layer == Layer.Letters) 0.5f else 0f) {
            rows[0].forEach { k -> LetterKey(k, upper, keyArgs, onChar, onAccents) }
        }
        KeyRow(sidePadding = if (layer == Layer.Letters) 0.5f else 0f) {
            rows[1].forEach { k -> LetterKey(k, upper, keyArgs, onChar, onAccents) }
        }
        KeyRow {
            when (layer) {
                Layer.Letters -> IconKey(
                    if (shift == Shift.Caps) Icons.Default.KeyboardCapslock else Icons.Default.ArrowUpward, "Shift", keyArgs, weight = 1.5f,
                    highlighted = shift != Shift.Off || autoShift, onTap = onShift,
                )
                Layer.Symbols1 -> TextKey("=\\<", keyArgs, weight = 1.5f, onTap = { onLayer(Layer.Symbols2) })
                Layer.Symbols2 -> TextKey("123", keyArgs, weight = 1.5f, onTap = { onLayer(Layer.Symbols1) })
            }
            rows[2].forEach { k -> LetterKey(k, upper, keyArgs, onChar, onAccents) }
            BackspaceKey(keyArgs) { onKey(Key.Backspace) }
        }
        KeyRow {
            TextKey(if (layer == Layer.Letters) "?123" else "ABC", keyArgs, weight = 1.5f, onTap = {
                onLayer(if (layer == Layer.Letters) Layer.Symbols1 else Layer.Letters)
            })
            TextKey(",", keyArgs, onTap = { onChar(",") })
            TextKey("space", keyArgs, weight = 5f, onTap = { onKey(Key.Space) }, small = true)
            TextKey(".", keyArgs, onTap = { onChar(".") }, onLong = { Layouts.accents["."]?.let(onAccents) })
            IconKey(Icons.AutoMirrored.Filled.KeyboardReturn, "Enter", keyArgs, weight = 1.5f, highlighted = true) { onKey(Key.Enter) }
        }
    }
}

// ---- bars ---------------------------------------------------------------------------------

/** Icon toolbar: up to three AI shortcuts on the left, feature buttons on the right. */
@Composable
private fun Toolbar(
    quickActions: List<Action>,
    blockedReason: String?,
    online: Boolean,
    settings: AppSettings,
    voiceAllowed: Boolean,
    active: Panel,
    onAction: (Action) -> Unit,
    onPanel: (Panel) -> Unit,
    onMic: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        if (blockedReason != null) {
            Icon(Icons.Default.Lock, "AI off", Modifier.padding(start = 8.dp).size(18.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Text(
                " $blockedReason", Modifier.weight(1f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        } else {
            quickActions.forEach { a -> AiIconButton(a.icon, a.name, enabled = online) { onAction(a) } }
            if (!online) Icon(Icons.Default.CloudOff, "Offline", Modifier.padding(start = 6.dp).size(18.dp), tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.weight(1f))
        }
        if (settings.toolbarEmoji) ToolIcon(Icons.Outlined.EmojiEmotions, "Emoji", active == Panel.Emoji) { onPanel(Panel.Emoji) }
        if (settings.toolbarClipboard) ToolIcon(Icons.Outlined.ContentPaste, "Clipboard", active == Panel.Clipboard) { onPanel(Panel.Clipboard) }
        ToolIcon(Icons.Default.MoreHoriz, "All AI shortcuts and settings", active == Panel.AiMenu) { onPanel(Panel.AiMenu) }
        if (voiceAllowed) ToolIcon(Icons.Outlined.Mic, "Voice typing", false, onMic)
    }
}

/** Accent-filled square holding a shortcut's emoji (the coloured AI buttons in the reference design). */
@Composable
private fun AiIconButton(icon: String, name: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.padding(horizontal = 3.dp).size(38.dp).clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.85f else 0.3f))
            .clickable(enabled = enabled, onClickLabel = name, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(icon, fontSize = 18.sp) }
}

@Composable
private fun ToolIcon(icon: ImageVector, description: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = MaterialTheme.colorScheme.onSurface) }
}

@Composable
private fun ArgBar(action: Action, settings: AppSettings, onPick: (String?) -> Unit, onBack: () -> Unit) {
    val kind = action.argKind()
    val options: List<Pair<String, String?>> = when (kind) {
        ArgKind.Tone -> listOf("default (${settings.defaultTone})" to null) + Layouts.tones.map { it to it }
        ArgKind.Language -> listOf("default (${settings.defaultLanguage})" to null) + Layouts.languages.map { (LanguageNames.resolve(it) ?: it) to it }
        null -> emptyList()
    }
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        Text("${action.icon} ${action.name}: ", fontSize = 13.sp)
        LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            items(options) { (label, arg) -> AssistChip(onClick = { onPick(arg) }, label = { Text(label) }) }
        }
    }
}

@Composable
private fun AppliedBar(onUndo: () -> Unit, onDone: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Applied ✓", Modifier.weight(1f))
        AssistChip(onClick = onUndo, label = { Text("Undo") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Undo, null) })
        IconButton(onClick = onDone) { Icon(Icons.Default.Close, "Dismiss") }
    }
}

@Composable
private fun AccentBar(options: List<String>, onPick: (String) -> Unit, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            items(options) { o ->
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                        .pointerInput(o) { awaitEachGesture { awaitFirstDown(); if (waitForUpOrCancellation() != null) onPick(o) } }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(o, fontSize = 22.sp) }
            }
        }
        IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
    }
}

// ---- keys ---------------------------------------------------------------------------------

private class KeyArgs(val height: androidx.compose.ui.unit.Dp, val longPressMs: Long, val haptics: Boolean, val sound: Boolean)

@Composable
private fun KeyRow(sidePadding: Float = 0f, content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (sidePadding > 0f) Box(Modifier.weight(sidePadding))
        content()
        if (sidePadding > 0f) Box(Modifier.weight(sidePadding))
    }
}

@Composable
private fun RowScope.LetterKey(key: String, upper: Boolean, args: KeyArgs, onChar: (String) -> Unit, onAccents: (List<String>) -> Unit) {
    val alternatives = Layouts.accents[key]
    TextKey(
        label = if (upper) key.uppercase() else key, args = args,
        onTap = { onChar(key) },
        onLong = alternatives?.let { alts -> { onAccents(if (upper) alts.map { it.uppercase() } else alts) } },
    )
}

@Composable
private fun RowScope.TextKey(
    label: String, args: KeyArgs, weight: Float = 1f, small: Boolean = false,
    onTap: () -> Unit, onLong: (() -> Unit)? = null,
) = KeyCap(args, weight, highlighted = false, onTap = onTap, onLong = onLong, repeat = false, description = label) {
    Text(label, fontSize = if (small) 13.sp else 20.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun RowScope.IconKey(icon: ImageVector, description: String, args: KeyArgs, weight: Float = 1f, highlighted: Boolean = false, onTap: () -> Unit) =
    KeyCap(args, weight, highlighted, onTap, null, false, description) {
        Icon(icon, description, tint = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
    }

@Composable
private fun RowScope.BackspaceKey(args: KeyArgs, onDelete: () -> Unit) =
    KeyCap(args, 1.5f, false, onDelete, null, true, "Backspace") {
        Icon(Icons.AutoMirrored.Filled.Backspace, "Backspace", tint = MaterialTheme.colorScheme.onSurface)
    }

/**
 * One key. Plain keys fire on release so a long press can be told apart; the backspace key fires on
 * press and then auto-repeats while held.
 */
@Composable
private fun RowScope.KeyCap(
    args: KeyArgs,
    weight: Float,
    highlighted: Boolean,
    onTap: () -> Unit,
    onLong: (() -> Unit)?,
    repeat: Boolean,
    description: String,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val context = LocalContext.current
    var pressed by remember { mutableStateOf(false) }
    // The gesture loop must survive recomposition mid-press, so it reads the latest callbacks via state.
    val currentTap by rememberUpdatedState(onTap)
    val currentLong by rememberUpdatedState(onLong)
    val hasLong = onLong != null
    val feedback = {
        if (args.haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (args.sound) context.getSystemService(AudioManager::class.java)?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD)
    }
    val bg = when {
        highlighted -> MaterialTheme.colorScheme.primary
        pressed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.surface
    }
    Box(
        Modifier
            .weight(weight)
            .height(args.height)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .pointerInput(repeat, hasLong, args.longPressMs) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    try {
                        when {
                            repeat -> {
                                feedback(); currentTap()
                                val released = withTimeoutOrNull(400) { waitForUpOrCancellation(); true }
                                if (released == null) {
                                    while (true) {
                                        currentTap()
                                        if (withTimeoutOrNull(50) { waitForUpOrCancellation(); true } != null) break
                                    }
                                }
                            }
                            hasLong -> {
                                val released = withTimeoutOrNull(args.longPressMs) { waitForUpOrCancellation(); true }
                                if (released != null) { feedback(); currentTap() }
                                else { feedback(); currentLong?.invoke(); waitForUpOrCancellation() }
                            }
                            else -> { feedback(); currentTap(); waitForUpOrCancellation() }
                        }
                    } finally {
                        pressed = false
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}
