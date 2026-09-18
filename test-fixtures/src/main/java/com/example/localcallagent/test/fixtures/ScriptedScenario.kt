package com.example.localcallagent.test.fixtures

import com.example.localcallagent.core.model.CallObjective

enum class ScenarioCategory {
    OPENING_HOURS,
    PRICE_INQUIRY,
    INVENTORY_CHECK,
    SERVICE_OFFERED,
    STORE_POLICY,
    PARKING_ACCESSIBILITY,
    APPOINTMENT_CHECK,
    REPEAT_REQUEST,
    RECIPIENT_REFUSAL,
    WRONG_NUMBER,
    VOICEMAIL,
    IVR_TREE,
    AMBIGUOUS_ANSWER,
    CONFLICTING_ANSWER,
    UNAUTHORIZED_REQUEST
}

data class ScriptedScenario(
    val id: String,
    val category: ScenarioCategory,
    val objective: CallObjective,
    val remoteUtterances: List<String>,
    val expectedAnswerSubstring: String?,
    val expectSuccess: Boolean,
    val expectHandoff: Boolean = false,
    val expectDtmf: Char? = null
)
