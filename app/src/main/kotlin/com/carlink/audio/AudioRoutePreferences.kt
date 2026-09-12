package com.carlink.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.core.content.edit
import com.carlink.protocol.StreamPurpose

/** Device IDs are session-local; persist type/address/name instead, and report missing devices. */
object AudioRoutePreferences {
    const val NAME = "carlink_audio_routes"
    const val MEDIA_OUTPUT = "media_output"
    const val VOICE_OUTPUT = "voice_output"
    const val RINGTONE_OUTPUT = "ringtone_output"
    const val NAVIGATION_OUTPUT = "navigation_output"
    const val INPUT = "input"
    const val SOURCE = "source"
    const val CHANNELS = "channels"
    const val CHANNEL = "channel"
    const val OUTPUT_CHANNEL = "output_channel"

    fun store(context: Context) = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun purposeLabel(purpose: StreamPurpose): String =
        when (purpose) {
            StreamPurpose.MEDIA -> "媒体音乐"
            StreamPurpose.SIRI -> "Siri 语音助手"
            StreamPurpose.NAVIGATION -> "导航"
            StreamPurpose.PHONE_CALL -> "电话通话"
            StreamPurpose.ALERT -> "系统提示"
            StreamPurpose.RINGTONE -> "来电铃声"
        }

    fun key(device: AudioDeviceInfo): String = "${device.type}|${device.address}|${device.productName}"

    fun label(device: AudioDeviceInfo?): String =
        device?.let {
            "${it.productName} · 类型=${it.type} · ${it.address.ifBlank { "编号=${it.id}" }} · ${it.channelCounts.joinToString("/")} 声道"
        } ?: "系统自动路由"

    fun devices(
        context: Context,
        input: Boolean,
    ): List<AudioDeviceInfo> =
        context.getSystemService(AudioManager::class.java)
            .getDevices(if (input) AudioManager.GET_DEVICES_INPUTS else AudioManager.GET_DEVICES_OUTPUTS).toList()

    fun selected(
        context: Context,
        setting: String,
    ): AudioDeviceInfo? {
        val value = savedDeviceKey(context, setting)
        if (value.isBlank()) return null
        return devices(context, setting == INPUT).firstOrNull { key(it) == value }
    }

    fun select(
        context: Context,
        setting: String,
        device: AudioDeviceInfo?,
    ) {
        store(context).edit { putString(setting, device?.let(::key).orEmpty()) }
    }

    fun attributes(purpose: StreamPurpose): Pair<Int, Int> =
        when (purpose) {
            StreamPurpose.MEDIA -> Pair(AudioAttributes.USAGE_MEDIA, AudioAttributes.CONTENT_TYPE_MUSIC)
            StreamPurpose.PHONE_CALL -> Pair(AudioAttributes.USAGE_VOICE_COMMUNICATION, AudioAttributes.CONTENT_TYPE_SPEECH)
            StreamPurpose.SIRI -> Pair(AudioAttributes.USAGE_ASSISTANT, AudioAttributes.CONTENT_TYPE_SPEECH)
            // ALERT (incoming-call ringtone): try USAGE_VOICE_COMMUNICATION_SIGNALLING
            // to attempt routing onto BUS_CALL_RING → vgroup 2 (RING) on GM AAOS.
            // Standard AOSP maps USAGE_NOTIFICATION_RINGTONE to CALL_RING context, but
            // GM's carAudioContexts.xml on this head unit remaps it to BUS_NOTIFICATION
            // (vgroup 6 SYSTEM) — not adjustable, not on the right physical bus.
            // SIGNALLING is the closest usage that semantically belongs to the telephony
            // group and may map to BUS_CALL_RING in GM's config without requiring a
            // Telecom ConnectionService. Fallback: USAGE_NOTIFICATION_RINGTONE.
            StreamPurpose.ALERT -> Pair(AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING, AudioAttributes.CONTENT_TYPE_SONIFICATION)
            StreamPurpose.RINGTONE -> Pair(AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING, AudioAttributes.CONTENT_TYPE_SONIFICATION)
            StreamPurpose.NAVIGATION -> Pair(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, AudioAttributes.CONTENT_TYPE_SPEECH)
        }

    fun outputKey(purpose: StreamPurpose): String =
        when (purpose) {
            StreamPurpose.SIRI, StreamPurpose.PHONE_CALL -> VOICE_OUTPUT
            StreamPurpose.ALERT, StreamPurpose.RINGTONE -> RINGTONE_OUTPUT
            StreamPurpose.NAVIGATION -> NAVIGATION_OUTPUT
            StreamPurpose.MEDIA -> MEDIA_OUTPUT
        }

    // Missing new settings inherit the old shared media route; explicit automatic stays automatic.
    internal fun resolveDeviceKey(
        setting: String,
        read: (String) -> String?,
    ): String =
        read(setting) ?: when (setting) {
            RINGTONE_OUTPUT, NAVIGATION_OUTPUT -> read(MEDIA_OUTPUT).orEmpty()
            else -> ""
        }

    fun savedDeviceKey(
        context: Context,
        setting: String,
    ): String = resolveDeviceKey(setting) { store(context).getString(it, null) }

    fun applyOutput(
        context: Context,
        track: AudioTrack,
        purpose: StreamPurpose,
    ): String {
        val setting = outputKey(purpose)
        val selected = selected(context, setting)
        val missing = savedDeviceKey(context, setting).isNotBlank() && selected == null
        val accepted = track.setPreferredDevice(selected)
        return "${purposeLabel(purpose)}：${if (missing) "所选设备已断开，回退系统；" else ""}请求=${label(selected)}，${if (accepted) "已接受请求" else "系统拒绝请求"}"
    }

    fun volume(
        context: Context,
        track: AudioTrack,
        volume: Float,
    ) {
        when (store(context).getInt(OUTPUT_CHANNEL, 0)) {
            1 -> track.setStereoVolume(volume, 0f)
            2 -> track.setStereoVolume(0f, volume)
            else -> track.setStereoVolume(volume, volume)
        }
    }

    fun micSource(context: Context): Int = store(context).getInt(SOURCE, MediaRecorder.AudioSource.VOICE_COMMUNICATION)

    fun channels(context: Context): Int = store(context).getInt(CHANNELS, 1).coerceIn(1, 8)

    fun channel(context: Context): Int = store(context).getInt(CHANNEL, 0).coerceIn(-1, channels(context) - 1)
}
