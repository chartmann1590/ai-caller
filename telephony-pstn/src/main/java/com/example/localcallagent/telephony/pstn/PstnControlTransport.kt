package com.example.localcallagent.telephony.pstn

import com.example.localcallagent.core.model.AppCallState
import com.example.localcallagent.core.model.PcmFrame
import com.example.localcallagent.telephony.api.CallTransport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow

/**
 * PHASE B / C — Cellular/PSTN Call Transport.
 *
 * Controls ordinary carrier calls via Android Telecom APIs.
 *
 * CRITICAL ARCHITECTURAL BOUNDARY:
 * Public Android APIs DO NOT expose raw bidirectional PCM audio for standard
 * cellular/carrier SIM calls to third-party applications.
 * `VOICE_CALL`, `VOICE_DOWNLINK`, and `VOICE_UPLINK` all require CAPTURE_AUDIO_OUTPUT,
 * which is strictly reserved for system/OEM components.
 *
 * Therefore, supportsProgrammaticMedia is FALSE, and media methods fail explicitly.
 */
class PstnControlTransport(
    private val controller: PstnCallController
) : CallTransport {

    override val state: StateFlow<AppCallState> = LocalInCallService.callState

    override val supportsProgrammaticMedia: Boolean = false

    override val remoteAudio: Flow<PcmFrame>
        get() = flow {
            throw UnsupportedOperationException(
                "Public Android APIs do not expose cellular call PCM to third-party apps. " +
                        "CAPTURE_AUDIO_OUTPUT is system-only."
            )
        }

    override suspend fun dial(destination: String) {
        controller.placeCall(destination)
    }

    override suspend fun answer() {
        LocalInCallService.answer()
    }

    override suspend fun hangUp() {
        LocalInCallService.hangUp()
    }

    override suspend fun sendDtmf(digit: Char) {
        LocalInCallService.playDtmfTone(digit)
    }

    override suspend fun sendAudio(frame: PcmFrame) {
        throw UnsupportedOperationException(
            "Public Android APIs do not expose cellular uplink PCM injection to third-party apps."
        )
    }
}
