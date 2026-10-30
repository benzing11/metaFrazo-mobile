package com.metafrazo.app.feature.captions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceReplacementPolicyTest {
    @Test
    fun mutesWhenEnabledListeningAndInCall() {
        assertTrue(VoiceReplacementPolicy.shouldMuteMic(voiceReplacement = true, listening = true, inCall = true))
    }

    @Test
    fun doesNotMuteWhenToggleOff() {
        assertFalse(VoiceReplacementPolicy.shouldMuteMic(voiceReplacement = false, listening = true, inCall = true))
    }

    @Test
    fun doesNotMuteWhenNotListening() {
        assertFalse(VoiceReplacementPolicy.shouldMuteMic(voiceReplacement = true, listening = false, inCall = true))
    }

    @Test
    fun doesNotMuteWhenNotInCall() {
        assertFalse(VoiceReplacementPolicy.shouldMuteMic(voiceReplacement = true, listening = true, inCall = false))
    }
}
