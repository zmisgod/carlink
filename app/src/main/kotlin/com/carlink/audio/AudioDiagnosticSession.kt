package com.carlink.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.carlink.protocol.StreamPurpose
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.sin

/** User-started, bounded tests. No recording is stored or sent to the adapter. */
class AudioDiagnosticSession(context: Context) {
    private val context = context.applicationContext
    private val worker = Executors.newSingleThreadExecutor()
    private val generation = AtomicLong()
    val playing = MutableStateFlow(false)
    val testingMic = MutableStateFlow(false)
    val message = MutableStateFlow("选择设备后，可试听或查看麦克风波形")
    private var closed = false

    fun stop() {
        generation.incrementAndGet()
    }

    fun microphone() {
        if (closed) return
        stop()
        val token = generation.get()
        worker.execute {
            if (generation.get() != token) return@execute
            val mic = MicrophoneCaptureManager(context, { android.util.Log.d("AudioDiagnostic", it) }, diagnostic = true)
            try {
                testingMic.value = true
                message.value = "麦克风测试：请说话，最长 60 秒；不保存录音"
                if (!mic.start()) {
                    message.value = AudioDiagnosticsState.input.value
                    return@execute
                }
                val end = android.os.SystemClock.elapsedRealtime() + 60_000
                while (generation.get() == token && mic.isCapturing() && android.os.SystemClock.elapsedRealtime() < end) Thread.sleep(40)
            } catch (error: Exception) {
                message.value = "麦克风测试失败：${error.message}"
            } finally {
                mic.stop()
                if (message.value.startsWith("麦克风测试：")) message.value = "声波测试已结束，点击“显示声波”可重新测试"
                testingMic.value = false
            }
        }
    }

    fun play(purpose: StreamPurpose) {
        if (closed) return
        stop()
        val token = generation.get()
        worker.execute {
            if (generation.get() != token) return@execute
            var track: AudioTrack? = null
            val manager = context.getSystemService(AudioManager::class.java)
            val (usage, contentType) = AudioRoutePreferences.attributes(purpose)
            val attributes = AudioAttributes.Builder().setUsage(usage).setContentType(contentType).build()
            val focus =
                AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attributes).setOnAudioFocusChangeListener { change ->
                        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stop()
                    }.build()
            try {
                if (manager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    message.value = "系统未授予试听音频焦点，请结束通话或其他音频后重试"
                    return@execute
                }
                track =
                    AudioTrack.Builder().setAudioAttributes(attributes)
                        .setAudioFormat(
                            AudioFormat.Builder().setSampleRate(48000).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build(),
                        )
                        .setBufferSizeInBytes(maxOf(19200, AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)))
                        .setTransferMode(AudioTrack.MODE_STREAM).build()
                message.value = AudioRoutePreferences.applyOutput(context, track, purpose)
                AudioRoutePreferences.volume(context, track, 0.35f)
                playing.value = true
                track.play()
                val data = melody()
                var offset = 0
                while (offset < data.size && generation.get() == token) {
                    val count = track.write(data, offset, minOf(1920, data.size - offset), AudioTrack.WRITE_NON_BLOCKING)
                    if (count < 0) error("AudioTrack.write=$count")
                    offset += count
                    message.value = "${AudioRoutePreferences.purposeLabel(purpose)} 试听 ${if (offset < 48000 * 4) {
                        "左声道"
                    } else if (offset < 48000 * 8) {
                        "右声道"
                    } else {
                        "双声道"
                    }}；实际=${AudioRoutePreferences.label(track.routedDevice)}"
                    if (count == 0) Thread.sleep(5)
                }
                val deadline = android.os.SystemClock.elapsedRealtime() + 1000
                while (generation.get() == token && track.playbackHeadPosition < data.size / 2 && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(20)
            } catch (error: Exception) {
                message.value = "试听失败：${error.message}"
            } finally {
                val actualDevice = runCatching { track?.routedDevice }.getOrNull()
                runCatching { track?.stop() }
                track?.release()
                manager.abandonAudioFocusRequest(focus)
                if (playing.value && !message.value.startsWith("试听失败")) {
                    val result = if (generation.get() == token) "试听完成" else "试听已停止"
                    message.value = "$result · 实际输出：${AudioRoutePreferences.label(actualDevice)}"
                }
                playing.value = false
            }
        }
    }

    fun close() {
        closed = true
        stop()
        worker.shutdown()
    }

    companion object {
        /** Six seconds of an original arpeggio: left, right, then stereo, two seconds each. */
        internal fun melody(): ShortArray {
            val notes = doubleArrayOf(261.63, 329.63, 392.0, 523.25, 392.0, 329.63, 293.66, 349.23)
            return ShortArray(48000 * 6 * 2).also { data ->
                for (frame in 0 until data.size / 2) {
                    val time = frame / 48000.0
                    val note = notes[((time / 0.25).toInt()) % notes.size]
                    val local = time % 0.25
                    val envelope = minOf(local / 0.012, (0.25 - local) / 0.04, 1.0).coerceAtLeast(0.0)
                    val sample = ((sin(2 * PI * note * time) + 0.2 * sin(4 * PI * note * time)) * envelope * 12000).toInt().toShort()
                    data[frame * 2] = if (time < 2 || time >= 4) sample else 0
                    data[frame * 2 + 1] = if (time >= 2) sample else 0
                }
            }
        }
    }
}
