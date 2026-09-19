package com.charles.localcallagent.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class SupportLevel {
    EXCELLENT,
    SUPPORTED,
    LIMITED,
    UNSUPPORTED
}

@Serializable
data class DeviceCapabilities(
    val telephonyCalling: Boolean,
    val roleDialerAvailable: Boolean,
    val roleDialerHeld: Boolean,
    val onDeviceSpeechRecognizerAvailable: Boolean,
    val aecAvailable: Boolean,
    val totalRamMb: Long,
    val availableStorageMb: Long,
    val arm64: Boolean,
    val gpuAvailable: Boolean,
    val modelSupported: Boolean,
    /**
     * MUST ALWAYS BE FALSE for standard third-party Android builds.
     * Public Android APIs do not expose bidirectional cellular audio PCM.
     */
    val autonomousCarrierMedia: Boolean = false,
    val supportLevel: SupportLevel = SupportLevel.SUPPORTED,
    val warnings: List<String> = emptyList()
) {
    init {
        // Enforce safety invariant: third-party build can never pretend to support autonomous carrier media.
        require(!autonomousCarrierMedia) {
            "autonomousCarrierMedia must never be true on standard Android third-party builds"
        }
    }
}
