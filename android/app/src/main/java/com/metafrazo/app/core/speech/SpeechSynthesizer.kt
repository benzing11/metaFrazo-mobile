package com.metafrazo.app.core.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Speaks translated captions aloud. Utterances are queued, not flushed, so overlapping captions
 * play in order. [onBusyChanged] fires when the queue goes from empty to speaking and back, so
 * callers can pause the microphone and avoid transcribing their own playback.
 */
class SpeechSynthesizer(
    context: Context,
    private val onBusyChanged: (Boolean) -> Unit,
    private val onUnavailable: (String) -> Unit,
) {
    private val pending = AtomicInteger(0)
    private var ready = false
    private var engine: TextToSpeech? = null
    private var currentLang: String? = null
    private val backlog = ArrayDeque<Pair<String, String>>()

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.setOnUtteranceProgressListener(listener)
                ready = true
                synchronized(backlog) {
                    while (backlog.isNotEmpty()) backlog.removeFirst().let { (t, l) -> speakNow(t, l) }
                }
            } else {
                onUnavailable("Text-to-speech is not available on this device")
            }
        }
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}
        override fun onDone(utteranceId: String?) = finished()
        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = finished()
        override fun onError(utteranceId: String?, errorCode: Int) = finished()
    }

    private fun finished() {
        if (pending.decrementAndGet() <= 0) {
            pending.set(0)
            onBusyChanged(false)
        }
    }

    fun speak(text: String, lang: String) {
        if (text.isBlank()) return
        if (!ready) {
            synchronized(backlog) { backlog.addLast(text to lang) }
            return
        }
        speakNow(text, lang)
    }

    private fun speakNow(text: String, lang: String) {
        val tts = engine ?: return
        if (currentLang != lang) {
            val result = tts.setLanguage(Locale.forLanguageTag(lang))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                onUnavailable("No ${lang.uppercase()} voice installed. Add one in system text-to-speech settings.")
                return
            }
            currentLang = lang
        }
        if (pending.getAndIncrement() == 0) onBusyChanged(true)
        val rc = tts.speak(text, TextToSpeech.QUEUE_ADD, null, "cap-${System.nanoTime()}")
        if (rc != TextToSpeech.SUCCESS) finished()
    }

    fun stop() {
        synchronized(backlog) { backlog.clear() }
        engine?.stop()
        if (pending.getAndSet(0) > 0) onBusyChanged(false)
    }

    fun shutdown() {
        stop()
        engine?.shutdown()
        engine = null
        ready = false
    }
}
