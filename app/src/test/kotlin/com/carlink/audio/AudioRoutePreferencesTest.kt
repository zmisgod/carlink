package com.carlink.audio

import android.media.AudioAttributes
import com.carlink.protocol.StreamPurpose
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioRoutePreferencesTest {
    @Test
    fun `new routes inherit old media choice until explicitly configured`() {
        val saved = mutableMapOf(AudioRoutePreferences.MEDIA_OUTPUT to "speaker")
        val nav = AudioRoutePreferences.NAVIGATION_OUTPUT
        val ring = AudioRoutePreferences.RINGTONE_OUTPUT
        assertEquals("speaker", AudioRoutePreferences.resolveDeviceKey(nav, saved::get))
        assertEquals("speaker", AudioRoutePreferences.resolveDeviceKey(ring, saved::get))
        saved[nav] = "navigation bus"
        saved[ring] = ""
        assertEquals("navigation bus", AudioRoutePreferences.resolveDeviceKey(nav, saved::get))
        assertEquals("", AudioRoutePreferences.resolveDeviceKey(ring, saved::get))
        assertEquals("speaker", AudioRoutePreferences.resolveDeviceKey(AudioRoutePreferences.MEDIA_OUTPUT, saved::get))
    }

    @Test
    fun `four output choices are independent and cover assistant and adapter alerts`() {
        val keys =
            listOf(StreamPurpose.PHONE_CALL, StreamPurpose.RINGTONE, StreamPurpose.NAVIGATION, StreamPurpose.MEDIA)
                .map(AudioRoutePreferences::outputKey)
        assertEquals(4, keys.toSet().size)
        assertEquals(keys[0], AudioRoutePreferences.outputKey(StreamPurpose.SIRI))
        assertEquals(keys[1], AudioRoutePreferences.outputKey(StreamPurpose.ALERT))
    }

    @Test
    fun `ringtone preview uses production signalling route rather than music`() {
        assertEquals(
            AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING to AudioAttributes.CONTENT_TYPE_SONIFICATION,
            AudioRoutePreferences.attributes(StreamPurpose.RINGTONE),
        )
        assertEquals(AudioRoutePreferences.attributes(StreamPurpose.RINGTONE), AudioRoutePreferences.attributes(StreamPurpose.ALERT))
    }
}
