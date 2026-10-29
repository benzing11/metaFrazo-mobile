package com.metafrazo.app.core.continuity

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Orders and dedupes incoming caption messages by sequence number.
 * Drop stale/duplicate/out-of-order entries. Keep a bounded window.
 */
class CaptionContinuityEngine(private val window: Int = 256) {
    data class Caption(val seq: Long, val speaker: String, val text: String, val sourceLang: String, val targetLang: String)

    private val seen = ConcurrentHashMap<Long, Caption>()
    private val order = ArrayDeque<Long>()
    private val nextSeq = AtomicLong(1)

    fun nextSequence(): Long = nextSeq.getAndIncrement()

    @Synchronized
    fun accept(caption: Caption): Caption? {
        if (seen.containsKey(caption.seq)) return null // duplicate
        seen[caption.seq] = caption
        order.addLast(caption.seq)
        while (order.size > window) {
            val old = order.removeFirst()
            seen.remove(old)
        }
        return caption
    }

    @Synchronized
    fun ordered(): List<Caption> = order.mapNotNull { seen[it] }

    fun reset() {
        seen.clear()
        order.clear()
    }
}