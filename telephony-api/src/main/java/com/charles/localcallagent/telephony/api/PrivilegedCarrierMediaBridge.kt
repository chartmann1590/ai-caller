package com.charles.localcallagent.telephony.api

import com.charles.localcallagent.core.model.PcmFrame
import kotlinx.coroutines.flow.Flow

/**
 * PHASE T — OEM / Carrier Extension Point.
 *
 * NOTE: Standard Google Play third-party applications CANNOT implement this,
 * because capturing/injecting raw PCM into cellular carrier voice calls requires
 * the CAPTURE_AUDIO_OUTPUT permission, which is reserved exclusively for system/OEM
 * privileged applications.
 *
 * This interface serves as the architectural decoupling boundary if this application
 * is later deployed as a privileged system app or partnered with an OEM/carrier.
 */
interface PrivilegedCarrierMediaBridge {
    val remotePcm: Flow<PcmFrame>
    suspend fun sendUplinkPcm(frame: PcmFrame)
}
