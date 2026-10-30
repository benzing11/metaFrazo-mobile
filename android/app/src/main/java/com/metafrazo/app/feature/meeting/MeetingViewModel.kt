package com.metafrazo.app.feature.meeting

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.metafrazo.app.core.meeting.MeetingCode
import com.metafrazo.app.core.meeting.MediaServerUrl
import com.metafrazo.app.core.network.Api
import com.metafrazo.app.core.service.MeetingForegroundService
import io.livekit.android.LiveKit
import io.livekit.android.RoomOptions
import io.livekit.android.events.RoomEvent
import io.livekit.android.room.Room
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class MeetingUiState(
    val status: String = "idle", // idle, creating, joining, connecting, connected, error, left
    val code: String? = null,
    val meetingId: String? = null,
    val title: String? = null,
    val token: String? = null,
    val url: String? = null,
    val error: String? = null,
    val micEnabled: Boolean = true,
    val cameraEnabled: Boolean = true,
    val screenSharing: Boolean = false,
    val reconnecting: Boolean = false,
    val recording: Boolean = false,
    val recordingBusy: Boolean = false,
    val egressId: String? = null,
    val recordingId: String? = null,
    val connectedAt: Long? = null,
) {
    val isBusy: Boolean get() = status == "creating" || status == "joining" || status == "connecting"
}

