package com.carlink.vehicle

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun VehicleIntegrationSettings() {
    val context = LocalContext.current
    val artworkStatus by VehicleProjectionState.artworkStatus.collectAsStateWithLifecycle()
    val status by VehicleProjectionState.status.collectAsStateWithLifecycle()
    val secondaryStatus by VehicleProjectionState.secondaryStatus.collectAsStateWithLifecycle()
    val keyStatus by VehicleProjectionState.keyStatus.collectAsStateWithLifecycle()
    val lastKey by VehicleProjectionState.lastKey.collectAsStateWithLifecycle()
    var enabled by remember { mutableStateOf(VehicleProjectionState.enabled(context)) }
    var secondary by remember { mutableStateOf(VehicleProjectionState.secondaryEnabled(context)) }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            VehicleProjectionState.refresh.value++
        }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("吉利 / ECARX 仪表", style = MaterialTheme.typography.titleMedium)
            Row {
                Text("音乐与导航发布到 DIM", Modifier.weight(1f))
                Switch(enabled, onCheckedChange = {
                    enabled = it
                    VehicleProjectionState.setEnabled(context, it)
                })
            }
            Row {
                Text("导航同时显示到 Android 副屏", Modifier.weight(1f))
                Switch(secondary, onCheckedChange = {
                    secondary = it
                    VehicleProjectionState.setSecondaryEnabled(context, it)
                })
            }
            Text("导航需同时开启 仪表导航；副屏显示转向与距离。")
            Text("封面：公共 JPEG + 歌名 / 艺人 / 封面哈希标识")
            Text(secondaryStatus, style = MaterialTheme.typography.bodySmall)
            Text(status, style = MaterialTheme.typography.bodySmall)
            Text(artworkStatus, style = MaterialTheme.typography.bodySmall)
            Button(onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        VehicleProjectionState.refresh.value++
                    } else {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    "package:${context.packageName}".toUri(),
                                ),
                            )
                        }.onFailure {
                            VehicleProjectionState.status.value = "无法打开公共目录权限设置：${it.message}"
                        }
                    }
                } else {
                    permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }) { Text("公共封面目录：设置写入权限 / 重发") }
            Button(onClick = { VehicleProjectionState.keyReconnect.value++ }) { Text("重新连接按键监听") }
            Text(keyStatus, style = MaterialTheme.typography.bodySmall)
            Text(lastKey, style = MaterialTheme.typography.bodySmall)
            Text("点击下方操作的录入按钮，再按上一曲、下一曲、播放/暂停或微信键。微信键可映射到 Siri 等现有操作。")
        }
    }
}
