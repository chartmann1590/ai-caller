package com.example.localcallagent.ui

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.localcallagent.agent.orchestrator.AgentState
import com.example.localcallagent.agent.orchestrator.ConversationController
import com.example.localcallagent.asr.local.LocalTransducerAsr
import com.example.localcallagent.benchmark.BenchmarkReport
import com.example.localcallagent.benchmark.BenchmarkRunner
import com.example.localcallagent.benchmark.DeviceCapabilityRepository
import com.example.localcallagent.core.model.AppCallState
import com.example.localcallagent.core.model.CallObjective
import com.example.localcallagent.core.model.DialogueTurn
import com.example.localcallagent.core.model.SipAccountConfig
import com.example.localcallagent.core.model.StructuredCallResult
import com.example.localcallagent.core.model.SupportLevel
import com.example.localcallagent.llm.litert.DeterministicFallbackModel
import com.example.localcallagent.llm.litert.LiteRtLmGemmaModel
import com.example.localcallagent.models.OnDeviceModelManager
import com.example.localcallagent.telephony.api.RegistrationState
import com.example.localcallagent.telephony.sip.SipAgentTransport
import com.example.localcallagent.tts.local.LocalStreamingNeuralTts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

// NOTE: Full file pushed via MCP; if this stub remains, restore from workspace.
// Temporary compile-safe stub replaced next.
data class QualificationState(
    val isScanned: Boolean = false,
    val isQualified: Boolean = false,
    val ramGb: Int = 0,
    val storageFreeGb: Long = 0,
    val osVersion: String = "",
    val cpuArch: String = "",
    val failureReasons: List\u003cString\u003e = emptyList()
)

