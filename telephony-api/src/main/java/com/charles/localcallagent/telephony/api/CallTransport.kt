package com.charles.localcallagent.telephony.api

import com.charles.localcallagent.core.model.AppCallState
import com.charles.localcallagent.core.model.PcmFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Universal call transport interface hiding the underlying telephony implementation.
 *
 * For SIP/VoIP: supportsProgrammaticMedia is true, and bidirectional PCM frames flow to/from the AI.
 * For Cellular/PSTN: supportsProgrammaticMedia is false, and attempting to access media throws UnsupportedOperationException.
 */
interface CallTransport {
    val state: StateFlow<AppCallState>
    val supportsProgrammaticMedia: Boolean
    val remoteAudio: Flow<PcmFrame>

    suspend fun dial(destination: String)
    suspend fun answer()
    suspend fun hangUp()
    suspend fun sendDtmf(digit: Char)
    suspend fun sendAudio(frame: PcmFrame)
}