class MeetingViewModel(private val app: Application) : ViewModel() {
    companion object {
        @Volatile var activeRoom: Room? = null
        fun factory(app: Application) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MeetingViewModel(app) as T
        }
    }

    private val _state = MutableStateFlow(MeetingUiState())
    val state: StateFlow<MeetingUiState> = _state

    var room: Room? = null
        private set
    private var eventsJob: Job? = null
    private var screenCaptureParams: io.livekit.android.room.track.screencapture.ScreenCaptureParams? = null

    /** Resets a finished or failed session so the lobby starts fresh. Keeps the user's mic/camera choice. */
    fun resetIfIdle() {
        val s = _state.value
        if (room == null && !s.isBusy) {
            // Keep the reason for an involuntary exit so the lobby can show it.
            _state.value = MeetingUiState(
                micEnabled = s.micEnabled,
                cameraEnabled = s.cameraEnabled,
                error = if (s.status == "left") s.error else null,
            )
        }
    }

    fun setPreJoinMic(enabled: Boolean) {
        if (room == null) _state.value = _state.value.copy(micEnabled = enabled)
    }

    fun setPreJoinCamera(enabled: Boolean) {
        if (room == null) _state.value = _state.value.copy(cameraEnabled = enabled)
    }

    /** Creates a meeting and joins it in one step. */
    fun startNewMeeting(title: String? = null) {
        if (_state.value.isBusy || room != null) return
        viewModelScope.launch {
            _state.value = _state.value.copy(status = "creating", error = null, code = null, meetingId = null)
            val payload = JSONObject().apply { if (!title.isNullOrBlank()) put("title", title.trim()) }.toString()
            val (c, body) = withContext(Dispatchers.IO) { Api.post("/api/meetings", payload) }
            if (c !in 200..299) {
                fail("Couldn't create meeting: ${readableError(c, body)}")
                return@launch
            }
            val code = runCatching { JSONObject(body).getString("code") }.getOrNull()
            if (code == null) {
                fail("Unexpected response from server")
                return@launch
            }
            joinInternal(code)
        }
    }

    fun joinMeeting(codeInput: String) {
        if (_state.value.isBusy || room != null) return
        val code = MeetingCode.extract(codeInput)
        if (!MeetingCode.isValid(code)) {
            fail("Meeting codes are ${MeetingCode.LENGTH} letters or numbers")
            return
        }
        viewModelScope.launch { joinInternal(code) }
    }

    private suspend fun joinInternal(code: String) {
        _state.value = _state.value.copy(status = "joining", error = null)
        val (c, body) = withContext(Dispatchers.IO) { Api.post("/api/meetings/join", JSONObject().put("code", code).toString()) }
        if (c == 404) {
            fail(
                "No meeting with code $code on ${Api.backendUrl}. Check the code, and make sure " +
                    "this phone uses the same server as the host (Settings > Backend URL).",
            )
            return
        }
        if (c !in 200..299) {
            fail("Couldn't join: ${readableError(c, body)}")
            return
        }
        val json = JSONObject(body)
        val joined = json.getString("code")
        _state.value = _state.value.copy(
            status = "connecting",
            code = joined,
            meetingId = json.optString("meetingId"),
            title = json.optString("title").takeIf { it.isNotBlank() && it != "null" },
        )
        val (tc, tbody) = withContext(Dispatchers.IO) { Api.post("/api/meetings/$joined/token", "{}") }
        if (tc !in 200..299) {
            fail("Couldn't get access token: ${readableError(tc, tbody)}")
            return
        }
        val tj = JSONObject(tbody)
        val url = MediaServerUrl.resolve(tj.getString("url"), Api.backendUrl)
        val token = tj.getString("token")
        _state.value = _state.value.copy(token = token, url = url)
        connect(url, token)
    }

    private suspend fun connect(url: String, token: String) {
        // Configure low-latency and bandwidth-saving settings:
        // - adaptiveStream: pulls video at only the rendered resolution, lowering Wi-Fi load & latency
        // - dynacast: pauses sending higher-res video layers if no receiver asks for them
        val options = RoomOptions(
            adaptiveStream = true,
            dynacast = true,
        )
        val r = LiveKit.create(app, options)
        try {
            r.connect(url, token)
        } catch (e: Exception) {
            Log.e("MeetingVM", "connect failed", e)
            runCatching { r.release() }
            fail(mediaServerError(url, e))
            return
        }
        room = r
        activeRoom = r
        // A denied permission or busy camera must not abort an otherwise-good connection.
        val wantMic = _state.value.micEnabled && granted(Manifest.permission.RECORD_AUDIO)
        val wantCam = _state.value.cameraEnabled && granted(Manifest.permission.CAMERA)
        val micOn = runCatching { r.localParticipant.setMicrophoneEnabled(wantMic); wantMic }
            .onFailure { Log.w("MeetingVM", "mic", it) }.getOrDefault(false)
        val camOn = runCatching { r.localParticipant.setCameraEnabled(wantCam); wantCam }
            .onFailure { Log.w("MeetingVM", "camera", it) }.getOrDefault(false)
        _state.value = _state.value.copy(
            status = "connected",
            error = null,
            micEnabled = micOn,
            cameraEnabled = camOn,
            connectedAt = System.currentTimeMillis(),
        )
        MeetingForegroundService.start(app)
        eventsJob?.cancel()
        eventsJob = viewModelScope.launch {
            r.events.events.collect { ev ->
                when (ev) {
                    is RoomEvent.Reconnecting -> _state.value = _state.value.copy(reconnecting = true)
                    is RoomEvent.Reconnected -> _state.value = _state.value.copy(reconnecting = false)
                    is RoomEvent.Disconnected -> if (room === r) {
                        leave("Lost connection to the meeting. Check your network, then rejoin with the same code.")
                    }
                    else -> {}
                }
            }
        }
    }

    private fun fail(message: String) {
        _state.value = _state.value.copy(status = "error", error = message)
    }

    /**
     * Stage-specific message for a LiveKit connect failure. A loopback media URL means USB
     * forwarding is the transport, so the actionable fix differs from a LAN setup.
     */
    private fun mediaServerError(url: String, e: Exception): String {
        val host = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull()
        val isLoopback = host == "127.0.0.1" || host == "localhost" || host == "::1"
        val detail = "${e.javaClass.simpleName}: ${e.message}"
        return if (isLoopback) {
            "Connected to the backend but couldn't reach the media server at $url. " +
                "On USB, make sure `adb reverse tcp:7880 tcp:7880` and `tcp:7881 tcp:7881` are set and LiveKit is running. [$detail]"
        } else {
            "Connected to the backend but couldn't reach the media server at $url. " +
                "Check that LiveKit is running and your phone is on the same Wi-Fi. [$detail]"
        }
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(app, permission) == PackageManager.PERMISSION_GRANTED

    fun toggleMic() {
        val enabled = !_state.value.micEnabled
        if (enabled && !granted(Manifest.permission.RECORD_AUDIO)) {
            _state.value = _state.value.copy(error = "Microphone access is off. Turn it on in system settings.")
            return
        }
        _state.value = _state.value.copy(micEnabled = enabled)
        val r = room ?: return
        viewModelScope.launch {
            runCatching { r.localParticipant.setMicrophoneEnabled(enabled) }.onFailure {
                _state.value = _state.value.copy(micEnabled = !enabled, error = "Microphone error: ${it.message}")
            }
        }
    }

    /**
     * Mutes or restores the published microphone for voice-replacement captions, without
     * touching the user's own mic toggle preference. When muted, other participants no longer
     * hear the raw voice; captions still flow and each listener's device speaks the translation.
     * Returns true when the room applied the change.
     */
    fun setMeetingMicMuted(muted: Boolean): Boolean {
        val r = room ?: return false
        val wantEnabled = !muted
        // Never un-mute if the user themselves muted the mic.
        if (wantEnabled && !_state.value.micEnabled) return false
        viewModelScope.launch {
            runCatching { r.localParticipant.setMicrophoneEnabled(wantEnabled) }
                .onFailure { Log.w("MeetingVM", "voice-replacement mic toggle failed", it) }
        }
        return true
    }

    fun toggleCamera() {
        val enabled = !_state.value.cameraEnabled
        if (enabled && !granted(Manifest.permission.CAMERA)) {
            _state.value = _state.value.copy(error = "Camera access is off. Turn it on in system settings.")
            return
        }
        _state.value = _state.value.copy(cameraEnabled = enabled)
        val r = room ?: return
        viewModelScope.launch {
            runCatching { r.localParticipant.setCameraEnabled(enabled) }.onFailure {
                _state.value = _state.value.copy(cameraEnabled = !enabled, error = "Camera error: ${it.message}")
            }
        }
    }

    fun switchCamera() {
        val track = room?.localParticipant?.getTrackPublication(Track.Source.CAMERA)?.track as? LocalVideoTrack ?: return
        runCatching { track.switchCamera() }.onFailure { Log.w("MeetingVM", "switch camera", it) }
    }

    fun startScreenShare(resultIntent: Intent) {
        viewModelScope.launch {
            val r = room ?: return@launch
            val params = io.livekit.android.room.track.screencapture.ScreenCaptureParams(
                resultIntent,
                1001,
                buildNotification(app),
                { },
            )
            screenCaptureParams = params
            try {
                r.localParticipant.setScreenShareEnabled(true, params)
                _state.value = _state.value.copy(screenSharing = true)
            } catch (e: Exception) {
                Log.e("MeetingVM", "screen share failed", e)
                _state.value = _state.value.copy(error = "Screen share failed: ${e.message}")
            }
        }
    }

    fun stopScreenShare() {
        viewModelScope.launch {
            val params = screenCaptureParams
            if (params != null) {
                try { room?.localParticipant?.setScreenShareEnabled(false, params) } catch (e: Exception) { Log.w("MeetingVM", "stop share err", e) }
            }
            screenCaptureParams = null
            _state.value = _state.value.copy(screenSharing = false)
        }
    }

    private fun buildNotification(context: Context): Notification {
        val channelId = "metafrazo_screenshare"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(NotificationChannel(channelId, "Screen sharing", NotificationManager.IMPORTANCE_LOW))
            }
        }
        return androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setContentTitle("MetaFrazo")
            .setContentText("Sharing screen")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }

    fun toggleRecording() {
        val s = _state.value
        val code = s.code ?: return
        if (s.recordingBusy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(recordingBusy = true, error = null)
            if (!s.recording) {
                val (c, body) = withContext(Dispatchers.IO) {
                    Api.post("/api/meetings/$code/recordings/start", "{\"mode\":\"standard\"}")
                }
                if (c in 200..299) {
                    val j = JSONObject(body)
                    _state.value = _state.value.copy(
                        recording = true,
                        recordingBusy = false,
                        egressId = j.optString("egressId"),
                        recordingId = j.optString("recordingId"),
                    )
                } else {
                    _state.value = _state.value.copy(
                        recordingBusy = false,
                        error = "Recording unavailable: ${readableError(c, body)}",
                    )
                }
            } else {
                val payload = JSONObject().put("egressId", s.egressId).put("recordingId", s.recordingId).toString()
                val (c, body) = withContext(Dispatchers.IO) {
                    Api.post("/api/meetings/$code/recordings/stop", payload)
                }
                _state.value = _state.value.copy(
                    recording = false,
                    recordingBusy = false,
                    egressId = null,
                    recordingId = null,
                    error = if (c in 200..299) null else "Stop recording failed: ${readableError(c, body)}",
                )
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun readableError(code: Int, body: String): String {
        if (code == -1) return if (body.isBlank()) "network error (is the backend URL set in Settings?)" else body
        if (code == 404) return "meeting not found"
        val msg = try { JSONObject(body).optString("error").takeIf { it.isNotBlank() } } catch (_: Exception) { null }
        return msg ?: "HTTP $code"
    }

    /** Ends the session. A non-null [reason] means it was not the user's choice and is shown in the lobby. */
    fun leave(reason: String? = null) {
        eventsJob?.cancel()
        eventsJob = null
        MeetingForegroundService.stop(app)
        val r = room
        room = null
        activeRoom = null
        screenCaptureParams = null
        r?.disconnect()
        r?.release()
        _state.value = MeetingUiState(
            status = "left",
            error = reason,
            micEnabled = _state.value.micEnabled,
            cameraEnabled = _state.value.cameraEnabled,
        )
    }

    override fun onCleared() {
        super.onCleared()
        room?.disconnect()
        room?.release()
        room = null
        activeRoom = null
    }
}
