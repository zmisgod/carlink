package com.carlink.ui

import android.content.pm.PackageManager
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.VideoSettings
import androidx.compose.material.icons.filled.WebAsset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carlink.CarlinkManager
import com.carlink.logging.FileLogManager
import com.carlink.logging.logInfo
import com.carlink.logging.logWarn
import com.carlink.protocol.InputKeyAction
import com.carlink.ui.components.LoadingSpinner
import com.carlink.ui.settings.AdapterConfigPreference
import com.carlink.ui.settings.AdapterConfigurationDialog
import com.carlink.ui.settings.DisplayMode
import com.carlink.ui.settings.DisplayModeDialog
import com.carlink.ui.settings.DisplayModePreference
import com.carlink.ui.settings.LogsTabContent
import com.carlink.ui.settings.LogcatKeyMapping
import com.carlink.ui.settings.LogcatTriggerMode
import com.carlink.ui.settings.PhonesTabContent
import com.carlink.ui.settings.SettingsTab
import com.carlink.ui.theme.AutomotiveDimens
import com.carlink.util.LogcatMonitorStatus
import kotlinx.coroutines.launch

/** Settings screen with NavigationRail for device control, display settings, and log management. */
@Composable
fun SettingsScreen(
    carlinkManager: CarlinkManager,
    fileLogManager: FileLogManager?,
    onNavigateBack: () -> Unit,
    onResetCluster: () -> Unit,
    onReinitForDisplayMode: (DisplayMode) -> Unit = {},
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
    var selectedTab by remember { mutableStateOf(SettingsTab.PHONES) }
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme

    // Log settings screen entry and tab changes
    LaunchedEffect(Unit) {
        logInfo(
            "[UI_STATE] SettingsScreen opened" +
                " - user is in app settings (NOT viewing CarPlay projection)",
            tag = "UI",
        )
    }

    LaunchedEffect(selectedTab) {
        logInfo("[UI_STATE] Settings tab changed: $selectedTab", tag = "UI")
    }

    // Get app version
    val appVersion =
        remember {
            try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                "${packageInfo.versionName}+${packageInfo.longVersionCode}"
            } catch (e: PackageManager.NameNotFoundException) {
                // NOTE: inconsistent log tag — all other logWarn/logInfo sites in this file pass
                // tag = "UI". This call omits it and falls back to the default tag. Flagged for
                // a future fix; left as-is to keep this pass comment-only.
                logWarn("[SettingsScreen] Failed to get package info: ${e.message}")
                "未知"
            }
        }

    val view = LocalView.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colorScheme.surface,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            // Left sidebar with NavigationRail
            Column(
                modifier = Modifier.fillMaxHeight(),
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 20.dp, horizontal = 12.dp),
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            // KEYBOARD_TAP is the project convention for settings-surface clicks
                            // (back / tab / close). Kept uniform across this screen; alternatives
                            // like CONTEXT_CLICK or VIRTUAL_KEY were not chosen.
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onNavigateBack()
                        },
                        modifier = Modifier.size(AutomotiveDimens.ButtonMinHeight),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            modifier = Modifier.size(AutomotiveDimens.IconSize),
                        )
                    }
                }

                // NavigationRail with tabs - Expanded to fill available space
                // Items centered vertically
                NavigationRail(
                    modifier = Modifier.weight(1f),
                    containerColor = colorScheme.surface,
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    SettingsTab.visible.forEach { tab ->
                        NavigationRailItem(
                            selected = selectedTab == tab,
                            onClick = {
                                // KEYBOARD_TAP chosen for tab switches to match the project's
                                // settings-surface convention (see back button above).
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                selectedTab = tab
                            },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title,
                                )
                            },
                            label = { Text(tab.title) },
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }

                // Close app button + version at bottom
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    var showCloseConfirm by remember { mutableStateOf(false) }

                    FilledTonalIconButton(
                        onClick = {
                            // KEYBOARD_TAP: see settings-surface haptic convention note above.
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            showCloseConfirm = true
                        },
                        modifier = Modifier.size(AutomotiveDimens.ButtonMinHeight),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "退出应用",
                            modifier = Modifier.size(AutomotiveDimens.IconSize),
                            tint = colorScheme.error,
                        )
                    }

                    if (showCloseConfirm) {
                        AlertDialog(
                            onDismissRequest = { showCloseConfirm = false },
                            title = { Text("退出应用？") },
                            text = { Text("将断开所有连接并退出应用。") },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        logWarn("[UI_ACTION] Close App confirmed", tag = "UI")
                                        // Graceful teardown: sends 0x0F+0x15 to adapter (see
                                        // AdapterDriver.kt:309-311 + MessageSerializer.kt:297-301).
                                        carlinkManager.stop()
                                        // HAZARD: silent no-op if LocalContext.current is not an
                                        // Activity (e.g. wrapped ContextWrapper). In that case
                                        // stop() still runs but the app window is not closed.
                                        // SettingsScreen is always hosted by MainActivity today, so
                                        // this is a known-safe cast; keep in mind if the hosting
                                        // model changes.
                                        (context as? android.app.Activity)?.finishAffinity()
                                    },
                                ) {
                                    Text("退出", color = colorScheme.error)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showCloseConfirm = false }) {
                                    Text("取消")
                                }
                            },
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "版本：",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (appVersion.isEmpty()) "- - -" else appVersion,
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                        color = if (appVersion.isEmpty()) colorScheme.onSurfaceVariant else colorScheme.onSurface,
                    )
                }
            }

            // Tab content - fills remaining space
            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .weight(1f),
            ) {
                when (selectedTab) {
                    SettingsTab.CONTROL ->
                        ControlTabContent(
                            carlinkManager,
                            onResetCluster,
                            onReinitForDisplayMode,
                            inputKeyMappings,
                            inputKeyCaptureAction,
                            onStartInputKeyCapture,
                            onCaptureInputKey,
                            onCancelInputKeyCapture,
                            onRemoveInputKeyMapping,
                            logcatMappings,
                            logcatMonitorStatus,
                            logcatReadLogsGranted,
                            onAddLogcatMapping,
                            onRemoveLogcatMapping,
                        )
                    SettingsTab.PHONES -> PhonesTabContent(carlinkManager)
                    SettingsTab.LOGS -> LogsTabContent(context, fileLogManager)
                    SettingsTab.MUSIC_LOG -> com.carlink.ui.settings.MusicLogTabContent()
                }
            }
        }
    }
}

