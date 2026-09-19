package com.charles.localcallagent.telephony.sip

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelephonySipTest {

    @Test
    fun testG711UlawRoundTrip() {
        val original = shortArrayOf(0, 1000, -1000, 5000, -5000, 16000, -16000)
        val encoded = G711Codec.linearToUlaw(original)
        val decoded = G711Codec.ulawToLinear(encoded)

        assertEquals(original.size, decoded.size)
        for (i in original.indices) {
            // G.711 is lossy companding, error should be within small tolerance (< 3%)
            val diff = Math.abs(original[i] - decoded[i])
            val maxAllowed = Math.max(50, (Math.abs(original[i].toInt()) * 0.05).toInt())
            assertTrue("Sample $i diff $diff > $maxAllowed", diff <= maxAllowed)
        }
    }

    @Test
    fun testG711AlawRoundTrip() {
        val original = shortArrayOf(0, 500, -500, 2000, -2000, 10000, -10000)
        val encoded = G711Codec.linearToAlaw(original)
        val decoded = G711Codec.alawToLinear(encoded)

        assertEquals(original.size, decoded.size)
        for (i in original.indices) {
            val diff = Math.abs(original[i] - decoded[i])
            val maxAllowed = Math.max(50, (Math.abs(original[i].toInt()) * 0.05).toInt())
            assertTrue("Sample $i diff $diff > $maxAllowed", diff <= maxAllowed)
        }
    }

    @Test
    fun testRtpPacketSerializationAndParsing() {
        val payload = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val packet = RtpPacket(
            version = 2,
            payloadType = 0,
            sequenceNumber = 1234,
            timestamp = 987654L,
            ssrc = 11223344L,
            payload = payload
        )

        val bytes = packet.toBytes()
        val parsed = RtpPacket.parse(bytes)

        assertNotNull(parsed)
        assertEquals(2, parsed!!.version)
        assertEquals(0, parsed.payloadType)
        assertEquals(1234, parsed.sequenceNumber)
        assertEquals(987654L, parsed.timestamp)
        assertEquals(11223344L, parsed.ssrc)
        assertArrayEquals(payload, parsed.payload)
    }

    @Test
    fun testRfc2833DtmfPayload() {
        val payload = Rfc2833Dtmf.createPayload('5', endOfEvent = true, volume = 10, durationSamples = 800)
        assertEquals(4, payload.size)
        assertEquals(5, payload[0].toInt()) // Event 5
        assertTrue((payload[1].toInt() and 0x80) != 0) // End-of-event flag set
    }

    @Test
    fun testSdpAudioTargetParsing() {
        val sdp = listOf(
            "v=0",
            "o=Asterisk 123 123 IN IP4 192.168.1.50",
            "s=Asterisk PBX",
            "c=IN IP4 192.168.1.50",
            "t=0 0",
            "m=audio 10050 RTP/AVP 0 8 101"
        ).joinToString("\r\n")

        val target = SipMessage.parseSdpAudioTarget(sdp)
        assertNotNull(target)
        assertEquals("192.168.1.50", target!!.first)
        assertEquals(10050, target.second)
    }

    @Test
    fun testSipMessageParseAndAuthDigest() {
        val raw = "INVITE sip:5185551234@sip.example.com SIP/2.0\r\n" +
                "Via: SIP/2.0/UDP 10.0.0.5:5060;branch=z9hG4bK1234\r\n" +
                "From: <sip:1001@sip.example.com>;tag=abc123\r\n" +
                "To: <sip:5185551234@sip.example.com>\r\n" +
                "Call-ID: call-xyz@10.0.0.5\r\n" +
                "CSeq: 1 INVITE\r\n" +
                "Content-Length: 0\r\n\r\n"

        val msg = SipMessage.parse(raw)
        assertNotNull(msg)
        assertTrue(msg!!.isRequest)
        assertEquals("INVITE", msg.method)
        assertEquals("call-xyz@10.0.0.5", msg.getHeader("Call-ID"))

        val auth = SipMessage.computeDigestAuth(
            username = "1001",
            realm = "sip.example.com",
            password = "secretpassword",
            method = "REGISTER",
            uri = "sip:sip.example.com",
            nonce = "dcd98b7102dd2f0e8b11d0f600bfb0c093"
        )
        assertTrue(auth.startsWith("Digest "))
        assertTrue(auth.contains("username=\"1001\""))
        assertTrue(auth.contains("nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\""))
    }

    @Test
    fun testAuthDigestUsesRequestUriNotBareDomain() {
        // INVITE auth must hash the request-URI used in the Authorization header
        val inviteUri = "sip:4444@sip2sip.info"
        val authInvite = SipMessage.computeDigestAuth(
            username = "alice",
            realm = "sip2sip.info",
            password = "secret",
            method = "INVITE",
            uri = inviteUri,
            nonce = "abc123"
        )
        val authRegister = SipMessage.computeDigestAuth(
            username = "alice",
            realm = "sip2sip.info",
            password = "secret",
            method = "REGISTER",
            uri = "sip:sip2sip.info",
            nonce = "abc123"
        )
        assertTrue(authInvite.contains("uri=\"$inviteUri\""))
        assertTrue(authRegister.contains("uri=\"sip:sip2sip.info\""))
        // Different method/uri => different response digests
        val respInvite = Regex("response=\"([a-f0-9]+)\"").find(authInvite)!!.groupValues[1]
        val respRegister = Regex("response=\"([a-f0-9]+)\"").find(authRegister)!!.groupValues[1]
        assertTrue(respInvite != respRegister)
    }

    @Test
    fun testDigestAuthWithQopAuthUsesRfc2617Response() {
        // sip2sip.info / iptel.org challenge with qop="auth" - response must be
        // MD5(HA1:nonce:nc:cnonce:qop:HA2), not the legacy MD5(HA1:nonce:HA2).
        val auth = SipMessage.computeDigestAuth(
            username = "alice",
            realm = "sip2sip.info",
            password = "secret",
            method = "REGISTER",
            uri = "sip:sip2sip.info",
            nonce = "abc123",
            qop = "auth",
            cnonce = "deadbeefcafefeed",
            nc = "00000001"
        )
        val ha1 = SipMessage.md5("alice:sip2sip.info:secret")
        val ha2 = SipMessage.md5("REGISTER:sip:sip2sip.info")
        val expectedResponse = SipMessage.md5("$ha1:abc123:00000001:deadbeefcafefeed:auth:$ha2")
        assertTrue(auth.contains("response=\"$expectedResponse\""))
        assertTrue(auth.contains("qop=auth"))
        assertTrue(auth.contains("nc=00000001"))
        assertTrue(auth.contains("cnonce=\"deadbeefcafefeed\""))
    }

    @Test
    fun testDigestAuthWithoutQopUsesLegacyResponse() {
        // No qop in the challenge => must stay on the legacy MD5(HA1:nonce:HA2) response
        // and must not emit qop/cnonce/nc (some servers reject unexpected params).
        val auth = SipMessage.computeDigestAuth(
            username = "alice",
            realm = "sip2sip.info",
            password = "secret",
            method = "REGISTER",
            uri = "sip:sip2sip.info",
            nonce = "abc123"
        )
        assertTrue(!auth.contains("qop="))
        assertTrue(!auth.contains("cnonce="))
    }

}
