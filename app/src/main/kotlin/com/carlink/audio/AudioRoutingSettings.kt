package com.carlink.audio

import android.Manifest
import android.content.SharedPreferences
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carlink.CarlinkManager
import com.carlink.protocol.InputKeyAction
import com.carlink.protocol.StreamPurpose
import kotlin.math.log10

@Composable
fun AudioRoutingSettings(manager: CarlinkManager?) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val prefs = remember { AudioRoutePreferences.store(context) }
    val session = remember { AudioDiagnosticSession(context) }
    var revision by remember { mutableIntStateOf(0) }
    val audioManager = remember { context.getSystemService(AudioManager::class.java) }
    DisposableEffect(session, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) session.stop() }
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        val devices =
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                    revision++
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                    session.stop()
                    revision++
                }
            }
        lifecycle.addObserver(observer)
        prefs.registerOnSharedPreferenceChangeListener(listener)
        audioManager.registerAudioDeviceCallback(devices, Handler(Looper.getMainLooper()))
        onDispose {
            lifecycle.removeObserver(observer)
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            audioManager.unregisterAudioDeviceCallback(devices)
            session.close()
        }
    }
    val inputs = remember(revision) { AudioRoutePreferences.devices(context, true) }
    val outputs = remember(revision) { AudioRoutePreferences.devices(context, false) }
    val message by session.message.collectAsStateWithLifecycle()
    val playing by session.playing.collectAsStateWithLifecycle()
    val testingMic by session.testingMic.collectAsStateWithLifecycle()
    val meters by AudioDiagnosticsState.meters.collectAsStateWithLifecycle()
    val lastError by AudioDiagnosticsState.lastInputError.collectAsStateWithLifecycle()
    val inputStatus by AudioDiagnosticsState.input.collectAsStateWithLifecycle()
    val outputStatus by AudioDiagnosticsState.output.collectAsStateWithLifecycle()
    val siri by AudioDiagnosticsState.siri.collectAsStateWithLifecycle()
    val bytes by AudioDiagnosticsState.microphoneBytesSent.collectAsStateWithLifecycle()
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                session.microphone()
            } else {
                session.message.value = "未获得麦克风权限，无法录音测试"
            }
        }
    var previewPurpose by remember { mutableStateOf<StreamPurpose?>(null) }
    var showMicOptions by remember { mutableStateOf(false) }
    var showSiri by remember { mutableStateOf(false) }
    val recording by AudioDiagnosticsState.recording.collectAsStateWithLifecycle()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("音频诊断", style = MaterialTheme.typography.titleMedium)
            Text("为每类声音选择输出设备，再点击试听。选择自动保存并用于实际播放。")
            listOf(
                StreamPurpose.PHONE_CALL to "语音通话",
                StreamPurpose.RINGTONE to "铃声",
                StreamPurpose.NAVIGATION to "导航",
                StreamPurpose.MEDIA to "音乐",
            ).forEach { (purpose, title) ->
                val key = AudioRoutePreferences.outputKey(purpose)
                val value = remember(revision) { AudioRoutePreferences.savedDeviceKey(context, key) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        AudioChoice(title, value, listOf("" to "系统自动") + outputs.map { AudioRoutePreferences.key(it) to AudioRoutePreferences.label(it) }) {
                            session.stop()
                            prefs.edit { putString(key, it) }
                        }
                    }
                    val active = playing && previewPurpose == purpose
                    Button(onClick = {
                        if (active) {
                            session.stop()
                        } else {
                            previewPurpose = purpose
                            session.play(purpose)
                        }
                    }, enabled = !testingMic && (!playing || active)) { Text(if (active) "停止" else "试听") }
                }
                if (previewPurpose == purpose) Text(message, style = MaterialTheme.typography.bodySmall)
            }
            AudioChoice("播放声道", prefs.getInt(AudioRoutePreferences.OUTPUT_CHANNEL, 0).toString(), listOf("0" to "双声道", "1" to "仅左声道", "2" to "仅右声道")) {
                session.stop()
                prefs.edit { putInt(AudioRoutePreferences.OUTPUT_CHANNEL, it.toInt()) }
            }
            Text("每次试听 6 秒：左声道 → 右声道 → 双声道，各 2 秒。语音通话的设备选择也用于 Siri。", style = MaterialTheme.typography.bodySmall)
            Text("车机可能调整实际输出设备；蓝牙直连手机的音乐不经过本应用。", style = MaterialTheme.typography.bodySmall)
            Text(outputStatus, style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    AudioChoice(
                        "麦克风输入",
                        AudioRoutePreferences.savedDeviceKey(context, AudioRoutePreferences.INPUT),
                        listOf("" to "系统自动") +
                            inputs.map {
                                AudioRoutePreferences.key(it) to AudioRoutePreferences.label(it)
                            },
                    ) {
                        session.stop()
                        prefs.edit { putString(AudioRoutePreferences.INPUT, it) }
                    }
                }
                Button(onClick = {
                    previewPurpose = null
                    if (testingMic) session.stop() else permission.launch(Manifest.permission.RECORD_AUDIO)
                }, enabled = !playing) { Text(if (testingMic) "停止声波" else "显示声波") }
            }
            Text(if (recording) "正在采集，请说话观察下方声波" else "点击“显示声波”后说话，最长测试 60 秒")
            if (previewPurpose == null) Text(message, style = MaterialTheme.typography.bodySmall)
            Text(inputStatus, style = MaterialTheme.typography.bodySmall)
            if (lastError.isNotBlank()) Text("最近采集 / 上传错误：$lastError", color = MaterialTheme.colorScheme.error)
            if (meters.isEmpty()) {
                val baseline = MaterialTheme.colorScheme.outline
                Canvas(Modifier.fillMaxWidth().height(80.dp)) {
                    drawLine(baseline, Offset(0f, size.height / 2), Offset(size.width, size.height / 2))
                }
                Text("等待麦克风声波", style = MaterialTheme.typography.bodySmall)
            }
            meters.forEachIndexed { index, meter ->
                Text("声道 ${index + 1} · RMS ${"%.1f".format(20 * log10(meter.rms.coerceAtLeast(0.000001f)))} dBFS · 峰值 ${"%.1f".format(meter.peak * 100)}%")
                val color = MaterialTheme.colorScheme.primary
                val baseline = MaterialTheme.colorScheme.outline
                Canvas(Modifier.fillMaxWidth().height(60.dp)) {
                    drawLine(baseline, Offset(0f, size.height / 2), Offset(size.width, size.height / 2))
                    val path = Path()
                    meter.waveform.forEachIndexed { point, amplitude ->
                        val x = point * size.width / (meter.waveform.size - 1).coerceAtLeast(1)
                        val y = size.height / 2 - (amplitude * 4).coerceIn(-1f, 1f) * size.height / 2
                        if (point == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, color, style = Stroke(2f))
                }
            }
            Text("声波随说话变化表示已采集到声音。波形放大 4 倍，不保存录音。", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { showMicOptions = !showMicOptions }) { Text(if (showMicOptions) "收起麦克风声道设置" else "麦克风声道设置") }
            if (showMicOptions) {
                AudioChoice(
                    "麦克风采集源",
                    prefs.getInt(AudioRoutePreferences.SOURCE, MediaRecorder.AudioSource.VOICE_COMMUNICATION).toString(),
                    listOf(
                        MediaRecorder.AudioSource.VOICE_COMMUNICATION to "通话处理（VOICE_COMMUNICATION）",
                        MediaRecorder.AudioSource.MIC to "普通麦克风（MIC）",
                        MediaRecorder.AudioSource.VOICE_RECOGNITION to "语音识别（VOICE_RECOGNITION）",
                        MediaRecorder.AudioSource.UNPROCESSED to "未经处理（UNPROCESSED，需系统支持）",
                        MediaRecorder.AudioSource.DEFAULT to "系统默认（DEFAULT）",
                        MediaRecorder.AudioSource.CAMCORDER to "摄像录音（CAMCORDER）",
                    ).map { it.first.toString() to it.second },
                ) {
                    session.stop()
                    prefs.edit { putInt(AudioRoutePreferences.SOURCE, it.toInt()) }
                }
                val channels = AudioRoutePreferences.channels(context)
                val supported =
                    (listOf(1, 2) + (AudioRoutePreferences.selected(context, AudioRoutePreferences.INPUT)?.channelCounts?.toList() ?: emptyList())).filter {
                        it in 1..8
                    }.distinct().sorted()
                AudioChoice("采集声道数", channels.toString(), supported.map { it.toString() to "$it 声道" }) {
                    session.stop()
                    prefs.edit {
                        putInt(AudioRoutePreferences.CHANNELS, it.toInt())
                        putInt(AudioRoutePreferences.CHANNEL, 0)
                    }
                }
                AudioChoice(
                    "发送给 Siri 的声道",
                    AudioRoutePreferences.channel(context).toString(),
                    listOf("-1" to "所有声道平均混合") + (0 until channels).map { it.toString() to "声道 ${it + 1}" },
                ) {
                    session.stop()
                    prefs.edit { putInt(AudioRoutePreferences.CHANNEL, it.toInt()) }
                }
            }
            TextButton(onClick = { showSiri = !showSiri }) { Text(if (showSiri) "收起 Siri 链路检查" else "Siri 链路检查") }
            if (showSiri) {
                Button(onClick = {
                    session.stop()
                    manager?.sendInputKeyAction(InputKeyAction.SIRI)
                    manager?.sendInputKeyAction(InputKeyAction.SIRI, released = true)
                }, enabled = manager != null) { Text("发送 Siri 唤醒测试") }
                Text(siri)
                Text("本次成功发送麦克风 PCM：$bytes 字节")
            }
        }
    }
}

@Composable
private fun AudioChoice(
    label: String,
    selected: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }) { Text(options.firstOrNull { it.first == selected }?.second ?: "所选设备已断开：$selected") }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (value, text) ->
                    DropdownMenuItem(text = { Text(text) }, onClick = {
                        expanded = false
                        onSelect(value)
                    })
                }
            }
        }
    }
}
