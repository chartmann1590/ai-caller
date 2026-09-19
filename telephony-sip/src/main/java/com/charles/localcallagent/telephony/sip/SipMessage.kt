package com.charles.localcallagent.telephony.sip

import java.security.MessageDigest
import java.util.Locale

data class SipMessage(
    val isRequest: Boolean,
    val method: String? = null,
    val requestUri: String? = null,
    val statusCode: Int = 0,
    val reasonPhrase: String? = null,
    val headers: Map<String, String>,
    val body: String = ""
) {
    fun getHeader(name: String): String? {
        val lower = name.lowercase(Locale.ROOT)
        for ((k, v) in headers) {
            if (k.lowercase(Locale.ROOT) == lower) return v
        }
        return null
    }

    fun toByteArray(): ByteArray {
        val sb = StringBuilder()
        if (isRequest) {
            sb.append("$method $requestUri SIP/2.0\r\n")
        } else {
            sb.append("SIP/2.0 $statusCode $reasonPhrase\r\n")
        }
        for ((k, v) in headers) {
            sb.append("$k: $v\r\n")
        }
        sb.append("\r\n")
        sb.append(body)
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    companion object {
        fun parse(data: String): SipMessage? {
            val lines = data.split("\r\n")
            if (lines.isEmpty() || lines[0].isBlank()) return null

            val startLine = lines[0].trim()
            val isRequest: Boolean
            var method: String? = null
            var requestUri: String? = null
            var statusCode = 0
            var reasonPhrase: String? = null

            if (startLine.startsWith("SIP/2.0")) {
                isRequest = false
                val parts = startLine.split(" ", limit = 3)
                if (parts.size >= 2) {
                    statusCode = parts[1].toIntOrNull() ?: 0
                    reasonPhrase = if (parts.size >= 3) parts[2] else ""
                }
            } else {
                isRequest = true
                val parts = startLine.split(" ", limit = 3)
                if (parts.size >= 2) {
                    method = parts[0]
                    requestUri = parts[1]
                }
            }

            val headers = mutableMapOf<String, String>()
            var i = 1
            while (i < lines.size) {
                val line = lines[i]
                if (line.isBlank()) {
                    i++
                    break
                }
                val colonIdx = line.indexOf(':')
                if (colonIdx > 0) {
                    val hName = line.substring(0, colonIdx).trim()
                    val hVal = line.substring(colonIdx + 1).trim()
                    headers[hName] = hVal
                }
                i++
            }

            val body = if (i < lines.size) {
                lines.subList(i, lines.size).joinToString("\r\n")
            } else ""

            return SipMessage(
                isRequest = isRequest,
                method = method,
                requestUri = requestUri,
                statusCode = statusCode,
                reasonPhrase = reasonPhrase,
                headers = headers,
                body = body
            )
        }

        fun md5(input: String): String {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(input.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }

        /**
         * Computes RFC 2617 Digest Authorization header value.
         */
        fun computeDigestAuth(
            username: String,
            realm: String,
            password: String,
            method: String,
            uri: String,
            nonce: String,
            opaque: String? = null,
            qop: String? = null,
            cnonce: String? = null,
            nc: String = "00000001"
        ): String {
            val ha1 = md5("$username:$realm:$password")
            val ha2 = md5("$method:$uri")
            // RFC 2617: qop=auth requires response = MD5(HA1:nonce:nc:cnonce:qop:HA2).
            // Servers advertising qop (sip2sip.info, iptel.org) reject the legacy
            // MD5(HA1:nonce:HA2) response, so qop must change the hash, not just the header.
            val useQop = qop?.split(",")?.map { it.trim() }?.firstOrNull { it == "auth" }
            val effectiveCnonce = cnonce ?: md5("$nonce:$username").take(16)
            val response = if (useQop != null) {
                md5("$ha1:$nonce:$nc:$effectiveCnonce:$useQop:$ha2")
            } else {
                md5("$ha1:$nonce:$ha2")
            }

            val sb = StringBuilder("Digest ")
            sb.append("username=\"$username\", ")
            sb.append("realm=\"$realm\", ")
            sb.append("nonce=\"$nonce\", ")
            sb.append("uri=\"$uri\", ")
            sb.append("response=\"$response\"")
            if (!opaque.isNullOrBlank()) {
                sb.append(", opaque=\"$opaque\"")
            }
            if (useQop != null) {
                sb.append(", qop=$useQop, nc=$nc, cnonce=\"$effectiveCnonce\"")
            }
            return sb.toString()
        }

        /**
         * Builds standard SDP offer for voice calling (G.711 μ-law, A-law, and DTMF telephone-event).
         */
        fun buildSdpOffer(localIp: String, localRtpPort: Int): String {
            return listOf(
                "v=0",
                "o=LocalCallAgent 123456 123456 IN IP4 $localIp",
                "s=Talk",
                "c=IN IP4 $localIp",
                "t=0 0",
                "m=audio $localRtpPort RTP/AVP 0 8 101",
                "a=rtpmap:0 PCMU/8000",
                "a=rtpmap:8 PCMA/8000",
                "a=rtpmap:101 telephone-event/8000",
                "a=fmtp:101 0-16",
                "a=sendrecv"
            ).joinToString("\r\n") + "\r\n"
        }

        /**
         * Parses remote RTP IP and Port from SDP body.
         */
        fun parseSdpAudioTarget(sdp: String): Pair<String, Int>? {
            var ip: String? = null
            var port: Int? = null

            for (line in sdp.lines()) {
                val trimmed = line.trim()
                if (trimmed.startsWith("c=IN IP4 ")) {
                    ip = trimmed.substring("c=IN IP4 ".length).trim()
                } else if (trimmed.startsWith("m=audio ")) {
                    val parts = trimmed.split(" ")
                    if (parts.size >= 2) {
                        port = parts[1].toIntOrNull()
                    }
                }
            }

            return if (ip != null && port != null) Pair(ip, port) else null
        }
    }
}
