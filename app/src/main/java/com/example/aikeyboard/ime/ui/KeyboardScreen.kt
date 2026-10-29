package com.example.aikeyboard.ime.ui

import android.media.AudioManager
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
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
import androidx.compose.ui.draw.shadow
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
    /** Replace the word being typed with this suggestion. */
    val onSuggestion: (String) -> Unit,
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
    suggestions: List<String>,
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

    val popup = remember { mutableStateOf<KeyPopup?>(null) }
    var iconsPinned by remember { mutableStateOf(false) }
    // A finished word (suggestions gone) releases the pin, so the next word shows suggestions again.
    LaunchedEffect(suggestions.isEmpty()) { if (suggestions.isEmpty()) iconsPinned = false }
    val showPanel = runner is RunnerState.Streaming || runner is RunnerState.Preview || runner is RunnerState.Failed
    // Which bar the top row shows; changes animate (slide up + fade), like the promo's state changes.
    val barMode = when {
        accent != null -> 0
        showPanel -> 1
        runner is RunnerState.Applied -> 2
        pendingArg != null -> 3
        // While a word is being typed the toolbar becomes the suggestion strip; ‹ brings the icons back
        // until the next word.
        suggestions.isNotEmpty() && !iconsPinned && panel == Panel.Keys -> 5
        else -> 4
    }

    Surface(color = MaterialTheme.colorScheme.background) {
      CompositionLocalProvider(LocalKeyPopup provides popup) {
      Box {
        Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 3.dp, vertical = 2.dp)) {
            // ---- toolbar / status row ----------------------------------------------------
            AnimatedContent(
                barMode, Modifier.fillMaxWidth().heightIn(min = 48.dp),
                transitionSpec = {
                    (fadeIn(tween(180)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 500f)) { it / 3 }) togetherWith
                        fadeOut(tween(120))
                },
                label = "bar",
            ) { mode ->
                when (mode) {
                    0 -> AccentBar(accent ?: emptyList(), onPick = { typeChar(it) ; accent = null }, onClose = { accent = null })
                    1 -> PreviewPanel(
                        state = runner, inputTruncated = inputTruncated,
                        onAccept = handlers.onAccept, onRetry = handlers.onRetry, onCopy = handlers.onCopy,
                        onCancel = handlers.onCancel, onOpenSettings = handlers.onOpenSettings,
                    )
                    2 -> AppliedBar(handlers.onUndo, handlers.onCancel)
                    3 -> pendingArg?.let { a ->
                        ArgBar(
                            action = a, settings = settings,
                            onPick = { arg -> handlers.onAction(a, arg); pendingArg = null },
                            onBack = { pendingArg = null },
                        )
                    }
                    5 -> SuggestionBar(
                        suggestions,
                        onPick = handlers.onSuggestion,
                        onBackToIcons = { iconsPinned = true },
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
            val area = when {
                voice !is VoiceInput.State.Idle -> 4
                panel == Panel.Emoji -> 1
                panel == Panel.Clipboard -> 2
                panel == Panel.AiMenu -> 3
                else -> 0
            }
            AnimatedContent(
                area,
                transitionSpec = {
                    (fadeIn(tween(160)) + scaleIn(spring(dampingRatio = 0.8f, stiffness = 600f), initialScale = 0.94f)) togetherWith
                        fadeOut(tween(100))
                },
                label = "area",
            ) { which ->
            when {
                which == 4 -> VoicePanel(
                    panelHeight, voice, onStop = handlers.onStopVoice, onRetry = handlers.onStartVoice, onClose = handlers.onCancelVoice,
                )
                which == 1 -> EmojiPanel(
                    panelHeight, onEmoji = { handlers.onKey(Key.Char(it)) },
                    onBackspace = { handlers.onKey(Key.Backspace) }, onAbc = { panel = Panel.Keys },
                )
                which == 2 -> ClipboardPanel(
                    panelHeight, clips,
                    aiReply = if (aiAllowed && online && settings.clipboardAccess) actions.firstOrNull { it.id == "reply" } else null,
                    onPaste = { handlers.onPaste(it.text); panel = Panel.Keys },
                    onAiReply = { a, item -> panel = Panel.Keys; handlers.onClipAction(a, item.text) },
                    onRemove = handlers.onRemoveClip, onClear = handlers.onClearClips,
                    onBackspace = { handlers.onKey(Key.Backspace) }, onAbc = { panel = Panel.Keys },
                )
                which == 3 -> AiMenuPanel(
                    panelHeight, toolbarActions, enabled = aiAllowed && online, onAction = ::runAction,
                    onOpenSettings = { panel = Panel.Keys; handlers.onOpenSettings() }, onAbc = { panel = Panel.Keys },
                )
                else -> KeyRows(
                    layer, upper, shift, autoShift, KeyArgs(rowHeight, longPress, settings.haptics, settings.keySound, settings.keyPopup), settings.numberRow,
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
        // Letter pop-up above the pressed key, drawn over everything inside the keyboard window.
        KeyPopupLayer(popup)
      }
      }
    }
}

/** The key being pressed, in root coordinates, for the pop-up bubble. */
private class KeyPopup(val label: String, val bounds: Rect)

private val LocalKeyPopup = staticCompositionLocalOf<MutableState<KeyPopup?>?> { null }

/** Reads the pop-up state in its own scope, so a key press redraws only the bubble, not the keyboard. */
@Composable
private fun BoxScope.KeyPopupLayer(state: MutableState<KeyPopup?>) {
    state.value?.let { KeyPopupBubble(it) }
}

/** A rounded bubble rising from the key with the letter enlarged; it springs up when it appears. */
@Composable
private fun BoxScope.KeyPopupBubble(p: KeyPopup) {
    val density = LocalDensity.current
    val rise = with(density) { 46.dp.toPx() }
    val w = p.bounds.width * 1.2f
    val top = (p.bounds.top - rise).coerceAtLeast(0f)
    val h = p.bounds.bottom - top
    val grow = remember(p) { Animatable(0.7f) }
    LaunchedEffect(p) { grow.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 900f)) }
    BoxWithConstraints(Modifier.matchParentSize()) {
        val maxX = constraints.maxWidth - w
        val left = (p.bounds.center.x - w / 2).coerceIn(0f, maxX.coerceAtLeast(0f))
        Box(
            Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .size(with(density) { w.toDp() }, with(density) { h.toDp() })
                .graphicsLayer {
                    scaleX = grow.value; scaleY = grow.value
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .shadow(8.dp, RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.TopCenter,
        ) {
            Text(p.label, Modifier.padding(top = 4.dp), fontSize = 30.sp, color = MaterialTheme.colorScheme.onSurface)
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
        KeyRow { // q…p spans the full width, like the number row
            rows[0].forEach { k -> LetterKey(k, upper, keyArgs, onChar, onAccents) }
        }
        KeyRow(sidePadding = if (layer == Layer.Letters) 0.5f else 0f) { // a…l: the usual half-key inset
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

/**
 * Word suggestions, best in the middle (as on most keyboards): [2nd] [1st] [3rd]. The ‹ on the left goes back
 * to the icon toolbar (✨ Fix and the other shortcuts).
 */
@Composable
private fun SuggestionBar(words: List<String>, onPick: (String) -> Unit, onBackToIcons: () -> Unit) {
    // While the strip animates out, it is drawn once more with the new (often empty) list.
    val best = words.firstOrNull() ?: return
    val order = listOfNotNull(words.getOrNull(1), best, words.getOrNull(2))
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).clickable(onClickLabel = "Show AI shortcuts", onClick = onBackToIcons),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Show AI shortcuts", tint = MaterialTheme.colorScheme.onSurface) }
        order.forEachIndexed { i, w ->
            if (i > 0) Box(Modifier.width(1.dp).height(22.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)))
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp)).clickable { onPick(w) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    w, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 17.sp,
                    fontWeight = if (w == best) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (w == best) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

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
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.padding(horizontal = 3.dp).pressScale(interaction, 0.9f).size(38.dp).clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.85f else 0.3f))
            .clickable(interaction, LocalIndication.current, enabled = enabled, onClickLabel = name, onClick = onClick),
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

private class KeyArgs(
    val height: androidx.compose.ui.unit.Dp, val longPressMs: Long, val haptics: Boolean, val sound: Boolean,
    /** Show the letter pop-up above pressed character keys. */
    val popup: Boolean = true,
)

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
) = KeyCap(
    args, weight, highlighted = false, onTap = onTap, onLong = onLong, repeat = false, description = label,
    popupLabel = label.takeIf { it.length <= 2 }, // letters, digits and symbols; not "space" or "?123"
) {
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
    popupLabel: String? = null,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val popupState = LocalKeyPopup.current
    val bounds = remember { arrayOf(Rect.Zero) } // updated on layout; read only when pressed, so no recomposition
    val showPopup = args.popup && popupLabel != null && popupState != null
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
        highlighted -> if (pressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.primary
        pressed -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }
    // Pressed keys sink: 96 % scale and 1.5 dp down, springing back on release (as in the promo).
    val press by animateFloatAsState(if (pressed) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 1400f), label = "key")
    val sink = with(LocalDensity.current) { 1.5.dp.toPx() }
    Box(
        Modifier
            .weight(weight)
            .height(args.height)
            .onGloballyPositioned { bounds[0] = it.boundsInRoot() }
            .graphicsLayer {
                val s = 1f - 0.04f * press
                scaleX = s; scaleY = s
                translationY = sink * press
            }
            .shadow(1.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .pointerInput(repeat, hasLong, args.longPressMs) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    val shown = if (showPopup) KeyPopup(popupLabel!!, bounds[0]).also { popupState!!.value = it } else null
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
                        if (shown != null && popupState!!.value === shown) popupState.value = null
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}
