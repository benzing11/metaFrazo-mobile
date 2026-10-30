package com.metafrazo.app.feature.captions

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptionUpsertTest {
    @Test
    fun upsertAddsNewItemWhenNoneMatches() {
        val existing = listOf(
            CaptionItem("Alice", "en", "Hello", mapOf("en" to "Hello"), 100.0)
        )
        val newItem = CaptionItem("Bob", "en", "Hi", mapOf("en" to "Hi"), 101.0)
        val result = CaptionUpsert.upsert(existing, newItem)
        assertEquals(2, result.size)
        assertEquals("Bob", result[1].speaker)
    }

    @Test
    fun upsertReplacesExistingItemWithSameSpeakerAndTimestamp() {
        val existing = listOf(
            CaptionItem("Alice", "en", "Hello", mapOf("en" to "Hello"), 100.0),
            CaptionItem("Bob", "en", "Hey", mapOf("en" to "Hey"), 101.0)
        )
        val updated = CaptionItem("Alice", "en", "Hello", mapOf("en" to "Hello", "es" to "Hola"), 100.0)
        val result = CaptionUpsert.upsert(existing, updated)
        assertEquals(2, result.size)
        assertEquals("Hola", result[0].translations["es"])
        assertEquals("Bob", result[1].speaker)
    }

    @Test
    fun upsertCapsAtLimit() {
        val limit = 5
        val existing = (1..5).map { i ->
            CaptionItem("Alice", "en", "Message $i", emptyMap(), i.toDouble())
        }
        val newItem = CaptionItem("Alice", "en", "Message 6", emptyMap(), 6.0)
        val result = CaptionUpsert.upsert(existing, newItem, maxItems = limit)
        assertEquals(limit, result.size)
        assertEquals("Message 2", result.first().original)
        assertEquals("Message 6", result.last().original)
    }
}
