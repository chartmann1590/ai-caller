package com.charles.localcallagent.audio.core

import com.charles.localcallagent.core.model.PcmFrame
import java.util.ArrayDeque

class BoundedAudioQueue(
    val maxCapacity: Int = 50 // ~1 second of 20ms frames
) {
    private val queue = ArrayDeque<PcmFrame>(maxCapacity)
    var droppedFramesCount: Long = 0L
        private set

    @Synchronized
    fun enqueue(frame: PcmFrame): Boolean {
        var dropped = false
        if (queue.size >= maxCapacity) {
            queue.pollFirst() // drop oldest frame to bound latency
            droppedFramesCount++
            dropped = true
        }
        queue.addLast(frame)
        return !dropped
    }

    @Synchronized
    fun dequeue(): PcmFrame? {
        return queue.pollFirst()
    }

    @Synchronized
    fun clear() {
        queue.clear()
    }

    val size: Int
        @Synchronized get() = queue.size

    val estimatedLatencyMs: Float
        @Synchronized get() = queue.sumOf { it.durationMs.toDouble() }.toFloat()
}
