package com.metafrazo.app.feature.captions

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.metafrazo.app.core.speech.MeetingMicFeed
import com.metafrazo.app.core.speech.SpeechRecognizerSource
import com.metafrazo.app.core.speech.SpeechSynthesizer
import com.metafrazo.app.core.speech.TranscriptSource
import com.metafrazo.app.core.speech.WhisperAsrSource
import com.metafrazo.app.core.network.Api
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
    /** Live interim transcript in the spoken language. */
    val partial: String = "",
    /** Live interim transcript translated into [targetLang], updated as the user speaks. */
    val partialTranslated: String = "",
    val status: String = "",
    val error: String? = null,
    val modelReady: Boolean = false,
    /** Read remote captions aloud in [targetLang]. */
    val speakTranslations: Boolean = false,
    /** Read incoming chat messages aloud. */
    val speakChat: Boolean = false,
    /** Which engine is producing local captions: "whisper", "device", or "" when idle. */
    val engine: String = "",
    /** True while Whisper hears speech or is transcribing a phrase (it has no word-by-word partials). */
    val hearing: Boolean = false,
    /** Names and terms passed to Whisper so they're spelled correctly. */
    val hardWords: String = "",
)

class CaptionsViewModel(app: Application) : AndroidViewModel(app) {
    companion object {
        private const val FLUSH_INTERVAL_MS = 10_000L
        /** Short enough to feel instant, long enough to skip translating every recognizer tick. */
        private const val PARTIAL_DEBOUNCE_MS = 80L
        /** Whisper reads at most ~224 prompt tokens; the service also truncates. */
        private const val MAX_HARD_WORDS_CHARS = 400

        /** Outlives the ViewModel so the final flush on leave still completes. */
        private val uploadScope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
        )

