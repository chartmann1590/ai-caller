package com.charles.localcallagent.asr.local

object CriticalSlotValidator {

    private const val CRITICAL_CONFIDENCE_THRESHOLD = 0.85f

    /**
     * Determines whether the extracted slot requires an explicit verbal confirmation.
     * Prevents the LLM from silently guessing uncertain numbers or pricing.
     */
    fun validate(asrResult: AsrResult): Boolean {
        if (asrResult.confidence < CRITICAL_CONFIDENCE_THRESHOLD) {
            return false // Needs clarification
        }

        val text = asrResult.text.lowercase()
        // If ambiguous mixed statements
        if (text.contains("yes") && text.contains("no")) {
            return false
        }

        // If price is mentioned, ensure it doesn't have conflicting numbers
        if (asrResult.detectedSlots.containsKey("PRICE")) {
            if (asrResult.confidence < 0.90f) {
                return false
            }
        }

        return true
    }
}
