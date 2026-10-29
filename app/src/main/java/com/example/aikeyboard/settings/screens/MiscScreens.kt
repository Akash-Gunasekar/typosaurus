package com.example.aikeyboard.settings.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.aikeyboard.R
import com.example.aikeyboard.core.action.AppSettings
import com.example.aikeyboard.core.provider.ProviderCatalog
import com.example.aikeyboard.ime.FieldPolicy
import com.example.aikeyboard.ime.ui.KeyboardAccentChoices
import com.example.aikeyboard.settings.SettingsViewModel

@Composable
fun TriggersScreen(vm: SettingsViewModel, nav: NavController) {
    val cfg by vm.settings.collectAsState()
    val actions by vm.actions.collectAsState()
    var sample by remember { mutableStateOf("") }
    ScreenScaffold("Inline triggers", nav, vm) {
        SwitchRow("Enable inline triggers", "Type text, then ${cfg.triggerPrefix}keyword and a space", cfg.triggersEnabled) { v -> vm.update { it.copy(triggersEnabled = v) } }
        SectionTitle("Prefix")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppSettings.ALLOWED_PREFIXES.forEach { p ->
                FilterChip(selected = cfg.triggerPrefix == p, onClick = { vm.update { it.copy(triggerPrefix = p) } }, label = { Text(p) })
            }
        }
        Hint("Triggers only fire when the token starts the text or follows whitespace, so URLs like example.com/fix are safe.")
        SectionTitle("Syntax")
        Text("pls send report tmrw ${cfg.triggerPrefix}fix␣\nthanks for the update ${cfg.triggerPrefix}tone:formal␣\ngood morning ${cfg.triggerPrefix}tr:ta␣", style = MaterialTheme.typography.bodyMedium)
        SectionTitle("Your triggers")
        actions.forEach { a -> Text("${cfg.triggerPrefix}${a.keyword}  →  ${a.icon} ${a.name}") }
        HorizontalDivider()
        SectionTitle("Test field")
        Hint("Select Typosaurus, then try a trigger here.")
        OutlinedTextField(sample, { sample = it }, Modifier.fillMaxWidth(), minLines = 3)
    }
}