        fun factory(app: Application) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = CaptionsViewModel(app) as T
        }
    }

    private val _state = MutableStateFlow(CaptionsUiState())
    val state: StateFlow<CaptionsUiState> = _state

    private var attached = false
    private var speech: TranscriptSource? = null
    private var startJob: kotlinx.coroutines.Job? = null
    private var partialJob: kotlinx.coroutines.Job? = null
    private val prefs = app.getSharedPreferences("metafrazo_captions", android.content.Context.MODE_PRIVATE)
    private var synthesizer: SpeechSynthesizer? = null
    private val uploader = TranscriptUploader()
    private var flushJob: kotlinx.coroutines.Job? = null

    /** Tells the transcript uploader which meeting finalized captions belong to. */
    fun setMeetingCode(code: String?) {
        uploader.setMeetingCode(code)
    }

    init {
        val savedTarget = prefs.getString("target_lang", null)?.takeIf { it in DeviceTranslator.supported }
        val savedSpoken = prefs.getString("spoken_lang", null)?.takeIf { it in DeviceTranslator.supported }
        _state.value = _state.value.copy(
            speakTranslations = prefs.getBoolean("speak_translations", false),
            speakChat = prefs.getBoolean("speak_chat", false),
            targetLang = savedTarget ?: _state.value.targetLang,
            spokenLang = savedSpoken ?: _state.value.spokenLang,
            hardWords = prefs.getString("hard_words", "").orEmpty(),
        )
    }

    fun setHardWords(words: String) {
        val trimmed = words.take(MAX_HARD_WORDS_CHARS)
        prefs.edit().putString("hard_words", trimmed).apply()
        _state.value = _state.value.copy(hardWords = trimmed)
    }

    /** Lets other screens pause dictation while they play speech, so it is not re-transcribed. */
    fun setTtsBusy(busy: Boolean) {
        if (busy) speech?.pause() else speech?.resume()
    }

    fun setSpeakChat(enabled: Boolean) {
        prefs.edit().putBoolean("speak_chat", enabled).apply()
        _state.value = _state.value.copy(speakChat = enabled)
    }

    fun setSpeakTranslations(enabled: Boolean) {
        prefs.edit().putBoolean("speak_translations", enabled).apply()
        _state.value = _state.value.copy(speakTranslations = enabled)
        if (!enabled) {
            synthesizer?.stop()
            speech?.resume()
        }
    }

    private fun speakAloud(item: CaptionItem) {
        val s = _state.value
        if (!s.speakTranslations) return
        // Transcribe-only for same-language captions: never speak your own language back.
        if (item.sourceLang == s.targetLang) return
        val text = item.translations[s.targetLang] ?: return
        val synth = synthesizer ?: SpeechSynthesizer(
            context = getApplication(),
            onBusyChanged = { busy -> if (busy) speech?.pause() else speech?.resume() },
            onUnavailable = { msg -> _state.value = _state.value.copy(error = msg) },
        ).also { synthesizer = it }
        synth.speak(text, s.targetLang)
    }

    private fun startFlushLoop() {
        if (flushJob?.isActive == true) return
        flushJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(FLUSH_INTERVAL_MS)
                uploader.flush()
            }
        }
    }

    /** Pushes any buffered captions now. Runs on an app-wide scope so it survives the ViewModel. */
    fun flushTranscript() {
        flushJob?.cancel()
        flushJob = null
        uploadScope.launch { uploader.flush() }
    }

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
        prefs.edit().putString("target_lang", lang).apply()
        val changed = _state.value.targetLang != lang
        _state.value = _state.value.copy(targetLang = lang)
        prepareModel()
        if (changed) announcePreference()
    }

    fun setSpokenLang(lang: String) {
        prefs.edit().putString("spoken_lang", lang).apply()
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
            // Same language: no translation model needed, captions are transcribe-only.
            val label = DeviceTranslator.labels[s.targetLang] ?: s.targetLang.uppercase()
            _state.value = s.copy(modelReady = true, status = "Captions ready ($label)", error = null)
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
                            val err = DeviceTranslator.prepare(sourceLang, target)
                            if (err == null) {
                                trans[target] = DeviceTranslator.translate(original, sourceLang, target)
                            }
                        }
                        val remote = CaptionItem(
                            speaker = json.optString("speaker", "Participant"),
                            sourceLang = sourceLang,
                            original = original,
                            translations = trans,
                            ts = json.optDouble("ts", System.currentTimeMillis() / 1000.0),
                        )
                        addCaption(remote)
                        speakAloud(remote)
                    } catch (e: Exception) {
                        Log.w("CaptionsVM", "bad caption payload", e)
                    }
                }
            }
        }
    }

    /**
     * Starts local captions. In a call with the AI service up, Whisper transcribes the meeting mic
     * (much better on names and jargon); otherwise the phone's recognizer is used.
     */
    fun startListening() {
        if (_state.value.listening || startJob?.isActive == true) return
        _state.value = _state.value.copy(listening = true, error = null)
        startJob = viewModelScope.launch {
            val aiUrl = Api.aiUrl
            val inCall = MeetingViewModel.activeRoom != null
            val whisperReady = inCall && kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                WhisperAsrSource.isReady(aiUrl)
            }
            if (!_state.value.listening) return@launch
            if (whisperReady && startWhisper()) return@launch
            startDeviceRecognizer()
        }
    }

    private fun startWhisper(): Boolean {
        val source = WhisperAsrSource(
            micFeed = MeetingMicFeed { MeetingViewModel.activeRoom },
            aiUrl = { Api.aiUrl },
            language = _state.value.spokenLang,
            prompt = { _state.value.hardWords },
            onFinal = { text ->
                _state.value = _state.value.copy(hearing = false)
                handleLocalTranscript(text, livePreview = "")
            },
            onHearing = { hearing -> _state.value = _state.value.copy(hearing = hearing) },
            onUnavailable = { reason ->
                Log.w("CaptionsVM", "Whisper unavailable, switching to on-device: $reason")
                speech?.stop()
                speech = null
                _state.value = _state.value.copy(hearing = false)
                startDeviceRecognizer()
            },
        )
        if (!source.start()) return false
        speech = source
        _state.value = _state.value.copy(engine = "whisper")
        prepareModel()
        return true
    }

    private fun startDeviceRecognizer() {
        val source = SpeechRecognizerSource(
            context = getApplication(),
            onPartial = { text -> onPartialTranscript(text) },
            onFinal = { text ->
                partialJob?.cancel()
                // Keep the partial on screen until the final line is added, so the overlay never blanks.
                handleLocalTranscript(text, livePreview = _state.value.partialTranslated)
            },
            onError = { msg -> _state.value = _state.value.copy(error = msg, listening = false) },
            // In a call WebRTC owns the mic and Android silences a second capture, so feed the
            // recognizer the meeting's own mic audio instead.
            micFeed = MeetingMicFeed { MeetingViewModel.activeRoom },
        )
        if (!source.isAvailable) {
            _state.value = _state.value.copy(error = "Speech recognition is not available on this device", listening = false, engine = "")
            return
        }
        speech = source
        source.start(localeTag(_state.value.spokenLang))
        _state.value = _state.value.copy(listening = true, engine = "device")
        prepareModel()
    }

    /**
     * Updates the interim transcript and translates it into the target language live, debounced so
     * a fast-changing partial does not fire a translation per keystroke. Only the target language is
     * translated here to keep the overlay instant; full multi-language fan-out happens on the final.
     */
    private fun onPartialTranscript(text: String) {
        val s = _state.value
        _state.value = s.copy(partial = text)
        if (text.isBlank() || s.spokenLang == s.targetLang) {
            _state.value = _state.value.copy(partialTranslated = "")
            return
        }
        // Leading edge: translate the first words of a sentence immediately; only debounce
        // follow-up partials that arrive while a translation is already in flight.
        val inFlight = partialJob?.isActive == true
        partialJob?.cancel()
        partialJob = viewModelScope.launch {
            if (inFlight) kotlinx.coroutines.delay(PARTIAL_DEBOUNCE_MS)
            val translated = DeviceTranslator.translate(text, s.spokenLang, s.targetLang)
            // Guard against a stale result overwriting a newer partial.
            if (_state.value.partial == text) {
                _state.value = _state.value.copy(partialTranslated = translated)
            }
        }
    }

    fun stopListening() {
        partialJob?.cancel()
        startJob?.cancel()
        startJob = null
        flushTranscript()
        speech?.stop()
        speech = null
        _state.value = _state.value.copy(listening = false, partial = "", partialTranslated = "", engine = "", hearing = false)
    }

    /** Surfaces a clear message when the user declines the microphone prompt. */
    fun onMicPermissionDenied() {
        _state.value = _state.value.copy(
            listening = false,
            error = "Microphone access is needed for live captions. Enable it in system settings.",
        )
    }

    private fun handleLocalTranscript(text: String, livePreview: String) {
        val s = _state.value
        val ts = System.currentTimeMillis() / 1000.0

        // Step 1: Show the caption on screen immediately — only translate into the reader's own
        // target language now (fast, one pair). Use the live partial as a placeholder until the
        // real translation arrives so the overlay never blanks between partial and final.
        val immediateTranslations = mutableMapOf<String, String>()
        if (s.spokenLang != s.targetLang && livePreview.isNotBlank()) {
            immediateTranslations[s.targetLang] = livePreview
        }
        val shown = CaptionItem(
            speaker = "You",
            sourceLang = s.spokenLang,
            original = text,
            translations = TranslationPlan.merge(s.spokenLang, text, immediateTranslations),
            ts = ts,
            isLocal = true,
        )
        addCaption(shown)

        // Step 2: Translate into the reader's target language on the main coroutine, then
        // replace the placeholder so the overlay updates as quickly as ML Kit responds.
        viewModelScope.launch {
            val translations = immediateTranslations.toMutableMap()
            if (s.spokenLang != s.targetLang) {
                when (val r = DeviceTranslator.translateResult(text, s.spokenLang, s.targetLang)) {
                    is TranslateResult.Translated -> translations[s.targetLang] = r.text
                    is TranslateResult.Failed -> Log.w("CaptionsVM", "target translate failed: ${r.reason}")
                    else -> {}
                }
            }
            val withTarget = shown.copy(translations = TranslationPlan.merge(s.spokenLang, text, translations))
            replaceCaption(withTarget)
            speakAloud(withTarget)

            // Step 3: Fan out to remote participants' preferred languages only (not all 13).
            uploadScope.launch {
                val remoteTargets = recipientPrefs.values.toSet()
                val allTranslations = translations.toMutableMap()
                val failures = mutableListOf<String>()
                for (lang in remoteTargets) {
                    if (lang == s.targetLang || allTranslations.containsKey(lang)) continue
                    val prepError = DeviceTranslator.prepare(s.spokenLang, lang)
                    if (prepError != null) { failures += "${lang.uppercase()}: $prepError"; continue }
                    when (val r = DeviceTranslator.translateResult(text, s.spokenLang, lang)) {
                        is TranslateResult.Translated -> allTranslations[lang] = r.text
                        is TranslateResult.Failed -> failures += "${lang.uppercase()}: ${r.reason}"
                        is TranslateResult.Unsupported -> {}
                        is TranslateResult.SameLanguage -> {}
                    }
                }
                if (failures.isNotEmpty()) Log.w("CaptionsVM", "fanout failures: ${failures.joinToString("; ")}")
                val full = withTarget.copy(translations = TranslationPlan.merge(s.spokenLang, text, allTranslations))
                uploader.enqueue(full)
                uploader.flush()
                // Publish on main thread (LiveKit SDK requirement)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    publish(full)
                }
            }
            startFlushLoop()
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
        val s = _state.value
        _state.value = if (item.isLocal) {
            // The final line replaces the live partial in a single state update: no blank frame.
            s.copy(captions = (s.captions + item).takeLast(200), partial = "", partialTranslated = "")
        } else {
            s.copy(captions = (s.captions + item).takeLast(200))
        }
    }

    /** Swaps in the fully translated copy of a caption already on screen (matched by timestamp). */
    private fun replaceCaption(item: CaptionItem) {
        val s = _state.value
        _state.value = s.copy(
            captions = s.captions.map { if (it.isLocal && it.ts == item.ts) item else it },
        )
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    override fun onCleared() {
        super.onCleared()
        synthesizer?.shutdown()
        synthesizer = null
        stopListening()
    }
}

fun localeTag(lang: String): String = when (lang) {
    "hi" -> "hi-IN"
    "ta" -> "ta-IN"
    "te" -> "te-IN"
    "kn" -> "kn-IN"
    "bn" -> "bn-IN"
    "mr" -> "mr-IN"
    "gu" -> "gu-IN"
    "ur" -> "ur-IN"
    "es" -> "es-ES"
    "fr" -> "fr-FR"
    "de" -> "de-DE"
    "ar" -> "ar-SA"
    "zh" -> "zh-CN"
    else -> "en-US"
}
