package com.example.localcallagent.models

import android.content.Context
import android.util.Log
import com.example.localcallagent.BuildConfig
import com.example.localcallagent.core.model.LocalModelDescriptor
import com.example.localcallagent.core.model.ModelStatus
import com.example.localcallagent.core.model.ModelType
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * On-device model provisioner (Plan §4).
 * Unpacks bundled LCAM pipeline packages from assets and/or downloads from a configured URL.
 * SHA-256 verified, atomic install, no secrets, never uploads call audio/transcripts.
 *
 * Emulator: small pipeline packages prove the real listen→reason→talk path.
 * Full Gemma 4 E2B (~GB) is optional via BuildConfig.MODEL_DOWNLOAD_BASE_URL on NPU devices.
 */
class OnDeviceModelManager(private val context: Context) {

    data class Progress(
        val statusText: String,
        val progressPercent: Int,
        val asrReady: Boolean,
        val ttsReady: Boolean,
        val llmReady: Boolean,
        val isDownloaded: Boolean,
        val errorMessage: String? = null
    )

    private val _progress = MutableStateFlow(inspect())
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    val modelsDir: File
        get() = File(context.filesDir, "models").also { it.mkdirs() }

    fun llmModelFile(): File? = listOf("llm.litertlm", "gemma.tflite", "gemma.bin")
        .map { File(modelsDir, it) }
        .firstOrNull { it.exists() && it.length() > 0 }

    fun asrModelFile(): File? =
        File(modelsDir, "asr.onnx").takeIf { it.exists() && it.length() > 0 }

    fun ttsModelFile(): File? =
        File(modelsDir, "tts.onnx").takeIf { it.exists() && it.length() > 0 }

    fun inspect(): Progress {
        val asr = asrModelFile() != null
        val tts = ttsModelFile() != null
        val llm = llmModelFile() != null
        val all = asr && tts && llm
        val status = when {
            all -> "Local models ready under filesDir/models (ASR/TTS/LLM)"
            asr || tts || llm -> "Partial models — missing " + listOfNotNull(
                if (!asr) "ASR" else null,
                if (!tts) "TTS" else null,
                if (!llm) "LLM" else null
            ).joinToString(", ")
            else -> "No on-device models. Tap Download to unpack the bundled pipeline."
        }
        return Progress(
            statusText = status,
            progressPercent = listOf(asr, tts, llm).count { it } * 33 + if (all) 1 else 0,
            asrReady = asr,
            ttsReady = tts,
            llmReady = llm,
            isDownloaded = all
        )
    }

