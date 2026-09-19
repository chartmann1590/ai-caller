package com.charles.localcallagent.audio.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CallAudioMode {
    BOT_CALLING,
    HUMAN_TAKEOVER,
    MUTED
}

class AudioRouter {
    private val _audioMode = MutableStateFlow(CallAudioMode.BOT_CALLING)
    val audioMode: StateFlow<CallAudioMode> = _audioMode.asStateFlow()

    private var isMuted = false

    fun takeOver() {
        _audioMode.value = CallAudioMode.HUMAN_TAKEOVER
    }

    fun resumeBot() {
        _audioMode.value = CallAudioMode.BOT_CALLING
    }

    fun toggleMute(): Boolean {
        isMuted = !isMuted
        _audioMode.value = if (isMuted) CallAudioMode.MUTED else CallAudioMode.BOT_CALLING
        return isMuted
    }

    fun reset() {
        isMuted = false
        _audioMode.value = CallAudioMode.BOT_CALLING
    }
}
