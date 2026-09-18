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

data class QualificationState(
    val isScanned: Boolean = false,
    val isQualified: Boolean = false,
    val ramGb: Int = 0,
    val storageFreeGb: Long = 0,
    val osVersion: String = "",
    val cpuArch: String = "",
    val failureReasons: List<String> = emptyList()
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

    private val capabilityRepo = DeviceCapabilityRepository(application)

    private val _qualificationState = MutableStateFlow(QualificationState())
    val qualificationState: StateFlow<QualificationState> = _qualificationState.asStateFlow()

    private val _modelState = MutableStateFlow(ModelDownloadState())
    val modelState: StateFlow<ModelDownloadState> = _modelState.asStateFlow()

    private val _benchmarkReport = MutableStateFlow<BenchmarkReport?>(null)
    val benchmarkReport: StateFlow<BenchmarkReport?> = _benchmarkReport.asStateFlow()

    private val _isBenchmarking = MutableStateFlow(false)
    val isBenchmarking: StateFlow<Boolean> = _isBenchmarking.asStateFlow()

    private val _sipConfig = MutableStateFlow(
        SipAccountConfig(
            username = "",
            password = "",
            domain = "",
            port = 5060,
            displayName = null
        )
    )
    val sipConfig: StateFlow<SipAccountConfig> = _sipConfig.asStateFlow()

    private val _registrationState = MutableStateFlow(RegistrationState.UNREGISTERED)
    val registrationState: StateFlow<RegistrationState> = _registrationState.asStateFlow()

    private val _sipStatusMessage = MutableStateFlow("Not registered — enter SIP credentials and tap Register")
    val sipStatusMessage: StateFlow<String> = _sipStatusMessage.asStateFlow()

    private val _callErrorMessage = MutableStateFlow<String?>(null)
    val callErrorMessage: StateFlow<String?> = _callErrorMessage.asStateFlow()

    /** Real SIP transport. Null until registerSip() / debug e2e creates one. */
    private var sipTransport: SipAgentTransport? = null
    private var registrationJob: Job? = null

    // Call task inputs — empty until the user fills them (no demo defaults)
    val businessName = MutableStateFlow("")
    val phoneNumber = MutableStateFlow("")
    val primaryQuestion = MutableStateFlow("")
    val clarifyingInstructions = MutableStateFlow("")

    private val _agentState = MutableStateFlow(AgentState.PRE_CALL)
    val agentState: StateFlow<AgentState> = _agentState.asStateFlow()

    private val _callDurationSeconds = MutableStateFlow(0L)
    val callDurationSeconds: StateFlow<Long> = _callDurationSeconds.asStateFlow()

    private val _liveTranscript = MutableStateFlow<List<DialogueTurn>>(emptyList())
    val liveTranscript: StateFlow<List<DialogueTurn>> = _liveTranscript.asStateFlow()

    private val _latestResult = MutableStateFlow<StructuredCallResult?>(null)
    val latestResult: StateFlow<StructuredCallResult?> = _latestResult.asStateFlow()

    private val _audioRms = MutableStateFlow(0.15f)
    val audioRms: StateFlow<Float> = _audioRms.asStateFlow()

    private var activeController: ConversationController? = null
    private var callTimerJob: Job? = null

    init {
        runDeviceQualification()
        refreshModelState()
    }

    fun runDeviceQualification() {
        val caps = capabilityRepo.probeCapabilities()
        val ramGb = (caps.totalRamMb / 1024).toInt().coerceAtLeast(0)
        val storageFreeGb = caps.availableStorageMb / 1024
        val osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        val cpuArch = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        val failures = caps.warnings.toMutableList()
        if (caps.totalRamMb < 7500) {
            failures.add("RAM ${caps.totalRamMb} MB below 8 GB recommended minimum")
        }
        if (caps.availableStorageMb < 5000) {
            failures.add("Free storage ${caps.availableStorageMb} MB below 6 GB recommended")
        }
        if (Build.VERSION.SDK_INT < 31) {
            failures.add("API ${Build.VERSION.SDK_INT} < 31 (minSdk)")
        }
        val isQualified =
            caps.supportLevel != SupportLevel.UNSUPPORTED &&
                caps.totalRamMb >= 7500 &&
                caps.availableStorageMb >= 5000 &&
                Build.VERSION.SDK_INT >= 31
        _qualificationState.value = QualificationState(
            isScanned = true,
            isQualified = isQualified,
            ramGb = ramGb,
            storageFreeGb = storageFreeGb,
            osVersion = osVersion,
            cpuArch = cpuArch,
            failureReasons = failures.distinct()
        )
    }

    fun refreshModelState() {
        val modelsDir = File(getApplication<Application>().filesDir, "models")
        val asrReady = File(modelsDir, "asr.onnx").let { it.exists() && it.length() > 0 }
        val ttsReady = File(modelsDir, "tts.onnx").let { it.exists() && it.length() > 0 }
        val llmReady = listOf("gemma.tflite", "gemma.bin", "llm.litertlm")
            .any { File(modelsDir, it).exists() }
        val allReady = asrReady && ttsReady && llmReady
        val anyReady = asrReady || ttsReady || llmReady
        val status = when {
            allReady -> "Local model files present under filesDir/models (ASR/TTS/LLM)"
            anyReady -> "Partial models on device — missing: " + listOfNotNull(
                if (!asrReady) "ASR" else null,
                if (!ttsReady) "TTS" else null,
                if (!llmReady) "LLM" else null
            ).joinToString(", ") + ". Dialogue uses DeterministicFallbackModel until LLM weights are installed."
            else -> "No on-device model weights under filesDir/models. ASR will not invent transcripts; LLM uses DeterministicFallbackModel; TTS uses LocalStreamingNeuralTts synthesis until weights are installed."
        }
        _modelState.value = ModelDownloadState(
            isDownloaded = allReady,
            progressPercent = listOf(asrReady, ttsReady, llmReady).count { it } * 33 + if (allReady) 1 else 0,
            statusText = status,
            asrReady = asrReady,
            ttsReady = ttsReady,
            llmReady = llmReady
        )
    }

    fun runBenchmark() {
        viewModelScope.launch {
            _isBenchmarking.value = true
            try {
                val report = BenchmarkRunner().runFullBenchmark()
                _benchmarkReport.value = report
            } catch (e: Exception) {
                Log.e(TAG, "Benchmark failed: ${e.message}")
            } finally {
                _isBenchmarking.value = false
            }
        }
    }

    fun updateSipConfig(config: SipAccountConfig) {
        _sipConfig.value = config
    }

    /**
     * Register with the configured SIP provider (UDP).
     * Credentials stay in memory only — never written to git or logs.
     */
    fun registerSip() {
        registrationJob?.cancel()
        registrationJob = viewModelScope.launch(Dispatchers.IO) {
            val cfg = _sipConfig.value
            if (cfg.username.isBlank() || cfg.domain.isBlank() || cfg.password.isBlank()) {
                _registrationState.value = RegistrationState.FAILED
                _sipStatusMessage.value = "Registration failed — username, password, and domain are required"
                return@launch
            }
            _registrationState.value = RegistrationState.REGISTERING
            _sipStatusMessage.value = "Registering with ${cfg.domain}…"
            try {
                val transport = sipTransport ?: SipAgentTransport().also { sipTransport = it }
                transport.register(cfg)
                launch {
                    transport.registrationState.collect { state ->
                        _registrationState.value = state
                        _sipStatusMessage.value = when (state) {
                            RegistrationState.UNREGISTERED -> "Unregistered"
                            RegistrationState.REGISTERING -> "Registering…"
                            RegistrationState.REGISTERED -> "Registered as ${cfg.username}@${cfg.domain}"
                            RegistrationState.FAILED -> "Registration failed — check username/password/proxy/NAT"
                        }
                    }
                }
            } catch (e: Exception) {
                _registrationState.value = RegistrationState.FAILED
                _sipStatusMessage.value = "Registration error: ${e.message ?: e::class.java.simpleName}"
            }
        }
    }

    fun unregisterSip() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                sipTransport?.unregister()
            } catch (_: Exception) {
            }
            _registrationState.value = RegistrationState.UNREGISTERED
            _sipStatusMessage.value = "Unregistered"
        }
    }

    private fun isSipLive(): Boolean {
        val transport = sipTransport ?: return false
        return transport.registrationState.value == RegistrationState.REGISTERED ||
            _registrationState.value == RegistrationState.REGISTERED
    }

    /**
     * Places a real SIP call via [SipAgentTransport] only.
     * If SIP is not registered, fails with a user-visible error — never falls back to mock dial.
     */
    fun startCall(onConnected: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            _callErrorMessage.value = null
            refreshModelState()

            if (!isSipLive()) {
                val msg = "Cannot dial: SIP not registered. Open Settings, enter credentials, and Register first."
                Log.e(TAG, "CALL_BLOCKED_NOT_REGISTERED")
                _agentState.value = AgentState.FAILED
                _callErrorMessage.value = msg
                _sipStatusMessage.value = msg
                return@launch
            }

            val liveTransport = sipTransport
            if (liveTransport == null) {
                val msg = "Cannot dial: SIP transport unavailable."
                Log.e(TAG, "CALL_BLOCKED_NO_TRANSPORT")
                _agentState.value = AgentState.FAILED
                _callErrorMessage.value = msg
                return@launch
            }

            val dest = phoneNumber.value.trim()
            if (dest.isBlank()) {
                val msg = "Cannot dial: destination number / SIP URI is empty."
                Log.e(TAG, "CALL_BLOCKED_EMPTY_DEST")
                _agentState.value = AgentState.FAILED
                _callErrorMessage.value = msg
                return@launch
            }

            val question = primaryQuestion.value.trim().ifBlank { "General inquiry" }
            val objective = CallObjective(
                destination = dest,
                businessName = businessName.value.ifBlank { null },
                primaryQuestion = question,
                callerName = _sipConfig.value.displayName?.ifBlank { null } ?: "Caller"
            )

            Log.i(TAG, "Using SipAgentTransport for live call dest=${objective.destination}")

            val modelsDir = File(getApplication<Application>().filesDir, "models")
            val asrModel = File(modelsDir, "asr.onnx").takeIf { it.exists() }
            val ttsModel = File(modelsDir, "tts.onnx").takeIf { it.exists() }
            val llmModel = listOf("gemma.tflite", "gemma.bin", "llm.litertlm")
                .map { File(modelsDir, it) }
                .firstOrNull { it.exists() }

            val asr = LocalTransducerAsr(modelFile = asrModel)
            val tts = LocalStreamingNeuralTts(modelFile = ttsModel)
            val dialogueModel = LiteRtLmGemmaModel(
                modelFile = llmModel,
                fallbackModel = DeterministicFallbackModel()
            )

            val controller = ConversationController(
                transport = liveTransport,
                asr = asr,
                tts = tts,
                model = dialogueModel
            )
            activeController = controller

            _agentState.value = AgentState.DIALING
            _callDurationSeconds.value = 0L
            _liveTranscript.value = emptyList()

            launch { controller.agentState.collect { _agentState.value = it } }
            launch { controller.liveTranscript.collect { _liveTranscript.value = it } }
            launch { controller.latestResult.collect { _latestResult.value = it } }

            controller.startCall(objective)
            onConnected()

            callTimerJob?.cancel()
            callTimerJob = launch {
                while (true) {
                    delay(1000)
                    if (_agentState.value != AgentState.COMPLETE && _agentState.value != AgentState.FAILED) {
                        _callDurationSeconds.value += 1
                    }
                }
            }
            // Live SIP: ConversationController observes transport.state for Active / Disconnected
        }
    }

    /**
     * Debug/headless helper: apply config, register, optionally dial [destination].
     * Never logs the password. Emits SipDebugE2E / MainViewModel lines for adb logcat.
     * Drives real [SipAgentTransport] only (lab loopback OK) — no mock transport.
     */
    fun runDebugSipE2e(config: SipAccountConfig, destination: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            updateSipConfig(config)
            if (!destination.isNullOrBlank()) {
                phoneNumber.value = destination
            }
            _registrationState.value = RegistrationState.REGISTERING
            _sipStatusMessage.value = "Debug e2e registering with ${config.domain}…"
            Log.i(TAG, "DEBUG_E2E_START user=${config.username} domain=${config.domain} proxy=${config.outboundProxy} dest=$destination")
            try {
                val transport = sipTransport ?: SipAgentTransport().also { sipTransport = it }
                transport.register(config)
                val reg = withTimeoutOrNull(20_000) {
                    transport.registrationState.first {
                        it == RegistrationState.REGISTERED || it == RegistrationState.FAILED
                    }
                } ?: RegistrationState.FAILED
                _registrationState.value = reg
                if (reg == RegistrationState.REGISTERED) {
                    _sipStatusMessage.value = "Registered as ${config.username}@${config.domain}"
                    Log.i(TAG, "REGISTERED")
                    Log.i(DEBUG_TAG, "REGISTERED")
                } else {
                    _sipStatusMessage.value = "Registration failed"
                    Log.i(TAG, "REGISTER_FAILED")
                    Log.i(DEBUG_TAG, "REGISTER_FAILED")
                    return@launch
                }

                if (!destination.isNullOrBlank()) {
                    phoneNumber.value = destination
                    startCall()
                    val call = withTimeoutOrNull(25_000) {
                        transport.state.first {
                            it is AppCallState.Active || it is AppCallState.Disconnected
                        }
                    }
                    when (call) {
                        is AppCallState.Active -> {
                            Log.i(TAG, "CALL_ACTIVE")
                            Log.i(DEBUG_TAG, "CALL_ACTIVE")
                        }
                        is AppCallState.Disconnected -> {
                            Log.i(TAG, "CALL_ENDED reason=${call.reason}")
                            Log.i(DEBUG_TAG, "CALL_ENDED reason=${call.reason}")
                        }
                        else -> {
                            Log.i(TAG, "CALL_TIMEOUT state=${transport.state.value}")
                            Log.i(DEBUG_TAG, "CALL_TIMEOUT state=${transport.state.value}")
                        }
                    }
                }
            } catch (e: Exception) {
                _registrationState.value = RegistrationState.FAILED
                _sipStatusMessage.value = "Debug e2e error: ${e.message ?: e::class.java.simpleName}"
                Log.e(TAG, "DEBUG_E2E_ERROR ${e.message ?: e::class.java.simpleName}")
                Log.e(DEBUG_TAG, "DEBUG_E2E_ERROR ${e.message ?: e::class.java.simpleName}")
            }
        }
    }

    fun takeOver() {
        activeController?.takeOver()
    }

    fun resumeBot() {
        activeController?.resumeBot()
    }

    fun sendDtmf(digit: Char) {
        viewModelScope.launch {
            activeController?.sendDtmf(digit)
        }
    }

    fun endCall() {
        callTimerJob?.cancel()
        activeController?.endCall()
        _agentState.value = AgentState.COMPLETE
    }

    fun purgePrivacyData() {
        _liveTranscript.value = emptyList()
        _latestResult.value = null
    }

    companion object {
        private const val TAG = "MainViewModel"
        const val DEBUG_TAG = "SipDebugE2E"
    }
}
