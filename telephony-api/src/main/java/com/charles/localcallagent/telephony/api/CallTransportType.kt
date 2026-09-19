package com.charles.localcallagent.telephony.api

enum class CallTransportType {
    /**
     * Autonomous AI Softphone using SIP/VoIP.
     * Full programmatic bidirectional PCM audio.
     */
    SIP_VOIP,

    /**
     * Android Telecom Carrier Dialing & Call Control.
     * No programmatic media access (public Android restriction).
     */
    CARRIER_PSTN
}
