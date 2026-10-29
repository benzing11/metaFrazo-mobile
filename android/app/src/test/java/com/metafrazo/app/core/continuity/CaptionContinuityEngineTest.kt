package com.metafrazo.app.core.continuity

import com.metafrazo.app.core.continuity.CaptionContinuityEngine.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionContinuityEngineTest {
    private fun engine() = CaptionContinuityEngine()

    @Test
    fun acceptsNewSegment() {
        val e = engine()
        val s = Segment("s1", 1, "alice", "hello", "en", "ta")
        assertEquals(s, e.accept(s))
    }

    @Test
    fun dropsExactDuplicate() {
        val e = engine()
        val s = Segment("s1", 1, "alice", "hello", "en", "ta")
        e.accept(s)
        assertNull(e.accept(s))
    }

    @Test
    fun finalReplacesPartial() {
        val e = engine()
        val partial = Segment("s1", 1, "alice", "hel", "en", "ta", isFinal = false)
        val finalSeg = Segment("s1", 2, "alice", "hello", "en", "ta", isFinal = true)
        e.accept(partial)
        e.accept(finalSeg)
        val ordered = e.ordered()
        assertEquals(1, ordered.size)
        assertEquals("hello", ordered[0].text)
        assertTrue(ordered[0].isFinal)
    }

    @Test
    fun stalePartialDoesNotOverwriteFinal() {
        val e = engine()
        val finalSeg = Segment("s1", 5, "alice", "hello", "en", "ta", isFinal = true)
        val stale = Segment("s2", 2, "alice", "old", "en", "ta", isFinal = false)
        e.accept(finalSeg)
        assertNull(e.accept(stale))
    }

    @Test
    fun orderedBySequence() {
        val e = engine()
        e.accept(Segment("a", 3, "alice", "c", "en", "ta"))
        e.accept(Segment("b", 1, "alice", "a", "en", "ta"))
        e.accept(Segment("c", 2, "alice", "b", "en", "ta"))
        val texts = e.ordered().map { it.text }
        assertEquals(listOf("a", "b", "c"), texts)
    }

    @Test
    fun resetClearsState() {
        val e = engine()
        e.accept(Segment("s1", 1, "alice", "hi", "en", "ta"))
        e.reset()
        assertTrue(e.ordered().isEmpty())
    }
}