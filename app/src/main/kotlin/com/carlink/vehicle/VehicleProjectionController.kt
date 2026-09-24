package com.carlink.vehicle

import android.app.Activity
import android.graphics.BitmapFactory
import android.hardware.display.DisplayManager
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.carlink.navigation.NavigationStateManager
import com.carlink.ui.settings.AdapterConfigPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One owner per Activity. Binder publishing and JPEG IO are serialized off the UI thread. */
class VehicleProjectionController(private val activity: Activity, owner: LifecycleOwner) {
    private val bridge = GeelyEcarxDimInteractionBridge.get(activity)
    private val displays = activity.getSystemService(DisplayManager::class.java)
    private var presentation: ClusterPresentation? = null
    private var presentationDisplay = -1
    private var artwork: Uri? = null
    private var artworkError = ""
    private var artworkId: String? = null
    private var artworkBytes: ByteArray? = null
    private var metadata: MediaMetadata? = null
    private val outputLock = Any()

    @Volatile private var closed = false
    private var lastArtworkAttempt = 0L
    private var lastRefresh = -1
    private var job: Job
    private val displayListener =
        object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {
                VehicleProjectionState.refresh.value++
            }

            override fun onDisplayRemoved(displayId: Int) {
                VehicleProjectionState.refresh.value++
            }

            override fun onDisplayChanged(displayId: Int) {
                VehicleProjectionState.refresh.value++
            }
        }

    init {
        displays.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
        job =
            owner.lifecycleScope.launch {
                combine(
                    VehicleProjectionState.music,
                    NavigationStateManager.state,
                    VehicleProjectionState.refresh,
                    AdapterConfigPreference.getInstance(activity).clusterNavigationFlow,
                ) { music, navigation, _, navigationEnabled -> Triple(music, navigation, navigationEnabled) }
                    .collect { (music, navigation, navigationEnabled) ->
                        val enabled = VehicleProjectionState.enabled(activity)
                        val info =
                            navigation.takeIf { enabled && navigationEnabled && !it.isIdle }
                                ?.let(VehicleNavigationAdapter::convert)
                        updateSecondary(info)
                        withContext(Dispatchers.IO) {
                            synchronized(outputLock) {
                                if (closed) return@withContext
                                try {
                                    if (lastRefresh != VehicleProjectionState.refresh.value) {
                                        lastArtworkAttempt = 0L
                                        lastRefresh = VehicleProjectionState.refresh.value
                                    }
                                    if (!enabled) {
                                        stopOutput()
                                        VehicleProjectionState.status.value = "DIM 已关闭"
                                        return@withContext
                                    }
                                    bridge.start()
                                    if (info == null) bridge.clearNavigation() else bridge.publishNavigation(info)
                                    if (music == null || music.info.songTitle.isNullOrBlank()) {
                                        if (metadata != null) stopMusic()
                                    } else {
                                        publishMusic(music)
                                    }
                                    VehicleProjectionState.status.value = bridge.status() +
                                        if (music?.info?.albumCover != null && artwork == null) "\n" + artworkError else ""
                                } catch (error: Exception) {
                                    android.util.Log.w("GeelyEcarxDim", "Vehicle snapshot failed", error)
                                    VehicleProjectionState.status.value = "DIM 发布失败：${error.message}"
                                }
                            }
                        }
                    }
            }
    }

    private fun publishMusic(music: VehicleProjectionState.Music) {
        val info = music.info
        val bytes = info.albumCover
        if (artworkId != music.id || !(artworkBytes contentEquals bytes)) {
            artwork = null
            lastArtworkAttempt = 0L
            artworkError = ""
            artworkId = music.id
            artworkBytes = bytes
        }
        val now = android.os.SystemClock.elapsedRealtime()
        if (artwork == null && bytes != null && (lastArtworkAttempt == 0L || now - lastArtworkAttempt >= 30_000L)) {
            lastArtworkAttempt = now
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val options = BitmapFactory.Options()
            while (maxOf(bounds.outWidth, bounds.outHeight) / options.inSampleSize.coerceAtLeast(1) > 384) {
                options.inSampleSize = options.inSampleSize.coerceAtLeast(1) * 2
            }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            if (bitmap == null) {
                artworkError = "歌曲封面无法解码"
            } else {
                try {
                    artwork = PublicArtworkStore.publish(music.id, bitmap)
                    artworkError = PublicArtworkStore.lastError()
                } finally {
                    bitmap.recycle()
                }
            }
        }
        VehicleProjectionState.artworkStatus.value =
            if (bytes == null) {
                "上游尚未提供当前歌曲封面 · ID=${music.id}"
            } else {
                "封面输入 ${bytes.size} 字节 · DIM ID=${music.id}\nJPEG=${artwork ?: artworkError}"
            }
        metadata =
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, music.id)
                .putString(MediaMetadata.METADATA_KEY_TITLE, info.songTitle)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, info.songArtist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, info.albumName)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, info.duration.coerceAtLeast(0))
                .apply {
                    artwork?.let { putString(MediaMetadata.METADATA_KEY_ART_URI, it.toString()) }
                }.build()
        // DIM identity includes title/artist/album/cover hash, and is shared by playlist and playback.
        bridge.publishMusic(
            metadata,
            PlaybackState.Builder().setState(
                if (info.isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                info.position.coerceAtLeast(0),
                if (info.isPlaying) 1f else 0f,
            ).setActiveQueueItemId(0L).build(),
        )
    }

    private fun stopMusic() {
        metadata?.let {
            bridge.publishMusic(
                MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "")
                    .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "").build(),
                PlaybackState.Builder().setState(PlaybackState.STATE_STOPPED, 0, 0f).build(),
            )
        }
        metadata = null
        VehicleProjectionState.artworkStatus.value = "等待音乐封面"
        artwork = null
        artworkId = null
        artworkBytes = null
    }

    private fun stopOutput() {
        stopMusic()
        bridge.stop()
    }

    private fun updateSecondary(info: NavigationInfo?) {
        val display =
            displays.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
                .firstOrNull { it.displayId != activity.windowManager.defaultDisplay.displayId && it.isValid }
        if (info == null || !VehicleProjectionState.secondaryEnabled(activity) || display == null) {
            presentation?.dismiss()
            presentation = null
            presentationDisplay = -1
            VehicleProjectionState.secondaryStatus.value =
                when {
                    !VehicleProjectionState.secondaryEnabled(activity) -> "Android 副屏未开启"
                    display == null -> "未发现可用 Android 副屏"
                    else -> "副屏等待导航数据"
                }
            return
        }
        if (presentationDisplay != display.displayId || presentation?.isShowing != true) {
            presentation?.dismiss()
            try {
                presentation = ClusterPresentation(activity, display, info).also { it.show() }
                presentationDisplay = display.displayId
                VehicleProjectionState.secondaryStatus.value = "导航副屏：${display.name} (${display.displayId})"
            } catch (error: RuntimeException) {
                presentation = null
                VehicleProjectionState.secondaryStatus.value = "副屏打开失败：${error.message}"
            }
        } else {
            presentation?.update(info)
        }
    }

    fun close() {
        closed = true
        job.cancel()
        displays.unregisterDisplayListener(displayListener)
        presentation?.dismiss()
        // stop() clears callbacks/retry handlers, so a destroyed Activity cannot republish stale data.
        synchronized(outputLock) { stopOutput() }
    }
}
