package com.example.localcallagent.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.localcallagent.core.model.AppCallState
import com.example.localcallagent.core.model.SipAccountConfig
import com.example.localcallagent.telephony.api.RegistrationState
import com.example.localcallagent.telephony.sip.SipAgentTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Debug-only BroadcastReceiver for headless SIP e2e (no Compose taps).
 *
 * Loopback lab (recommended on emulator without host NetworkAgent):
 * adb shell am broadcast -a com.example.localcallagent.sip.DEBUG_SIP_E2E \
 *   -n com.example.localcallagent.sip/com.example.localcallagent.debug.SipDebugE2eReceiver \
 *   --es sip_user 1001 --es sip_pass secret --es sip_domain 127.0.0.1 \
 *   --ei sip_port 15060 --es sip_dest 1002 --ez sip_loopback true
 *
 * Host lab (when 10.0.2.2 reachable):
 *   --es sip_proxy 10.0.2.2 --ei sip_port 5060 --ez sip_loopback false
 *
 * Watch: adb logcat -s SipDebugE2E:I SipEngine:I LabSipRegistrar:I
 * Never logs passwords.
 */
class SipDebugE2eReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        if (intent.action != ACTION) {
            Log.w(TAG, "Ignoring action=${intent.action}")
            return
        }
        val user = intent.getStringExtra(EXTRA_USER) ?: run {
            Log.e(TAG, "Missing sip_user"); return
        }
        val pass = intent.getStringExtra(EXTRA_PASS) ?: run {
            Log.e(TAG, "Missing sip_pass"); return
        }
        val domain = intent.getStringExtra(EXTRA_DOMAIN) ?: "127.0.0.1"
        val proxy = intent.getStringExtra(EXTRA_PROXY)
        val dest = intent.getStringExtra(EXTRA_DEST)
        val port = intent.getIntExtra(EXTRA_PORT, if (intent.getBooleanExtra(EXTRA_LOOPBACK, true)) 15060 else 5060)
        val display = intent.getStringExtra(EXTRA_DISPLAY) ?: "DebugAgent"
        val loopback = intent.getBooleanExtra(EXTRA_LOOPBACK, proxy.isNullOrBlank() || proxy == "127.0.0.1")

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            var lab: LabSipRegistrar? = null
            try {
                if (loopback) {
                    lab = LabSipRegistrar(bindPort = port).also { it.start() }
                    kotlinx.coroutines.delay(200)
                }
                Log.i(TAG, "START user=$user domain=$domain proxy=$proxy dest=$dest loopback=$loopback port=$port")
                val config = SipAccountConfig(
                    username = user,
                    password = pass,
                    domain = domain,
                    port = port,
                    outboundProxy = if (loopback) null else proxy?.ifBlank { null },
                    displayName = display
                )
                val transport = SipAgentTransport()
                transport.register(config)
                val reg = withTimeoutOrNull(20_000) {
                    transport.registrationState.first {
                        it == RegistrationState.REGISTERED || it == RegistrationState.FAILED
                    }
                } ?: RegistrationState.FAILED
                if (reg != RegistrationState.REGISTERED) {
                    Log.i(TAG, "REGISTER_FAILED")
                    return@launch
                }
                Log.i(TAG, "REGISTERED")

                if (!dest.isNullOrBlank()) {
                    transport.dial(dest)
                    Log.i(TAG, "INVITE_SENT dest=$dest")
                    val call = withTimeoutOrNull(25_000) {
                        transport.state.first {
                            it is AppCallState.Active || it is AppCallState.Disconnected
                        }
                    }
                    when (call) {
                        is AppCallState.Active -> Log.i(TAG, "CALL_ACTIVE")
                        is AppCallState.Disconnected -> Log.i(TAG, "CALL_ENDED reason=${call.reason}")
                        else -> Log.i(TAG, "CALL_TIMEOUT state=${transport.state.value}")
                    }
                    try { transport.hangUp() } catch (_: Exception) {}
                }
                try { transport.unregister() } catch (_: Exception) {}
                Log.i(TAG, "DONE")
            } catch (e: Exception) {
                Log.e(TAG, "ERROR ${e.message ?: e::class.java.simpleName}")
            } finally {
                try { lab?.stop() } catch (_: Exception) {}
                pending.finish()
            }
        }
    }

    companion object {
        const val TAG = "SipDebugE2E"
        const val ACTION = "com.example.localcallagent.sip.DEBUG_SIP_E2E"
        const val EXTRA_USER = "sip_user"
        const val EXTRA_PASS = "sip_pass"
        const val EXTRA_DOMAIN = "sip_domain"
        const val EXTRA_PROXY = "sip_proxy"
        const val EXTRA_DEST = "sip_dest"
        const val EXTRA_PORT = "sip_port"
        const val EXTRA_DISPLAY = "sip_display"
        const val EXTRA_LOOPBACK = "sip_loopback"
    }
}
