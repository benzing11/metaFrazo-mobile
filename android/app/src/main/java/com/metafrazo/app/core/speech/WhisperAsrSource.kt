package com.metafrazo.app.core.speech

import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ArrayBlockingQueue
import java.util.regex.Pattern
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Captions from the AI service's Whisper model, which handles names and technical terms far
 * better than the phone's recognizer.
 *
 * Taps the meeting mic via [MeetingMicFeed], cuts the audio into phrases with
 * [UtteranceSegmenter], and posts each phrase to `/asr` as raw 16 kHz PCM. Results arrive per
 * phrase, in order. After repeated network failures [onUnavailable] fires so the caller can fall
 * back to on-device recognition.
 */
class WhisperAsrSource(
    private val micFeed: MeetingMicFeed,
    private val aiUrl: () -> String,
    private val language: String,
    /** Names and terms Whisper should prefer when decoding. */
    private val prompt: () -> String,
    private val onFinal: (String) -> Unit,
    /** True while the user is speaking or a phrase is being transcribed. */
    private val onHearing: (Boolean) -> Unit,
    private val onUnavailable: (String) -> Unit,
) : TranscriptSource {
    private val main = Handler(Looper.getMainLooper())
    private val audio = ArrayBlockingQueue<ByteArray>(AUDIO_QUEUE_CAPACITY)
    private val uploads = Executors.newSingleThreadExecutor { r -> Thread(r, "WhisperUpload").apply { isDaemon = true } }
    private val segmenter = UtteranceSegmenter(sampleRate = MeetingMicFeed.TARGET_RATE)
    @Volatile private var running = false
    @Volatile private var paused = false
    @Volatile private var inFlight = 0
    @Volatile private var consecutiveFailures = 0
    private var worker: Thread? = null
    private var lastHearing = false

    /** Starts streaming; returns false when there's no unmuted meeting mic to tap. */
    fun start(): Boolean {
        if (!micFeed.startStreaming { pcm -> if (running && !paused) audio.offer(pcm) }) return false
        running = true
        worker = Thread({ drain() }, "WhisperSegmenter").apply { isDaemon = true; start() }
        Log.i(TAG, "started lang=$language")
        return true
    }

    private fun drain() {
        while (running) {
            val chunk = try {
                audio.poll(200, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                break
            } ?: continue
            if (paused) continue
            segmenter.feed(chunk).forEach(::submit)
            reportHearing()
        }
    }

    private fun submit(pcm: ByteArray) {
        inFlight++
        uploads.execute {
            try {
                if (!running) return@execute
                val started = System.currentTimeMillis()
                val text = canonicalize(transcribe(pcm), prompt())
                Log.i(TAG, "asr ${pcm.size / 32}ms audio -> ${System.currentTimeMillis() - started}ms: '$text'")
                consecutiveFailures = 0
                if (text.isNotBlank()) main.post { if (running) onFinal(text) }
            } catch (e: Exception) {
                consecutiveFailures++
                Log.w(TAG, "asr failed (consecutive=$consecutiveFailures): ${e.message}")
                if (consecutiveFailures >= MAX_FAILURES) {
                    main.post { if (running) onUnavailable("AI speech service unreachable (${e.message})") }
                }
            } finally {
                inFlight--
                reportHearing()
            }
        }
    }

    private fun reportHearing() {
        val hearing = segmenter.inSpeech || inFlight > 0
        if (hearing == lastHearing) return
        lastHearing = hearing
        main.post { if (running) onHearing(hearing) }
    }

    /** Posts one phrase to `/asr`. Throws on network or service errors. */
    private fun transcribe(pcm: ByteArray): String {
        val boundary = "----metafrazo${System.nanoTime()}"
        val conn = URL("${aiUrl()}/asr").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 3_000
            conn.readTimeout = 30_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            DataOutputStream(conn.outputStream).use { o ->
                fun field(name: String, value: String) {
                    o.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n")
                    o.write(value.toByteArray(Charsets.UTF_8))
                    o.writeBytes("\r\n")
                }
                field("encoding", "pcm_s16le")
                field("sample_rate", MeetingMicFeed.TARGET_RATE.toString())
                field("language", language)
                prompt().trim().takeIf { it.isNotEmpty() }?.let { field("initial_prompt", it) }
                o.writeBytes(
                    "--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"phrase.pcm\"\r\n" +
                        "Content-Type: application/octet-stream\r\n\r\n",
                )
                o.write(pcm)
                o.writeBytes("\r\n--$boundary--\r\n")
            }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText().orEmpty()
            if (code !in 200..299) error("HTTP $code")
            val json = JSONObject(body)
            if (json.has("error")) error(json.optString("error"))
            return json.optString("text").trim()
        } finally {
            conn.disconnect()
        }
    }

    override fun pause() {
        paused = true
        audio.clear()
        segmenter.reset()
        reportHearing()
    }

    override fun resume() {
        paused = false
    }

    override fun stop() {
        running = false
        micFeed.stop()
        worker?.interrupt()
        worker = null
        audio.clear()
        uploads.shutdownNow()
        main.removeCallbacksAndMessages(null)
    }

    /** Restore the configured casing of names and terms that Whisper heard but misspelled. */
    private fun canonicalize(text: String, hardWords: String): String {
        if (text.isBlank()) return text
        var out = text
        hardWords.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }.forEach { name ->
            try {
                val matcher = Pattern.compile("\\b" + Pattern.quote(name) + "\\b", Pattern.CASE_INSENSITIVE)
                out = matcher.matcher(out).replaceAll(java.util.regex.Matcher.quoteReplacement(name))
            } catch (_: Exception) {
            }
        }
        return out
    }

    companion object {
        private const val TAG = "WhisperAsr"
        /** ~4 s of 10 ms chunks between the audio thread and the segmenter. */
        private const val AUDIO_QUEUE_CAPACITY = 400
        private const val MAX_FAILURES = 3

        /** True when the AI service answers and has Whisper installed. Blocking; call off the main thread. */
        fun isReady(aiUrl: String): Boolean = try {
            val conn = URL("$aiUrl/health").openConnection() as HttpURLConnection
            conn.connectTimeout = 2_000
            conn.readTimeout = 3_000
            try {
                conn.responseCode in 200..299 &&
                    JSONObject(conn.inputStream.bufferedReader().readText())
                        .optJSONObject("components")?.optBoolean("faster_whisper") == true
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.i(TAG, "AI service not ready at $aiUrl: ${e.message}")
            false
        }
    }
}
