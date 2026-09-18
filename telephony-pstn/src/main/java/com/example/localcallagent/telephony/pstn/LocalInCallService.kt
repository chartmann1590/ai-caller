package com.example.localcallagent.telephony.pstn

import android.telecom.Call
import android.telecom.InCallService
import com.example.localcallagent.core.model.AppCallState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LocalInCallService : InCallService() {

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call?, state: Int) {
            super.onStateChanged(call, state)
            updateState(state)
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        activeTelecomCall = call
        call.registerCallback(callback)
        updateState(call.state)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callback)
        if (activeTelecomCall == call) {
            activeTelecomCall = null
            _callState.value = AppCallState.Disconnected("Call terminated")
        }
    }

    private fun updateState(telecomState: Int) {
        _callState.value = when (telecomState) {
            Call.STATE_NEW, Call.STATE_CONNECTING -> AppCallState.Dialing
            Call.STATE_DIALING -> AppCallState.Dialing
            Call.STATE_RINGING -> AppCallState.Ringing
            Call.STATE_ACTIVE -> AppCallState.Active
            Call.STATE_HOLDING -> AppCallState.Holding
            Call.STATE_DISCONNECTED -> AppCallState.Disconnected("Remote disconnected")
            Call.STATE_DISCONNECTING -> AppCallState.Disconnected("Disconnecting")
            else -> AppCallState.Idle
        }
    }

    companion object {
        var activeTelecomCall: Call? = null
            private set

        private val _callState = MutableStateFlow<AppCallState>(AppCallState.Idle)
        val callState: StateFlow<AppCallState> = _callState.asStateFlow()

        fun hangUp() {
            activeTelecomCall?.disconnect()
        }

        fun answer() {
            activeTelecomCall?.answer(0)
        }

        fun playDtmfTone(digit: Char) {
            activeTelecomCall?.playDtmfTone(digit)
            activeTelecomCall?.stopDtmfTone()
        }
    }
}
