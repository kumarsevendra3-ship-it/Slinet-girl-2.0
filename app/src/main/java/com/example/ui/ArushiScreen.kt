package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bridge.ContactMatch
import kotlin.math.cos
import kotlin.math.sin

val NeonPurple = Color(0xFF7C4DFF)
val NeonMagenta = Color(0xFFE040FB)
val NeonCyan = Color(0xFF00E5FF)
val DarkBackground = Color(0xFF0B0914)
val DarkSurface = Color(0xFF141124)
val DarkCard = Color(0xFF1F1A36)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArushiScreen(
    viewModel: ArushiViewModel,
    hasMicPermission: Boolean,
    onRequestMicPermission: () -> Unit,
    onRequestContactsPermission: () -> Unit
) {
    val assistantState by viewModel.assistantState.collectAsState()
    val transcript by viewModel.transcript.collectAsState()
    val userSpokenText by viewModel.userSpokenText.collectAsState()
    val lastActionNotice by viewModel.lastActionNotice.collectAsState()
    val micAmplitude by viewModel.micAmplitude.collectAsState()
    val outputAmplitude by viewModel.outputAmplitude.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val contactChoices by viewModel.contactChoices.collectAsState()
    val currentApiKey by viewModel.currentApiKey.collectAsState()

    var showLogsSheet by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Scaffold(
        containerColor = DarkBackground,
        contentColor = Color.White,
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    when (assistantState) {
                                        AssistantState.SPEAKING -> NeonMagenta
                                        AssistantState.LISTENING -> NeonCyan
                                        AssistantState.CONNECTING -> Color(0xFFFFD600)
                                        AssistantState.ERROR -> Color(0xFFFF5252)
                                        AssistantState.IDLE -> Color.Gray
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Arushi",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            )
                            Text(
                                text = "Full Phone Control AI Assistant",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = NeonCyan,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                },
                actions = {
                    // Speaker Test & Voice Greeting Button
                    IconButton(
                        onClick = { viewModel.runSpeakerTest() },
                        modifier = Modifier.testTag("speaker_test_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Test Speaker & Voice",
                            tint = NeonCyan
                        )
                    }
                    // API Key Settings
                    IconButton(
                        onClick = { showApiKeyDialog = true },
                        modifier = Modifier.testTag("api_key_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "API Key Settings",
                            tint = if (currentApiKey.isNotBlank()) NeonMagenta else Color.LightGray
                        )
                    }
                    // Debug logs sheet
                    IconButton(
                        onClick = { showLogsSheet = true },
                        modifier = Modifier.testTag("debug_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = "Diagnostic Logs",
                            tint = Color.LightGray
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section: Status and Subtitle Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StateBadge(state = assistantState)

                Spacer(modifier = Modifier.height(8.dp))

                AnimatedVisibility(
                    visible = lastActionNotice != null,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = NeonPurple.copy(alpha = 0.25f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .border(1.dp, NeonPurple.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    ) {
                        Text(
                            text = lastActionNotice ?: "",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = NeonCyan,
                                fontWeight = FontWeight.SemiBold
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                TranscriptCard(
                    userText = userSpokenText,
                    arushiText = transcript,
                    state = assistantState,
                    onReplayVoice = {
                        if (transcript.isNotBlank()) {
                            viewModel.audioOutputManager.speakText(transcript)
                        }
                    }
                )
            }

            // Middle Section: Dynamic Voice Orb
            Box(
                modifier = Modifier
                    .size(190.dp)
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                VoiceOrb(
                    state = assistantState,
                    amplitude = if (assistantState == AssistantState.SPEAKING) outputAmplitude else micAmplitude
                )
            }

            // Bottom Section: Phone Controls, Text Input & Activation
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // One-tap Hardware Control Buttons
                PhoneControlShortcutsRow(
                    onAction = { actionKey ->
                        viewModel.executeQuickAction(actionKey)
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Voice Prompts
                QuickPromptChips(
                    onPromptSelected = { prompt ->
                        viewModel.handleUserVoicePrompt(prompt)
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Text Command Input Bar (Supports typing if user cannot speak)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = {
                            Text(
                                "Type command or speak...",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (textInput.isNotBlank()) {
                                    val prompt = textInput.trim()
                                    textInput = ""
                                    focusManager.clearFocus()
                                    viewModel.handleUserVoicePrompt(prompt)
                                }
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = NeonPurple.copy(alpha = 0.5f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = NeonCyan
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    if (textInput.isNotBlank()) {
                        IconButton(
                            onClick = {
                                val prompt = textInput.trim()
                                textInput = ""
                                focusManager.clearFocus()
                                viewModel.handleUserVoicePrompt(prompt)
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(NeonMagenta)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send Command",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        // Mic Activation Button
                        MainPowerButton(
                            state = assistantState,
                            onClick = {
                                if (!hasMicPermission) {
                                    onRequestMicPermission()
                                } else {
                                    viewModel.toggleAssistant(hasMicPermission)
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = when (assistantState) {
                        AssistantState.IDLE -> "Mic dabayein ya command type karein (Hindi/English)"
                        AssistantState.CONNECTING -> "Arushi taiyar ho rahi hai..."
                        AssistantState.LISTENING -> "Arushi sun rahi hai..."
                        AssistantState.SPEAKING -> "Arushi bol rahi hai..."
                        AssistantState.ERROR -> "Check microphone permission"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.LightGray.copy(alpha = 0.8f),
                        fontSize = 11.sp
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    // API Key Dialog
    if (showApiKeyDialog) {
        ApiKeyDialog(
            currentKey = currentApiKey,
            onSave = { newKey ->
                viewModel.saveCustomApiKey(newKey)
                showApiKeyDialog = false
            },
            onDismiss = { showApiKeyDialog = false }
        )
    }

    // Contact Disambiguation Dialog
    if (contactChoices.isNotEmpty()) {
        ContactChoicesDialog(
            contacts = contactChoices,
            onSelect = { viewModel.selectContact(it) },
            onDismiss = { viewModel.dismissContactChoices() }
        )
    }

    // Diagnostic Logs Bottom Sheet
    if (showLogsSheet) {
        DiagnosticLogsSheet(
            logs = logs,
            onDismiss = { showLogsSheet = false },
            onClear = { viewModel.clearLogs() }
        )
    }
}

@Composable
fun ApiKeyDialog(
    currentKey: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var keyInput by remember { mutableStateOf(currentKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Gemini API Key Settings", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    text = "Arushi uses Gemini for intelligent phone actions and natural voice conversation. Configure your API key below:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    placeholder = { Text("Enter GEMINI_API_KEY...") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = NeonPurple,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(keyInput) },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = NeonMagenta)
            ) {
                Text("Save Key")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = DarkCard,
        titleContentColor = Color.White
    )
}

@Composable
fun PhoneControlShortcutsRow(onAction: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        PhoneActionIcon(icon = Icons.Default.FlashlightOn, label = "Torch", onClick = { onAction("torch") })
        PhoneActionIcon(icon = Icons.Default.BatteryChargingFull, label = "Battery", onClick = { onAction("battery") })
        PhoneActionIcon(icon = Icons.Default.VolumeUp, label = "Vol +", onClick = { onAction("volume_up") })
        PhoneActionIcon(icon = Icons.Default.CameraAlt, label = "Camera", onClick = { onAction("camera") })
        PhoneActionIcon(icon = Icons.Default.Settings, label = "Settings", onClick = { onAction("settings") })
    }
}

@Composable
fun PhoneActionIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(DarkCard)
                .border(1.dp, NeonPurple.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = NeonCyan,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = Color.LightGray)
        )
    }
}

@Composable
fun StateBadge(state: AssistantState) {
    val (label, bgGradient, textColor) = when (state) {
        AssistantState.IDLE -> Triple(
            "READY • TAP MIC OR TYPE COMMAND",
            Brush.horizontalGradient(listOf(Color(0xFF2C283E), Color(0xFF1E1B2E))),
            Color.LightGray
        )
        AssistantState.CONNECTING -> Triple(
            "CONNECTING...",
            Brush.horizontalGradient(listOf(Color(0xFFE65100), Color(0xFFF57C00))),
            Color.White
        )
        AssistantState.LISTENING -> Triple(
            "LISTENING (SPEAK FREELY)...",
            Brush.horizontalGradient(listOf(NeonCyan, Color(0xFF0091EA))),
            Color.Black
        )
        AssistantState.SPEAKING -> Triple(
            "ARUSHI IS SPEAKING",
            Brush.horizontalGradient(listOf(NeonMagenta, NeonPurple)),
            Color.White
        )
        AssistantState.ERROR -> Triple(
            "TAP TO RETRY",
            Brush.horizontalGradient(listOf(Color(0xFFD50000), Color(0xFFFF1744))),
            Color.White
        )
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        modifier = Modifier
            .background(bgGradient, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = textColor
            )
        )
    }
}

@Composable
fun TranscriptCard(
    userText: String,
    arushiText: String,
    state: AssistantState,
    onReplayVoice: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .border(1.dp, NeonPurple.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                if (userText.isNotBlank()) {
                    Text(
                        text = "Aap: $userText",
                        style = MaterialTheme.typography.bodySmall.copy(color = NeonCyan),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                Text(
                    text = when {
                        arushiText.isNotBlank() -> "\"$arushiText\""
                        state == AssistantState.LISTENING -> "Boliye: 'Torch on karo', 'Battery kitni hai?', 'WhatsApp kholo'..."
                        state == AssistantState.SPEAKING -> "Arushi bol rahi hai..."
                        else -> "\"Namaste! Main Arushi hoon. Aapke phone ko full control kar sakti hoon!\""
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White,
                        lineHeight = 18.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (arushiText.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Row(
                        modifier = Modifier
                            .clickable(onClick = onReplayVoice)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Replay Voice",
                            tint = NeonCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "Replay Voice",
                            style = MaterialTheme.typography.labelSmall.copy(color = NeonCyan, fontSize = 10.sp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceOrb(
    state: AssistantState,
    amplitude: Float
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_anim")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val dynamicAmp = (amplitude * 1.5f).coerceIn(0f, 1f)

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .scale(pulseScale)
    ) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val baseRadius = size.minDimension / 3.4f

        val (c1, c2) = when (state) {
            AssistantState.SPEAKING -> Pair(NeonMagenta, NeonPurple)
            AssistantState.LISTENING -> Pair(NeonCyan, Color(0xFF00B0FF))
            AssistantState.CONNECTING -> Pair(Color(0xFFFF9100), NeonMagenta)
            AssistantState.ERROR -> Pair(Color(0xFFFF1744), Color(0xFFB71C1C))
            AssistantState.IDLE -> Pair(NeonPurple.copy(alpha = 0.5f), Color(0xFF3F51B5).copy(alpha = 0.4f))
        }

        for (i in 1..3) {
            val rippleRadius = baseRadius + (i * 24f) + (dynamicAmp * 32f * i)
            drawCircle(
                color = c1.copy(alpha = (0.28f / i) * (0.4f + dynamicAmp * 0.6f)),
                radius = rippleRadius,
                center = center,
                style = Stroke(width = 2.5f)
            )
        }

        val gradient = Brush.radialGradient(
            colors = listOf(c1, c2, DarkBackground),
            center = center,
            radius = baseRadius * (1f + dynamicAmp * 0.4f)
        )
        drawCircle(
            brush = gradient,
            radius = baseRadius * (1f + dynamicAmp * 0.25f),
            center = center
        )

        val orbitRadius = baseRadius * 1.15f
        for (j in 0 until 8) {
            val angleRad = Math.toRadians((rotationAngle + j * 45).toDouble())
            val x = centerX + orbitRadius * cos(angleRad).toFloat()
            val y = centerY + orbitRadius * sin(angleRad).toFloat()
            drawCircle(
                color = c2,
                radius = 3.5f + dynamicAmp * 3f,
                center = androidx.compose.ui.geometry.Offset(x, y)
            )
        }
    }
}

@Composable
fun MainPowerButton(
    state: AssistantState,
    onClick: () -> Unit
) {
    val isActive = state == AssistantState.LISTENING || state == AssistantState.SPEAKING

    val buttonGradient = if (isActive) {
        Brush.linearGradient(listOf(NeonMagenta, NeonPurple))
    } else {
        Brush.linearGradient(listOf(Color(0xFF2E274D), Color(0xFF1B1633)))
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(52.dp)
            .shadow(if (isActive) 12.dp else 4.dp, CircleShape)
            .clip(CircleShape)
            .background(buttonGradient)
            .border(
                2.dp,
                if (isActive) NeonCyan else Color.White.copy(alpha = 0.2f),
                CircleShape
            )
            .clickable(onClick = onClick)
            .testTag("power_mic_toggle_button")
    ) {
        Icon(
            imageVector = if (isActive) Icons.Default.Mic else Icons.Default.PowerSettingsNew,
            contentDescription = "Toggle Assistant Power / Mic",
            tint = if (isActive) Color.White else NeonCyan,
            modifier = Modifier.size(26.dp)
        )
    }
}

@Composable
fun QuickPromptChips(onPromptSelected: (String) -> Unit) {
    val prompts = listOf(
        "Torch on karo",
        "Battery kitni hai?",
        "Volume badhao",
        "WhatsApp kholo",
        "Camera kholo",
        "YouTube open karo",
        "Call Mom",
        "Alarm lagao"
    )

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(prompts) { prompt ->
            FilterChip(
                selected = false,
                onClick = { onPromptSelected(prompt) },
                label = {
                    Text(
                        text = prompt,
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.White)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = DarkCard,
                    labelColor = Color.White
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = NeonPurple.copy(alpha = 0.4f),
                    enabled = true,
                    selected = false
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("prompt_chip_$prompt")
            )
        }
    }
}

@Composable
fun ContactChoicesDialog(
    contacts: List<ContactMatch>,
    onSelect: (ContactMatch) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Multiple Contacts Found",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column {
                Text(
                    text = "Arushi found more than one contact. Which one should she call?",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                contacts.forEach { contact ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(contact) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Call",
                            tint = NeonCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = contact.name,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = contact.phoneNumber,
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = DarkCard,
        titleContentColor = Color.White,
        textContentColor = Color.LightGray
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticLogsSheet(
    logs: List<String>,
    onDismiss: () -> Unit,
    onClear: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkSurface,
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Audio & API Pipeline Logs",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                TextButton(onClick = onClear) {
                    Text("Clear", color = NeonCyan)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .background(Color.Black, RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                if (logs.isEmpty()) {
                    item {
                        Text(
                            text = "No logs yet. Activate Arushi to see live audio and WebSocket events.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                items(logs) { logLine ->
                    Text(
                        text = logLine,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = when {
                                logLine.contains("error", ignoreCase = true) || logLine.contains("failure", ignoreCase = true) -> Color(0xFFFF5252)
                                logLine.contains("audio", ignoreCase = true) || logLine.contains("PCM", ignoreCase = true) || logLine.contains("speak", ignoreCase = true) -> NeonCyan
                                logLine.contains("connected", ignoreCase = true) -> Color(0xFF69F0AE)
                                else -> Color(0xFFE0E0E0)
                            },
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        ),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}
