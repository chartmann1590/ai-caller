package com.example.localcallagent.telephony.sip

/**
 * ITU-T G.711 μ-law (PCMU) and A-law (PCMA) codec.
 * Converts 16-bit linear PCM audio to/from standard PSTN/VoIP 8-bit companded audio.
 */
object G711Codec {

    private const val BIAS = 0x84
    private const val CLIP = 32635
    private const val QUANT_MASK = 0x0F

    private val ULAW_DECODE_TABLE = ShortArray(256)
    private val ALAW_DECODE_TABLE = ShortArray(256)

    init {
        for (i in 0 until 256) {
            ULAW_DECODE_TABLE[i] = decodeUlawSample(i.toByte())
            ALAW_DECODE_TABLE[i] = decodeAlawSample(i.toByte())
        }
    }

    fun linearToUlaw(pcm: ShortArray): ByteArray {
        val out = ByteArray(pcm.size)
        for (i in pcm.indices) {
            out[i] = encodeUlawSample(pcm[i])
        }
        return out
    }

    fun ulawToLinear(ulaw: ByteArray): ShortArray {
        val out = ShortArray(ulaw.size)
        for (i in ulaw.indices) {
            out[i] = ULAW_DECODE_TABLE[ulaw[i].toInt() and 0xFF]
        }
        return out
    }

    fun linearToAlaw(pcm: ShortArray): ByteArray {
        val out = ByteArray(pcm.size)
        for (i in pcm.indices) {
            out[i] = encodeAlawSample(pcm[i])
        }
        return out
    }

    fun alawToLinear(alaw: ByteArray): ShortArray {
        val out = ShortArray(alaw.size)
        for (i in alaw.indices) {
            out[i] = ALAW_DECODE_TABLE[alaw[i].toInt() and 0xFF]
        }
        return out
    }

    private fun encodeUlawSample(sample: Short): Byte {
        var sign = (sample.toInt() shr 8) and 0x80
        var pcm = sample.toInt()
        if (sign != 0) {
            pcm = -pcm
            sign = 0x80
        }
        if (pcm > CLIP) pcm = CLIP
        pcm += BIAS
        var exponent = 7
        var mask = 0x4000
        while ((pcm and mask) == 0 && exponent > 0) {
            exponent--
            mask = mask shr 1
        }
        val mantissa = (pcm shr (exponent + 3)) and 0x0F
        val ulaw = sign or (exponent shl 4) or mantissa
        return (ulaw xor 0xFF).toByte()
    }

    private fun decodeUlawSample(ulaw: Byte): Short {
        val u = ulaw.toInt().inv() and 0xFF
        val sign = u and 0x80
        val exponent = (u shr 4) and 0x07
        val mantissa = u and 0x0F
        var pcm = (mantissa shl (exponent + 3)) + (BIAS shl exponent) - BIAS
        if (sign != 0) pcm = -pcm
        return pcm.coerceIn(-32768, 32767).toShort()
    }

    private fun encodeAlawSample(sample: Short): Byte {
        var pcm = sample.toInt()
        val mask: Int
        if (pcm >= 0) {
            mask = 0xD5
        } else {
            mask = 0x55
            pcm = -pcm - 1
        }
        val seg = when {
            pcm < 0x100 -> 0
            pcm < 0x200 -> 1
            pcm < 0x400 -> 2
            pcm < 0x800 -> 3
            pcm < 0x1000 -> 4
            pcm < 0x2000 -> 5
            pcm < 0x4000 -> 6
            else -> 7
        }
        val shift = if (seg == 0) 4 else seg + 3
        val aval = ((seg shl 4) or ((pcm shr shift) and QUANT_MASK)) xor mask
        return aval.toByte()
    }

    private fun decodeAlawSample(alaw: Byte): Short {
        val a = alaw.toInt() xor 0x55
        var t = (a and QUANT_MASK) shl 4
        val seg = (a and 0x70) shr 4
        when (seg) {
            0 -> t += 8
            1 -> t += 0x108
            else -> {
                t += 0x108
                t = t shl (seg - 1)
            }
        }
        return (if ((a and 0x80) != 0) t else -t).toShort()
    }
}
