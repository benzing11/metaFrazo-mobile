package com.metafrazo.app.feature.captions

/**
 * Pure decision for voice-replacement mode, free of LiveKit types so it can be unit tested.
 *
 * The published microphone is muted only when voice replacement is enabled, captions are
 * actively listening, and the user is in a call. Leaving the captions screen, stopping
 * dictation, or disabling the toggle all restore the raw mic.
 */
object VoiceReplacementPolicy {
    fun shouldMuteMic(
        voiceReplacement: Boolean,
        listening: Boolean,
        inCall: Boolean,
    ): Boolean = voiceReplacement && listening && inCall
}
