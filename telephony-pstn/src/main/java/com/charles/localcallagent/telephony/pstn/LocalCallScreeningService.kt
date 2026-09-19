package com.charles.localcallagent.telephony.pstn

import android.telecom.Call
import android.telecom.CallScreeningService

class LocalCallScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        // Default behavior: allow call through without blocking or silence
        val response = CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSilenceCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()
        respondToCall(callDetails, response)
    }
}
