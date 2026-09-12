package com.carlink.vehicle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleKeyGestureGateTest {
    @Test
    fun `down up and short summary produce a single gesture`() {
        val gate = VehicleKeyGestureGate()
        assertTrue(gate.accept(200087, 0, 1000))
        assertFalse(gate.accept(200087, 0, 1001))
        assertTrue(gate.accept(200087, 1, 2000))
        assertFalse(gate.accept(200087, 2, 2001))
        assertFalse(gate.accept(200087, 1, 2002))
        assertTrue(gate.accept(200087, 0, 2400))
    }

    @Test
    fun `short-only firmware works without duplicate raw events`() {
        val gate = VehicleKeyGestureGate()
        assertTrue(gate.accept(200400, 2, 1000))
        assertFalse(gate.accept(200400, 0, 1001))
        assertFalse(gate.accept(200400, 1, 1002))
        assertFalse(gate.accept(200400, 2, 1100))
        assertTrue(gate.accept(200400, 2, 1400))
    }

    @Test
    fun `keys remain independent and stopping clears held state`() {
        val gate = VehicleKeyGestureGate()
        assertTrue(gate.accept(200088, 0, 1000))
        assertTrue(gate.accept(200085, 0, 1001))
        assertFalse(gate.accept(200088, 2, 5000))
        gate.clear()
        assertTrue(gate.accept(200088, 0, 5001))
        assertFalse(gate.accept(200088, 3, 5002))
    }
}
