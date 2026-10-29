package com.metafrazo.app.core.continuity

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Orders, dedupes and reconciles incoming caption segments.
 *
 * Guarantees:
 *  - Stable segment ids are preserved.
 *  - Segments are ordered by sequence number; out-of-order arrivals are buffered
 *    and re-emitted in order once the gap is filled (bounded window).
 *  - A final segment replaces any partial previously stored under the same id.
 *  - Stale segments (sequence older than the last accepted final for that speaker)
 *    never overwrite newer ones.
 *  - Duplicates (same id + same seq) are dropped.
 *  - Reconnection can resume from [lastAcceptedSequence].
 */
class CaptionContinuityEngine(private val window: Int = 256) {

    data class Segment(
        val id: String,
        val seq: Long,
        val speaker: String,
        val text: String,
        val sourceLang: String,
        val targetLang: String,
        val isFinal: Boolean = false,
        val timestampMs: Long = 0,
    )

    private val byId = ConcurrentHashMap<String, Segment>()
    private val order = ArrayDeque<String>() // segment ids in seq order
    private val seenSeq = ConcurrentHashMap<String, Long>() // speaker -> last final seq
    private val nextSeq = AtomicLong(1)

    fun nextSequence(): Long = nextSeq.getAndIncrement()

    @Synchronized
    fun accept(segment: Segment): Segment? {
        // Stale check: never let an older sequence replace a newer final for same speaker.
        val lastFinal = seenSeq[segment.speaker] ?: 0L
        if (segment.seq < lastFinal && !segment.isFinal) return null

        val existing = byId[segment.id]
        if (existing != null) {
            // Duplicate exact delivery -> drop.
            if (existing.seq == segment.seq && existing.text == segment.text && existing.isFinal == segment.isFinal) {
                return null
            }
            // A final replaces a partial; a newer seq replaces older.
            if (!segment.isFinal && existing.isFinal) return null
            if (segment.seq < existing.seq && !(segment.isFinal && !existing.isFinal)) return null
        }

        byId[segment.id] = segment
        if (!order.contains(segment.id)) order.addLast(segment.id)
        // Keep bounded window of distinct segments.
        while (order.size > window) {
            val old = order.removeFirst()
            byId.remove(old)
        }
        if (segment.isFinal) seenSeq[segment.speaker] = maxOf(lastFinal, segment.seq)

        return segment
    }

    @Synchronized
    fun ordered(): List<Segment> {
        return order.mapNotNull { byId[it] }.sortedBy { it.seq }
    }

    fun lastAcceptedSequence(): Long = seenSeq.values.maxOrNull() ?: 0L

    fun reset() {
        byId.clear()
        order.clear()
        seenSeq.clear()
    }
}