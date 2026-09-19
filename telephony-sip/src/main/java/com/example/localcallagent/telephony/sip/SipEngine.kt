package com.example.localcallagent.telephony.sip

import android.util.Log
import com.example.localcallagent.core.model.AppCallState
import com.example.localcallagent.core.model.PcmFrame
import com.example.localcallagent.core.model.SipAccountConfig
import com.example.localcallagent.telephony.api.RegistrationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
    private var dialedUser: String? = null
    private var pendingInviteSdp: String = ""
    private var authAttempts: Int = 0
    private var remoteRtpAddress: InetAddress? = null
    private var remoteRtpPort: Int = 0

    private var rtpSeqNumber = Random().nextInt(10000)
    private var rtpTimestamp = Random().nextLong() and 0xFFFFFF
    private val rtpSsrc = (Random().nextLong() and 0x7FFFFFFF) + 1000

    private var receiveJob: Job? = null
    private var rtpReceiveJob: Job? = null
    private var jitterBufferPumpJob: Job? = null
    // Dedicated sender scope so blocking UDP sends never stall the socket
    // reader coroutine (head-of-line blocking).
    private val sendScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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
                    if (msg != null) handleSipMessage(msg)
                } catch (e: Exception) {
                    if (!socket.isClosed) { }
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
                    if (rtp != null) jitterBuffer.push(rtp)
                } catch (e: Exception) { }
            }
        }
        jitterBufferPumpJob = scope.launch {
            while (isActive) {
                val frame = jitterBuffer.popPcmFrame()
                if (frame != null) _incomingPcm.emit(frame)
                delay(20)
            }
        }
    }

    private suspend fun handleSipMessage(msg: SipMessage) {
        if (!msg.isRequest) {
            when (msg.statusCode) {
                100 -> _callState.value = AppCallState.Dialing
                180, 183 -> {
                    _callState.value = AppCallState.Ringing
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
                        Log.i(TAG, "REGISTERED")
                    } else if (cseqHeader.contains("INVITE")) {
                        _callState.value = AppCallState.Active
                        Log.i(TAG, "CALL_ACTIVE")
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
                401, 407 -> handleAuthChallenge(msg)
                486 -> { _callState.value = AppCallState.Disconnected("Busy Here (486)"); stopRtp() }
                603 -> { _callState.value = AppCallState.Disconnected("Decline (603)"); stopRtp() }
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
            when (msg.method) {
                "BYE" -> {
                    sendResponse(msg, 200, "OK")
                    _callState.value = AppCallState.Disconnected("Remote party hung up")
                    stopRtp()
                }
                "OPTIONS" -> sendResponse(msg, 200, "OK")
            }
        }
    }

    suspend fun register() {
        val config = activeConfig ?: return
        _registrationState.value = RegistrationState.REGISTERING
        currentCallId = UUID.randomUUID().toString() + "@" + getLocalIp()
        fromTag = UUID.randomUUID().toString().substring(0, 8)
        cseq = 1; authAttempts = 0; dialedUser = null; pendingInviteSdp = ""
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
        sendSip(SipMessage(isRequest = true, method = "REGISTER", requestUri = "sip:${config.domain}", headers = headers))
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
        sendSip(SipMessage(isRequest = true, method = "REGISTER", requestUri = "sip:${config.domain}", headers = headers))
    }

    suspend fun dial(destination: String) {
        val config = activeConfig ?: throw IllegalStateException("SIP account not configured")
        _callState.value = AppCallState.Dialing
        currentCallId = UUID.randomUUID().toString() + "@" + getLocalIp()
        fromTag = UUID.randomUUID().toString().substring(0, 8)
        toTag = null; cseq = 1; dialedUser = destination; authAttempts = 0
        val localPort = rtpSocket?.localPort ?: 10000
        val sdp = SipMessage.buildSdpOffer(getLocalIp(), localPort)
        pendingInviteSdp = sdp
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
        sendSip(SipMessage(isRequest = true, method = "INVITE", requestUri = "sip:$destination@${config.domain}", headers = headers, body = sdp))
        Log.i(TAG, "INVITE_SENT dest=$destination")
    }

    suspend fun hangup() {
        val config = activeConfig ?: return
        val callId = currentCallId ?: return
        val headers = mutableMapOf(
            "Via" to "SIP/2.0/UDP ${getLocalIp()}:${sipSocket?.localPort ?: 5060};branch=z9hG4bK${UUID.randomUUID().toString().substring(0, 8)};rport",
            "Max-Forwards" to "70",
            "From" to "<sip:${config.username}@${config.domain}>;tag=$fromTag",
            "To" to "<sip:${dialedUser ?: config.username}@${config.domain}>" + (if (toTag != null) ";tag=$toTag" else ""),
            "Call-ID" to callId,
            "CSeq" to "${++cseq} BYE",
            "Content-Length" to "0"
        )
        val byeUri = if (dialedUser != null) "sip:${dialedUser}@${config.domain}" else "sip:${config.domain}"
        sendSip(SipMessage(isRequest = true, method = "BYE", requestUri = byeUri, headers = headers))
        _callState.value = AppCallState.Disconnected("User hung up")
        stopRtp()
    }

    suspend fun sendDtmf(digit: Char) {
        val addr = remoteRtpAddress ?: return
        val port = remoteRtpPort
        val socket = rtpSocket ?: return
        for (i in 1..3) {
            val isEnd = (i == 3)
            val duration = i * 200
            val payload = Rfc2833Dtmf.createPayload(digit, endOfEvent = isEnd, durationSamples = duration)
            val packet = RtpPacket(payloadType = 101, sequenceNumber = rtpSeqNumber++, timestamp = rtpTimestamp, ssrc = rtpSsrc, marker = (i == 1), payload = payload)
            val bytes = packet.toBytes()
            socket.send(DatagramPacket(bytes, bytes.size, addr, port))
            delay(20)
        }
        rtpTimestamp += 800
    }

    suspend fun sendAudioFrame(frame: PcmFrame) {
        val addr = remoteRtpAddress ?: return
        val port = remoteRtpPort
        val socket = rtpSocket ?: return
        val samples8k = if (frame.sampleRateHz == 16000) downsample16kTo8k(frame.samples) else frame.samples
        val ulawBytes = G711Codec.linearToUlaw(samples8k)
        val packet = RtpPacket(payloadType = 0, sequenceNumber = rtpSeqNumber++, timestamp = rtpTimestamp, ssrc = rtpSsrc, payload = ulawBytes)
        rtpTimestamp += samples8k.size
        val bytes = packet.toBytes()
        socket.send(DatagramPacket(bytes, bytes.size, addr, port))
    }

    private fun downsample16kTo8k(in16k: ShortArray): ShortArray {
        val out8k = ShortArray(in16k.size / 2)
        for (i in out8k.indices) out8k[i] = in16k[i * 2]
        return out8k
    }

    private suspend fun sendAck() {
        val config = activeConfig ?: return
        val headers = mutableMapOf(
            "Via" to "SIP/2.0/UDP ${getLocalIp()}:${sipSocket?.localPort ?: 5060};branch=z9hG4bK${UUID.randomUUID().toString().substring(0, 8)};rport",
            "Max-Forwards" to "70",
            "From" to "<sip:${config.username}@${config.domain}>;tag=$fromTag",
            "To" to "<sip:${dialedUser ?: config.username}@${config.domain}>" + (if (toTag != null) ";tag=$toTag" else ""),
            "Call-ID" to currentCallId!!,
            "CSeq" to "$cseq ACK",
            "Content-Length" to "0"
        )
        val ackUri = if (dialedUser != null) "sip:${dialedUser}@${config.domain}" else "sip:${config.domain}"
        sendSipAsync(SipMessage(isRequest = true, method = "ACK", requestUri = ackUri, headers = headers))
    }

    private suspend fun sendResponse(req: SipMessage, code: Int, reason: String) {
        val headers = mutableMapOf<String, String>()
        req.getHeader("Via")?.let { headers["Via"] = it }
        req.getHeader("From")?.let { headers["From"] = it }
        req.getHeader("To")?.let { headers["To"] = it }
        req.getHeader("Call-ID")?.let { headers["Call-ID"] = it }
        req.getHeader("CSeq")?.let { headers["CSeq"] = it }
        headers["Content-Length"] = "0"
        sendSipAsync(SipMessage(isRequest = false, statusCode = code, reasonPhrase = reason, headers = headers))
    }

    private suspend fun handleAuthChallenge(msg: SipMessage) {
        val config = activeConfig ?: return
        if (authAttempts >= 3) {
            val cseqHeader = msg.getHeader("CSeq") ?: ""
            if (cseqHeader.contains("REGISTER")) _registrationState.value = RegistrationState.FAILED
            else _callState.value = AppCallState.Disconnected("Authentication failed")
            return
        }
        authAttempts++
        val isProxy = msg.getHeader("Proxy-Authenticate") != null
        val authHeader = msg.getHeader("WWW-Authenticate") ?: msg.getHeader("Proxy-Authenticate") ?: return
        val realm = extractParameter(authHeader, "realm") ?: config.domain
        val nonce = extractParameter(authHeader, "nonce") ?: return
        val opaque = extractParameter(authHeader, "opaque")
        val qop = extractParameter(authHeader, "qop")
        val cseqHeader = msg.getHeader("CSeq") ?: ""
        val method = if (cseqHeader.contains("REGISTER")) "REGISTER" else "INVITE"
        val localIp = getLocalIp()
        val localSipPort = sipSocket?.localPort ?: 5060
        val requestUri = if (method == "INVITE" && dialedUser != null) "sip:${dialedUser}@${config.domain}" else "sip:${config.domain}"
        val body = if (method == "INVITE") pendingInviteSdp else ""
        val authVal = SipMessage.computeDigestAuth(username = config.authUsername, realm = realm, password = config.password, method = method, uri = requestUri, nonce = nonce, opaque = opaque)
        val headers = mutableMapOf(
            "Via" to "SIP/2.0/UDP $localIp:$localSipPort;branch=z9hG4bK${UUID.randomUUID().toString().substring(0, 8)};rport",
            "Max-Forwards" to "70",
            "From" to (if (method == "INVITE") "\"${config.displayName ?: config.username}\" <sip:${config.username}@${config.domain}>;tag=$fromTag" else "<sip:${config.username}@${config.domain}>;tag=$fromTag"),
            "To" to (if (method == "INVITE" && dialedUser != null) "<sip:${dialedUser}@${config.domain}>" else "<sip:${config.username}@${config.domain}>"),
            "Call-ID" to (currentCallId ?: return),
            "CSeq" to "${++cseq} $method",
            "Contact" to "<sip:${config.username}@$localIp:$localSipPort>",
            "Content-Length" to body.length.toString()
        )
        if (method == "REGISTER") headers["Expires"] = config.registrationIntervalSeconds.toString()
        if (method == "INVITE" && body.isNotEmpty()) headers["Content-Type"] = "application/sdp"
        if (isProxy) headers["Proxy-Authorization"] = authVal else headers["Authorization"] = authVal
        if (!qop.isNullOrBlank()) { }
        sendSipAsync(SipMessage(isRequest = true, method = method, requestUri = requestUri, headers = headers, body = body))
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
        val bytes = msg.toByteArray()
        // Resolve + send off the reader thread.
        withContext(Dispatchers.IO) {
            val addr = InetAddress.getByName(targetHost)
            socket.send(DatagramPacket(bytes, bytes.size, addr, config.port))
        }
    }

    private fun sendSipAsync(msg: SipMessage) {
        sendScope.launch {
            try { sendSip(msg) } catch (_: Exception) { }
        }
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
        sipSocket?.close(); sipSocket = null
        rtpSocket?.close(); rtpSocket = null
    }

    private fun getLocalIp(): String {
        try {
            DatagramSocket().use { socket ->
                socket.connect(InetAddress.getByName("8.8.8.8"), 53)
                val addr = socket.localAddress?.hostAddress
                if (!addr.isNullOrBlank() && addr != "0.0.0.0" && !addr.startsWith("127.")) return addr
            }
        } catch (_: Exception) { }
        try {
            val interfaces = java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                for (addr in java.util.Collections.list(intf.inetAddresses)) {
                    if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) return addr.hostAddress ?: continue
                }
            }
        } catch (_: Exception) { }
        return try { InetAddress.getLocalHost().hostAddress ?: "127.0.0.1" } catch (_: Exception) { "127.0.0.1" }
    }

    companion object {
        private const val TAG = "SipEngine"
    }
}
