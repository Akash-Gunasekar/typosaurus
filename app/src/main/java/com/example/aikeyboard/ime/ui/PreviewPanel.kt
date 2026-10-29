package com.example.aikeyboard.ime.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aikeyboard.core.runner.RunnerState

/** Streaming text with Accept / Retry / Copy / Cancel. Shared by the keyboard, PROCESS_TEXT and "Try it". */
@Composable
fun PreviewPanel(
    state: RunnerState,
    inputTruncated: Boolean,
    onAccept: () -> Unit,
    onRetry: () -> Unit,
    onCopy: () -> Unit,
    onCancel: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    acceptLabel: String = "Accept",
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (state) {
            is RunnerState.Streaming -> {
                Header("${state.action.icon} ${state.action.name}", "working…")
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Body(state.partial.ifEmpty { "…" })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCancel) { Icon(Icons.Default.Close, null); Spacer(Modifier.width(4.dp)); Text("Cancel") }
                }
            }
            is RunnerState.Preview -> {
                Header("${state.action.icon} ${state.action.name}", state.via)
                Body(state.result)
                if (inputTruncated) Note("Text truncated: only the last 4,000 characters were sent.")
                if (state.truncated) Note("The result was cut off by the token limit — consider raising Max tokens for this action.")
                // Copy and Cancel are icon-only so the row fits on narrow phones.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onAccept, contentPadding = CompactPadding) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(4.dp)); Text(acceptLabel) }
                    OutlinedButton(onClick = onRetry, contentPadding = CompactPadding) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(4.dp)); Text("Retry") }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, "Copy") }
                    IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "Cancel") }
                }
            }
            is RunnerState.Failed -> {
                Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.canRetry) OutlinedButton(onClick = onRetry) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(4.dp)); Text("Retry") }
                    if (state.openSettings) Button(onClick = onOpenSettings) { Icon(Icons.Default.Settings, null); Spacer(Modifier.width(4.dp)); Text("Open settings") }
                    TextButton(onClick = onCancel) { Text("Dismiss") }
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.width(8.dp))
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Body(text: String) {
    val scroll = rememberScrollState()
    LaunchedEffect(text) { scroll.scrollTo(scroll.maxValue) } // follow the stream
    Column(Modifier.fillMaxWidth().heightIn(max = 96.dp).verticalScroll(scroll)) {
        Text(text, fontSize = 15.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun Note(text: String) =
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))

private val CompactPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
