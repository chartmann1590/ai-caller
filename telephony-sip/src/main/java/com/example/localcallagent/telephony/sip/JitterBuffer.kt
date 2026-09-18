package com.example.localcallagent.telephony.sip

import com.example.localcallagent.core.model.PcmFrame
import java.util.PriorityQueue

class JitterBuffer(
    private val sampleRateHz: Int = 8000,
    private val maxBufferPackets: Int = 10
) {
    private val queue = PriorityQueue<RtpPacket>(Comparator { p1, p2 ->
        val diff = (p1.sequenceNumber - p2.sequenceNumber)
        when {
            diff > 30000 -> -1
            diff < -30000 -> 1
            else -> diff
        }
    })

    private var expectedSeq: Int = -1

    @Synchronized
    fun push(packet: RtpPacket) {
        if (queue.size >= maxBufferPackets * 2) {
            queue.poll() // drop oldest if overloaded
        }
        queue.offer(packet)
    }

    @Synchronized
    fun popPcmFrame(): PcmFrame? {
        val packet = queue.poll() ?: return null

        if (expectedSeq == -1) {
            expectedSeq = packet.sequenceNumber
        } else {
            expectedSeq = (expectedSeq + 1) and 0xFFFF
        }

        val pcmSamples: ShortArray = when (packet.payloadType) {
            0 -> G711Codec.ulawToLinear(packet.payload) // PCMU
            8 -> G711Codec.alawToLinear(packet.payload) // PCMA
            else -> {
                // If unknown or unsupported payload type, produce silence frame of same length
                ShortArray(packet.payload.size)
            }
        }

        return PcmFrame(
            samples = pcmSamples,
            sampleRateHz = sampleRateHz,
            channelCount = 1,
            timestampUs = (packet.timestamp * 1_000_000L) / sampleRateHz
        )
    }

    @Synchronized
    fun clear() {
        queue.clear()
        expectedSeq = -1
    }

    val size: Int
        @Synchronized get() = queue.size
}
