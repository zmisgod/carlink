package com.carlink.logging

import androidx.compose.ui.graphics.Color

/**
 * User-facing policy bundles that reconfigure [Logger] tag/level filtering.
 *
 * Each entry is presented in the LogsTab settings UI as a color-tagged chip
 * (displayName + description). User-chosen preset is persisted via
 * [LoggingPreferences] and re-applied at cold start.
 *
 * ORDERING IS LOAD-BEARING: entries are persisted by `ordinal` in
 * [LoggingPreferences.KEY_LOG_LEVEL]. Reordering, inserting non-terminal entries,
 * or removing any entry will silently reinterpret every user's saved preference.
 * Append new entries at the end only. A future-proof fix is to migrate persistence
 * to stringPreferencesKey + LogPreset.name.
 *
 * PROTO_UNKNOWN note: [Logger.Tags.PROTO_UNKNOWN] bypasses all filtering in
 * [Logger.log], so SILENT and tag-clearing presets do NOT silence protocol-anomaly
 * messages — that is intentional, for post-mortem forensics.
 */
enum class LogPreset(
    val displayName: String,
    val description: String,
    val color: Color,
) {
    SILENT(
        displayName = "静默",
        description = "仅记录错误",
        color = Color(0xFFE57373), // red[300]
    ),
    MINIMAL(
        displayName = "精简",
        description = "错误和警告",
        color = Color(0xFFFFB74D), // orange[300]
    ),
    NORMAL(
        displayName = "常规",
        description = "常规运行日志",
        color = Color(0xFF64B5F6), // blue[300]
    ),
    DEBUG(
        displayName = "调试",
        description = "完整调试日志（不含原始数据）",
        color = Color(0xFF4DD0E1), // cyan[300]
    ),
    PERFORMANCE(
        displayName = "性能",
        description = "仅记录性能指标",
        color = Color(0xFFBA68C8), // purple[300]
    ),
    RX_MESSAGES(
        displayName = "适配器消息",
        description = "原始消息（不含音视频）",
        color = Color(0xFF7986CB), // indigo[300]
    ),
    VIDEO_ONLY(
        displayName = "仅视频",
        description = "视频事件和原始视频数据",
        color = Color(0xFF81C784), // green[300]
    ),
    AUDIO_ONLY(
        displayName = "仅音频",
        description = "音频事件和原始音频数据",
        color = Color(0xFFFFD54F), // amber[300]
    ),
    PIPELINE_DEBUG(
        displayName = "音视频链路调试",
        description = "完整音视频链路调试信息",
        color = Color(0xFFA5D6A7), // green[200]
    ),
    CLUSTER_MEDIA(
        displayName = "仪表盘",
        description = "媒体和导航元数据",
        color = Color(0xFFBCAAA4), // brown[200]
    ),
    ;

    companion object {
        // Out-of-range fallback = SILENT (same as the LoggingPreferences cold-start
        // default). Cannot detect enum reordering — see class KDoc ordering hazard.
        fun fromIndex(index: Int): LogPreset = entries.getOrElse(index) { SILENT }
    }
}

/**
 * Apply this preset to the Logger system.
 *
 * Required call order inside each branch (do not reorder — step 3 depends on step 2):
 *  1. setLogLevel for all four [Logger.LogLevel] values (contiguous range; see
 *     [Logger.recalculateMinLevel] fast-path assumption).
 *  2. Reset the tag baseline via [Logger.enableAllTags] or [Logger.disableAllTags].
 *  3. Selectively flip specific tags via [Logger.setTagsEnabled].
 *  4. Set [Logger.setDebugLoggingEnabled]:
 *     - BuildConfig.DEBUG: for general-use presets (SILENT, MINIMAL, NORMAL) — respect
 *       the build variant; release builds get zero-cost inline debug helpers.
 *     - true: for user-requested diagnostic presets (DEBUG, PERFORMANCE, RX_MESSAGES,
 *       VIDEO_ONLY, AUDIO_ONLY, PIPELINE_DEBUG, CLUSTER_MEDIA) — the user explicitly
 *       asked for diagnostics, so force inline helpers on in release builds too.
 */
