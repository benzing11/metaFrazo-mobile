package com.metafrazo.app.core.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class UtteranceSegmenterTest {
    private val rate = 16_000

    private fun silence(ms: Int) = ByteArray(rate * ms / 1000 * 2)

    private fun tone(ms: Int, amplitude: Int = 5000): ByteArray {
        val n = rate * ms / 1000
        val out = ByteArray(n * 2)
        for (i in 0 until n) {
            val s = (amplitude * sin(2 * PI * 220 * i / rate)).toInt()
            out[i * 2] = (s and 0xFF).toByte()
            out[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }
        return out
    }

    private fun ms(bytes: ByteArray) = bytes.size / 2 * 1000 / rate

    @Test
    fun `one phrase between silences becomes one utterance`() {
        val seg = UtteranceSegmenter()
        val out = seg.feed(silence(1000)) + seg.feed(tone(1000)) + seg.feed(silence(1000))
        assertEquals(1, out.size)
        // Pre-roll + speech + the trailing silence that ended it.
        assertTrue(ms(out[0]) in 1000..2100)
        assertFalse(seg.inSpeech)
    }

    @Test
    fun `short click is dropped`() {
        val seg = UtteranceSegmenter()
        val out = seg.feed(silence(500)) + seg.feed(tone(100)) + seg.feed(silence(1000))
        assertTrue(out.isEmpty())
    }

    @Test
    fun `long speech is split at the max length`() {
        val seg = UtteranceSegmenter(maxUtteranceMs = 8_000)
        val out = seg.feed(tone(10_000))
        assertEquals(1, out.size)
        assertTrue(ms(out[0]) in 7_900..8_100)
        assertTrue(seg.inSpeech)
        assertTrue(seg.flush() != null)
    }

    @Test
    fun `audio arriving in odd-sized chunks is handled`() {
        val seg = UtteranceSegmenter()
        val all = silence(500) + tone(800) + silence(900)
        val out = mutableListOf<ByteArray>()
        var i = 0
        while (i < all.size) {
            val end = minOf(i + 333, all.size)
            out += seg.feed(all.copyOfRange(i, end))
            i = end
        }
        assertEquals(1, out.size)
    }

    @Test
    fun `flush with nothing collected returns null`() {
        val seg = UtteranceSegmenter()
        seg.feed(silence(500))
        assertNull(seg.flush())
    }
}
