package com.metafrazo.app.ui.screens.meeting

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.automirrored.filled.StopScreenShare
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.metafrazo.app.core.meeting.MeetingCode
import com.metafrazo.app.core.screen.ScreenTranslator
import com.metafrazo.app.feature.meeting.MeetingViewModel
import com.metafrazo.app.ui.components.ParticipantTile
import com.metafrazo.app.ui.components.ScreenTranslateOverlay
import io.livekit.android.events.RoomEvent
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.delay

private data class Tile(
    val key: String,
    val name: String,
    val participantId: String,
    val track: VideoTrack?,
    val isLocal: Boolean,
    val isMuted: Boolean,
    val isScreenShare: Boolean,
)

// The call stage is always dark, like most video apps, regardless of system theme.
private val StageColors = darkColorScheme(
    primary = Color(0xFF8AB4FF),
    onPrimary = Color(0xFF002A77),
    primaryContainer = Color(0xFF2C3E73),
    onPrimaryContainer = Color(0xFFDCE1FF),
    background = Color(0xFF0E1015),
    onBackground = Color(0xFFE6E7EC),
    surface = Color(0xFF181B22),
    onSurface = Color(0xFFE6E7EC),
    surfaceVariant = Color(0xFF242833),
    onSurfaceVariant = Color(0xFFB4B8C4),
    surfaceContainerHigh = Color(0xFF242833),
    error = Color(0xFFE5484D),
    onError = Color.White,
    errorContainer = Color(0xFF5C1A1D),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF3A3F4B),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingRoomScreen(
    onLeave: () -> Unit,
    onCaptions: () -> Unit = {},
    onChat: (String) -> Unit = {},
    onNotes: (String) -> Unit = {},
    onTranscripts: (String) -> Unit = {},
    vm: MeetingViewModel = viewModel(
        factory = MeetingViewModel.factory(LocalContext.current.applicationContext as android.app.Application),
        viewModelStoreOwner = LocalContext.current as androidx.activity.ComponentActivity,
    ),
) {
    val state by vm.state.collectAsState()
    val room = vm.room
    val context = LocalContext.current
    var tiles by remember { mutableStateOf<List<Tile>>(emptyList()) }
    var activeSpeakers by remember { mutableStateOf<Set<String>>(emptySet()) }
    var menuOpen by remember { mutableStateOf(false) }
    var shareSheetOpen by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var translateScreen by remember { mutableStateOf(false) }
    val translationScope = rememberCoroutineScope()
    val screenTranslator = remember { ScreenTranslator(translationScope) }
    val screenTranslation by screenTranslator.state.collectAsState()

    DisposableEffect(screenTranslator) {
        onDispose { screenTranslator.close() }
    }

    val projectionManager = remember { context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager }
    val screenLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK && res.data != null) vm.startScreenShare(res.data!!)
    }

    fun tilesFor(p: Participant, isLocal: Boolean): List<Tile> {
        val name = p.name?.takeIf { it.isNotBlank() } ?: p.identity?.value ?: "Participant"
        val id = p.identity?.value ?: name
        val videos = p.videoTrackPublications.mapNotNull { (pub, track) ->
            val vt = track as? VideoTrack ?: return@mapNotNull null
            if (pub.muted) return@mapNotNull null
            Tile("$id:${pub.sid}", name, id, vt, isLocal, !p.isMicrophoneEnabled, pub.source == Track.Source.SCREEN_SHARE)
        }
        val camera = videos.filterNot { it.isScreenShare }
        val avatar = if (camera.isEmpty()) listOf(Tile("$id:avatar", name, id, null, isLocal, !p.isMicrophoneEnabled, false)) else emptyList()
        return videos + avatar
    }

    fun refresh() {
        val r = room ?: return
        tiles = tilesFor(r.localParticipant, true) + r.remoteParticipants.values.flatMap { tilesFor(it, false) }
    }

    LaunchedEffect(room) {
        if (room == null) return@LaunchedEffect
        refresh()
        room.events.events.collect { event ->
            if (event is RoomEvent.ActiveSpeakersChanged) {
                activeSpeakers = event.speakers.mapNotNull { it.identity?.value }.toSet()
            }
            refresh()
        }
    }
    LaunchedEffect(state.micEnabled, state.cameraEnabled, state.screenSharing) { refresh() }
    // Room is gone (left, remote disconnect, or process restore without a live call): exit the screen.
    LaunchedEffect(state.status, room) { if (state.status == "left" || (room == null && !state.isBusy)) onLeave() }

    BackHandler { confirmLeave = true }

    val code = state.code.orEmpty()
    val participantCount = tiles.map { it.participantId }.distinct().size
    val screenShare = tiles.firstOrNull { it.isScreenShare }
    val people = tiles.filterNot { it.isScreenShare }

    LaunchedEffect(translateScreen, screenShare?.track) {
        val track = screenShare?.track
        if (translateScreen && track != null && screenShare.isLocal.not()) {
            screenTranslator.attach(track)
        } else {
            screenTranslator.detach()
        }
    }

    MaterialTheme(colorScheme = StageColors, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                RoomHeader(
                    title = state.title ?: "Meeting",
                    code = code,
                    connectedAt = state.connectedAt,
                    participantCount = participantCount,
                    reconnecting = state.reconnecting,
                    recording = state.recording,
                    onInvite = { shareSheetOpen = true },
                )

                AnimatedVisibility(visible = state.error != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    ) {
                        Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                state.error.orEmpty(),
                                Modifier.weight(1f).padding(vertical = 10.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            TextButton(onClick = { vm.clearError() }) { Text("OK") }
                        }
                    }
                }

                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    when {
                        tiles.isEmpty() -> Column(
                            Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text("Connecting…", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                        }
                        screenShare != null -> SpotlightLayout(
                            share = screenShare,
                            people = people,
                            activeSpeakers = activeSpeakers,
                            room = room,
                            overlayEnabled = translateScreen && !screenShare.isLocal,
                            overlayRegions = screenTranslation.regions,
                            overlayAspect = screenTranslation.aspect,
                            overlayStatus = screenTranslation.status,
                        )
                        else -> GridLayout(people, activeSpeakers, room)
                    }
                    if (participantCount <= 1 && tiles.isNotEmpty() && screenShare == null) {
                        WaitingBanner(code = code, onInvite = { shareSheetOpen = true }, modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp))
                    }
                }

                ControlBar(
                    micEnabled = state.micEnabled,
                    cameraEnabled = state.cameraEnabled,
                    screenSharing = state.screenSharing,
                    onMic = vm::toggleMic,
                    onCamera = vm::toggleCamera,
                    onSwitchCamera = vm::switchCamera,
                    onScreenShare = {
                        if (state.screenSharing) vm.stopScreenShare()
                        else screenLauncher.launch(projectionManager.createScreenCaptureIntent())
                    },
                    onLeave = { confirmLeave = true },
                    menuOpen = menuOpen,
                    onMenu = { menuOpen = it },
                    menu = {
                        DropdownMenuItem(text = { Text("Live captions") }, leadingIcon = { Icon(Icons.Default.ClosedCaption, null) }, onClick = { menuOpen = false; onCaptions() })
                        DropdownMenuItem(text = { Text("Chat") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Chat, null) }, onClick = { menuOpen = false; if (code.isNotEmpty()) onChat(code) })
                        DropdownMenuItem(text = { Text("Notes") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, null) }, onClick = { menuOpen = false; if (code.isNotEmpty()) onNotes(code) })
                        DropdownMenuItem(text = { Text("Transcript") }, leadingIcon = { Icon(Icons.Default.Description, null) }, onClick = { menuOpen = false; if (code.isNotEmpty()) onTranscripts(code) })
                        DropdownMenuItem(
                            text = { Text(if (translateScreen) "Turn off screen translation" else "Translate shared screen") },
                            leadingIcon = { Icon(Icons.Default.Translate, null) },
                            enabled = screenShare != null && !screenShare.isLocal,
                            onClick = { menuOpen = false; translateScreen = !translateScreen },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(if (state.recording) "Stop recording" else "Start recording") },
                            leadingIcon = { Icon(Icons.Default.FiberManualRecord, null, tint = MaterialTheme.colorScheme.error) },
                            enabled = !state.recordingBusy,
                            onClick = { menuOpen = false; vm.toggleRecording() },
                        )
                        DropdownMenuItem(text = { Text("Invite people") }, leadingIcon = { Icon(Icons.Default.PersonAdd, null) }, onClick = { menuOpen = false; shareSheetOpen = true })
                    },
                )
            }
        }

        if (shareSheetOpen) {
            ModalBottomSheet(onDismissRequest = { shareSheetOpen = false }) {
                InviteSheet(code = code, onDone = { shareSheetOpen = false })
            }
        }

        if (confirmLeave) {
            AlertDialog(
                onDismissRequest = { confirmLeave = false },
                title = { Text("Leave meeting?") },
                text = { Text("You can rejoin with code $code.") },
                confirmButton = {
                    Button(
                        onClick = { confirmLeave = false; vm.leave() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                    ) { Text("Leave") }
                },
                dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Stay") } },
            )
        }
    }
}

