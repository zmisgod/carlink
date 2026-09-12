package com.carlink.vehicle

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import com.carlink.CarlinkManager
import com.carlink.navigation.NavigationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class VehicleProjectionTest {
    @Test
    fun `cover identity changes with content and remains stable across progress and revisits`() {
        VehicleProjectionState.music.value = null
        val first = CarlinkManager.MediaInfo("Song", "Artist", "Album", null, byteArrayOf(1), 120000, 0, true)
        VehicleProjectionState.updateMusic(first)
        val id = VehicleProjectionState.music.value!!.id
        VehicleProjectionState.updateMusic(first.copy(position = 5000, isPlaying = false, albumCover = byteArrayOf(1)))
        assertEquals(id, VehicleProjectionState.music.value!!.id)
        VehicleProjectionState.updateMusic(first.copy(albumCover = byteArrayOf(2)))
        assertNotEquals(id, VehicleProjectionState.music.value!!.id)
        VehicleProjectionState.updateMusic(first.copy(songTitle = "Other song"))
        assertNotEquals(id, VehicleProjectionState.music.value!!.id)
        VehicleProjectionState.updateMusic(first)
        assertEquals(id, VehicleProjectionState.music.value!!.id)
        VehicleProjectionState.music.value = null
    }

    @Test
    fun `navigation preserves seconds and does not invent heading or lanes`() {
        val info =
            VehicleNavigationAdapter.convert(
                NavigationState(
                    status = 1,
                    maneuverType = 2,
                    roadName = "人民路",
                    remainDistance = 320,
                    distanceToDestination = 8000,
                    timeToDestination = 965,
                ),
            )
        assertEquals(NavigationInfo.Maneuver.RIGHT, info.maneuver)
        assertEquals(965, info.remainingSeconds())
        assertEquals(17, info.routeRemainingMinutes)
        assertEquals(17, info.etaMinutes)
        assertEquals("人民路", info.nextRoadName)
        assertEquals(320, info.distanceMeters)
        assertEquals(-1, info.details.direction())
        assertEquals(0, info.details.lanes().size)
        assertEquals(-1, info.currentSpeedKmh)
        assertTrue(info.latitude.isNaN())
        assertEquals(NavigationInfo.Maneuver.ARRIVED, VehicleNavigationAdapter.maneuver(24))
        assertEquals(NavigationInfo.Maneuver.U_TURN, VehicleNavigationAdapter.maneuver(26))
    }

    @Test
    fun `public cover is a decodable bounded JPEG and leaves DIM track UUID intact`() {
        val bitmap = Bitmap.createBitmap(800, 400, Bitmap.Config.ARGB_8888)
        val id = "test-track"
        var lastFile: File? = null
        try {
            repeat(5) {
                val uri = PublicArtworkStore.publish(id, bitmap)
                assertNotNull(PublicArtworkStore.lastError(), uri)
                val file = File(uri!!.path!!)
                lastFile = file
                val bytes = file.readBytes()
                assertEquals(0xff, bytes[0].toInt() and 0xff)
                assertEquals(0xd8, bytes[1].toInt() and 0xff)
                val image = BitmapFactory.decodeFile(file.absolutePath)
                assertEquals(192, image.width)
                assertEquals(96, image.height)
                image.recycle()
                val metadata =
                    MediaMetadata.Builder()
                        .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, id)
                        .putString(MediaMetadata.METADATA_KEY_ART_URI, uri.toString()).build()
                assertEquals(id, GeelyEcarxDimInteractionBridge.mediaUuid(metadata))
                assertEquals(uri, GeelyEcarxDimInteractionBridge.artworkUri(metadata))
                assertNotEquals(id, file.nameWithoutExtension)
            }
            assertEquals(3, lastFile!!.parentFile!!.listFiles()!!.size)
        } finally {
            bitmap.recycle()
            lastFile?.parentFile?.deleteRecursively()
        }
    }
}
