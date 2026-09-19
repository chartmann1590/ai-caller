package com.charles.localcallagent.core.privacy

object PrivacyAudit {
    private val FORBIDDEN_KEYWORDS = listOf(
        "credit_card",
        "cvv",
        "ssn",
        "bank_account",
        "pin_number",
        "password",
        "user_private_fact"
    )

    /**
     * Checks if any string to be logged or serialized violates privacy rules.
     * Throws SecurityException if violation detected.
     */
    fun assertSafeForLogging(text: String) {
        val lower = text.lowercase()
        for (keyword in FORBIDDEN_KEYWORDS) {
            if (lower.contains(keyword)) {
                throw SecurityException("Privacy violation: Log contains forbidden sensitive key '$keyword'")
            }
        }
    }

    /**
     * Verifies that no conversation audio, prompts, or raw transcripts are being sent over analytics.
     */
    fun verifyAnalyticsPayload(event: String, params: Map<String, Any?>) {
        val disallowedKeys = setOf("transcript", "prompt", "audio", "speech", "phone_number", "contact_name", "business_reply")
        for (key in params.keys) {
            if (disallowedKeys.contains(key.lowercase())) {
                throw SecurityException("Privacy violation: Analytics payload cannot contain key '$key'")
            }
        }
    }
}
