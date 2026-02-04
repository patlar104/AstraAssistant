package dev.patrick.astra.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.patrick.astra.assistant.AstraMessage
import dev.patrick.astra.assistant.AstraUiState
import dev.patrick.astra.assistant.AstraViewModel
import dev.patrick.astra.brains.intent.ActionPlan
import dev.patrick.astra.diagnostics.DiagnosticEntry
import dev.patrick.astra.diagnostics.DiagnosticsLog
import dev.patrick.astra.domain.AssistantPhase
import dev.patrick.astra.domain.AssistantVisualState
import dev.patrick.astra.domain.DebugFlags
import dev.patrick.astra.domain.Emotion
import dev.patrick.astra.domain.HealthState
import dev.patrick.astra.legacy.AccessibilityBridge
import dev.patrick.astra.overlay.OverlayService
import dev.patrick.astra.ui.theme.AstraAssistantTheme
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AstraAssistantTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AstraHomeScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AstraHomeScreen(
    viewModel: AstraViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val visualState by viewModel.visualState.collectAsState()
    val healthState by viewModel.healthState.collectAsState()
    val overlayLogsEnabled by DebugFlags.overlayLogsEnabled.collectAsState()
    val diagnosticsEntries by DiagnosticsLog.entries.collectAsState()
    var inputText by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.snackbarEvents.collectLatest { event ->
            val result = snackbarHostState.showSnackbar(
                message = event.message,
                actionLabel = event.actionLabel
            )
            if (result == SnackbarResult.ActionPerformed) {
                event.onAction?.invoke()
            }
        }
    }

    // Launcher to open the overlay permission screen
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshHealth()
    }

    fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        overlayPermissionLauncher.launch(intent)
    }

    fun startOverlayIfPossible() {
        if (!OverlayService.canDrawOverlays(context)) {
            requestOverlayPermission()
        } else {
            val intent = Intent(context, OverlayService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    fun stopOverlay() {
        val intent = Intent(context, OverlayService::class.java).apply {
            action = OverlayService.ACTION_STOP_OVERLAY
        }
        context.startService(intent)
    }

    fun openAccessibilitySettings() {
        AccessibilityBridge(context).openServiceSettings()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Astra Assistant") },
                actions = {
                    IconButton(onClick = { startOverlayIfPossible() }) {
                        Text("🟣")
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { paddingValues ->
        AstraHomeScreenContent(
            uiState = uiState,
            visualState = visualState,
            overlayLogsEnabled = overlayLogsEnabled,
            healthState = healthState,
            diagnosticsEntries = diagnosticsEntries,
            inputText = inputText,
            onInputChanged = { inputText = it },
            onSend = {
                viewModel.sendUserMessage(inputText)
                inputText = ""
            },
            onStartVoice = { viewModel.startVoiceInput() },
            onStopVoice = { viewModel.stopVoiceInput() },
            onToggleOverlayLogs = { DebugFlags.setOverlayLogsEnabled(it) },
            onRequestOverlayPermission = { requestOverlayPermission() },
            onStopOverlay = { stopOverlay() },
            onOpenAccessibilitySettings = { openAccessibilitySettings() },
            onConfirmAction = { skip -> viewModel.confirmPendingAction(skip) },
            onCancelAction = { viewModel.cancelPendingAction() },
            modifier = Modifier.padding(paddingValues)
        )
    }
}

/**
 * Stateless UI content so we can reuse it in previews.
 */
@Composable
private fun AstraHomeScreenContent(
    uiState: AstraUiState,
    visualState: AssistantVisualState,
    overlayLogsEnabled: Boolean,
    healthState: HealthState,
    diagnosticsEntries: List<DiagnosticEntry>,
    inputText: String,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    onStartVoice: () -> Unit,
    onStopVoice: () -> Unit,
    onToggleOverlayLogs: (Boolean) -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onStopOverlay: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onConfirmAction: (Boolean) -> Unit,
    onCancelAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val recordAudioPermission = Manifest.permission.RECORD_AUDIO

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                onStartVoice()
            }
        }
    )

    fun handleMicClick() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            recordAudioPermission
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            if (visualState.phase is AssistantPhase.Listening) {
                onStopVoice()
            } else {
                onStartVoice()
            }
        } else {
            permissionLauncher.launch(recordAudioPermission)
        }
    }

    var showDiagnostics by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (!healthState.overlayPermissionGranted) {
            OverlayPermissionBanner(
                onRequestPermission = onRequestOverlayPermission
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        HealthStatusRow(
            healthState = healthState,
            onStopOverlay = onStopOverlay,
            onOpenAccessibilitySettings = onOpenAccessibilitySettings
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Top: character + intro text
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AstraCharacter()

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Hey, I’m Astra.\nI’ll help you navigate your phone.",
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Middle: conversation
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(uiState.messages) { message ->
                    ChatBubble(message = message)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        if (visualState.phase is AssistantPhase.Thinking) {
            Text(
                text = "Astra is thinking…",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        if (visualState.phase is AssistantPhase.Listening) {
            Text(
                text = "Listening…",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Bottom: text input + mic + send button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = inputText,
                onValueChange = onInputChanged,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                placeholder = { Text("Type a message…") }
            )

            // Mic button
            Button(
                onClick = { handleMicClick() },
                modifier = Modifier.padding(end = 8.dp)
            ) {
                val label = if (visualState.phase is AssistantPhase.Listening) "Stop" else "Mic"
                Text(label)
            }

            Button(
                onClick = onSend,
                enabled = inputText.isNotBlank()
            ) {
                Text("Send")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Overlay debug logs",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = overlayLogsEnabled,
                onCheckedChange = onToggleOverlayLogs
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Diagnostics",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = showDiagnostics,
                onCheckedChange = { showDiagnostics = it }
            )
        }

        if (showDiagnostics) {
            DiagnosticsPanel(entries = diagnosticsEntries)
        }
    }

    if (uiState.confirmationRequired && uiState.pendingActionPlan != null) {
        ActionConfirmationDialog(
            plan = uiState.pendingActionPlan,
            onConfirm = onConfirmAction,
            onCancel = onCancelAction
        )
    }
}

@Composable
private fun OverlayPermissionBanner(
    onRequestPermission: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Overlay permission is required to show the bubble.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onRequestPermission) {
                Text("Enable")
            }
        }
    }
}

@Composable
private fun HealthStatusRow(
    healthState: HealthState,
    onStopOverlay: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Health",
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Overlay permission: ${if (healthState.overlayPermissionGranted) "Granted" else "Missing"}",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = "Voice available: ${if (healthState.voiceAvailable) "Yes" else "No"}",
            style = MaterialTheme.typography.bodySmall
        )
        healthState.voiceError?.let { error ->
            Text(
                text = "Voice error: $error",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(
            text = "Accessibility service: ${if (healthState.accessibilityEnabled) "Enabled" else "Disabled"}",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onStopOverlay, modifier = Modifier.padding(end = 8.dp)) {
                Text("Stop overlay")
            }
            Button(onClick = onOpenAccessibilitySettings) {
                Text("Enable accessibility")
            }
        }
    }
}

@Composable
private fun ActionConfirmationDialog(
    plan: ActionPlan.ExecuteDeviceActions,
    onConfirm: (Boolean) -> Unit,
    onCancel: () -> Unit
) {
    var skipConfirmation by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Confirm action") },
        text = {
            Column {
                Text("Astra is ready to perform:")
                Spacer(modifier = Modifier.height(8.dp))
                plan.steps.forEach { step ->
                    Text("• ${step.javaClass.simpleName}")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = skipConfirmation,
                        onCheckedChange = { skipConfirmation = it }
                    )
                    Text("Don’t ask again for these actions")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(skipConfirmation) }) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DiagnosticsPanel(entries: List<DiagnosticEntry>) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (entries.isEmpty()) {
                Text("No diagnostics yet", style = MaterialTheme.typography.bodySmall)
            } else {
                entries.takeLast(12).forEach { entry ->
                    Text(
                        text = "${entry.level}: ${entry.tag} - ${entry.message}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

/**
 * Simple chat bubble for user vs Astra messages.
 */
@Composable
private fun ChatBubble(message: AstraMessage) {
    val backgroundColor =
        if (message.fromUser) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }

    val alignment =
        if (message.fromUser) Alignment.CenterEnd else Alignment.CenterStart

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Surface(
            color = backgroundColor,
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AstraHomePreview() {
    AstraAssistantTheme {
        AstraHomeScreenContent(
            uiState = AstraUiState(
                messages = listOf(
                    AstraMessage(false, "Hi, I’m Astra. Ask me anything!"),
                    AstraMessage(true, "Hey Astra!"),
                    AstraMessage(false, "I heard: \"Hey Astra!\" (Astra’s brain isn’t wired yet.)")
                ),
                isThinking = false,
                isListening = false,
                pendingActionPlan = null,
                confirmationRequired = false
            ),
            visualState = AssistantVisualState(
                phase = AssistantPhase.Idle,
                emotion = Emotion.Neutral
            ),
            overlayLogsEnabled = false,
            healthState = HealthState(
                overlayPermissionGranted = true,
                voiceAvailable = true,
                voiceError = null,
                accessibilityEnabled = false
            ),
            diagnosticsEntries = emptyList(),
            inputText = "",
            onInputChanged = {},
            onSend = {},
            onStartVoice = {},
            onStopVoice = {},
            onToggleOverlayLogs = {},
            onRequestOverlayPermission = {},
            onStopOverlay = {},
            onOpenAccessibilitySettings = {},
            onConfirmAction = {},
            onCancelAction = {},
            modifier = Modifier
        )
    }
}