@Composable
private fun RoomHeader(
    title: String,
    code: String,
    connectedAt: Long?,
    participantCount: Int,
    reconnecting: Boolean,
    recording: Boolean,
    onInvite: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(connectedAt) {
        while (connectedAt != null) { now = System.currentTimeMillis(); delay(1000) }
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (recording) {
                    Spacer(Modifier.width(8.dp))
                    Row(
                        Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.error).padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = Color.White, modifier = Modifier.size(8.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("REC", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
            val sub = buildString {
                if (reconnecting) append("Reconnecting…") else {
                    append(code)
                    connectedAt?.let { append("  ·  ").append(formatElapsed(now - it)) }
                    append("  ·  ").append(if (participantCount == 1) "1 person" else "$participantCount people")
                }
            }
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = if (reconnecting) Color(0xFFFFC857) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { if (reconnecting) liveRegion = LiveRegionMode.Polite },
            )
        }
        IconButton(onClick = onInvite) { Icon(Icons.Default.PersonAdd, contentDescription = "Invite people", tint = MaterialTheme.colorScheme.onBackground) }
    }
}

@Composable
private fun GridLayout(people: List<Tile>, activeSpeakers: Set<String>, room: io.livekit.android.room.Room?) {
    val config = LocalConfiguration.current
    val landscape = config.screenWidthDp > config.screenHeightDp
    val columns = when {
        people.size <= 1 -> 1
        people.size == 2 -> if (landscape) 2 else 1
        landscape -> 3
        else -> 2
    }
    val aspect = when {
        people.size == 1 -> if (landscape) 16f / 9f else 3f / 4f
        people.size == 2 && !landscape -> 4f / 3f
        else -> 1f
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(people, key = { it.key }) { tile ->
            ParticipantTile(
                name = tile.name,
                track = tile.track,
                room = room,
                isSpeaking = tile.participantId in activeSpeakers,
                isMuted = tile.isMuted,
                isLocal = tile.isLocal,
                isScreenShare = false,
                modifier = Modifier.fillMaxWidth().aspectRatio(aspect),
            )
        }
    }
}

@Composable
private fun SpotlightLayout(
    share: Tile,
    people: List<Tile>,
    activeSpeakers: Set<String>,
    room: io.livekit.android.room.Room?,
    overlayEnabled: Boolean,
    overlayRegions: List<com.metafrazo.app.ui.components.OverlayRegion>,
    overlayAspect: Float,
    overlayStatus: String,
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            ParticipantTile(
                name = share.name,
                track = share.track,
                room = room,
                isSpeaking = false,
                isMuted = false,
                isLocal = share.isLocal,
                isScreenShare = true,
                modifier = Modifier.fillMaxSize(),
            )
            if (overlayEnabled) {
                ScreenTranslateOverlay(
                    regions = overlayRegions,
                    imageAspect = overlayAspect,
                    modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium),
                )
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        overlayStatus.ifBlank { "Translated overlay" },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(people, key = { it.key }) { tile ->
                ParticipantTile(
                    name = tile.name,
                    track = tile.track,
                    room = room,
                    isSpeaking = tile.participantId in activeSpeakers,
                    isMuted = tile.isMuted,
                    isLocal = tile.isLocal,
                    isScreenShare = false,
                    modifier = Modifier.size(width = 112.dp, height = 140.dp),
                )
            }
        }
    }
}