@Composable
fun KeyboardPrefsScreen(vm: SettingsViewModel, nav: NavController) {
    val ctx = LocalContext.current
    val cfg by vm.settings.collectAsState()
    val askMic by vm.requestMic.collectAsState()
    fun micGranted() = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    var micOk by remember { mutableStateOf(micGranted()) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micOk = granted
        vm.message.value = if (granted) "Microphone allowed — tap the mic on the keyboard" else "Voice typing needs the microphone permission"
    }
    LaunchedEffect(askMic) {
        if (askMic) { vm.requestMic.value = false; if (!micGranted()) micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
    }
    ScreenScaffold("Keyboard", nav, vm) {
        SectionTitle("Toolbar buttons")
        Hint("The first three shortcuts in Settings › Shortcuts appear as coloured buttons; ⋯ opens all of them.")
        SwitchRow("😊 Emoji", null, cfg.toolbarEmoji) { v -> vm.update { it.copy(toolbarEmoji = v) } }
        SwitchRow("📋 Clipboard", "Paste from text you copied recently", cfg.toolbarClipboard) { v -> vm.update { it.copy(toolbarClipboard = v) } }
        SwitchRow("🎤 Voice typing", "Uses your phone's speech recognition service. Off in password and incognito fields.", cfg.toolbarVoice) { v ->
            vm.update { it.copy(toolbarVoice = v) }
            if (v && !micGranted()) micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        if (cfg.toolbarVoice && !micOk) {
            OutlinedButton(onClick = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Allow microphone") }
        }
        HorizontalDivider()
        DropdownField("Keyboard theme (System follows your phone)", cfg.keyboardTheme.name, AppSettings.Theme.entries.map { it.name to it }) { t -> vm.update { it.copy(keyboardTheme = t) } }
        DropdownField("App theme", cfg.theme.name, AppSettings.Theme.entries.map { it.name to it }) { t -> vm.update { it.copy(theme = t) } }
        SectionTitle("Accent in dark mode")
        Hint("Light mode uses slate blue. In dark mode, buttons and the Enter key use this colour.")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KeyboardAccentChoices.forEach { c ->
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(Color(c))
                        .border(if (cfg.keyboardAccent == c) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        .clickable { vm.update { it.copy(keyboardAccent = c, accentColor = c) } },
                )
            }
        }
        SwitchRow("Number row", null, cfg.numberRow) { v -> vm.update { it.copy(numberRow = v) } }
        SwitchRow("Haptic feedback", null, cfg.haptics) { v -> vm.update { it.copy(haptics = v) } }
        SwitchRow("Key sound", null, cfg.keySound) { v -> vm.update { it.copy(keySound = v) } }
        Text("Key height: ${cfg.heightPercent}%")
        Slider(cfg.heightPercent.toFloat(), { v -> vm.update { it.copy(heightPercent = v.toInt()) } }, valueRange = 80f..130f)
        Text("Long-press delay: ${cfg.longPressDelayMs} ms")
        Slider(cfg.longPressDelayMs.toFloat(), { v -> vm.update { it.copy(longPressDelayMs = (v / 25).toInt() * 25) } }, valueRange = 200f..600f)
        Hint("Long-press a letter for accents, “.” for punctuation. Hold backspace to repeat.")
    }
}

@Composable
fun PrivacyScreen(vm: SettingsViewModel, nav: NavController) {
    val ctx = LocalContext.current
    val cfg by vm.settings.collectAsState()
    var newPackage by remember { mutableStateOf("") }
    var confirmReset by remember { mutableStateOf(false) }
    ScreenScaffold("Privacy", nav, vm) {
        SectionTitle("What is sent, and where")
        Text("Only when you tap an AI action or finish an inline trigger, the selected text (or the text in the field, up to 4,000 characters) goes over HTTPS straight to the provider you chose. Nothing is sent while you type, and this app has no server of its own. Nothing is saved or logged; the only data kept on the phone is your settings and your encrypted API keys.", style = MaterialTheme.typography.bodyMedium)
        SwitchRow("Consent given", "Turn off to disable all AI features.", cfg.consentGiven) { v -> vm.update { it.copy(consentGiven = v) } }
        SwitchRow("AI reply to clipboard items", "Shows a reply button next to copied text; that text is sent only when you tap it.", cfg.clipboardAccess) { v -> vm.update { it.copy(clipboardAccess = v) } }
        SwitchRow("Clipboard history", "Remember text copied while this keyboard is active, in memory only (cleared when the keyboard restarts). Text marked sensitive, like passwords, is never kept.", cfg.clipboardHistory) { v -> vm.update { it.copy(clipboardHistory = v) } }

        HorizontalDivider()
        SectionTitle("Always off")
        Hint("Password, PIN, non-text and incognito fields — and these apps (starter list):")
        FieldPolicy.DEFAULT_BLOCKED_PACKAGES.sorted().forEach { Hint(it) }

        SectionTitle("Never send text from these apps")
        cfg.blockedPackages.sorted().forEach { pkg ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(pkg, Modifier.weight(1f))
                IconButton(onClick = { vm.update { it.copy(blockedPackages = it.blockedPackages - pkg) } }) { Icon(Icons.Default.Close, "Remove") }
            }
        }
        OutlinedTextField(newPackage, { newPackage = it.trim() }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Package name, e.g. com.mybank.app") })
        Button(enabled = newPackage.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")), onClick = {
            vm.update { it.copy(blockedPackages = it.blockedPackages + newPackage) }; newPackage = ""
        }) { Text("Block app") }

        HorizontalDivider()
        SectionTitle("Provider data policies")
        ProviderCatalog.all.mapNotNull { p -> p.privacyUrl?.let { p.displayName to it } }.forEach { (name, url) ->
            TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) { Text(name) }
        }
        Hint("Each provider decides how long it keeps requests and whether it trains on them. Check the plan you use.")

        HorizontalDivider()
        OutlinedButton(onClick = { confirmReset = true }) { Text("Delete all keys and settings") }
    }
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("Delete everything?") },
        text = { Text("API keys, settings and custom shortcuts are removed and the encryption key is destroyed.") },
        confirmButton = { TextButton(onClick = { confirmReset = false; vm.resetEverything { vm.message.value = "All data deleted" } }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
    )
}

