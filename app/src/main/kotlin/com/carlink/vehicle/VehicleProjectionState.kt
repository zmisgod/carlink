package com.carlink.vehicle

import android.content.Context
import androidx.core.content.edit
import com.carlink.CarlinkManager
import kotlinx.coroutines.flow.MutableStateFlow

/** Latest adapter snapshot, independent of the audio output mode and Media3 session. */
object VehicleProjectionState {
    data class Music(val id: String, val info: CarlinkManager.MediaInfo)

    val music = MutableStateFlow<Music?>(null)
    val artworkStatus = MutableStateFlow("等待音乐封面")
    val status = MutableStateFlow("DIM 等待连接")
    val secondaryStatus = MutableStateFlow("Android 副屏未开启")
    val keyStatus = MutableStateFlow("OneOS 等待连接")
    val keyReconnect = MutableStateFlow(0)
    val lastKey = MutableStateFlow("")
    val refresh = MutableStateFlow(0)

    fun updateMusic(info: CarlinkManager.MediaInfo) {
        val previous = music.value
        val unchanged =
            previous != null && previous.info.songTitle == info.songTitle &&
                previous.info.songArtist == info.songArtist && previous.info.albumName == info.albumName &&
                previous.info.albumCover === info.albumCover
        val id = if (unchanged) previous.id else MusicArtworkIdentity.id(info.songTitle, info.songArtist, info.albumName, info.albumCover)
        music.value = Music(id, info)
    }

    fun enabled(context: Context): Boolean = context.getSharedPreferences("carlink_vehicle", Context.MODE_PRIVATE).getBoolean("enabled", true)

    fun setEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        context.getSharedPreferences("carlink_vehicle", Context.MODE_PRIVATE).edit { putBoolean("enabled", enabled) }
        refresh.value++
    }

    fun secondaryEnabled(context: Context): Boolean = context.getSharedPreferences("carlink_vehicle", Context.MODE_PRIVATE).getBoolean("secondary", false)

    fun setSecondaryEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        context.getSharedPreferences("carlink_vehicle", Context.MODE_PRIVATE).edit { putBoolean("secondary", enabled) }
        refresh.value++
    }
}
