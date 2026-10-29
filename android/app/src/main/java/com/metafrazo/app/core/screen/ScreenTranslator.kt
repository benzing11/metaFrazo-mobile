package com.metafrazo.app.core.screen

import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.metafrazo.app.core.translate.DeviceTranslator
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

    @Volatile var sourceLang: String = "en"
    @Volatile var targetLang: String = "ta"

    private val sink = VideoSink { frame -> onFrame(frame) }

    fun attach(videoTrack: VideoTrack) {
        if (track === videoTrack) return
        detach()
        track = videoTrack
        videoTrack.addRenderer(sink)
        _state.value = _state.value.copy(status = "Reading shared screen…")
    }

    fun detach() {
        track?.removeRenderer(sink)
        track = null
        job?.cancel()
        manager.nextGeneration()
        _state.value = State()
    }

    private fun onFrame(frame: VideoFrame) {
        val now = System.currentTimeMillis()
        if (now - lastSample < intervalMs || !busy.compareAndSet(false, true)) return
        lastSample = now
        // Copy pixels synchronously: the frame is only valid during this callback.
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
                val regions = recognize(image, w, h)
                if (manager.isStale(generation)) return@launch
                _state.value = State(
                    regions = regions,
                    aspect = w / h,
                    status = if (regions.isEmpty()) "No text found on the shared screen" else "Translated ${regions.size} text blocks",
                )
            } catch (e: Exception) {
                Log.w("ScreenTranslator", "frame failed", e)
                _state.value = _state.value.copy(status = "Screen translation error: ${e.message}")
            } finally {
                busy.set(false)
            }
        }
    }

    private suspend fun recognize(image: InputImage, w: Float, h: Float): List<OverlayRegion> {
        val client = if (sourceLang == "hi") devanagari else latin
        val text = suspendCancellableCoroutine<com.google.mlkit.vision.text.Text?> { cont ->
            client.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        } ?: return emptyList()
        DeviceTranslator.prepare(sourceLang, targetLang)
        return text.textBlocks.mapNotNull { block ->
            val box = block.boundingBox ?: return@mapNotNull null
            val original = block.text.replace('\n', ' ').trim()
            if (original.length < 2) return@mapNotNull null
            OverlayRegion(
                text = original,
                translated = DeviceTranslator.translate(original, sourceLang, targetLang),
                x = box.left / w,
                y = box.top / h,
                w = box.width() / w,
                h = box.height() / h,
            )
        }
    }

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