@Composable
fun AboutScreen(nav: NavController) {
    val ctx = LocalContext.current
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?" }
    ScreenScaffold("About", nav) {
        Text("Typosaurus $version", style = MaterialTheme.typography.titleMedium)
        Hint("A keyboard with one-tap AI writing shortcuts. Bring your own key.")
        TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ctx.getString(R.string.privacy_policy_url)))) }) { Text("Privacy policy") }
        HorizontalDivider()
        SectionTitle("Open-source licenses")
        listOf(
            "Kotlin, kotlinx.coroutines, kotlinx.serialization — Apache 2.0",
            "OkHttp — Apache 2.0",
            "Inter typeface — SIL Open Font License 1.1",
            "AndroidX (Compose, Lifecycle, DataStore, Navigation, Activity) — Apache 2.0",
        ).forEach { Hint(it) }
    }
}

@Composable
fun DiagnosticsScreen(vm: SettingsViewModel, nav: NavController) {
    val cfg by vm.settings.collectAsState()
    val m by vm.metrics.collectAsState()
    val actions by vm.actions.collectAsState()
    fun pct(v: Double?) = v?.let { "${(it * 100).toInt()}%" } ?: "—"
    ScreenScaffold("Diagnostics", nav, vm) {
        SwitchRow(
            "Keep usage stats on this phone",
            "Timings, error types and counts only. No text, prompts, results or keys, and nothing is uploaded.",
            cfg.usageStats,
        ) { v -> vm.update { it.copy(usageStats = v) } }

        SectionTitle("Keyboard start")
        Text(
            if (m.coldStart.count == 0) "No data yet"
            else "Average ${m.coldStart.avgMs} ms · slowest ${m.coldStart.maxMs} ms · ${m.coldStart.count} starts (target < 300 ms)",
            style = MaterialTheme.typography.bodyMedium,
        )

        HorizontalDivider()
        SectionTitle("Providers and models")
        if (m.models.isEmpty()) Hint("No requests yet")
        m.models.entries.sortedByDescending { it.value.requests }.forEach { (key, s) ->
            Text(key, style = MaterialTheme.typography.titleSmall)
            Hint(
                "${s.requests} requests · first token ${if (s.firstToken.count > 0) "${s.firstToken.avgMs} ms" else "—"}" +
                    " · total ${if (s.total.count > 0) "${s.total.avgMs} ms (max ${s.total.maxMs})" else "—"}",
            )
            if (s.errors.isNotEmpty()) Hint("Errors: " + s.errors.entries.joinToString { "${it.key} ×${it.value}" })
        }

        HorizontalDivider()
        SectionTitle("Shortcuts")
        if (m.actions.isEmpty()) Hint("No shortcuts used yet")
        val names = actions.associate { it.id to "${it.icon} ${it.name}" }
        m.actions.entries.sortedByDescending { it.value.runs }.forEach { (id, s) ->
            Text(names[id] ?: id, style = MaterialTheme.typography.titleSmall)
            Hint("${s.runs} runs · ${s.applied} applied · replaced in place ${pct(s.replaceSuccessRate)} · undone ${pct(s.undoRate)}")
        }
        Hint("A high undo rate usually means the prompt needs tuning for that shortcut.")

        HorizontalDivider()
        OutlinedButton(onClick = { vm.clearMetrics(); vm.message.value = "Stats cleared" }) { Text("Clear stats") }
    }
}
