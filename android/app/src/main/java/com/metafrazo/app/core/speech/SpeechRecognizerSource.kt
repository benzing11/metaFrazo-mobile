package com.metafrazo.app.core.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Continuous on-device dictation using the platform SpeechRecognizer.
 * Emits partial and final transcripts and restarts itself so a meeting can be
 * captioned without the Python ASR service.
 */
class SpeechRecognizerSource(
    private val context: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null
    private var wantRunning = false

    val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(languageTag: String) {
        if (!isAvailable) {
            onError("Speech recognition is not available on this device")
            return
        }
        wantRunning = true
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(listener(languageTag))
            }
        }
        listen(languageTag)
    }

    private fun listen(languageTag: String) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            onError("Could not start dictation: ${e.message}")
        }
    }

    private fun listener(languageTag: String) = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            // No-speech and timeout errors are normal in a quiet room: just listen again.
            val benign = error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            if (!benign) {
                Log.w("SpeechSource", "recognizer error $error")
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    this@SpeechRecognizerSource.onError("Microphone permission is required for captions")
                    wantRunning = false
                    return
                }
            }
            if (wantRunning) listen(languageTag)
        }

        override fun onResults(results: Bundle?) {
            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let(onFinal)
            if (wantRunning) listen(languageTag)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let(onPartial)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun stop() {
        wantRunning = false
        try {
            recognizer?.stopListening()
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {
        }
        recognizer = null
    }
}
