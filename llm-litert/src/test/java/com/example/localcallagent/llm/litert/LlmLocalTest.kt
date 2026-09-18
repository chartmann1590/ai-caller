package com.example.localcallagent.llm.litert

import com.example.localcallagent.core.model.AgentAction
import com.example.localcallagent.core.model.CallObjective
import com.example.localcallagent.core.model.DialogueContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmLocalTest {

    private val objective = CallObjective(
        destination = "+15185551234",
        businessName = "Mike's Tires",
        primaryQuestion = "Do you install customer tires and how much is it?"
    )

    @Test
    fun testIvrSelection() = runBlocking {
        val model = DeterministicFallbackModel()
        val context = DialogueContext(
            objective = objective,
            lastRemoteUtterance = "Thank you for calling. Press 1 for sales or 2 for service."
        )
        val decision = model.decide(context)
        assertEquals(AgentAction.SEND_DTMF, decision.action)
        assertEquals('1', decision.dtmfDigit)
    }

    @Test
    fun testVoicemailDetection() = runBlocking {
        val model = DeterministicFallbackModel()
        val context = DialogueContext(
            objective = objective,
            lastRemoteUtterance = "Please leave a message at the tone."
        )
        val decision = model.decide(context)
        assertEquals(AgentAction.FINISH, decision.action)
        assertEquals("VOICEMAIL_DETECTED", decision.reasonCode)
    }

    @Test
    fun testRecipientRefusalHandoff() = runBlocking {
        val model = DeterministicFallbackModel()
        val context = DialogueContext(
            objective = objective,
            lastRemoteUtterance = "I don't want to talk to a robot, let me speak to a human."
        )
        val decision = model.decide(context)
        assertEquals(AgentAction.HANDOFF, decision.action)
        assertTrue(decision.speech!!.contains("Alex"))
    }

    @Test
    fun testPriceResolution() = runBlocking {
        val model = DeterministicFallbackModel()
        val context = DialogueContext(
            objective = objective,
            lastRemoteUtterance = "Yes, we install them. It costs $35 per tire."
        )
        val decision = model.decide(context)
        assertEquals(AgentAction.FINISH, decision.action)
        assertTrue(decision.extractedAnswer!!.contains("$35"))
    }

    @Test
    fun testUnauthorizedPaymentRequestDeclined() = runBlocking {
        val model = DeterministicFallbackModel()
        val context = DialogueContext(
            objective = objective,
            lastRemoteUtterance = "Can I have your credit card number to hold the spot?"
        )
        val decision = model.decide(context)
        assertEquals(AgentAction.SPEAK, decision.action)
        assertEquals("UNAUTHORIZED_REQUEST_DECLINED", decision.reasonCode)
        assertTrue(decision.speech!!.contains("not authorized"))
    }
}
