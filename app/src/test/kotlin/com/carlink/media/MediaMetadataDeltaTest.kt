package com.carlink.media

import androidx.test.core.app.ApplicationProvider
import com.carlink.CarlinkManager
import com.carlink.protocol.MediaDataMessage
import com.carlink.protocol.MediaType
import com.carlink.protocol.MessageHeader
import com.carlink.protocol.MessageType
import com.carlink.vehicle.VehicleProjectionState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class MediaMetadataDeltaTest {
    private lateinit var manager: CarlinkManager

    @Before
    fun setUp() {
        manager = CarlinkManager(ApplicationProvider.getApplicationContext())
        send("MediaSongName" to "First", "MediaArtistName" to "Artist", "MediaAlbumName" to "Album")
    }

    @After
    fun tearDown() {
        reset()
        CarlinkMediaBrowserService.clearNowPlaying()
    }

    @Test
    fun `title delta retains unchanged artist and album`() {
        send("MediaSongName" to "Second")
        assertEquals("Second", info().songTitle)
        assertEquals("Artist", info().songArtist)
        assertEquals("Album", info().albumName)
    }

    @Test
    fun `artist and album arriving before title survive title update`() {
        send("MediaArtistName" to "Other artist", "MediaAlbumName" to "Other album")
        send("MediaSongName" to "Second")
        assertEquals("Second", info().songTitle)
        assertEquals("Other artist", info().songArtist)
        assertEquals("Other album", info().albumName)
    }

    @Test
    fun `artist arriving after title updates the projection snapshot`() {
        send("MediaSongName" to "Second")
        send("MediaArtistName" to "Other artist")
        send("MediaSongPlayTime" to 1000)
        assertEquals("Second", info().songTitle)
        assertEquals("Other artist", info().songArtist)
        assertEquals(1000L, info().position)
    }

    @Test
    fun `complete consecutive tracks replace both title and artist`() {
        repeat(3) { index ->
            send("MediaSongName" to "Track $index", "MediaArtistName" to "Artist $index")
            assertEquals("Track $index", info().songTitle)
            assertEquals("Artist $index", info().songArtist)
        }
    }

    @Test
    fun `explicit empty fields clear old text`() {
        send("MediaSongName" to "Second", "MediaArtistName" to "", "MediaAlbumName" to "")
        assertEquals("", info().songArtist)
        assertEquals("", info().albumName)
        send("MediaSongName" to "")
        assertEquals("", info().songTitle)
    }

    @Test
    fun `disconnect prevents text leaking into a new session`() {
        reset()
        assertNull(VehicleProjectionState.music.value)
        send("MediaSongName" to "Second")
        assertNull(info().songArtist)
        assertNull(info().albumName)
    }

    private fun info() = VehicleProjectionState.music.value!!.info

    private fun reset() {
        CarlinkManager::class.java.getDeclaredMethod("clearCachedMediaMetadata").apply {
            isAccessible = true
        }.invoke(manager)
    }

    private fun send(vararg fields: Pair<String, Any>) {
        val message = MediaDataMessage(MessageHeader(0, MessageType.MEDIA_DATA), MediaType.DATA, mapOf(*fields))
        CarlinkManager::class.java.getDeclaredMethod("processMediaMetadata", MediaDataMessage::class.java).apply {
            isAccessible = true
        }.invoke(manager, message)
    }
}
