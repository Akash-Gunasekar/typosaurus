package com.example.aikeyboard

import android.app.Application
import android.content.Context
import com.example.aikeyboard.core.provider.ProviderRegistry
import com.example.aikeyboard.core.provider.ResilientStreamer
import com.example.aikeyboard.core.runner.ActionRunner
import com.example.aikeyboard.core.runner.ClipboardPort
import com.example.aikeyboard.core.runner.TextPort
import com.example.aikeyboard.data.ApiKeyRepository
import com.example.aikeyboard.data.MetricsStore
import com.example.aikeyboard.data.ModelCache
import com.example.aikeyboard.data.SettingsRepository
import com.example.aikeyboard.ime.ClipboardHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AiKeyboardApp : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }
}

/** Manual dependency graph; shared by the IME service, settings and PROCESS_TEXT. */
class AppGraph(context: Context) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsRepository(context, appScope)
    val keys = ApiKeyRepository(context)
    val modelCache = ModelCache(context)
    val registry = ProviderRegistry(keys, { settings.settings.value })
    val streamer = ResilientStreamer(registry)
    val clipboard = ClipboardHelper(context)
    val metrics = MetricsStore(context, appScope, { settings.settings.value.usageStats })

    fun newRunner(scope: CoroutineScope, text: TextPort, clip: ClipboardPort = clipboard) =
        ActionRunner(scope, text, streamer, settings, clip, metrics = metrics)
}

val Context.graph: AppGraph get() = (applicationContext as AiKeyboardApp).graph
