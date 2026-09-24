package com.carlink.protocol

/** CallStatus metadata is authoritative; main-channel audio provides an early/fallback signal. */
internal class CallKeyRouter {
    enum class State { IDLE, RINGING, ACTIVE }

    @Volatile var state = State.IDLE
        private set

    private var hasCallMetadata = false

    @Synchronized
    fun reset() {
        hasCallMetadata = false
        state = State.IDLE
    }

    @Synchronized
    fun onCallStatus(status: Int) {
        val next = when (status) {
            0 -> State.IDLE
            2 -> State.RINGING
            // 3 is connecting; 4 is ACTIVE, not disconnecting. Dialing also permits hangup.
            1, 3, 4 -> State.ACTIVE
            else -> return
        }
        hasCallMetadata = next != State.IDLE
        state = next
    }

    @Synchronized
    fun onAudioCommand(command: AudioCommand, audioType: Int) {
        // Channel 2 is navigation. Its PHONECALL_STOP is a channel-clear signal.
        if (audioType != 1) return
        state = when (command) {
            AudioCommand.AUDIO_INCOMING_CALL_INIT -> State.RINGING
            AudioCommand.AUDIO_PHONECALL_START -> State.ACTIVE
            AudioCommand.AUDIO_PHONECALL_STOP -> if (hasCallMetadata) state else State.IDLE
            // Also covers a missed/rejected call followed by media resumption.
            AudioCommand.AUDIO_MEDIA_START -> if (!hasCallMetadata && state == State.RINGING) State.IDLE else state
            // ALERT_STOP may precede PHONECALL_START; Siri/output events are independent.
            else -> state
        }
    }

    fun command(action: InputKeyAction, released: Boolean = false): CommandMapping? {
        if (released) return action.releaseCommand
        val snapshot = state
        return when {
            action == InputKeyAction.NEXT && snapshot == State.RINGING -> action.callCommand
            action == InputKeyAction.PREVIOUS && snapshot != State.IDLE -> action.callCommand
            else -> action.command
        }
    }
}
