package com.carlink.ui.settings

import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carlink.CarlinkManager
import com.carlink.logging.logInfo
import com.carlink.logging.logWarn
import com.carlink.protocol.InputKeyAction
import com.carlink.ui.theme.AutomotiveDimens
import com.carlink.util.CustomHomeIconStore
import com.carlink.util.LogcatMonitorStatus
import com.carlink.util.WindowMetricsCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Adapter Configuration Dialog
 *
 * Scrollable popup dialog for configuring adapter initialization settings.
 * Designed to be extensible - new configuration options can be easily added.
 *
 * Structure:
 * - Header with icon and title
 * - Subtitle ("Changes require app restart…" — see HAZARD note at that string)
 * - SecondaryTabRow with Audio / Visual / Keys / Misc tabs (ephemeral state via remember)
 * - Scrollable content area whose body switches on the selected tab
 * - Footer with Cancel / Default / Apply buttons (tiered restart strategy at Apply)
 *
 * @param adapterConfigPreference DataStore-backed flows + setters for each config field.
 * @param carlinkManager Current manager (nullable before connection); used by Tier 3 stop()
 *   and by Reboot Adapter action.
 * @param currentDisplayMode Drives usable-width/height calculation for resolution options.
 * @param onDismiss Invoked on Cancel / Default / Apply completion / outside tap.
 * @param onReinitAdapter Tier-2 callback. NAME IS MISLEADING: MainActivity implements this
 *   as `reinitializeForDisplayMode`, i.e. a full CarlinkManager rebuild — not just an
 *   adapter re-init. See MainActivity for actual behavior.
 */
