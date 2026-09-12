package com.carlink.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmChannelsTest {
    private fun pcm(vararg samples: Int): ByteArray = samples.flatMap { listOf(it.toByte(), (it shr 8).toByte()) }.toByteArray()

    @Test
    fun `selected hardware channel becomes mono without changing frame count`() {
        val input = pcm(1000, -2000, 3000, -4000)
        val output = ByteArray(4)
        assertEquals(4, PcmChannels.mono(input, input.size, 2, 1, output))
        assertArrayEquals(pcm(-2000, -4000), output)
        PcmChannels.mono(input, input.size, 2, 0, output)
        assertArrayEquals(pcm(1000, 3000), output)
    }

    @Test
    fun `multichannel mixing does not overflow and incomplete frames are excluded`() {
        val input = pcm(32767, 32767, 32767, 32767, -32768, -32768, -32768, -32768, 120)
        val output = ByteArray(4)
        assertEquals(4, PcmChannels.mono(input, input.size, 4, -1, output))
        assertArrayEquals(pcm(32767, -32768), output)
    }

    @Test
    fun `meters distinguish silent channel from active channel`() {
        val input = pcm(0, 16384, 0, -16384)
        val meters = PcmChannels.meters(input, input.size, 2)
        assertEquals(0f, meters[0].rms, 0.00001f)
        assertEquals(0.5f, meters[1].rms, 0.00001f)
        assertEquals(0.5f, meters[1].peak, 0.00001f)
        assertTrue(meters[1].waveform.first() > 0)
        assertTrue(meters[1].waveform.last() < 0)
    }

    @Test
    fun `demo melody sends left then right then both with bounded peaks`() {
        val data = AudioDiagnosticSession.melody()
        assertEquals(48000 * 6 * 2, data.size)
        assertTrue(data.any { it != 0.toShort() })
        for (frame in 0 until 48000 * 6) {
            if (frame < 48000 * 2) assertEquals(0, data[frame * 2 + 1].toInt())
            if (frame in 48000 * 2 until 48000 * 4) assertEquals(0, data[frame * 2].toInt())
            if (frame >= 48000 * 4) assertEquals(data[frame * 2], data[frame * 2 + 1])
            assertTrue(kotlin.math.abs(data[frame * 2].toInt()) < 16000)
        }
    }
}
