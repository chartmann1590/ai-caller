package com.charles.localcallagent.core.privacy

import com.charles.localcallagent.core.model.StructuredCallResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface TranscriptStore {
    suspend fun saveCallResult(result: StructuredCallResult, transcript: String?)
    suspend fun loadCallResults(): List<StructuredCallResult>
    suspend fun loadTranscript(callTimestampEpochMs: Long): String?
    suspend fun deleteAll()
    suspend fun deleteOlderThan(cutoffEpochMs: Long)
}

/**
 * Local encrypted store using AES-GCM 256-bit encryption.
 * Keeps data strictly private on device, excluded from cloud backups.
 */
class LocalEncryptedTranscriptStore(
    private val storageDir: File,
    private val key: SecretKey = generateAesKey()
) : TranscriptStore {

    private val json = Json { ignoreUnknownKeys = true }

    init {
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
    }

    override suspend fun saveCallResult(result: StructuredCallResult, transcript: String?) {
        val resultFile = File(storageDir, "${result.timestampEpochMs}.result.enc")
        val serializedResult = json.encodeToString(result)
        val encryptedResult = encrypt(serializedResult.toByteArray(Charsets.UTF_8))
        resultFile.writeBytes(encryptedResult)

        if (!transcript.isNullOrBlank()) {
            val transcriptFile = File(storageDir, "${result.timestampEpochMs}.transcript.enc")
            val encryptedTranscript = encrypt(transcript.toByteArray(Charsets.UTF_8))
            transcriptFile.writeBytes(encryptedTranscript)
        }
    }

    override suspend fun loadCallResults(): List<StructuredCallResult> {
        val resultFiles = storageDir.listFiles { _, name -> name.endsWith(".result.enc") } ?: emptyArray()
        val results = mutableListOf<StructuredCallResult>()
        for (file in resultFiles) {
            try {
                val decrypted = decrypt(file.readBytes())
                val result = json.decodeFromString<StructuredCallResult>(String(decrypted, Charsets.UTF_8))
                results.add(result)
            } catch (e: Exception) {
                // Ignore corrupted or unreadable entries safely
            }
        }
        return results.sortedByDescending { it.timestampEpochMs }
    }

    override suspend fun loadTranscript(callTimestampEpochMs: Long): String? {
        val file = File(storageDir, "$callTimestampEpochMs.transcript.enc")
        if (!file.exists()) return null
        return try {
            val decrypted = decrypt(file.readBytes())
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun deleteAll() {
        storageDir.listFiles()?.forEach { it.delete() }
    }

    override suspend fun deleteOlderThan(cutoffEpochMs: Long) {
        storageDir.listFiles()?.forEach { file ->
            val timestamp = file.name.substringBefore(".").toLongOrNull()
            if (timestamp != null && timestamp < cutoffEpochMs) {
                file.delete()
            }
        }
    }

    private fun encrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12).apply { SecureRandom().nextBytes(this) }
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)
        val encrypted = cipher.doFinal(data)
        // Prepend IV (12 bytes)
        return iv + encrypted
    }

    private fun decrypt(data: ByteArray): ByteArray {
        require(data.size > 12) { "Corrupted encrypted data" }
        val iv = data.copyOfRange(0, 12)
        val encrypted = data.copyOfRange(12, data.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        return cipher.doFinal(encrypted)
    }

    companion object {
        fun generateAesKey(): SecretKey {
            val keyGen = KeyGenerator.getInstance("AES")
            keyGen.init(256)
            return keyGen.generateKey()
        }
    }
}
