package com.example.aikeyboard.settings.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.aikeyboard.BuildConfig
import com.example.aikeyboard.core.provider.BaseUrlPolicy
import com.example.aikeyboard.core.provider.ModelQuirks
import com.example.aikeyboard.core.provider.ProviderCatalog
import com.example.aikeyboard.settings.SettingsViewModel

@Composable
fun ProvidersScreen(vm: SettingsViewModel, nav: NavController) {
    val cfg by vm.settings.collectAsState()
    val keys by vm.configuredKeys.collectAsState()
    ScreenScaffold("Providers", nav, vm) {
        Hint("Bring your own key. Keys are encrypted with the Android Keystore and never leave this phone except to call your chosen provider.")
        ProviderCatalog.all.forEach { p ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    NavRow(
                        title = p.displayName + if (p.id == cfg.defaultProviderId) "  ·  default" else "",
                        subtitle = buildString {
                            append(if (p.id in keys) "Key saved" else if (p.keyRequired) "No key" else "No key needed")
                            cfg.modelFor(p.id)?.let { append(" · $it") }
                        },
                    ) { nav.navigate("provider/${p.id}") }
                }
            }
        }
    }
}

@Composable
fun ProviderScreen(vm: SettingsViewModel, nav: NavController, id: String) {
    val info = ProviderCatalog.find(id)
    if (info == null) { ScreenScaffold("Provider", nav) { Text("Unknown provider") }; return }
    val ctx = LocalContext.current
    val cfg by vm.settings.collectAsState()
    val keys by vm.configuredKeys.collectAsState()
    val models by vm.models.collectAsState()
    val busy by vm.busy.collectAsState()

    var keyInput by remember { mutableStateOf("") }
    var masked by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(id, keys) { masked = vm.maskedKey(id); vm.loadCachedModels(id) }

    var baseUrl by remember(cfg.baseUrls[id]) { mutableStateOf(cfg.baseUrls[id] ?: info.defaultBaseUrl) }
    val urlError = baseUrl.takeIf { it.isNotBlank() }?.let(BaseUrlPolicy::validate)
    val urlEditable = info.baseUrlEditable || BuildConfig.ALLOW_BASE_URL_OVERRIDE
    val model = cfg.modelFor(id).orEmpty()

    ScreenScaffold(info.displayName, nav, vm) {
        SectionTitle("API key")
        if (masked != null) Text("Saved: $masked", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            keyInput, { keyInput = it }, Modifier.fillMaxWidth(),
            label = { Text(if (info.keyRequired) "API key" else "API key (optional)") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = keyInput.isNotBlank(), onClick = { vm.saveKey(id, keyInput) { keyInput = "" } }) { Text("Save key") }
            OutlinedButton(enabled = id in keys, onClick = { vm.deleteKey(id) }) { Text("Delete key") }
        }

        if (urlEditable) {
            SectionTitle("Base URL")
            OutlinedTextField(
                baseUrl, { baseUrl = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Base URL") },
                isError = urlError != null, supportingText = { urlError?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = urlError == null && baseUrl.isNotBlank(), onClick = {
                    vm.update { s -> s.copy(baseUrls = s.baseUrls + (id to baseUrl.trim())) }
                }) { Text("Save URL") }
                if (info.defaultBaseUrl.isNotBlank()) TextButton(onClick = {
                    baseUrl = info.defaultBaseUrl; vm.update { s -> s.copy(baseUrls = s.baseUrls - id) }
                }) { Text("Reset") }
            }
        }

        SectionTitle("Connection")
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Button(enabled = !busy && (id in keys || !info.keyRequired), onClick = { vm.testConnection(id) }) { Text("Test connection") }
            if (busy) { Spacer(Modifier.width(12.dp)); CircularProgressIndicator(Modifier.size(24.dp)) }
        }

        SectionTitle("Default model")
        val list = models[id].orEmpty()
        if (list.isNotEmpty()) {
            DropdownField("Pick from ${list.size} available models", model, list.map { it to it }) { picked ->
                vm.update { s -> s.copy(defaultModels = s.defaultModels + (id to picked)) }
            }
        } else Hint("Tap “Test connection” to load this provider's model list, or type a model id below.")
        var typed by remember(model) { mutableStateOf(model) }
        OutlinedTextField(typed, { typed = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Model id") })
        Button(enabled = typed.isNotBlank() && typed != model, onClick = {
            vm.update { s -> s.copy(defaultModels = s.defaultModels + (id to typed.trim())) }
        }) { Text("Use this model") }

        if (model.isNotBlank() && info.kind == com.example.aikeyboard.core.provider.ProviderKind.OpenAiCompatible) {
            HorizontalDivider()
            SectionTitle("Advanced · $model")
            Hint("Some newer models reject max_tokens or temperature. Switch these if you see “Request rejected” errors.")
            val q = cfg.quirksFor(id, model)
            fun setQuirks(n: ModelQuirks) = vm.update { s -> s.copy(modelQuirks = s.modelQuirks + ("$id:$model" to n)) }
            SwitchRow("Send max_completion_tokens", "Instead of max_tokens", q.useMaxCompletionTokens) { setQuirks(q.copy(useMaxCompletionTokens = it)) }
            SwitchRow("Omit temperature", "For models that only accept the default", q.omitTemperature) { setQuirks(q.copy(omitTemperature = it)) }
        }

        SectionTitle("Make default")
        Button(enabled = cfg.defaultProviderId != id, onClick = { vm.update { it.copy(defaultProviderId = id) } }) {
            Text(if (cfg.defaultProviderId == id) "Default provider" else "Use as default provider")
        }
        info.privacyUrl?.let { url ->
            TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) { Text("Provider privacy policy") }
        }
    }
}

@Composable
fun DefaultsScreen(vm: SettingsViewModel, nav: NavController) {
    val cfg by vm.settings.collectAsState()
    val providers = ProviderCatalog.all
    ScreenScaffold("Defaults", nav, vm) {
        DropdownField("Default provider", providers.firstOrNull { it.id == cfg.defaultProviderId }?.displayName.orEmpty(),
            providers.map { it.displayName to it.id }) { id -> vm.update { it.copy(defaultProviderId = id) } }
        Hint("Model: ${cfg.modelFor(cfg.defaultProviderId) ?: "not chosen — set it under Providers"}")

        DropdownField("Fallback provider (used on rate limits and server errors)",
            providers.firstOrNull { it.id == cfg.fallbackProviderId }?.displayName ?: "None",
            listOf<Pair<String, String?>>("None" to null) + providers.map { it.displayName to it.id }) { id -> vm.update { it.copy(fallbackProviderId = id) } }
        Hint("Tip: a fast, cheap model for Fix / Shorten and a stronger one for Detail / Reply — set per shortcut under Shortcuts.")

        SwitchRow("Stream results", "Show text as it arrives. Off waits for the full reply.", cfg.streaming) { v -> vm.update { it.copy(streaming = v) } }

        var tone by remember(cfg.defaultTone) { mutableStateOf(cfg.defaultTone) }
        OutlinedTextField(tone, { tone = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Default tone") })
        var lang by remember(cfg.defaultLanguage) { mutableStateOf(cfg.defaultLanguage) }
        OutlinedTextField(lang, { lang = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Default translation language") })
        Button(onClick = { vm.update { it.copy(defaultTone = tone.trim().ifBlank { "friendly" }, defaultLanguage = lang.trim().ifBlank { "English" }) } }) { Text("Save") }
    }
}
