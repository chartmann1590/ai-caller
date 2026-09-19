package com.charles.localcallagent.agent.orchestrator

import com.charles.localcallagent.core.model.AgentAction
import com.charles.localcallagent.core.model.AgentDecision
import com.charles.localcallagent.core.model.CallObjective
import java.util.regex.Pattern

object SafetyValidator {

    private val FORBIDDEN_PURCHASE_PATTERN = Pattern.compile(
        """\b(i agree to buy|charge my card|here is my credit card|book the appointment for me|schedule me for|reserve the|i authorize the charge)\b""",
        Pattern.CASE_INSENSITIVE
    )

    private val ALLOWED_DTMF = setOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '*', '#', 'A', 'B', 'C', 'D')

    /**
     * Validates whether an AgentDecision produced by the LLM is legal and safe.
     * Returns a safe sanitized decision if an unsafe action was attempted.
     */
    fun validate(decision: AgentDecision, objective: CallObjective): AgentDecision {
        // 1. Validate DTMF digit
        if (decision.action == AgentAction.SEND_DTMF) {
            val digit = decision.dtmfDigit
            if (digit == null || !ALLOWED_DTMF.contains(digit.uppercaseChar())) {
                return AgentDecision(
                    action = AgentAction.LISTEN,
                    confidence = 0.5f,
                    reasonCode = "INVALID_DTMF_REJECTED"
                )
            }
        }

        // 2. Validate Speech Safety
        val speech = decision.speech
        if (!speech.isNullOrBlank()) {
            if (speech.length > 500) {
                // Speech excessively long
                return decision.copy(speech = speech.substring(0, 300) + "...")
            }

            if (FORBIDDEN_PURCHASE_PATTERN.matcher(speech).find()) {
                // Model hallucinated a purchase or booking commitment! Block immediately!
                return AgentDecision(
                    action = AgentAction.HANDOFF,
                    speech = "I cannot authorize purchases or bookings. Let me transfer you to ${objective.callerName}.",
                    reasonCode = "SAFETY_VIOLATION_PURCHASE_ATTEMPT_BLOCKED"
                )
            }
        }

        // 3. Prevent appointment bookings in MVP
        if (!objective.mayBookAppointment && decision.extractedAnswer?.lowercase()?.contains("appointment booked") == true) {
            return decision.copy(
                extractedAnswer = "Appointment booking not permitted in MVP.",
                reasonCode = "APPOINTMENT_BOOKING_PREVENTED"
            )
        }

        return decision
    }
}
