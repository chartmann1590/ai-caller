package com.example.localcallagent.telephony.api

import com.example.localcallagent.core.model.AppCallState
import com.example.localcallagent.core.model.PcmFrame
import com.example.localcallagent.core.model.SipAccountConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class RegistrationState {
    UNREGISTERED,
    REGISTERING,
    REGISTERED,
    FAILED
}

/**
 * Clean VoIP softphone abstraction hiding SIP protocol details.
 * The AI layer interacts with PCM audio, not SIP signaling.
 */
interface VoipClient {
    val registrationState: StateFlow<RegistrationState>
    val callState: StateFlow<AppCallState>

    suspend fun register(config: SipAccountConfig)
    suspend fun unregister()
    suspend fun call(destination: String)
    suspend fun answer()
    suspend fun hangup()
    suspend fun sendDtmf(digit: Char)

    val incomingAudio: Flow<PcmFrame>
    suspend fun sendAudio(frame: PcmFrame)
}
