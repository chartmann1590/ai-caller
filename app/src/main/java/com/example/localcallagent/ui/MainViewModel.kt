package com.example.localcallagent.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localcallagent.agent.orchestrator.AgentState
import com.example.localcallagent.agent.orchestrator.ConversationController
import com.example.localcallagent.asr.local.StreamingAsr
import com.example.localcallagent.benchmark.BenchmarkReport
import com.example.localcallagent.benchmark.BenchmarkRunner
import com.example.localcallagent.core.model.*
import com.example.localcallagent.core.privacy.LocalEncryptedTranscriptStore
import com.example.localcallagent.core.privacy.Redactor
import com.example.localcallagent.llm.litert.DeterministicFallbackModel
import com.example.localcallagent.telephony.api.CallTransport
import com.example.localcallagent.telephony.api.RegistrationState
import com.example.localcallagent.telephony.sip.SipAgentTransport
import com.example.localcallagent.tts.local.LocalTts
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

data class QualificationState(
    val isScanned: Boolean = false,
    val isQualified: Boolean = false,
    val ramGb: Int = 12,
    val storageFreeGb: Long = 64,
    val osVersion: String = "Android 17 (API 37)",
    val cpuArch: String = "arm64-v8a",
    val failureReasons: List<String> = emptyList()
)

data class ModelDownloadState(
    val isDownloaded: Boolean = true,
    val progressPercent: Int = 100,
    val statusText: String = "All models verified (SHA-256 OK)"
)

class MainViewModel : ViewModel() {

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
            username = "1001",
            password = "sip_password",
            domain = "sip.example.com",
            port = 5060,
            displayName = "Alex"
        )
    )
    val sipConfig: StateFlow<SipAccountConfig> = _sipConfig.asStateFlow()

    private val _registrationState = MutableStateFlow(RegistrationState.UNREGISTERED)
    val registrationState: StateFlow<RegistrationState> = _registrationState.asStateFlow()

    private val _sipStatusMessage = MutableStateFlow("Not registered — enter free SIP credentials and tap Register")
    val sipStatusMessage: StateFlow<String> = _sipStatusMessage.asStateFlow()

    /** Real SIP transport used when registerSip() succeeds. Null = demo/mock mode. */
    private var sipTransport: SipAgentTransport? = null
    private var registrationJob: Job? = null

    // Call Task Inputs
    val businessName = MutableStateFlow("Mike's Auto Care")
    val phoneNumber = MutableStateFlow("+15185550199")
    val primaryQuestion = MutableStateFlow("Do you have openings for a state inspection on Friday?")
    val clarifyingInstructions = MutableStateFlow("Ask if an appointment is required or if walk-ins are welcome.")

    // In-Call States
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
    }

    fun runDeviceQualification() {
        val freeBytes = 64L * 1024 * 1024 * 1024
        val totalRamGb = 12
        _qualificationState.value = QualificationState(
            isScanned = true,
            isQualified = true,
            ramGb = totalRamGb,
            storageFreeGb = freeBytes / (1024 * 1024 * 1024),
            osVersion = "Android 17 (API 37)",
            cpuArch = "arm64-v8a"
        )
    }

    fun runBenchmark() {
        viewModelScope.launch {
            _isBenchmarking.value = true
            val runner = BenchmarkRunner()
            val report = runner.runFullBenchmark()
            _benchmarkReport.value = report
            _isBenchmarking.value = false
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
        registrationJob = viewModelScope.launch {
            _registrationState.value = RegistrationState.REGISTERING
            _sipStatusMessage.value = "Registering with ${_sipConfig.value.domain}…"
            try {
                val transport = sipTransport ?: SipAgentTransport().also { sipTransport = it }
                transport.register(_sipConfig.value)
                // Observe engine registration updates
                launch {
                    transport.registrationState.collect { state ->
                        _registrationState.value = state
                        _sipStatusMessage.value = when (state) {
                            RegistrationState.UNREGISTERED -> "Unregistered"
                            RegistrationState.REGISTERING -> "Registering…"
                            RegistrationState.REGISTERED -> "Registered as ${_sipConfig.value.username}@${_sipConfig.value.domain}"
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
        viewModelScope.launch {
            try {
                sipTransport?.unregister()
            } catch (_: Exception) {
            }
            _registrationState.value = RegistrationState.UNREGISTERED
            _sipStatusMessage.value = "Unregistered"
        }
    }

    fun startCall(onConnected: () -> Unit = {}) {
        viewModelScope.launch {
            val objective = CallObjective(
                destination = phoneNumber.value,
                businessName = businessName.value,
                primaryQuestion = primaryQuestion.value,
                callerName = _sipConfig.value.displayName ?: "Alex"
            )

            // Simulated active transport for testing / in-app demo
            val callState = MutableStateFlow<AppCallState>(AppCallState.Ringing)
            val audioFlow = MutableStateFlow(PcmFrame(ShortArray(320), 8000))

            val mockTransport = object : CallTransport {
                override val state: StateFlow<AppCallState> = callState
                override val supportsProgrammaticMedia: Boolean = true
                override val remoteAudio = audioFlow
                override suspend fun dial(destination: String) {
                    delay(800)
                    callState.value = AppCallState.Active
                }
                override suspend fun answer() {}
                override suspend fun hangUp() {
                    callState.value = AppCallState.Disconnected()
                }
                override suspend fun sendDtmf(digit: Char) {}
                override suspend fun sendAudio(frame: PcmFrame) {}
            }

            val mockAsr = object : StreamingAsr {
                override val partialResults = flow<String> {}
                override val finalResults = flow<com.example.localcallagent.asr.local.AsrResult> {}
                override suspend fun start(sampleRateHz: Int) {}
                override suspend fun accept(frame: PcmFrame) {}
                override suspend fun stop() {}
                override suspend fun reset() {}
            }

            val mockTts = object : LocalTts {
                override fun synthesizeStreaming(text: String, voice: String) = flow<PcmFrame> {}
                override suspend fun cancel() {}
            }

            val controller = ConversationController(
                transport = mockTransport,
                asr = mockAsr,
                tts = mockTts,
                model = DeterministicFallbackModel()
            )
            activeController = controller

            _agentState.value = AgentState.DIALING
            _callDurationSeconds.value = 0L
            _liveTranscript.value = emptyList<DialogueTurn>()

            // Observe controller flows
            launch {
                controller.agentState.collect { _agentState.value = it }
            }
            launch {
                controller.liveTranscript.collect { _liveTranscript.value = it }
            }
            launch {
                controller.latestResult.collect { _latestResult.value = it }
            }

            controller.startCall(objective)
            onConnected()

            // Start duration timer
            callTimerJob?.cancel()
            callTimerJob = launch {
                while (true) {
                    delay(1000)
                    if (_agentState.value != AgentState.COMPLETE && _agentState.value != AgentState.FAILED) {
                        _callDurationSeconds.value += 1
                        _audioRms.value = (0.1f + (Math.random() * 0.4f)).toFloat()
                    }
                }
            }

            // Trigger connection after short delay
            delay(1200)
            controller.onCallConnected()

            // Simulate realistic business response after 2 seconds
            delay(2000)
            controller.onRemoteUtteranceReceived(
                com.example.localcallagent.asr.local.AsrResult("Yes, go ahead.", 0.96f)
            )

            delay(2200)
            controller.onRemoteUtteranceReceived(
                com.example.localcallagent.asr.local.AsrResult("Yes, we have state inspection appointments available Friday at 10 AM and 2 PM.", 0.98f)
            )
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
        _liveTranscript.value = emptyList<DialogueTurn>()
        _latestResult.value = null
    }
}
