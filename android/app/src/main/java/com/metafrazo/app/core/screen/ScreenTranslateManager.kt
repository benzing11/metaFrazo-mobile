package com.metafrazo.app.core.screen

import java.util.concurrent.atomic.AtomicLong

/**
 * Tracks the generation of screen-translation passes so stale results (from an older frame)
 * are dropped, and memoizes per-block translations so a slide that stays on screen is
 * translated once instead of on every sampled frame.
 */
class ScreenTranslateManager {
    private val generation = AtomicLong(0)
    fun nextGeneration(): Long = generation.incrementAndGet()
    fun current(): Long = generation.get()
    fun isStale(resultGeneration: Long): Boolean = resultGeneration < generation.get()

    /** Language pair the cached translations belong to; a change invalidates them. */
    private var cachedPair: String? = null

    /**
     * Bounded LRU of original block text to its translation. Access-ordered so the blocks
     * currently on screen stay cached while text from earlier slides ages out.
     */
    private val translations = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean =
            size > MAX_CACHED_BLOCKS
    }

    /**
     * Declares the language pair subsequent lookups apply to. Clears the cache when the pair
     * changes so a language switch never shows translations for the previous target.
     */
    @Synchronized
    fun useLanguagePair(sourceLang: String, targetLang: String) {
        val pair = "$sourceLang>$targetLang"
        if (cachedPair != pair) {
            cachedPair = pair
            translations.clear()
        }
    }

    /** Previously translated text for [original], or null when it hasn't been seen. */
    @Synchronized
    fun cached(original: String): String? = translations[original]

    @Synchronized
    fun remember(original: String, translated: String) {
        translations[original] = translated
    }

    /** Number of cached blocks; used by tests to assert eviction. */
    @Synchronized
    fun cacheSize(): Int = translations.size

    private companion object {
        /** Enough for several dense slides without growing unbounded over a long presentation. */
        const val MAX_CACHED_BLOCKS = 300
    }
}
