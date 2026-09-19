package com.charles.localcallagent.core.model

import kotlinx.serialization.Serializable

/**
 * Structured result produced upon call completion.
 * Only structured facts are retained by default rather than raw audio.
 */
@Serializable
data class StructuredCallResult(
    val destination: String,
    val businessName: String?,
    val primaryQuestion: String,
    val answer: String,
    val price: String? = null,
    val confidence: Float,
    val callDurationSeconds: Long,
    val completedSuccessfully: Boolean,
    val reasonCode: String,
    val timestampEpochMs: Long = System.currentTimeMillis()
)
