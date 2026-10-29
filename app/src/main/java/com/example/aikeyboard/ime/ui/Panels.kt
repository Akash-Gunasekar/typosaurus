package com.example.aikeyboard.ime.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aikeyboard.core.action.Action
import com.example.aikeyboard.ime.VoiceInput

/** One clipboard entry; [sensitive] entries (passwords, codes) are shown masked. */
data class ClipItem(val text: String, val sensitive: Boolean = false)

/** Emoji tapped recently in this keyboard session (in memory only). */
private object EmojiRecents {
    val items = mutableStateListOf<String>()
    fun add(e: String) { items.remove(e); items.add(0, e); while (items.size > 32) items.removeAt(items.lastIndex) }
}

@Composable
fun EmojiPanel(height: Dp, onEmoji: (String) -> Unit, onBackspace: () -> Unit, onAbc: () -> Unit) {
    var tab by remember { mutableIntStateOf(if (EmojiRecents.items.isEmpty()) 1 else 0) }
    Column(Modifier.fillMaxWidth().height(height)) {
        LazyRow(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            itemsIndexed(Emoji.categories) { i, c ->
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (i == tab) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.background)
                        .clickable { tab = i },
                    contentAlignment = Alignment.Center,
                ) { Text(c.icon, fontSize = 20.sp) }
            }
        }
        val list = if (tab == 0) EmojiRecents.items.toList() else Emoji.categories[tab].emoji
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (list.isEmpty()) {
                Text("Emoji you use show up here", Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            } else {
                LazyVerticalGrid(GridCells.Adaptive(44.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(4.dp)) {
                    items(list) { e ->
                        Box(
                            Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).clickable { EmojiRecents.add(e); onEmoji(e) },
                            contentAlignment = Alignment.Center,
                        ) { Text(e, fontSize = 26.sp, textAlign = TextAlign.Center) }
                    }
                }
            }
        }
        PanelBottomRow(onAbc, onBackspace)
    }
}

@Composable
fun ClipboardPanel(
    height: Dp,
    items: List<ClipItem>,
    aiReply: Action?,
    onPaste: (ClipItem) -> Unit,
    onAiReply: (Action, ClipItem) -> Unit,
    onRemove: (ClipItem) -> Unit,
    onClear: () -> Unit,
    onBackspace: () -> Unit,
    onAbc: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().height(height)) {
        Row(Modifier.fillMaxWidth().height(40.dp).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ContentPaste, null, Modifier.size(18.dp))
            Text("  Clipboard", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            if (items.isNotEmpty()) TextButton(onClick = onClear) { Text("Clear all") }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (items.isEmpty()) {
                Text(
                    "Copy text in any app and it shows up here.\nKept in memory only — never saved or uploaded.",
                    Modifier.align(Alignment.Center).padding(16.dp), textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 13.sp,
                )
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(items, key = { it.text }) { item ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface)
                                .clickable { onPaste(item) }.padding(start = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (item.sensitive) "•••••••• (hidden)" else item.text,
                                Modifier.weight(1f).padding(vertical = 10.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp,
                            )
                            if (aiReply != null && !item.sensitive) {
                                IconButton(onClick = { onAiReply(aiReply, item) }) { Icon(Icons.AutoMirrored.Filled.Reply, "AI reply to this") }
                            }
                            IconButton(onClick = { onRemove(item) }) { Icon(Icons.Default.Close, "Remove", Modifier.size(18.dp)) }
                        }
                    }
                }
            }
        }
        PanelBottomRow(onAbc, onBackspace)
    }
}

/** "More" panel: every AI shortcut as a tile, plus Settings. */
@Composable
fun AiMenuPanel(height: Dp, actions: List<Action>, enabled: Boolean, onAction: (Action) -> Unit, onOpenSettings: () -> Unit, onAbc: () -> Unit) {
    Column(Modifier.fillMaxWidth().height(height)) {
        LazyVerticalGrid(GridCells.Fixed(4), Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(actions, key = { it.id }) { a ->
                Tile(a.icon, a.name, enabled) { onAction(a) }
            }
        }
        if (!enabled) Text("AI is off in this field", Modifier.padding(horizontal = 12.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        PanelBottomRow(onAbc, onBackspace = null, onSettings = onOpenSettings)
    }
}

@Composable
private fun Tile(icon: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.14f else 0.05f))
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(icon, fontSize = 22.sp)
        Text(label, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

@Composable
fun VoicePanel(height: Dp, state: VoiceInput.State, onStop: () -> Unit, onRetry: () -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().height(height).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        when (state) {
            is VoiceInput.State.Listening -> {
                Text(state.partial.ifEmpty { "Listening…" }, textAlign = TextAlign.Center, fontSize = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(20.dp))
                val pulse by animateFloatAsState(1f + state.level * 0.35f, label = "mic")
                Box(
                    Modifier.size(72.dp).scale(pulse).clip(CircleShape).background(MaterialTheme.colorScheme.primary).clickable(onClick = onStop),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Stop, "Stop", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(34.dp)) }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onClose) { Text("Cancel") }
            }
            is VoiceInput.State.Error -> {
                Text(state.message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).clickable(onClick = onRetry),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Mic, "Try again", tint = MaterialTheme.colorScheme.onPrimary) }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onClose) { Text("Back to keyboard") }
            }
            VoiceInput.State.Idle -> Unit
        }
    }
}

