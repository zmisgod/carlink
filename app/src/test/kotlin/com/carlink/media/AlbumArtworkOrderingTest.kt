package com.carlink.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlbumArtworkOrderingTest {
    @Test
    fun `covers arriving before titles survive three consecutive track changes`() {
        val ordering = AlbumArtworkOrdering()
        repeat(3) { index ->
            val cover = byteArrayOf(index.toByte())
            ordering.onStandalone(cover, index * 10000L)
            assertArrayEquals(cover, ordering.onTitleChanged(index * 10000L + 200))
            assertNull(ordering.onTitleChanged(index * 10000L + 300))
        }
    }

    @Test
    fun `old cover and disconnect state do not leak into new track`() {
        val ordering = AlbumArtworkOrdering()
        ordering.onStandalone(byteArrayOf(1), 100)
        assertNull(ordering.onTitleChanged(10000))
        ordering.onStandalone(byteArrayOf(2), 10100)
        ordering.clear()
        assertNull(ordering.onTitleChanged(10200))
    }
}
