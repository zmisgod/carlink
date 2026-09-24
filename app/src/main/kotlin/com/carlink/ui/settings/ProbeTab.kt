package com.carlink.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carlink.bridge.ProbeReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
internal fun ProbeTabContent() {
    val state by ProbeReceiver.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("iOS / CarPlay 通信测试", style = MaterialTheme.typography.headlineSmall)
        Text("局域网测试：仅接收操作并回执，不执行车控。此测试不经过适配器 USB 通道。")
        Button(onClick = { scope.launch(Dispatchers.IO) { if (state.running) ProbeReceiver.stop() else ProbeReceiver.start() } }) {
            Text(if (state.running) "停止接收" else "开启测试接收")
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.running) {
            Text("在 iPhone 测试 App 填写能访问的地址与本次配对码。切回投屏后仍可接收；关闭应用后停止。")
            SelectionContainer {
                Text((state.addresses.ifEmpty { listOf("没有局域网 IPv4 地址，请先连接网络") } + "配对码：${state.token}").joinToString("\n"))
            }
        }
        Text("最近回执（最多 50 条）", style = MaterialTheme.typography.titleMedium)
        if (state.receipts.isEmpty()) Text("尚未收到测试操作")
        state.receipts.forEach { receipt -> SelectionContainer { Text(receipt, style = MaterialTheme.typography.bodySmall) } }
    }
}
