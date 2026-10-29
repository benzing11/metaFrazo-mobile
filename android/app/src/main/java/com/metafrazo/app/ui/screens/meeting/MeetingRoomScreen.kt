package com.metafrazo.app.ui.screens.meeting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.collect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.metafrazo.app.feature.meeting.MeetingViewModel
import io.livekit.android.compose.ui.VideoTrackView
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.livekit.android.events.RoomEvent
import io.livekit.android.room.Room
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.VideoTrack

@Composable
fun MeetingRoomScreen(onLeave: () -> Unit, onCaptions: () -> Unit = {}, onChat: (String) -> Unit = {}, onNotes: (String) -> Unit = {}, onTranscripts: (String) -> Unit = {}, vm: MeetingViewModel = viewModel(factory = MeetingViewModel.factory(LocalContext.current.applicationContext as android.app.Application), viewModelStoreOwner = LocalContext.current as androidx.activity.ComponentActivity)) {
    val state by vm.state.collectAsState()
    val room = vm.room
    val context = LocalContext.current
    data class TrackInfo(val track: VideoTrack, val participantId: String, val name: String)
    var tracks by remember { mutableStateOf<List<TrackInfo>>(emptyList()) }
    var activeSpeakers by remember { mutableStateOf<Set<String>>(emptySet()) }
    var recording by remember { mutableStateOf(false) }
    var egressId by remember { mutableStateOf<String?>(null) }
    var recordingId by remember { mutableStateOf<String?>(null) }
    val projectionManager = remember { context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager }
    val screenLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK && res.data != null) {
            vm.startScreenShare(res.data!!)
        }
    }

    val updateTracks = {
        val list = mutableListOf<TrackInfo>()
        room?.localParticipant?.videoTrackPublications?.forEach { pub ->
            val t = pub.second
            if (t is VideoTrack) list.add(TrackInfo(t, room.localParticipant.identity.toString(), (room.localParticipant.name ?: room.localParticipant.identity).toString()))
        }
        room?.remoteParticipants?.values?.forEach { p ->
            p.videoTrackPublications.forEach { pub ->
                val t = pub.second
                if (t is VideoTrack) list.add(TrackInfo(t, p.identity.toString(), (p.name ?: p.identity).toString()))
            }
        }
        tracks = list
    }

    LaunchedEffect(room) {
        if (room == null) return@LaunchedEffect
        updateTracks()
        room.events.events.collect { event ->
            when (event) {
                is RoomEvent.ActiveSpeakersChanged -> activeSpeakers = event.speakers.map { it.identity.toString() }.toSet()
                else -> {}
            }
            updateTracks()
        }
    }

    Column(Modifier.fillMaxSize().padding(8.dp), Arrangement.spacedBy(8.dp)) {
        Text("Room: ${state.code ?: ""}  status: ${state.status}${if (recording) "  • REC" else ""}", style = MaterialTheme.typography.titleMedium, color = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tracks) { ti ->
                val active = ti.participantId in activeSpeakers
                Column {
                    Text(ti.name, style = MaterialTheme.typography.labelMedium)
                    Box(Modifier.border(if (active) 3.dp else 1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))) {
                        VideoTrackView(videoTrack = ti.track, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f), passedRoom = room)
                    }
                }
            }
            if (tracks.isEmpty()) {
                item { Text("No video tracks yet", style = MaterialTheme.typography.bodyMedium) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.toggleMic() }) { Text(if (state.micEnabled) "Mute" else "Unmute") }
            Button(onClick = { vm.toggleCamera() }) { Text(if (state.cameraEnabled) "Cam off" else "Cam on") }
            Button(onClick = {
                kotlinx.coroutines.MainScope().launch {
                    if (!recording) {
                        val (c, body) = withContext(Dispatchers.IO) { com.metafrazo.app.core.network.Api.post("/api/meetings/${state.code}/recordings/start", "{\"mode\":\"standard\"}") }
                        if (c in 200..299) {
                            val j = org.json.JSONObject(body)
                            egressId = j.optString("egressId")
                            recordingId = j.optString("recordingId")
                            recording = true
                        }
                    } else {
                        val (c, _) = withContext(Dispatchers.IO) { com.metafrazo.app.core.network.Api.post("/api/meetings/${state.code}/recordings/stop", "{\"egressId\":\"$egressId\",\"recordingId\":\"$recordingId\"}") }
                        recording = false
                    }
                }
            }) { Text(if (recording) "Stop rec" else "Record") }
            Button(onClick = onCaptions) { Text("Captions") }
            Button(onClick = { state.code?.let { onChat(it) } }) { Text("Chat") }
            Button(onClick = { state.code?.let { onNotes(it) } }) { Text("Notes") }
            Button(onClick = { state.code?.let { onTranscripts(it) } }) { Text("Transcript") }
            Button(onClick = {
                if (state.screenSharing) vm.stopScreenShare() else screenLauncher.launch(projectionManager.createScreenCaptureIntent())
            }) { Text(if (state.screenSharing) "Stop share" else "Share screen") }
            Button(onClick = { vm.leave(); onLeave() }) { Text("Leave") }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}