package com.metafrazo.app.core.speech

/** A running speech-to-text engine that captions can pause (e.g. during text-to-speech) and stop. */
interface TranscriptSource {
    fun pause()
    fun resume()
    fun stop()
}
