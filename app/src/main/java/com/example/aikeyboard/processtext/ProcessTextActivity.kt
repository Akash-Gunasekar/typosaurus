package com.example.aikeyboard.processtext

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.aikeyboard.core.action.Action
import com.example.aikeyboard.core.runner.RunnerState
import com.example.aikeyboard.graph
import com.example.aikeyboard.ime.ClipboardHelper
import com.example.aikeyboard.ime.InMemoryTextPort
import com.example.aikeyboard.ime.ui.AppTheme
import com.example.aikeyboard.ime.ui.Layouts
import com.example.aikeyboard.ime.ui.PreviewPanel
import com.example.aikeyboard.settings.SettingsActivity

/**
 * "AI Fix" in the long-press text menu of other apps (works even when another keyboard is active).
 * Editable fields get the result back via EXTRA_PROCESS_TEXT; read-only text gets it on the clipboard.
 */
class ProcessTextActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val input = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        val readOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
        if (input.isNullOrBlank()) { finish(); return }

        setContent {
            val g = graph
            val cfg by g.settings.settings.collectAsState()
            val actions by g.settings.actions.collectAsState()
            val scope = rememberCoroutineScope()
            val port = remember { InMemoryTextPort(input) }
            val clip = remember { ClipboardHelper(this) }
            val runner = remember { g.newRunner(scope, port, clip) }
            val state by runner.state.collectAsState()
            var pendingArg by remember { mutableStateOf<Action?>(null) }

            LaunchedEffect(state) {
                if (state is RunnerState.Applied) {
                    val result = port.text.value
                    if (readOnly) clip.write(result)
                    else setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, result))
                    finish()
                }
            }

            AppTheme(cfg) {
                Box(Modifier.fillMaxSize().clickable { finish() }, contentAlignment = Alignment.BottomCenter) {
                    Surface(
                        Modifier.fillMaxWidth().clickable(enabled = false) {},
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(input.take(160) + if (input.length > 160) "…" else "", style = MaterialTheme.typography.bodySmall, maxLines = 3)
                            when (state) {
                                is RunnerState.Idle -> {
                                    val options = pendingArg?.let { a -> if ("{tone}" in a.systemPrompt) Layouts.tones else Layouts.languages }
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (pendingArg == null) items(actions, key = { it.id }) { a ->
                                            AssistChip(onClick = {
                                                if ("{tone}" in a.systemPrompt || "{language}" in a.systemPrompt) pendingArg = a else runner.run(a)
                                            }, label = { Text("${a.icon} ${a.name}") })
                                        } else items(options.orEmpty()) { o ->
                                            AssistChip(onClick = { runner.run(pendingArg!!, o); pendingArg = null }, label = { Text(o) })
                                        }
                                    }
                                }
                                is RunnerState.Applied -> Text("Done ✓")
                                else -> PreviewPanel(
                                    state = state, inputTruncated = runner.inputTruncated,
                                    onAccept = runner::accept, onRetry = runner::retry, onCopy = runner::copyResult,
                                    onCancel = { runner.cancel() },
                                    onOpenSettings = {
                                        startActivity(Intent(this@ProcessTextActivity, SettingsActivity::class.java)); finish()
                                    },
                                    acceptLabel = if (readOnly) "Copy result" else "Replace",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
