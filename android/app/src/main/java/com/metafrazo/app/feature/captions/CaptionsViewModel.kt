package com.metafrazo.app.feature.captions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metafrazo.app.feature.meeting.MeetingViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

data class CaptionItem(
    val speaker: String,
    val sourceLang: String,
    val original: String,
    val translations: Map<String, String>,
    val ts: Double,
)

data class CaptionsUiState(
    val captions: List<CaptionItem> = emptyList(),
    val targetLang: String = "en",
    val showOriginal: Boolean = true,
)

class CaptionsViewModel : ViewModel() {
    private val _state = MutableStateFlow(CaptionsUiState())
    val state: StateFlow<CaptionsUiState> = _state

    fun setTargetLang(lang: String) { _state.value = _state.value.copy(targetLang = lang) }
    fun toggleOriginal() { _state.value = _state.value.copy(showOriginal = !_state.value.showOriginal) }

    fun attachToRoom() {
        val room = MeetingViewModel.activeRoom ?: return
        viewModelScope.launch {
            room.events.events.collect { ev ->
                if (ev is io.livekit.android.events.RoomEvent.DataReceived) {
                    try {
                        val json = JSONObject(String(ev.data))
                        if (json.optString("type") == "caption") {
                            val trans = mutableMapOf<String, String>()
                            val t = json.optJSONObject("translations")
                            t?.keys()?.forEach { k -> trans[k] = t.optString(k) }
                            val item = CaptionItem(
                                speaker = json.optString("speaker"),
                                sourceLang = json.optString("source_lang"),
                                original = json.optString("original"),
                                translations = trans,
                                ts = json.optDouble("ts"),
                            )
                            _state.value = _state.value.copy(captions = (_state.value.captions + item).takeLast(200))
                        }
                    } catch (_: Exception) { }
                }
            }
        }
    }
}