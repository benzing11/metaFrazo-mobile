package com.metafrazo.app.ui.screens.meeting

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.metafrazo.app.core.auth.AuthRepository
import com.metafrazo.app.core.meeting.MeetingCode
import com.metafrazo.app.feature.meeting.MeetingViewModel

enum class LobbyMode { Start, Join }

/**
 * Pre-join screen shared by "Start" and "Join": pick a display name, choose mic/camera,
 * then create-and-join (Start) or join by code (Join). Navigates once connected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingLobby(
    mode: LobbyMode,
    vm: MeetingViewModel,
    initialCode: String = "",
    onBack: () -> Unit,
    onConnected: () -> Unit,
) {
    val context = LocalContext.current
    val state by vm.state.collectAsState()
    var name by rememberSaveable { mutableStateOf(AuthRepository.meetingName(context)) }
    var title by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf(initialCode) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(Unit) { vm.resetIfIdle() }
    LaunchedEffect(state.status) { if (state.status == "connected") onConnected() }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        // Respect a denial by switching that device off before joining instead of failing later.
        if (result[Manifest.permission.RECORD_AUDIO] == false) vm.setPreJoinMic(false)
        if (result[Manifest.permission.CAMERA] == false) vm.setPreJoinCamera(false)
        pendingAction?.invoke()
        pendingAction = null
    }

    fun withPermissions(action: () -> Unit) {
        val needed = buildList {
            if (state.micEnabled) add(Manifest.permission.RECORD_AUDIO)
            if (state.cameraEnabled) add(Manifest.permission.CAMERA)
        }.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isEmpty()) action() else {
            pendingAction = action
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    val normalizedCode = MeetingCode.extract(code)
    val canSubmit = name.isNotBlank() && !state.isBusy && (mode == LobbyMode.Start || MeetingCode.isValid(normalizedCode))

    fun submit() {
        if (!canSubmit) return
        AuthRepository.setMeetingName(context, name)
        withPermissions {
            if (mode == LobbyMode.Start) vm.startNewMeeting(title) else vm.joinMeeting(normalizedCode)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (mode == LobbyMode.Start) "New meeting" else "Join a meeting") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            PreviewCard(
                name = name,
                micEnabled = state.micEnabled,
                cameraEnabled = state.cameraEnabled,
                onToggleMic = { vm.setPreJoinMic(!state.micEnabled) },
                onToggleCamera = { vm.setPreJoinCamera(!state.cameraEnabled) },
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Your name") },
                    supportingText = { Text("Shown to everyone in the meeting") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (mode == LobbyMode.Start) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it.take(80) },
                        label = { Text("Meeting title (optional)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { submit() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("Meeting code or link") },
                        placeholder = { Text("e.g. AB3XYZ") },
                        isError = code.isNotBlank() && !MeetingCode.isValid(normalizedCode),
                        supportingText = {
                            Text(
                                if (code.isNotBlank() && !MeetingCode.isValid(normalizedCode)) "Codes are ${MeetingCode.LENGTH} letters or numbers"
                                else "Paste a join link or type the code",
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { submit() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            AnimatedVisibility(visible = state.error != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            state.error.orEmpty(),
                            Modifier.weight(1f).padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = { vm.clearError() }) { Text("Dismiss") }
                    }
                }
            }

            Button(
                onClick = { submit() },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                if (state.isBusy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.size(12.dp))
                }
                Text(
                    when (state.status) {
                        "creating" -> "Creating meeting…"
                        "joining" -> "Joining…"
                        "connecting" -> "Connecting…"
                        else -> if (mode == LobbyMode.Start) "Start meeting" else "Join meeting"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (name.isBlank()) {
                Text(
                    "Enter your name to continue.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PreviewCard(
    name: String,
    micEnabled: Boolean,
    cameraEnabled: Boolean,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        // Reserve room at the bottom so the caption never collides with the device toggles.
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(bottom = 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    initials(name),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                if (cameraEnabled) "Camera will turn on when you join" else "Camera is off",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DeviceToggle(
                checked = micEnabled,
                onToggle = onToggleMic,
                onIcon = { Icon(Icons.Default.Mic, contentDescription = "Microphone on, tap to turn off") },
                offIcon = { Icon(Icons.Default.MicOff, contentDescription = "Microphone off, tap to turn on") },
            )
            DeviceToggle(
                checked = cameraEnabled,
                onToggle = onToggleCamera,
                onIcon = { Icon(Icons.Default.Videocam, contentDescription = "Camera on, tap to turn off") },
                offIcon = { Icon(Icons.Default.VideocamOff, contentDescription = "Camera off, tap to turn on") },
            )
        }
    }
}

@Composable
private fun DeviceToggle(
    checked: Boolean,
    onToggle: () -> Unit,
    onIcon: @Composable () -> Unit,
    offIcon: @Composable () -> Unit,
) {
    FilledIconToggleButton(
        checked = !checked,
        onCheckedChange = { onToggle() },
        modifier = Modifier.size(56.dp),
        // Off state is neutral (white) to match the in-call RoundControl; red is reserved
        // for destructive actions like leaving the call.
        colors = IconButtonDefaults.filledIconToggleButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            checkedContainerColor = Color.White,
            checkedContentColor = Color(0xFF0E1015),
        ),
    ) { if (checked) onIcon() else offIcon() }
}

internal fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts[0].take(1) + parts.last().take(1)).uppercase()
    }
}
