package com.charles.localcallagent.telephony.pstn

import com.charles.localcallagent.core.model.PcmFrame
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Test

class PstnControlTransportTest {

    // Mock controller without invoking Android Context
    private val transport = PstnControlTransport(
        controller = PstnCallController(
            context = object : android.content.ContextWrapper(null) {}
        )
    )

    @Test
    fun testSupportsProgrammaticMediaIsFalse() {
        assertFalse(transport.supportsProgrammaticMedia)
    }

    @Test(expected = UnsupportedOperationException::class)
    fun testRemoteAudioThrowsUnsupportedOperation() = runBlocking {
        transport.remoteAudio.first()
        Unit
    }

    @Test(expected = UnsupportedOperationException::class)
    fun testSendAudioThrowsUnsupportedOperation() = runBlocking {
        val dummyFrame = PcmFrame(ShortArray(160), 8000)
        transport.sendAudio(dummyFrame)
    }
}
