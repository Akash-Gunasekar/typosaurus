package com.example.aikeyboard.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
            AppTheme(cfg) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val nav = rememberNavController()
                    val start = when {
                        startRoute != null -> "home"
                        !cfg.onboardingDone -> "onboarding"
                        else -> "home"
                    }
                    NavHost(nav, startDestination = start) {
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
        const val EXTRA_ROUTE = "route"
        /** Sent by the keyboard's mic button: ask for the microphone permission on arrival. */
        const val EXTRA_REQUEST_MIC = "request_mic"
    }
}
