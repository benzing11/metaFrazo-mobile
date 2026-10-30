package com.metafrazo.app.core.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTranslateManagerTest {
    @Test
    fun olderGenerationIsStale() {
        val m = ScreenTranslateManager()
        val first = m.nextGeneration()
        m.nextGeneration()
        assertTrue(m.isStale(first))
    }

    @Test
    fun currentGenerationIsNotStale() {
        val m = ScreenTranslateManager()
        m.nextGeneration()
        assertFalse(m.isStale(m.current()))
    }

    @Test
    fun remembersTranslationForRepeatedBlock() {
        val m = ScreenTranslateManager()
        m.useLanguagePair("en", "hi")
        m.remember("Revenue growth", "राजस्व वृद्धि")
        assertEquals("राजस्व वृद्धि", m.cached("Revenue growth"))
    }

    @Test
    fun unseenBlockIsNotCached() {
        val m = ScreenTranslateManager()
        m.useLanguagePair("en", "hi")
        assertNull(m.cached("Never seen"))
    }

    @Test
    fun changingLanguagePairClearsCache() {
        val m = ScreenTranslateManager()
        m.useLanguagePair("en", "hi")
        m.remember("Revenue growth", "राजस्व वृद्धि")
        m.useLanguagePair("en", "ta")
        assertNull(m.cached("Revenue growth"))
    }

    @Test
    fun samePairKeepsCache() {
        val m = ScreenTranslateManager()
        m.useLanguagePair("en", "hi")
        m.remember("Revenue growth", "राजस्व वृद्धि")
        m.useLanguagePair("en", "hi")
        assertEquals("राजस्व वृद्धि", m.cached("Revenue growth"))
    }

    @Test
    fun evictsOldestBlocksBeyondLimit() {
        val m = ScreenTranslateManager()
        m.useLanguagePair("en", "hi")
        repeat(400) { i -> m.remember("block $i", "translated $i") }
        assertEquals(300, m.cacheSize())
        // The earliest blocks aged out; the most recent survive.
        assertNull(m.cached("block 0"))
        assertEquals("translated 399", m.cached("block 399"))
    }
}
