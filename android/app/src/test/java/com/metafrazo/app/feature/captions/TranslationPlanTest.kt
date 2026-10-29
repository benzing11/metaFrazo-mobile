package com.metafrazo.app.feature.captions

import org.junit.Assert.assertEquals
import org.junit.Test

class TranslationPlanTest {
    private val supported = listOf("en", "ta", "hi")

    @Test
    fun targets_englishSourceRequestsTamilAndHindi() {
        assertEquals(listOf("ta", "hi"), TranslationPlan.targetsFor("en", supported))
    }

    @Test
    fun targets_sourceNeverAppearsAsTarget() {
        assertEquals(listOf("en", "hi"), TranslationPlan.targetsFor("ta", supported))
    }

    @Test
    fun merge_alwaysIncludesOriginalUnderSource() {
        val merged = TranslationPlan.merge("en", "hello", mapOf("ta" to "வணக்கம்"))
        assertEquals("hello", merged["en"])
        assertEquals("வணக்கம்", merged["ta"])
    }

    @Test
    fun merge_failedPairDoesNotDropSuccessfulOne() {
        // "hi" failed and is simply absent; "ta" still present.
        val merged = TranslationPlan.merge("en", "hello", mapOf("ta" to "வணக்கம்"))
        assertEquals(setOf("en", "ta"), merged.keys)
    }
}
