package com.example.localcallagent.telephony.sip

import java.nio.ByteBuffer

/**
 * RFC 2833 / RFC 4733 telephone-event DTMF packet payload.
 *
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |     event     |E|R| volume    |          duration             |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */
object Rfc2833Dtmf {

    fun digitToEvent(digit: Char): Int {
        return when (digit) {
            in '0'..'9' -> digit - '0'
            '*' -> 10
            '#' -> 11
            'A', 'a' -> 12
            'B', 'b' -> 13
            'C', 'c' -> 14
            'D', 'd' -> 15
            else -> throw IllegalArgumentException("Invalid DTMF digit '$digit'")
        }
    }

    fun createPayload(digit: Char, endOfEvent: Boolean, volume: Int = 10, durationSamples: Int = 800): ByteArray {
        val event = digitToEvent(digit)
        val buffer = ByteBuffer.allocate(4)
        buffer.put(event.toByte())
        var byte1 = (volume and 0x3F)
        if (endOfEvent) byte1 = byte1 or 0x80
        buffer.put(byte1.toByte())
        buffer.putShort((durationSamples and 0xFFFF).toShort())
        return buffer.array()
    }
}
