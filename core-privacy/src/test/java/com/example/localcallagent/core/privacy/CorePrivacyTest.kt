package com.example.localcallagent.core.privacy

import com.example.localcallagent.core.model.StructuredCallResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CorePrivacyTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testRedactorSanitizesPhoneAndCard() {
        val raw = "Call 518-555-1234 or card 4111111111111111 and ssn 123-45-6789"
        val redacted = Redactor.redact(raw)
        assertTrue(redacted.contains("[REDACTED_PHONE]"))
        assertTrue(redacted.contains("[REDACTED_CARD]"))
        assertTrue(redacted.contains("[REDACTED_SSN]"))
    }

    @Test(expected = SecurityException::class)
    fun testPrivacyAuditBlocksDisallowedAnalyticsKeys() {
        val payload = mapOf<String, Any?>("event" to "call_end", "transcript" to "Hello world")
        PrivacyAudit.verifyAnalyticsPayload("call_end", payload)
    }

    @Test(expected = SecurityException::class)
    fun testNetworkPolicyEnforcerBlocksPstnNetworkAccess() {
        NetworkPolicyEnforcer.validateConnection("sip.example.com", isPstnFlavor = true, configuredSipHosts = setOf("sip.example.com"))
    }

    @Test(expected = SecurityException::class)
    fun testNetworkPolicyEnforcerBlocksNonAllowlistedSipHost() {
        NetworkPolicyEnforcer.validateConnection("evil-ai-server.com", isPstnFlavor = false, configuredSipHosts = setOf("sip.myprovider.com"))
    }

    @Test
    fun testNetworkPolicyEnforcerAllowsConfiguredSipHost() {
        NetworkPolicyEnforcer.validateConnection("sip.myprovider.com", isPstnFlavor = false, configuredSipHosts = setOf("sip.myprovider.com"))
    }

    @Test
    fun testEncryptedTranscriptStoreEncryptsAndDecrypts() = runBlocking {
        val storeDir = tempFolder.newFolder("encrypted_transcripts")
        val store = LocalEncryptedTranscriptStore(storeDir)

        val result = StructuredCallResult(
            destination = "+15185551234",
            businessName = "Test Shop",
            primaryQuestion = "What time do you open?",
            answer = "8am",
            price = "$50",
            confidence = 0.95f,
            callDurationSeconds = 45,
            completedSuccessfully = true,
            reasonCode = "SUCCESS",
            timestampEpochMs = 1234567890L
        )

        val transcript = "Bot: What time do you open?\nBusiness: We open at 8am."
        store.saveCallResult(result, transcript)

        val loadedResults = store.loadCallResults()
        assertEquals(1, loadedResults.size)
        assertEquals("8am", loadedResults[0].answer)

        val loadedTranscript = store.loadTranscript(1234567890L)
        assertNotNull(loadedTranscript)
        assertTrue(loadedTranscript!!.contains("8am"))

        // Verify that the file on disk is NOT plaintext
        val files = storeDir.listFiles()!!
        assertTrue(files.isNotEmpty())
        val onDiskBytes = files[0].readBytes()
        val onDiskString = String(onDiskBytes, Charsets.UTF_8)
        assertTrue(!onDiskString.contains("What time do you open?"))
    }
}
