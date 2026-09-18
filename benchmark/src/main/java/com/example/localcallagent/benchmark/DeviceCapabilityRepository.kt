package com.example.localcallagent.benchmark

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.telecom.TelecomManager
import com.example.localcallagent.core.model.DeviceCapabilities
import com.example.localcallagent.core.model.SupportLevel
import java.io.File

class DeviceCapabilityRepository(
    private val context: Context
) {

    fun probeCapabilities(): DeviceCapabilities {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val totalRamMb = (memInfo.totalMem / (1024 * 1024))

        val stat = StatFs(Environment.getDataDirectory().path)
        val availableStorageMb = (stat.availableBlocksLong * stat.blockSizeLong) / (1024 * 1024)

        val arm64 = Build.SUPPORTED_ABIS.any { it.contains("arm64") || it.contains("x86_64") }
        val telecom = context.getSystemService(TelecomManager::class.java)
        val hasTelephony = telecom != null

        // Evaluate support level
        val warnings = mutableListOf<String>()
        val level = when {
            totalRamMb >= 11500 && availableStorageMb >= 8000 && arm64 -> {
                SupportLevel.EXCELLENT
            }
            totalRamMb >= 7500 && availableStorageMb >= 5000 && arm64 -> {
                SupportLevel.SUPPORTED
            }
            totalRamMb >= 5500 && availableStorageMb >= 3000 -> {
                warnings.add("RAM is below 8GB recommended; model inference latency may be elevated.")
                SupportLevel.LIMITED
            }
            else -> {
                if (!arm64) warnings.add("No 64-bit ABI (arm64/x86_64) detected.")
                if (totalRamMb < 5500) warnings.add("Insufficient RAM for on-device Gemma 4.")
                if (availableStorageMb < 3000) warnings.add("Insufficient free storage for AI models.")
                SupportLevel.UNSUPPORTED
            }
        }

        return DeviceCapabilities(
            telephonyCalling = hasTelephony,
            roleDialerAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
            roleDialerHeld = false,
            onDeviceSpeechRecognizerAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            aecAvailable = true,
            totalRamMb = totalRamMb,
            availableStorageMb = availableStorageMb,
            arm64 = arm64,
            gpuAvailable = true,
            modelSupported = level != SupportLevel.UNSUPPORTED,
            autonomousCarrierMedia = false, // MUST ALWAYS BE FALSE
            supportLevel = level,
            warnings = warnings
        )
    }
}
