package com.carlink.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MicCaptureLeaseTest {
    @Test
    fun `Siri preempts a diagnostic and diagnostic cannot steal Siri`() {
        val diagnostic = Any()
        val siri = Any()
        var stopped = false
        try {
            assertTrue(
                MicCaptureLease.acquire(diagnostic, true) {
                    stopped = true
                    MicCaptureLease.release(diagnostic)
                },
            )
            assertTrue(MicCaptureLease.acquire(siri, false) { MicCaptureLease.release(siri) })
            assertTrue(stopped)
            assertFalse(MicCaptureLease.acquire(diagnostic, true) { })
            MicCaptureLease.release(diagnostic)
            assertFalse(MicCaptureLease.acquire(Any(), false) { })
        } finally {
            MicCaptureLease.release(siri)
            MicCaptureLease.release(diagnostic)
        }
    }
}
