package com.carlink.audio

import kotlin.math.sqrt

object PcmChannels {
    data class Meter(val waveform: List<Float>, val rms: Float, val peak: Float)

    private fun sample(
        bytes: ByteArray,
        offset: Int,
    ): Int = ((bytes[offset].toInt() and 255) or (bytes[offset + 1].toInt() shl 8)).toShort().toInt()

    fun mono(
        input: ByteArray,
        length: Int,
        channels: Int,
        selected: Int,
        output: ByteArray,
    ): Int {
        require(channels > 0 && selected in -1 until channels)
        val frames = length.coerceAtMost(input.size) / (channels * 2)
        require(output.size >= frames * 2)
        for (frame in 0 until frames) {
            val offset = frame * channels * 2
            val value =
                if (selected >= 0) {
                    sample(input, offset + selected * 2)
                } else {
                    var sum = 0
                    for (channel in 0 until channels) sum += sample(input, offset + channel * 2)
                    sum / channels
                }
            output[frame * 2] = value.toByte()
            output[frame * 2 + 1] = (value shr 8).toByte()
        }
        return frames * 2
    }

    fun meters(
        input: ByteArray,
        length: Int,
        channels: Int,
    ): List<Meter> {
        require(channels > 0)
        val frames = length.coerceAtMost(input.size) / (channels * 2)
        return (0 until channels).map { channel ->
            var square = 0.0
            var peak = 0f
            val waveform = mutableListOf<Float>()
            val stride = (frames / 96).coerceAtLeast(1)
            for (frame in 0 until frames) {
                val value = sample(input, (frame * channels + channel) * 2) / 32768f
                square += value.toDouble() * value
                peak = maxOf(peak, kotlin.math.abs(value))
                if (frame % stride == 0 && waveform.size < 96) waveform += value
            }
            Meter(waveform, if (frames == 0) 0f else sqrt(square / frames).toFloat(), peak)
        }
    }
}
