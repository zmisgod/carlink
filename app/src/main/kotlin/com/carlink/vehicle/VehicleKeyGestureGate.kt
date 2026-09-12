package com.carlink.vehicle

/** Collapses raw DOWN/UP plus OneOS short-click summaries into one gesture. */
class VehicleKeyGestureGate {
    private val held = mutableSetOf<Int>()
    private val lastEvent = mutableMapOf<Int, Long>()

    fun accept(
        code: Int,
        action: Int,
        now: Long,
    ): Boolean {
        val previous = lastEvent[code]
        return when (action) {
            0 -> {
                if (code in held || (previous != null && now - previous < 80)) {
                    false
                } else {
                    held.add(code)
                    lastEvent[code] = now
                    true
                }
            }
            1 -> {
                val wasHeld = held.remove(code)
                if (wasHeld) lastEvent[code] = now
                wasHeld
            }
            2 -> {
                if (code in held || (previous != null && now - previous < 350)) {
                    false
                } else {
                    lastEvent[code] = now
                    true
                }
            }
            else -> false
        }
    }

    fun clear() {
        held.clear()
        lastEvent.clear()
    }
}
