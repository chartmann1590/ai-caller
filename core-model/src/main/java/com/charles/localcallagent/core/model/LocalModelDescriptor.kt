package com.charles.localcallagent.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ModelType {
    LLM,
    ASR,
    TTS,
    VAD
}

@Serializable
enum class ModelStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    VERIFYING,
    READY,
    CORRUPTED,
    ERROR
}

@Serializable
data class LocalModelDescriptor(
    val id: String,
    val name: String,
    val version: String,
    val type: ModelType,
    val downloadUrl: String,
    val localPath: String,
    val sizeBytes: Long,
    val sha256: String,
    val minimumRamMb: Int,
    val status: ModelStatus = ModelStatus.NOT_DOWNLOADED,
    val downloadProgress: Float = 0f,
    val errorMessage: String? = null
)
