package com.example.aikeyboard.settings.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.aikeyboard.core.action.Action
import com.example.aikeyboard.core.action.ActionCodec
import com.example.aikeyboard.core.provider.ProviderCatalog
import com.example.aikeyboard.core.runner.RunnerState
import com.example.aikeyboard.ime.InMemoryTextPort
import com.example.aikeyboard.ime.ui.PreviewPanel
import com.example.aikeyboard.settings.SettingsViewModel
import java.util.UUID

@Composable
fun ActionsScreen(vm: SettingsViewModel, nav: NavController) {
    val actions by vm.actions.collectAsState()
    val ctx = LocalContext.current

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri?.let {
            runCatching { ctx.contentResolver.openOutputStream(it)?.use { out -> out.write(vm.exportJson().toByteArray()) } }
                .onSuccess { vm.message.value = "Exported custom shortcuts" }
                .onFailure { vm.message.value = "Export failed" }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            val text = runCatching { ctx.contentResolver.openInputStream(it)?.use { s -> s.readBytes().decodeToString() } }.getOrNull()
            if (text == null) vm.message.value = "Could not read file" else vm.importJson(text)
        }
    }

    ScreenScaffold("Shortcuts", nav, vm) {
        Hint("Shortcuts appear as chips in the keyboard toolbar and as inline triggers (type the keyword after your prefix, then space).")
        Button(onClick = { nav.navigate("action/new") }) { Icon(Icons.Default.Add, null); Text(" New shortcut") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { export.launch("ai-keyboard-shortcuts.json") }) { Text("Export") }
            OutlinedButton(onClick = { import.launch(arrayOf("application/json", "text/*", "*/*")) }) { Text("Import") }
        }
        actions.forEachIndexed { i, a ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable { nav.navigate("action/${a.id}") }) {
                        Text("${a.icon} ${a.name}", style = MaterialTheme.typography.bodyLarge)
                        Hint("/${a.keyword}" + (if (a.builtIn) " · built-in" else "") + (a.model?.let { " · $it" } ?: ""))
                    }
                    IconButton(enabled = i > 0, onClick = { vm.moveAction(a.id, -1) }) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                    IconButton(enabled = i < actions.lastIndex, onClick = { vm.moveAction(a.id, 1) }) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                    Switch(a.inToolbar, { on -> vm.saveAction(a.copy(inToolbar = on)) {} })
                }
            }
        }
    }
}

