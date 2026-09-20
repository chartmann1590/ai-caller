package com.charles.localcallagent.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.charles.localcallagent.BuildConfig
import com.charles.localcallagent.core.model.SipAccountConfig
import com.charles.localcallagent.ui.navigation.Screen
import com.charles.localcallagent.ui.screens.*
import com.charles.localcallagent.ui.theme.DarkBackground
import com.charles.localcallagent.ui.theme.LocalCallAgentTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var debugLabRegistrar: Any? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (BuildConfig.DEBUG) {
            maybeHandleDebugSipExtras(intent)
        }

        setContent {
            LocalCallAgentTheme {
                Surface(
                    // Every screen renders inside this Surface via Crossfade, so applying
                    // safeDrawingPadding() once here keeps content clear of the status bar
                    // and nav bar everywhere instead of patching each screen individually.
                    modifier = Modifier.fillMaxSize().safeDrawingPadding(),
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (BuildConfig.DEBUG) {
            maybeHandleDebugSipExtras(intent)
        }
    }

    /**
     * Headless e2e via:
     * adb shell am start -n com.charles.localcallagent.sip/.ui.MainActivity \
     *   --es sip_user 1001 --es sip_pass … --es sip_domain 127.0.0.1 \
     *   --es sip_proxy 10.0.2.2 --es sip_dest 1002
     * Password is never logged.
     */
    private fun maybeHandleDebugSipExtras(intent: Intent?) {
        if (intent == null) return
        val user = intent.getStringExtra(EXTRA_SIP_USER) ?: return
        val pass = intent.getStringExtra(EXTRA_SIP_PASS) ?: return
        val domain = intent.getStringExtra(EXTRA_SIP_DOMAIN) ?: return
        val proxy = intent.getStringExtra(EXTRA_SIP_PROXY)
        val dest = intent.getStringExtra(EXTRA_SIP_DEST)
        val port = intent.getIntExtra(EXTRA_SIP_PORT, 5060)
        val display = intent.getStringExtra(EXTRA_SIP_DISPLAY) ?: "DebugAgent"
        val loopback = intent.getBooleanExtra(EXTRA_SIP_LOOPBACK, proxy.isNullOrBlank() || proxy == "127.0.0.1")
        val effectivePort = if (intent.hasExtra(EXTRA_SIP_PORT)) port else if (loopback) 15060 else 5060
        Log.i(MainViewModel.DEBUG_TAG, "INTENT_E2E user=$user domain=$domain proxy=$proxy dest=$dest loopback=$loopback port=$effectivePort")
        if (loopback) {
            try {
                val clazz = Class.forName("com.charles.localcallagent.debug.LabSipRegistrar")
                val ctor = clazz.getConstructor(String::class.java, Int::class.javaPrimitiveType)
                val lab = ctor.newInstance("127.0.0.1", effectivePort)
                clazz.getMethod("start").invoke(lab)
                // Keep reference so GC does not stop the thread early
                debugLabRegistrar = lab
            } catch (e: Exception) {
                Log.e(MainViewModel.DEBUG_TAG, "Loopback lab start failed: ${e.message}")
            }
        }
        val config = SipAccountConfig(
            username = user,
            password = pass,
            domain = domain,
            port = effectivePort,
            outboundProxy = if (loopback) null else proxy?.ifBlank { null },
            displayName = display
        )
        viewModel.runDebugSipE2e(config, dest)
    }

    companion object {
        const val EXTRA_SIP_USER = "sip_user"
        const val EXTRA_SIP_PASS = "sip_pass"
        const val EXTRA_SIP_DOMAIN = "sip_domain"
        const val EXTRA_SIP_PROXY = "sip_proxy"
        const val EXTRA_SIP_DEST = "sip_dest"
        const val EXTRA_SIP_PORT = "sip_port"
        const val EXTRA_SIP_DISPLAY = "sip_display"
        const val EXTRA_SIP_LOOPBACK = "sip_loopback"
    }
}
