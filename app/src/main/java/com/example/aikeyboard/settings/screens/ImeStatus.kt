package com.example.aikeyboard.settings.screens

import android.content.Context
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

object ImeStatus {
    fun isEnabled(ctx: Context): Boolean =
        ctx.getSystemService(InputMethodManager::class.java).enabledInputMethodList.any { it.packageName == ctx.packageName }

    fun isSelected(ctx: Context): Boolean =
        Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.startsWith(ctx.packageName + "/") == true
}

data class ImeState(val enabled: Boolean, val selected: Boolean)

/** Re-checked every time the user returns from system settings. */
@Composable
fun rememberImeState(): ImeState {
    val ctx = LocalContext.current
    var state by remember { mutableStateOf(ImeState(ImeStatus.isEnabled(ctx), ImeStatus.isSelected(ctx))) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) state = ImeState(ImeStatus.isEnabled(ctx), ImeStatus.isSelected(ctx))
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return state
}
