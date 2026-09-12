package com.carlink.vehicle

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID

/** Include the song title: an artist's tracks often share exactly the same album artwork. */
object MusicArtworkIdentity {
    fun id(
        title: String?,
        artist: String?,
        album: String?,
        artwork: ByteArray?,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        listOf(title, artist, album).forEach { value ->
            val bytes = value.orEmpty().trim().toByteArray(Charsets.UTF_8)
            digest.update(ByteBuffer.allocate(4).putInt(bytes.size).array())
            digest.update(bytes)
        }
        digest.update(if (artwork == null) 0.toByte() else 1.toByte())
        artwork?.let(digest::update)
        return UUID.nameUUIDFromBytes(digest.digest()).toString()
    }
}
