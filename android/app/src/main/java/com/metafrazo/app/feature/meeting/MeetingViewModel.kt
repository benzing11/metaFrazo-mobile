package com.metafrazo.app.feature.meeting

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.metafrazo.app.core.network.Api
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import io.livekit.android.room.participant.LocalParticipant
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.participant.RemoteParticipant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

data class MeetingUiState(
    val status: String = "idle", // idle, creating, created, joining, connected, error, left
    val code: String? = null,
    val meetingId: String? = null,
    val token: String? = null,
    val url: String? = null,
    val error: String? = null,
    val micEnabled: Boolean = true,
    val cameraEnabled: Boolean = true,
    val screenSharing: Boolean = false,
    val reconnecting: Boolean = false,
)

class MeetingViewModel(private val app: Application) : ViewModel() {
    companion object {
        @Volatile var activeRoom: io.livekit.android.room.Room? = null
        fun factory(app: Application) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MeetingViewModel(app) as T
        }
    }
    private val _state = MutableStateFlow(MeetingUiState())
    val state: StateFlow<MeetingUiState> = _state

    var room: Room? = null
        private set
    private var screenCaptureParams: io.livekit.android.room.track.screencapture.ScreenCaptureParams? = null

    fun createMeeting(title: String? = null) {
        viewModelScope.launch {
            _state.value = _state.value.copy(status = "creating", error = null)
            val (code, body) = Api.post("/api/meetings", "{\"title\":${title?.let { "\"$it\"" } ?: "null"}}")
            if (code in 200..299) {
                val json = JSONObject(body)
                _state.value = _state.value.copy(status = "created", code = json.getString("code"), meetingId = json.getString("meetingId"))
            } else {
                _state.value = _state.value.copy(status = "error", error = "Create failed ($code): $body")
            }
        }
    }

    fun joinMeeting(codeInput: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(status = "joining", error = null)
            val norm = codeInput.trim().uppercase().replace(Regex("\\s+"), "")
            val (code, body) = Api.post("/api/meetings/join", "{\"code\":\"$norm\"}")
            if (code in 200..299) {
                val json = JSONObject(body)
                _state.value = _state.value.copy(status = "joined", code = json.getString("code"), meetingId = json.getString("meetingId"))
                fetchToken(json.getString("code"))
            } else {
                _state.value = _state.value.copy(status = "error", error = "Join failed ($code): $body")
            }
        }
    }

    private fun fetchToken(code: String) {
        viewModelScope.launch {
            val (c, body) = Api.post("/api/meetings/$code/token", "{}")
            if (c in 200..299) {
                val json = JSONObject(body)
                _state.value = _state.value.copy(status = "connecting", token = json.getString("token"), url = json.getString("url"))
                connect(json.getString("url"), json.getString("token"))
            } else {
                _state.value = _state.value.copy(status = "error", error = "Token failed ($c): $body")
            }
        }
    }

    private fun connect(url: String, token: String) {
        viewModelScope.launch {
            try {
                val r = LiveKit.create(app)
                room = r
                activeRoom = r
                r.connect(url, token)
                r.localParticipant.setMicrophoneEnabled(_state.value.micEnabled)
                r.localParticipant.setCameraEnabled(_state.value.cameraEnabled)
                _state.value = _state.value.copy(status = "connected")
                com.metafrazo.app.core.service.MeetingForegroundService.start(app)
                viewModelScope.launch {
                    r.events.events.collect { ev ->
                        when (ev) {
                            is io.livekit.android.events.RoomEvent.Reconnecting -> _state.value = _state.value.copy(reconnecting = true)
                            is io.livekit.android.events.RoomEvent.Reconnected -> _state.value = _state.value.copy(reconnecting = false)
                            is io.livekit.android.events.RoomEvent.Disconnected -> _state.value = _state.value.copy(status = "left", reconnecting = false)
                            else -> { }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MeetingVM", "connect failed", e)
                _state.value = _state.value.copy(status = "error", error = "Connect failed: ${e.message}")
            }
        }
    }

    fun toggleMic() {
        val enabled = !_state.value.micEnabled
        _state.value = _state.value.copy(micEnabled = enabled)
        viewModelScope.launch { room?.localParticipant?.setMicrophoneEnabled(enabled) }
    }

    fun toggleCamera() {
        val enabled = !_state.value.cameraEnabled
        _state.value = _state.value.copy(cameraEnabled = enabled)
        viewModelScope.launch { room?.localParticipant?.setCameraEnabled(enabled) }
    }

    fun startScreenShare(resultIntent: Intent) {
        viewModelScope.launch {
            val r = room ?: return@launch
            val notification = buildNotification(app)
            val params = io.livekit.android.room.track.screencapture.ScreenCaptureParams(
                resultIntent,
                1001,
                notification,
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

    fun leave() {
        com.metafrazo.app.core.service.MeetingForegroundService.stop(app)
        room?.disconnect()
        activeRoom = null
        room = null
        _state.value = MeetingUiState(status = "left")
    }

    override fun onCleared() {
        super.onCleared()
        room?.disconnect()
        room = null
    }
}