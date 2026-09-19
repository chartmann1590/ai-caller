package com.charles.localcallagent.telephony.sip

import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccountHandle
import com.charles.localcallagent.core.model.AppCallState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AgentConnectionService : ConnectionService() {

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        val connection = SipTelecomConnection()
        connection.setInitializing()
        connection.setDialing()
        activeConnection = connection
        _connectionState.value = AppCallState.Dialing
        return connection
    }

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        val connection = SipTelecomConnection()
        connection.setRinging()
        activeConnection = connection
        _connectionState.value = AppCallState.Ringing
        return connection
    }

    class SipTelecomConnection : Connection() {
        init {
            connectionCapabilities = CAPABILITY_MUTE or CAPABILITY_SUPPORT_HOLD or CAPABILITY_HOLD
        }

        override fun onAnswer(videoState: Int) {
            super.onAnswer(videoState)
            setActive()
            _connectionState.value = AppCallState.Active
        }

        override fun onDisconnect() {
            super.onDisconnect()
            setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
            destroy()
            activeConnection = null
            _connectionState.value = AppCallState.Disconnected("Local disconnected")
        }

        override fun onAbort() {
            super.onAbort()
            setDisconnected(DisconnectCause(DisconnectCause.CANCELED))
            destroy()
            activeConnection = null
            _connectionState.value = AppCallState.Disconnected("Call aborted")
        }

        override fun onHold() {
            super.onHold()
            setOnHold()
            _connectionState.value = AppCallState.Holding
        }

        override fun onUnhold() {
            super.onUnhold()
            setActive()
            _connectionState.value = AppCallState.Active
        }
    }

    companion object {
        var activeConnection: SipTelecomConnection? = null
            private set

        private val _connectionState = MutableStateFlow<AppCallState>(AppCallState.Idle)
        val connectionState: StateFlow<AppCallState> = _connectionState.asStateFlow()

        fun markActive() {
            activeConnection?.setActive()
            _connectionState.value = AppCallState.Active
        }

        fun markDisconnected(reason: String) {
            activeConnection?.setDisconnected(DisconnectCause(DisconnectCause.REMOTE, reason))
            activeConnection?.destroy()
            activeConnection = null
            _connectionState.value = AppCallState.Disconnected(reason)
        }
    }
}
