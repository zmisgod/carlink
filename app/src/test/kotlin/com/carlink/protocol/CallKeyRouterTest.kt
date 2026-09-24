package com.carlink.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallKeyRouterTest {
    private val router = CallKeyRouter()

    @Test
    fun `call metadata works without any ringtone audio command`() {
        router.onCallStatus(2)
        assertEquals(CommandMapping.PHONE_ANSWER, router.command(InputKeyAction.NEXT))
        assertEquals(CommandMapping.PHONE_HANG_UP, router.command(InputKeyAction.PREVIOUS))
        router.onCallStatus(3)
        router.onCallStatus(4)
        assertEquals(CommandMapping.PHONE_HANG_UP, router.command(InputKeyAction.PREVIOUS))
        router.onCallStatus(0)
        assertEquals(CommandMapping.PREV, router.command(InputKeyAction.PREVIOUS))
    }

    @Test
    fun `known call metadata survives audio cleanup until definitive idle`() {
        for (status in listOf(1, 2, 3, 4)) {
            router.onCallStatus(status)
            audio(AudioCommand.AUDIO_ALERT_STOP)
            audio(AudioCommand.AUDIO_PHONECALL_STOP)
            audio(AudioCommand.AUDIO_MEDIA_START)
            assertEquals(CommandMapping.PHONE_HANG_UP, router.command(InputKeyAction.PREVIOUS))
        }
        router.onCallStatus(0)
        assertEquals(CommandMapping.NEXT, router.command(InputKeyAction.NEXT))
    }

    @Test
    fun `missed call metadata clears state even without media resuming`() {
        router.onCallStatus(2)
        audio(AudioCommand.AUDIO_ALERT_STOP)
        router.onCallStatus(0)
        assertEquals(CommandMapping.NEXT, router.command(InputKeyAction.NEXT))
    }

    @Test
    fun `unknown metadata leaves state intact and reset restores audio fallback`() {
        router.onCallStatus(4)
        router.onCallStatus(99)
        router.onCallStatus(-1)
        assertEquals(CommandMapping.PHONE_HANG_UP, router.command(InputKeyAction.PREVIOUS))
        router.reset()
        audio(AudioCommand.AUDIO_PHONECALL_START)
        audio(AudioCommand.AUDIO_PHONECALL_STOP)
        assertEquals(CommandMapping.PREV, router.command(InputKeyAction.PREVIOUS))
    }

    @Test
    fun `idle keys control music`() {
        assertEquals(CommandMapping.NEXT, router.command(InputKeyAction.NEXT))
        assertEquals(CommandMapping.PREV, router.command(InputKeyAction.PREVIOUS))
    }

    @Test
    fun `ringing keys answer or reject and active previous hangs up`() {
        audio(AudioCommand.AUDIO_INCOMING_CALL_INIT)
        assertEquals(CommandMapping.PHONE_ANSWER, router.command(InputKeyAction.NEXT))
        assertEquals(CommandMapping.PHONE_HANG_UP, router.command(InputKeyAction.PREVIOUS))
        audio(AudioCommand.AUDIO_PHONECALL_START)
        assertEquals(CommandMapping.PHONE_HANG_UP, router.command(InputKeyAction.PREVIOUS))
        assertEquals(CommandMapping.NEXT, router.command(InputKeyAction.NEXT))
    }

    @Test
    fun `ringtone stops before call connects without losing call controls`() {
        audio(AudioCommand.AUDIO_INCOMING_CALL_INIT)
        audio(AudioCommand.AUDIO_MEDIA_STOP)
        audio(AudioCommand.AUDIO_OUTPUT_STOP)
        audio(AudioCommand.AUDIO_ALERT_START)
        audio(AudioCommand.AUDIO_ALERT_STOP)
        audio(AudioCommand.AUDIO_OUTPUT_STOP)
        audio(AudioCommand.AUDIO_INPUT_CONFIG)
        assertEquals(CommandMapping.PHONE_HANG_UP, router.command(InputKeyAction.PREVIOUS))
        audio(AudioCommand.AUDIO_PHONECALL_START)
        audio(AudioCommand.AUDIO_ALERT_STOP)
        assertEquals(CommandMapping.PHONE_HANG_UP, router.command(InputKeyAction.PREVIOUS))
    }

    @Test
    fun `navigation channel clear does not end ringing or active call`() {
        for (start in listOf(AudioCommand.AUDIO_INCOMING_CALL_INIT, AudioCommand.AUDIO_PHONECALL_START)) {
            audio(start)
            router.onAudioCommand(AudioCommand.AUDIO_PHONECALL_STOP, audioType = 2)
            assertEquals(CommandMapping.PHONE_HANG_UP, router.command(InputKeyAction.PREVIOUS))
        }
    }

    @Test
    fun `Siri and output cycling preserve a known incoming call`() {
        audio(AudioCommand.AUDIO_INCOMING_CALL_INIT)
        audio(AudioCommand.AUDIO_SIRI_START)
        audio(AudioCommand.AUDIO_SIRI_STOP)
        audio(AudioCommand.AUDIO_OUTPUT_START)
        audio(AudioCommand.AUDIO_OUTPUT_STOP)
        assertEquals(CommandMapping.PHONE_ANSWER, router.command(InputKeyAction.NEXT))
    }

    @Test
    fun `outgoing call allows hangup and media does not clear an active call`() {
        audio(AudioCommand.AUDIO_PHONECALL_START)
        audio(AudioCommand.AUDIO_MEDIA_START)
        assertEquals(CommandMapping.PHONE_HANG_UP, router.command(InputKeyAction.PREVIOUS))
    }

    @Test
    fun `ended and missed calls return to music`() {
        audio(AudioCommand.AUDIO_INCOMING_CALL_INIT)
        audio(AudioCommand.AUDIO_PHONECALL_STOP)
        audio(AudioCommand.AUDIO_PHONECALL_STOP)
        assertEquals(CommandMapping.PREV, router.command(InputKeyAction.PREVIOUS))
        audio(AudioCommand.AUDIO_INCOMING_CALL_INIT)
        audio(AudioCommand.AUDIO_ALERT_STOP)
        audio(AudioCommand.AUDIO_MEDIA_START)
        assertEquals(CommandMapping.NEXT, router.command(InputKeyAction.NEXT))
    }

    @Test
    fun `notification alert alone is not an incoming call`() {
        audio(AudioCommand.AUDIO_ALERT_START)
        assertEquals(CommandMapping.NEXT, router.command(InputKeyAction.NEXT))
        audio(AudioCommand.AUDIO_ALERT_STOP)
        assertEquals(CommandMapping.PREV, router.command(InputKeyAction.PREVIOUS))
    }

    @Test
    fun `disconnect reset discards old call state`() {
        audio(AudioCommand.AUDIO_PHONECALL_START)
        router.reset()
        assertEquals(CommandMapping.PREV, router.command(InputKeyAction.PREVIOUS))
    }

    @Test
    fun `dedicated phone keys work without audio call signals and never repeat on release`() {
        assertEquals(CommandMapping.PHONE_ANSWER, router.command(InputKeyAction.ANSWER_CALL))
        assertEquals(CommandMapping.PHONE_HANG_UP, router.command(InputKeyAction.HANG_UP_CALL))
        for (action in listOf(InputKeyAction.NEXT, InputKeyAction.PREVIOUS, InputKeyAction.ANSWER_CALL, InputKeyAction.HANG_UP_CALL)) {
            assertNull(router.command(action, released = true))
        }
        assertEquals(CommandMapping.SIRI_BUTTON_UP, router.command(InputKeyAction.SIRI, released = true))
    }

    private fun audio(command: AudioCommand) = router.onAudioCommand(command, audioType = 1)
}
