package com.carlink.media

/** CarPlay/AA can send a standalone cover shortly before the new title. Keep it across that reset. */
class AlbumArtworkOrdering(private val reorderWindowMs: Long = 2000) {
    private var pending: ByteArray? = null
    private var receivedAt = 0L

    fun onStandalone(
        bytes: ByteArray,
        now: Long,
    ) {
        pending = bytes
        receivedAt = now
    }

    fun onTitleChanged(now: Long): ByteArray? {
        val result = pending.takeIf { now - receivedAt in 0..reorderWindowMs }
        clear()
        return result
    }

    fun clear() {
        pending = null
        receivedAt = 0
    }
}
