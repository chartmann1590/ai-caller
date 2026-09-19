package com.charles.localcallagent.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class AgentAction {
    SPEAK,
    LISTEN,
    CLARIFY,
    SEND_DTMF,
    HANDOFF,
    FINISH,
    ABORT
}

@Serializable
data class AgentDecision(
    val action: AgentAction,
    val speech: String? = null,
    val extractedAnswer: String? = null,
    val confidence: Float = 1.0f,
    val reasonCode: String = "DEFAULT",
    val dtmfDigit: Char? = null
)
