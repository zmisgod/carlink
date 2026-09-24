package com.carlink.media

import com.carlink.protocol.MediaType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Opt-in, bounded in-memory capture. Never performs disk IO on the USB reader. */
object MusicDiagnosticLog {
    const val MAX_ENTRIES = 1000
    private const val MAX_TEXT = 16384

    data class Entry(val id: Long, val time: String, val kind: String, val text: String) {
        fun render() = "#$id $time [$kind]\n$text"
    }

    data class State(
        val enabled: Boolean = false,
        val entries: List<Entry> = emptyList(),
        val dropped: Long = 0,
    )

    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()
    private var sequence = 0L

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        mutable.value = mutable.value.copy(enabled = enabled)
    }

    @Synchronized
    fun clear() {
        mutable.value = State(enabled = mutable.value.enabled)
        sequence = 0L
    }

    fun recordFrame(subtype: Int, payload: ByteArray, end: Int) {
        if (!state.value.enabled) return
        val size = (end.coerceAtMost(payload.size) - 4).coerceAtLeast(0)
        when (MediaType.fromId(subtype)) {
            MediaType.DATA -> {
                // Capture before JSON parsing, retaining unknown fields, empty strings,
                // explicit nulls and even malformed JSON. Only wire NUL terminators removed.
                val count = size.coerceAtMost(MAX_TEXT)
                val raw = String(payload, 4, count, Charsets.UTF_8).trimEnd('\u0000')
                append("RX JSON", "subtype=$subtype bodyBytes=$size\n$raw" +
                    if (count < size) "\n[原文超过 $MAX_TEXT 字节，已截断]" else "")
            }
            MediaType.ALBUM_COVER, MediaType.ALBUM_COVER_AA ->
                append("RX COVER", "subtype=$subtype coverBytes=$size（仅记录大小）")
            MediaType.UNKNOWN -> {
                val hex = payload.drop(4).take(size.coerceAtMost(64)).joinToString(" ") { "%02X".format(it) }
                append("RX UNKNOWN", "subtype=$subtype bodyBytes=$size hexPreview=$hex")
            }
            else -> Unit // Navigation and phone calls are not music diagnostics.
        }
    }

    fun recordMerged(title: String?, artist: String?, album: String?, app: String?, duration: Long, position: Long) {
        if (!state.value.enabled) return
        val json = JSONObject()
            .put("title", title ?: JSONObject.NULL)
            .put("artist", artist ?: JSONObject.NULL)
            .put("album", album ?: JSONObject.NULL)
            .put("app", app ?: JSONObject.NULL)
            .put("duration", duration).put("position", position)
        append("MERGED", json.toString())
    }

    @Synchronized
    private fun append(kind: String, text: String) {
        val current = mutable.value
        if (!current.enabled) return
        val entry = Entry(++sequence, SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(Date()), kind,
            if (text.length > MAX_TEXT + 200) text.take(MAX_TEXT) + "\n[已截断]" else text)
        val overflow = (current.entries.size + 1 - MAX_ENTRIES).coerceAtLeast(0)
        mutable.value = current.copy(
            entries = current.entries.drop(overflow) + entry,
            dropped = current.dropped + overflow,
        )
    }

    fun exportText(): String {
        val snapshot = state.value
        return "Carlink 音乐日志（适配器接收数据）\n" +
            "RX JSON=原始音乐消息；MERGED=应用合并后的信息，不代表 DIM 已显示。\n" +
            "记录 ${snapshot.entries.size} 条；淘汰 ${snapshot.dropped} 条。\n\n" +
            snapshot.entries.joinToString("\n\n") { it.render() }
    }
}
