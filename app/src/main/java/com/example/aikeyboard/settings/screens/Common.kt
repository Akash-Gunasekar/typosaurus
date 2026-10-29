package com.example.aikeyboard.settings.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.collectAsState
import androidx.navigation.NavController
import com.example.aikeyboard.settings.SettingsViewModel

/** Scaffold with a back arrow and a snackbar host wired to [SettingsViewModel.message]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    nav: NavController?,
    vm: SettingsViewModel? = null,
    actions: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    if (vm != null) {
        val msg by vm.message.collectAsState()
        LaunchedEffect(msg) { msg?.let { snackbar.showSnackbar(it); vm.message.value = null } }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    if (nav != null && nav.previousBackStackEntry != null) {
                        IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    }
                },
                actions = { actions() },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        // Edge-to-edge: the window no longer resizes for the keyboard, so pad for it here and the
        // scrolling column brings the focused field into view above the keys.
        Column(Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
        }
    }
}

@Composable
fun SectionTitle(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

@Composable
fun Hint(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))

@Composable
fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Hint(subtitle)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun NavRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Hint(subtitle)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
    }
}

/** Simple dropdown: current value as a button, options in a menu. */
@Composable
fun <T> DropdownField(label: String, current: String, options: List<Pair<String, T>>, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        Hint(label)
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text(current.ifBlank { "—" }) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { (text, value) -> DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onPick(value) }) }
            }
        }
    }
}
