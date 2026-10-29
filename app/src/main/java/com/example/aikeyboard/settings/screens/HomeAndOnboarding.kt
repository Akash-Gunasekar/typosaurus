package com.example.aikeyboard.settings.screens

import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.example.aikeyboard.ime.ui.maskReveal
import com.example.aikeyboard.ime.ui.popIn
import com.example.aikeyboard.ime.ui.pressScale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.aikeyboard.R
import com.example.aikeyboard.core.provider.ProviderCatalog
import com.example.aikeyboard.settings.SettingsViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(vm: SettingsViewModel, nav: NavController) {
    val ctx = LocalContext.current
    val ime = rememberImeState()
    val cfg by vm.settings.collectAsState()
    val keys by vm.configuredKeys.collectAsState()
    val provider = ProviderCatalog.find(cfg.defaultProviderId)?.displayName ?: cfg.defaultProviderId
    val hasKey = cfg.defaultProviderId in keys
    val ready = ime.enabled && ime.selected && cfg.consentGiven && hasKey

    ScreenScaffold("Typosaurus", nav, vm) {
        // ---- status header: the launch poster, dino in the corner -------------------------
        // The artwork is light, so the card keeps the poster's own colours in both themes.
        Box(Modifier.fillMaxWidth().popIn(0, from = 0.94f).clip(RoundedCornerShape(24.dp)).background(PosterBackground)) {
            // The dino sits in the bottom-right corner; text keeps the left side.
            Image(
                painterResource(R.drawable.header_dino), null,
                Modifier.align(Alignment.BottomEnd).fillMaxWidth(0.48f).popIn(4, stepMs = 60, from = 0.6f, lift = 24.dp),
                contentScale = ContentScale.Fit,
            )
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Headline and subtitle span the card, one line each.
                Text(
                    if (ready) "You're all set" else "Finish setting up", Modifier.maskReveal(80), color = PosterInk,
                    style = MaterialTheme.typography.headlineSmall, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (ready) "Tap ✨ on the keyboard in any app" else "A few steps left before AI works", Modifier.maskReveal(180), color = PosterMuted,
                    style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                )
                // Chips and the button stay on the left, clear of the dino in the corner.
                Column(Modifier.fillMaxWidth(0.52f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusChip("Keyboard", ime.enabled && ime.selected)
                        StatusChip(provider, hasKey)
                        StatusChip("Privacy", cfg.consentGiven)
                    }
                    if (!ready) {
                        Button(
                            onClick = { nav.navigate("onboarding") },
                            colors = ButtonDefaults.buttonColors(containerColor = PosterAccent, contentColor = Color.White),
                        ) { Text("Finish setup") }
                    }
                }
            }
        }

        // ---- sections ----------------------------------------------------------------------
        val tiles = listOf(
            HomeTile("Providers", "Keys and models", Icons.Outlined.Key, "providers"),
            HomeTile("Shortcuts", "AI buttons and prompts", Icons.Outlined.Bolt, "actions"),
            HomeTile("Keyboard", "Look, sound, toolbar", Icons.Outlined.Keyboard, "keyboard"),
            HomeTile("Triggers", "Type /fix then space", Icons.Outlined.Code, "triggers"),
            HomeTile("Defaults", "Fallback, tone, language", Icons.Outlined.Tune, "defaults"),
            HomeTile("Privacy", "What is sent, blocked apps", Icons.Outlined.Shield, "privacy"),
            HomeTile("Diagnostics", "Speed and errors", Icons.Outlined.Insights, "diagnostics"),
            HomeTile("About", "Version and licenses", Icons.Outlined.Info, "about"),
        )
        tiles.chunked(2).forEachIndexed { row, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEachIndexed { col, t ->
                    TileCard(t, Modifier.weight(1f).popIn(2 + row * 2 + col, stepMs = 40, from = 0.9f, lift = 16.dp)) { nav.navigate(t.route) }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        // ---- try it ------------------------------------------------------------------------
        Card(Modifier.fillMaxWidth().popIn(11, stepMs = 40, from = 0.94f, lift = 16.dp), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Try it", style = MaterialTheme.typography.titleMedium)
                Hint("Type here with Typosaurus selected, then tap ✨.")
                var sample by remember { mutableStateOf("") }
                OutlinedTextField(sample, { sample = it }, Modifier.fillMaxWidth(), placeholder = { Text("teh cat sat on teh mat") }, minLines = 3)
                TextButton(onClick = { ctx.getSystemService(InputMethodManager::class.java).showInputMethodPicker() }) { Text("Switch keyboard") }
            }
        }
    }
}

