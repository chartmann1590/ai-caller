package com.example.localcallagent.telephony.sip

import com.example.localcallagent.core.model.AppCallState
import com.example.localcallagent.core.model.PcmFrame
import com.example.localcallagent.core.model.SipAccountConfig
import com.example.localcallagent.telephony.api.CallTransport
import com.example.localcallagent.telephony.api.RegistrationState
import com.example.localcallagent.telephony.api.VoipClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * PHASE C / PHASE 9 — SIP Agent Transport.
 *
 * Implements full autonomous VoIP call media ownership:
 * - supportsProgrammaticMedia = true
 * - incoming remote audio delivered as PCM frames
 * - outgoing synthesized PCM audio injected into RTP uplink
 * - RFC 2833 / RFC 4733 DTMF generation
 */
class SipAgentTransport(
    private val engine: SipEngine = SipEngine()
) : CallTransport, VoipClient {

    override val state: StateFlow<AppCallState> = engine.callState

    override val supportsProgrammaticMedia: Boolean = true

    override val remoteAudio: Flow<PcmFrame> = engine.incomingPcm

    override val registrationState: StateFlow<RegistrationState> = engine.registrationState

    override val callState: StateFlow<AppCallState> = engine.callState

    override val incomingAudio: Flow<PcmFrame> = engine.incomingPcm

    override suspend fun register(config: SipAccountConfig) {
        engine.initialize(config)
        engine.register()
    }

    override suspend fun unregister() {
        engine.unregister()
    }

    override suspend fun call(destination: String) {
        dial(destination)
    }

    override suspend fun dial(destination: String) {
        engine.dial(destination)
    }

    override suspend fun answer() {
        // Inbound answer logic if needed
    }

    override suspend fun hangup() {
        hangUp()
    }

    override suspend fun hangUp() {
        engine.hangup()
        AgentConnectionService.markDisconnected("Call ended")
    }

    override suspend fun sendDtmf(digit: Char) {
        engine.sendDtmf(digit)
    }

    override suspend fun sendAudio(frame: PcmFrame) {
        engine.sendAudioFrame(frame)
    }
}
