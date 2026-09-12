package com.carlink.audio

import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicReference

object AudioDiagnosticsState {
    val siri = MutableStateFlow("尚未发送 Siri 按键命令")
    val lastInputError = MutableStateFlow("")
    val input = MutableStateFlow("麦克风未启动")
    val output = MutableStateFlow("音频输出未启动")
    val meters = MutableStateFlow<List<PcmChannels.Meter>>(emptyList())
    val recording = MutableStateFlow(false)
    val microphoneBytesSent = MutableStateFlow(0L)
}

/** Production Siri/calls preempt the test; tests never steal an active production mic. */
object MicCaptureLease {
    private data class Lease(val owner: Any, val diagnostic: Boolean, val stop: () -> Unit)

    private val current = AtomicReference<Lease?>(null)

    fun acquire(
        owner: Any,
        diagnostic: Boolean,
        stop: () -> Unit,
    ): Boolean {
        while (true) {
            val previous = current.get()
            if (previous != null) {
                if (diagnostic || !previous.diagnostic) return false
                previous.stop()
                // A stopped diagnostic must release its lease before a production start.
                if (current.get() === previous) return false
                continue
            }
            if (current.compareAndSet(null, Lease(owner, diagnostic, stop))) return true
        }
    }

    fun release(owner: Any) {
        val previous = current.get()
        if (previous?.owner === owner) current.compareAndSet(previous, null)
    }
}