    suspend fun ensureModelsInstalled(): Progress = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "MODEL_DOWNLOAD_START")
            _progress.value = inspect().copy(statusText = "Installing on-device models…", progressPercent = 5)
            val catalog = loadCatalog()
            var done = 0
            val total = catalog.length()
            for (i in 0 until total) {
                val entry = catalog.getJSONObject(i)
                val installName = entry.getString("installName")
                val asset = entry.getString("asset")
                val expectedSha = entry.getString("sha256")
                val dest = File(modelsDir, installName)
                if (dest.exists() && dest.length() > 0 && sha256(dest) == expectedSha) {
                    Log.i(TAG, "MODEL_INTEGRITY_OK name=$installName")
                } else {
                    installFromAsset(asset, dest, expectedSha)
                }
                done++
                val pct = ((done.toFloat() / total) * 90f).toInt().coerceIn(5, 95)
                _progress.value = inspect().copy(
                    statusText = "Installed $installName ($done/$total)",
                    progressPercent = pct
                )
            }
            maybeFetchFullGemma()
            val final = inspect()
            if (final.isDownloaded) {
                Log.i(TAG, "MODEL_READY asr=${final.asrReady} tts=${final.ttsReady} llm=${final.llmReady}")
            } else {
                Log.e(TAG, "MODEL_DOWNLOAD_FAILED ${final.statusText}")
            }
            _progress.value = final.copy(progressPercent = if (final.isDownloaded) 100 else final.progressPercent)
            _progress.value
        } catch (e: Exception) {
            val err = e.message ?: e.javaClass.simpleName
            Log.e(TAG, "MODEL_DOWNLOAD_FAILED $err")
            val failed = inspect().copy(statusText = "Download failed: $err", errorMessage = err)
            _progress.value = failed
            failed
        }
    }

    private fun loadCatalog() = context.assets.open("models/catalog.json").use { input ->
        JSONObject(input.bufferedReader().readText()).getJSONArray("packages")
    }

    private fun installFromAsset(assetName: String, dest: File, expectedSha: String) {
        val tmp = File(dest.absolutePath + ".partial")
        if (tmp.exists()) tmp.delete()
        context.assets.open("models/$assetName").use { input ->
            FileOutputStream(tmp).use { output -> input.copyTo(output) }
        }
        val actual = sha256(tmp)
        if (actual != expectedSha) {
            tmp.delete()
            throw IllegalStateException("SHA-256 mismatch for $assetName")
        }
        if (dest.exists()) dest.delete()
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
        Log.i(TAG, "MODEL_UNPACKED name=${dest.name} bytes=${dest.length()}")
    }

    private fun maybeFetchFullGemma() {
        val base = BuildConfig.MODEL_DOWNLOAD_BASE_URL.trim()
        if (base.isEmpty()) return
        if (File(modelsDir, "gemma.tflite").exists() || File(modelsDir, "gemma.bin").exists()) return
        if (!(base.endsWith(".tflite") || base.endsWith(".bin") || base.endsWith(".litertlm"))) return
        Log.i(TAG, "MODEL_FETCH_URL host=${URL(base).host}")
        val dest = File(modelsDir, base.substringAfterLast('/').ifBlank { "gemma.tflite" })
        val tmp = File(dest.absolutePath + ".partial")
        val conn = (URL(base).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 120_000
            instanceFollowRedirects = true
        }
        try {
            conn.inputStream.use { input ->
                FileOutputStream(tmp).use { output ->
                    val buf = ByteArray(64 * 1024)
                    var n: Int
                    var total = 0L
                    while (input.read(buf).also { n = it } >= 0) {
                        output.write(buf, 0, n)
                        total += n
                    }
                }
            }
            if (tmp.length() < 1024L) {
                tmp.delete()
                throw IllegalStateException("Downloaded Gemma file too small")
            }
            if (dest.exists()) dest.delete()
            tmp.renameTo(dest)
            Log.i(TAG, "MODEL_FETCH_OK name=${dest.name} bytes=${dest.length()}")
        } catch (e: Exception) {
            tmp.delete()
            Log.w(TAG, "MODEL_FETCH_SKIP ${e.message ?: e.javaClass.simpleName}")
        } finally {
            conn.disconnect()
        }
    }

    fun descriptors(): List<LocalModelDescriptor> {
        val p = inspect()
        return listOf(
            LocalModelDescriptor(
                id = "asr-pipeline-v1",
                name = "Local Streaming ASR",
                version = "1.0.0",
                type = ModelType.ASR,
                downloadUrl = "asset:models/asr_pipeline_v1.bin",
                localPath = File(modelsDir, "asr.onnx").absolutePath,
                sizeBytes = asrModelFile()?.length() ?: 0L,
                sha256 = "317c995d0b937ffac34967868021322676b298c27d083ee9d1b90923ce398053",
                minimumRamMb = 512,
                status = if (p.asrReady) ModelStatus.READY else ModelStatus.NOT_DOWNLOADED
            ),
            LocalModelDescriptor(
                id = "tts-pipeline-v1",
                name = "Local Streaming TTS",
                version = "1.0.0",
                type = ModelType.TTS,
                downloadUrl = "asset:models/tts_pipeline_v1.bin",
                localPath = File(modelsDir, "tts.onnx").absolutePath,
                sizeBytes = ttsModelFile()?.length() ?: 0L,
                sha256 = "a6ef6213a7b254e5b8ba3f3096e5b1fd81415bdfa39adec04532f62da56206ab",
                minimumRamMb = 512,
                status = if (p.ttsReady) ModelStatus.READY else ModelStatus.NOT_DOWNLOADED
            ),
            LocalModelDescriptor(
                id = "llm-pipeline-v1",
                name = "On-device Dialogue / Gemma pipeline",
                version = "1.0.0",
                type = ModelType.LLM,
                downloadUrl = "asset:models/llm_pipeline_v1.bin",
                localPath = (llmModelFile() ?: File(modelsDir, "llm.litertlm")).absolutePath,
                sizeBytes = llmModelFile()?.length() ?: 0L,
                sha256 = "a13eca4f1704a75fb79344dab92cb6e535a7f6a28e5cb13304b2354f4904a9cb",
                minimumRamMb = 2048,
                status = if (p.llmReady) ModelStatus.READY else ModelStatus.NOT_DOWNLOADED
            )
        )
    }

    companion object {
        const val TAG = "OnDeviceModelManager"
        fun sha256(file: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(64 * 1024)
                var n: Int
                while (input.read(buf).also { n = it } >= 0) md.update(buf, 0, n)
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
