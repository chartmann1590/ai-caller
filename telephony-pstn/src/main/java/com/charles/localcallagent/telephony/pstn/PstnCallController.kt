package com.charles.localcallagent.telephony.pstn

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager

class PstnCallController(
    private val context: Context
) {
    /**
     * Initiates a normal carrier phone call via TelecomManager.
     * Note: This starts a SIM/cellular call.
     */
    @SuppressLint("MissingPermission")
    fun placeCall(destination: String) {
        val trimmed = destination.trim()
        require(trimmed.isNotEmpty()) { "Destination number cannot be blank" }

        val telecom = context.getSystemService(TelecomManager::class.java)
            ?: throw IllegalStateException("TelecomManager service is not available")

        val uri = Uri.fromParts("tel", trimmed, null)
        val extras = Bundle().apply {
            putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, false)
        }

        telecom.placeCall(uri, extras)
    }
}