@Composable
fun AdapterConfigurationDialog(
    adapterConfigPreference: AdapterConfigPreference,
    carlinkManager: CarlinkManager?,
    currentDisplayMode: DisplayMode,
    onDismiss: () -> Unit,
    onReinitAdapter: () -> Unit = {},
    inputKeyMappings: Map<Int, InputKeyAction> = emptyMap(),
    inputKeyCaptureAction: InputKeyAction? = null,
    onStartInputKeyCapture: (InputKeyAction) -> Unit = {},
    onCaptureInputKey: (Int) -> Unit = {},
    onCancelInputKeyCapture: () -> Unit = {},
    onRemoveInputKeyMapping: (InputKeyAction) -> Unit = {},
    logcatMappings: List<LogcatKeyMapping> = emptyList(),
    logcatMonitorStatus: LogcatMonitorStatus = LogcatMonitorStatus.STOPPED,
    logcatReadLogsGranted: Boolean = false,
    onAddLogcatMapping: (String, InputKeyAction, LogcatTriggerMode) -> Unit = { _, _, _ -> },
    onRemoveLogcatMapping: (String) -> Unit = {},
) {
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    // Load saved values from preferences
    val savedAudioSource by adapterConfigPreference.audioSourceFlow.collectAsStateWithLifecycle(
        initialValue = AudioSourceConfig.DEFAULT,
    )
    val savedMicSource by adapterConfigPreference.micSourceFlow.collectAsStateWithLifecycle(
        initialValue = MicSourceConfig.DEFAULT,
    )
    val savedWifiBand by adapterConfigPreference.wifiBandFlow.collectAsStateWithLifecycle(
        initialValue = WiFiBandConfig.DEFAULT,
    )
    val savedMediaDelay by adapterConfigPreference.mediaDelayFlow.collectAsStateWithLifecycle(
        initialValue = MediaDelayConfig.DEFAULT,
    )
    val savedVideoResolution by adapterConfigPreference.videoResolutionFlow.collectAsStateWithLifecycle(
        initialValue = VideoResolutionConfig.AUTO,
    )
    val savedFps by adapterConfigPreference.fpsFlow.collectAsStateWithLifecycle(
        initialValue = FpsConfig.DEFAULT,
    )
    val savedHandDrive by adapterConfigPreference.handDriveFlow.collectAsStateWithLifecycle(
        initialValue = HandDriveConfig.DEFAULT,
    )
    val savedGpsForwarding by adapterConfigPreference.gpsForwardingFlow.collectAsStateWithLifecycle(
        initialValue = false,
    )
    val savedClusterNavigation by adapterConfigPreference.clusterNavigationFlow.collectAsStateWithLifecycle(
        initialValue = false,
    )

    // Get usable display dimensions based on current display mode
    // FULLSCREEN_IMMERSIVE: Use full display bounds (bars are hidden)
    // Other modes: Subtract system bar insets from bounds
    val context = LocalContext.current

    val savedCustomHomeIcon = remember { CustomHomeIconStore.loadBytes(context) }
    var selectedCustomHomeIcon by remember { mutableStateOf(savedCustomHomeIcon) }
    val iconPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                scope.launch(Dispatchers.IO) {
                    val bytes = CustomHomeIconStore.readUri(context, uri)
                    if (bytes != null) {
                        withContext(Dispatchers.Main.immediate) {
                            selectedCustomHomeIcon = bytes
                        }
                    }
                }
            }
        }
    val customIconPreview =
        remember(selectedCustomHomeIcon) {
            selectedCustomHomeIcon?.let(::decodeIconPreview)
        }
    var captureKeyCode by remember { mutableStateOf<Int?>(null) }
    val activity = context as? ComponentActivity
    val (usableWidth, usableHeight) =
        if (activity != null) {
            // WindowMetricsCompat falls back to getRealMetrics + WindowInsetsCompat on
            // API 29 (AAOS 10); the API 30+ path is unchanged.
            val windowManager = activity.windowManager
            val bounds = WindowMetricsCompat.displayBounds(windowManager)
            val windowInsets =
                WindowMetricsCompat.stableWindowInsets(windowManager, activity.window.decorView)

            when (currentDisplayMode) {
                DisplayMode.FULLSCREEN_IMMERSIVE -> {
                    // Full display - no insets subtracted
                    Pair(bounds.width() and 1.inv(), bounds.height() and 1.inv())
                }

                DisplayMode.STATUS_BAR_HIDDEN -> {
                    // Only subtract navigation bar (bottom), not status bar
                    val insets =
                        windowInsets.getInsetsIgnoringVisibility(
                            WindowInsetsCompat.Type.navigationBars(),
                        )
                    val w = bounds.width() - insets.left - insets.right
                    val h = bounds.height() - insets.bottom
                    Pair(w and 1.inv(), h and 1.inv())
                }

                DisplayMode.NAV_BAR_HIDDEN -> {
                    // Only subtract status bar (top), not navigation bar
                    val insets =
                        windowInsets.getInsetsIgnoringVisibility(
                            WindowInsetsCompat.Type.statusBars(),
                        )
                    val w = bounds.width()
                    val h = bounds.height() - insets.top
                    Pair(w and 1.inv(), h and 1.inv())
                }

                DisplayMode.SYSTEM_UI_VISIBLE -> {
                    // Subtract all system bar insets
                    val insets =
                        windowInsets.getInsetsIgnoringVisibility(
                            WindowInsetsCompat.Type.systemBars() or
                                WindowInsetsCompat.Type.displayCutout(),
                        )
                    val w = bounds.width() - insets.left - insets.right
                    val h = bounds.height() - insets.top - insets.bottom
                    Pair(w and 1.inv(), h and 1.inv())
                }
            }
        } else {
            Pair(0, 0)
        }
    val resolutionOptions =
        if (usableWidth > 0 && usableHeight > 0) {
            VideoResolutionConfig.calculateOptions(usableWidth, usableHeight)
        } else {
            emptyList()
        }

    // Local state for editing - allows cancel without saving
    var selectedAudioSource by remember { mutableStateOf(savedAudioSource) }
    var selectedMicSource by remember { mutableStateOf(savedMicSource) }
    var selectedWifiBand by remember { mutableStateOf(savedWifiBand) }
    var selectedMediaDelay by remember { mutableStateOf(savedMediaDelay) }
    var selectedVideoResolution by remember { mutableStateOf(savedVideoResolution) }
    var selectedFps by remember { mutableStateOf(savedFps) }
    var selectedHandDrive by remember { mutableStateOf(savedHandDrive) }
    var selectedGpsForwarding by remember { mutableStateOf(savedGpsForwarding) }
    var selectedClusterNavigation by remember { mutableStateOf(savedClusterNavigation) }

    // Sync local state when saved value loads (for initial load).
    // KNOWN THEORETICAL RACE (flow-shadow): any subsequent DataStore emission for one of these
    // keys re-runs the LaunchedEffect and OVERWRITES the user's in-flight selectedXxx choice.
    // Low probability in practice — DataStore only emits when something else writes — but a
    // concurrent write from elsewhere (e.g. resetToDefaults, external setter) will clobber
    // edits the user has made in the open dialog. Not fixed here; documented only.
    LaunchedEffect(savedAudioSource) { selectedAudioSource = savedAudioSource }
    LaunchedEffect(savedMicSource) { selectedMicSource = savedMicSource }
    LaunchedEffect(savedWifiBand) { selectedWifiBand = savedWifiBand }
    LaunchedEffect(savedMediaDelay) { selectedMediaDelay = savedMediaDelay }
    LaunchedEffect(savedVideoResolution) { selectedVideoResolution = savedVideoResolution }
    LaunchedEffect(savedFps) { selectedFps = savedFps }
    LaunchedEffect(savedHandDrive) { selectedHandDrive = savedHandDrive }
    LaunchedEffect(savedGpsForwarding) { selectedGpsForwarding = savedGpsForwarding }
    LaunchedEffect(savedClusterNavigation) { selectedClusterNavigation = savedClusterNavigation }

    // Track if any changes were made.
    // NOTE (stale): the old one-liner here claimed "All adapter configuration changes require
    // app restart" — that is no longer true. Apply uses a tiered strategy (see Apply block
    // ~line 828): only Misc changes (GPS/WiFi/ClusterNav) force a Tier-3 app kill; Audio/Visual
    // changes take the Tier-2 in-place reinit path. See also the misleading subtitle at ~line 273.
    val hasChanges =
        selectedAudioSource != savedAudioSource ||
            selectedMicSource != savedMicSource ||
            selectedWifiBand != savedWifiBand ||
            selectedMediaDelay != savedMediaDelay ||
            selectedVideoResolution != savedVideoResolution ||
            selectedFps != savedFps ||
            selectedHandDrive != savedHandDrive ||
            selectedGpsForwarding != savedGpsForwarding ||
            selectedClusterNavigation != savedClusterNavigation ||
            !selectedCustomHomeIcon.sameBytesAs(savedCustomHomeIcon)
    val customHomeIconChanged = !selectedCustomHomeIcon.sameBytesAs(savedCustomHomeIcon)

    // Responsive dialog width - 60% of container width, clamped between 320dp and 600dp
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val containerWidthDp = with(density) { windowInfo.containerSize.width.toDp() }
    val dialogMaxWidth = (containerWidthDp * 0.6f).coerceIn(320.dp, 600.dp)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier =
                Modifier
                    .widthIn(max = dialogMaxWidth)
                    .onPreviewKeyEvent { event ->
                        val keyCode = event.nativeKeyEvent.keyCode
                        if (inputKeyCaptureAction != null &&
                            event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                            event.nativeKeyEvent.repeatCount == 0
                        ) {
                            captureKeyCode = keyCode
                            onCaptureInputKey(keyCode)
                            true
                        } else {
                            // Consume the release belonging to the capture gesture.
                            event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_UP &&
                                captureKeyCode == keyCode
                        }
                    },
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
            ) {
                // Header with icon and title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.SettingsInputComponent,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "适配器设置",
                        style =
                            MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Subtitle.
                // HAZARD (stale UX copy — misleading to end users): this string is pending a
                // product-copy update. Audio/Visual-only changes take the Tier-2 in-place
                // reinit path (no app restart). Only Misc changes (GPS/WiFi/ClusterNav)
                // actually restart the app. DO NOT edit the string here without product sign-off.
                Text(
                    text = "更改需要重启应用后生效",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Tab bar for configuration categories
                var selectedTabIndex by remember { mutableIntStateOf(0) }

                SecondaryTabRow(selectedTabIndex = selectedTabIndex) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = { Text("音频") },
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = { Text("画面") },
                    )
                    Tab(
                        selected = selectedTabIndex == 2,
                        onClick = { selectedTabIndex = 2 },
                        text = { Text("按键") },
                    )
                    Tab(
                        selected = selectedTabIndex == 3,
                        onClick = { selectedTabIndex = 3 },
                        text = { Text("其他") },
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable content area — shows settings for selected tab
                Column(
                    modifier =
                        Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (selectedTabIndex) {
                        // ===== Audio Tab =====
                        0 -> {
                            // Audio Source Configuration Option
                            ConfigurationOptionCard(
                                title = "音频来源",
                                description = "选择手机音频的播放方式",
                                icon = Icons.AutoMirrored.Filled.VolumeUp,
                            ) {
                                // Audio source selection buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    // Bluetooth button
                                    AudioSourceButton(
                                        label = "蓝牙",
                                        icon = Icons.Default.Bluetooth,
                                        isSelected = selectedAudioSource == AudioSourceConfig.BLUETOOTH,
                                        onClick = { selectedAudioSource = AudioSourceConfig.BLUETOOTH },
                                        modifier = Modifier.weight(1f),
                                    )

                                    // Adapter button
                                    AudioSourceButton(
                                        label = "适配器",
                                        icon = Icons.Default.Usb,
                                        isSelected = selectedAudioSource == AudioSourceConfig.ADAPTER,
                                        onClick = { selectedAudioSource = AudioSourceConfig.ADAPTER },
                                        modifier = Modifier.weight(1f),
                                    )
                                }

                                // Current selection indicator
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text =
                                        when (selectedAudioSource) {
                                            AudioSourceConfig.BLUETOOTH -> "手机通过蓝牙连接车载音响"
                                            AudioSourceConfig.ADAPTER -> "通过 USB 和本应用播放音频"
                                        },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.primary,
                                )
                            }

                            // Microphone Source Configuration
                            ConfigurationOptionCard(
                                title = "麦克风来源",
                                description = "选择语音输入使用的麦克风",
                                icon = Icons.Default.Mic,
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    AudioSourceButton(
                                        label = "车机",
                                        icon = Icons.Default.Radio,
                                        isSelected = selectedMicSource == MicSourceConfig.APP,
                                        onClick = { selectedMicSource = MicSourceConfig.APP },
                                        modifier = Modifier.weight(1f),
                                    )
                                    AudioSourceButton(
                                        label = "手机",
                                        icon = Icons.Default.PhoneAndroid,
                                        isSelected = selectedMicSource == MicSourceConfig.PHONE,
                                        onClick = { selectedMicSource = MicSourceConfig.PHONE },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text =
                                        when (selectedMicSource) {
                                            MicSourceConfig.APP -> "通过车机系统采集麦克风声音"
                                            MicSourceConfig.PHONE -> "由连接的手机采集语音"
                                        },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.primary,
                                )
                            }

                            com.carlink.audio.AudioRoutingSettings(carlinkManager)

                            // Call Quality — UI removed (not the whole feature).
                            // VESTIGIAL / UI-DEAD: CallQualityConfig enum, setter/getter, ConfigKey,
                            // MessageSerializer.addChangedSettings branch, and ProGuard keep rule
                            // all still exist (see AdapterConfigPreference.kt:117-118). The
                            // firmware bug (CMD_BOX_INFO callQuality transform; value 2 (24kHz)
                            // breaks adapter mic input buffer) is why it is hardcoded to 1 in
                            // BoxSettings and why the UI toggle was pulled.

                            // Media Delay Configuration
                            ConfigurationOptionCard(
                                title = "媒体延迟",
                                description = "适配器音频缓冲区大小",
                                icon = Icons.Default.Timer,
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    AudioSourceButton(
                                        label = "低",
                                        icon = Icons.Default.Timer,
                                        isSelected = selectedMediaDelay == MediaDelayConfig.LOW,
                                        onClick = { selectedMediaDelay = MediaDelayConfig.LOW },
                                        modifier = Modifier.weight(1f),
                                    )
                                    AudioSourceButton(
                                        label = "中",
                                        icon = Icons.Default.Timer,
                                        isSelected = selectedMediaDelay == MediaDelayConfig.MEDIUM,
                                        onClick = { selectedMediaDelay = MediaDelayConfig.MEDIUM },
                                        modifier = Modifier.weight(1f),
                                    )
                                    AudioSourceButton(
                                        label = "标准",
                                        icon = Icons.Default.Timer,
                                        isSelected = selectedMediaDelay == MediaDelayConfig.STANDARD,
                                        onClick = { selectedMediaDelay = MediaDelayConfig.STANDARD },
                                        modifier = Modifier.weight(1f),
                                    )
                                    AudioSourceButton(
                                        label = "高",
                                        icon = Icons.Default.Timer,
                                        isSelected = selectedMediaDelay == MediaDelayConfig.HIGH,
                                        onClick = { selectedMediaDelay = MediaDelayConfig.HIGH },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text =
                                        when (selectedMediaDelay) {
                                            MediaDelayConfig.LOW -> "300ms — Lowest latency, may glitch on poor USB"
                                            MediaDelayConfig.MEDIUM -> "500ms — Balanced latency and stability"
                                            MediaDelayConfig.STANDARD -> "1000ms — Firmware default (recommended)"
                                            MediaDelayConfig.HIGH -> "2000ms — Maximum buffer for problematic setups"
                                        },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.primary,
                                )
                            }
                        }

                        // ===== Visual Tab =====
                        1 -> {
                            // Video Resolution Configuration
                            //
                            // HAZARD — SILENT DROP under Tier 2 (see Apply block):
                            // MessageSerializer.addChangedSettings has NO branches for
                            // VIDEO_RESOLUTION or FPS (see AdapterConfigPreference.kt:355-362,
                            // 375-383). If the user changes ONLY resolution and/or FPS here
                            // (Visual tab, no Misc changes), Apply takes the Tier-2
                            // MINIMAL_PLUS_CHANGES path — the new values are persisted to
                            // DataStore but are NOT propagated to the adapter until a FULL
                            // init (version bump or cold start). User sees the toggle move
                            // but no visible effect. Do NOT fix here — fix in the serializer.
                            ConfigurationOptionCard(
                                title = "画面分辨率",
                                description = "选择 CarPlay 分辨率，Android Auto 不支持此设置。",
                                icon = Icons.Default.AspectRatio,
                            ) {
                                // Auto option button
                                val autoLabel =
                                    if (usableWidth > 0 && usableHeight > 0) {
                                        "自动（${usableWidth}x$usableHeight）"
                                    } else {
                                        "自动"
                                    }

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    // Auto button - full width
                                    ResolutionButton(
                                        label = autoLabel,
                                        isSelected = selectedVideoResolution.isAuto,
                                        isRecommended = true,
                                        onClick = { selectedVideoResolution = VideoResolutionConfig.AUTO },
                                        modifier = Modifier.fillMaxWidth(),
                                    )

                                    // Resolution options - 2x2 grid
                                    if (resolutionOptions.isNotEmpty()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            resolutionOptions.take(2).forEach { option ->
                                                ResolutionButton(
                                                    label = "${option.width}x${option.height}",
                                                    isSelected = selectedVideoResolution == option,
                                                    isRecommended = false,
                                                    onClick = { selectedVideoResolution = option },
                                                    modifier = Modifier.weight(1f),
                                                )
                                            }
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            resolutionOptions.drop(2).take(2).forEach { option ->
                                                ResolutionButton(
                                                    label = "${option.width}x${option.height}",
                                                    isSelected = selectedVideoResolution == option,
                                                    isRecommended = false,
                                                    onClick = { selectedVideoResolution = option },
                                                    modifier = Modifier.weight(1f),
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text =
                                        if (selectedVideoResolution.isAuto) {
                                            "使用检测到的屏幕分辨率"
                                        } else {
                                            "降低分辨率可减轻 GPU 负载"
                                        },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.primary,
                                )

                                // Android Auto warning for low resolutions
                                if (!selectedVideoResolution.isAuto && selectedVideoResolution.height < 720) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "提示：此分辨率下 Android Auto 将使用 800x480",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.error,
                                    )
                                }
                            }

                            // Frame Rate Configuration
                            // HAZARD: same silent-drop issue as Video Resolution above — FPS
                            // has no MessageSerializer.addChangedSettings branch, so a Tier-2
                            // reinit persists the new value to DataStore without pushing it
                            // to the adapter until a FULL init. See HAZARD above for details.
                            ConfigurationOptionCard(
                                title = "帧率",
                                description = "CarPlay / Android Auto 投屏帧率",
                                icon = Icons.Default.Speed,
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    AudioSourceButton(
                                        label = "30 FPS",
                                        icon = Icons.Default.Speed,
                                        isSelected = selectedFps == FpsConfig.FPS_30,
                                        onClick = { selectedFps = FpsConfig.FPS_30 },
                                        modifier = Modifier.weight(1f),
                                    )
                                    AudioSourceButton(
                                        label = "60 FPS",
                                        icon = Icons.Default.Speed,
                                        isSelected = selectedFps == FpsConfig.FPS_60,
                                        onClick = { selectedFps = FpsConfig.FPS_60 },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text =
                                        when (selectedFps) {
                                            FpsConfig.FPS_30 -> "可能略有卡顿，资源占用较低"
                                            FpsConfig.FPS_60 -> "动画更流畅，资源占用较高（默认）"
                                        },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.primary,
                                )
                            }

                            // Hand Drive Mode Configuration
                            ConfigurationOptionCard(
                                title = "驾驶位方向",
                                description = "设置投屏快捷栏显示在哪一侧。",
                                icon = Icons.Default.DirectionsCar,
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    AudioSourceButton(
                                        label = "左舵",
                                        icon = Icons.Default.DirectionsCar,
                                        isSelected = selectedHandDrive == HandDriveConfig.LEFT,
                                        onClick = { selectedHandDrive = HandDriveConfig.LEFT },
                                        modifier = Modifier.weight(1f),
                                    )
                                    AudioSourceButton(
                                        label = "右舵",
                                        icon = Icons.Default.DirectionsCar,
                                        isSelected = selectedHandDrive == HandDriveConfig.RIGHT,
                                        onClick = { selectedHandDrive = HandDriveConfig.RIGHT },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                // HAZARD — PLACEHOLDER COPY shipping to end users.
                                // Both strings are ambiguous/incorrect:
                                //   LEFT  -> "靠右行驶"  (LHD, but the copy
                                //           reads as a location hint rather than LHD label)
                                //   RIGHT -> "靠左行驶"  (joke placeholder)
                                // These must be replaced with proper user-facing copy before
                                // release. Do NOT change the strings here without product
                                // sign-off; only flagged for visibility.
                                Text(
                                    text =
                                        when (selectedHandDrive) {
                                            HandDriveConfig.LEFT -> "靠右行驶"
                                            HandDriveConfig.RIGHT -> "靠左行驶"
                                        },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.primary,
                                )
                            }

                            // CarPlay home icon is uploaded to the adapter as the
                            // 120/180/256px icon set plus /etc/oem_icon.png during
                            // the next session reinit.
                            ConfigurationOptionCard(
                                title = "CarPlay 主屏图标",
                                description = "使用自定义方形图片作为 CarPlay 主屏图标。",
                                icon = Icons.Default.Home,
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    if (customIconPreview != null) {
                                        Image(
                                            bitmap = customIconPreview,
                                            contentDescription = "已选择的 CarPlay 主屏图标",
                                            modifier = Modifier.size(72.dp).aspectRatio(1f),
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (selectedCustomHomeIcon == null) "内置图标" else "已选择自定义图标",
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        Text(
                                            text = "PNG 或 JPEG 图片，最大 4 MB",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = { iconPicker.launch(arrayOf("image/png", "image/jpeg", "image/*")) },
                                    ) {
                                        Icon(Icons.Default.Upload, contentDescription = "选择图片")
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("选择")
                                    }
                                    if (selectedCustomHomeIcon != null) {
                                        IconButton(
                                            onClick = { selectedCustomHomeIcon = null },
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "使用内置图标")
                                        }
                                    }
                                }
                            }
                        }

                        // ===== Keys Tab =====
                        2 -> {
                            com.carlink.vehicle.VehicleIntegrationSettings()
                            InputKeyMappingCard(
                                mappings = inputKeyMappings,
                                capturingAction = inputKeyCaptureAction,
                                onStartCapture = onStartInputKeyCapture,
                                onCancelCapture = onCancelInputKeyCapture,
                                onRemoveMapping = onRemoveInputKeyMapping,
                            )
                            LogcatKeyMappingCard(
                                mappings = logcatMappings,
                                monitorStatus = logcatMonitorStatus,
                                readLogsGranted = logcatReadLogsGranted,
                                onAddMapping = onAddLogcatMapping,
                                onRemoveMapping = onRemoveLogcatMapping,
                            )
                        }

                        // ===== Misc Tab =====
                        3 -> {

                            // GPS Forwarding Configuration
                            ConfigurationOptionCard(
                                title = "GPS 转发",
                                description = "转发车辆 GPS，iOS 不会将其用于导航",
                                icon = Icons.Default.LocationOn,
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    AudioSourceButton(
                                        label = "关闭",
                                        icon = Icons.Default.LocationOn,
                                        isSelected = !selectedGpsForwarding,
                                        onClick = { selectedGpsForwarding = false },
                                        modifier = Modifier.weight(1f),
                                    )
                                    AudioSourceButton(
                                        label = "开启",
                                        icon = Icons.Default.LocationOn,
                                        isSelected = selectedGpsForwarding,
                                        onClick = { selectedGpsForwarding = true },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text =
                                        if (selectedGpsForwarding) {
                                            "已开启：将车辆 GPS 转发到适配器"
                                        } else {
                                            "已关闭：手机使用自身 GPS"
                                        },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.primary,
                                )
                            }

                            // Cluster Navigation Configuration
                            ConfigurationOptionCard(
                                title = "仪表导航",
                                description = "在仪表中显示投屏导航的转向指引。",
                                icon = Icons.Default.Map,
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    AudioSourceButton(
                                        label = "关闭",
                                        icon = Icons.Default.Map,
                                        isSelected = !selectedClusterNavigation,
                                        onClick = { selectedClusterNavigation = false },
                                        modifier = Modifier.weight(1f),
                                    )
                                    AudioSourceButton(
                                        label = "已启用",
                                        icon = Icons.Default.Map,
                                        isSelected = selectedClusterNavigation,
                                        onClick = { selectedClusterNavigation = true },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text =
                                        if (selectedClusterNavigation) {
                                            "已开启：在仪表显示投屏导航（需重启）"
                                        } else {
                                            "已关闭：仪表由其他导航应用使用"
                                        },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.primary,
                                )
                            }

                            // WiFi Band Configuration
                            ConfigurationOptionCard(
                                title = "Wi-Fi 频段",
                                description = "选择适配器使用的无线频段。",
                                icon = Icons.Default.Wifi,
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    AudioSourceButton(
                                        label = "5 GHz",
                                        icon = Icons.Default.Speed,
                                        isSelected = selectedWifiBand == WiFiBandConfig.BAND_5GHZ,
                                        onClick = { selectedWifiBand = WiFiBandConfig.BAND_5GHZ },
                                        modifier = Modifier.weight(1f),
                                    )
                                    AudioSourceButton(
                                        label = "2.4 GHz",
                                        icon = Icons.Default.SignalCellularAlt,
                                        isSelected = selectedWifiBand == WiFiBandConfig.BAND_24GHZ,
                                        onClick = { selectedWifiBand = WiFiBandConfig.BAND_24GHZ },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text =
                                        when (selectedWifiBand) {
                                            WiFiBandConfig.BAND_5GHZ -> "速度更快，干扰更少（推荐）"
                                            WiFiBandConfig.BAND_24GHZ -> "备用选项（速度较慢，不推荐）"
                                        },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.primary,
                                )
                            }

                            // Adapter Reset Actions
                            Spacer(modifier = Modifier.height(8.dp))

                            var showRebootDialog by remember { mutableStateOf(false) }

                            FilledTonalButton(
                                onClick = { showRebootDialog = true },
                                enabled = carlinkManager != null,
                                modifier = Modifier.fillMaxWidth().height(AutomotiveDimens.ButtonMinHeight),
                                colors =
                                    ButtonDefaults.filledTonalButtonColors(
                                        containerColor = colorScheme.tertiaryContainer,
                                        contentColor = colorScheme.onTertiaryContainer,
                                    ),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RestartAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "重启适配器",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }

                            // Reboot Confirmation Dialog
                            if (showRebootDialog) {
                                AlertDialog(
                                    onDismissRequest = { showRebootDialog = false },
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.RestartAlt,
                                            contentDescription = null,
                                            tint = colorScheme.tertiary,
                                        )
                                    },
                                    title = { Text("重启适配器？") },
                                    text = {
                                        Text("适配器将重启，约 15 秒后自动重新连接。")
                                    },
                                    confirmButton = {
                                        TextButton(
                                            onClick = {
                                                showRebootDialog = false
                                                onDismiss()
                                                CoroutineScope(Dispatchers.IO).launch {
                                                    carlinkManager?.rebootAdapter()
                                                }
                                            },
                                        ) {
                                            Text("重启", color = colorScheme.tertiary)
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showRebootDialog = false }) {
                                            Text("取消")
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Footer with action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Cancel button
                    TextButton(
                        onClick = {
                            logWarn("[UI_ACTION] Adapter Config: Cancel button clicked", tag = "UI")
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("取消")
                    }

                    // Default button.
                    // HAZARD — INCONSISTENT with Apply: this path resets DataStore preferences
                    // but does NOT reinit the manager, kill the app, or call onReinitAdapter.
                    // The current session keeps running on the OLD in-memory AdapterConfig
                    // until some other trigger fires (version bump, cold start, later Apply).
                    // The log message below says "next session will run FULL init", which
                    // implies the user must restart manually — contrast Apply's auto-restart.
                    // Consider harmonizing the two flows (do not change here without approval).
                    TextButton(
                        onClick = {
                            logWarn(
                                "[UI_ACTION] Adapter Config: Reset to Defaults clicked" +
                                    " - next session will run FULL init",
                                tag = "UI",
                            )
                            scope.launch {
                                adapterConfigPreference.resetToDefaults()
                                withContext(Dispatchers.IO) { CustomHomeIconStore.clear(context) }
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("恢复默认")
                    }

                    // Apply button — tiered restart strategy:
                    // Misc settings (GPS, WiFi, Cluster Nav) → Tier 3: kill app + adapter reboot
                    // Audio/Visual settings only → Tier 2: in-place reinit (no app kill)
                    val miscChanged =
                        selectedGpsForwarding != savedGpsForwarding ||
                            selectedWifiBand != savedWifiBand ||
                            selectedClusterNavigation != savedClusterNavigation

                    Button(
                        onClick = {
                            logWarn(
                                "[UI_ACTION] Adapter Config: Apply clicked" +
                                    " - audio=$selectedAudioSource" +
                                    ", mic=$selectedMicSource" +
                                    ", wifi=$selectedWifiBand" +
                                    ", callQuality=1(hardcoded)" +
                                    ", mediaDelay=$selectedMediaDelay" +
                                    ", resolution=${selectedVideoResolution.toStorageString()}" +
                                    ", fps=${selectedFps.fps}" +
                                    ", handDrive=$selectedHandDrive" +
                                    ", gpsForwarding=$selectedGpsForwarding" +
                                    ", clusterNav=$selectedClusterNavigation" +
                                    ", tier=${if (miscChanged) "3-KILL" else "2-REINIT"}",
                                tag = "UI",
                            )
                            // NOTE: "callQuality=1(hardcoded)" in the log string above encodes
                            // the firmware-bug workaround as a literal. If the BoxSettings
                            // hardcode ever changes, this log will silently lie — keep in sync.
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    CustomHomeIconStore.saveBytes(context, selectedCustomHomeIcon)
                                }
                                if (customHomeIconChanged) {
                                    adapterConfigPreference.markCustomHomeIconChanged()
                                }
                                // Save all configuration
                                adapterConfigPreference.setAudioSource(selectedAudioSource)
                                adapterConfigPreference.setMicSource(selectedMicSource)
                                adapterConfigPreference.setWifiBand(selectedWifiBand)
                                adapterConfigPreference.setMediaDelay(selectedMediaDelay)
                                adapterConfigPreference.setVideoResolution(selectedVideoResolution)
                                adapterConfigPreference.setFps(selectedFps)
                                adapterConfigPreference.setHandDrive(selectedHandDrive)
                                adapterConfigPreference.setGpsForwarding(selectedGpsForwarding)
                                adapterConfigPreference.setClusterNavigation(selectedClusterNavigation)

                                if (miscChanged) {
                                    // Tier 3: Misc settings changed — always kills the app,
                                    // but ONLY GPS changes trigger an adapter reboot (see
                                    // `needsReboot` below). WiFi-band and cluster-nav changes
                                    // take the app-kill path with stop(reboot=false) — the
                                    // adapter itself is NOT rebooted.
                                    // GPS forwarding: controls whether app sends GNSS_DATA (0x29) to adapter
                                    //   -> needsReboot = true (adapter state change)
                                    // WiFi band: firmware radio reconfiguration
                                    //   -> app kill only; adapter reboot NOT triggered here
                                    // Cluster navigation: CarAppActivity shim lifecycle
                                    //   -> app kill only; adapter reboot NOT triggered here
                                    logWarn(
                                        "[ADAPTER_REINIT] Tier 3: Misc settings changed" +
                                            " (gps=${selectedGpsForwarding != savedGpsForwarding}" +
                                            ", wifi=${selectedWifiBand != savedWifiBand}" +
                                            ", cluster=${selectedClusterNavigation != savedClusterNavigation})" +
                                            " — killing app",
                                        tag = "UI",
                                    )
                                    val needsReboot = selectedGpsForwarding != savedGpsForwarding
                                    carlinkManager?.stop(reboot = needsReboot)
                                    val launchIntent =
                                        context.packageManager
                                            .getLaunchIntentForPackage(context.packageName)
                                            ?.addFlags(
                                                android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                                                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK,
                                            )
                                    launchIntent?.let { context.startActivity(it) }
                                    // The 500ms delay gives startActivity(launchIntent) time
                                    // to dispatch before we killProcess(myPid()) below —
                                    // without it the new task may never come up.
                                    //
                                    // FRAGILITY: this block runs inside `scope.launch`. If the
                                    // dialog leaves composition before the delay elapses, the
                                    // coroutine is cancelled and killProcess is never called,
                                    // leaving the new task racing the old PID. onDismiss() is
                                    // deliberately NOT called on the Tier-3 path for this reason.
                                    kotlinx.coroutines.delay(500)
                                    android.os.Process.killProcess(android.os.Process.myPid())
                                } else {
                                    // Tier 2: Audio/Visual settings only — in-place reinit.
                                    // Tear down adapter session, rebuild CarlinkManager with
                                    // new AdapterConfig, reconnect with MINIMAL_PLUS_CHANGES.
                                    //
                                    // HAZARD: the MINIMAL_PLUS_CHANGES name is accurate, but
                                    // adapter-side coverage is INCOMPLETE — VIDEO_RESOLUTION
                                    // and FPS have no branches in
                                    // MessageSerializer.addChangedSettings, so changes to those
                                    // two fields alone are saved to DataStore but not pushed to
                                    // the adapter until a FULL init. See HAZARD at the Video
                                    // Resolution / Frame Rate cards in the Visual tab above.
                                    logInfo(
                                        "[ADAPTER_REINIT] Tier 2: Audio/Visual settings only" +
                                            " — reinitializing in-place (no app kill)",
                                        tag = "UI",
                                    )
                                    onDismiss()
                                    onReinitAdapter()
                                }
                            }
                        },
                        modifier = Modifier.weight(1.5f),
                        enabled = hasChanges,
                    ) {
                        Icon(
                            imageVector = if (miscChanged) Icons.Default.RestartAlt else Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (miscChanged) "应用并重启" else "应用")
                    }
                }
            }
        }
    }
}

@Composable
private fun InputKeyMappingCard(
    mappings: Map<Int, InputKeyAction>,
    capturingAction: InputKeyAction?,
    onStartCapture: (InputKeyAction) -> Unit,
    onCancelCapture: () -> Unit,
    onRemoveMapping: (InputKeyAction) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    ConfigurationOptionCard(
        title = "外接按键映射",
        description = "将外接按键绑定到投屏控制操作。",
        icon = Icons.Default.SettingsInputComponent,
    ) {
        InputKeyAction.entries.forEach { action ->
            val mappedKeyCode = mappings.entries.firstOrNull { it.value == action }?.key
            val isCapturing = capturingAction == action
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(action.label, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = mappedKeyCode?.let { keyCodeLabel(it) } ?: "未绑定",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (mappedKeyCode == null) colorScheme.onSurfaceVariant else colorScheme.primary,
                    )
                }
                TextButton(
                    onClick = {
                        if (isCapturing) onCancelCapture() else onStartCapture(action)
                    },
                ) {
                    Text(if (isCapturing) "取消" else "识别按键")
                }
                if (mappedKeyCode != null && !isCapturing) {
                    TextButton(onClick = { onRemoveMapping(action) }) {
                        Text("清除")
                    }
                }
            }
        }
        if (capturingAction != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "请按下用于“${capturingAction.label}”的按键",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.primary,
            )
        }
    }
}

