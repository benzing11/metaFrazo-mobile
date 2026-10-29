package com.metafrazo.app.feature.captions

import android.util.Log
import com.metafrazo.app.core.network.Api
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Buffers finalized local captions and posts them to the backend transcript so summaries and
 * Q&A have something to read. Failed batches stay queued and are retried on the next flush.
 */
class TranscriptUploader(private val maxPending: Int = 500) {
    @Volatile private var meetingCode: String? = null
    private val pending = ArrayDeque<CaptionItem>()
    private val lock = Mutex()

    fun setMeetingCode(code: String?) {
        meetingCode = code?.takeIf { it.isNotBlank() }
    }

    suspend fun enqueue(item: CaptionItem) {
        lock.withLock {
            pending.addLast(item)
            while (pending.size > maxPending) pending.removeFirst()
        }
    }

    suspend fun flush() {
        val code = meetingCode ?: return
        val batch = lock.withLock { pending.toList() }
        if (batch.isEmpty()) return
        val ok = withContext(Dispatchers.IO) { post(code, batch) }
        if (ok) lock.withLock { repeat(batch.size.coerceAtMost(pending.size)) { pending.removeFirst() } }
    }

    private fun post(code: String, batch: List<CaptionItem>): Boolean {
        val segments = JSONArray()
        for (c in batch) {
            val translations = JSONArray()
            for ((lang, text) in c.translations) {
                if (lang == c.sourceLang || text.isBlank()) continue
                translations.put(JSONObject().put("target_lang", lang).put("translated_text", text))
            }
            segments.put(
                JSONObject()
                    .put("source_lang", c.sourceLang)
                    .put("original_text", c.original)
                    // Millisecond timestamps order segments across speakers.
                    .put("sequence", (c.ts * 1000).toLong())
                    .put("translations", translations),
            )
        }
        val (status, body) = Api.post(
            "/api/meetings/$code/transcripts",
            JSONObject().put("segments", segments).toString(),
        )
        if (status !in 200..299) Log.w("TranscriptUploader", "upload failed ($status): $body")
        return status in 200..299
    }
}
