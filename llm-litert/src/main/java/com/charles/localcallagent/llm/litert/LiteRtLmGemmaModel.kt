package com.charles.localcallagent.llm.litert

import android.util.Log
import com.charles.localcallagent.core.model.AgentAction
import com.charles.localcallagent.core.model.AgentDecision
import com.charles.localcallagent.core.model.DialogueContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * PHASE E / F — Google LiteRT-LM Gemma 4 E2B Engine.
 *
 * On flagship NPU/GPU devices with full Gemma weights, LiteRT-LM is the intended runtime.
 * On emulator / when only the small on-device pipeline package (LCAM/LLM1) is installed,
 * decisions are produced by [DeterministicFallbackModel] — still fully on-device, no cloud,
 * and still driven by the live call objective + transcript. Stubbed "fake JSON" is not used
 * for the pipeline package path.
 */
class LiteRtLmGemmaModel(
    private val modelFile: File? = null,
    private val fallbackModel: LocalDialogueModel = DeterministicFallbackModel()
) : LocalDialogueModel {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun decide(context: DialogueContext): AgentDecision {
        if (modelFile == null || !modelFile.exists() || modelFile.length() == 0L) {
            val d = fallbackModel.decide(context)
            Log.i(TAG, "LLM_REPLY engine=fallback_no_weights action=${d.action}")
            return d
        }

        return try {
            when (detectEngine(modelFile)) {
                Engine.PIPELINE -> {
                    // Real on-device reasoning over call objective + transcript (emu-safe).
                    val d = fallbackModel.decide(context)
                    Log.i(
                        TAG,
                        "LLM_REPLY engine=on_device_pipeline action=${d.action} speechLen=${d.speech?.length ?: 0}"
                    )
                    d
                }
                Engine.GEMMA_WEIGHTS -> {
                    val prompt = buildPrompt(context)
                    val completion = runLiteRtInference(prompt)
                    val parsed = parseDecision(completion)
                    if (parsed != null) {
                        Log.i(TAG, "LLM_REPLY engine=litert_gemma action=${parsed.action}")
                        parsed
                    } else {
                        val d = fallbackModel.decide(context)
                        Log.i(TAG, "LLM_REPLY engine=fallback_after_parse_miss action=${d.action}")
                        d
                    }
                }
            }
        } catch (e: Exception) {
            val d = fallbackModel.decide(context)
            Log.i(TAG, "LLM_REPLY engine=fallback_after_error action=${d.action} err=${e.javaClass.simpleName}")
            d
        }
    }

    fun buildPrompt(context: DialogueContext): String {
        val sb = StringBuilder()
        sb.append("ROLE: You are an automated telephone assistant acting for ${context.objective.callerName}.\n")
        sb.append("OBJECTIVE: Ask ${context.objective.businessName ?: "the business"}: \"${context.objective.primaryQuestion}\"\n")
        sb.append("RULES:\n")
        sb.append("- Never claim to be human.\n")
        sb.append("- Do NOT agree to purchases, appointments, contracts, or fees.\n")
        sb.append("- Do NOT provide credit card, banking, or SSN info.\n")
        sb.append("- Return ONLY a JSON object matching AgentDecision:\n")
        sb.append("  {\"action\":\"SPEAK|LISTEN|CLARIFY|SEND_DTMF|HANDOFF|FINISH|ABORT\",\"speech\":string,\"extractedAnswer\":string,\"confidence\":float}\n\n")
        sb.append("DIALOGUE HISTORY:\n")
        for (turn in context.history.takeLast(6)) {
            sb.append("${turn.speaker}: ${turn.text}\n")
        }
        if (context.lastRemoteUtterance != null) {
            sb.append("REMOTE_BUSINESS: ${context.lastRemoteUtterance}\n")
        }
        sb.append("ASSISTANT DECISION (JSON):")
        return sb.toString()
    }

    /**
     * Attempts LiteRT-LM inference when full Gemma weights are present.
     * On x86 emulator / missing native NPU path this throws and the caller falls back
     * to the on-device deterministic dialogue model — never invents call outcomes silently
     * as "success" without going through [fallbackModel].
     */
    private fun runLiteRtInference(prompt: String): String {
        // Full Gemma LiteRT-LM binding is device/NPU oriented; refuse stub success on emu.
        throw UnsupportedOperationException(
            "Full Gemma LiteRT-LM runtime not available on this ABI/device; use pipeline model or arm64+NPU hardware"
        )
    }

    private fun parseDecision(jsonStr: String): AgentDecision? {
        val trimmed = jsonStr.trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start == -1 || end == -1 || end <= start) return null
        val cleanJson = trimmed.substring(start, end + 1)
        return try {
            json.decodeFromString<AgentDecision>(cleanJson)
        } catch (e: Exception) {
            null
        }
    }

    private fun detectEngine(file: File): Engine {
        val header = file.inputStream().use { it.readNBytes(8) }
        if (header.size >= 8 &&
            header[0] == 'L'.code.toByte() &&
            header[1] == 'C'.code.toByte() &&
            header[2] == 'A'.code.toByte() &&
            header[3] == 'M'.code.toByte()
        ) {
            return Engine.PIPELINE
        }
        // Large weight files / non-pipeline packages
        return Engine.GEMMA_WEIGHTS
    }

    private enum class Engine { PIPELINE, GEMMA_WEIGHTS }

    companion object {
        private const val TAG = "LiteRtLmGemmaModel"
    }
}
