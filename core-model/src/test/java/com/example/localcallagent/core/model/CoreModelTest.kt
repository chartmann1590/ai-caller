package com.example.localcallagent.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreModelTest {

    @Test
    fun testPcmFrameDurationCalculation() {
        // 16000 samples @ 16kHz mono = 1.0 second = 1000ms
        val samples = ShortArray(16000)
        val frame = PcmFrame(samples = samples, sampleRateHz = 16000, channelCount = 1)
        assertEquals(1000f, frame.durationMs, 0.01f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testAutonomousCarrierMediaCannotBeTrue() {
        DeviceCapabilities(
            telephonyCalling = true,
            roleDialerAvailable = true,
            roleDialerHeld = false,
            onDeviceSpeechRecognizerAvailable = true,
            aecAvailable = true,
            totalRamMb = 12000,
            availableStorageMb = 50000,
            arm64 = true,
            gpuAvailable = true,
            modelSupported = true,
            autonomousCarrierMedia = true // MUST FAIL!
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun testCallObjectiveCannotCommitMoney() {
        CallObjective(
            destination = "+15185551234",
            primaryQuestion = "How much for tires?",
            mayCommitMoney = true // MUST FAIL!
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun testCallObjectiveCannotBookAppointment() {
        CallObjective(
            destination = "+15185551234",
            primaryQuestion = "What time do you close?",
            mayBookAppointment = true // MUST FAIL!
        )
    }

    @Test
    fun testValidCallObjective() {
        val objective = CallObjective(
            destination = "+15185551234",
            businessName = "Mike's Tires",
            primaryQuestion = "Do you install customer-supplied tires?"
        )
        assertEquals("+15185551234", objective.destination)
        assertFalse(objective.mayBookAppointment)
        assertFalse(objective.mayCommitMoney)
    }
}
