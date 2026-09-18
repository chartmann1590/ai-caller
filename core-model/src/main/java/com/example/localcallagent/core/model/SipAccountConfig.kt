package com.example.localcallagent.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class SipTransportType {
    UDP,
    TCP,
    TLS
}

@Serializable
enum class DtmfMode {
    RFC2833,
    SIP_INFO,
    INBAND
}

@Serializable
data class SipAccountConfig(
    val id: String = UUID.randomUUID().toString(),
    val accountName: String = "Default SIP",
    val username: String,
    val authUsername: String = username,
    val password: String,
    val domain: String,
    val port: Int = 5060,
    val transport: SipTransportType = SipTransportType.UDP,
    val outboundProxy: String? = null,
    val displayName: String? = null,
    val stunServer: String? = null,
    val turnServer: String? = null,
    val codecs: List<String> = listOf("PCMU", "PCMA", "OPUS"),
    val registrationIntervalSeconds: Int = 3600,
    val natKeepAliveIntervalSeconds: Int = 30,
    val dtmfMode: DtmfMode = DtmfMode.RFC2833,
    val isEnabled: Boolean = true
)
