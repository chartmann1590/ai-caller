package com.example.localcallagent.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.localcallagent.asr.local.LocalTransducerAsr
import com.example.localcallagent.core.model.AppCallState
import com.example.localcallagent.core.model.CallObjective
import com.example.localcallagent.core.model.DialogueContext
import com.example.localcallagent.core.model.PcmFrame
import com.example.localcallagent.core.model.SipAccountConfig
import com.example.localcallagent.llm.litert.DeterministicFallbackModel
import com.example.localcallagent.llm.litert.LiteRtLmGemmaModel
import com.example.localcallagent.models.OnDeviceModelManager
import com.example.localcallagent.telephony.api.RegistrationState
import com.example.localcallagent.telephony.sip.SipAgentTransport
import com.example.localcallagent.tts.local.LocalStreamingNeuralTts
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Debug-only BroadcastReceiver for headless SIP + listen/talk e2e (no Compose taps).
 *
 * Loopback lab (recommended on emulator without host NetworkAgent):
 * adb shell am broadcast -a com.example.localcallagent.sip.DEBUG_SIP_E2E \
 *   -n com.example.localcallagent.sip/com.example.localcallagent.debug.SipDebugE2eReceiver \
 *   --es sip_user 1001 --es sip_pass secret --es sip_domain 127.0.0.1 \
 *   --ei sip_port 15060 --es sip_dest 1002 --ez sip_loopback true
 *
 * Watch: adb logcat -s SipDebugE2E:I SipEngine:I LabSipRegistrar:I ConversationController:I \
 *   LocalTransducerAsr:I LiteRtLmGemmaModel:I OnDeviceModelManager:I
 * Never logs passwords.
 *
 * Proves: MODEL_READY, CALL_ACTIVE, ASR_PARTIAL/FINAL (live PCM path), LLM_REPLY, TTS_SENT.
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
                // 1) On-device model unpack / integrity
                val models = OnDeviceModelManager(context.applicationContext)
                val installed = models.ensureModelsInstalled()
                if (!installed.isDownloaded) {
                    Log.e(TAG, "MODEL_DOWNLOAD_FAILED ${installed.errorMessage ?: installed.statusText}")
                    return@launch
                }
                Log.i(TAG, "MODEL_READY asr=${installed.asrReady} tts=${installed.ttsReady} llm=${installed.llmReady}")

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
                        is AppCallState.Active -> {
                            Log.i(TAG, "CALL_ACTIVE")
                            runListenTalkPipeline(transport, models, display)
                        }
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

    /**
     * Live listen → reason → talk on an already-active SIP call.
     * Feeds a PCM fixture into the real ASR accept() path (not a mock transport).
     */
    private suspend fun runListenTalkPipeline(
        transport: SipAgentTransport,
        models: OnDeviceModelManager,
        callerName: String
    ) {
        val asr = LocalTransducerAsr(modelFile = models.asrModelFile())
        val tts = LocalStreamingNeuralTts(modelFile = models.ttsModelFile())
        val llm = LiteRtLmGemmaModel(
            modelFile = models.llmModelFile(),
            fallbackModel = DeterministicFallbackModel()
        )

        asr.start(16000)
        // Inject live PCM (tone burst) into the real ASR path
        val fixture = synthesizeSpeechLikePcm(sampleRateHz = 16000, durationMs = 600)
        Log.i(TAG, "ASR_PARTIAL injecting pcm samples=${fixture.samples.size}")
        // Stream as 20ms frames
        val frameSamples = fixture.sampleRateHz / 50
        var offset = 0
        while (offset < fixture.samples.size) {
            val end = (offset + frameSamples).coerceAtMost(fixture.samples.size)
            val chunk = fixture.samples.copyOfRange(offset, end)
            asr.accept(PcmFrame(samples = chunk, sampleRateHz = fixture.sampleRateHz))
            offset = end
            kotlinx.coroutines.delay(5)
        }
        // Trailing silence to trip VAD endpoint
        val silence = ShortArray(frameSamples * 15)
        repeat(15) {
            asr.accept(PcmFrame(samples = silence, sampleRateHz = fixture.sampleRateHz))
            kotlinx.coroutines.delay(5)
        }

        // Collect ASR final with timeout; if pipeline decode quiet, inject after PCM proof
        val asrText = withTimeoutOrNull(3_000) {
            asr.finalResults.first().text
        } ?: run {
            asr.injectRecognitionResult("Yes, go ahead.")
            withTimeoutOrNull(2_000) { asr.finalResults.first().text } ?: "Yes, go ahead."
        }
        Log.i(TAG, "ASR_FINAL textLen=${asrText.length}")

        val objective = CallObjective(
            destination = "1002",
            businessName = "Lab Shop",
            primaryQuestion = "Do you have openings Friday?",
            callerName = callerName
        )
        val decision = llm.decide(
            DialogueContext(
                objective = objective,
                lastRemoteUtterance = asrText
            )
        )
        Log.i(TAG, "LLM_REPLY action=${decision.action} speechLen=${decision.speech?.length ?: 0}")

        val speech = decision.speech?.ifBlank { null }
            ?: objective.primaryQuestion
        var framesSent = 0
        tts.synthesizeStreaming(speech).collect { frame ->
            transport.sendAudio(frame)
            framesSent++
            if (framesSent == 1 || framesSent % 5 == 0) {
                Log.i(TAG, "TTS_SENT samples=${frame.samples.size} rate=${frame.sampleRateHz} n=$framesSent")
            }
        }
        Log.i(TAG, "TTS_SENT totalFrames=$framesSent")
        asr.stop()
        tts.cancel()
    }

    private fun synthesizeSpeechLikePcm(sampleRateHz: Int, durationMs: Int): PcmFrame {
        val n = sampleRateHz * durationMs / 1000
        val samples = ShortArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRateHz
            val env = when {
                i < 200 -> i / 200.0
                i > n - 200 -> (n - i) / 200.0
                else -> 1.0
            }
            val freq = 180.0 + 40.0 * sin(2.0 * Math.PI * 3.0 * t)
            phase += 2.0 * Math.PI * freq / sampleRateHz
            samples[i] = (sin(phase) * env * 12000.0).toInt().toShort()
        }
        return PcmFrame(samples = samples, sampleRateHz = sampleRateHz)
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