@Composable
private fun LogcatKeyMappingCard(
    mappings: List<LogcatKeyMapping>,
    monitorStatus: LogcatMonitorStatus,
    readLogsGranted: Boolean,
    onAddMapping: (String, InputKeyAction, LogcatTriggerMode) -> Unit,
    onRemoveMapping: (String) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    var pattern by remember { mutableStateOf("") }
    var selectedAction by remember { mutableStateOf(InputKeyAction.NEXT) }
    var selectedMode by remember { mutableStateOf(LogcatTriggerMode.CARPLAY_ONLY) }
    var actionMenuExpanded by remember { mutableStateOf(false) }
    val canAdd = isValidLogcatPattern(pattern)

    ConfigurationOptionCard(
        title = "日志按键触发器",
        description = "匹配车辆日志，触发对应的投屏操作。",
        icon = Icons.Default.Terminal,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "监听状态：${monitorStatus.label}",
                style = MaterialTheme.typography.bodySmall,
                color = if (monitorStatus == LogcatMonitorStatus.ERROR) colorScheme.error else colorScheme.primary,
            )
            if (mappings.isNotEmpty() && !readLogsGranted) {
                Text(
                    text = "权限受限",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.error,
                )
            }
        }
        if (!readLogsGranted) {
            Text(
                text = "当前应用无法读取系统日志。读取车辆日志需要系统特权或通过 adb 授予 READ_LOGS 权限。",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.error,
            )
        }
        OutlinedTextField(
            value = pattern,
            onValueChange = { pattern = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("日志文本") },
            supportingText = { Text("包含匹配，不区分大小写；可使用 % 或 * 作为通配符") },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { actionMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = selectedAction.label,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                DropdownMenu(
                    expanded = actionMenuExpanded,
                    onDismissRequest = { actionMenuExpanded = false },
                ) {
                    InputKeyAction.entries.forEach { action ->
                        DropdownMenuItem(
                            text = { Text(action.label) },
                            onClick = {
                                selectedAction = action
                                actionMenuExpanded = false
                            },
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LogcatTriggerMode.entries.forEach { mode ->
                val selected = selectedMode == mode
                Button(
                    onClick = { selectedMode = mode },
                    modifier = Modifier.weight(1f),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = if (selected) colorScheme.primary else colorScheme.surfaceVariant,
                            contentColor = if (selected) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                        ),
                ) {
                    Text(
                        text = mode.label,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (selectedMode == LogcatTriggerMode.LOCAL_ONLY) {
            Text(
                text = "仅在本地记录匹配的操作，不向 CarPlay 或安卓媒体发送指令。",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
        } else if (selectedAction == InputKeyAction.PAUSE) {
            Text(
                text = "播放 / 暂停通过 CarPlay 适配器指令发送，不生成安卓媒体按键。",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
        }
        Button(
            onClick = {
                onAddMapping(pattern, selectedAction, selectedMode)
                pattern = ""
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = canAdd,
        ) {
            Text("添加触发器")
        }

        mappings.forEach { mapping ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mapping.pattern,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${mapping.action.label} · ${mapping.mode.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { onRemoveMapping(mapping.id) }) {
                    Icon(Icons.Default.Delete, contentDescription = "删除触发器")
                }
            }
        }
    }
}

private fun keyCodeLabel(keyCode: Int): String =
    com.carlink.vehicle.VehicleKeyNames.name(keyCode) + " ($keyCode)"

private fun decodeIconPreview(bytes: ByteArray): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val sample = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 256)
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}

private fun ByteArray?.sameBytesAs(other: ByteArray?): Boolean =
    when {
        this === other -> true
        this == null || other == null -> false
        else -> contentEquals(other)
    }

/**
 * Configuration Option Card - Container for a single configuration option
 *
 * Provides consistent styling for configuration options in the dialog.
 * Designed to be reusable for future configuration options.
 */
@Composable
internal fun ConfigurationOptionCard(
    title: String,
    description: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = colorScheme.surfaceContainerHighest,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            // Header row with icon and title
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Description
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Option content
            content()
        }
    }
}

/**
 * Audio Source Selection Button
 *
 * Toggle button with animated visual indicator for selected state.
 */
@Composable
private fun AudioSourceButton(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    // Animated properties for smooth selection transitions
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) colorScheme.primaryContainer else colorScheme.surfaceContainer,
        label = "backgroundColor",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 1.dp,
        label = "borderWidth",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) colorScheme.primary else colorScheme.outline,
        label = "borderColor",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) colorScheme.primary else colorScheme.onSurfaceVariant,
        label = "contentColor",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f else 1f,
        label = "iconScale",
    )

    Surface(
        onClick = onClick,
        modifier = modifier.height(AutomotiveDimens.ButtonMinHeight),
        shape = MaterialTheme.shapes.medium,
        color = backgroundColor,
        border =
            BorderStroke(
                width = borderWidth,
                color = borderColor,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier =
                    Modifier
                        .size(24.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        },
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style =
                    MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    ),
                color = contentColor,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Resolution Selection Button
 *
 * Compact button for resolution selection with optional "Recommended" badge.
 */
@Composable
private fun ResolutionButton(
    label: String,
    isSelected: Boolean,
    isRecommended: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) colorScheme.primaryContainer else colorScheme.surfaceContainer,
        label = "backgroundColor",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 1.dp,
        label = "borderWidth",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) colorScheme.primary else colorScheme.outline,
        label = "borderColor",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) colorScheme.primary else colorScheme.onSurfaceVariant,
        label = "contentColor",
    )

    Surface(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = MaterialTheme.shapes.medium,
        color = backgroundColor,
        border =
            BorderStroke(
                width = borderWidth,
                color = borderColor,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style =
                    MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    ),
                color = contentColor,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (isRecommended && !isSelected) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "(Rec)",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.tertiary,
                )
            }
        }
    }
}
