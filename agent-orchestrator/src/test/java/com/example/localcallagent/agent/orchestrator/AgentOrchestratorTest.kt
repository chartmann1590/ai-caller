package com.example.localcallagent.agent.orchestrator

import com.example.localcallagent.asr.local.AsrResult
import com.example.localcallagent.asr.local.StreamingAsr
import com.example.localcallagent.core.model.AgentAction
import com.example.localcallagent.core.model.AgentDecision
import com.example.localcallagent.core.model.AppCallState
import com.example.localcallagent.core.model.CallObjective
import com.example.localcallagent.core.model.PcmFrame
import com.example.localcallagent.llm.litert.DeterministicFallbackModel
import com.example.localcallagent.telephony.api.CallTransport
import com.example.localcallagent.tts.local.LocalTts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentOrchestratorTest {

    private val testObjective = CallObjective(
        destination = "+15185551234",
        businessName = "Mike's Tires",
        primaryQuestion = "Do you install customer tires?",
        allowedFollowUps = listOf("How much is it for four tires?")
    )

    private val mockCallState = MutableStateFlow<AppCallState>(AppCallState.Idle)
    private val mockAudioFlow = MutableSharedFlow<PcmFrame>()
    val sentAudio = mutableListOf<PcmFrame>()
    val sentDtmf = mutableListOf<Char>()

    private val mockTransport = object : CallTransport {
        override val state: StateFlow<AppCallState> = mockCallState
        override val supportsProgrammaticMedia: Boolean = true
        override val remoteAudio: Flow<PcmFrame> = mockAudioFlow

        override suspend fun dial(destination: String) {
            mockCallState.value = AppCallState.Active
        }

        override suspend fun answer() {}
        override suspend fun hangUp() {
            mockCallState.value = AppCallState.Disconnected("Hangup")
        }

        override suspend fun sendDtmf(digit: Char) {
            sentDtmf.add(digit)
        }

        override suspend fun sendAudio(frame: PcmFrame) {
            sentAudio.add(frame)
        }
    }

    private val mockAsr = object : StreamingAsr {
        val finalFlow = MutableSharedFlow<AsrResult>()
        override val partialResults: Flow<String> = flow {}
        override val finalResults: Flow<AsrResult> = finalFlow
        override suspend fun start(sampleRateHz: Int) {}
        override suspend fun accept(frame: PcmFrame) {}
        override suspend fun stop() {}
        override suspend fun reset() {}
    }

    private val mockTts = object : LocalTts {
        override fun synthesizeStreaming(text: String, voice: String): Flow<PcmFrame> = flow {
            emit(PcmFrame(ShortArray(160), 16000))
        }
        override suspend fun cancel() {}
    }

    @Test
    fun testSafetyValidatorBlocksUnauthorizedPurchases() {
        val badDecision = AgentDecision(
            action = AgentAction.SPEAK,
            speech = "I agree to buy the tires. Charge my card 12345.",
            reasonCode = "TEST"
        )
        val validated = SafetyValidator.validate(badDecision, testObjective)
        assertEquals(AgentAction.HANDOFF, validated.action)
        assertEquals("SAFETY_VIOLATION_PURCHASE_ATTEMPT_BLOCKED", validated.reasonCode)
    }

    @Test
    fun testFullConversationFlowToSuccess() = runBlocking {
        val controller = ConversationController(
            transport = mockTransport,
            asr = mockAsr,
            tts = mockTts,
            model = DeterministicFallbackModel()
        )

        controller.startCall(testObjective)
        controller.onCallConnected()
        assertEquals(AgentState.LISTENING, controller.agentState.value)

        // Turn 1: Bot said disclosure, remote says "Yes, go ahead"
        controller.onRemoteUtteranceReceived(AsrResult("Yes, go ahead.", 0.95f))
        assertEquals(AgentState.LISTENING, controller.agentState.value)

        // Turn 2: Remote says "Yes, we install them. It is $35 each."
        controller.onRemoteUtteranceReceived(AsrResult("Yes, we install them. It is $35 each.", 0.95f))

        // Bot finishes and records price
        assertEquals(AgentState.COMPLETE, controller.agentState.value)
        val result = controller.latestResult.value
        assertNotNull(result)
        assertTrue(result!!.completedSuccessfully)
        assertTrue(result.answer.contains("$35"))
    }

    @Test
    fun testHumanTakeoverHaltsBot() = runBlocking {
        val controller = ConversationController(
            transport = mockTransport,
            asr = mockAsr,
            tts = mockTts,
            model = DeterministicFallbackModel()
        )

        controller.startCall(testObjective)
        controller.takeOver()
        assertEquals(AgentState.HANDOFF, controller.agentState.value)
    }
}
