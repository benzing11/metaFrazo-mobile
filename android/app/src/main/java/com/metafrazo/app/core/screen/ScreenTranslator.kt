package com.metafrazo.app.core.screen

import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.metafrazo.app.core.translate.DeviceTranslator
import com.metafrazo.app.core.translate.TranslateResult
import com.metafrazo.app.ui.components.OverlayRegion
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import livekit.org.webrtc.VideoFrame
import livekit.org.webrtc.VideoSink
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Translates text inside a shared screen on this device:
 * samples a frame every [intervalMs] from the remote screen-share track, runs ML Kit OCR,
 * translates each text block, and publishes normalized overlay regions.
 * Results from older frames are dropped via [ScreenTranslateManager] generations.
 */
class ScreenTranslator(
    private val scope: CoroutineScope,
    private val intervalMs: Long = 2500,
) {
    data class State(
        val regions: List<OverlayRegion> = emptyList(),
        val aspect: Float = 16f / 9f,
        val status: String = "",
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private val manager = ScreenTranslateManager()
    private val latin = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val devanagari = TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
    private val busy = AtomicBoolean(false)
    private var lastSample = 0L
    private var track: VideoTrack? = null
    private var job: Job? = null

    /** Language the text on the shared screen is in. Defaults to English. */
    @Volatile var sourceLang: String = "en"

    /** Language to translate overlay text into. Defaults to Hindi. */
    @Volatile var targetLang: String = "hi"

    private val sink = VideoSink { frame -> onFrame(frame) }

    fun attach(videoTrack: VideoTrack) {
        if (track === videoTrack) return
        detach()
        track = videoTrack
        videoTrack.addRenderer(sink)
        _state.value = _state.value.copy(status = "Reading shared screen…")
        Log.i("ScreenTranslator", "attached track, src=$sourceLang tgt=$targetLang")
    }

    fun detach() {
        track?.removeRenderer(sink)
        track = null
        job?.cancel()
        manager.nextGeneration()
        _state.value = State()
        busy.set(false)
    }

    private fun onFrame(frame: VideoFrame) {
        val now = System.currentTimeMillis()
        if (now - lastSample < intervalMs || !busy.compareAndSet(false, true)) return
        lastSample = now

        // Copy pixels synchronously (we're on WebRTC's render thread) but keep it minimal:
        // convert to I420 and immediately copy to a ByteArray so the frame can be released.
        val i420 = frame.buffer.toI420() ?: run { busy.set(false); return }
        val width = i420.width
        val height = i420.height
        val rotation = frame.rotation
        val nv21 = try { toNv21(i420) } finally { i420.release() }

        val generation = manager.nextGeneration()
        job = scope.launch(Dispatchers.Default) {
            try {
                val image = InputImage.fromByteArray(nv21, width, height, rotation, InputImage.IMAGE_FORMAT_NV21)
                val rotated = rotation == 90 || rotation == 270
                val w = (if (rotated) height else width).toFloat()
                val h = (if (rotated) width else height).toFloat()
                val result = recognize(image, w, h)
                if (manager.isStale(generation)) return@launch
                _state.value = State(
                    regions = result.regions,
                    aspect = w / h,
                    status = result.status,
                )
            } catch (e: Exception) {
                Log.w("ScreenTranslator", "frame failed", e)
                if (!manager.isStale(generation)) {
                    _state.value = _state.value.copy(status = "Screen translation error: ${e.message}")
                }
            } finally {
                busy.set(false)
            }
        }
    }

    private data class Pass(val regions: List<OverlayRegion>, val status: String)

    private suspend fun recognize(image: InputImage, w: Float, h: Float): Pass {
        // ML Kit on-device OCR only ships Latin and Devanagari recognizers. Pick Devanagari
        // for Devanagari-script languages; use Latin for Latin-script languages. Everything
        // else (Tamil, Telugu, Kannada, Bengali, Gujarati, Urdu, Arabic, Chinese …) has no
        // on-device recognizer, so report that honestly instead of silently returning nothing.
        val devanagariLangs = setOf("hi", "mr", "ne", "sa")
        val latinLangs = setOf("en", "es", "fr", "de", "it", "pt", "nl", "id", "ms", "tr", "vi")
        val client = when (sourceLang) {
            in devanagariLangs -> devanagari
            in latinLangs -> latin
            else -> {
                val label = DeviceTranslator.labels[sourceLang] ?: sourceLang.uppercase()
                return Pass(emptyList(), "On-device screen OCR supports Latin and Devanagari text only; $label isn't available")
            }
        }

        val text = suspendCancellableCoroutine<com.google.mlkit.vision.text.Text?> { cont ->
            client.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { e ->
                    Log.w("ScreenTranslator", "OCR failed", e)
                    cont.resume(null)
                }
        } ?: return Pass(emptyList(), "Couldn't read text from the shared screen")

        // Make sure the translation model for this pair is downloaded before translating.
        if (sourceLang != targetLang) {
            val prepError = DeviceTranslator.prepare(sourceLang, targetLang)
            if (prepError != null) {
                Log.w("ScreenTranslator", "model prepare failed: $prepError")
                // Still show the detected (untranslated) text so the overlay isn't empty,
                // but tell the user translation is unavailable.
                val raw = text.textBlocks.mapNotNull { block ->
                    val box = block.boundingBox ?: return@mapNotNull null
                    val original = block.text.replace('\n', ' ').trim()
                    if (original.length < 2) return@mapNotNull null
                    OverlayRegion(
                        text = original,
                        translated = original,
                        x = box.left / w,
                        y = box.top / h,
                        w = box.width() / w,
                        h = box.height() / h,
                    )
                }
                return Pass(raw, "Translation unavailable ($prepError)")
            }
        }

        var translated = 0
        var failed = 0
        val regions = text.textBlocks.mapNotNull { block ->
            val box = block.boundingBox ?: return@mapNotNull null
            val original = block.text.replace('\n', ' ').trim()
            if (original.length < 2) return@mapNotNull null
            val shown = if (sourceLang == targetLang) {
                original
            } else {
                when (val r = DeviceTranslator.translateResult(original, sourceLang, targetLang)) {
                    is TranslateResult.Translated -> { translated++; r.text }
                    is TranslateResult.SameLanguage -> r.text
                    is TranslateResult.Unsupported -> { failed++; r.text }
                    is TranslateResult.Failed -> {
                        Log.w("ScreenTranslator", "translate failed: ${r.reason}")
                        failed++; r.text
                    }
                }
            }
            OverlayRegion(
                text = original,
                translated = shown,
                x = box.left / w,
                y = box.top / h,
                w = box.width() / w,
                h = box.height() / h,
            )
        }

        val status = when {
            regions.isEmpty() -> "No text found on the shared screen"
            sourceLang == targetLang -> "Showing ${regions.size} text block${plural(regions.size)}"
            failed > 0 && translated == 0 -> "Couldn't translate the detected text"
            failed > 0 -> "Translated $translated of ${regions.size} text blocks"
            else -> "Translated ${regions.size} text block${plural(regions.size)}"
        }
        return Pass(regions, status)
    }

    private fun plural(n: Int) = if (n == 1) "" else "s"

    fun close() {
        detach()
        latin.close()
        devanagari.close()
    }

    private fun toNv21(buf: VideoFrame.I420Buffer): ByteArray {
        val w = buf.width
        val h = buf.height
        val out = ByteArray(w * h + 2 * ((w + 1) / 2) * ((h + 1) / 2))
        val y = buf.dataY
        val u = buf.dataU
        val v = buf.dataV
        var pos = 0
        for (row in 0 until h) {
            y.position(row * buf.strideY)
            y.get(out, pos, w)
            pos += w
        }
        val cw = (w + 1) / 2
        val ch = (h + 1) / 2
        for (row in 0 until ch) {
            for (col in 0 until cw) {
                out[pos++] = v.get(row * buf.strideV + col)
                out[pos++] = u.get(row * buf.strideU + col)
            }
        }
        return out
    }
}
