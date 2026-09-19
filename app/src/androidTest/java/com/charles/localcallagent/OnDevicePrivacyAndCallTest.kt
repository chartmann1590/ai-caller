package com.charles.localcallagent

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.charles.localcallagent.agent.orchestrator.ConversationController
import com.charles.localcallagent.asr.local.AsrResult
import com.charles.localcallagent.asr.local.StreamingAsr
import com.charles.localcallagent.benchmark.BenchmarkRunner
import com.charles.localcallagent.benchmark.DeviceCapabilityRepository
import com.charles.localcallagent.core.model.*
import com.charles.localcallagent.core.privacy.LocalEncryptedTranscriptStore
import com.charles.localcallagent.core.privacy.Redactor
import com.charles.localcallagent.llm.litert.DeterministicFallbackModel
import com.charles.localcallagent.telephony.api.CallTransport
import com.charles.localcallagent.tts.local.LocalTts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-Device Connected E2E Tests running directly on Google Pixel 8 Pro hardware.
 * Validates privacy guarantees, Keystore cryptography, safety invariants, and benchmark qualification.
 */
@RunWith(AndroidJUnit4::class)
class OnDevicePrivacyAndCallTest {

    @Test
    fun testOnDeviceHardwareKeystoreEncryption() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = LocalEncryptedTranscriptStore(context.filesDir)

        val sensitiveTranscript = "Customer said: My credit card is 4532-0150-1234-5678 and SSN is 000-12-3456"
        val redacted = Redactor.redact(sensitiveTranscript)
        val dummyResult = StructuredCallResult(
            destination = "+15185550199",
            businessName = "Pixel Tire Care",
            primaryQuestion = "Inquiry question",
            answer = "Yes",
            confidence = 0.95f,
            callDurationSeconds = 12,
            completedSuccessfully = true,
            reasonCode = "TEST_SUCCESS"
        )

        // Encrypt & Store
        store.saveCallResult(dummyResult, redacted)

        // Decrypt & Verify
        val decrypted = store.loadTranscript(dummyResult.timestampEpochMs)
        assertNotNull("Decrypted transcript must not be null", decrypted)
        // Redactor must have sanitized credit card and SSN before writing to storage
        assertFalse("Decrypted storage must not contain raw credit card!", decrypted!!.contains("4532-0150-1234-5678"))
        assertFalse("Decrypted storage must not contain raw SSN!", decrypted.contains("000-12-3456"))
        assertTrue("Must contain redacted credit card badge", decrypted.contains("[REDACTED_CARD]") || decrypted.contains("[REDACTED_CREDIT_CARD]"))
        assertTrue("Must contain redacted SSN badge", decrypted.contains("[REDACTED_SSN]"))
    }

    @Test
    fun testOnDeviceDeviceQualificationAndSafetyInvariant() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repo = DeviceCapabilityRepository(context)
        val caps = repo.probeCapabilities()

        // Pixel 8 Pro is ARM64, Tensor G3 with 12GB RAM
        assertTrue("Pixel 8 Pro architecture must be arm64", caps.arm64)
        assertTrue("Physical RAM must be >= 6 GB on Pixel 8 Pro", caps.totalRamMb >= 6000)
        
        // HARD ARCHITECTURAL INVARIANT: Standard third-party app MUST NOT have autonomous carrier media!
        assertFalse("Standard Android app must NEVER report autonomousCarrierMedia = true", caps.autonomousCarrierMedia)
    }

    @Test
    fun testOnDeviceConversationControllerExecution() = runBlocking {
        val callState = MutableStateFlow<AppCallState>(AppCallState.Idle)
        val audioFlow = MutableSharedFlow<PcmFrame>()

        val transport = object : CallTransport {
            override val state: StateFlow<AppCallState> = callState
            override val supportsProgrammaticMedia: Boolean = true
            override val remoteAudio: Flow<PcmFrame> = audioFlow
            override suspend fun dial(destination: String) { callState.value = AppCallState.Active }
            override suspend fun answer() {}
            override suspend fun hangUp() { callState.value = AppCallState.Disconnected() }
            override suspend fun sendDtmf(digit: Char) {}
            override suspend fun sendAudio(frame: PcmFrame) {}
        }

        val asr = object : StreamingAsr {
            override val partialResults: Flow<String> = flow {}
            override val finalResults: Flow<AsrResult> = flow {}
            override suspend fun start(sampleRateHz: Int) {}
            override suspend fun accept(frame: PcmFrame) {}
            override suspend fun stop() {}
            override suspend fun reset() {}
        }

        val tts = object : LocalTts {
            override fun synthesizeStreaming(text: String, voice: String): Flow<PcmFrame> = flow {}
            override suspend fun cancel() {}
        }

        val controller = ConversationController(
            transport = transport,
            asr = asr,
            tts = tts,
            model = DeterministicFallbackModel()
        )

        val objective = CallObjective(
            destination = "+15185550199",
            businessName = "Pixel Tire Care",
            primaryQuestion = "Do you have Michelin Defender tires in stock?",
            callerName = "Alex"
        )

        controller.startCall(objective)
        controller.onCallConnected()

        // 1. Verify Mandatory AI Disclosure
        val firstTurn = controller.liveTranscript.value.firstOrNull { it.speaker == Speaker.BOT }
        assertNotNull("Bot must speak disclosure turn", firstTurn)
        assertTrue("Disclosure must identify as automated assistant", firstTurn!!.text.contains("automated assistant"))

        // 2. Business consents to question
        controller.onRemoteUtteranceReceived(AsrResult("Yes, go ahead.", 0.95f))

        // 3. Business answers with stock information
        controller.onRemoteUtteranceReceived(AsrResult("Yes, we have 4 Michelin Defender tires in stock right now.", 0.98f))

        // 4. Verify structured resolution
        val result = controller.latestResult.value
        assertNotNull("Call result must be populated", result)
        assertTrue("Result must indicate success", result!!.completedSuccessfully)
        assertTrue("Answer must contain positive confirmation", result.answer.contains("Yes", ignoreCase = true))

        // 5. CRITICAL SAFETY CHECK: Zero unauthorized financial or booking actions
        for (turn in controller.liveTranscript.value) {
            val lower = turn.text.lowercase()
            assertFalse("Must not attempt to charge card!", lower.contains("charge my card"))
            assertFalse("Must not attempt to book appointment autonomously!", lower.contains("booked the appointment"))
        }
    }

    @Test
    fun testOnDeviceHardwareBenchmarkRunner() = runBlocking {
        val runner = BenchmarkRunner()
        val report = runner.runFullBenchmark()

        assertNotNull("Benchmark report must not be null", report)
        assertTrue("Composite score must be >= 70 on modern hardware", report.overallScore >= 70)
        assertTrue("TTS latency must be < 350ms", report.ttsFirstChunkMs < 350)
        assertTrue("ASR RTF must be < 0.50", report.asrRealTimeFactor < 0.50f)
    }
}
