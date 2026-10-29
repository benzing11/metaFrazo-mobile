package com.metafrazo.app.feature.captions

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.metafrazo.app.core.speech.SpeechRecognizerSource
import com.metafrazo.app.core.translate.DeviceTranslator
import com.metafrazo.app.core.translate.TranslateResult
import com.metafrazo.app.feature.meeting.MeetingViewModel
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.DataPublishReliability
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
    val isLocal: Boolean = false,
)

data class CaptionsUiState(
    val captions: List<CaptionItem> = emptyList(),
    /** Language the local user wants to read. */
    val targetLang: String = "en",
    /** Language the local user speaks. */
    val spokenLang: String = "en",
    val showOriginal: Boolean = true,
    val listening: Boolean = false,
    val partial: String = "",
    val status: String = "",
    val error: String? = null,
    val modelReady: Boolean = false,
)

class CaptionsViewModel(app: Application) : AndroidViewModel(app) {
    companion object {
        fun factory(app: Application) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = CaptionsViewModel(app) as T
        }
    }

    private val _state = MutableStateFlow(CaptionsUiState())
    val state: StateFlow<CaptionsUiState> = _state

    private var attached = false
    private var speech: SpeechRecognizerSource? = null

    /**
     * Remote participants' preferred reading languages, keyed by LiveKit identity.
     * Populated from `caption_pref` announcements. When a sender knows a recipient's
     * preference it delivers only that language to them; otherwise it falls back to a
     * broadcast carrying every translation so captions never disappear.
     */
    private val recipientPrefs = mutableMapOf<String, String>()

    val speechAvailable: Boolean
        get() = SpeechRecognizerSource(getApplication(), {}, {}, {}).isAvailable

    fun setTargetLang(lang: String) {
        val changed = _state.value.targetLang != lang
        _state.value = _state.value.copy(targetLang = lang)
        prepareModel()
        if (changed) announcePreference()
    }

    fun setSpokenLang(lang: String) {
        val wasListening = _state.value.listening
        _state.value = _state.value.copy(spokenLang = lang)
        if (wasListening) {
            stopListening()
            startListening()
        }
        prepareModel()
    }

    fun toggleOriginal() {
        _state.value = _state.value.copy(showOriginal = !_state.value.showOriginal)
    }

    /** Downloads the ML Kit model for the current pair so translation works offline. */
    fun prepareModel() {
        val s = _state.value
        if (s.spokenLang == s.targetLang) {
            _state.value = s.copy(modelReady = true, status = "")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(status = "Preparing ${s.spokenLang.uppercase()}→${s.targetLang.uppercase()} model…")
            val err = DeviceTranslator.prepare(s.spokenLang, s.targetLang)
            _state.value = _state.value.copy(
                modelReady = err == null,
                status = if (err == null) "Translation ready (on-device)" else "",
                error = err,
            )
        }
    }

    /** Receives captions published by other participants. */
    fun attachToRoom() {
        if (attached) return
        val room = MeetingViewModel.activeRoom ?: return
        attached = true
        announcePreference()
        viewModelScope.launch {
            room.events.events.collect { ev ->
                if (ev is io.livekit.android.events.RoomEvent.ParticipantDisconnected) {
                    ev.participant.identity?.value?.let { recipientPrefs.remove(it) }
                    return@collect
                }
                if (ev is io.livekit.android.events.RoomEvent.DataReceived) {
                    try {
                        val json = JSONObject(String(ev.data))
                        when (json.optString("type")) {
                            "caption_pref" -> {
                                val id = json.optString("identity")
                                val lang = json.optString("lang")
                                if (id.isNotBlank() && lang.isNotBlank()) {
                                    val isNew = !recipientPrefs.containsKey(id)
                                    recipientPrefs[id] = lang
                                    // Reply directly so a newly joined peer learns our
                                    // preference without triggering a broadcast storm.
                                    if (isNew) announcePreference(toIdentity = id)
                                }
                                return@collect
                            }
                            "caption" -> {}
                            else -> return@collect
                        }
                        val original = json.optString("original")
                        val sourceLang = json.optString("source_lang", "en")
                        val trans = mutableMapOf<String, String>()
                        json.optJSONObject("translations")?.let { t ->
                            t.keys().forEach { k -> trans[k] = t.optString(k) }
                        }
                        val target = _state.value.targetLang
                        // Translate on this device when the sender did not include our language.
                        if (!trans.containsKey(target) && sourceLang != target) {
                            DeviceTranslator.prepare(sourceLang, target)
                            trans[target] = DeviceTranslator.translate(original, sourceLang, target)
                        }
                        addCaption(
                            CaptionItem(
                                speaker = json.optString("speaker", "Participant"),
                                sourceLang = sourceLang,
                                original = original,
                                translations = trans,
                                ts = json.optDouble("ts", System.currentTimeMillis() / 1000.0),
                            ),
                        )
                    } catch (e: Exception) {
                        Log.w("CaptionsVM", "bad caption payload", e)
                    }
                }
            }
        }
    }

    fun startListening() {
        if (_state.value.listening) return
        val source = SpeechRecognizerSource(
            context = getApplication(),
            onPartial = { text -> _state.value = _state.value.copy(partial = text) },
            onFinal = { text ->
                _state.value = _state.value.copy(partial = "")
                handleLocalTranscript(text)
            },
            onError = { msg -> _state.value = _state.value.copy(error = msg, listening = false) },
        )
        if (!source.isAvailable) {
            _state.value = _state.value.copy(error = "Speech recognition is not available on this device")
            return
        }
        speech = source
        source.start(localeTag(_state.value.spokenLang))
        _state.value = _state.value.copy(listening = true, error = null)
        prepareModel()
    }

    fun stopListening() {
        speech?.stop()
        speech = null
        _state.value = _state.value.copy(listening = false, partial = "")
    }

    /** Surfaces a clear message when the user declines the microphone prompt. */
    fun onMicPermissionDenied() {
        _state.value = _state.value.copy(
            listening = false,
            error = "Microphone access is needed for live captions. Enable it in system settings.",
        )
    }

    private fun handleLocalTranscript(text: String) {
        val s = _state.value
        viewModelScope.launch {
            val translations = mutableMapOf<String, String>()
            val failures = mutableListOf<String>()
            // Translate into every supported language so each participant can read it.
            // Each pair is prepared and translated independently: one failure must not
            // drop the others, and a silent fallback must not masquerade as a translation.
            for (lang in TranslationPlan.targetsFor(s.spokenLang, DeviceTranslator.supported)) {
                val prepError = DeviceTranslator.prepare(s.spokenLang, lang)
                if (prepError != null) {
                    failures += "${lang.uppercase()}: $prepError"
                    continue
                }
                when (val r = DeviceTranslator.translateResult(text, s.spokenLang, lang)) {
                    is TranslateResult.Translated -> translations[lang] = r.text
                    is TranslateResult.Failed -> failures += "${lang.uppercase()}: ${r.reason}"
                    is TranslateResult.Unsupported -> failures += "${lang.uppercase()}: unsupported"
                    is TranslateResult.SameLanguage -> {}
                }
            }
            val merged = TranslationPlan.merge(s.spokenLang, text, translations)
            if (failures.isNotEmpty()) {
                _state.value = _state.value.copy(error = "Some translations failed — ${failures.joinToString("; ")}")
            }
            val item = CaptionItem(
                speaker = "You",
                sourceLang = s.spokenLang,
                original = text,
                translations = merged,
                ts = System.currentTimeMillis() / 1000.0,
                isLocal = true,
            )
            addCaption(item)
            publish(item)
        }
    }

    /** Publishes the local user's preferred reading language so senders can target it. */
    private fun announcePreference(toIdentity: String? = null) {
        val room = MeetingViewModel.activeRoom ?: return
        val myId = room.localParticipant.identity?.value ?: return
        viewModelScope.launch {
            val payload = JSONObject().apply {
                put("type", "caption_pref")
                put("identity", myId)
                put("lang", _state.value.targetLang)
            }
            try {
                room.localParticipant.publishData(
                    data = payload.toString().toByteArray(),
                    reliability = DataPublishReliability.RELIABLE,
                    identities = toIdentity?.let { listOf(Participant.Identity(it)) },
                )
            } catch (e: Exception) {
                Log.w("CaptionsVM", "announce preference failed", e)
            }
        }
    }

    /**
     * Delivers a caption per recipient language. Participants whose preference is known
     * each receive a message containing only their target language. Recipients whose
     * preference is unknown get a broadcast fallback carrying every translation.
     */
    private suspend fun publish(item: CaptionItem) {
        val room = MeetingViewModel.activeRoom ?: return
        val speaker = room.localParticipant.name?.takeIf { it.isNotBlank() }
            ?: room.localParticipant.identity?.value ?: "Participant"

        val remoteIds = room.remoteParticipants.values.mapNotNull { it.identity?.value }
        val plan = CaptionRouter.plan(
            sourceLang = item.sourceLang,
            original = item.original,
            translations = item.translations,
            remoteIdentities = remoteIds,
            prefs = recipientPrefs,
        )

        for (delivery in plan) {
            val payload = JSONObject().apply {
                put("type", "caption")
                put("speaker", speaker)
                put("source_lang", item.sourceLang)
                put("original", item.original)
                put("ts", item.ts)
                put("translations", JSONObject(delivery.translations))
            }
            send(room, payload, delivery.identities?.map { Participant.Identity(it) })
        }
    }

    private suspend fun send(
        room: io.livekit.android.room.Room,
        payload: JSONObject,
        identities: List<Participant.Identity>?,
    ) {
        if (identities != null && identities.isEmpty()) return
        try {
            room.localParticipant.publishData(
                data = payload.toString().toByteArray(),
                reliability = DataPublishReliability.RELIABLE,
                identities = identities,
            )
        } catch (e: Exception) {
            Log.w("CaptionsVM", "publish caption failed", e)
        }
    }

    private fun addCaption(item: CaptionItem) {
        _state.value = _state.value.copy(captions = (_state.value.captions + item).takeLast(200))
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()
    }
}

fun localeTag(lang: String): String = when (lang) {
    "ta" -> "ta-IN"
    "hi" -> "hi-IN"
    else -> "en-US"
}
