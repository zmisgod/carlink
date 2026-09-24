package com.carlink.media

import com.carlink.protocol.MediaDataMessage
import com.carlink.protocol.MediaType
import com.carlink.protocol.MessageHeader
import com.carlink.protocol.MessageParser
import com.carlink.protocol.MessageType
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class MusicDiagnosticLogTest {
    @Before
    fun prepare() {
        MusicDiagnosticLog.setEnabled(false)
        MusicDiagnosticLog.clear()
    }

    @After
    fun cleanup() = prepare()

    @Test
    fun `disabled capture does not record or alter parsed metadata`() {
        val message = receive("""{"MediaSongName":"First"}""")
        assertEquals("First", message.payload["MediaSongName"])
        assertTrue(MusicDiagnosticLog.state.value.entries.isEmpty())
    }

    @Test
    fun `raw JSON preserves missing empty null and unknown fields`() {
        MusicDiagnosticLog.setEnabled(true)
        val raw = """{ "MediaArtistName": "歌手", "MediaAlbumName":"", "extra":null, "OtherTitle":"新歌" }"""
        val message = receive(raw + "\u0000")
        assertFalse(message.payload.containsKey("MediaSongName"))
        assertEquals("", message.payload["MediaAlbumName"])
        assertEquals(raw, MusicDiagnosticLog.state.value.entries.single().text.substringAfter('\n'))
    }

    @Test
    fun `malformed JSON is captured before parser discards it`() {
        MusicDiagnosticLog.setEnabled(true)
        val raw = "{\"MediaSongName\":broken"
        assertTrue(receive(raw).payload.isEmpty())
        assertTrue(MusicDiagnosticLog.exportText().contains(raw))
    }

    @Test
    fun `stop retains records clear keeps switch state and cover has no binary body`() {
        MusicDiagnosticLog.setEnabled(true)
        receive("image-data", MediaType.ALBUM_COVER)
        assertTrue(MusicDiagnosticLog.state.value.entries.single().text.contains("coverBytes=10"))
        assertFalse(MusicDiagnosticLog.exportText().contains("image-data"))
        MusicDiagnosticLog.setEnabled(false)
        receive("{}")
        assertEquals(1, MusicDiagnosticLog.state.value.entries.size)
        MusicDiagnosticLog.clear()
        assertFalse(MusicDiagnosticLog.state.value.enabled)
        assertTrue(MusicDiagnosticLog.state.value.entries.isEmpty())
        MusicDiagnosticLog.setEnabled(true)
        MusicDiagnosticLog.clear()
        assertTrue(MusicDiagnosticLog.state.value.enabled)
    }

    @Test
    fun `navigation and calls are excluded`() {
        MusicDiagnosticLog.setEnabled(true)
        receive("{}", MediaType.CALL_STATUS)
        receive("{}", MediaType.NAVI_JSON)
        assertTrue(MusicDiagnosticLog.state.value.entries.isEmpty())
    }

    @Test
    fun `bounded log drops oldest entries and marks oversized payload`() {
        MusicDiagnosticLog.setEnabled(true)
        repeat(MusicDiagnosticLog.MAX_ENTRIES + 2) { receive("{}") }
        val state = MusicDiagnosticLog.state.value
        assertEquals(MusicDiagnosticLog.MAX_ENTRIES, state.entries.size)
        assertEquals(2L, state.dropped)
        assertEquals(3L, state.entries.first().id)
        receive("""{"MediaSongName":"${"x".repeat(20000)}"}""")
        assertTrue(MusicDiagnosticLog.state.value.entries.last().text.contains("已截断"))
    }

    @Test
    fun `export distinguishes received delta and merged metadata in arrival order`() {
        MusicDiagnosticLog.setEnabled(true)
        receive("""{"MediaArtistName":"New artist"}""")
        MusicDiagnosticLog.recordMerged("Old title", "New artist", null, "Spotify", 1000, 0)
        val text = MusicDiagnosticLog.exportText()
        assertTrue(text.indexOf("#1") < text.indexOf("#2"))
        assertTrue(text.contains("[RX JSON]"))
        assertTrue(text.contains("[MERGED]"))
        assertTrue(text.contains("\"title\":\"Old title\""))
        assertTrue(text.contains("\"album\":null"))
    }

    private fun receive(raw: String, type: MediaType = MediaType.DATA): MediaDataMessage {
        val body = raw.toByteArray(Charsets.UTF_8)
        val bytes = ByteBuffer.allocate(4 + body.size).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(type.id).put(body).array()
        return MessageParser.parseMessage(MessageHeader(bytes.size, MessageType.MEDIA_DATA), bytes) as MediaDataMessage
    }
}