@Composable
private fun WaitingBanner(code: String, onInvite: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("You're the only one here", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text("Share code $code to invite others", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onInvite) { Text("Invite") }
        }
    }
}

@Composable
private fun ControlBar(
    micEnabled: Boolean,
    cameraEnabled: Boolean,
    screenSharing: Boolean,
    onMic: () -> Unit,
    onCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onScreenShare: () -> Unit,
    onLeave: () -> Unit,
    menuOpen: Boolean,
    onMenu: (Boolean) -> Unit,
    menu: @Composable () -> Unit,
) {
    LazyRow(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item { RoundControl(
            icon = if (micEnabled) Icons.Default.Mic else Icons.Default.MicOff,
            description = if (micEnabled) "Mute microphone" else "Unmute microphone",
            off = !micEnabled,
            onClick = onMic,
        ) }
        item { RoundControl(
            icon = if (cameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
            description = if (cameraEnabled) "Turn camera off" else "Turn camera on",
            off = !cameraEnabled,
            onClick = onCamera,
        ) }
        item { RoundControl(
            icon = Icons.Default.Cameraswitch,
            description = "Switch camera",
            off = false,
            enabled = cameraEnabled,
            onClick = onSwitchCamera,
        ) }
        item { RoundControl(
            icon = if (screenSharing) Icons.AutoMirrored.Filled.StopScreenShare else Icons.AutoMirrored.Filled.ScreenShare,
            description = if (screenSharing) "Stop sharing screen" else "Share screen",
            off = false,
            highlighted = screenSharing,
            onClick = onScreenShare,
        ) }
        item { Box {
            RoundControl(icon = Icons.Default.MoreVert, description = "More options", off = false, onClick = { onMenu(true) })
            DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenu(false) }) { menu() }
        } }
        item { FilledIconButton(
            onClick = onLeave,
            modifier = Modifier.size(width = 64.dp, height = 52.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
        ) { Icon(Icons.Default.CallEnd, contentDescription = "Leave meeting") } }
    }
}

@Composable
private fun RoundControl(
    icon: ImageVector,
    description: String,
    off: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    highlighted: Boolean = false,
) {
    val container = when {
        off -> Color.White
        highlighted -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when {
        off -> Color(0xFF0E1015)
        highlighted -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(52.dp),
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        ),
    ) { Icon(icon, contentDescription = description) }
}

@Composable
private fun InviteSheet(code: String, onDone: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val link = MeetingCode.joinLink(code)
    var copied by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Invite people", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("Share this code. Others can enter it on the Join screen.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Text(
                code.chunked(3).joinToString(" "),
                style = MaterialTheme.typography.displaySmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 20.dp).fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { clipboard.setText(AnnotatedString(code)); copied = true },
                modifier = Modifier.weight(1f).height(48.dp),
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (copied) "Copied" else "Copy code")
            }
            Button(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Join my MetaFrazo meeting with code $code\n$link")
                    }
                    context.startActivity(Intent.createChooser(send, "Share meeting"))
                    onDone()
                },
                modifier = Modifier.weight(1f).height(48.dp),
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Share")
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

private fun formatElapsed(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
