package com.charles.localcallagent.telephony.sip

import java.nio.ByteBuffer

/**
 * RFC 3550 RTP Packet structure.
 * Fixed 12-byte header:
 * 0                   1                   2                   3
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |V=2|P|X|  CC   |M|     PT      |       sequence number         |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                           timestamp                           |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |           synchronization source (SSRC) identifier            |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */
data class RtpPacket(
    val version: Int = 2,
    val padding: Boolean = false,
    val extension: Boolean = false,
    val csrcCount: Int = 0,
    val marker: Boolean = false,
    val payloadType: Int, // 0 = PCMU, 8 = PCMA, 101 = telephone-event
    val sequenceNumber: Int,
    val timestamp: Long,
    val ssrc: Long,
    val payload: ByteArray
) {
    fun toBytes(): ByteArray {
        val buffer = ByteBuffer.allocate(12 + payload.size)
        var byte0 = (version shl 6)
        if (padding) byte0 = byte0 or 0x20
        if (extension) byte0 = byte0 or 0x10
        byte0 = byte0 or (csrcCount and 0x0F)
        buffer.put(byte0.toByte())

        var byte1 = payloadType and 0x7F
        if (marker) byte1 = byte1 or 0x80
        buffer.put(byte1.toByte())

        buffer.putShort((sequenceNumber and 0xFFFF).toShort())
        buffer.putInt(timestamp.toInt())
        buffer.putInt(ssrc.toInt())
        buffer.put(payload)
        return buffer.array()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RtpPacket
        if (sequenceNumber != other.sequenceNumber) return false
        if (timestamp != other.timestamp) return false
        if (payloadType != other.payloadType) return false
        if (ssrc != other.ssrc) return false
        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sequenceNumber
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + payloadType
        result = 31 * result + ssrc.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }

    companion object {
        fun parse(data: ByteArray, length: Int = data.size): RtpPacket? {
            if (length < 12) return null
            val buffer = ByteBuffer.wrap(data, 0, length)
            val byte0 = buffer.get().toInt() and 0xFF
            val version = byte0 shr 6
            if (version != 2) return null
            val padding = (byte0 and 0x20) != 0
            val extension = (byte0 and 0x10) != 0
            val cc = byte0 and 0x0F

            val byte1 = buffer.get().toInt() and 0xFF
            val marker = (byte1 and 0x80) != 0
            val payloadType = byte1 and 0x7F

            val sequenceNumber = buffer.short.toInt() and 0xFFFF
            val timestamp = buffer.int.toLong() and 0xFFFFFFFFL
            val ssrc = buffer.int.toLong() and 0xFFFFFFFFL

            // Skip CSRC list if any
            val headerSize = 12 + cc * 4
            if (length < headerSize) return null
            buffer.position(headerSize)

            val payloadSize = length - headerSize
            val payload = ByteArray(payloadSize)
            buffer.get(payload)

            return RtpPacket(
                version = version,
                padding = padding,
                extension = extension,
                csrcCount = cc,
                marker = marker,
                payloadType = payloadType,
                sequenceNumber = sequenceNumber,
                timestamp = timestamp,
                ssrc = ssrc,
                payload = payload
            )
        }
    }
}
