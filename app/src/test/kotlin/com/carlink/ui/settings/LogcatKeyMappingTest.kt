package com.carlink.ui.settings

import com.carlink.protocol.InputKeyAction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogcatKeyMappingTest {
    @Test
    fun `plain pattern is a case insensitive contains match`() {
        val mapping = mapping("KEYCODE_MEDIA_NEXT")

        assertTrue(mapping.matches("InputReader: keycode_media_next pressed"))
        assertFalse(mapping.matches("InputReader: KEYCODE_MEDIA_PREVIOUS pressed"))
    }

    @Test
    fun `percent and star are wildcard characters`() {
        assertTrue(mapping("steering%NEXT").matches("Vehicle: STEERING_WHEEL_MEDIA_NEXT"))
        assertTrue(mapping("button*pressed").matches("Button volume knob pressed"))
        assertFalse(mapping("steering%NEXT").matches("Vehicle: steering previous"))
    }

    @Test
    fun `regex characters and underscores stay literal`() {
        assertTrue(mapping("key_[NEXT]").matches("Input: key_[next] down"))
        assertFalse(mapping("key_[NEXT]").matches("Input: key_XN down"))
    }

    @Test
    fun `overly broad patterns are rejected`() {
        assertFalse(isValidLogcatPattern("%%*"))
        assertFalse(isValidLogcatPattern("%"))
        assertTrue(isValidLogcatPattern("up"))
        assertTrue(isValidLogcatPattern("key%"))
    }

    private fun mapping(pattern: String) =
        LogcatKeyMapping(
            id = "test",
            pattern = pattern,
            action = InputKeyAction.NEXT,
            mode = LogcatTriggerMode.CARPLAY_ONLY,
        )
}
