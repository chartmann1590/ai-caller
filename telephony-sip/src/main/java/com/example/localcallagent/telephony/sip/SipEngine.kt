package com.example.localcallagent.telephony.sip

import com.example.localcallagent.core.model.AppCallState
import com.example.localcallagent.core.model.PcmFrame
import com.example.localcallagent.core.model.SipAccountConfig
import com.example.localcallagent.telephony.api.RegistrationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.Random
import java.util.UUID

class SipEngine(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val _registrationState = MutableStateFlow(RegistrationState.UNREGISTERED)
    val registrationState: StateFlow<RegistrationState> = _registrationState.asStateFlow()

    private val _callState = MutableStateFlow<AppCallState>(AppCallState.Idle)
    val callState: StateFlow<AppCallState> = _callState.asStateFlow()

    private val _incomingPcm = MutableSharedFlow<PcmFrame>(extraBufferCapacity = 64)
    val incomingPcm: SharedFlow<PcmFrame> = _incomingPcm.asSharedFlow()

    val jitterBuffer = JitterBuffer(sampleRateHz = 8000)

    private var sipSocket: DatagramSocket? = null
    private var rtpSocket: DatagramSocket? = null

    private var activeConfig: SipAccountConfig? = null
    private var currentCallId: String? = null
    private var fromTag: String = ""
    private var toTag: String? = null
    private var cseq = 1
    private var remoteRtpAddress: InetAddress? = null
    private var remoteRtpPort: Int = 0

    private var rtpSeqNumber = Random().nextInt(10000)
    private var rtpTimestamp = Random().nextLong() and 0xFFFFFF
    private val rtpSsrc = (Random().nextLong() and 0x7FFFFFFF) + 1000

    private var receiveJob: Job? = null
    private var rtpReceiveJob: Job? = null
    private var jitterBufferPumpJob: Job? = null

    fun initialize(config: SipAccountConfig) {
        activeConfig = config
        closeSockets()

        try {
            sipSocket = DatagramSocket()
            rtpSocket = DatagramSocket()
            startListening()
        } catch (e: Exception) {
            _registrationState.value = RegistrationState.FAILED
        }
    }

    private fun startListening() {
        val socket = sipSocket ?: return
        receiveJob = scope.launch {
            val buf = ByteArray(4096)
            while (isActive && !socket.isClosed) {
                try {
                    val packet = DatagramPacket(buf, buf.size)
                    socket.receive(packet)
                    val rawStr = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    val msg = SipMessage.parse(rawStr)
                    if (msg != null) {
                        handleSipMessage(msg)
                    }
                } catch (e: Exception) {
                    if (!socket.isClosed) {
                        // socket read error
                    }
                }
            }
        }
    }

    private fun startRtpListening() {
        val socket = rtpSocket ?: return
        rtpReceiveJob = scope.launch {
            val buf = ByteArray(1500)
            while (isActive && !socket.isClosed) {
                try {
                    val packet = DatagramPacket(buf, buf.size)
                    socket.receive(packet)
                    val rtp = RtpPacket.parse(packet.data, packet.length)
                    if (rtp != null) {
                        jitterBuffer.push(rtp)
                    }
                } catch (e: Exception) {
                    // Socket closed or error
                }
            }
        }

        jitterBufferPumpJob = scope.launch {
            while (isActive) {
                val frame = jitterBuffer.popPcmFrame()
                if (frame != null) {
                    _incomingPcm.emit(frame)
                }
                delay(20) // 20ms frame cadence
            }
        }
    }

    private suspend fun handleSipMessage(msg: SipMessage) {
        if (!msg.isRequest) {
            when (msg.statusCode) {
                100 -> _callState.value = AppCallState.Dialing
                180, 183 -> {
                    _callState.value = AppCallState.Ringing
                    // Check for early media in 183
                    if (msg.body.isNotEmpty()) {
                        val audioTarget = SipMessage.parseSdpAudioTarget(msg.body)
                        if (audioTarget != null) {
                            remoteRtpAddress = InetAddress.getByName(audioTarget.first)
                            remoteRtpPort = audioTarget.second
                            startRtpListening()
                        }
                    }
                }
                200 -> {
                    val cseqHeader = msg.getHeader("CSeq") ?: ""
                    if (cseqHeader.contains("REGISTER")) {
                        _registrationState.value = RegistrationState.REGISTERED
                    } else if (cseqHeader.contains("INVITE")) {
                        _callState.value = AppCallState.Active
                        toTag = extractTag(msg.getHeader("To"))
                        val audioTarget = SipMessage.parseSdpAudioTarget(msg.body)
                        if (audioTarget != null) {
                            remoteRtpAddress = InetAddress.getByName(audioTarget.first)
                            remoteRtpPort = audioTarget.second
                            startRtpListening()
                        }
                        sendAck()
                    } else if (cseqHeader.contains("BYE")) {
                        _callState.value = AppCallState.Disconnected("Call ended normally")
                        stopRtp()
                    }
                }
                401, 407 -> {
                    handleAuthChallenge(msg)
                }
                486 -> {
                    _callState.value = AppCallState.Disconnected("Busy Here (486)")
                    stopRtp()
                }
                603 -> {
                    _callState.value = AppCallState.Disconnected("Decline (603)")
                    stopRtp()
                }
                in 400..699 -> {
                    val cseqHeader = msg.getHeader("CSeq") ?: ""
                    if (cseqHeader.contains("REGISTER")) {
                        _registrationState.value = RegistrationState.FAILED
                    } else {
                        _callState.value = AppCallState.Disconnected("Call rejected (${msg.statusCode} ${msg.reasonPhrase})")
                        stopRtp()
                    }
                }
            }
        } else {
            // Inbound request from remote
            when (msg.method) {
                "BYE" -> {
                    sendResponse(msg, 200, "OK")
                    _callState.value = AppCallState.Disconnected("Remote party hung up")
                    stopRtp()
                }
                "OPTIONS" -> {
                    sendResponse(msg, 200, "OK")
                }
            }
        }
    }

    suspend fun register() {
        val config = activeConfig ?: return
        _registrationState.value = RegistrationState.REGISTERING
        currentCallId = UUID.randomUUID().toString() + "@" + getLocalIp()
        fromTag = UUID.randomUUID().toString().substring(0, 8)
        cseq = 1

        val headers = mutableMapOf(
            "Via" to "SIP/2.0/UDP ${getLocalIp()}:${sipSocket?.localPort ?: 5060};branch=z9hG4bK${UUID.randomUUID().toString().substring(0, 8)};rport",
            "Max-Forwards" to "70",
            "From" to "<sip:${config.username}@${config.domain}>;tag=$fromTag",
            "To" to "<sip:${config.username}@${config.domain}>",
            "Call-ID" to currentCallId!!,
            "CSeq" to "$cseq REGISTER",
            "Contact" to "<sip:${config.username}@${getLocalIp()}:${sipSocket?.localPort ?: 5060}>",
            "Expires" to config.registrationIntervalSeconds.toString(),
            "Content-Length" to "0"
        )

        val msg = SipMessage(
            isRequest = true,
            method = "REGISTER",
            requestUri = "sip:${config.domain}",
            headers = headers
        )
        sendSip(msg)
    }

    suspend fun unregister() {
        val config = activeConfig ?: return
        _registrationState.value = RegistrationState.UNREGISTERED
        val headers = mutableMapOf(
            "Via" to "SIP/2.0/UDP ${getLocalIp()}:${sipSocket?.localPort ?: 5060};branch=z9hG4bK${UUID.randomUUID().toString().substring(0, 8)};rport",
            "Max-Forwards" to "70",
            "From" to "<sip:${config.username}@${config.domain}>;tag=$fromTag",
            "To" to "<sip:${config.username}@${config.domain}>",
            "Call-ID" to (currentCallId ?: UUID.randomUUID().toString()),
            "CSeq" to "${++cseq} REGISTER",
            "Contact" to "*",
            "Expires" to "0",
            "Content-Length" to "0"
        )
        val msg = SipMessage(isRequest = true, method = "REGISTER", requestUri = "sip:${config.domain}", headers = headers)
        sendSip(msg)
    }

    suspend fun dial(destination: String) {
        val config = activeConfig ?: throw IllegalStateException("SIP account not configured")
        _callState.value = AppCallState.Dialing
        currentCallId = UUID.randomUUID().toString() + "@" + getLocalIp()
        fromTag = UUID.randomUUID().toString().substring(0, 8)
        toTag = null
        cseq = 1

        val localPort = rtpSocket?.localPort ?: 10000
        val sdp = SipMessage.buildSdpOffer(getLocalIp(), localPort)

        val headers = mutableMapOf(
            "Via" to "SIP/2.0/UDP ${getLocalIp()}:${sipSocket?.localPort ?: 5060};branch=z9hG4bK${UUID.randomUUID().toString().substring(0, 8)};rport",
            "Max-Forwards" to "70",
            "From" to "\"${config.displayName ?: config.username}\" <sip:${config.username}@${config.domain}>;tag=$fromTag",
            "To" to "<sip:$destination@${config.domain}>",
            "Call-ID" to currentCallId!!,
            "CSeq" to "$cseq INVITE",
            "Contact" to "<sip:${config.username}@${getLocalIp()}:${sipSocket?.localPort ?: 5060}>",
            "Content-Type" to "application/sdp",
            "Content-Length" to sdp.length.toString()
        )

        val msg = SipMessage(
            isRequest = true,
            method = "INVITE",
            requestUri = "sip:$destination@${config.domain}",
            headers = headers,
            body = sdp
        )
        sendSip(msg)
    }

    suspend fun hangup() {
        val config = activeConfig ?: return
        val callId = currentCallId ?: return

        val headers = mutableMapOf(
            "Via" to "SIP/2.0/UDP ${getLocalIp()}:${sipSocket?.localPort ?: 5060};branch=z9hG4bK${UUID.randomUUID().toString().substring(0, 8)};rport",
            "Max-Forwards" to "70",
            "From" to "<sip:${config.username}@${config.domain}>;tag=$fromTag",
            "To" to "<sip:${config.username}@${config.domain}>" + (if (toTag != null) ";tag=$toTag" else ""),
            "Call-ID" to callId,
            "CSeq" to "${++cseq} BYE",
            "Content-Length" to "0"
        )
        val msg = SipMessage(isRequest = true, method = "BYE", requestUri = "sip:${config.domain}", headers = headers)
        sendSip(msg)

        _callState.value = AppCallState.Disconnected("User hung up")
        stopRtp()
    }

    suspend fun sendDtmf(digit: Char) {
        val addr = remoteRtpAddress ?: return
        val port = remoteRtpPort
        val socket = rtpSocket ?: return

        // Send 3 RFC 2833 packets with increasing duration then end-of-event flag
        for (i in 1..3) {
            val isEnd = (i == 3)
            val duration = i * 200
            val payload = Rfc2833Dtmf.createPayload(digit, endOfEvent = isEnd, durationSamples = duration)
            val packet = RtpPacket(
                payloadType = 101, // telephone-event
                sequenceNumber = rtpSeqNumber++,
                timestamp = rtpTimestamp,
                ssrc = rtpSsrc,
                marker = (i == 1),
                payload = payload
            )
            val bytes = packet.toBytes()
            val datagram = DatagramPacket(bytes, bytes.size, addr, port)
            socket.send(datagram)
            delay(20)
        }
        rtpTimestamp += 800
    }

    suspend fun sendAudioFrame(frame: PcmFrame) {
        val addr = remoteRtpAddress ?: return
        val port = remoteRtpPort
        val socket = rtpSocket ?: return

        // Telephony G.711 operates at 8kHz. If frame is at 16kHz, downsample or encode.
        val samples8k = if (frame.sampleRateHz == 16000) {
            downsample16kTo8k(frame.samples)
        } else {
            frame.samples
        }

        val ulawBytes = G711Codec.linearToUlaw(samples8k)
        val packet = RtpPacket(
            payloadType = 0, // PCMU
            sequenceNumber = rtpSeqNumber++,
            timestamp = rtpTimestamp,
            ssrc = rtpSsrc,
            payload = ulawBytes
        )
        rtpTimestamp += samples8k.size

        val bytes = packet.toBytes()
        val datagram = DatagramPacket(bytes, bytes.size, addr, port)
        socket.send(datagram)
    }

    private fun downsample16kTo8k(in16k: ShortArray): ShortArray {
        val out8k = ShortArray(in16k.size / 2)
        for (i in out8k.indices) {
            out8k[i] = in16k[i * 2]
        }
        return out8k
    }

    private suspend fun sendAck() {
        val config = activeConfig ?: return
        val headers = mutableMapOf(
            "Via" to "SIP/2.0/UDP ${getLocalIp()}:${sipSocket?.localPort ?: 5060};branch=z9hG4bK${UUID.randomUUID().toString().substring(0, 8)};rport",
            "Max-Forwards" to "70",
            "From" to "<sip:${config.username}@${config.domain}>;tag=$fromTag",
            "To" to "<sip:${config.username}@${config.domain}>" + (if (toTag != null) ";tag=$toTag" else ""),
            "Call-ID" to currentCallId!!,
            "CSeq" to "$cseq ACK",
            "Content-Length" to "0"
        )
        val msg = SipMessage(isRequest = true, method = "ACK", requestUri = "sip:${config.domain}", headers = headers)
        sendSip(msg)
    }

    private suspend fun sendResponse(req: SipMessage, code: Int, reason: String) {
        val headers = mutableMapOf<String, String>()
        req.getHeader("Via")?.let { headers["Via"] = it }
        req.getHeader("From")?.let { headers["From"] = it }
        req.getHeader("To")?.let { headers["To"] = it }
        req.getHeader("Call-ID")?.let { headers["Call-ID"] = it }
        req.getHeader("CSeq")?.let { headers["CSeq"] = it }
        headers["Content-Length"] = "0"

        val resp = SipMessage(isRequest = false, statusCode = code, reasonPhrase = reason, headers = headers)
        sendSip(resp)
    }

    private suspend fun handleAuthChallenge(msg: SipMessage) {
        val config = activeConfig ?: return
        val authHeader = msg.getHeader("WWW-Authenticate") ?: msg.getHeader("Proxy-Authenticate") ?: return
        val realm = extractParameter(authHeader, "realm") ?: config.domain
        val nonce = extractParameter(authHeader, "nonce") ?: return
        val opaque = extractParameter(authHeader, "opaque")

        val cseqHeader = msg.getHeader("CSeq") ?: ""
        val method = if (cseqHeader.contains("REGISTER")) "REGISTER" else "INVITE"
        val uri = "sip:${config.domain}"

        val authVal = SipMessage.computeDigestAuth(
            username = config.authUsername,
            realm = realm,
            password = config.password,
            method = method,
            uri = uri,
            nonce = nonce,
            opaque = opaque
        )

        val newHeaders = msg.headers.toMutableMap()
        newHeaders["Authorization"] = authVal
        newHeaders["CSeq"] = "${++cseq} $method"

        val resendMsg = SipMessage(isRequest = true, method = method, requestUri = uri, headers = newHeaders, body = msg.body)
        sendSip(resendMsg)
    }

    private fun extractParameter(header: String, paramName: String): String? {
        val key = "$paramName=\""
        val idx = header.indexOf(key)
        if (idx == -1) return null
        val start = idx + key.length
        val end = header.indexOf('"', start)
        if (end == -1) return null
        return header.substring(start, end)
    }

    private fun extractTag(header: String?): String? {
        if (header == null) return null
        val idx = header.indexOf("tag=")
        if (idx == -1) return null
        return header.substring(idx + 4).split(";")[0].trim()
    }

    private suspend fun sendSip(msg: SipMessage) {
        val config = activeConfig ?: return
        val socket = sipSocket ?: return
        val targetHost = config.outboundProxy ?: config.domain
        val addr = InetAddress.getByName(targetHost)
        val bytes = msg.toByteArray()
        val packet = DatagramPacket(bytes, bytes.size, addr, config.port)
        socket.send(packet)
    }

    private fun stopRtp() {
        jitterBufferPumpJob?.cancel()
        rtpReceiveJob?.cancel()
        jitterBuffer.clear()
        remoteRtpAddress = null
        remoteRtpPort = 0
    }

    fun closeSockets() {
        stopRtp()
        receiveJob?.cancel()
        sipSocket?.close()
        sipSocket = null
        rtpSocket?.close()
        rtpSocket = null
    }

    private fun getLocalIp(): String {
        return try {
            InetAddress.getLocalHost().hostAddress ?: "127.0.0.1"
        } catch (e: Exception) {
            "127.0.0.1"
        }
    }
}
