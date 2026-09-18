package com.example.localcallagent.core.model

/**
 * Unified call state exposed by both PSTN and SIP transports.
 */
sealed interface AppCallState {
    data object Idle : AppCallState
    data object Dialing : AppCallState
    data object Ringing : AppCallState
    data object Active : AppCallState
    data object Holding : AppCallState
    data class Disconnected(val reason: String? = null) : AppCallState
}