fun LogPreset.apply() {
    when (this) {
        LogPreset.SILENT -> {
            Logger.setLogLevel(Logger.LogLevel.DEBUG, false)
            Logger.setLogLevel(Logger.LogLevel.INFO, false)
            Logger.setLogLevel(Logger.LogLevel.WARN, false)
            Logger.setLogLevel(Logger.LogLevel.ERROR, true)
            Logger.disableAllTags()
            Logger.setDebugLoggingEnabled(com.carlink.BuildConfig.DEBUG)
        }

        LogPreset.MINIMAL -> {
            Logger.setLogLevel(Logger.LogLevel.DEBUG, false)
            Logger.setLogLevel(Logger.LogLevel.INFO, false)
            Logger.setLogLevel(Logger.LogLevel.WARN, true)
            Logger.setLogLevel(Logger.LogLevel.ERROR, true)
            Logger.enableAllTags()
            Logger.setTagsEnabled(
                listOf(Logger.Tags.SERIALIZE, Logger.Tags.TOUCH, Logger.Tags.AUDIO, Logger.Tags.MEDIA),
                false,
            )
            Logger.setDebugLoggingEnabled(com.carlink.BuildConfig.DEBUG)
        }

        LogPreset.NORMAL -> {
            Logger.setLogLevel(Logger.LogLevel.DEBUG, false)
            Logger.setLogLevel(Logger.LogLevel.INFO, true)
            Logger.setLogLevel(Logger.LogLevel.WARN, true)
            Logger.setLogLevel(Logger.LogLevel.ERROR, true)
            Logger.enableAllTags()
            Logger.setTagsEnabled(listOf(Logger.Tags.SERIALIZE, Logger.Tags.TOUCH, Logger.Tags.MEDIA), false)
            Logger.setDebugLoggingEnabled(com.carlink.BuildConfig.DEBUG)
        }

        LogPreset.DEBUG -> {
            Logger.setLogLevel(Logger.LogLevel.DEBUG, true)
            Logger.setLogLevel(Logger.LogLevel.INFO, true)
            Logger.setLogLevel(Logger.LogLevel.WARN, true)
            Logger.setLogLevel(Logger.LogLevel.ERROR, true)
            Logger.enableAllTags()
            Logger.setTagsEnabled(listOf(Logger.Tags.VIDEO, Logger.Tags.AUDIO, Logger.Tags.USB_RAW), false)
            Logger.setDebugLoggingEnabled(true)
        }

        LogPreset.PERFORMANCE -> {
            Logger.setLogLevel(Logger.LogLevel.DEBUG, false)
            Logger.setLogLevel(Logger.LogLevel.INFO, true)
            Logger.setLogLevel(Logger.LogLevel.WARN, true)
            Logger.setLogLevel(Logger.LogLevel.ERROR, true)
            Logger.disableAllTags()
            Logger.setTagsEnabled(
                listOf(
                    Logger.Tags.USB,
                    Logger.Tags.ADAPTR,
                    Logger.Tags.PLATFORM,
                    Logger.Tags.VIDEO_PERF,
                    Logger.Tags.AUDIO_PERF,
                ),
                true,
            )
            Logger.setDebugLoggingEnabled(true)
        }

        LogPreset.RX_MESSAGES -> {
            Logger.setLogLevel(Logger.LogLevel.DEBUG, true)
            Logger.setLogLevel(Logger.LogLevel.INFO, true)
            Logger.setLogLevel(Logger.LogLevel.WARN, true)
            Logger.setLogLevel(Logger.LogLevel.ERROR, true)
            Logger.disableAllTags()
            Logger.setTagsEnabled(listOf(Logger.Tags.USB_RAW, Logger.Tags.USB, Logger.Tags.ADAPTR), true)
            Logger.setDebugLoggingEnabled(true)
        }

        LogPreset.VIDEO_ONLY -> {
            Logger.setLogLevel(Logger.LogLevel.DEBUG, true)
            Logger.setLogLevel(Logger.LogLevel.INFO, true)
            Logger.setLogLevel(Logger.LogLevel.WARN, true)
            Logger.setLogLevel(Logger.LogLevel.ERROR, true)
            Logger.disableAllTags()
            Logger.setTagsEnabled(
                listOf(
                    Logger.Tags.VIDEO,
                    Logger.Tags.H264_RENDERER,
                    Logger.Tags.VIDEO_PERF,
                    Logger.Tags.ADAPTR,
                    Logger.Tags.USB,
                    Logger.Tags.PLATFORM,
                    Logger.Tags.CONFIG,
                ),
                true,
            )
            Logger.setDebugLoggingEnabled(true)
        }

        LogPreset.AUDIO_ONLY -> {
            Logger.setLogLevel(Logger.LogLevel.DEBUG, true)
            Logger.setLogLevel(Logger.LogLevel.INFO, true)
            Logger.setLogLevel(Logger.LogLevel.WARN, true)
            Logger.setLogLevel(Logger.LogLevel.ERROR, true)
            Logger.disableAllTags()
            Logger.setTagsEnabled(
                listOf(
                    Logger.Tags.AUDIO,
                    Logger.Tags.AUDIO_DEBUG,
                    Logger.Tags.MIC,
                    Logger.Tags.AUDIO_PERF,
                    Logger.Tags.ADAPTR,
                    Logger.Tags.USB,
                    Logger.Tags.PLATFORM,
                    Logger.Tags.CONFIG,
                ),
                true,
            )
            Logger.setDebugLoggingEnabled(true)
        }

        LogPreset.PIPELINE_DEBUG -> {
            Logger.setLogLevel(Logger.LogLevel.DEBUG, true)
            Logger.setLogLevel(Logger.LogLevel.INFO, true)
            Logger.setLogLevel(Logger.LogLevel.WARN, true)
            Logger.setLogLevel(Logger.LogLevel.ERROR, true)
            Logger.disableAllTags()
            Logger.setTagsEnabled(
                listOf(
                    Logger.Tags.VIDEO,
                    Logger.Tags.VIDEO_USB,
                    Logger.Tags.VIDEO_RING_BUFFER,
                    Logger.Tags.VIDEO_CODEC,
                    Logger.Tags.VIDEO_SURFACE,
                    Logger.Tags.VIDEO_PERF,
                    Logger.Tags.H264_RENDERER,
                    Logger.Tags.AUDIO,
                    Logger.Tags.AUDIO_PERF,
                    Logger.Tags.MIC,
                    Logger.Tags.USB,
                    Logger.Tags.ADAPTR,
                ),
                true,
            )
            // Override release-build restriction for field diagnostics
            Logger.setDebugLoggingEnabled(true)
        }

        LogPreset.CLUSTER_MEDIA -> {
            Logger.setLogLevel(Logger.LogLevel.DEBUG, true)
            Logger.setLogLevel(Logger.LogLevel.INFO, true)
            Logger.setLogLevel(Logger.LogLevel.WARN, true)
            Logger.setLogLevel(Logger.LogLevel.ERROR, true)
            Logger.disableAllTags()
            Logger.setTagsEnabled(
                listOf(
                    Logger.Tags.MEDIA,
                    Logger.Tags.MEDIA_SESSION,
                    Logger.Tags.NAVI,
                    Logger.Tags.CLUSTER,
                    Logger.Tags.ICON_SHIM,
                    Logger.Tags.ADAPTR,
                ),
                true,
            )
            Logger.setDebugLoggingEnabled(true)
        }
    }
}
