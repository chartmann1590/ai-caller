package com.charles.localcallagent.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class Speaker {
    USER,
    BOT,
    REMOTE_BUSINESS
}

@Serializable
data class DialogueTurn(
    val speaker: Speaker,
    val text: String,
    val timestampMs: Long = System.currentTimeMillis()
)

@Serializable
data class DialogueContext(
    val objective: CallObjective,
    val history: List<DialogueTurn> = emptyList(),
    val currentQuestionIndex: Int = 0,
    val clarificationAttempts: Int = 0,
    val lastRemoteUtterance: String? = null
)