@Composable
fun ActionEditorScreen(vm: SettingsViewModel, nav: NavController, id: String) {
    val all by vm.actions.collectAsState()
    val cfg by vm.settings.collectAsState()
    val existing = all.firstOrNull { it.id == id }
    val isNew = id == "new"
    val seed = existing ?: Action(
        id = "c_" + UUID.randomUUID().toString().take(8), name = "", icon = "⭐", keyword = "",
        systemPrompt = "Rewrite the user's text as described: ... ${Action.DATA_NOTE} Return ONLY the result.",
    )

    var draft by remember(existing?.id ?: id) { mutableStateOf(seed) }
    var maxTokensText by remember(seed.id) { mutableStateOf(seed.maxTokens.toString()) }
    var confirmDelete by remember { mutableStateOf(false) }

    val keywordTaken = all.any { it.id != draft.id && it.keyword == draft.keyword }
    val keywordError = when {
        draft.keyword.isBlank() -> "Required"
        !ActionCodec.isValidKeyword(draft.keyword) -> "Use a-z, 0-9, - or _ (starts with a letter, max 20)"
        keywordTaken -> "Already used by another shortcut"
        else -> null
    }
    val maxTokens = maxTokensText.toIntOrNull()
    val valid = draft.name.isNotBlank() && keywordError == null && draft.systemPrompt.isNotBlank() && maxTokens != null && maxTokens in 16..8192

    ScreenScaffold(if (isNew) "New shortcut" else draft.name.ifBlank { "Shortcut" }, nav, vm) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(draft.icon, { draft = draft.copy(icon = it.take(4)) }, Modifier.weight(0.3f), singleLine = true, label = { Text("Icon") })
            OutlinedTextField(draft.name, { draft = draft.copy(name = it) }, Modifier.weight(0.7f), singleLine = true, label = { Text("Name") })
        }
        OutlinedTextField(
            draft.keyword, { draft = draft.copy(keyword = it.lowercase().trim()) }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Trigger keyword (after ${cfg.triggerPrefix})") }, prefix = { Text(cfg.triggerPrefix) },
            isError = keywordError != null, supportingText = { keywordError?.let { Text(it) } },
        )
        OutlinedTextField(draft.systemPrompt, { draft = draft.copy(systemPrompt = it) }, Modifier.fillMaxWidth(), minLines = 4, label = { Text("System prompt") })
        OutlinedTextField(draft.userTemplate, { draft = draft.copy(userTemplate = it) }, Modifier.fillMaxWidth(), minLines = 2, label = { Text("Message template") })
        Hint("Variables: {text} {arg} {tone} {language}. Keep {text} inside <text> tags so the model treats it as data, not instructions.")

        val providers = ProviderCatalog.all
        DropdownField("Provider", draft.providerId?.let { pid -> providers.firstOrNull { it.id == pid }?.displayName } ?: "Default",
            listOf<Pair<String, String?>>("Default" to null) + providers.map { it.displayName to it.id }) { draft = draft.copy(providerId = it) }
        OutlinedTextField(draft.model.orEmpty(), { draft = draft.copy(model = it.trim().ifBlank { null }) }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Model (blank = provider default)") })

        Text("Temperature: ${"%.1f".format(draft.temperature)}")
        Slider(draft.temperature.toFloat(), { draft = draft.copy(temperature = (it * 10).toInt() / 10.0) }, valueRange = 0f..1.5f)
        OutlinedTextField(
            maxTokensText, { maxTokensText = it.filter(Char::isDigit).take(4); it.toIntOrNull()?.let { n -> draft = draft.copy(maxTokens = n) } },
            Modifier.fillMaxWidth(), singleLine = true, label = { Text("Max tokens (16–8192)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        SwitchRow("Show in toolbar", null, draft.inToolbar) { draft = draft.copy(inToolbar = it) }
        SwitchRow("Auto-apply", "Skip the preview. Still previews if the result looks too long or was cut off.", draft.autoApply) { draft = draft.copy(autoApply = it) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = valid, onClick = {
                vm.saveAction(draft) { r -> r.onSuccess { nav.popBackStack() }.onFailure { vm.message.value = it.message } }
            }) { Text("Save") }
            if (existing?.builtIn == true) OutlinedButton(onClick = { vm.resetBuiltIn(existing.id); nav.popBackStack() }) { Text("Reset to default") }
            if (existing != null && !existing.builtIn) TextButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, null); Text(" Delete") }
        }

        HorizontalDivider()
        TryIt(vm, draft)
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete “${draft.name}”?") },
        confirmButton = { TextButton(onClick = { vm.deleteAction(draft.id); confirmDelete = false; nav.popBackStack() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

/** Runs the (unsaved) shortcut on sample text through the real provider, without needing another app. */
@Composable
private fun TryIt(vm: SettingsViewModel, action: Action) {
    val scope = rememberCoroutineScope()
    var sample by remember { mutableStateOf("pls send me teh report tmrw") }
    var arg by remember { mutableStateOf("") }
    val port = remember { InMemoryTextPort(sample) }
    val runner = remember { vm.newRunnerFor(port, scope) }
    val state by runner.state.collectAsState()
    val result by port.text.collectAsState()

    SectionTitle("Try it")
    OutlinedTextField(sample, { sample = it; port.text.value = it }, Modifier.fillMaxWidth(), minLines = 2, label = { Text("Sample text") })
    OutlinedTextField(arg, { arg = it.trim() }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Argument (e.g. formal, ta) — optional") })
    Button(enabled = sample.isNotBlank(), onClick = { port.text.value = sample; runner.run(action, arg.ifBlank { null }) }) { Text("Run") }
    PreviewPanel(
        state = state, inputTruncated = false,
        onAccept = runner::accept, onRetry = runner::retry, onCopy = runner::copyResult,
        onCancel = runner::cancel, onOpenSettings = {}, acceptLabel = "Use result",
    )
    if (state is RunnerState.Applied) {
        Text("Result:", style = MaterialTheme.typography.labelMedium)
        Text(result)
    }
}
