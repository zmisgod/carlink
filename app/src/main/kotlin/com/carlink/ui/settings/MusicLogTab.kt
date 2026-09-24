package com.carlink.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carlink.media.MusicDiagnosticLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun MusicLogTabContent() {
    val state by MusicDiagnosticLog.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf("") }
    var exportSnapshot by remember { mutableStateOf("") }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            val text = exportSnapshot
            scope.launch {
                result = withContext(Dispatchers.IO) {
                    try {
                        checkNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter().use { it.write(text) }
                        "日志已导出"
                    } catch (error: Exception) {
                        "导出失败：${error.message}"
                    }
                }
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("音乐日志", style = MaterialTheme.typography.headlineSmall)
            Switch(checked = state.enabled, onCheckedChange = MusicDiagnosticLog::setEnabled)
        }
        Text(if (state.enabled) "正在记录 · 切回投屏后继续记录" else "记录已关闭 · 已有日志仍可查看")
        Text("先开启，再打开 Spotify 连续切歌，回来关闭开关查看。RX JSON 是适配器传来的原始音乐消息；MERGED 是应用合并结果。JSON 没有某字段表示这条消息没传，空字符串和 null 会原样显示。",
            style = MaterialTheme.typography.bodySmall)
        Text("仅保留内存中最近 ${MusicDiagnosticLog.MAX_ENTRIES} 条，重启应用后清空。当前 ${state.entries.size} 条，已淘汰 ${state.dropped} 条。",
            style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = {
                clipboard.setText(AnnotatedString(MusicDiagnosticLog.exportText().takeLast(200000)))
                result = "已复制最近日志（最多 20 万字符）；完整内容请导出"
            }, enabled = state.entries.isNotEmpty()) { Text("复制日志") }
            Button(onClick = {
                exportSnapshot = MusicDiagnosticLog.exportText()
                exporter.launch("carlink-music-${System.currentTimeMillis()}.txt")
            }, enabled = state.entries.isNotEmpty()) { Text("导出日志") }
            Button(onClick = { MusicDiagnosticLog.clear(); result = "已清空" }) { Text("清空") }
        }
        if (result.isNotEmpty()) Text(result, style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.entries.isEmpty()) item { Text("暂无音乐消息。开启后请播放音乐或切换歌曲。") }
            items(state.entries.asReversed(), key = { it.id }) { entry ->
                SelectionContainer {
                    Text(entry.render(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider()
            }
        }
    }
}