private class HomeTile(val title: String, val subtitle: String, val icon: ImageVector, val route: String)

@Composable
private fun TileCard(t: HomeTile, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Card(onClick = onClick, modifier = modifier.pressScale(interaction), shape = RoundedCornerShape(20.dp), interactionSource = interaction) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Icon and name on one line, description underneath.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(t.icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.width(10.dp))
                Text(t.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                t.subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            )
        }
    }
}

@Composable
private fun StatusChip(label: String, ok: Boolean) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.85f)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (ok) Icons.Default.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null, Modifier.size(14.dp),
            tint = if (ok) PosterAccent else PosterMuted,
        )
        Text(" $label", color = PosterInk, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

// Colours of the launch poster (github_banner / header_dino).
private val PosterBackground = Color(0xFFF2F7FA)
private val PosterInk = Color(0xFF2C3E50)
private val PosterMuted = Color(0xFF5D6D7E)
private val PosterAccent = Color(0xFF4A90E2)


@Composable
fun OnboardingScreen(vm: SettingsViewModel, nav: NavController) {
    val ctx = LocalContext.current
    val ime = rememberImeState()
    val cfg by vm.settings.collectAsState()
    val keys by vm.configuredKeys.collectAsState()
    var sample by remember { mutableStateOf("") }

    ScreenScaffold("Set up", if (nav.previousBackStackEntry != null) nav else null, vm) {
        Step(1, "Enable the keyboard", ime.enabled) {
            Hint("Turn on “Typosaurus” in the system list.")
            Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }) { Text("Open keyboard settings") }
        }
        Step(2, "Select it as your keyboard", ime.selected) {
            Button(enabled = ime.enabled, onClick = { ctx.getSystemService(InputMethodManager::class.java).showInputMethodPicker() }) { Text("Choose keyboard") }
        }
        Step(3, "Privacy notice", cfg.consentGiven) {
            Text(ctx.getString(R.string.keyboard_warning), style = MaterialTheme.typography.bodyMedium)
            Hint("• Text goes directly from your phone to the AI provider you choose, over HTTPS. There is no Typosaurus server.\n" +
                "• Each provider has its own retention and training policy (see Privacy).\n" +
                "• AI is disabled in password, PIN and incognito fields and in apps you block.\n" +
                "• Nothing is saved or logged by this app.")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(cfg.consentGiven, { v -> vm.update { it.copy(consentGiven = v) } })
                Text("I understand and agree")
            }
        }
        Step(4, "Add an AI provider key", cfg.defaultProviderId in keys) {
            Hint("Bring your own key: OpenAI, Anthropic, Groq, OpenRouter, Ollama Cloud, or a custom endpoint.")
            Button(onClick = { nav.navigate("providers") }) { Text("Add a key") }
        }
        Step(5, "Try it", sample.isNotBlank()) {
            OutlinedTextField(sample, { sample = it }, Modifier.fillMaxWidth(), label = { Text("Type “teh cat sat” then tap ✨ Fix") })
        }
        Button(
            onClick = {
                vm.update { it.copy(onboardingDone = true) }
                // Back to the Home that opened Setup, or replace Setup with Home on first run: never two Homes.
                if (!nav.popBackStack("home", inclusive = false)) {
                    nav.navigate("home") { popUpTo("onboarding") { inclusive = true } }
                }
            },
            enabled = cfg.consentGiven,
        ) { Text("Done") }
    }
}

@Composable
private fun Step(n: Int, title: String, done: Boolean, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().popIn(n, stepMs = 60, from = 0.94f, lift = 20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${if (done) "✓" else "$n."} $title", style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
