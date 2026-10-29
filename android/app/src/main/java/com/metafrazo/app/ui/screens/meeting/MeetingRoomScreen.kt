package com.metafrazo.app.ui.screens.meeting

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.metafrazo.app.core.meeting.MeetingCode
import com.metafrazo.app.core.screen.ScreenTranslator
import com.metafrazo.app.core.translate.DeviceTranslator
import com.metafrazo.app.feature.captions.CaptionsViewModel
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
    val clipboard = LocalClipboardManager.current
    var tiles by remember { mutableStateOf<List<Tile>>(emptyList()) }
    var activeSpeakers by remember { mutableStateOf<Set<String>>(emptySet()) }
    var menuOpen by remember { mutableStateOf(false) }
    var shareSheetOpen by remember { mutableStateOf(false) }
    var inviteBannerDismissed by rememberSaveable { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var translateScreen by remember { mutableStateOf(false) }
    val translationScope = rememberCoroutineScope()
    val screenTranslator = remember { ScreenTranslator(translationScope) }
    val screenTranslation by screenTranslator.state.collectAsState()

    // Live captions overlaid on the call. Shares the active LiveKit room via MeetingViewModel.activeRoom.
    val captionsVm: CaptionsViewModel = viewModel(
        factory = CaptionsViewModel.factory(context.applicationContext as android.app.Application),
        viewModelStoreOwner = context as androidx.activity.ComponentActivity,
    )
    val captionsState by captionsVm.state.collectAsState()
    var captionsOn by remember { mutableStateOf(false) }
    var captionLangPickerOpen by remember { mutableStateOf(false) }
    val captionMicLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            captionsVm.startListening()
        } else {
            captionsOn = false
            captionsVm.onMicPermissionDenied()
        }
    }

    fun enableCaptions() {
        captionsOn = true
        captionsVm.attachToRoom()
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            captionsVm.startListening()
        } else {
            captionMicLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    fun disableCaptions() {
        captionsOn = false
        captionsVm.stopListening()
    }

    DisposableEffect(screenTranslator) {
        onDispose {
            screenTranslator.close()
            captionsVm.stopListening()
        }
    }

    val projectionManager = remember { context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager }
    val screenLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK && res.data != null) vm.startScreenShare(res.data!!)
    }

    LaunchedEffect(state.code) { captionsVm.setMeetingCode(state.code) }

    // Captions are on by default on the video screen: attach and start dictation once connected.
    LaunchedEffect(state.status) {
        if (state.status == "connected" && !captionsOn) enableCaptions()
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
                    onCopyCode = { if (code.isNotEmpty()) clipboard.setText(AnnotatedString(code)) },
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
                    // Hidden while captions are on so the two never stack at the bottom of the video.
                    if (participantCount <= 1 && tiles.isNotEmpty() && screenShare == null && !inviteBannerDismissed && !captionsOn) {
                        WaitingBanner(
                            code = code,
                            onInvite = { shareSheetOpen = true },
                            onDismiss = { inviteBannerDismissed = true },
                            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                        )
                    }
                    if (captionsOn) {
                        CaptionOverlay(
                            captions = captionsState.captions,
                            spokenLang = captionsState.spokenLang,
                            targetLang = captionsState.targetLang,
                            partial = captionsState.partial,
                            partialTranslated = captionsState.partialTranslated,
                            listening = captionsState.listening,
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }

                ControlBar(
                    micEnabled = state.micEnabled,
                    cameraEnabled = state.cameraEnabled,
                    screenSharing = state.screenSharing,
                    captionsOn = captionsOn,
                    onCaptions = { if (captionsOn) disableCaptions() else enableCaptions() },
                    onMic = vm::toggleMic,
                    onCamera = vm::toggleCamera,
                    onScreenShare = {
                        if (state.screenSharing) vm.stopScreenShare()
                        else screenLauncher.launch(projectionManager.createScreenCaptureIntent())
                    },
                    onLeave = { confirmLeave = true },
                    menuOpen = menuOpen,
                    onMenu = { menuOpen = it },
                    menu = {
                        DropdownMenuItem(
                            text = { Text("Caption language (${DeviceTranslator.labels[captionsState.targetLang] ?: captionsState.targetLang.uppercase()})") },
                            leadingIcon = { Icon(Icons.Default.ClosedCaption, null) },
                            onClick = { menuOpen = false; captionLangPickerOpen = true },
                        )
                        DropdownMenuItem(
                            text = { Text(if (captionsState.speakTranslations) "Stop speaking translations" else "Speak translations aloud") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.VolumeUp, null) },
                            onClick = { menuOpen = false; captionsVm.setSpeakTranslations(!captionsState.speakTranslations) },
                        )
                        DropdownMenuItem(
                            text = { Text("Switch camera") },
                            leadingIcon = { Icon(Icons.Default.Cameraswitch, null) },
                            enabled = state.cameraEnabled,
                            onClick = { menuOpen = false; vm.switchCamera() },
                        )
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

        if (captionLangPickerOpen) {
            ModalBottomSheet(onDismissRequest = { captionLangPickerOpen = false }) {
                CaptionLanguageSheet(
                    spokenLang = captionsState.spokenLang,
                    targetLang = captionsState.targetLang,
                    onSpokenLang = { captionsVm.setSpokenLang(it) },
                    onTargetLang = { captionsVm.setTargetLang(it) },
                    status = captionsState.status,
                )
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
    onCopyCode: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(connectedAt) {
        while (connectedAt != null) { now = System.currentTimeMillis(); delay(1000) }
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            // The meeting code is always pinned at the top, tappable to copy.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = onCopyCode,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = CircleShape,
                ) {
                    Row(
                        Modifier.padding(start = 12.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            code.ifBlank { "Meeting" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy meeting code", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }
                if (recording) {
                    Spacer(Modifier.width(8.dp))
                    Row(
                        Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.error).padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = Color.White, modifier = Modifier.size(8.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("REC", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            val sub = buildString {
                if (reconnecting) append("Reconnecting…") else {
                    if (title.isNotBlank() && title != "Meeting") append(title).append("  ·  ")
                    connectedAt?.let { append(formatElapsed(now - it)).append("  ·  ") }
                    append(if (participantCount == 1) "1 person" else "$participantCount people")
                }
            }
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = if (reconnecting) Color(0xFFFFC857) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp).semantics { if (reconnecting) liveRegion = LiveRegionMode.Polite },
            )
        }
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
private fun WaitingBanner(code: String, onInvite: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("You're the only one here", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text("Share code $code to invite others", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onInvite) { Text("Invite") }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss invite banner", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ControlBar(
    micEnabled: Boolean,
    cameraEnabled: Boolean,
    screenSharing: Boolean,
    captionsOn: Boolean,
    onCaptions: () -> Unit,
    onMic: () -> Unit,
    onCamera: () -> Unit,
    onScreenShare: () -> Unit,
    onLeave: () -> Unit,
    menuOpen: Boolean,
    onMenu: (Boolean) -> Unit,
    menu: @Composable () -> Unit,
) {
    LazyRow(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
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
            icon = Icons.Default.ClosedCaption,
            description = if (captionsOn) "Turn off live captions" else "Turn on live captions",
            off = false,
            highlighted = captionsOn,
            onClick = onCaptions,
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

/** One caption line as rendered; [id] stays stable while a live partial updates. */
private data class CaptionLine(
    val id: String,
    val speaker: String,
    val fromLang: String,
    val main: String,
    val sub: String?,
    val interim: Boolean,
)

private val CaptionAccent = Color(0xFF8AB4FF)
private val CaptionLive = Color(0xFF5BE49B)

/** Caption text in the reader's language, falling back to the original transcript. */
private fun com.metafrazo.app.feature.captions.CaptionItem.textFor(targetLang: String): String =
    (if (sourceLang == targetLang) null else translations[targetLang]?.takeIf { it.isNotBlank() }) ?: original

@Composable
private fun CaptionOverlay(
    captions: List<com.metafrazo.app.feature.captions.CaptionItem>,
    spokenLang: String,
    targetLang: String,
    partial: String,
    partialTranslated: String,
    listening: Boolean,
    modifier: Modifier = Modifier,
) {
    val latest = captions.lastOrNull()
    val speakingNow = partial.isNotBlank()

    // Prefer the live partial while the user is mid-sentence; otherwise show the last final line.
    val line: CaptionLine = when {
        speakingNow -> {
            val translated = partialTranslated.takeIf { it.isNotBlank() && spokenLang != targetLang }
            CaptionLine(
                id = "live",
                speaker = "You",
                fromLang = spokenLang,
                main = translated ?: partial,
                sub = if (translated != null) partial else null,
                interim = true,
            )
        }
        latest != null -> {
            val main = latest.textFor(targetLang)
            CaptionLine(
                id = "${latest.speaker}:${latest.ts}",
                speaker = latest.speaker,
                fromLang = latest.sourceLang,
                main = main,
                sub = latest.original.takeIf { it.isNotBlank() && it != main },
                interim = false,
            )
        }
        listening -> CaptionLine(id = "idle", speaker = "You", fromLang = spokenLang, main = "", sub = null, interim = true)
        else -> return
    }

    // The line before the current one, dimmed above it, so short sentences keep their context.
    val previous = when {
        line.id == "idle" -> null
        speakingNow -> latest
        else -> captions.getOrNull(captions.size - 2)
    }?.let { it.speaker to it.textFor(targetLang) }

    Surface(
        color = Color(0xE6101218),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.animateContentSize(tween(160)),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 14.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CaptionHeader(
                speaker = line.speaker,
                live = line.interim,
                fromLang = line.fromLang,
                targetLang = targetLang,
            )

            if (previous != null) {
                Text(
                    if (previous.first == line.speaker) previous.second else "${previous.first}: ${previous.second}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.45f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Animate only when the line identity changes (a new final line). Live partials keep the
            // same id, so their text updates in place instead of re-animating on every word.
            AnimatedContent(
                targetState = line,
                contentKey = { it.id },
                transitionSpec = {
                    (fadeIn(tween(160)) + slideInVertically(tween(180)) { it / 5 })
                        .togetherWith(fadeOut(tween(80)))
                },
                label = "captionLine",
            ) { shown ->
                if (shown.id == "idle") {
                    ListeningDots()
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            shown.main,
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp, lineHeight = 26.sp),
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!shown.sub.isNullOrBlank()) {
                            Text(
                                shown.sub,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.6f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptionHeader(speaker: String, live: Boolean, fromLang: String, targetLang: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Breathing dot while the recognizer is hearing speech; a static CC glyph otherwise.
        if (live) {
            val pulse = rememberInfiniteTransition(label = "liveDot")
            val dotAlpha by pulse.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                label = "liveDotAlpha",
            )
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(CaptionLive.copy(alpha = dotAlpha)),
            )
        } else {
            Icon(Icons.Default.ClosedCaption, contentDescription = null, tint = CaptionAccent, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            speaker,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = CaptionAccent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.weight(1f))
        val badge = if (fromLang == targetLang) {
            fromLang.uppercase()
        } else {
            "${fromLang.uppercase()} → ${targetLang.uppercase()}"
        }
        Surface(color = Color.White.copy(alpha = 0.10f), shape = CircleShape) {
            Text(
                badge,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .padding(horizontal = 10.dp, vertical = 3.dp)
                    .semantics {
                        contentDescription = "Captions in ${DeviceTranslator.labels[targetLang] ?: targetLang}"
                    },
            )
        }
    }
}

/** Three staggered dots shown while waiting for the first words. */
@Composable
private fun ListeningDots() {
    val transition = rememberInfiniteTransition(label = "listeningDots")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .height(26.dp)
            .semantics { contentDescription = "Listening" },
    ) {
        Text(
            "Listening",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.7f),
        )
        repeat(3) { i ->
            val a by transition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(500, delayMillis = i * 150),
                    RepeatMode.Reverse,
                ),
                label = "dot$i",
            )
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = a)),
            )
        }
    }
}

@Composable
private fun CaptionLanguageSheet(
    spokenLang: String,
    targetLang: String,
    onSpokenLang: (String) -> Unit,
    onTargetLang: (String) -> Unit,
    status: String,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Captions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("I speak", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(com.metafrazo.app.core.translate.DeviceTranslator.supported) { lang ->
                androidx.compose.material3.FilterChip(
                    selected = spokenLang == lang,
                    onClick = { onSpokenLang(lang) },
                    label = { Text(DeviceTranslator.labels[lang] ?: lang.uppercase()) },
                )
            }
        }
        Text("Show captions in", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(com.metafrazo.app.core.translate.DeviceTranslator.supported) { lang ->
                androidx.compose.material3.FilterChip(
                    selected = targetLang == lang,
                    onClick = { onTargetLang(lang) },
                    label = { Text(DeviceTranslator.labels[lang] ?: lang.uppercase()) },
                )
            }
        }
        if (status.isNotBlank()) {
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
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