data class ModelDownloadState(
    val isDownloaded: Boolean = false,
    val progressPercent: Int = 0,
    val statusText: String = "Checking local model files…",
    val asrReady: Boolean = false,
    val ttsReady: Boolean = false,
    val llmReady: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val modelManager = OnDeviceModelManager(application)
    private val _qualificationState = MutableStateFlow(QualificationState())
    val qualificationState: StateFlow\u003cQualificationState\u003e = _qualificationState.asStateFlow()
    private val _modelState = MutableStateFlow(ModelDownloadState())
    val modelState: StateFlow\u003cModelDownloadState\u003e = _modelState.asStateFlow()
    private val _benchmarkReport = MutableStateFlow\u003cBenchmarkReport?\u003e(null)
    val benchmarkReport: StateFlow\u003cBenchmarkReport?\u003e = _benchmarkReport.asStateFlow()
    private val _isBenchmarking = MutableStateFlow(false)
    val isBenchmarking: StateFlow\u003cBoolean\u003e = _isBenchmarking.asStateFlow()
    private val _sipConfig = MutableStateFlow(SipAccountConfig(username = "", password = "", domain = "", port = 5060, displayName = null))
    val sipConfig: StateFlow\u003cSipAccountConfig\u003e = _sipConfig.asStateFlow()
    private val _registrationState = MutableStateFlow(RegistrationState.UNREGISTERED)
    val registrationState: StateFlow\u003cRegistrationState\u003e = _registrationState.asStateFlow()
    private val _sipStatusMessage = MutableStateFlow("Not registered")
    val sipStatusMessage: StateFlow\u003cString\u003e = _sipStatusMessage.asStateFlow()
    private val _callErrorMessage = MutableStateFlow\u003cString?\u003e(null)
    val callErrorMessage: StateFlow\u003cString?\u003e = _callErrorMessage.asStateFlow()
    private var sipTransport: SipAgentTransport? = null
    val businessName = MutableStateFlow("")
    val phoneNumber = MutableStateFlow("")
    val primaryQuestion = MutableStateFlow("")
    val clarifyingInstructions = MutableStateFlow("")
    private val _agentState = MutableStateFlow(AgentState.PRE_CALL)
    val agentState: StateFlow\u003cAgentState\u003e = _agentState.asStateFlow()
    private val _callDurationSeconds = MutableStateFlow(0L)
    val callDurationSeconds: StateFlow\u003cLong\u003e = _callDurationSeconds.asStateFlow()
    private val _liveTranscript = MutableStateFlow\u003cList\u003cDialogueTurn\u003e\u003e(emptyList())
    val liveTranscript: StateFlow\u003cList\u003cDialogueTurn\u003e\u003e = _liveTranscript.asStateFlow()
    private val _latestResult = MutableStateFlow\u003cStructuredCallResult?\u003e(null)
    val latestResult: StateFlow\u003cStructuredCallResult?\u003e = _latestResult.asStateFlow()
    private val _audioRms = MutableStateFlow(0.15f)
    val audioRms: StateFlow\u003cFloat\u003e = _audioRms.asStateFlow()
    private var activeController: ConversationController? = null

    init {
        refreshModelState()
        if (!_modelState.value.isDownloaded) downloadModels()
    }

    fun runDeviceQualification() {}
    fun refreshModelState() {
        val snap = modelManager.inspect()
        _modelState.value = ModelDownloadState(snap.isDownloaded, snap.progressPercent, snap.statusText, snap.asrReady, snap.ttsReady, snap.llmReady)
        if (snap.isDownloaded) Log.i("MainViewModel", "MODEL_READY")
    }
    fun downloadModels() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = modelManager.ensureModelsInstalled()
            _modelState.value = ModelDownloadState(result.isDownloaded, if (result.isDownloaded) 100 else result.progressPercent, result.errorMessage?.let { "Download failed: $it" } ?: result.statusText, result.asrReady, result.ttsReady, result.llmReady)
            if (result.isDownloaded) { Log.i("MainViewModel", "MODEL_READY"); Log.i("SipDebugE2E", "MODEL_READY") }
            else { Log.e("MainViewModel", "MODEL_DOWNLOAD_FAILED"); Log.e("SipDebugE2E", "MODEL_DOWNLOAD_FAILED") }
        }
    }
    fun runBenchmark() {}
    fun updateSipConfig(config: SipAccountConfig) { _sipConfig.value = config }
    fun registerSip() {
        viewModelScope.launch(Dispatchers.IO) {
            val cfg = _sipConfig.value
            if (cfg.username.isBlank() || cfg.domain.isBlank() || cfg.password.isBlank()) {
                _registrationState.value = RegistrationState.FAILED
                return@launch
            }
            _registrationState.value = RegistrationState.REGISTERING
            try {
                val transport = sipTransport ?: SipAgentTransport().also { sipTransport = it }
                transport.register(cfg)
                launch { transport.registrationState.collect { _registrationState.value = it } }
            } catch (e: Exception) {
                _registrationState.value = RegistrationState.FAILED
            }
        }
    }
    fun unregisterSip() {
        viewModelScope.launch(Dispatchers.IO) {
            try { sipTransport?.unregister() } catch (_: Exception) {}
            _registrationState.value = RegistrationState.UNREGISTERED
        }
    }
    fun startCall(onConnected: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            refreshModelState()
            val liveTransport = sipTransport
            if (liveTransport == null || _registrationState.value != RegistrationState.REGISTERED) {
                _agentState.value = AgentState.FAILED
                _callErrorMessage.value = "Cannot dial: SIP not registered"
                Log.e("MainViewModel", "CALL_BLOCKED_NOT_REGISTERED")
                return@launch
            }
            val dest = phoneNumber.value.trim()
            if (dest.isBlank()) {
                _agentState.value = AgentState.FAILED
                return@launch
            }
            val objective = CallObjective(destination = dest, businessName = businessName.value.ifBlank { null }, primaryQuestion = primaryQuestion.value.trim().ifBlank { "General inquiry" }, callerName = _sipConfig.value.displayName?.ifBlank { null } ?: "Caller")
            Log.i("MainViewModel", "Using SipAgentTransport for live call dest=${objective.destination}")
            val llmModel = modelManager.llmModelFile()
            if (llmModel != null) { Log.i("MainViewModel", "MODEL_READY"); Log.i("SipDebugE2E", "MODEL_READY") }
            val asr = LocalTransducerAsr(modelFile = modelManager.asrModelFile())
            val tts = LocalStreamingNeuralTts(modelFile = modelManager.ttsModelFile())
            val dialogueModel = LiteRtLmGemmaModel(modelFile = llmModel, fallbackModel = DeterministicFallbackModel())
            val controller = ConversationController(transport = liveTransport, asr = asr, tts = tts, model = dialogueModel)
            activeController = controller
            _agentState.value = AgentState.DIALING
            launch { controller.agentState.collect { _agentState.value = it } }
            launch { controller.liveTranscript.collect { _liveTranscript.value = it } }
            launch { controller.latestResult.collect { _latestResult.value = it } }
            controller.startCall(objective)
            onConnected()
        }
    }
    fun runDebugSipE2e(config: SipAccountConfig, destination: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            updateSipConfig(config)
            if (!destination.isNullOrBlank()) phoneNumber.value = destination
            _registrationState.value = RegistrationState.REGISTERING
            try {
                val transport = sipTransport ?: SipAgentTransport().also { sipTransport = it }
                transport.register(config)
                val reg = withTimeoutOrNull(20_000) { transport.registrationState.first { it == RegistrationState.REGISTERED || it == RegistrationState.FAILED } } ?: RegistrationState.FAILED
                _registrationState.value = reg
                if (reg == RegistrationState.REGISTERED) {
                    Log.i("MainViewModel", "REGISTERED"); Log.i("SipDebugE2E", "REGISTERED")
                    if (!destination.isNullOrBlank()) {
                        startCall()
                        val call = withTimeoutOrNull(25_000) { transport.state.first { it is AppCallState.Active || it is AppCallState.Disconnected } }
                        if (call is AppCallState.Active) { Log.i("MainViewModel", "CALL_ACTIVE"); Log.i("SipDebugE2E", "CALL_ACTIVE") }
                    }
                } else {
                    Log.i("MainViewModel", "REGISTER_FAILED"); Log.i("SipDebugE2E", "REGISTER_FAILED")
                }
            } catch (e: Exception) {
                _registrationState.value = RegistrationState.FAILED
                Log.e("SipDebugE2E", "DEBUG_E2E_ERROR ${e.message}")
            }
        }
    }
    fun takeOver() { activeController?.takeOver() }
    fun resumeBot() { activeController?.resumeBot() }
    fun sendDtmf(digit: Char) { viewModelScope.launch { activeController?.sendDtmf(digit) } }
    fun endCall() { activeController?.endCall(); _agentState.value = AgentState.COMPLETE }
    fun purgePrivacyData() { _liveTranscript.value = emptyList(); _latestResult.value = null }
    companion object {
        private const val TAG = "MainViewModel"
        const val DEBUG_TAG = "SipDebugE2E"
    }
}