@Composable
private fun PanelBottomRow(onAbc: () -> Unit, onBackspace: (() -> Unit)?, onSettings: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 4.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier.width(72.dp).height(40.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface).clickable(onClick = onAbc),
            contentAlignment = Alignment.Center,
        ) { Text("ABC", fontSize = 14.sp) }
        Spacer(Modifier.weight(1f))
        if (onSettings != null) {
            Row(
                Modifier.height(40.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface)
                    .clickable(onClick = onSettings).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { Icon(Icons.Default.Settings, null, Modifier.size(20.dp)); Text("  Settings", fontSize = 14.sp) }
        }
        if (onBackspace != null) {
            Box(
                Modifier.width(72.dp).height(40.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface).clickable(onClick = onBackspace),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.Backspace, "Backspace") }
        }
    }
}

private object Emoji {
    class Category(val icon: String, val emoji: List<String>)

    private fun c(icon: String, all: String) = Category(icon, all.split(' ').filter { it.isNotBlank() })

    val categories = listOf(
        Category("🕘", emptyList()), // recents
        c("😀", "😀 😃 😄 😁 😆 😅 🤣 😂 🙂 🙃 😉 😊 😇 🥰 😍 🤩 😘 😗 😚 😙 😋 😛 😜 🤪 😝 🤑 🤗 🤭 🤫 🤔 🤐 🤨 😐 😑 😶 😏 😒 🙄 😬 😮‍💨 🤥 😌 😔 😪 🤤 😴 😷 🤒 🤕 🤢 🤮 🥵 🥶 🥴 😵 🤯 🤠 🥳 😎 🤓 🧐 😕 😟 🙁 😮 😯 😲 😳 🥺 😦 😧 😨 😰 😥 😢 😭 😱 😖 😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬 😈 👿 💀 💩 🤡 👻 👽 🤖"),
        c("👋", "👋 🤚 🖐 ✋ 🖖 👌 🤌 🤏 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ 👍 👎 ✊ 👊 🤛 🤜 👏 🙌 👐 🤲 🤝 🙏 ✍️ 💅 🤳 💪 🦾 👀 👁 👄 🧠 🫶 🙋 🙇 🤦 🤷 💁 🙆 🙅 🧑‍💻 👨‍👩‍👧 👶 🧒 👦 👧 🧑 👨 👩 🧓 👴 👵"),
        c("❤️", "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💟 ♥️ 💋 💯 💢 💥 💫 💦 💨 🔥 ✨ ⭐ 🌟 ⚡ 🎉 🎊 🎈 🎁 🏆 🥇 👑 💎"),
        c("🐶", "🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🙈 🙉 🙊 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🐺 🐴 🦄 🐝 🦋 🐌 🐞 🐢 🐍 🐙 🐬 🐳 🐟 🐠 🦈 🐘 🦒 🐄 🐕 🐈 🌸 🌹 🌺 🌻 🌼 🌷 🌱 🌲 🌳 🌴 🌵 🍀 🍁 🍂 🌍 🌙 ☀️ ⛅ 🌧 ⛈ ❄️ 🌈 🌊"),
        c("🍔", "🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🫐 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🥑 🥦 🥕 🌽 🌶 🥔 🍞 🥐 🧀 🥚 🍳 🥞 🥓 🍗 🍖 🌭 🍔 🍟 🍕 🥪 🌮 🌯 🥗 🍝 🍜 🍲 🍛 🍣 🍱 🥟 🍚 🍙 🍦 🍰 🎂 🍩 🍪 🍫 🍬 ☕ 🍵 🧃 🥤 🍺 🍻 🥂 🍷"),
        c("⚽", "⚽ 🏀 🏈 ⚾ 🎾 🏐 🏉 🎱 🏓 🏸 🏏 🥊 ⛳ 🏹 🎣 🏋️ 🚴 🏊 🧘 🏃 🎯 🎮 🎲 🧩 🎨 🎬 🎤 🎧 🎸 🎹 🥁 🎻 📚 ✏️ 📝 💼 📅 📌 📎"),
        c("🚗", "🚗 🚕 🚙 🚌 🏎 🚓 🚑 🚒 🚚 🚲 🛵 🏍 ✈️ 🚀 🛸 🚁 ⛵ 🚢 🚆 🚇 🚉 🏠 🏡 🏢 🏥 🏦 🏨 🏫 ⛪ 🕌 🛕 🗽 🗼 🏰 🎡 🏖 🏝 ⛰ 🌋 🗺 🧭 ⛽ 🚦"),
        c("💡", "💡 📱 💻 ⌨️ 🖥 🖨 🖱 📷 📸 📹 🎥 📞 ☎️ 📺 📻 ⏰ ⌛ 🔋 🔌 💰 💵 💳 🧾 ✉️ 📧 📦 📮 🗂 📁 📊 📈 📉 🔒 🔑 🔨 🛠 ⚙️ 🧲 💊 🩺 🧸 🛒"),
        c("🔣", "✅ ☑️ ✔️ ❌ ❎ ➕ ➖ ✖️ ➗ ❓ ❗ ‼️ ⁉️ ⚠️ 🚫 ⛔ 🔞 ♻️ 🔁 🔄 ▶️ ⏸ ⏹ ⏺ ⏭ ⏮ 🔼 🔽 ⬆️ ⬇️ ⬅️ ➡️ ↗️ ↘️ 🆗 🆕 🆒 🆓 🔴 🟠 🟡 🟢 🔵 🟣 ⚫ ⚪ 🟥 🟩 🟦 ©️ ®️ ™️ #️⃣ *️⃣ 🔟 💲 ℹ️"),
    )
}
