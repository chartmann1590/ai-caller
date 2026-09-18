package com.example.localcallagent.core.model

import kotlinx.serialization.Serializable

/**
 * Immutable call objective defined before dialing.
 * For MVP safety: mayBookAppointment and mayCommitMoney MUST default to false.
 */
@Serializable
data class CallObjective(
    val destination: String,
    val businessName: String? = null,
    val primaryQuestion: String,
    val allowedFollowUps: List<String> = emptyList(),
    val mayBookAppointment: Boolean = false,
    val mayCommitMoney: Boolean = false,
    val maxCallDurationSeconds: Int = 300,
    val callerName: String = "Alex",
    val userFacts: Map<String, String> = emptyMap(),
    val customDisclosure: String? = null
) {
    init {
        require(destination.isNotBlank()) { "Destination number cannot be blank" }
        require(primaryQuestion.isNotBlank()) { "Primary question cannot be blank" }
        require(!mayBookAppointment) { "Appointment booking is strictly prohibited in MVP" }
        require(!mayCommitMoney) { "Monetary commitment is strictly prohibited in MVP" }
    }
}
