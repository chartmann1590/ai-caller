package com.charles.localcallagent.agent.orchestrator

import android.util.Log

import com.charles.localcallagent.asr.local.AsrResult
import com.charles.localcallagent.asr.local.StreamingAsr
import com.charles.localcallagent.audio.core.AudioRouter
import com.charles.localcallagent.audio.core.CallAudioMode
import com.charles.localcallagent.audio.core.VadDetector
import com.charles.localcallagent.core.model.AgentAction
import com.charles.localcallagent.core.model.AgentDecision
import com.charles.localcallagent.core.model.AppCallState
import com.charles.localcallagent.core.model.CallObjective
import com.charles.localcallagent.core.model.DialogueContext
import com.charles.localcallagent.core.model.DialogueTurn
import com.charles.localcallagent.core.model.PcmFrame
import com.charles.localcallagent.core.model.Speaker
import com.charles.localcallagent.core.model.StructuredCallResult
import com.charles.localcallagent.llm.litert.LocalDialogueModel
import com.charles.localcallagent.telephony.api.CallTransport
import com.charles.localcallagent.tts.local.LocalTts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class ConversationController(
    private val transport: CallTransport,
    private val asr: StreamingAsr,
    private val tts: LocalTts,
    private val model: LocalDialogueModel,
    private val audioRouter: AudioRouter = AudioRouter(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val _agentState = MutableStateFlow(AgentState.PRE_CALL)
    val agentState: StateFlow<AgentState> = _agentState.asStateFlow()

    private val _liveTranscript = MutableStateFlow<List<DialogueTurn>>(emptyList())
    val liveTranscript: StateFlow<List<DialogueTurn>> = _liveTranscript.asStateFlow()

    private val _latestResult = MutableStateFlow<StructuredCallResult?>(null)
    val latestResult: StateFlow<StructuredCallResult?> = _latestResult.asStateFlow()

    private var activeObjective: CallObjective? = null
    private var callStartTimeMs: Long = 0L
    private var clarificationAttempts = 0
    private var currentQuestionIndex = 0

    private val vad = VadDetector(energyThresholdRms = 300f)
    private var remoteAudioJob: Job? = null
    private var asrResultsJob: Job? = null
    private var callStateJob: Job? = null

    fun startCall(objective: CallObjective) {
        activeObjective = objective
        _agentState.value = AgentState.DIALING
        _liveTranscript.value = emptyList()
        _latestResult.value = null
        clarificationAttempts = 0
        currentQuestionIndex = 0
        callStartTimeMs = System.currentTimeMillis()

        observeCallState()
        observeRemoteAudio()
        observeAsr()

        scope.launch {
            try {
                transport.dial(objective.destination)
            } catch (e: Exception) {
                failCall("Dialing failed: ${e.message}")
            }
        }
    }

    private fun observeCallState() {
        callStateJob?.cancel()
        callStateJob = scope.launch {
            transport.state.collect { state ->
                when (state) {
                    is AppCallState.Active -> {
                        if (_agentState.value == AgentState.DIALING || _agentState.value == AgentState.WAITING_FOR_ANSWER) {
                            onCallConnected()
                        }
                    }
                    is AppCallState.Disconnected -> {
                        if (_agentState.value != AgentState.COMPLETE && _agentState.value != AgentState.FAILED) {
                            completeCall(success = false, reason = state.reason ?: "Remote party disconnected")
                        }
                    }
                    is AppCallState.Ringing -> {
                        _agentState.value = AgentState.WAITING_FOR_ANSWER
                    }
                    else -> {}
                }
            }
        }
    }

    private fun observeRemoteAudio() {
        remoteAudioJob?.cancel()
        if (!transport.supportsProgrammaticMedia) return

        remoteAudioJob = scope.launch {
            try {
                transport.remoteAudio.collect { frame ->
                    if (audioRouter.audioMode.value == CallAudioMode.HUMAN_TAKEOVER) {
                        return@collect // Audio routed directly to human
                    }

                    // Check for barge-in if bot is currently speaking
                    if (_agentState.value == AgentState.ASKING || _agentState.value == AgentState.CLARIFYING || _agentState.value == AgentState.DISCLOSURE) {
                        if (vad.isBargeInTriggered(frame)) {
                            // Interrupt TTS immediately! (<150ms)
                            tts.cancel()
                            _agentState.value = AgentState.LISTENING
                            asr.start()
                        }
                    }

                    // Feed remote audio to ASR
                    if (_agentState.value == AgentState.LISTENING) {
                        asr.accept(frame)
                    }
                }
            } catch (e: Exception) {
                // Media transport error
            }
        }
    }

    private fun observeAsr() {
        asrResultsJob?.cancel()
        asrResultsJob = scope.launch {
            asr.finalResults.collect { result ->
                if (_agentState.value == AgentState.LISTENING) {
                    onRemoteUtteranceReceived(result)
                }
            }
        }
    }

    suspend fun onCallConnected() {
        Log.i(TAG, "CALL_ACTIVE")
        val objective = activeObjective ?: return
        _agentState.value = AgentState.DISCLOSURE
        asr.start()

        // 1. Mandatory FCC & privacy disclosure
        val disclosureText = objective.customDisclosure ?:
        "Hi, I'm an automated assistant calling on behalf of ${objective.callerName}. I'm calling to ask a quick question. Is it okay if I continue?"

        speakBotUtterance(disclosureText)

        // After disclosure, listen for acknowledgment or objection
        _agentState.value = AgentState.LISTENING
    }

    suspend fun onRemoteUtteranceReceived(result: AsrResult) {
        val text = result.text.trim()
        if (text.isBlank()) return
        Log.i(TAG, "ASR_FINAL textLen=${text.length}")

        recordTranscript(Speaker.REMOTE_BUSINESS, text)
        val objective = activeObjective ?: return

        _agentState.value = AgentState.THINKING

        val context = DialogueContext(
            objective = objective,
            history = _liveTranscript.value,
            currentQuestionIndex = currentQuestionIndex,
            clarificationAttempts = clarificationAttempts,
            lastRemoteUtterance = text
        )

        val rawDecision = model.decide(context)
        Log.i(TAG, "LLM_REPLY action=${rawDecision.action} speechLen=${rawDecision.speech?.length ?: 0}")
        val validatedDecision = SafetyValidator.validate(rawDecision, objective)

        // If in early disclosure phase and recipient simply consented to continue
        val lower = text.lowercase()
        val isSimpleConsent = lower.contains("go ahead") || lower.contains("ask away") ||
                lower.contains("what do you need") || lower.contains("what is it") || lower.contains("what's up") ||
                lower == "yes" || lower == "sure" || lower == "yes, what's your question?"

        if (_liveTranscript.value.size <= 2 && isSimpleConsent && validatedDecision.action != AgentAction.SEND_DTMF && validatedDecision.action != AgentAction.HANDOFF) {
            _agentState.value = AgentState.ASKING
            speakBotUtterance(objective.primaryQuestion)
            _agentState.value = AgentState.LISTENING
            return
        }

        executeDecision(validatedDecision)
    }

    private suspend fun executeDecision(decision: AgentDecision) {
        when (decision.action) {
            AgentAction.SPEAK -> {
                _agentState.value = AgentState.ASKING
                decision.speech?.let { speakBotUtterance(it) }
                _agentState.value = AgentState.LISTENING
            }
            AgentAction.CLARIFY -> {
                _agentState.value = AgentState.CLARIFYING
                clarificationAttempts++
                decision.speech?.let { speakBotUtterance(it) }
                _agentState.value = AgentState.LISTENING
            }
            AgentAction.SEND_DTMF -> {
                decision.dtmfDigit?.let { transport.sendDtmf(it) }
                _agentState.value = AgentState.LISTENING
            }
            AgentAction.HANDOFF -> {
                _agentState.value = AgentState.HANDOFF
                decision.speech?.let { speakBotUtterance(it) }
                activeObjective?.let { obj ->
                    _latestResult.value = StructuredCallResult(
                        destination = obj.destination,
                        businessName = obj.businessName,
                        primaryQuestion = obj.primaryQuestion,
                        answer = decision.extractedAnswer ?: "Recipient declined automated assistant",
                        confidence = decision.confidence,
                        callDurationSeconds = (System.currentTimeMillis() - callStartTimeMs) / 1000,
                        completedSuccessfully = false,
                        reasonCode = decision.reasonCode
                    )
                }
                takeOver()
            }
            AgentAction.FINISH -> {
                _agentState.value = AgentState.GOODBYE
                decision.speech?.let { speakBotUtterance(it) }
                completeCall(
                    success = true,
                    answer = decision.extractedAnswer ?: "Question answered",
                    reason = decision.reasonCode
                )
            }
            AgentAction.ABORT -> {
                speakBotUtterance("I apologize, but I must end this call now. Goodbye.")
                completeCall(success = false, reason = decision.reasonCode)
            }
            AgentAction.LISTEN -> {
                _agentState.value = AgentState.LISTENING
            }
        }
    }

    suspend fun speakBotUtterance(text: String) {
        if (text.isBlank()) return
        recordTranscript(Speaker.BOT, text)

        if (transport.supportsProgrammaticMedia) {
            tts.synthesizeStreaming(text).collect { frame ->
                if (_agentState.value == AgentState.LISTENING) {
                    // Barge-in interrupted playback! Stop streaming immediately.
                    return@collect
                }
                transport.sendAudio(frame)
                Log.i(TAG, "TTS_SENT samples=${frame.samples.size} rate=${frame.sampleRateHz}")
            }
        }
    }

    fun takeOver() {
        audioRouter.takeOver()
        scope.launch {
            tts.cancel()
        }
        _agentState.value = AgentState.HANDOFF
    }

    fun resumeBot() {
        audioRouter.resumeBot()
        _agentState.value = AgentState.LISTENING
    }

    fun endCall() {
        scope.launch {
            transport.hangUp()
            completeCall(success = false, reason = "Ended by user")
        }
    }

    suspend fun sendDtmf(digit: Char) {
        transport.sendDtmf(digit)
    }

    private fun completeCall(success: Boolean, answer: String = "", reason: String = "COMPLETE") {
        val objective = activeObjective ?: return
        val duration = (System.currentTimeMillis() - callStartTimeMs) / 1000

        val result = StructuredCallResult(
            destination = objective.destination,
            businessName = objective.businessName,
            primaryQuestion = objective.primaryQuestion,
            answer = answer.ifBlank { if (success) "Completed successfully" else "Call ended before resolution" },
            confidence = if (success) 0.95f else 0.50f,
            callDurationSeconds = duration,
            completedSuccessfully = success,
            reasonCode = reason,
            timestampEpochMs = System.currentTimeMillis()
        )

        _latestResult.value = result
        _agentState.value = if (success) AgentState.COMPLETE else AgentState.FAILED
        cleanup()
    }

    private fun failCall(reason: String) {
        _agentState.value = AgentState.FAILED
        completeCall(success = false, reason = reason)
    }

    private fun cleanup() {
        scope.launch {
            asr.stop()
            tts.cancel()
            remoteAudioJob?.cancel()
            asrResultsJob?.cancel()
            callStateJob?.cancel()
        }
    }

    private fun recordTranscript(speaker: Speaker, text: String) {
        val turn = DialogueTurn(speaker = speaker, text = text)
        _liveTranscript.value = _liveTranscript.value + turn
    }

    companion object {
        private const val TAG = "ConversationController"
    }
}
