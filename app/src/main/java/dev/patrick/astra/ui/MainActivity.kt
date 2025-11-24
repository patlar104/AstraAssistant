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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
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
import dev.patrick.astra.domain.AssistantPhase
import dev.patrick.astra.domain.AssistantVisualState
import dev.patrick.astra.domain.Emotion
import dev.patrick.astra.domain.DebugFlags
import dev.patrick.astra.overlay.OverlayService
import dev.patrick.astra.ui.theme.AstraAssistantTheme

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
    val overlayLogsEnabled by DebugFlags.overlayLogsEnabled.collectAsState()
    var inputText by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current

    val snackbarHostState = remember { SnackbarHostState() }

    // Launcher to open the overlay permission screen
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // User returns from permission screen; they can tap again to start overlay if granted.
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
            context.startService(intent)
        }
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
            inputText = inputText,
            onInputChanged = { inputText = it },
            onSend = {
                viewModel.sendUserMessage(inputText)
                inputText = ""
            },
            onStartVoice = { viewModel.startVoiceInput() },
            onStopVoice = { viewModel.stopVoiceInput() },
            onToggleOverlayLogs = { DebugFlags.setOverlayLogsEnabled(it) },
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
    inputText: String,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    onStartVoice: () -> Unit,
    onStopVoice: () -> Unit,
    onToggleOverlayLogs: (Boolean) -> Unit,
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
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
                isListening = false
            ),
            visualState = AssistantVisualState(
                phase = AssistantPhase.Idle,
                emotion = Emotion.Neutral
            ),
            overlayLogsEnabled = false,
            inputText = "",
            onInputChanged = {},
            onSend = {},
            onStartVoice = {},
            onStopVoice = {},
            onToggleOverlayLogs = {},
            modifier = Modifier
        )
    }
}
