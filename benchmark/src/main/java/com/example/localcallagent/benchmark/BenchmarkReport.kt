package com.example.localcallagent.benchmark

import com.example.localcallagent.core.model.SupportLevel
import kotlinx.serialization.Serializable

@Serializable
data class BenchmarkReport(
    val coldLoadTimeMs: Long,
    val warmLoadTimeMs: Long,
    val timeToFirstTokenMs: Long,
    val decodeTokensPerSecond: Float,
    val asrRealTimeFactor: Float,
    val ttsFirstChunkMs: Long,
    val overallScore: Int,
    val supportLevel: SupportLevel,
    val thermalState: String = "NORMAL",
    val timestampEpochMs: Long = System.currentTimeMillis()
) {
    fun toCsv(): String {
        return "coldLoadMs,warmLoadMs,ttftMs,decodeToksPerSec,asrRtf,ttsFirstChunkMs,overallScore,supportLevel\n" +
                "$coldLoadTimeMs,$warmLoadTimeMs,$timeToFirstTokenMs,$decodeTokensPerSecond,$asrRealTimeFactor,$ttsFirstChunkMs,$overallScore,$supportLevel"
    }
}
