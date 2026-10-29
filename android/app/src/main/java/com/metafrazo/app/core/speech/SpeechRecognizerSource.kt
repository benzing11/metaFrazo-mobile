package com.metafrazo.app.core.speech

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Continuous on-device dictation using the platform SpeechRecognizer.
 * Emits partial and final transcripts and restarts itself so a meeting can be
 * captioned without the Python ASR service.
 *
 * Prefers the dedicated on-device recognizer (API 33+) because the system default
 * can resolve to a stub (e.g. the TTS package's recognition service) that never
 * emits partials, leaving captions stuck on "Listening…".
 *
 * Self-heals: a busy/client error or repeated failures recreate the recognizer after a
 * short delay, a missing on-device language model falls back to the standard recognizer,
 * and a watchdog restarts a session that has gone silent without any callback.
 */
class SpeechRecognizerSource(
    private val context: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onError: (String) -> Unit,
    /** Supplies meeting mic audio during a call, when Android silences a second mic capture. */
    private val micFeed: MeetingMicFeed? = null,
) : TranscriptSource {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var wantRunning = false
    @Volatile private var paused = false
    private var activeTag: String = "en-US"
    private var usingOnDevice = false
    /** Set once the on-device engine proves unusable for this session's language. */
    private var onDeviceDisabled = false
    private var consecutiveErrors = 0
    private var lastActivityAt = 0L
    private var usingMeetingAudio = false
    private var lastLoggedMeetingAudio: Boolean? = null

    private val onDeviceAvailable: Boolean
        get() = !onDeviceDisabled &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    /** True when some recognizer — on-device or networked — can be created. */
    val isAvailable: Boolean
        get() = onDeviceAvailable || SpeechRecognizer.isRecognitionAvailable(context)

    private val watchdog = object : Runnable {
        override fun run() {
            if (!wantRunning) return
            if (!paused && System.currentTimeMillis() - lastActivityAt > WATCHDOG_STALL_MS) {
                Log.w(TAG, "watchdog: no recognizer activity for ${WATCHDOG_STALL_MS}ms, recreating")
                recreate(delayMs = 0)
            }
            main.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    fun start(languageTag: String) {
        if (!isAvailable) {
            onError("No speech recognizer is available. Install or enable Google Speech Services.")
            return
        }
        wantRunning = true
        paused = false
        activeTag = languageTag
        consecutiveErrors = 0
        ensureRecognizer()
        Log.i(TAG, "start lang=$languageTag onDevice=$usingOnDevice")
        listen()
        main.removeCallbacks(watchdog)
        main.postDelayed(watchdog, WATCHDOG_INTERVAL_MS)
    }

    private fun ensureRecognizer() {
        if (recognizer != null) return
        recognizer = if (onDeviceAvailable) {
            usingOnDevice = true
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            usingOnDevice = false
            SpeechRecognizer.createSpeechRecognizer(context)
        }.apply { setRecognitionListener(listener) }
    }

    private fun destroyRecognizer() {
        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {
        }
        recognizer = null
    }

    /** Tears the engine down and starts a fresh one; fixes busy/stuck recognizer states. */
    private fun recreate(delayMs: Long) {
        destroyRecognizer()
        main.postDelayed({
            if (!wantRunning || paused) return@postDelayed
            ensureRecognizer()
            Log.i(TAG, "recreated recognizer onDevice=$usingOnDevice")
            listen()
        }, delayMs)
    }

    /** Stops listening without tearing down, e.g. while text-to-speech plays so it is not re-captured. */
    override fun pause() {
        if (!wantRunning || paused) return
        paused = true
        try {
            recognizer?.cancel()
        } catch (_: Exception) {
        }
    }

    override fun resume() {
        if (!wantRunning || !paused) return
        paused = false
        listen()
    }

    private fun listen() {
        if (paused || !wantRunning) return
        lastActivityAt = System.currentTimeMillis()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, activeTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, usingOnDevice)
            // Finalize segments quickly so captions don't hang waiting for a long silence.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 700L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                micFeed?.openSession()?.let { pipe ->
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe)
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, MeetingMicFeed.TARGET_RATE)
                    usingMeetingAudio = true
                } ?: run { usingMeetingAudio = false }
            }
        }
        if (usingMeetingAudio != lastLoggedMeetingAudio) {
            Log.i(TAG, "audio source: ${if (usingMeetingAudio) "meeting mic track" else "device mic"}")
            lastLoggedMeetingAudio = usingMeetingAudio
        }
        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.w(TAG, "startListening failed", e)
            recreate(delayMs = RESTART_DELAY_MS)
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            lastActivityAt = System.currentTimeMillis()
            Log.i(TAG, "onReadyForSpeech")
        }
        override fun onBeginningOfSpeech() {
            lastActivityAt = System.currentTimeMillis()
            Log.i(TAG, "onBeginningOfSpeech")
        }
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { Log.i(TAG, "onEndOfSpeech") }

        override fun onError(error: Int) {
            lastActivityAt = System.currentTimeMillis()
            if (!wantRunning || paused) return
            when (error) {
                // Normal in a quiet room: listen again right away.
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    Log.d(TAG, "benign error $error, restarting")
                    consecutiveErrors = 0
                    listen()
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    Log.w(TAG, "recognizer error $error (permission)")
                    wantRunning = false
                    main.removeCallbacks(watchdog)
                    this@SpeechRecognizerSource.onError("Microphone permission is required for captions")
                }
                // The on-device engine has no model for this language: use the standard recognizer.
                ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE -> {
                    Log.w(TAG, "recognizer error $error (language), onDevice=$usingOnDevice")
                    if (usingOnDevice) {
                        onDeviceDisabled = true
                        recreate(delayMs = 0)
                    } else {
                        wantRunning = false
                        main.removeCallbacks(watchdog)
                        this@SpeechRecognizerSource.onError("Speech recognition doesn't support this language on this device")
                    }
                }
                else -> {
                    consecutiveErrors++
                    Log.w(TAG, "recognizer error $error (consecutive=$consecutiveErrors)")
                    if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS && usingOnDevice) {
                        // On-device engine keeps failing: try the standard recognizer instead.
                        onDeviceDisabled = true
                    }
                    // Busy/client/server errors need a fresh engine; restarting in place loops.
                    recreate(delayMs = RESTART_DELAY_MS)
                }
            }
        }

        override fun onResults(results: Bundle?) {
            lastActivityAt = System.currentTimeMillis()
            consecutiveErrors = 0
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            Log.i(TAG, "onResults: '${text.orEmpty()}'")
            text?.takeIf { it.isNotBlank() }?.let(onFinal)
            if (wantRunning && !paused) listen()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            lastActivityAt = System.currentTimeMillis()
            val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!text.isNullOrBlank()) {
                consecutiveErrors = 0
                Log.i(TAG, "onPartialResults: '$text'")
                onPartial(text)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    override fun stop() {
        wantRunning = false
        main.removeCallbacksAndMessages(null)
        micFeed?.stop()
        try {
            recognizer?.stopListening()
        } catch (_: Exception) {
        }
        destroyRecognizer()
    }

    private companion object {
        const val TAG = "SpeechSource"
        const val RESTART_DELAY_MS = 300L
        const val MAX_CONSECUTIVE_ERRORS = 3
        const val WATCHDOG_INTERVAL_MS = 4_000L
        const val WATCHDOG_STALL_MS = 12_000L
        // SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED / ERROR_LANGUAGE_UNAVAILABLE (API 31+),
        // kept as literals so the file compiles against any compileSdk.
        const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        const val ERROR_LANGUAGE_UNAVAILABLE = 13
    }
}
