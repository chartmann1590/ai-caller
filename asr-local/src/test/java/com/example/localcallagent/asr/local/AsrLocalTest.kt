package com.example.localcallagent.asr.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AsrLocalTest {

    @Test
    fun testRuleSlotExtractorPrice() {
        val text = "Sure, it costs $35 per tire to get them installed."
        val slots = RuleSlotExtractor.extractSlots(text)
        assertEquals("$35", slots["PRICE"])
        assertEquals("YES", slots["CONFIRMATION"])
    }

    @Test
    fun testRuleSlotExtractorIvr() {
        val text = "Thank you for calling. Press 1 for sales, or press 2 for customer service."
        val slots = RuleSlotExtractor.extractSlots(text)
        assertNotNull(slots["IVR_DIGIT"])
        assertEquals("1", slots["IVR_DIGIT"])
    }

    @Test
    fun testCriticalSlotValidatorDetectsAmbiguity() {
        // High confidence single answer
        val goodResult = AsrResult(text = "Yes, we are open.", confidence = 0.95f)
        assertTrue(CriticalSlotValidator.validate(goodResult))

        // Low confidence requires clarification
        val lowConfResult = AsrResult(text = "Yeah maybe 30", confidence = 0.65f)
        assertFalse(CriticalSlotValidator.validate(lowConfResult))

        // Contradictory yes and no
        val mixedResult = AsrResult(text = "Yes but actually no", confidence = 0.90f)
        assertFalse(CriticalSlotValidator.validate(mixedResult))
    }
}
