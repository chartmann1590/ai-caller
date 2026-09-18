package com.example.localcallagent.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.localcallagent.ui.navigation.Screen
import com.example.localcallagent.ui.screens.*
import com.example.localcallagent.ui.theme.DarkBackground
import com.example.localcallagent.ui.theme.LocalCallAgentTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            LocalCallAgentTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    var currentScreen by remember { mutableStateOf(Screen.ONBOARDING) }

                    Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
                        when (screen) {
                            Screen.ONBOARDING -> {
                                OnboardingScreen(
                                    viewModel = viewModel,
                                    onComplete = { currentScreen = Screen.TASK_CREATION }
                                )
                            }
                            Screen.TASK_CREATION -> {
                                TaskCreationScreen(
                                    viewModel = viewModel,
                                    onStartCall = { currentScreen = Screen.LIVE_CALL },
                                    onOpenSettings = { currentScreen = Screen.SETTINGS }
                                )
                            }
                            Screen.LIVE_CALL -> {
                                LiveCallScreen(
                                    viewModel = viewModel,
                                    onCallEnded = { currentScreen = Screen.RESULT_SUMMARY }
                                )
                            }
                            Screen.RESULT_SUMMARY -> {
                                ResultSummaryScreen(
                                    viewModel = viewModel,
                                    onNewCall = { currentScreen = Screen.TASK_CREATION }
                                )
                            }
                            Screen.SETTINGS -> {
                                SettingsScreen(
                                    viewModel = viewModel,
                                    onBack = { currentScreen = Screen.TASK_CREATION }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
