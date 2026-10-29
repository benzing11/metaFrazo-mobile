package com.metafrazo.app.core.speech

import android.os.ParcelFileDescriptor
import android.util.Log
import io.livekit.android.room.Room
import io.livekit.android.room.track.LocalAudioTrack
import io.livekit.android.room.track.Track
import livekit.org.webrtc.AudioTrackSink
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Feeds the meeting's own microphone audio to the speech recognizer.
 *
 * While a call is live, WebRTC owns the mic and Android silences any second capture, so a
 * recognizer opening its own mic only ever hears zeros. Instead we tap the published LiveKit
 * mic track, convert it to 16 kHz mono PCM, and stream it through a pipe the recognizer reads
 * via [android.speech.RecognizerIntent.EXTRA_AUDIO_SOURCE].
 */
class MeetingMicFeed(private val roomProvider: () -> Room?) : AudioTrackSink {
    private val queue = ArrayBlockingQueue<ByteArray>(QUEUE_CAPACITY)
    @Volatile private var out: OutputStream? = null
    @Volatile private var running = false
    private var track: LocalAudioTrack? = null
    private var readSide: ParcelFileDescriptor? = null
    private var writer: Thread? = null

    /** The live, unmuted local mic track, or null when not in a call or muted. */
    private fun currentMicTrack(): LocalAudioTrack? {
        val pub = roomProvider()?.localParticipant?.getTrackPublication(Track.Source.MICROPHONE) ?: return null
        if (pub.muted) return null
        return pub.track as? LocalAudioTrack
    }

    /** True when there's a meeting mic to tap; otherwise the recognizer should use the mic directly. */
    val isAvailable: Boolean get() = currentMicTrack() != null

    /**
     * Opens a fresh audio stream for one recognition session and returns the read end, or null
     * when no meeting mic is available. Any previous session's stream is closed.
     */
    @Synchronized
    fun openSession(): ParcelFileDescriptor? {
        val mic = currentMicTrack() ?: run { closeTrack(); return null }
        if (mic !== track) {
            closeTrack()
            mic.addSink(this)
            track = mic
            Log.i(TAG, "attached to meeting mic track")
        }
        ensureWriter()
        closeSession()
        val (r, w) = ParcelFileDescriptor.createPipe().let { it[0] to it[1] }
        queue.clear()
        out = ParcelFileDescriptor.AutoCloseOutputStream(w)
        readSide = r
        return r
    }

    private fun ensureWriter() {
        if (running) return
        running = true
        writer = Thread({
            while (running) {
                val chunk = try {
                    queue.poll(200, TimeUnit.MILLISECONDS)
                } catch (_: InterruptedException) {
                    break
                } ?: continue
                val o = out ?: continue
                try {
                    o.write(chunk)
                } catch (_: IOException) {
                    // Recognizer finished and closed its end; wait for the next session.
                    out = null
                }
            }
        }, "MeetingMicFeed").apply { isDaemon = true; start() }
    }

    /**
     * Runs on WebRTC's audio thread: must never block. Converts to 16 kHz mono 16-bit and
     * drops the chunk if the writer is behind.
     */
    override fun onData(
        audioData: ByteBuffer,
        bitsPerSample: Int,
        sampleRate: Int,
        numberOfChannels: Int,
        numberOfFrames: Int,
        absoluteCaptureTimestampMs: Long,
    ) {
        if (out == null || bitsPerSample != 16 || sampleRate <= 0 || numberOfChannels <= 0) return
        val src = audioData.duplicate().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val ratio = sampleRate.toDouble() / TARGET_RATE
        val outFrames = (numberOfFrames / ratio).toInt()
        if (outFrames <= 0) return
        val bytes = ByteArray(outFrames * 2)
        for (i in 0 until outFrames) {
            // Box-average the source window (all channels) for a cheap anti-aliased downsample.
            val start = (i * ratio).toInt()
            val end = minOf(((i + 1) * ratio).toInt().coerceAtLeast(start + 1), numberOfFrames)
            var sum = 0L
            var n = 0
            for (f in start until end) {
                for (c in 0 until numberOfChannels) {
                    sum += src.get(f * numberOfChannels + c)
                    n++
                }
            }
            val s = (sum / maxOf(n, 1)).toInt()
            bytes[i * 2] = (s and 0xFF).toByte()
            bytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }
        queue.offer(bytes)
    }

    private fun closeSession() {
        try { out?.close() } catch (_: IOException) {}
        out = null
        try { readSide?.close() } catch (_: IOException) {}
        readSide = null
    }

    private fun closeTrack() {
        try { track?.removeSink(this) } catch (_: Exception) {}
        track = null
    }

    @Synchronized
    fun stop() {
        running = false
        writer?.interrupt()
        writer = null
        closeSession()
        closeTrack()
        queue.clear()
    }

    companion object {
        private const val TAG = "MeetingMicFeed"
        const val TARGET_RATE = 16_000
        /** ~2 s of 10 ms chunks; older audio is dropped rather than delaying captions. */
        private const val QUEUE_CAPACITY = 200
    }
}
