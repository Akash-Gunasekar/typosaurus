package com.example.aikeyboard.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.graphics.Color
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.IntOffset
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.aikeyboard.core.action.AppSettings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.aikeyboard.ime.ui.AppTheme
import com.example.aikeyboard.settings.screens.ActionEditorScreen
import com.example.aikeyboard.settings.screens.ActionsScreen
import com.example.aikeyboard.settings.screens.AboutScreen
import com.example.aikeyboard.settings.screens.DefaultsScreen
import com.example.aikeyboard.settings.screens.DiagnosticsScreen
import com.example.aikeyboard.settings.screens.HomeScreen
import com.example.aikeyboard.settings.screens.KeyboardPrefsScreen
import com.example.aikeyboard.settings.screens.OnboardingScreen
import com.example.aikeyboard.settings.screens.PrivacyScreen
import com.example.aikeyboard.settings.screens.ProviderScreen
import com.example.aikeyboard.settings.screens.ProvidersScreen
import com.example.aikeyboard.settings.screens.TriggersScreen

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startRoute = intent.getStringExtra(EXTRA_ROUTE)
        val requestMic = savedInstanceState == null && intent.getBooleanExtra(EXTRA_REQUEST_MIC, false)
        setContent {
            val vm: SettingsViewModel = viewModel()
            androidx.compose.runtime.LaunchedEffect(Unit) { if (requestMic) vm.requestMic.value = true }
            val cfg by vm.settings.collectAsState()
            val dark = when (cfg.theme) {
                AppSettings.Theme.System -> isSystemInDarkTheme()
                AppSettings.Theme.Light -> false
                AppSettings.Theme.Dark -> true
            }
            // System bar icons follow the app's own theme, not the phone's.
            androidx.compose.runtime.LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            AppTheme(cfg) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    // Choose the start screen once, after settings have loaded. Recomputing it from live settings
                    // swapped the graph mid-session and left Home on the back stack twice.
                    val loaded by vm.settingsLoaded.collectAsState()
                    if (!loaded) return@Surface
                    val start = rememberSaveable {
                        when {
                            startRoute != null -> "home"
                            !cfg.onboardingDone -> "onboarding"
                            else -> "home"
                        }
                    }
                    val nav = rememberNavController()
                    NavHost(
                        nav, startDestination = start,
                        // App navigation like the promo: the new screen slides in from the right while the old one
                        // drifts a little to the left; back reverses it.
                        enterTransition = { slideInHorizontally(NavMotion) { it } + fadeIn(NavFade) },
                        exitTransition = { slideOutHorizontally(NavMotion) { -it / 4 } + fadeOut(NavFade) },
                        popEnterTransition = { slideInHorizontally(NavMotion) { -it / 4 } + fadeIn(NavFade) },
                        popExitTransition = { slideOutHorizontally(NavMotion) { it } + fadeOut(NavFade) },
                    ) {
                        composable("home") { HomeScreen(vm, nav) }
                        composable("onboarding") { OnboardingScreen(vm, nav) }
                        composable("providers") { ProvidersScreen(vm, nav) }
                        composable("provider/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
                            ProviderScreen(vm, nav, it.arguments?.getString("id").orEmpty())
                        }
                        composable("defaults") { DefaultsScreen(vm, nav) }
                        composable("actions") { ActionsScreen(vm, nav) }
                        composable("action/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
                            ActionEditorScreen(vm, nav, it.arguments?.getString("id").orEmpty())
                        }
                        composable("triggers") { TriggersScreen(vm, nav) }
                        composable("keyboard") { KeyboardPrefsScreen(vm, nav) }
                        composable("privacy") { PrivacyScreen(vm, nav) }
                        composable("diagnostics") { DiagnosticsScreen(vm, nav) }
                        composable("about") { AboutScreen(nav) }
                    }
                    if (startRoute != null) {
                        androidx.compose.runtime.LaunchedEffect(Unit) {
                            if (startRoute != "home") nav.navigate(startRoute)
                        }
                    }
                }
            }
        }
    }

    companion object {
        private val NavMotion = tween<IntOffset>(420, easing = FastOutSlowInEasing)
        private val NavFade = tween<Float>(300)

        const val EXTRA_ROUTE = "route"
        /** Sent by the keyboard's mic button: ask for the microphone permission on arrival. */
        const val EXTRA_REQUEST_MIC = "request_mic"
    }
}
