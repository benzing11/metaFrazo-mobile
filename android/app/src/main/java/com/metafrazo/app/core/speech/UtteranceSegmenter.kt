package com.metafrazo.app.core.speech

import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

/**
 * Splits a live 16-bit little-endian mono PCM stream into utterances with a simple energy VAD,
 * so each spoken phrase can be sent to the speech service on its own.
 *
 * Speech starts after two loud frames in a row (with a short pre-roll so the first syllable is
 * kept) and ends after [endSilenceMs] of quiet or at [maxUtteranceMs]. Bursts with less than
 * [minSpeechMs] of loud audio (clicks, coughs) are dropped. The loudness threshold follows the
 * room's noise floor so a quiet hum doesn't count as speech.
 */
class UtteranceSegmenter(
    sampleRate: Int = 16_000,
    private val frameMs: Int = 20,
    private val endSilenceMs: Int = 700,
    private val maxUtteranceMs: Int = 8_000,
    private val minSpeechMs: Int = 300,
    preRollMs: Int = 300,
    private val minThreshold: Double = 400.0,
) {
    private val frameBytes = sampleRate * frameMs / 1000 * 2
    private val preRollFrames = preRollMs / frameMs
    private val pending = ByteArray(frameBytes)
    private var pendingLen = 0
    private val preRoll = ArrayDeque<ByteArray>()
    private val utterance = ByteArrayOutputStream()
    private var noiseFloor = 0.0
    private var loudStreak = 0
    private var speechFrames = 0
    private var silenceFrames = 0
    private var totalFrames = 0

    /** True while an utterance is being collected. */
    var inSpeech: Boolean = false
        private set

    /** Feeds more PCM; returns any utterances that completed, as PCM bytes. */
    fun feed(pcm: ByteArray): List<ByteArray> {
        var out: MutableList<ByteArray>? = null
        var i = 0
        while (i < pcm.size) {
            val n = minOf(frameBytes - pendingLen, pcm.size - i)
            System.arraycopy(pcm, i, pending, pendingLen, n)
            pendingLen += n
            i += n
            if (pendingLen == frameBytes) {
                pendingLen = 0
                onFrame(pending.copyOf())?.let { (out ?: mutableListOf<ByteArray>().also { l -> out = l }).add(it) }
            }
        }
        return out ?: emptyList()
    }

    /** Ends any utterance in progress and returns it if it held enough speech. */
    fun flush(): ByteArray? = if (inSpeech) finish() else null

    fun reset() {
        pendingLen = 0
        preRoll.clear()
        utterance.reset()
        inSpeech = false
        loudStreak = 0
        speechFrames = 0
        silenceFrames = 0
        totalFrames = 0
    }

    private fun onFrame(frame: ByteArray): ByteArray? {
        val level = rms(frame)
        val loud = level > maxOf(minThreshold, noiseFloor * 3)
        if (!inSpeech) {
            if (loud) {
                loudStreak++
            } else {
                loudStreak = 0
                noiseFloor = if (noiseFloor == 0.0) level else noiseFloor * 0.95 + level * 0.05
            }
            preRoll.addLast(frame)
            while (preRoll.size > preRollFrames + 2) preRoll.removeFirst()
            if (loudStreak >= 2) {
                inSpeech = true
                preRoll.forEach { utterance.write(it) }
                totalFrames = preRoll.size
                preRoll.clear()
                speechFrames = loudStreak
                silenceFrames = 0
            }
            return null
        }
        utterance.write(frame)
        totalFrames++
        if (loud) {
            speechFrames++
            silenceFrames = 0
        } else {
            silenceFrames++
        }
        return if (silenceFrames * frameMs >= endSilenceMs || totalFrames * frameMs >= maxUtteranceMs) finish() else null
    }

    private fun finish(): ByteArray? {
        val enough = speechFrames * frameMs >= minSpeechMs
        val bytes = if (enough) utterance.toByteArray() else null
        utterance.reset()
        inSpeech = false
        loudStreak = 0
        speechFrames = 0
        silenceFrames = 0
        totalFrames = 0
        return bytes
    }

    private fun rms(frame: ByteArray): Double {
        var sum = 0.0
        var k = 0
        while (k + 1 < frame.size) {
            val s = ((frame[k + 1].toInt() shl 8) or (frame[k].toInt() and 0xFF)).toShort().toDouble()
            sum += s * s
            k += 2
        }
        return sqrt(sum / (frame.size / 2))
    }
}