/**
 * Visual severity class for [ControlButton]. Drives container/content colors only — does not
 * affect behavior or confirmation flow (callers still own any "are you sure?" dialogs).
 *
 * NOTE: the `when(severity)` blocks in [ControlButton] (L631+) are intentionally non-exhaustive
 * on a semantic level but exhaustive on this enum (only two values). Adding a new severity
 * requires extending both `when` arms.
 */
private enum class ButtonSeverity {
    WARNING, // Warning action (tertiary/amber)
    DESTRUCTIVE, // Destructive action (error/red)
}

/**
 * Control tab content: adapter configuration + app control (display mode, reset buttons,
 * reset-connection). Hosts several dialogs (reset-cluster confirm, cluster-nav-off info,
 * adapter-config, display-mode) driven by local state.
 *
 * KNOWN LIMITATIONS (documented at the relevant lines below):
 *   1. `clusterNavigationEnabled` is a one-shot snapshot (L300) — can go stale vs. the
 *      adapter-config dialog's toggle.
 *   2. `isProcessing` is a single shared flag across 4 actions but only Reset Connection flips
 *      it — the other 3 buttons gate on it but never raise it themselves (UX: spinner appears
 *      on all of them when Reset Connection runs; no per-action progress indicator).
 */
@Composable
private fun ControlTabContent(
    carlinkManager: CarlinkManager,
    onResetCluster: () -> Unit,
    onReinitForDisplayMode: (DisplayMode) -> Unit = {},
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
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    // KNOWN UX LIMITATION: `isProcessing` is shared across Disconnect Phone, Disconnect Adapter,
    // Reset Decoder, Reset Cluster, and Reset Connection — but only the Reset Connection
    // onClick actually sets it true (L456/461 below). The other four buttons read it to gate
    // `enabled` and to swap in the spinner, so when Reset Connection is running they all show
    // the spinner and go disabled. They never show their own progress. Fix would be per-action
    // processing flags (e.g. `isResettingConnection`, `isDisconnectingPhone`, …), but that is
    // deliberately out of scope for this pass.
    var isProcessing by remember { mutableStateOf(false) }
    var showResetClusterDialog by remember { mutableStateOf(false) }
    var showClusterNavOffDialog by remember { mutableStateOf(false) }
    val isDeviceConnected = carlinkManager.state != CarlinkManager.State.DISCONNECTED

    val displayModePreference = remember { DisplayModePreference.getInstance(context) }
    // Initial value is the platform-aware default (gminfo37 + AAOS emulator →
    // FULLSCREEN_IMMERSIVE; else SYSTEM_UI_VISIBLE). Flow will overwrite with any
    // persisted user choice on the first emission.
    val displayModeInitial = remember { DisplayMode.platformDefault(context) }
    val currentDisplayMode by displayModePreference.displayModeFlow.collectAsStateWithLifecycle(
        initialValue = displayModeInitial,
    )
    var showDisplayModeDialog by remember { mutableStateOf(false) }

    val adapterConfigPreference = remember { AdapterConfigPreference.getInstance(context) }
    var showAdapterConfigDialog by remember { mutableStateOf(false) }
    // KNOWN STALENESS WINDOW: `remember { … }` with no key captures the value once for the
    // lifetime of this composition. If the user enables/disables Cluster Navigation in the
    // AdapterConfigurationDialog (L529-540) while the Control tab is still composed, this
    // snapshot does not refresh — so the Reset Cluster button at L436 may branch on a stale
    // value and incorrectly show the "仪表导航未开启" dialog after a just-enabled
    // toggle (or, conversely, proceed to the reset-confirm after a just-disabled toggle).
    //
    // FIX: switch to a reactive flow, mirroring `displayModeFlow` on L293:
    //   val clusterNavigationEnabled by adapterConfigPreference.clusterNavigationFlow
    //       .collectAsStateWithLifecycle(initialValue = <default>)
    // (See AdapterConfigPreference.kt:627 for the flow declaration.) Left unchanged in this
    // pass to keep scope to comments only.
    val clusterNavigationEnabled = remember { adapterConfigPreference.getClusterNavigationSync() }

    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val containerWidthDp = with(density) { windowInfo.containerSize.width.toDp() }
    // Sized for 2400px-wide gminfo37 (confirmed 2026-04-20): 75% × 2400 = 1800 → clamped by
    // 1200.dp ceiling. 400.dp floor only matters for narrow splits not seen on gminfo37.
    // Retune if a device with container <533dp or >1600dp appears.
    val maxContentWidth = (containerWidthDp * 0.75f).coerceIn(400.dp, 1200.dp)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = maxContentWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Adapter Configuration Card (includes device control actions)
                ControlCard(
                    modifier = Modifier.weight(1f),
                    title = "适配器设置",
                    icon = Icons.Default.SettingsInputComponent,
                ) {
                    // Configure button
                    FilledTonalButton(
                        onClick = { showAdapterConfigDialog = true },
                        modifier = Modifier.fillMaxWidth().height(AutomotiveDimens.ButtonMinHeight),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "配置适配器",
                            modifier = Modifier.size(AutomotiveDimens.IconSize),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "设置",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    ControlButton(
                        label = "断开手机",
                        icon = Icons.Default.PhoneDisabled,
                        severity = ButtonSeverity.WARNING,
                        enabled = isDeviceConnected && !isProcessing,
                        isProcessing = isProcessing,
                        onClick = {
                            logWarn("[UI_ACTION] Disconnect Phone button clicked", tag = "UI")
                            carlinkManager.disconnectPhone()
                        },
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ControlButton(
                        label = "断开适配器",
                        icon = Icons.Default.PowerOff,
                        severity = ButtonSeverity.DESTRUCTIVE,
                        enabled = isDeviceConnected && !isProcessing,
                        isProcessing = isProcessing,
                        onClick = {
                            logWarn("[UI_ACTION] Disconnect Adapter button clicked", tag = "UI")
                            carlinkManager.stop()
                        },
                    )
                }

                // App Control Card
                ControlCard(
                    modifier = Modifier.weight(1f),
                    title = "应用控制",
                    icon = Icons.Default.DisplaySettings,
                ) {
                    FilledTonalButton(
                        onClick = { showDisplayModeDialog = true },
                        modifier = Modifier.fillMaxWidth().height(AutomotiveDimens.ButtonMinHeight),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Icon(
                            imageVector =
                                when (currentDisplayMode) {
                                    DisplayMode.SYSTEM_UI_VISIBLE -> Icons.Default.FullscreenExit
                                    DisplayMode.STATUS_BAR_HIDDEN -> Icons.Default.Layers
                                    DisplayMode.NAV_BAR_HIDDEN -> Icons.Default.WebAsset
                                    DisplayMode.FULLSCREEN_IMMERSIVE -> Icons.Default.Fullscreen
                                },
                            contentDescription = "设置显示模式",
                            modifier = Modifier.size(AutomotiveDimens.IconSize),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "显示模式",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ControlButton(
                            label = "重置解码器",
                            icon = Icons.Default.VideoSettings,
                            severity = ButtonSeverity.WARNING,
                            enabled = !isProcessing,
                            isProcessing = isProcessing,
                            onClick = {
                                logWarn("[UI_ACTION] Reset Decoder button clicked", tag = "UI")
                                carlinkManager.resetVideoDecoder()
                            },
                            modifier = Modifier.weight(1f),
                        )

                        ControlButton(
                            label = "重置仪表",
                            icon = Icons.Default.Speed,
                            severity = ButtonSeverity.WARNING,
                            enabled = !isProcessing,
                            isProcessing = isProcessing,
                            onClick = {
                                if (clusterNavigationEnabled) {
                                    showResetClusterDialog = true
                                } else {
                                    showClusterNavOffDialog = true
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    ControlButton(
                        label = "重置连接",
                        icon = Icons.Default.Usb,
                        severity = ButtonSeverity.DESTRUCTIVE,
                        enabled = isDeviceConnected && !isProcessing,
                        isProcessing = isProcessing,
                        onClick = {
                            logWarn("[UI_ACTION] Reset Connection button clicked", tag = "UI")
                            isProcessing = true
                            scope.launch {
                                try {
                                    carlinkManager.restart()
                                } finally {
                                    isProcessing = false
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    // Reset Cluster Confirmation Dialog
    if (showResetClusterDialog) {
        AlertDialog(
            onDismissRequest = { showResetClusterDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = colorScheme.tertiary,
                )
            },
            title = { Text("重置仪表？") },
            text = {
                Text("将重置仪表显示。辅助应用会重新加载并短暂切换到前台。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        logWarn("[UI_ACTION] Reset Cluster confirmed", tag = "UI")
                        showResetClusterDialog = false
                        onResetCluster()
                    },
                ) {
                    Text("重置", color = colorScheme.tertiary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetClusterDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    // Cluster Navigation Off Info Dialog
    if (showClusterNavOffDialog) {
        AlertDialog(
            onDismissRequest = { showClusterNavOffDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant,
                )
            },
            title = { Text("仪表导航未开启") },
            text = {
                Text("请先在适配器设置中开启仪表导航，再使用此功能。")
            },
            confirmButton = {
                TextButton(onClick = { showClusterNavOffDialog = false }) {
                    Text("确定")
                }
            },
        )
    }

    // Adapter Configuration Dialog
    if (showAdapterConfigDialog) {
        AdapterConfigurationDialog(
            adapterConfigPreference = adapterConfigPreference,
            carlinkManager = carlinkManager,
            currentDisplayMode = currentDisplayMode,
            onDismiss = {
                onCancelInputKeyCapture()
                showAdapterConfigDialog = false
            },
            onReinitAdapter = {
                onCancelInputKeyCapture()
                showAdapterConfigDialog = false
                onReinitForDisplayMode(currentDisplayMode)
            },
            inputKeyMappings = inputKeyMappings,
            inputKeyCaptureAction = inputKeyCaptureAction,
            onStartInputKeyCapture = onStartInputKeyCapture,
            onCaptureInputKey = onCaptureInputKey,
            onCancelInputKeyCapture = onCancelInputKeyCapture,
            onRemoveInputKeyMapping = onRemoveInputKeyMapping,
            logcatMappings = logcatMappings,
            logcatMonitorStatus = logcatMonitorStatus,
            logcatReadLogsGranted = logcatReadLogsGranted,
            onAddLogcatMapping = onAddLogcatMapping,
            onRemoveLogcatMapping = onRemoveLogcatMapping,
        )
    }

    // Display Mode Dialog with live preview
    if (showDisplayModeDialog) {
        DisplayModeDialog(
            displayModePreference = displayModePreference,
            onDismiss = { showDisplayModeDialog = false },
            onApplyAndRestart = { newMode ->
                showDisplayModeDialog = false
                scope.launch {
                    // Save the new display mode preference
                    displayModePreference.setDisplayMode(newMode)
                    logInfo(
                        "[DISPLAY_REINIT] User applied display mode: ${newMode.name} — " +
                            "reinitializing in-place (no app kill)",
                        tag = "UI",
                    )
                    // Tier-2 session restart: tear down adapter, apply new mode,
                    // recalculate resolution, rebuild CarlinkManager.
                    // Replaces the old Process.killProcess() approach.
                    onReinitForDisplayMode(newMode)
                }
            },
        )
    }
}

/**
 * Material 3 elevated card used as a titled container for groups of control actions
 * (e.g. "适配器设置", "应用控制").
 *
 * Renders a leading [icon] + [title] header row, a fixed-height spacer, and then the caller's
 * [content] in a [ColumnScope]. Callers are responsible for laying out their own buttons and
 * inter-button spacing inside [content]. Does not manage any state of its own.
 */
@Composable
private fun ControlCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
        ) {
            // Header row
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style =
                        MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            content()
        }
    }
}

/**
 * Material 3 action button used inside a [ControlCard]. Swaps its leading icon for a
 * [LoadingSpinner] while [isProcessing] is true, with a fade+scale transition.
 *
 * Colors are picked from [severity]: DESTRUCTIVE uses error container (solid [Button]);
 * WARNING uses tertiary container ([FilledTonalButton]). The button fills the available
 * width and uses [AutomotiveDimens.ButtonMinHeight] to stay within the automotive
 * touch-target spec.
 *
 * Caller contract:
 *   - [enabled] should already factor in any app-level gating (connection state, etc.);
 *     see the redundancy note at the `enabled = enabled && !isProcessing` call sites.
 *   - [isProcessing] controls only the visual swap — this composable does not perform
 *     the work or manage the flag's lifecycle.
 */
@Composable
private fun ControlButton(
    label: String,
    icon: ImageVector,
    severity: ButtonSeverity,
    enabled: Boolean,
    isProcessing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    when (severity) {
        ButtonSeverity.DESTRUCTIVE -> {
            Button(
                onClick = onClick,
                // Redundant-but-intentional: every call site already passes
                // `enabled = X && !isProcessing`. Kept as belt-and-suspenders so the
                // composable is safe against future callers that forget the gate.
                enabled = enabled && !isProcessing,
                modifier =
                    modifier
                        .fillMaxWidth()
                        .height(AutomotiveDimens.ButtonMinHeight),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = colorScheme.error,
                        contentColor = colorScheme.onError,
                    ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            ) {
                AnimatedContent(
                    targetState = isProcessing,
                    transitionSpec = {
                        (fadeIn() + scaleIn()).togetherWith(fadeOut() + scaleOut())
                    },
                    label = "iconTransition",
                ) { processing ->
                    if (processing) {
                        LoadingSpinner(
                            size = 24.dp,
                            color = colorScheme.onError,
                        )
                    } else {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }

        ButtonSeverity.WARNING -> {
            FilledTonalButton(
                onClick = onClick,
                // Redundant-but-intentional: see note on the DESTRUCTIVE branch above.
                enabled = enabled && !isProcessing,
                modifier =
                    modifier
                        .fillMaxWidth()
                        .height(AutomotiveDimens.ButtonMinHeight),
                colors =
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = colorScheme.tertiaryContainer,
                        contentColor = colorScheme.onTertiaryContainer,
                    ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            ) {
                AnimatedContent(
                    targetState = isProcessing,
                    transitionSpec = {
                        (fadeIn() + scaleIn()).togetherWith(fadeOut() + scaleOut())
                    },
                    label = "iconTransition",
                ) { processing ->
                    if (processing) {
                        LoadingSpinner(
                            size = 24.dp,
                        )
                    } else {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}
